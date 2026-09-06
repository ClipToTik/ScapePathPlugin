/*
 * Copyright (c) 2026, ScapePath
 * All rights reserved. BSD 2-Clause. See LICENSE file.
 */
package com.scapepath.plugin.game;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import com.scapepath.plugin.game.DiaryDefinitions.DiaryDef;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;

public class DiaryDefinitionsTest
{
	@Test
	public void hasTwelveRegionsFourTiersEach()
	{
		assertEquals(48, DiaryDefinitions.all().size());

		Set<String> regions = new HashSet<>();
		for (DiaryDef def : DiaryDefinitions.all())
		{
			regions.add(def.getRegion());
		}
		assertEquals(12, regions.size());
	}

	@Test
	public void everyRegionHasAllFourTiers()
	{
		Set<String> pairs = new HashSet<>();
		for (DiaryDef def : DiaryDefinitions.all())
		{
			pairs.add(def.getRegion() + "/" + def.getTier());
		}
		// 12 regions * 4 distinct tiers, no duplicates/omissions.
		assertEquals(48, pairs.size());
	}

	@Test
	public void karamjaThresholdsAreExplicit()
	{
		for (DiaryDef def : DiaryDefinitions.all())
		{
			if (def.getRegion().equals("Karamja"))
			{
				int expected = def.getTier().equals("Elite") ? 1 : 2;
				assertEquals("Karamja " + def.getTier(), expected, def.getCompleteValue());
			}
			else
			{
				assertEquals("Standard regions complete at 1", 1, def.getCompleteValue());
			}
		}
	}

	@Test
	public void varbitIdSetMatchesDistinctVarbits()
	{
		Set<Integer> ids = new HashSet<>();
		for (DiaryDef def : DiaryDefinitions.all())
		{
			ids.add(def.getVarbitId());
			// varbitIds() also watches every exposed per-task varbit (V2), so include them.
			for (DiaryDefinitions.DiaryTaskDef task : def.getTasks())
			{
				ids.add(task.getVarbitId());
			}
		}
		assertEquals(ids.size(), DiaryDefinitions.varbitIds().size());
		assertTrue(DiaryDefinitions.varbitIds().containsAll(ids));
	}

	@Test
	public void onlyKaramjaExposesTasksAndTaskCountsMatchApi()
	{
		int easy = 0, medium = 0, hard = 0;
		for (DiaryDef def : DiaryDefinitions.all())
		{
			if (!def.getRegion().equals("Karamja"))
			{
				assertTrue(def.getRegion() + " " + def.getTier() + " must expose no tasks",
					def.getTasks().isEmpty());
				continue;
			}
			switch (def.getTier())
			{
				case "Easy": easy = def.getTasks().size(); break;
				case "Medium": medium = def.getTasks().size(); break;
				case "Hard": hard = def.getTasks().size(); break;
				default: assertTrue("Karamja Elite exposes no tasks", def.getTasks().isEmpty());
			}
		}
		assertEquals(10, easy);
		assertEquals(19, medium);
		assertEquals(10, hard);
	}
}
