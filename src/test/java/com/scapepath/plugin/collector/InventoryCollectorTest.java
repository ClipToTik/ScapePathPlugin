/*
 * Copyright (c) 2026, ScapePath
 * All rights reserved. BSD 2-Clause. See LICENSE file.
 */
package com.scapepath.plugin.collector;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.scapepath.plugin.game.FakeGameStateAccessor;
import com.scapepath.plugin.snapshot.CollectedSection;
import com.scapepath.plugin.snapshot.SnapshotSectionType;
import com.scapepath.plugin.snapshot.SourceFreshness;
import com.scapepath.plugin.snapshot.data.InventoryData;
import com.scapepath.plugin.snapshot.data.ItemSnapshot;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.runelite.api.gameval.InventoryID;
import org.junit.Test;

public class InventoryCollectorTest
{
	private final InventoryCollector collector = new InventoryCollector();

	private static FakeGameStateAccessor loggedIn()
	{
		return new FakeGameStateAccessor().loggedIn("Zezima", 302, 0, 1L);
	}

	private static InventoryData collect(InventoryCollector c, FakeGameStateAccessor game)
	{
		return (InventoryData) c.collect(CollectorContext.of(game)).getData();
	}

	@Test
	public void emptyInventory()
	{
		FakeGameStateAccessor game = loggedIn().container(InventoryID.INV, Collections.emptyList());
		InventoryData data = collect(collector, game);
		assertEquals(0, data.getOccupiedSlots());
		assertTrue(data.getItems().isEmpty());
	}

	@Test
	public void oneItem()
	{
		FakeGameStateAccessor game = loggedIn().container(InventoryID.INV,
			Collections.singletonList(new ItemSnapshot(995, 100, 0)));
		InventoryData data = collect(collector, game);
		assertEquals(1, data.getOccupiedSlots());
		assertEquals(995, data.getItems().get(0).getId());
		assertEquals(100, data.getItems().get(0).getQuantity());
		assertEquals(0, data.getItems().get(0).getSlot());
	}

	@Test
	public void multipleItemsIncludingStacked()
	{
		FakeGameStateAccessor game = loggedIn().container(InventoryID.INV, Arrays.asList(
			new ItemSnapshot(995, 2_000_000, 0), // stacked coins
			new ItemSnapshot(526, 1, 1),
			new ItemSnapshot(4151, 1, 2)));
		InventoryData data = collect(collector, game);
		assertEquals(3, data.getOccupiedSlots());
		assertEquals(2_000_000, data.getItems().get(0).getQuantity());
	}

	@Test
	public void fullInventory()
	{
		List<ItemSnapshot> items = new ArrayList<>();
		for (int i = 0; i < 28; i++)
		{
			items.add(new ItemSnapshot(1000 + i, 1, i));
		}
		FakeGameStateAccessor game = loggedIn().container(InventoryID.INV, items);
		InventoryData data = collect(collector, game);
		assertEquals(28, data.getOccupiedSlots());
	}

	@Test
	public void reflectsRemovalAndQuantityChange()
	{
		FakeGameStateAccessor game = loggedIn().container(InventoryID.INV, Arrays.asList(
			new ItemSnapshot(995, 100, 0),
			new ItemSnapshot(526, 5, 1)));
		assertEquals(2, collect(collector, game).getOccupiedSlots());

		// Removal + quantity change: re-configure the container and re-collect.
		game.container(InventoryID.INV, Collections.singletonList(new ItemSnapshot(995, 250, 0)));
		InventoryData after = collect(collector, game);
		assertEquals(1, after.getOccupiedSlots());
		assertEquals(250, after.getItems().get(0).getQuantity());
	}

	@Test
	public void unavailableWhenLoggedOut()
	{
		FakeGameStateAccessor game = new FakeGameStateAccessor().loggedOut();
		CollectedSection section = collector.collect(CollectorContext.of(game));
		assertEquals(SnapshotSectionType.INVENTORY, section.getType());
		assertEquals(SourceFreshness.UNAVAILABLE, section.getFreshness());
		assertNull(section.getData());
	}
}
