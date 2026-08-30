/*
 * Copyright (c) 2026, ScapePath
 * All rights reserved. BSD 2-Clause. See LICENSE file.
 */
package com.scapepath.plugin.game;

import com.scapepath.plugin.snapshot.data.ItemSnapshot;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Player;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.game.ItemManager;

/**
 * Live {@link GameStateAccessor} backed by the RuneLite {@link Client}.
 *
 * <p>Every method is a thin, read-only delegation to a public RuneLite API. There is no
 * mutation, no input, and no I/O. Must be invoked on the client thread (see
 * {@link GameStateAccessor}).</p>
 */
@Singleton
public class RuneLiteGameStateAccessor implements GameStateAccessor
{
	private final Client client;
	private final ItemManager itemManager;

	@Inject
	RuneLiteGameStateAccessor(Client client, ItemManager itemManager)
	{
		this.client = client;
		this.itemManager = itemManager;
	}

	@Override
	public boolean isLoggedIn()
	{
		return client.getGameState() == GameState.LOGGED_IN && client.getLocalPlayer() != null;
	}

	@Override
	public long getAccountHash()
	{
		return client.getAccountHash();
	}

	@Override
	public String getPlayerName()
	{
		final Player local = client.getLocalPlayer();
		return local == null ? null : local.getName();
	}

	@Override
	public int getAccountTypeVarbit()
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return -1;
		}
		// VarbitID.IRONMAN (1777) is the current, non-deprecated account-type varbit
		// (0 = normal, 1 = ironman, …); the legacy net.runelite.api.Varbits is deprecated.
		return client.getVarbitValue(VarbitID.IRONMAN);
	}

	@Override
	public int getWorld()
	{
		return client.getWorld();
	}

	@Override
	public int getRealSkillLevel(Skill skill)
	{
		return client.getRealSkillLevel(skill);
	}

	@Override
	public int getSkillExperience(Skill skill)
	{
		return client.getSkillExperience(skill);
	}

	@Override
	public int getTotalLevel()
	{
		return client.getTotalLevel();
	}

	@Override
	public long getOverallExperience()
	{
		return client.getOverallExperience();
	}

	@Override
	public List<ItemSnapshot> readContainer(int containerId)
	{
		final ItemContainer container = client.getItemContainer(containerId);
		if (container == null)
		{
			return Collections.emptyList();
		}

		final Item[] items = container.getItems();
		final List<ItemSnapshot> result = new ArrayList<>(items.length);
		for (int slot = 0; slot < items.length; slot++)
		{
			final Item item = items[slot];
			// Skip empty slots (id -1) and zero-quantity bank placeholders.
			if (item == null || item.getId() < 0 || item.getQuantity() <= 0)
			{
				continue;
			}
			result.add(new ItemSnapshot(item.getId(), item.getQuantity(), slot));
		}
		return Collections.unmodifiableList(result);
	}

	@Override
	public int getItemPrice(int itemId)
	{
		return itemManager.getItemPrice(itemId);
	}

	@Override
	public int getVarbitValue(int varbitId)
	{
		return client.getVarbitValue(varbitId);
	}

	@Override
	public int getVarpValue(int varpId)
	{
		return client.getVarpValue(varpId);
	}

	@Override
	public QuestState getQuestState(Quest quest)
	{
		return quest.getState(client);
	}
}
