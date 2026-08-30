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
import com.scapepath.plugin.snapshot.data.EquipmentData;
import com.scapepath.plugin.snapshot.data.ItemSnapshot;
import java.util.Arrays;
import java.util.Collections;
import net.runelite.api.gameval.InventoryID;
import org.junit.Test;

public class EquipmentCollectorTest
{
	private final EquipmentCollector collector = new EquipmentCollector();

	private static FakeGameStateAccessor loggedIn()
	{
		return new FakeGameStateAccessor().loggedIn("Zezima", 302, 0, 1L);
	}

	private static EquipmentData collect(EquipmentCollector c, FakeGameStateAccessor game)
	{
		return (EquipmentData) c.collect(CollectorContext.of(game)).getData();
	}

	@Test
	public void emptyEquipment()
	{
		FakeGameStateAccessor game = loggedIn().container(InventoryID.WORN, Collections.emptyList());
		assertTrue(collect(collector, game).getItems().isEmpty());
	}

	@Test
	public void oneEquippedItem()
	{
		// Weapon slot index 3.
		FakeGameStateAccessor game = loggedIn().container(InventoryID.WORN,
			Collections.singletonList(new ItemSnapshot(4151, 1, 3)));
		EquipmentData data = collect(collector, game);
		assertEquals(1, data.getItems().size());
		assertEquals(3, data.getItems().get(0).getSlot());
		assertEquals(4151, data.getItems().get(0).getId());
	}

	@Test
	public void multipleSlotsOnlyOccupiedReturned()
	{
		// Only occupied slots present (empty slots omitted by the container reader).
		FakeGameStateAccessor game = loggedIn().container(InventoryID.WORN, Arrays.asList(
			new ItemSnapshot(1163, 1, 0),  // head
			new ItemSnapshot(4151, 1, 3),  // weapon
			new ItemSnapshot(4127, 1, 4))); // body
		EquipmentData data = collect(collector, game);
		assertEquals(3, data.getItems().size());
	}

	@Test
	public void reflectsEquipmentChange()
	{
		FakeGameStateAccessor game = loggedIn().container(InventoryID.WORN,
			Collections.singletonList(new ItemSnapshot(1163, 1, 0)));
		assertEquals(1, collect(collector, game).getItems().size());

		game.container(InventoryID.WORN, Arrays.asList(
			new ItemSnapshot(1163, 1, 0),
			new ItemSnapshot(4151, 1, 3)));
		assertEquals(2, collect(collector, game).getItems().size());
	}

	@Test
	public void unavailableWhenLoggedOut()
	{
		FakeGameStateAccessor game = new FakeGameStateAccessor().loggedOut();
		CollectedSection section = collector.collect(CollectorContext.of(game));
		assertEquals(SnapshotSectionType.EQUIPMENT, section.getType());
		assertEquals(SourceFreshness.UNAVAILABLE, section.getFreshness());
		assertNull(section.getData());
	}
}
