/*
 * Copyright (c) 2026, ScapePath
 * All rights reserved. BSD 2-Clause. See LICENSE file.
 */
package com.scapepath.plugin.collector;

import com.scapepath.plugin.game.GameStateAccessor;
import com.scapepath.plugin.snapshot.CollectedSection;
import com.scapepath.plugin.snapshot.SnapshotSectionType;
import com.scapepath.plugin.snapshot.SourceFreshness;
import com.scapepath.plugin.snapshot.data.EquipmentData;
import com.scapepath.plugin.snapshot.data.ItemSnapshot;
import java.util.List;
import javax.inject.Singleton;
import net.runelite.api.gameval.InventoryID;

/**
 * Collects currently worn equipment from the {@code WORN} container.
 *
 * <p>Each item's slot is the equipment slot index (head, cape, …). Empty slots are
 * omitted. Always readable while logged in; {@code UNAVAILABLE} otherwise.</p>
 */
@Singleton
public class EquipmentCollector implements AccountDataCollector
{
	@Override
	public SnapshotSectionType type()
	{
		return SnapshotSectionType.EQUIPMENT;
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

		final List<ItemSnapshot> items = game.readContainer(InventoryID.WORN);
		final EquipmentData data = new EquipmentData(items);
		return new CollectedSection(type(), SourceFreshness.COMPLETE, context.getSnapshotTime(), data);
	}
}
