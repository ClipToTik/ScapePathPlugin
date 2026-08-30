/*
 * Copyright (c) 2026, ScapePath
 * All rights reserved. BSD 2-Clause. See LICENSE file.
 */
package com.scapepath.plugin.collector;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.scapepath.plugin.game.FakeGameStateAccessor;
import com.scapepath.plugin.snapshot.CollectedSection;
import com.scapepath.plugin.snapshot.SnapshotSectionType;
import com.scapepath.plugin.snapshot.SourceFreshness;
import com.scapepath.plugin.snapshot.data.IdentityData;
import org.junit.Test;

public class IdentityCollectorTest
{
	private final IdentityCollector collector = new IdentityCollector();

	@Test
	public void collectsIdentityWhenLoggedIn()
	{
		FakeGameStateAccessor game = new FakeGameStateAccessor()
			.loggedIn("Zezima", 302, 1 /* IRONMAN */, 1234567890L);
		CollectorContext ctx = CollectorContext.of(game);

		assertTrue(collector.isReady(ctx));
		CollectedSection section = collector.collect(ctx);

		assertEquals(SnapshotSectionType.IDENTITY, section.getType());
		assertEquals(SourceFreshness.COMPLETE, section.getFreshness());

		IdentityData data = (IdentityData) section.getData();
		assertTrue(data.isLoggedIn());
		assertEquals("Zezima", data.getRsn());
		assertEquals(302, data.getWorld());
		assertEquals("IRONMAN", data.getAccountType());
		assertEquals(1234567890L, data.getAccountHash());
	}

	@Test
	public void unavailableWhenLoggedOut()
	{
		FakeGameStateAccessor game = new FakeGameStateAccessor().loggedOut();
		CollectorContext ctx = CollectorContext.of(game);

		assertFalse(collector.isReady(ctx));
		CollectedSection section = collector.collect(ctx);

		assertEquals(SnapshotSectionType.IDENTITY, section.getType());
		assertEquals(SourceFreshness.UNAVAILABLE, section.getFreshness());
		assertNull(section.getData());
	}

	@Test
	public void normalAndUnknownAccountTypesMap()
	{
		IdentityData normal = (IdentityData) collector.collect(CollectorContext.of(
			new FakeGameStateAccessor().loggedIn("Bob", 1, 0, 42L))).getData();
		assertEquals("NORMAL", normal.getAccountType());

		// Out-of-range varbit maps to UNKNOWN rather than being guessed.
		IdentityData weird = (IdentityData) collector.collect(CollectorContext.of(
			new FakeGameStateAccessor().loggedIn("Bob", 1, 99, 42L))).getData();
		assertEquals("UNKNOWN", weird.getAccountType());
	}
}
