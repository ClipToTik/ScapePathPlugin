/*
 * Copyright (c) 2026, ScapePath
 * All rights reserved. BSD 2-Clause. See LICENSE file.
 */
package com.scapepath.plugin.game;

import com.scapepath.plugin.snapshot.data.ItemSnapshot;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;

/**
 * In-memory {@link GameStateAccessor} for unit tests. No RuneLite login required.
 *
 * <p>Defaults to logged-out. Configure via the fluent setters. Unset skills read as
 * level 1 / 0 xp.</p>
 */
public class FakeGameStateAccessor implements GameStateAccessor
{
	private boolean loggedIn = false;
	private long accountHash = -1L;
	private String playerName = null;
	private int accountTypeVarbit = -1;
	private int world = 0;

	private final Map<Skill, Integer> levels = new EnumMap<>(Skill.class);
	private final Map<Skill, Integer> xp = new EnumMap<>(Skill.class);
	private final Map<Integer, List<ItemSnapshot>> containers = new HashMap<>();
	private final Map<Integer, Integer> prices = new HashMap<>();
	private final Map<Integer, Integer> varbits = new HashMap<>();
	private final Map<Integer, Integer> varps = new HashMap<>();
	private final Map<Quest, QuestState> questStates = new EnumMap<>(Quest.class);

	/** @param accountTypeVarbit raw Varbits.ACCOUNT_TYPE value (0 = normal, 1 = ironman, …) */
	public FakeGameStateAccessor loggedIn(String rsn, int world, int accountTypeVarbit, long hash)
	{
		this.loggedIn = true;
		this.playerName = rsn;
		this.world = world;
		this.accountTypeVarbit = accountTypeVarbit;
		this.accountHash = hash;
		return this;
	}

	public FakeGameStateAccessor loggedOut()
	{
		this.loggedIn = false;
		this.playerName = null;
		this.accountTypeVarbit = -1;
		this.accountHash = -1L;
		this.world = 0;
		return this;
	}

	public FakeGameStateAccessor skill(Skill skill, int level, int experience)
	{
		levels.put(skill, level);
		xp.put(skill, experience);
		return this;
	}

	/** Set every skill in the current {@code Skill.values()} to the same level/xp. */
	public FakeGameStateAccessor allSkills(int level, int experience)
	{
		for (Skill s : Skill.values())
		{
			skill(s, level, experience);
		}
		return this;
	}

	/** Populate a container (by gameval InventoryID) with the given stacks. */
	public FakeGameStateAccessor container(int containerId, List<ItemSnapshot> items)
	{
		containers.put(containerId, items);
		return this;
	}

	/** Set a local item price. */
	public FakeGameStateAccessor price(int itemId, int price)
	{
		prices.put(itemId, price);
		return this;
	}

	public FakeGameStateAccessor varbit(int varbitId, int value)
	{
		varbits.put(varbitId, value);
		return this;
	}

	public FakeGameStateAccessor varp(int varpId, int value)
	{
		varps.put(varpId, value);
		return this;
	}

	public FakeGameStateAccessor quest(Quest quest, QuestState state)
	{
		questStates.put(quest, state);
		return this;
	}

	@Override
	public boolean isLoggedIn()
	{
		return loggedIn;
	}

	@Override
	public long getAccountHash()
	{
		return accountHash;
	}

	@Override
	public String getPlayerName()
	{
		return playerName;
	}

	@Override
	public int getAccountTypeVarbit()
	{
		return accountTypeVarbit;
	}

	@Override
	public int getWorld()
	{
		return world;
	}

	@Override
	public int getRealSkillLevel(Skill skill)
	{
		return levels.getOrDefault(skill, 1);
	}

	@Override
	public int getSkillExperience(Skill skill)
	{
		return xp.getOrDefault(skill, 0);
	}

	@Override
	public int getTotalLevel()
	{
		int total = 0;
		for (Skill s : Skill.values())
		{
			total += getRealSkillLevel(s);
		}
		return total;
	}

	@Override
	public long getOverallExperience()
	{
		long total = 0;
		for (Skill s : Skill.values())
		{
			total += getSkillExperience(s);
		}
		return total;
	}

	@Override
	public List<ItemSnapshot> readContainer(int containerId)
	{
		return containers.getOrDefault(containerId, Collections.emptyList());
	}

	@Override
	public int getItemPrice(int itemId)
	{
		return prices.getOrDefault(itemId, 0);
	}

	@Override
	public int getVarbitValue(int varbitId)
	{
		return varbits.getOrDefault(varbitId, 0);
	}

	@Override
	public int getVarpValue(int varpId)
	{
		return varps.getOrDefault(varpId, 0);
	}

	@Override
	public QuestState getQuestState(Quest quest)
	{
		return questStates.getOrDefault(quest, QuestState.NOT_STARTED);
	}
}
