/*
 * Copyright (c) 2026, ScapePath
 * All rights reserved. BSD 2-Clause. See LICENSE file.
 */
package com.scapepath.plugin.collector;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.scapepath.plugin.game.CombatAchievementDefinitions;
import com.scapepath.plugin.game.FakeGameStateAccessor;
import com.scapepath.plugin.snapshot.CollectedSection;
import com.scapepath.plugin.snapshot.SnapshotSectionType;
import com.scapepath.plugin.snapshot.SourceFreshness;
import com.scapepath.plugin.snapshot.data.CombatAchievementData;
import net.runelite.api.gameval.VarbitID;
import org.junit.Test;

public class CombatAchievementCollectorTest
{
	private final CombatAchievementCollector collector = new CombatAchievementCollector();

	private static FakeGameStateAccessor loggedIn()
	{
		return new FakeGameStateAccessor().loggedIn("Zezima", 302, 0, 1L);
	}

	@Test
	public void unavailableWhenLoggedOut()
	{
		CollectedSection s = collector.collect(CollectorContext.of(new FakeGameStateAccessor().loggedOut()));
		assertEquals(SnapshotSectionType.COMBAT_ACHIEVEMENTS, s.getType());
		assertEquals(SourceFreshness.UNAVAILABLE, s.getFreshness());
		assertNull(s.getData());
	}

	@Test
	public void freshAccountReadsAllZeroButComplete()
	{
		// Logged in, no CA varbits set: a genuine "0 completed" is real state, not unknown.
		CollectedSection s = collector.collect(CollectorContext.of(loggedIn()));
		assertEquals(SourceFreshness.COMPLETE, s.getFreshness());

		CombatAchievementData d = (CombatAchievementData) s.getData();
		assertEquals(0, d.getPoints());
		assertEquals(0, d.getCompletedCount());
		assertTrue(d.getCompletedTaskIds().isEmpty());
		assertEquals(6, d.getTiers().size());
		assertEquals(CombatAchievementDefinitions.tasks().size(), d.getEnumeratedTasks());
	}

	@Test
	public void completedTasksAndTierAggregatesAreReported()
	{
		FakeGameStateAccessor game = loggedIn()
			.varbit(VarbitID.CA_POINTS, 435)
			.varbit(VarbitID.CA_TASK_BANDOS_KILLCOUNT_1_COMPLETED, 1)
			.varbit(VarbitID.CA_TASK_ARMADYL_KILLCOUNT_1_COMPLETED, 1)
			.varbit(VarbitID.CA_TOTAL_TASKS_COMPLETED_EASY, 33)
			.varbit(VarbitID.CA_TIER_STATUS_EASY, 2)
			.varbit(VarbitID.CA_THRESHOLD_EASY, 33);

		CombatAchievementData d = (CombatAchievementData)
			collector.collect(CollectorContext.of(game)).getData();

		assertEquals(435, d.getPoints());
		assertTrue(d.getCompletedTaskIds().contains("CA_TASK_BANDOS_KILLCOUNT_1_COMPLETED"));
		assertTrue(d.getCompletedTaskIds().contains("CA_TASK_ARMADYL_KILLCOUNT_1_COMPLETED"));
		assertFalse(d.getCompletedTaskIds().contains("CA_TASK_BANDOS_KILLCOUNT_2_COMPLETED"));

		CombatAchievementData.TierProgress easy = d.getTiers().get(0);
		assertEquals("EASY", easy.getTier());
		assertEquals(33, easy.getCompleted());
		assertEquals(2, easy.getStatus());
		assertEquals(33, easy.getThreshold());

		// completedCount is the sum of the per-tier game counts.
		assertEquals(33, d.getCompletedCount());
	}

	@Test
	public void tierOrderIsEasiestFirst()
	{
		CombatAchievementData d = (CombatAchievementData)
			collector.collect(CollectorContext.of(loggedIn())).getData();
		assertEquals("EASY", d.getTiers().get(0).getTier());
		assertEquals("GRANDMASTER", d.getTiers().get(d.getTiers().size() - 1).getTier());
	}

	@Test
	public void enumeratesExpectedTaskCount()
	{
		// Guards against accidental additions/removals in the generated definitions.
		assertEquals(398, CombatAchievementDefinitions.tasks().size());
	}
}
