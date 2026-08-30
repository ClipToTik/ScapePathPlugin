/*
 * Copyright (c) 2026, ScapePath
 * All rights reserved. BSD 2-Clause. See LICENSE file.
 */
package com.scapepath.plugin.collector;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.scapepath.plugin.game.FakeGameStateAccessor;
import com.scapepath.plugin.snapshot.CollectedSection;
import com.scapepath.plugin.snapshot.SnapshotSectionType;
import com.scapepath.plugin.snapshot.SourceFreshness;
import com.scapepath.plugin.snapshot.data.SkillData;
import com.scapepath.plugin.snapshot.data.SkillsData;
import java.util.HashSet;
import java.util.Set;
import net.runelite.api.Experience;
import net.runelite.api.Skill;
import org.junit.Test;

public class SkillsCollectorTest
{
	private final SkillsCollector collector = new SkillsCollector();

	private static FakeGameStateAccessor loggedIn()
	{
		return new FakeGameStateAccessor().loggedIn("Zezima", 302, 0, 1L);
	}

	@Test
	public void collectsEveryRealSkill()
	{
		FakeGameStateAccessor game = loggedIn().allSkills(1, 0);
		SkillsData data = (SkillsData) collector.collect(CollectorContext.of(game)).getData();

		// Current API: Skill.values() is the real skills (legacy OVERALL is not in it).
		assertEquals(Skill.values().length, data.getSkills().size());

		Set<String> names = new HashSet<>();
		for (SkillData s : data.getSkills())
		{
			names.add(s.getName());
		}
		assertFalse("OVERALL is not a real skill", names.contains("Overall"));
		assertTrue(names.contains(Skill.ATTACK.getName()));
		assertTrue(names.contains(Skill.CONSTRUCTION.getName()));
	}

	@Test
	public void mapsLevelAndXpCorrectly()
	{
		FakeGameStateAccessor game = loggedIn().allSkills(1, 0)
			.skill(Skill.FISHING, 76, 1_336_443);
		SkillsData data = (SkillsData) collector.collect(CollectorContext.of(game)).getData();

		SkillData fishing = data.getSkills().stream()
			.filter(s -> s.getName().equals(Skill.FISHING.getName()))
			.findFirst().orElseThrow(AssertionError::new);
		assertEquals(76, fishing.getLevel());
		assertEquals(1_336_443, fishing.getXp());
	}

	@Test
	public void combatLevelMatchesRuneLiteHelper()
	{
		FakeGameStateAccessor game = loggedIn().allSkills(1, 0)
			.skill(Skill.ATTACK, 60, 0)
			.skill(Skill.STRENGTH, 60, 0)
			.skill(Skill.DEFENCE, 60, 0)
			.skill(Skill.HITPOINTS, 60, 0)
			.skill(Skill.RANGED, 50, 0)
			.skill(Skill.MAGIC, 50, 0)
			.skill(Skill.PRAYER, 43, 0);

		int expected = Experience.getCombatLevel(60, 60, 60, 60, 50, 50, 43);
		SkillsData data = (SkillsData) collector.collect(CollectorContext.of(game)).getData();
		assertEquals(expected, data.getCombatLevel());
	}

	@Test
	public void aggregatesTotalLevelAndXp()
	{
		FakeGameStateAccessor game = loggedIn().allSkills(10, 1154);
		SkillsData data = (SkillsData) collector.collect(CollectorContext.of(game)).getData();

		int realSkills = Skill.values().length;
		assertEquals(realSkills * 10, data.getTotalLevel());
		assertEquals((long) realSkills * 1154, data.getTotalXp());
	}

	@Test
	public void unavailableWhenLoggedOut()
	{
		FakeGameStateAccessor game = new FakeGameStateAccessor().loggedOut();
		CollectorContext ctx = CollectorContext.of(game);

		assertFalse(collector.isReady(ctx));
		CollectedSection section = collector.collect(ctx);
		assertEquals(SourceFreshness.UNAVAILABLE, section.getFreshness());
		assertNull(section.getData());
	}
}
