/*
 * Copyright (c) 2026, ScapePath
 * All rights reserved. BSD 2-Clause. See LICENSE file.
 */
package com.scapepath.plugin.collector;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import com.scapepath.plugin.game.CollectionLogDefinitions;
import com.scapepath.plugin.game.FakeGameStateAccessor;
import com.scapepath.plugin.snapshot.CollectedSection;
import com.scapepath.plugin.snapshot.SnapshotSectionType;
import com.scapepath.plugin.snapshot.SourceFreshness;
import com.scapepath.plugin.snapshot.data.CollectionLogData;
import net.runelite.api.gameval.VarPlayerID;
import org.junit.Test;

public class CollectionLogCollectorTest
{
	private final CollectionLogCollector collector = new CollectionLogCollector();

	private static FakeGameStateAccessor loggedIn()
	{
		return new FakeGameStateAccessor().loggedIn("Zezima", 302, 0, 1L);
	}

	@Test
	public void unavailableWhenLoggedOut()
	{
		CollectedSection s = collector.collect(CollectorContext.of(new FakeGameStateAccessor().loggedOut()));
		assertEquals(SnapshotSectionType.COLLECTION_LOG, s.getType());
		assertEquals(SourceFreshness.UNAVAILABLE, s.getFreshness());
		assertNull(s.getData());
	}

	@Test
	public void unavailableWhenCountsNotYetPopulated()
	{
		// Logged in but the game has not synced the account's collection log counts (max 0).
		CollectedSection s = collector.collect(CollectorContext.of(loggedIn()));
		assertEquals(SourceFreshness.UNAVAILABLE, s.getFreshness());
		assertNull(s.getData());
	}

	@Test
	public void completeWhenCountsPresent()
	{
		FakeGameStateAccessor game = loggedIn()
			.varp(VarPlayerID.COLLECTION_COUNT_MAX, 1500)
			.varp(VarPlayerID.COLLECTION_COUNT, 573)
			.varp(VarPlayerID.COLLECTION_COUNT_BOSSES, 100)
			.varp(VarPlayerID.COLLECTION_COUNT_BOSSES_MAX, 400);

		CollectedSection s = collector.collect(CollectorContext.of(game));
		assertEquals(SourceFreshness.COMPLETE, s.getFreshness());

		CollectionLogData d = (CollectionLogData) s.getData();
		assertEquals(573, d.getObtained());
		assertEquals(1500, d.getTotal());
		assertEquals(CollectionLogDefinitions.tabs().size(), d.getTabs().size());

		CollectionLogData.TabCount bosses = d.getTabs().stream()
			.filter(t -> t.getId().equals("BOSSES")).findFirst().orElseThrow(AssertionError::new);
		assertEquals(100, bosses.getObtained());
		assertEquals(400, bosses.getTotal());
	}

	@Test
	public void tabOrderIsDeterministic()
	{
		FakeGameStateAccessor game = loggedIn().varp(VarPlayerID.COLLECTION_COUNT_MAX, 1);
		CollectionLogData d = (CollectionLogData) collector.collect(CollectorContext.of(game)).getData();
		assertEquals("BOSSES", d.getTabs().get(0).getId());
		assertEquals("OTHER", d.getTabs().get(d.getTabs().size() - 1).getId());
	}
}
