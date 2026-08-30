/*
 * Copyright (c) 2026, ScapePath
 * All rights reserved. BSD 2-Clause. See LICENSE file.
 */
package com.scapepath.plugin.collector;

import com.scapepath.plugin.game.BankTracker;
import com.scapepath.plugin.game.GameStateAccessor;
import com.scapepath.plugin.snapshot.CollectedSection;
import com.scapepath.plugin.snapshot.SnapshotSectionType;
import com.scapepath.plugin.snapshot.SourceFreshness;
import com.scapepath.plugin.snapshot.data.ItemSnapshot;
import com.scapepath.plugin.snapshot.data.WealthData;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;

/**
 * Derives wealth from locally available containers.
 *
 * <p>GP on hand comes from the inventory coin stack. Bank GP and estimated bank value are
 * present only when the bank has been synced; otherwise they are {@code null} (never
 * conflated with zero). Bank value is an estimate from locally-cached prices.</p>
 */
@Singleton
public class WealthCollector implements AccountDataCollector
{
	private final BankTracker bankTracker;

	@Inject
	public WealthCollector(BankTracker bankTracker)
	{
		this.bankTracker = bankTracker;
	}

	@Override
	public SnapshotSectionType type()
	{
		return SnapshotSectionType.WEALTH;
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

		final long gpOnHand = sumCoins(game.readContainer(InventoryID.INV));

		Long bankGp = null;
		Long estimatedBankValue = null;
		final BankTracker.Snapshot bank = bankTracker.view();
		if (bank.hasBeenSynced())
		{
			final List<ItemSnapshot> bankItems = bank.getItems();
			bankGp = sumCoins(bankItems);
			estimatedBankValue = estimateValue(bankItems, game);
		}

		final WealthData data = new WealthData(gpOnHand, bankGp, estimatedBankValue);
		return new CollectedSection(type(), SourceFreshness.COMPLETE, context.getSnapshotTime(), data);
	}

	private static long sumCoins(List<ItemSnapshot> items)
	{
		long coins = 0;
		for (ItemSnapshot item : items)
		{
			if (item.getId() == ItemID.COINS)
			{
				coins += item.getQuantity();
			}
		}
		return coins;
	}

	private static long estimateValue(List<ItemSnapshot> items, GameStateAccessor game)
	{
		long total = 0;
		for (ItemSnapshot item : items)
		{
			final int price = game.getItemPrice(item.getId());
			if (price > 0)
			{
				total += (long) price * item.getQuantity();
			}
		}
		return total;
	}
}
