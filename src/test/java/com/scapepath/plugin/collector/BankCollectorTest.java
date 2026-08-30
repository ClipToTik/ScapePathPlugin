/*
 * Copyright (c) 2026, ScapePath
 * All rights reserved. BSD 2-Clause. See LICENSE file.
 */
package com.scapepath.plugin.collector;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.scapepath.plugin.game.BankTracker;
import com.scapepath.plugin.game.FakeGameStateAccessor;
import com.scapepath.plugin.snapshot.CollectedSection;
import com.scapepath.plugin.snapshot.SnapshotSectionType;
import com.scapepath.plugin.snapshot.SourceFreshness;
import com.scapepath.plugin.snapshot.data.BankData;
import com.scapepath.plugin.snapshot.data.ItemSnapshot;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public class BankCollectorTest
{
	private static FakeGameStateAccessor loggedIn()
	{
		return new FakeGameStateAccessor().loggedIn("Zezima", 302, 0, 1L);
	}

	private static CollectedSection collect(BankTracker tracker, FakeGameStateAccessor game)
	{
		return new BankCollector(tracker).collect(CollectorContext.of(game));
	}

	@Test
	public void unavailableWhenLoggedOut()
	{
		BankTracker tracker = new BankTracker();
		tracker.updateItems(Collections.singletonList(new ItemSnapshot(995, 1, 0)), Instant.now());
		CollectedSection s = collect(tracker, new FakeGameStateAccessor().loggedOut());
		assertEquals(SourceFreshness.UNAVAILABLE, s.getFreshness());
		assertNull(s.getData());
	}

	@Test
	public void neverOpenedIsUnavailableNotEmpty()
	{
		BankTracker tracker = new BankTracker(); // never synced
		CollectedSection s = collect(tracker, loggedIn());

		assertEquals(SnapshotSectionType.BANK, s.getType());
		assertEquals(SourceFreshness.UNAVAILABLE, s.getFreshness());
		assertNull("Unknown bank must NOT be represented as an empty bank", s.getData());
	}

	@Test
	public void openBankIsCurrentWithData()
	{
		BankTracker tracker = new BankTracker();
		Instant at = Instant.now();
		tracker.updateItems(Arrays.asList(
			new ItemSnapshot(995, 5_000_000, 0),
			new ItemSnapshot(4151, 1, 1)), at);
		tracker.setOpen(true);

		FakeGameStateAccessor game = loggedIn().price(4151, 2_000_000).price(995, 1);
		CollectedSection s = collect(tracker, game);

		assertEquals(SourceFreshness.COMPLETE, s.getFreshness());
		assertEquals(at, s.getCollectedAt());
		BankData bank = (BankData) s.getData();
		assertNotNull(bank);
		assertEquals(2, bank.getUniqueItems());
		assertEquals(5_000_000L, bank.getCoins());
		// 5,000,000*1 (coins) + 1*2,000,000 (whip) = 7,000,000
		assertEquals(7_000_000L, bank.getEstimatedValue());
		assertEquals(BankData.SOURCE_BANK_INTERFACE, bank.getSource());
	}

	@Test
	public void closedBankIsStaleButRetained()
	{
		BankTracker tracker = new BankTracker();
		tracker.updateItems(Collections.singletonList(new ItemSnapshot(995, 1000, 0)), Instant.now());
		tracker.setOpen(false); // closed

		CollectedSection s = collect(tracker, loggedIn());
		assertEquals(SourceFreshness.STALE, s.getFreshness());
		assertNotNull(s.getData());
		assertEquals(1000L, ((BankData) s.getData()).getCoins());
	}

	@Test
	public void openedEmptyBankIsCurrentAndEmptyNotUnavailable()
	{
		BankTracker tracker = new BankTracker();
		tracker.updateItems(Collections.emptyList(), Instant.now());
		tracker.setOpen(true);

		CollectedSection s = collect(tracker, loggedIn());
		assertEquals(SourceFreshness.COMPLETE, s.getFreshness());
		BankData bank = (BankData) s.getData();
		assertNotNull("Empty synced bank still has BankData", bank);
		assertTrue(bank.getItems().isEmpty());
		assertEquals(0, bank.getUniqueItems());
		assertEquals(0L, bank.getCoins());
	}
}
