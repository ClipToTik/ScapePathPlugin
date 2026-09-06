/*
 * Copyright (c) 2026, ScapePath
 * All rights reserved. BSD 2-Clause. See LICENSE file.
 */
package com.scapepath.plugin.snapshot.data;

import java.util.Map;
import javax.annotation.Nullable;
import lombok.Value;

/**
 * Completion state of one Achievement Diary tier within a region.
 *
 * <p>{@link #completed} is the tier-level flag (present for every tier — backward
 * compatible with the V1 payload). {@link #tasks}, when non-null, adds exact per-task
 * completion keyed by the task's stable RuneLite identifier; it is {@code null} for tiers
 * where RuneLite does not reliably expose per-task state, so "no task data" stays distinct
 * from "no tasks completed".</p>
 */
@Value
public class DiaryTierSnapshot
{
	/** Region name, e.g. {@code "Ardougne"}, {@code "Karamja"}. */
	String region;

	/** Tier name: Easy / Medium / Hard / Elite. */
	String tier;

	/** {@code true} if this tier is fully complete. */
	boolean completed;

	/**
	 * Ordered map of stable task id → completed, or {@code null} when per-task state is not
	 * available for this tier. Never an empty map for an "available but zero" tier — the
	 * collector only sets this when the tier genuinely exposes tasks.
	 */
	@Nullable
	Map<String, Boolean> tasks;
}
