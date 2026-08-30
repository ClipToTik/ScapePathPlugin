/*
 * Copyright (c) 2026, ScapePath
 * All rights reserved. BSD 2-Clause. See LICENSE file.
 */
package com.scapepath.plugin.collector;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.scapepath.plugin.game.DiaryDefinitions;
import com.scapepath.plugin.game.FakeGameStateAccessor;
import com.scapepath.plugin.snapshot.CollectedSection;
import com.scapepath.plugin.snapshot.SnapshotSectionType;
import com.scapepath.plugin.snapshot.SourceFreshness;
import com.scapepath.plugin.snapshot.data.AchievementDiaryData;
import com.scapepath.plugin.snapshot.data.DiaryTierSnapshot;
import java.util.HashSet;
import java.util.Set;
import net.runelite.api.gameval.VarbitID;
import org.junit.Test;

public class AchievementDiaryCollectorTest
{
	private final AchievementDiaryCollector collector = new AchievementDiaryCollector();

	private static FakeGameStateAccessor loggedIn()
	{
		return new FakeGameStateAccessor().loggedIn("Zezima", 302, 0, 1L);
	}

	private static DiaryTierSnapshot tier(AchievementDiaryData d, String region, String tierName)
	{
		return d.getTiers().stream()
			.filter(t -> t.getRegion().equals(region) && t.getTier().equals(tierName))
			.findFirst().orElseThrow(AssertionError::new);
	}

	@Test
	public void unavailableWhenLoggedOut()
	{
		CollectedSection s = collector.collect(CollectorContext.of(new FakeGameStateAccessor().loggedOut()));
		assertEquals(SnapshotSectionType.ACHIEVEMENT_DIARIES, s.getType());
		assertEquals(SourceFreshness.UNAVAILABLE, s.getFreshness());
		assertNull(s.getData());
	}

	@Test
	public void loggedInIsCompleteAndCoversEveryTier()
	{
		CollectedSection s = collector.collect(CollectorContext.of(loggedIn()));
		assertEquals(SourceFreshness.COMPLETE, s.getFreshness());

		AchievementDiaryData d = (AchievementDiaryData) s.getData();
		assertEquals(DiaryDefinitions.all().size(), d.getTiers().size());
		assertEquals(48, d.getTiers().size()); // 12 regions x 4 tiers
	}

	@Test
	public void allTwelveRegionsRepresentedNoneOmitted()
	{
		AchievementDiaryData d = (AchievementDiaryData) collector.collect(CollectorContext.of(loggedIn())).getData();
		Set<String> regions = new HashSet<>();
		for (DiaryTierSnapshot t : d.getTiers())
		{
			regions.add(t.getRegion());
		}
		assertEquals(12, regions.size());
		assertTrue(regions.contains("Karamja"));
		assertTrue(regions.contains("Ardougne"));
		assertTrue(regions.contains("Wilderness"));
	}

	@Test
	public void nothingCompleteByDefault()
	{
		AchievementDiaryData d = (AchievementDiaryData) collector.collect(CollectorContext.of(loggedIn())).getData();
		assertEquals(0, d.getCompletedTiers());
	}

	@Test
	public void standardTierCompletesAtValueOne()
	{
		FakeGameStateAccessor game = loggedIn()
			.varbit(VarbitID.ARDOUGNE_DIARY_EASY_COMPLETE, 1)
			.varbit(VarbitID.ARDOUGNE_DIARY_ELITE_COMPLETE, 1);
		AchievementDiaryData d = (AchievementDiaryData) collector.collect(CollectorContext.of(game)).getData();

		assertTrue(tier(d, "Ardougne", "Easy").isCompleted());
		assertTrue(tier(d, "Ardougne", "Elite").isCompleted());
		assertFalse(tier(d, "Ardougne", "Medium").isCompleted());
		assertEquals(2, d.getCompletedTiers());
	}

	@Test
	public void karamjaEasyMediumHardRequireValueTwo()
	{
		// Value 1 = "started" for Karamja E/M/H, so NOT complete.
		FakeGameStateAccessor started = loggedIn()
			.varbit(VarbitID.ATJUN_EASY_DONE, 1)
			.varbit(VarbitID.ATJUN_MED_DONE, 1)
			.varbit(VarbitID.ATJUN_HARD_DONE, 1);
		AchievementDiaryData d1 = (AchievementDiaryData) collector.collect(CollectorContext.of(started)).getData();
		assertFalse(tier(d1, "Karamja", "Easy").isCompleted());
		assertFalse(tier(d1, "Karamja", "Medium").isCompleted());
		assertFalse(tier(d1, "Karamja", "Hard").isCompleted());

		// Value 2 = complete.
		FakeGameStateAccessor done = loggedIn()
			.varbit(VarbitID.ATJUN_EASY_DONE, 2)
			.varbit(VarbitID.ATJUN_MED_DONE, 2)
			.varbit(VarbitID.ATJUN_HARD_DONE, 2)
			.varbit(VarbitID.KARAMJA_DIARY_ELITE_COMPLETE, 1);
		AchievementDiaryData d2 = (AchievementDiaryData) collector.collect(CollectorContext.of(done)).getData();
		assertTrue(tier(d2, "Karamja", "Easy").isCompleted());
		assertTrue(tier(d2, "Karamja", "Medium").isCompleted());
		assertTrue(tier(d2, "Karamja", "Hard").isCompleted());
		assertTrue(tier(d2, "Karamja", "Elite").isCompleted());
	}
}
