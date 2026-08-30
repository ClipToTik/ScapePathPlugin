/*
 * Copyright (c) 2026, ScapePath
 * All rights reserved. BSD 2-Clause. See LICENSE file.
 */
package com.scapepath.plugin.collector;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.scapepath.plugin.game.FakeGameStateAccessor;
import com.scapepath.plugin.snapshot.CollectedSection;
import com.scapepath.plugin.snapshot.SnapshotSectionType;
import com.scapepath.plugin.snapshot.SourceFreshness;
import com.scapepath.plugin.snapshot.data.QuestSnapshot;
import com.scapepath.plugin.snapshot.data.QuestsData;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.gameval.VarPlayerID;
import org.junit.Test;

public class QuestCollectorTest
{
	private final QuestCollector collector = new QuestCollector();

	private static FakeGameStateAccessor loggedIn()
	{
		return new FakeGameStateAccessor().loggedIn("Zezima", 302, 0, 1L);
	}

	@Test
	public void unavailableWhenLoggedOut()
	{
		CollectedSection s = collector.collect(CollectorContext.of(new FakeGameStateAccessor().loggedOut()));
		assertEquals(SnapshotSectionType.QUESTS, s.getType());
		assertEquals(SourceFreshness.UNAVAILABLE, s.getFreshness());
		assertNull(s.getData());
	}

	@Test
	public void loggedInIsCompleteAndCoversAllQuests()
	{
		QuestsData d = (QuestsData) collector.collect(CollectorContext.of(loggedIn())).getData();
		assertEquals(Quest.values().length, d.getTotalCount());
		assertEquals(Quest.values().length, d.getQuests().size());
	}

	@Test
	public void completedAndIncompleteRepresentedWithStableIds()
	{
		FakeGameStateAccessor game = loggedIn()
			.quest(Quest.COOKS_ASSISTANT, QuestState.FINISHED)
			.quest(Quest.CLOCK_TOWER, QuestState.IN_PROGRESS);
		// BLACK_KNIGHTS_FORTRESS left default NOT_STARTED.

		QuestsData d = (QuestsData) collector.collect(CollectorContext.of(game)).getData();

		QuestSnapshot cooks = find(d, Quest.COOKS_ASSISTANT);
		QuestSnapshot clock = find(d, Quest.CLOCK_TOWER);
		QuestSnapshot bkf = find(d, Quest.BLACK_KNIGHTS_FORTRESS);

		assertEquals("FINISHED", cooks.getState());
		assertEquals("IN_PROGRESS", clock.getState());
		assertEquals("NOT_STARTED", bkf.getState());

		// Stable id preserved from the RuneLite Quest enum.
		assertEquals(Quest.COOKS_ASSISTANT.getId(), cooks.getId());
		assertTrue(d.getCompletedCount() >= 1);
	}

	@Test
	public void questPointsRead()
	{
		FakeGameStateAccessor game = loggedIn().varp(VarPlayerID.QP, 185);
		QuestsData d = (QuestsData) collector.collect(CollectorContext.of(game)).getData();
		assertEquals(185, d.getQuestPoints());
	}

	@Test
	public void completedCountMatchesFinishedQuests()
	{
		FakeGameStateAccessor game = loggedIn()
			.quest(Quest.COOKS_ASSISTANT, QuestState.FINISHED)
			.quest(Quest.CLOCK_TOWER, QuestState.FINISHED);
		QuestsData d = (QuestsData) collector.collect(CollectorContext.of(game)).getData();
		assertEquals(2, d.getCompletedCount());
	}

	private static QuestSnapshot find(QuestsData d, Quest quest)
	{
		return d.getQuests().stream()
			.filter(q -> q.getId() == quest.getId())
			.findFirst().orElseThrow(AssertionError::new);
	}
}
