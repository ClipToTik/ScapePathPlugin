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
import com.scapepath.plugin.snapshot.data.BankData;
import com.scapepath.plugin.snapshot.data.ItemSnapshot;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.gameval.ItemID;

/**
 * Produces the interface-gated {@code BANK} section from the {@link BankTracker}.
 *
 * <p>Freshness is the crux of this collector, and "unknown" is never rendered as
 * "empty":</p>
 * <ul>
 *   <li>logged out, or bank never opened &rarr; {@link SourceFreshness#UNAVAILABLE},
 *       no {@link BankData};</li>
 *   <li>bank currently open &rarr; {@link SourceFreshness#COMPLETE} (current);</li>
 *   <li>bank closed but previously opened &rarr; {@link SourceFreshness#STALE} (cached).</li>
 * </ul>
 *
 * <p>The section's {@code collectedAt} is the time the bank was last read, not the
 * snapshot assembly time.</p>
 */
@Singleton
public class BankCollector implements AccountDataCollector
{
	private final BankTracker bankTracker;

	@Inject
	public BankCollector(BankTracker bankTracker)
	{
		this.bankTracker = bankTracker;
	}

	@Override
	public SnapshotSectionType type()
	{
		return SnapshotSectionType.BANK;
	}

	@Override
	public boolean isReady(CollectorContext context)
	{
		return context.getGame().isLoggedIn();
	}

	@Override
	public CollectedSection collect(CollectorContext context)
	{
		final BankTracker.Snapshot bank = bankTracker.view();
		if (!context.getGame().isLoggedIn() || !bank.hasBeenSynced())
		{
			// Never opened (or logged out): unavailable, NOT an empty bank.
			return CollectedSection.unavailable(type(), context.getSnapshotTime());
		}

		final List<ItemSnapshot> items = bank.getItems();
		final long coins = sumCoins(items);
		final long estimatedValue = estimateValue(items, context.getGame());

		final BankData data = new BankData(
			items,
			items.size(),
			coins,
			estimatedValue,
			BankData.SOURCE_BANK_INTERFACE
		);

		final SourceFreshness freshness = bank.isOpen()
			? SourceFreshness.COMPLETE   // bank open right now => current
			: SourceFreshness.STALE;     // cached from an earlier opening

		return new CollectedSection(type(), freshness, bank.getCollectedAt(), data);
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
