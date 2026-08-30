/*
 * Copyright (c) 2026, ScapePath
 * All rights reserved. BSD 2-Clause. See LICENSE file.
 */
package com.scapepath.plugin.snapshot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import java.time.Instant;
import org.junit.Test;

public class AccountSnapshotTest
{
	@Test
	public void emptySnapshotCarriesMetadataAndNoData()
	{
		Instant now = Instant.now();
		AccountSnapshot snapshot = AccountSnapshot.empty("1.2.3", now, null);

		assertEquals("1.2.3", snapshot.getPluginVersion());
		assertEquals(now, snapshot.getTimestamp());
		assertNull(snapshot.getRsn());
		assertTrue(snapshot.getSections().isEmpty());
	}

	@Test
	public void unavailableSectionHoldsNoPayload()
	{
		Instant now = Instant.now();
		CollectedSection section = CollectedSection.unavailable(SnapshotSectionType.BANK, now);

		assertEquals(SnapshotSectionType.BANK, section.getType());
		assertEquals(SourceFreshness.UNAVAILABLE, section.getFreshness());
		assertNull("Session 1 must carry no account payload", section.getData());
	}

	@Test
	public void everySectionTypeHasDisplayName()
	{
		for (SnapshotSectionType type : SnapshotSectionType.values())
		{
			assertTrue(type.getDisplayName() != null && !type.getDisplayName().isEmpty());
		}
	}
}
