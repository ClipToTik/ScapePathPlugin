/*
 * Copyright (c) 2026, ScapePath
 * All rights reserved. BSD 2-Clause. See LICENSE file.
 */
package com.scapepath.plugin.collector;

import com.scapepath.plugin.game.GameStateAccessor;
import com.scapepath.plugin.snapshot.CollectedSection;
import com.scapepath.plugin.snapshot.SnapshotSectionType;
import com.scapepath.plugin.snapshot.SourceFreshness;
import com.scapepath.plugin.snapshot.data.QuestSnapshot;
import com.scapepath.plugin.snapshot.data.QuestsData;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.gameval.VarPlayerID;

/**
 * Collects quest state from the live client.
 *
 * <p>Read-only and interface-free: quest state comes from {@link Quest#getState} and quest
 * points from {@code VarPlayerID.QP}. Works generically over {@link Quest#values()} using
 * the stable quest id as the source of truth. Complete when logged in; {@code UNAVAILABLE}
 * otherwise.</p>
 */
@Slf4j
@Singleton
public class QuestCollector implements AccountDataCollector
{
	@Override
	public SnapshotSectionType type()
	{
		return SnapshotSectionType.QUESTS;
	}

	@Override
	public boolean isReady(CollectorContext context)
	{
		return context.getGame().isLoggedIn();
	}

	@Override
	public CollectedSection collect(CollectorContext context)
	{
		final GameStateAccessor game = context.getGame();
		if (!game.isLoggedIn())
		{
			return CollectedSection.unavailable(type(), context.getSnapshotTime());
		}

		final List<QuestSnapshot> quests = new ArrayList<>();
		int completed = 0;
		for (Quest quest : Quest.values())
		{
			try
			{
				final QuestState state = game.getQuestState(quest);
				if (state == QuestState.FINISHED)
				{
					completed++;
				}
				quests.add(new QuestSnapshot(quest.getId(), quest.getName(), state.name()));
			}
			catch (RuntimeException ex)
			{
				// A quest the current client cannot resolve; skip rather than crash.
				log.debug("Skipping unreadable quest {}", quest, ex);
			}
		}

		final int questPoints = game.getVarpValue(VarPlayerID.QP);
		final QuestsData data = new QuestsData(
			Collections.unmodifiableList(quests), completed, quests.size(), questPoints);

		return new CollectedSection(type(), SourceFreshness.COMPLETE, context.getSnapshotTime(), data);
	}
}
