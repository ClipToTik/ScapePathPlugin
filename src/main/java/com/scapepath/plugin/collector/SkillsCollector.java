/*
 * Copyright (c) 2026, ScapePath
 * All rights reserved. BSD 2-Clause. See LICENSE file.
 */
package com.scapepath.plugin.collector;

import com.scapepath.plugin.game.GameStateAccessor;
import com.scapepath.plugin.snapshot.CollectedSection;
import com.scapepath.plugin.snapshot.SnapshotSectionType;
import com.scapepath.plugin.snapshot.SourceFreshness;
import com.scapepath.plugin.snapshot.data.SkillData;
import com.scapepath.plugin.snapshot.data.SkillsData;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Experience;
import net.runelite.api.Skill;

/**
 * Collects every normal OSRS skill (level + XP), plus total level, total XP, and combat
 * level.
 *
 * <p>Works generically over {@link Skill#values()} rather than hardcoding skills. In the
 * current API {@code values()} contains the real skills only (the legacy {@code OVERALL}
 * pseudo-skill is deprecated and no longer part of the array). Each skill read is guarded
 * so that a client which does not yet fully expose a newly-added enum value (e.g. the
 * upcoming Sailing skill) is skipped rather than crashing the collector. Combat level
 * uses RuneLite's {@link Experience#getCombatLevel} helper.</p>
 */
@Slf4j
@Singleton
public class SkillsCollector implements AccountDataCollector
{
	@Override
	public SnapshotSectionType type()
	{
		return SnapshotSectionType.SKILLS;
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

		final List<SkillData> skills = new ArrayList<>();
		for (Skill skill : Skill.values())
		{
			try
			{
				skills.add(new SkillData(
					skill.getName(),
					game.getRealSkillLevel(skill),
					game.getSkillExperience(skill)
				));
			}
			catch (RuntimeException ex)
			{
				// A skill the current client does not expose yet (e.g. unreleased). Skip it.
				log.debug("Skipping unreadable skill {}", skill, ex);
			}
		}

		final SkillsData data = new SkillsData(
			Collections.unmodifiableList(skills),
			game.getTotalLevel(),
			game.getOverallExperience(),
			computeCombatLevel(game)
		);

		return new CollectedSection(type(), SourceFreshness.COMPLETE, context.getSnapshotTime(), data);
	}

	private static int computeCombatLevel(GameStateAccessor game)
	{
		return Experience.getCombatLevel(
			game.getRealSkillLevel(Skill.ATTACK),
			game.getRealSkillLevel(Skill.STRENGTH),
			game.getRealSkillLevel(Skill.DEFENCE),
			game.getRealSkillLevel(Skill.HITPOINTS),
			game.getRealSkillLevel(Skill.MAGIC),
			game.getRealSkillLevel(Skill.RANGED),
			game.getRealSkillLevel(Skill.PRAYER)
		);
	}
}
