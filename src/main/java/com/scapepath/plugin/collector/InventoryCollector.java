/*
 * Copyright (c) 2026, ScapePath
 * All rights reserved. BSD 2-Clause. See LICENSE file.
 */
package com.scapepath.plugin.collector;

import com.scapepath.plugin.game.GameStateAccessor;
import com.scapepath.plugin.snapshot.CollectedSection;
import com.scapepath.plugin.snapshot.SnapshotSectionType;
import com.scapepath.plugin.snapshot.SourceFreshness;
import com.scapepath.plugin.snapshot.data.InventoryData;
import com.scapepath.plugin.snapshot.data.ItemSnapshot;
import java.util.List;
import javax.inject.Singleton;
import net.runelite.api.gameval.InventoryID;

/**
 * Collects the current inventory (occupied slots only) from the live client.
 *
 * <p>Always readable while logged in; produces {@code UNAVAILABLE} otherwise. Item id and
 * quantity are the source of truth; empty slots are omitted.</p>
 */
@Singleton
public class InventoryCollector implements AccountDataCollector
{
	@Override
	public SnapshotSectionType type()
	{
		return SnapshotSectionType.INVENTORY;
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

		final List<ItemSnapshot> items = game.readContainer(InventoryID.INV);
		final InventoryData data = new InventoryData(items, items.size());
		return new CollectedSection(type(), SourceFreshness.COMPLETE, context.getSnapshotTime(), data);
	}
}
