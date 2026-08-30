/*
 * Copyright (c) 2026, ScapePath
 * All rights reserved. BSD 2-Clause. See LICENSE file.
 */
package com.scapepath.plugin.transport;

import com.scapepath.plugin.ScapePath;
import com.scapepath.plugin.snapshot.AccountSnapshot;
import com.scapepath.plugin.snapshot.CollectedSection;
import com.scapepath.plugin.snapshot.SectionData;
import com.scapepath.plugin.snapshot.SnapshotSectionType;
import com.scapepath.plugin.snapshot.data.AchievementDiaryData;
import com.scapepath.plugin.snapshot.data.BankData;
import com.scapepath.plugin.snapshot.data.DiaryTierSnapshot;
import com.scapepath.plugin.snapshot.data.EquipmentData;
import com.scapepath.plugin.snapshot.data.IdentityData;
import com.scapepath.plugin.snapshot.data.InventoryData;
import com.scapepath.plugin.snapshot.data.ItemSnapshot;
import com.scapepath.plugin.snapshot.data.QuestSnapshot;
import com.scapepath.plugin.snapshot.data.QuestsData;
import com.scapepath.plugin.snapshot.data.SkillData;
import com.scapepath.plugin.snapshot.data.SkillsData;
import com.scapepath.plugin.snapshot.data.WealthData;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import javax.inject.Singleton;

/**
 * Converts a normalized {@link AccountSnapshot} into the deterministic, versioned JSON
 * transport payload &mdash; the API contract between the plugin and the future ScapePath
 * ingestion API.
 *
 * <p><b>This class performs no I/O and knows nothing about HTTP.</b> It produces a
 * {@code String}; a future transport layer is responsible for sending it. Determinism:
 * the same snapshot always yields byte-for-byte identical JSON, because field order,
 * section order, null handling, and enum/timestamp formatting are all fixed here.</p>
 *
 * <p>Contract rules:</p>
 * <ul>
 *   <li>stable semantic field names (no Java class names);</li>
 *   <li>enums emitted as their {@code name()} (e.g. {@code "COMPLETE"});</li>
 *   <li>timestamps as ISO-8601 UTC (e.g. {@code "2026-08-29T21:40:31Z"});</li>
 *   <li>a section's {@code data} is JSON {@code null} when the section is unavailable
 *       &mdash; never an empty object, so "unknown" and "empty" stay distinct;</li>
 *   <li>empty lists are emitted as {@code []}; nullable scalars as {@code null}.</li>
 * </ul>
 */
@Singleton
public class SnapshotPayloadSerializer
{
	/** Canonical section order and stable JSON keys. Only present sections are emitted. */
	private static final Map<SnapshotSectionType, String> SECTION_KEYS = buildSectionKeys();

	private static Map<SnapshotSectionType, String> buildSectionKeys()
	{
		final Map<SnapshotSectionType, String> m = new LinkedHashMap<>();
		m.put(SnapshotSectionType.IDENTITY, "identity");
		m.put(SnapshotSectionType.SKILLS, "skills");
		m.put(SnapshotSectionType.QUESTS, "quests");
		m.put(SnapshotSectionType.ACHIEVEMENT_DIARIES, "achievementDiaries");
		m.put(SnapshotSectionType.INVENTORY, "inventory");
		m.put(SnapshotSectionType.EQUIPMENT, "equipment");
		m.put(SnapshotSectionType.BANK, "bank");
		m.put(SnapshotSectionType.WEALTH, "wealth");
		return m;
	}

	/** Serialize a snapshot to the deterministic transport JSON. */
	public String toJson(AccountSnapshot snapshot)
	{
		final JsonWriter w = new JsonWriter();
		w.beginObject();
		w.name("schemaVersion").value(ScapePath.SCHEMA_VERSION);
		w.name("pluginVersion").value(snapshot.getPluginVersion());
		w.name("timestamp").value(iso(snapshot.getTimestamp()));

		w.name("account").beginObject();
		w.name("rsn").value(snapshot.getRsn());
		w.endObject();

		w.name("sections").beginObject();
		for (Map.Entry<SnapshotSectionType, String> entry : SECTION_KEYS.entrySet())
		{
			final CollectedSection section = snapshot.getSection(entry.getKey());
			if (section == null)
			{
				continue; // section not produced this snapshot
			}
			w.name(entry.getValue());
			writeSection(w, section);
		}
		w.endObject();

		w.endObject();
		return w.toJson();
	}

	/**
	 * Build a local, non-transmitted description of the payload (JSON, byte size, and
	 * per-section freshness) for the diagnostic panel.
	 */
	public PayloadPreview preview(AccountSnapshot snapshot)
	{
		final String json = toJson(snapshot);
		final int byteSize = json.getBytes(StandardCharsets.UTF_8).length;

		final List<PayloadPreview.SectionSummary> summaries = new ArrayList<>();
		for (Map.Entry<SnapshotSectionType, String> entry : SECTION_KEYS.entrySet())
		{
			final CollectedSection section = snapshot.getSection(entry.getKey());
			if (section != null)
			{
				summaries.add(new PayloadPreview.SectionSummary(
					entry.getValue(), section.getFreshness().name()));
			}
		}

		return new PayloadPreview(
			ScapePath.SCHEMA_VERSION,
			snapshot.getPluginVersion(),
			iso(snapshot.getTimestamp()),
			json,
			byteSize,
			summaries);
	}

	private void writeSection(JsonWriter w, CollectedSection section)
	{
		w.beginObject();
		w.name("freshness").value(section.getFreshness().name());
		w.name("collectedAt").value(iso(section.getCollectedAt()));
		w.name("data");
		final SectionData data = section.getData();
		if (data == null)
		{
			w.nullValue(); // unavailable: explicitly null, distinct from an empty object
		}
		else
		{
			writeData(w, data);
		}
		w.endObject();
	}

	private void writeData(JsonWriter w, SectionData data)
	{
		if (data instanceof IdentityData)
		{
			writeIdentity(w, (IdentityData) data);
		}
		else if (data instanceof SkillsData)
		{
			writeSkills(w, (SkillsData) data);
		}
		else if (data instanceof QuestsData)
		{
			writeQuests(w, (QuestsData) data);
		}
		else if (data instanceof AchievementDiaryData)
		{
			writeDiaries(w, (AchievementDiaryData) data);
		}
		else if (data instanceof InventoryData)
		{
			writeInventory(w, (InventoryData) data);
		}
		else if (data instanceof EquipmentData)
		{
			writeEquipment(w, (EquipmentData) data);
		}
		else if (data instanceof BankData)
		{
			writeBank(w, (BankData) data);
		}
		else if (data instanceof WealthData)
		{
			writeWealth(w, (WealthData) data);
		}
		else
		{
			// Unknown payload type: emit an empty object rather than leak class info.
			w.beginObject().endObject();
		}
	}

	private void writeIdentity(JsonWriter w, IdentityData d)
	{
		w.beginObject();
		w.name("loggedIn").value(d.isLoggedIn());
		w.name("accountHash").value(d.getAccountHash());
		w.name("rsn").value(d.getRsn());
		w.name("accountType").value(d.getAccountType());
		w.name("world").value(d.getWorld());
		w.endObject();
	}

	private void writeSkills(JsonWriter w, SkillsData d)
	{
		w.beginObject();
		w.name("totalLevel").value(d.getTotalLevel());
		w.name("totalXp").value(d.getTotalXp());
		w.name("combatLevel").value(d.getCombatLevel());
		w.name("skills").beginArray();
		for (SkillData s : d.getSkills())
		{
			w.beginObject();
			w.name("name").value(s.getName());
			w.name("level").value(s.getLevel());
			w.name("xp").value(s.getXp());
			w.endObject();
		}
		w.endArray();
		w.endObject();
	}

	private void writeQuests(JsonWriter w, QuestsData d)
	{
		w.beginObject();
		w.name("questPoints").value(d.getQuestPoints());
		w.name("completedCount").value(d.getCompletedCount());
		w.name("totalCount").value(d.getTotalCount());
		w.name("quests").beginArray();
		for (QuestSnapshot q : d.getQuests())
		{
			w.beginObject();
			w.name("id").value(q.getId());
			w.name("name").value(q.getName());
			w.name("state").value(q.getState());
			w.endObject();
		}
		w.endArray();
		w.endObject();
	}

	private void writeDiaries(JsonWriter w, AchievementDiaryData d)
	{
		w.beginObject();
		w.name("completedTiers").value(d.getCompletedTiers());
		w.name("totalTiers").value(d.getTotalTiers());
		w.name("tiers").beginArray();
		for (DiaryTierSnapshot t : d.getTiers())
		{
			w.beginObject();
			w.name("region").value(t.getRegion());
			w.name("tier").value(t.getTier());
			w.name("completed").value(t.isCompleted());
			w.endObject();
		}
		w.endArray();
		w.endObject();
	}

	private void writeInventory(JsonWriter w, InventoryData d)
	{
		w.beginObject();
		w.name("occupiedSlots").value(d.getOccupiedSlots());
		w.name("items");
		writeItems(w, d.getItems());
		w.endObject();
	}

	private void writeEquipment(JsonWriter w, EquipmentData d)
	{
		w.beginObject();
		w.name("items");
		writeItems(w, d.getItems());
		w.endObject();
	}

	private void writeBank(JsonWriter w, BankData d)
	{
		w.beginObject();
		w.name("uniqueItems").value(d.getUniqueItems());
		w.name("coins").value(d.getCoins());
		w.name("estimatedValue").value(d.getEstimatedValue());
		w.name("source").value(d.getSource());
		w.name("items");
		writeItems(w, d.getItems());
		w.endObject();
	}

	private void writeWealth(JsonWriter w, WealthData d)
	{
		w.beginObject();
		w.name("gpOnHand").value(d.getGpOnHand());
		w.name("bankGp").value(d.getBankGp());
		w.name("estimatedBankValue").value(d.getEstimatedBankValue());
		w.endObject();
	}

	private void writeItems(JsonWriter w, java.util.List<ItemSnapshot> items)
	{
		w.beginArray();
		for (ItemSnapshot item : items)
		{
			w.beginObject();
			w.name("id").value(item.getId());
			w.name("quantity").value(item.getQuantity());
			w.name("slot").value(item.getSlot());
			w.endObject();
		}
		w.endArray();
	}

	private static String iso(@Nullable Instant instant)
	{
		return instant == null ? null : DateTimeFormatter.ISO_INSTANT.format(instant);
	}
}
