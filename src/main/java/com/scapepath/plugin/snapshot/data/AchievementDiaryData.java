/*
 * Copyright (c) 2026, ScapePath
 * All rights reserved. BSD 2-Clause. See LICENSE file.
 */
package com.scapepath.plugin.snapshot.data;

import com.scapepath.plugin.snapshot.SectionData;
import java.util.List;
import lombok.Value;

/**
 * Typed payload for the {@code ACHIEVEMENT_DIARIES} section: every region/tier and its
 * completion state, plus aggregates.
 */
@Value
public class AchievementDiaryData implements SectionData
{
	/** One entry per region/tier (12 regions x 4 tiers). */
	List<DiaryTierSnapshot> tiers;

	/** Number of completed tiers. */
	int completedTiers;

	/** Total number of tiers represented. */
	int totalTiers;
}
