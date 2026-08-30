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
		}
		assertEquals(ids.size(), DiaryDefinitions.varbitIds().size());
		assertTrue(DiaryDefinitions.varbitIds().containsAll(ids));
	}
}
