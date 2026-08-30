/*
 * Copyright (c) 2026, ScapePath
 * All rights reserved. BSD 2-Clause. See LICENSE file.
 */
package com.scapepath.plugin.collector;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import com.scapepath.plugin.game.BankTracker;
import com.scapepath.plugin.game.FakeGameStateAccessor;
import com.scapepath.plugin.snapshot.data.ItemSnapshot;
import com.scapepath.plugin.snapshot.data.WealthData;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import net.runelite.api.gameval.InventoryID;
import org.junit.Test;

public class WealthCollectorTest
{
	private static FakeGameStateAccessor loggedIn()
	{
		return new FakeGameStateAccessor().loggedIn("Zezima", 302, 0, 1L);
	}

	private static WealthData collect(BankTracker tracker, FakeGameStateAccessor game)
	{
		return (WealthData) new WealthCollector(tracker).collect(CollectorContext.of(game)).getData();
	}

	@Test
	public void zeroGpWhenNoCoins()
	{
		FakeGameStateAccessor game = loggedIn().container(InventoryID.INV,
			Collections.singletonList(new ItemSnapshot(4151, 1, 0)));
		WealthData w = collect(new BankTracker(), game);
		assertEquals(0L, w.getGpOnHand());
	}

	@Test
	public void gpOnHandFromCoinStack()
	{
		FakeGameStateAccessor game = loggedIn().container(InventoryID.INV,
			Arrays.asList(new ItemSnapshot(995, 125_430, 0), new ItemSnapshot(4151, 1, 1)));
		assertEquals(125_430L, collect(new BankTracker(), game).getGpOnHand());
	}

	@Test
	public void largeCoinStack()
	{
		FakeGameStateAccessor game = loggedIn().container(InventoryID.INV,
			Collections.singletonList(new ItemSnapshot(995, 2_100_000_000, 0)));
		assertEquals(2_100_000_000L, collect(new BankTracker(), game).getGpOnHand());
	}

	@Test
	public void bankUnavailableLeavesBankFieldsNull()
	{
		FakeGameStateAccessor game = loggedIn().container(InventoryID.INV,
			Collections.singletonList(new ItemSnapshot(995, 100, 0)));
		WealthData w = collect(new BankTracker(), game); // bank never synced
		assertEquals(100L, w.getGpOnHand());
		assertNull("Bank GP must be null (unknown), not 0", w.getBankGp());
		assertNull(w.getEstimatedBankValue());
	}

	@Test
	public void bankCoinsAndEstimatedValueWhenSynced()
	{
		BankTracker tracker = new BankTracker();
		tracker.updateItems(Arrays.asList(
			new ItemSnapshot(995, 10_000_000, 0),
			new ItemSnapshot(4151, 3, 1)), Instant.now());

		FakeGameStateAccessor game = loggedIn()
			.container(InventoryID.INV, Collections.singletonList(new ItemSnapshot(995, 50, 0)))
			.price(995, 1).price(4151, 2_000_000);

		WealthData w = collect(tracker, game);
		assertEquals(50L, w.getGpOnHand());
		assertEquals(Long.valueOf(10_000_000L), w.getBankGp());
		// 10,000,000*1 + 3*2,000,000 = 16,000,000
		assertEquals(Long.valueOf(16_000_000L), w.getEstimatedBankValue());
	}

	@Test
	public void missingPriceDataIsSkippedInEstimate()
	{
		BankTracker tracker = new BankTracker();
		tracker.updateItems(Arrays.asList(
			new ItemSnapshot(995, 5_000, 0),
			new ItemSnapshot(4151, 1, 1)), Instant.now()); // no price set for 4151

		FakeGameStateAccessor game = loggedIn()
			.container(InventoryID.INV, Collections.emptyList())
			.price(995, 1); // whip price missing -> excluded

		WealthData w = collect(tracker, game);
		assertEquals(Long.valueOf(5_000L), w.getEstimatedBankValue());
	}
}
