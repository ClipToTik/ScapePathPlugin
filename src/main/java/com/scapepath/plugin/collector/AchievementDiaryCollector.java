/*
 * Copyright (c) 2026, ScapePath
 * All rights reserved. BSD 2-Clause. See LICENSE file.
 */
package com.scapepath.plugin.collector;

import com.scapepath.plugin.game.DiaryDefinitions;
import com.scapepath.plugin.game.GameStateAccessor;
import com.scapepath.plugin.snapshot.CollectedSection;
import com.scapepath.plugin.snapshot.SnapshotSectionType;
import com.scapepath.plugin.snapshot.SourceFreshness;
import com.scapepath.plugin.snapshot.data.AchievementDiaryData;
import com.scapepath.plugin.snapshot.data.DiaryTierSnapshot;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Singleton;

/**
 * Collects Achievement Diary completion from the live client.
 *
 * <p>Read-only and interface-free: each tier's completion is read from its varbit via the
 * centralized {@link DiaryDefinitions} table (which also encodes Karamja's non-standard
 * completion threshold). Every region/tier is always represented &mdash; none are omitted.
 * Complete when logged in; {@code UNAVAILABLE} otherwise.</p>
 */
@Singleton
public class AchievementDiaryCollector implements AccountDataCollector
{
	@Override
	public SnapshotSectionType type()
	{
		return SnapshotSectionType.ACHIEVEMENT_DIARIES;
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

		final List<DiaryTierSnapshot> tiers = new ArrayList<>();
		int completed = 0;
		for (DiaryDefinitions.DiaryDef def : DiaryDefinitions.all())
		{
			final boolean done = game.getVarbitValue(def.getVarbitId()) >= def.getCompleteValue();
			if (done)
			{
				completed++;
			}

			// Per-task state only where RuneLite reliably exposes it; otherwise null (never
			// fabricated as all-false). Ordered so serialization is deterministic.
			Map<String, Boolean> tasks = null;
			if (!def.getTasks().isEmpty())
			{
				tasks = new LinkedHashMap<>();
				for (DiaryDefinitions.DiaryTaskDef taskDef : def.getTasks())
				{
					tasks.put(taskDef.getId(),
						game.getVarbitValue(taskDef.getVarbitId()) >= taskDef.getCompleteValue());
				}
				tasks = Collections.unmodifiableMap(tasks);
			}

			tiers.add(new DiaryTierSnapshot(def.getRegion(), def.getTier(), done, tasks));
		}

		final AchievementDiaryData data = new AchievementDiaryData(
			Collections.unmodifiableList(tiers), completed, tiers.size());

		return new CollectedSection(type(), SourceFreshness.COMPLETE, context.getSnapshotTime(), data);
	}
}
