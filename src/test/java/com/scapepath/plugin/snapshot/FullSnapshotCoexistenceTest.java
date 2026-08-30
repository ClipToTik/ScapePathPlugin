/*
 * Copyright (c) 2026, ScapePath
 * All rights reserved. BSD 2-Clause. See LICENSE file.
 */
package com.scapepath.plugin.snapshot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.scapepath.plugin.collector.AchievementDiaryCollector;
import com.scapepath.plugin.collector.BankCollector;
import com.scapepath.plugin.collector.CollectorRegistry;
import com.scapepath.plugin.collector.EquipmentCollector;
import com.scapepath.plugin.collector.IdentityCollector;
import com.scapepath.plugin.collector.InventoryCollector;
import com.scapepath.plugin.collector.QuestCollector;
import com.scapepath.plugin.collector.SkillsCollector;
import com.scapepath.plugin.collector.WealthCollector;
import com.scapepath.plugin.game.BankTracker;
import com.scapepath.plugin.game.FakeGameStateAccessor;
import com.scapepath.plugin.snapshot.data.IdentityData;
import com.scapepath.plugin.snapshot.data.InventoryData;
import com.scapepath.plugin.snapshot.data.SkillsData;
import java.time.Instant;
import java.util.Collections;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.Skill;
import com.scapepath.plugin.snapshot.data.ItemSnapshot;
import org.junit.Test;

public class FullSnapshotCoexistenceTest
{
	private static CollectorRegistry registryWithAll(BankTracker tracker)
	{
		CollectorRegistry r = new CollectorRegistry();
		r.register(new IdentityCollector());
		r.register(new SkillsCollector());
		r.register(new QuestCollector());
		r.register(new AchievementDiaryCollector());
		r.register(new InventoryCollector());
		r.register(new EquipmentCollector());
		r.register(new BankCollector(tracker));
		r.register(new WealthCollector(tracker));
		return r;
	}

	@Test
	public void allSectionsCoexistAndExistingOnesUnaffected()
	{
		BankTracker tracker = new BankTracker();
		tracker.updateItems(Collections.singletonList(new ItemSnapshot(995, 1_000_000, 0)), Instant.now());
		tracker.setOpen(true);

		FakeGameStateAccessor game = new FakeGameStateAccessor()
			.loggedIn("Zezima", 302, 0, 99L)
			.allSkills(1, 0)
			.skill(Skill.HITPOINTS, 10, 1154)
			.container(InventoryID.INV, Collections.singletonList(new ItemSnapshot(995, 500, 0)))
			.container(InventoryID.WORN, Collections.singletonList(new ItemSnapshot(4151, 1, 3)))
			.price(995, 1);

		AccountSnapshot snap = registryWithAll(tracker).buildSnapshot(game);

		// Eight sections present, no duplicates.
		assertEquals(8, snap.getSections().size());
		for (SnapshotSectionType t : new SnapshotSectionType[]{
			SnapshotSectionType.IDENTITY, SnapshotSectionType.SKILLS, SnapshotSectionType.QUESTS,
			SnapshotSectionType.ACHIEVEMENT_DIARIES, SnapshotSectionType.INVENTORY,
			SnapshotSectionType.EQUIPMENT, SnapshotSectionType.BANK, SnapshotSectionType.WEALTH})
		{
			assertTrue("missing " + t, snap.getSection(t) != null);
		}

		// Existing collectors still correct.
		assertEquals("Zezima", ((IdentityData) snap.getSection(SnapshotSectionType.IDENTITY).getData()).getRsn());
		assertEquals(10, ((SkillsData) snap.getSection(SnapshotSectionType.SKILLS).getData()).getSkills().stream()
			.filter(s -> s.getName().equals(Skill.HITPOINTS.getName())).findFirst().orElseThrow(AssertionError::new).getLevel());
		assertEquals(1, ((InventoryData) snap.getSection(SnapshotSectionType.INVENTORY).getData()).getOccupiedSlots());
	}

	@Test
	public void loggedOutMarksNewSectionsUnavailableAndBankUnknown()
	{
		BankTracker tracker = new BankTracker();
		AccountSnapshot snap = registryWithAll(tracker).buildSnapshot(new FakeGameStateAccessor().loggedOut());

		assertNull(snap.getRsn());
		assertEquals(SourceFreshness.UNAVAILABLE, snap.getSection(SnapshotSectionType.QUESTS).getFreshness());
		assertEquals(SourceFreshness.UNAVAILABLE, snap.getSection(SnapshotSectionType.ACHIEVEMENT_DIARIES).getFreshness());
		assertEquals(SourceFreshness.UNAVAILABLE, snap.getSection(SnapshotSectionType.INVENTORY).getFreshness());
		assertEquals(SourceFreshness.UNAVAILABLE, snap.getSection(SnapshotSectionType.EQUIPMENT).getFreshness());
		assertEquals(SourceFreshness.UNAVAILABLE, snap.getSection(SnapshotSectionType.BANK).getFreshness());
		assertNull(snap.getSection(SnapshotSectionType.BANK).getData());
	}
}
