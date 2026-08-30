/*
 * Copyright (c) 2026, ScapePath
 * All rights reserved. BSD 2-Clause. See LICENSE file.
 */
package com.scapepath.plugin.transport;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.scapepath.plugin.ScapePath;
import com.scapepath.plugin.snapshot.AccountSnapshot;
import com.scapepath.plugin.snapshot.CollectedSection;
import com.scapepath.plugin.snapshot.SnapshotSectionType;
import com.scapepath.plugin.snapshot.SourceFreshness;
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
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public class SnapshotPayloadSerializerTest
{
	private final SnapshotPayloadSerializer serializer = new SnapshotPayloadSerializer();
	private static final Instant T = Instant.parse("2026-08-29T21:40:31Z");

	private static CollectedSection section(SnapshotSectionType type, SourceFreshness f,
		Instant at, com.scapepath.plugin.snapshot.SectionData data)
	{
		return new CollectedSection(type, f, at, data);
	}

	private AccountSnapshot.AccountSnapshotBuilder base()
	{
		return AccountSnapshot.builder().timestamp(T).pluginVersion("0.1.0-SNAPSHOT").rsn("Zezima");
	}

	private JsonObject parse(String json)
	{
		return new JsonParser().parse(json).getAsJsonObject();
	}

	private JsonObject sections(String json)
	{
		return parse(json).getAsJsonObject("sections");
	}

	@Test
	public void topLevelHasSchemaAndMetadata()
	{
		AccountSnapshot snap = base().build();
		JsonObject root = parse(serializer.toJson(snap));

		assertEquals(ScapePath.SCHEMA_VERSION, root.get("schemaVersion").getAsInt());
		assertEquals("0.1.0-SNAPSHOT", root.get("pluginVersion").getAsString());
		assertEquals("2026-08-29T21:40:31Z", root.get("timestamp").getAsString());
		assertEquals("Zezima", root.getAsJsonObject("account").get("rsn").getAsString());
	}

	@Test
	public void producesValidJson()
	{
		AccountSnapshot snap = base()
			.section(SnapshotSectionType.IDENTITY, section(SnapshotSectionType.IDENTITY,
				SourceFreshness.COMPLETE, T, new IdentityData(true, 123L, "Zezima", "IRONMAN", 302)))
			.build();
		// JsonParser throws on invalid JSON.
		JsonObject root = parse(serializer.toJson(snap));
		assertTrue(root.has("sections"));
	}

	@Test
	public void deterministicForSameSnapshot()
	{
		AchievementDiaryData diaries = new AchievementDiaryData(
			Arrays.asList(new DiaryTierSnapshot("Ardougne", "Easy", true),
				new DiaryTierSnapshot("Karamja", "Elite", false)), 1, 2);
		AccountSnapshot snap = base()
			.section(SnapshotSectionType.ACHIEVEMENT_DIARIES, section(
				SnapshotSectionType.ACHIEVEMENT_DIARIES, SourceFreshness.COMPLETE, T, diaries))
			.build();

		assertEquals(serializer.toJson(snap), serializer.toJson(snap));
	}

	@Test
	public void identitySerialization()
	{
		AccountSnapshot snap = base().section(SnapshotSectionType.IDENTITY, section(
			SnapshotSectionType.IDENTITY, SourceFreshness.COMPLETE, T,
			new IdentityData(true, 999L, "Zezima", "NORMAL", 330))).build();
		JsonObject id = sections(serializer.toJson(snap)).getAsJsonObject("identity").getAsJsonObject("data");

		assertEquals(999L, id.get("accountHash").getAsLong());
		assertEquals("Zezima", id.get("rsn").getAsString());
		assertEquals("NORMAL", id.get("accountType").getAsString());
		assertEquals(330, id.get("world").getAsInt());
		assertTrue(id.get("loggedIn").getAsBoolean());
	}

	@Test
	public void skillsSerialization()
	{
		SkillsData skills = new SkillsData(
			Arrays.asList(new SkillData("Attack", 60, 273742), new SkillData("Hitpoints", 62, 350000)),
			122, 623742L, 65);
		AccountSnapshot snap = base().section(SnapshotSectionType.SKILLS, section(
			SnapshotSectionType.SKILLS, SourceFreshness.COMPLETE, T, skills)).build();
		JsonObject data = sections(serializer.toJson(snap)).getAsJsonObject("skills").getAsJsonObject("data");

		assertEquals(122, data.get("totalLevel").getAsInt());
		assertEquals(623742L, data.get("totalXp").getAsLong());
		assertEquals(65, data.get("combatLevel").getAsInt());
		JsonArray arr = data.getAsJsonArray("skills");
		assertEquals(2, arr.size());
		assertEquals("Attack", arr.get(0).getAsJsonObject().get("name").getAsString());
	}

	@Test
	public void questSerializationWithStableIds()
	{
		QuestsData quests = new QuestsData(
			Collections.singletonList(new QuestSnapshot(29, "Cook's Assistant", "FINISHED")), 1, 1, 185);
		AccountSnapshot snap = base().section(SnapshotSectionType.QUESTS, section(
			SnapshotSectionType.QUESTS, SourceFreshness.COMPLETE, T, quests)).build();
		JsonObject data = sections(serializer.toJson(snap)).getAsJsonObject("quests").getAsJsonObject("data");

		assertEquals(185, data.get("questPoints").getAsInt());
		JsonObject q = data.getAsJsonArray("quests").get(0).getAsJsonObject();
		assertEquals(29, q.get("id").getAsInt());
		assertEquals("FINISHED", q.get("state").getAsString());
	}

	@Test
	public void diarySerialization()
	{
		AchievementDiaryData diaries = new AchievementDiaryData(
			Collections.singletonList(new DiaryTierSnapshot("Karamja", "Elite", true)), 1, 1);
		AccountSnapshot snap = base().section(SnapshotSectionType.ACHIEVEMENT_DIARIES, section(
			SnapshotSectionType.ACHIEVEMENT_DIARIES, SourceFreshness.COMPLETE, T, diaries)).build();
		JsonObject t = sections(serializer.toJson(snap)).getAsJsonObject("achievementDiaries")
			.getAsJsonObject("data").getAsJsonArray("tiers").get(0).getAsJsonObject();

		assertEquals("Karamja", t.get("region").getAsString());
		assertEquals("Elite", t.get("tier").getAsString());
		assertTrue(t.get("completed").getAsBoolean());
	}

	@Test
	public void inventoryAndEquipmentSerialization()
	{
		AccountSnapshot snap = base()
			.section(SnapshotSectionType.INVENTORY, section(SnapshotSectionType.INVENTORY,
				SourceFreshness.COMPLETE, T, new InventoryData(
					Collections.singletonList(new ItemSnapshot(995, 100, 0)), 1)))
			.section(SnapshotSectionType.EQUIPMENT, section(SnapshotSectionType.EQUIPMENT,
				SourceFreshness.COMPLETE, T, new EquipmentData(
					Collections.singletonList(new ItemSnapshot(4151, 1, 3)))))
			.build();
		JsonObject secs = sections(serializer.toJson(snap));

		JsonObject inv = secs.getAsJsonObject("inventory").getAsJsonObject("data");
		assertEquals(1, inv.get("occupiedSlots").getAsInt());
		assertEquals(995, inv.getAsJsonArray("items").get(0).getAsJsonObject().get("id").getAsInt());

		JsonObject eq = secs.getAsJsonObject("equipment").getAsJsonObject("data");
		assertEquals(3, eq.getAsJsonArray("items").get(0).getAsJsonObject().get("slot").getAsInt());
	}

	@Test
	public void wealthNullablesSerializeAsJsonNull()
	{
		WealthData wealth = new WealthData(500L, null, null);
		AccountSnapshot snap = base().section(SnapshotSectionType.WEALTH, section(
			SnapshotSectionType.WEALTH, SourceFreshness.COMPLETE, T, wealth)).build();
		JsonObject data = sections(serializer.toJson(snap)).getAsJsonObject("wealth").getAsJsonObject("data");

		assertEquals(500L, data.get("gpOnHand").getAsLong());
		assertTrue(data.get("bankGp").isJsonNull());
		assertTrue(data.get("estimatedBankValue").isJsonNull());
	}

	// --- Bank freshness contract ---

	@Test
	public void bankCompleteSerialization()
	{
		BankData bank = new BankData(
			Collections.singletonList(new ItemSnapshot(995, 1_000_000, 0)), 1, 1_000_000L, 1_000_000L,
			BankData.SOURCE_BANK_INTERFACE);
		AccountSnapshot snap = base().section(SnapshotSectionType.BANK, section(
			SnapshotSectionType.BANK, SourceFreshness.COMPLETE, T, bank)).build();
		JsonObject s = sections(serializer.toJson(snap)).getAsJsonObject("bank");

		assertEquals("COMPLETE", s.get("freshness").getAsString());
		assertEquals("2026-08-29T21:40:31Z", s.get("collectedAt").getAsString());
		JsonObject data = s.getAsJsonObject("data");
		assertEquals(1, data.get("uniqueItems").getAsInt());
		assertEquals("BANK_INTERFACE", data.get("source").getAsString());
	}

	@Test
	public void bankStaleRetainsDataAndTimestamp()
	{
		Instant earlier = Instant.parse("2026-08-29T21:00:00Z");
		BankData bank = new BankData(Collections.singletonList(new ItemSnapshot(995, 42, 0)),
			1, 42L, 42L, BankData.SOURCE_BANK_INTERFACE);
		AccountSnapshot snap = base().section(SnapshotSectionType.BANK, section(
			SnapshotSectionType.BANK, SourceFreshness.STALE, earlier, bank)).build();
		JsonObject s = sections(serializer.toJson(snap)).getAsJsonObject("bank");

		assertEquals("STALE", s.get("freshness").getAsString());
		assertEquals("2026-08-29T21:00:00Z", s.get("collectedAt").getAsString());
		assertFalse(s.get("data").isJsonNull());
	}

	@Test
	public void bankUnavailableHasNullDataNotEmptyBank()
	{
		AccountSnapshot snap = base().section(SnapshotSectionType.BANK,
			CollectedSection.unavailable(SnapshotSectionType.BANK, T)).build();
		JsonObject s = sections(serializer.toJson(snap)).getAsJsonObject("bank");

		assertEquals("UNAVAILABLE", s.get("freshness").getAsString());
		assertTrue("Unavailable bank must be JSON null, not an empty object",
			s.get("data").isJsonNull());
	}

	@Test
	public void emptyOpenedBankIsCompleteWithEmptyItemsNotNull()
	{
		BankData emptyBank = new BankData(Collections.emptyList(), 0, 0L, 0L,
			BankData.SOURCE_BANK_INTERFACE);
		AccountSnapshot snap = base().section(SnapshotSectionType.BANK, section(
			SnapshotSectionType.BANK, SourceFreshness.COMPLETE, T, emptyBank)).build();
		JsonObject s = sections(serializer.toJson(snap)).getAsJsonObject("bank");

		assertEquals("COMPLETE", s.get("freshness").getAsString());
		assertFalse(s.get("data").isJsonNull());
		assertEquals(0, s.getAsJsonObject("data").getAsJsonArray("items").size());
	}

	@Test
	public void noCredentialOrAuthFieldsPresent()
	{
		AccountSnapshot snap = base()
			.section(SnapshotSectionType.IDENTITY, section(SnapshotSectionType.IDENTITY,
				SourceFreshness.COMPLETE, T, new IdentityData(true, 7L, "Zezima", "NORMAL", 1)))
			.build();
		String json = serializer.toJson(snap).toLowerCase();
		for (String banned : new String[]{"password", "cookie", "token", "oauth", "session",
			"credential", "jagex", "email", "filesystem", "\\path\\"})
		{
			assertFalse("payload must not contain " + banned, json.contains(banned));
		}
	}
}
