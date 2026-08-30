/*
 * Copyright (c) 2026, ScapePath
 * All rights reserved. BSD 2-Clause. See LICENSE file.
 */
package com.scapepath.plugin.snapshot.data;

import com.scapepath.plugin.snapshot.SectionData;
import java.util.List;
import lombok.Value;

/**
 * Typed payload for the {@code SKILLS} snapshot section.
 *
 * <p>Holds every collected skill plus aggregate figures. Combat level is computed with
 * RuneLite's {@link net.runelite.api.Experience#getCombatLevel} helper rather than a
 * re-implemented formula.</p>
 */
@Value
public class SkillsData implements SectionData
{
	/** One entry per collected skill (the current API's {@code Skill.values()}). */
	List<SkillData> skills;

	/** Sum of real skill levels (RuneLite {@code getTotalLevel}). */
	int totalLevel;

	/** Sum of all skill experience (RuneLite {@code getOverallExperience}). */
	long totalXp;

	/** Combat level via {@link net.runelite.api.Experience#getCombatLevel}. */
	int combatLevel;
}
