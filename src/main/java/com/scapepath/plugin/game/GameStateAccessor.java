/*
 * Copyright (c) 2026, ScapePath
 * All rights reserved. BSD 2-Clause. See LICENSE file.
 */
package com.scapepath.plugin.game;

import com.scapepath.plugin.snapshot.data.ItemSnapshot;
import java.util.List;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;

/**
 * Narrow, read-only abstraction over the RuneLite {@code Client} for the exact account
 * state ScapePath reads.
 *
 * <p>This is the single seam through which the plugin touches the game client. Keeping
 * it here means:</p>
 * <ul>
 *   <li>{@code Client} access is not scattered across collectors;</li>
 *   <li>collectors depend on this interface, so they can be unit-tested with a fake and
 *       never require a real RuneLite login;</li>
 *   <li>the surface of what we read is auditable in one place.</li>
 * </ul>
 *
 * <p><b>Threading:</b> implementations backed by the live client read mutable game
 * state and therefore must be called on the RuneLite client thread. Callers (the
 * plugin/orchestration layer) are responsible for that; implementations do not
 * schedule work themselves.</p>
 *
 * <p>This interface is strictly read-only: there is intentionally no method that can
 * alter game state, send input, or perform I/O.</p>
 */
public interface GameStateAccessor
{
	/** {@code true} only when fully logged in with a local player available. */
	boolean isLoggedIn();

	/**
	 * Stable per-account identifier, or {@code -1} when logged out / unavailable.
	 * This is not personal information and not a credential.
	 */
	long getAccountHash();

	/** Account display name (RSN), or {@code null} when logged out / unavailable. */
	String getPlayerName();

	/**
	 * Raw {@code Varbits.ACCOUNT_TYPE} value (0 = normal, 1 = ironman, …), or {@code -1}
	 * when logged out / unavailable. Interpreted via {@link OsrsAccountType#fromVarbit}.
	 */
	int getAccountTypeVarbit();

	/** Current world number, or {@code 0} when unavailable. */
	int getWorld();

	/** Real (unboosted) level for a skill. */
	int getRealSkillLevel(Skill skill);

	/** Total experience in a skill. */
	int getSkillExperience(Skill skill);

	/** Sum of real skill levels. */
	int getTotalLevel();

	/** Sum of all skill experience. */
	long getOverallExperience();

	/**
	 * Read an item container by its {@code gameval.InventoryID} id, returning one
	 * {@link ItemSnapshot} per occupied slot (empty slots and zero-quantity placeholders
	 * omitted). Returns an empty list when the container is absent (e.g. not logged in,
	 * or a bank that has never been opened).
	 */
	List<ItemSnapshot> readContainer(int containerId);

	/**
	 * Locally-cached price for an item id (RuneLite {@code ItemManager}). Returns 0 when
	 * unknown. This performs no network I/O; it reads RuneLite's in-memory price cache.
	 */
	int getItemPrice(int itemId);

	/** Value of a varbit by id (used for achievement-diary completion). */
	int getVarbitValue(int varbitId);

	/** Value of a VarPlayer by id (used for quest points). */
	int getVarpValue(int varpId);

	/** Authoritative state of a quest, via RuneLite's {@code Quest.getState}. */
	QuestState getQuestState(Quest quest);
}
