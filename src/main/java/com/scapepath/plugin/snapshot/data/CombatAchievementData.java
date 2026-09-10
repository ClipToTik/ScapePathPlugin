/*
 * Copyright (c) 2026, ScapePath
 * All rights reserved. BSD 2-Clause. See LICENSE file.
 */
package com.scapepath.plugin.snapshot.data;

import com.scapepath.plugin.snapshot.SectionData;
import java.util.List;
import lombok.Value;

/**
 * Typed payload for the {@code COMBAT_ACHIEVEMENTS} section.
 *
 * <p>Carries only player <b>state</b> read from stable account varbits: total points, a
 * per-tier breakdown (completed-task count, raw status, points threshold), and the stable
 * ids of every completed task. It intentionally holds <b>no</b> static metadata &mdash; which
 * task belongs to which tier/boss, its point value, or the full task universe are ScapePath's
 * responsibility. The website derives "missing" tasks by subtracting {@link #completedTaskIds}
 * from its own canonical task list.</p>
 */
@Value
public class CombatAchievementData implements SectionData
{
	/** Total Combat Achievement points the account has earned. */
	int points;

	/** Sum of completed tasks across all tiers (game-authoritative). */
	int completedCount;

	/**
	 * Number of tasks this plugin build enumerates (the size of the known task set). Not the
	 * authoritative game total &mdash; it lets the website detect version drift between the
	 * plugin's task set and its own canonical list.
	 */
	int enumeratedTasks;

	/** Per-tier aggregate state, easiest first. */
	List<TierProgress> tiers;

	/** Stable ids (RuneLite {@code VarbitID} constant names) of every completed task. */
	List<String> completedTaskIds;

	/** Aggregate state for one Combat Achievement tier. */
	@Value
	public static class TierProgress
	{
		/** Stable tier id, e.g. {@code "EASY"}. */
		String tier;

		/** Number of completed tasks in this tier. */
		int completed;

		/** Raw tier status varbit value (game-defined; the website interprets it). */
		int status;

		/** Points threshold required to complete this tier. */
		int threshold;
	}
}
