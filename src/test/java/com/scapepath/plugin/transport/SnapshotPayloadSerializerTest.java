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
import com.scapepath.plugin.snapshot.data.CollectionLogData;
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
import java.util.LinkedHashMap;
import java.util.Map;
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
	public void diaryTasksEmittedOnlyWhenPresent()
	{
		Map<String, Boolean> tasks = new LinkedHashMap<>();
		tasks.put("ATJUN_EASY_BANANA", true);
		tasks.put("ATJUN_EASY_GOLD", false);
		AchievementDiaryData diaries = new AchievementDiaryData(
			Arrays.asList(
				new DiaryTierSnapshot("Ardougne", "Easy", true, null),   // tier-only (V1 shape)
				new DiaryTierSnapshot("Karamja", "Easy", false, tasks)), // V2 task detail
			1, 2);
		AccountSnapshot snap = base()
			.section(SnapshotSectionType.ACHIEVEMENT_DIARIES, section(
				SnapshotSectionType.ACHIEVEMENT_DIARIES, SourceFreshness.COMPLETE, T, diaries))
			.build();

		JsonArray tiers = sections(serializer.toJson(snap)).getAsJsonObject("achievementDiaries")
			.getAsJsonObject("data").getAsJsonArray("tiers");

		JsonObject ardougne = tiers.get(0).getAsJsonObject();
		assertFalse("tier-only region must not carry a tasks key", ardougne.has("tasks"));

		JsonObject karamja = tiers.get(1).getAsJsonObject();
		assertTrue(karamja.has("tasks"));
		JsonObject kt = karamja.getAsJsonObject("tasks");
		assertTrue(kt.get("ATJUN_EASY_BANANA").getAsBoolean());
		assertFalse(kt.get("ATJUN_EASY_GOLD").getAsBoolean());
	}

	@Test
	public void collectionLogSectionSerializes()
	{
		CollectionLogData clog = new CollectionLogData(573, 1500,
			Arrays.asList(
				new CollectionLogData.TabCount("BOSSES", 100, 400),
				new CollectionLogData.TabCount("OTHER", 50, 300)));
		AccountSnapshot snap = base()
			.section(SnapshotSectionType.COLLECTION_LOG, section(
				SnapshotSectionType.COLLECTION_LOG, SourceFreshness.COMPLETE, T, clog))
			.build();

		JsonObject data = sections(serializer.toJson(snap)).getAsJsonObject("collectionLog")
			.getAsJsonObject("data");
		assertEquals(573, data.get("obtained").getAsInt());
		assertEquals(1500, data.get("total").getAsInt());
		JsonArray tabs = data.getAsJsonArray("tabs");
		assertEquals(2, tabs.size());
		assertEquals("BOSSES", tabs.get(0).getAsJsonObject().get("id").getAsString());
	}

	@Test
	public void combatAchievementsSectionSerializes()
	{
		com.scapepath.plugin.snapshot.data.CombatAchievementData ca =
			new com.scapepath.plugin.snapshot.data.CombatAchievementData(
				435, 33, 398,
				Arrays.asList(
					new com.scapepath.plugin.snapshot.data.CombatAchievementData.TierProgress(
						"EASY", 33, 2, 33),
					new com.scapepath.plugin.snapshot.data.CombatAchievementData.TierProgress(
						"MEDIUM", 0, 0, 115)),
				Arrays.asList(
					"CA_TASK_ARMADYL_KILLCOUNT_1_COMPLETED",
					"CA_TASK_BANDOS_KILLCOUNT_1_COMPLETED"));
		AccountSnapshot snap = base()
			.section(SnapshotSectionType.COMBAT_ACHIEVEMENTS, section(
				SnapshotSectionType.COMBAT_ACHIEVEMENTS, SourceFreshness.COMPLETE, T, ca))
			.build();

		JsonObject data = sections(serializer.toJson(snap)).getAsJsonObject("combatAchievements")
			.getAsJsonObject("data");
		assertEquals(435, data.get("points").getAsInt());
		assertEquals(33, data.get("completedCount").getAsInt());
		assertEquals(398, data.get("enumeratedTasks").getAsInt());

		JsonArray tiers = data.getAsJsonArray("tiers");
		assertEquals(2, tiers.size());
		JsonObject easy = tiers.get(0).getAsJsonObject();
		assertEquals("EASY", easy.get("tier").getAsString());
		assertEquals(33, easy.get("completed").getAsInt());
		assertEquals(2, easy.get("status").getAsInt());
		assertEquals(33, easy.get("threshold").getAsInt());

		JsonArray ids = data.getAsJsonArray("completedTaskIds");
		assertEquals(2, ids.size());
		assertEquals("CA_TASK_ARMADYL_KILLCOUNT_1_COMPLETED", ids.get(0).getAsString());
	}

	@Test
	public void deterministicForSameSnapshot()
	{
		AchievementDiaryData diaries = new AchievementDiaryData(
			Arrays.asList(new DiaryTierSnapshot("Ardougne", "Easy", true, null),
				new DiaryTierSnapshot("Karamja", "Elite", false, null)), 1, 2);
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

		// accountHash MUST be a JSON string, not a number, to avoid >2^53 precision loss.
		assertTrue("accountHash must be a JSON string", id.get("accountHash").getAsJsonPrimitive().isString());
		assertEquals("999", id.get("accountHash").getAsString());
		assertEquals("Zezima", id.get("rsn").getAsString());
		assertEquals("NORMAL", id.get("accountType").getAsString());
		assertEquals(330, id.get("world").getAsInt());
		assertTrue(id.get("loggedIn").getAsBoolean());
	}

	@Test
	public void accountHashPreservesFullPrecisionAsString()
	{
		// A real 64-bit accountHash exceeds 2^53; it must round-trip exactly as a string so
		// the server's pinned value (from link) equals the value seen at sync.
		final long bigHash = 6291812345678901234L;
		AccountSnapshot snap = base().section(SnapshotSectionType.IDENTITY, section(
			SnapshotSectionType.IDENTITY, SourceFreshness.COMPLETE, T,
			new IdentityData(true, bigHash, "Zezima", "NORMAL", 330))).build();
		JsonObject id = sections(serializer.toJson(snap)).getAsJsonObject("identity").getAsJsonObject("data");

		assertTrue(id.get("accountHash").getAsJsonPrimitive().isString());
		assertEquals(String.valueOf(bigHash), id.get("accountHash").getAsString());
	}

	@Test
	public void accountHashUnavailableEmitsNull()
	{
		AccountSnapshot snap = base().section(SnapshotSectionType.IDENTITY, section(
			SnapshotSectionType.IDENTITY, SourceFreshness.COMPLETE, T,
			new IdentityData(true, -1L, "Zezima", "NORMAL", 330))).build();
		JsonObject id = sections(serializer.toJson(snap)).getAsJsonObject("identity").getAsJsonObject("data");

		assertTrue("unavailable accountHash must be JSON null", id.get("accountHash").isJsonNull());
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
			Collections.singletonList(new DiaryTierSnapshot("Karamja", "Elite", true, null)), 1, 1);
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
