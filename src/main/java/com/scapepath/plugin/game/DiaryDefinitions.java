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
		/**
		 * Individual tasks within this tier, in stable declared order, or an empty list when
		 * RuneLite does not reliably expose per-task completion for this tier (see class doc).
		 * Never {@code null}.
		 */
		List<DiaryTaskDef> tasks;
	}

	/**
	 * One individual Achievement Diary task whose completion RuneLite exposes as a stable
	 * named varbit. The {@link #id} is the RuneLite {@code VarbitID} constant name (e.g.
	 * {@code "ATJUN_EASY_BANANA"}) — a machine-readable identifier that is stable across game
	 * updates and independent of the human-facing task description.
	 */
	@Value
	public static class DiaryTaskDef
	{
		/** Stable task identifier: the RuneLite {@code VarbitID} constant name. */
		String id;
		int varbitId;
		/** Task is complete when {@code getVarbitValue(varbitId) >= completeValue} (1 for these boolean flags). */
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
		// Karamja is the ONLY region for which RuneLite's gameval VarbitID enumerates every
		// individual task's completion varbit (the ATJUN_* constants). All other regions expose
		// only tier-level completion, so their task lists are intentionally empty (never faked).
		d.add(new DiaryDef("Karamja", EASY, VarbitID.ATJUN_EASY_DONE, 2, karamjaEasyTasks()));
		d.add(new DiaryDef("Karamja", MEDIUM, VarbitID.ATJUN_MED_DONE, 2, karamjaMediumTasks()));
		d.add(new DiaryDef("Karamja", HARD, VarbitID.ATJUN_HARD_DONE, 2, karamjaHardTasks()));
		d.add(new DiaryDef("Karamja", ELITE, VarbitID.KARAMJA_DIARY_ELITE_COMPLETE, 1, Collections.emptyList()));

		return Collections.unmodifiableList(d);
	}

	private static void addStandard(List<DiaryDef> d, String region, int easy, int medium, int hard, int elite)
	{
		final List<DiaryTaskDef> none = Collections.emptyList();
		d.add(new DiaryDef(region, EASY, easy, 1, none));
		d.add(new DiaryDef(region, MEDIUM, medium, 1, none));
		d.add(new DiaryDef(region, HARD, hard, 1, none));
		d.add(new DiaryDef(region, ELITE, elite, 1, none));
	}

	/** Build one boolean (>= 1) task from its RuneLite varbit id and constant name. */
	private static DiaryTaskDef task(String id, int varbitId)
	{
		return new DiaryTaskDef(id, varbitId, 1);
	}

	private static List<DiaryTaskDef> karamjaEasyTasks()
	{
		final List<DiaryTaskDef> t = new ArrayList<>();
		t.add(task("ATJUN_EASY_BANANA", VarbitID.ATJUN_EASY_BANANA));
		t.add(task("ATJUN_EASY_SWING", VarbitID.ATJUN_EASY_SWING));
		t.add(task("ATJUN_EASY_GOLD", VarbitID.ATJUN_EASY_GOLD));
		t.add(task("ATJUN_EASY_BOAT_SARIM", VarbitID.ATJUN_EASY_BOAT_SARIM));
		t.add(task("ATJUN_EASY_BOAT_ARDY", VarbitID.ATJUN_EASY_BOAT_ARDY));
		t.add(task("ATJUN_EASY_CAIRN", VarbitID.ATJUN_EASY_CAIRN));
		t.add(task("ATJUN_EASY_FISHING", VarbitID.ATJUN_EASY_FISHING));
		t.add(task("ATJUN_EASY_SEAWEED", VarbitID.ATJUN_EASY_SEAWEED));
		t.add(task("ATJUN_EASY_TZHAAR", VarbitID.ATJUN_EASY_TZHAAR));
		t.add(task("ATJUN_EASY_JOGRE", VarbitID.ATJUN_EASY_JOGRE));
		return Collections.unmodifiableList(t);
	}

	private static List<DiaryTaskDef> karamjaMediumTasks()
	{
		final List<DiaryTaskDef> t = new ArrayList<>();
		t.add(task("ATJUN_MED_AGILITY", VarbitID.ATJUN_MED_AGILITY));
		t.add(task("ATJUN_MED_VOLCANO", VarbitID.ATJUN_MED_VOLCANO));
		t.add(task("ATJUN_MED_CRANDOR", VarbitID.ATJUN_MED_CRANDOR));
		t.add(task("ATJUN_MED_CART", VarbitID.ATJUN_MED_CART));
		t.add(task("ATJUN_MED_CLEANUP", VarbitID.ATJUN_MED_CLEANUP));
		t.add(task("ATJUN_MED_SPIDER", VarbitID.ATJUN_MED_SPIDER));
		t.add(task("ATJUN_MED_TOPAZ", VarbitID.ATJUN_MED_TOPAZ));
		t.add(task("ATJUN_MED_TEAK", VarbitID.ATJUN_MED_TEAK));
		t.add(task("ATJUN_MED_MAHOGANY", VarbitID.ATJUN_MED_MAHOGANY));
		t.add(task("ATJUN_MED_KARAMBWAN", VarbitID.ATJUN_MED_KARAMBWAN));
		t.add(task("ATJUN_MED_MACHETTE", VarbitID.ATJUN_MED_MACHETTE));
		t.add(task("ATJUN_MED_GLIDER", VarbitID.ATJUN_MED_GLIDER));
		t.add(task("ATJUN_MED_FARMING", VarbitID.ATJUN_MED_FARMING));
		t.add(task("ATJUN_MED_GRAAHK", VarbitID.ATJUN_MED_GRAAHK));
		t.add(task("ATJUN_MED_SHILO_VINES", VarbitID.ATJUN_MED_SHILO_VINES));
		t.add(task("ATJUN_MED_SHILO_LAVA", VarbitID.ATJUN_MED_SHILO_LAVA));
		t.add(task("ATJUN_MED_SHILO_STAIRS", VarbitID.ATJUN_MED_SHILO_STAIRS));
		t.add(task("ATJUN_MED_KHAZARD", VarbitID.ATJUN_MED_KHAZARD));
		t.add(task("ATJUN_MED_CHARTER", VarbitID.ATJUN_MED_CHARTER));
		return Collections.unmodifiableList(t);
	}

	private static List<DiaryTaskDef> karamjaHardTasks()
	{
		final List<DiaryTaskDef> t = new ArrayList<>();
		t.add(task("ATJUN_HARD_FIGHTPITS", VarbitID.ATJUN_HARD_FIGHTPITS));
		t.add(task("ATJUN_HARD_FIGHTCAVE", VarbitID.ATJUN_HARD_FIGHTCAVE));
		t.add(task("ATJUN_HARD_OOMLIE", VarbitID.ATJUN_HARD_OOMLIE));
		t.add(task("ATJUN_HARD_NATURE", VarbitID.ATJUN_HARD_NATURE));
		t.add(task("ATJUN_HARD_KARAMBWAN", VarbitID.ATJUN_HARD_KARAMBWAN));
		t.add(task("ATJUN_HARD_DEATHWING", VarbitID.ATJUN_HARD_DEATHWING));
		t.add(task("ATJUN_HARD_XBOW", VarbitID.ATJUN_HARD_XBOW));
		t.add(task("ATJUN_HARD_PALM", VarbitID.ATJUN_HARD_PALM));
		t.add(task("ATJUN_HARD_DURADEL", VarbitID.ATJUN_HARD_DURADEL));
		t.add(task("ATJUN_HARD_DRAGON", VarbitID.ATJUN_HARD_DRAGON));
		return Collections.unmodifiableList(t);
	}

	private static Set<Integer> collectVarbitIds(List<DiaryDef> defs)
	{
		final Set<Integer> ids = new HashSet<>();
		for (DiaryDef def : defs)
		{
			ids.add(def.getVarbitId());
			for (DiaryTaskDef task : def.getTasks())
			{
				ids.add(task.getVarbitId());
			}
		}
		return Collections.unmodifiableSet(ids);
	}
}
