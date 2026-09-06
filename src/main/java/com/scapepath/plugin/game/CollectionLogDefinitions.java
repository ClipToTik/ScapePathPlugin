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
import net.runelite.api.gameval.VarPlayerID;

/**
 * The auditable mapping of Collection Log progress counts to the stable RuneLite
 * {@code VarPlayerID} constants that store them.
 *
 * <p>RuneLite's {@code gameval} does not expose a complete, stable per-item identifier map
 * for the ~1,600 Collection Log items (only a small handful are named), and reliable
 * per-item obtained state is interface-gated (only populated after the player opens the
 * Collection Log). This foundation therefore captures the authoritative, compact aggregate
 * the game stores account-side: the overall obtained/total slot counts and the five tab
 * counts. Each is a stable RuneLite VarPlayer, so no raw magic numbers are used.</p>
 *
 * <p>These counts read {@code 0} until the account's Collection Log data has been synced by
 * the game. Callers treat a non-positive {@code overallMax} as "not yet available" rather
 * than "zero obtained", keeping "unknown" distinct from "empty".</p>
 */
public final class CollectionLogDefinitions
{
	/** One Collection Log tab and the VarPlayers holding its obtained/total counts. */
	@Value
	public static class TabDef
	{
		/** Stable tab identifier, e.g. {@code "BOSSES"}. */
		String id;
		int obtainedVarpId;
		int maxVarpId;
	}

	/** VarPlayer holding the overall obtained slot count across every tab. */
	public static final int OVERALL_OBTAINED_VARP = VarPlayerID.COLLECTION_COUNT;
	/** VarPlayer holding the overall total slot count (0 until the log data is synced). */
	public static final int OVERALL_MAX_VARP = VarPlayerID.COLLECTION_COUNT_MAX;

	private static final List<TabDef> TABS = build();
	private static final Set<Integer> VARP_IDS = collectVarpIds();

	private CollectionLogDefinitions()
	{
	}

	/** The five Collection Log tabs, in a fixed order. */
	public static List<TabDef> tabs()
	{
		return TABS;
	}

	/** Every VarPlayer id this section reads — used to trigger targeted snapshot rebuilds. */
	public static Set<Integer> varpIds()
	{
		return VARP_IDS;
	}

	private static List<TabDef> build()
	{
		final List<TabDef> t = new ArrayList<>();
		t.add(new TabDef("BOSSES", VarPlayerID.COLLECTION_COUNT_BOSSES, VarPlayerID.COLLECTION_COUNT_BOSSES_MAX));
		t.add(new TabDef("RAIDS", VarPlayerID.COLLECTION_COUNT_RAIDS, VarPlayerID.COLLECTION_COUNT_RAIDS_MAX));
		t.add(new TabDef("CLUES", VarPlayerID.COLLECTION_COUNT_CLUES, VarPlayerID.COLLECTION_COUNT_CLUES_MAX));
		t.add(new TabDef("MINIGAMES", VarPlayerID.COLLECTION_COUNT_MINIGAMES, VarPlayerID.COLLECTION_COUNT_MINIGAMES_MAX));
		t.add(new TabDef("OTHER", VarPlayerID.COLLECTION_COUNT_OTHER, VarPlayerID.COLLECTION_COUNT_OTHER_MAX));
		return Collections.unmodifiableList(t);
	}

	private static Set<Integer> collectVarpIds()
	{
		final Set<Integer> ids = new HashSet<>();
		ids.add(OVERALL_OBTAINED_VARP);
		ids.add(OVERALL_MAX_VARP);
		for (TabDef tab : TABS)
		{
			ids.add(tab.getObtainedVarpId());
			ids.add(tab.getMaxVarpId());
		}
		return Collections.unmodifiableSet(ids);
	}
}
