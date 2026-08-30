/*
 * Copyright (c) 2026, ScapePath
 * All rights reserved. BSD 2-Clause. See LICENSE file.
 */
package com.scapepath.plugin.game;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.scapepath.plugin.snapshot.data.ItemSnapshot;
import java.time.Instant;
import java.util.Collections;
import org.junit.Test;

public class BankTrackerTest
{
	@Test
	public void startsNeverSynced()
	{
		BankTracker t = new BankTracker();
		assertFalse(t.hasBeenSynced());
		assertFalse(t.isOpen());
		assertNull(t.view().getItems());
	}

	@Test
	public void updateMarksSyncedWithTimestamp()
	{
		BankTracker t = new BankTracker();
		Instant at = Instant.now();
		t.updateItems(Collections.singletonList(new ItemSnapshot(995, 1000, 0)), at);

		assertTrue(t.hasBeenSynced());
		assertEquals(at, t.view().getCollectedAt());
		assertEquals(1, t.view().getItems().size());
	}

	@Test
	public void emptyBankIsSyncedNotUnknown()
	{
		BankTracker t = new BankTracker();
		t.updateItems(Collections.emptyList(), Instant.now());
		assertTrue("An opened empty bank is synced", t.hasBeenSynced());
		assertTrue(t.view().getItems().isEmpty());
	}

	@Test
	public void resetClearsEverything()
	{
		BankTracker t = new BankTracker();
		t.updateItems(Collections.singletonList(new ItemSnapshot(995, 1, 0)), Instant.now());
		t.setOpen(true);

		t.reset();

		assertFalse(t.hasBeenSynced());
		assertFalse(t.isOpen());
		assertNull(t.view().getCollectedAt());
	}
}
