/*
 * Copyright (c) 2026, ScapePath
 * All rights reserved. BSD 2-Clause. See LICENSE file.
 */
package com.scapepath.plugin.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.Value;
import net.runelite.api.gameval.VarbitID;

/**
 * The single, auditable mapping of every OSRS Achievement Diary tier to the RuneLite
 * varbit that records its completion, verified against the resolved RuneLite API.
 *
 * <p>Eleven regions use the standard boolean {@code <REGION>_DIARY_<TIER>_COMPLETE}
 * varbits (complete when the value is &ge; 1). <b>Karamja is structurally different:</b>
 * its Easy/Medium/Hard tiers use the {@code ATJUN_*_DONE} varbits, which are complete
 * only at value {@code 2} (value {@code 1} means "started"); its Elite tier uses the
 * standard boolean varbit. These thresholds are captured explicitly per entry so the
 * mapping never has to guess.</p>
 */
public final class DiaryDefinitions
{
	/** One diary tier: which region/tier it is, its varbit, and the "complete" threshold. */
	@Value
	public static class DiaryDef
	{
		String region;
		String tier;
		int varbitId;
		/** Completion is {@code getVarbitValue(varbitId) >= completeValue}. */
		int completeValue;
	}

	public static final String EASY = "Easy";
	public static final String MEDIUM = "Medium";
	public static final String HARD = "Hard";
	public static final String ELITE = "Elite";

	private static final List<DiaryDef> DEFS = build();
	private static final Set<Integer> VARBIT_IDS = collectVarbitIds(DEFS);

	private DiaryDefinitions()
	{
	}

	/** All 48 diary tiers (12 regions x 4 tiers), never omitting a region. */
	public static List<DiaryDef> all()
	{
		return DEFS;
	}

	/** The set of varbit ids to watch for diary-completion changes. */
	public static Set<Integer> varbitIds()
	{
		return VARBIT_IDS;
	}

	private static List<DiaryDef> build()
	{
		final List<DiaryDef> d = new ArrayList<>();

		// --- Standard regions: complete when the boolean varbit is >= 1 ---
		addStandard(d, "Ardougne",
			VarbitID.ARDOUGNE_DIARY_EASY_COMPLETE, VarbitID.ARDOUGNE_DIARY_MEDIUM_COMPLETE,
			VarbitID.ARDOUGNE_DIARY_HARD_COMPLETE, VarbitID.ARDOUGNE_DIARY_ELITE_COMPLETE);
		addStandard(d, "Desert",
			VarbitID.DESERT_DIARY_EASY_COMPLETE, VarbitID.DESERT_DIARY_MEDIUM_COMPLETE,
			VarbitID.DESERT_DIARY_HARD_COMPLETE, VarbitID.DESERT_DIARY_ELITE_COMPLETE);
		addStandard(d, "Falador",
			VarbitID.FALADOR_DIARY_EASY_COMPLETE, VarbitID.FALADOR_DIARY_MEDIUM_COMPLETE,
			VarbitID.FALADOR_DIARY_HARD_COMPLETE, VarbitID.FALADOR_DIARY_ELITE_COMPLETE);
		addStandard(d, "Fremennik",
			VarbitID.FREMENNIK_DIARY_EASY_COMPLETE, VarbitID.FREMENNIK_DIARY_MEDIUM_COMPLETE,
			VarbitID.FREMENNIK_DIARY_HARD_COMPLETE, VarbitID.FREMENNIK_DIARY_ELITE_COMPLETE);
		addStandard(d, "Kandarin",
			VarbitID.KANDARIN_DIARY_EASY_COMPLETE, VarbitID.KANDARIN_DIARY_MEDIUM_COMPLETE,
			VarbitID.KANDARIN_DIARY_HARD_COMPLETE, VarbitID.KANDARIN_DIARY_ELITE_COMPLETE);
		addStandard(d, "Kourend",
			VarbitID.KOUREND_DIARY_EASY_COMPLETE, VarbitID.KOUREND_DIARY_MEDIUM_COMPLETE,
			VarbitID.KOUREND_DIARY_HARD_COMPLETE, VarbitID.KOUREND_DIARY_ELITE_COMPLETE);
		addStandard(d, "Lumbridge",
			VarbitID.LUMBRIDGE_DIARY_EASY_COMPLETE, VarbitID.LUMBRIDGE_DIARY_MEDIUM_COMPLETE,
			VarbitID.LUMBRIDGE_DIARY_HARD_COMPLETE, VarbitID.LUMBRIDGE_DIARY_ELITE_COMPLETE);
		addStandard(d, "Morytania",
			VarbitID.MORYTANIA_DIARY_EASY_COMPLETE, VarbitID.MORYTANIA_DIARY_MEDIUM_COMPLETE,
			VarbitID.MORYTANIA_DIARY_HARD_COMPLETE, VarbitID.MORYTANIA_DIARY_ELITE_COMPLETE);
		addStandard(d, "Varrock",
			VarbitID.VARROCK_DIARY_EASY_COMPLETE, VarbitID.VARROCK_DIARY_MEDIUM_COMPLETE,
			VarbitID.VARROCK_DIARY_HARD_COMPLETE, VarbitID.VARROCK_DIARY_ELITE_COMPLETE);
		addStandard(d, "Western Provinces",
			VarbitID.WESTERN_DIARY_EASY_COMPLETE, VarbitID.WESTERN_DIARY_MEDIUM_COMPLETE,
			VarbitID.WESTERN_DIARY_HARD_COMPLETE, VarbitID.WESTERN_DIARY_ELITE_COMPLETE);
		addStandard(d, "Wilderness",
			VarbitID.WILDERNESS_DIARY_EASY_COMPLETE, VarbitID.WILDERNESS_DIARY_MEDIUM_COMPLETE,
			VarbitID.WILDERNESS_DIARY_HARD_COMPLETE, VarbitID.WILDERNESS_DIARY_ELITE_COMPLETE);

		// --- Karamja: Easy/Medium/Hard complete at value 2 (1 = started); Elite boolean ---
		d.add(new DiaryDef("Karamja", EASY, VarbitID.ATJUN_EASY_DONE, 2));
		d.add(new DiaryDef("Karamja", MEDIUM, VarbitID.ATJUN_MED_DONE, 2));
		d.add(new DiaryDef("Karamja", HARD, VarbitID.ATJUN_HARD_DONE, 2));
		d.add(new DiaryDef("Karamja", ELITE, VarbitID.KARAMJA_DIARY_ELITE_COMPLETE, 1));

		return Collections.unmodifiableList(d);
	}

	private static void addStandard(List<DiaryDef> d, String region, int easy, int medium, int hard, int elite)
	{
		d.add(new DiaryDef(region, EASY, easy, 1));
		d.add(new DiaryDef(region, MEDIUM, medium, 1));
		d.add(new DiaryDef(region, HARD, hard, 1));
		d.add(new DiaryDef(region, ELITE, elite, 1));
	}

	private static Set<Integer> collectVarbitIds(List<DiaryDef> defs)
	{
		final Set<Integer> ids = new HashSet<>();
		for (DiaryDef def : defs)
		{
			ids.add(def.getVarbitId());
		}
		return Collections.unmodifiableSet(ids);
	}
}
