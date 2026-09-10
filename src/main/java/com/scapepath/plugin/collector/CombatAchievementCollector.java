/*
 * Copyright (c) 2026, ScapePath
 * All rights reserved. BSD 2-Clause. See LICENSE file.
 */
package com.scapepath.plugin.collector;

import com.scapepath.plugin.game.CombatAchievementDefinitions;
import com.scapepath.plugin.game.GameStateAccessor;
import com.scapepath.plugin.snapshot.CollectedSection;
import com.scapepath.plugin.snapshot.SnapshotSectionType;
import com.scapepath.plugin.snapshot.SourceFreshness;
import com.scapepath.plugin.snapshot.data.CombatAchievementData;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.inject.Singleton;

/**
 * Collects Combat Achievement completion from the live client.
 *
 * <p>Read-only and interface-free: reads each task's completion varbit and the per-tier
 * aggregate varbits via the centralized {@link CombatAchievementDefinitions} table. Every
 * completed task is reported by its stable id; the per-tier completed-task counts, status,
 * points threshold, and total points come straight from account varbits. Complete when
 * logged in; {@code UNAVAILABLE} otherwise. No static task metadata is fabricated &mdash;
 * tier/boss/point mappings and the full task universe are ScapePath's responsibility.</p>
 */
@Singleton
public class CombatAchievementCollector implements AccountDataCollector
{
	@Override
	public SnapshotSectionType type()
	{
		return SnapshotSectionType.COMBAT_ACHIEVEMENTS;
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

		// Completed tasks: only the ids that are done are emitted, so the payload scales with
		// progress. Order follows the definition order for deterministic serialization.
		final List<String> completedTaskIds = new ArrayList<>();
		for (CombatAchievementDefinitions.TaskDef task : CombatAchievementDefinitions.tasks())
		{
			if (game.getVarbitValue(task.getVarbitId()) >= CombatAchievementDefinitions.TASK_COMPLETE_MIN)
			{
				completedTaskIds.add(task.getId());
			}
		}

		// Per-tier aggregates straight from the game varbits (authoritative counts).
		final List<CombatAchievementData.TierProgress> tiers = new ArrayList<>();
		int completedFromTiers = 0;
		for (CombatAchievementDefinitions.TierDef def : CombatAchievementDefinitions.tiers())
		{
			final int completed = game.getVarbitValue(def.getCompletedCountVarbitId());
			completedFromTiers += completed;
			tiers.add(new CombatAchievementData.TierProgress(
				def.getTier(),
				completed,
				game.getVarbitValue(def.getStatusVarbitId()),
				game.getVarbitValue(def.getThresholdVarbitId())));
		}

		final CombatAchievementData data = new CombatAchievementData(
			game.getVarbitValue(CombatAchievementDefinitions.POINTS_VARBIT),
			completedFromTiers,
			CombatAchievementDefinitions.tasks().size(),
			Collections.unmodifiableList(tiers),
			Collections.unmodifiableList(completedTaskIds));

		return new CollectedSection(type(), SourceFreshness.COMPLETE, context.getSnapshotTime(), data);
	}
}
