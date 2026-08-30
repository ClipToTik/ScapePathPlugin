/*
 * Copyright (c) 2026, ScapePath
 * All rights reserved. BSD 2-Clause. See LICENSE file.
 */
package com.scapepath.plugin.snapshot.data;

import lombok.Value;

/**
 * Completion state of one Achievement Diary tier within a region.
 *
 * <p>The game exposes only completed / not-completed per tier, so there is no
 * intermediate state.</p>
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
}
