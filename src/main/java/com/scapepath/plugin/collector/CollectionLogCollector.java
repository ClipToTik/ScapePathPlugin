/*
 * Copyright (c) 2026, ScapePath
 * All rights reserved. BSD 2-Clause. See LICENSE file.
 */
package com.scapepath.plugin.collector;

import com.scapepath.plugin.game.CollectionLogDefinitions;
import com.scapepath.plugin.game.GameStateAccessor;
import com.scapepath.plugin.snapshot.CollectedSection;
import com.scapepath.plugin.snapshot.SnapshotSectionType;
import com.scapepath.plugin.snapshot.SourceFreshness;
import com.scapepath.plugin.snapshot.data.CollectionLogData;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.inject.Singleton;

/**
 * Collects Collection Log progress counts from the live client.
 *
 * <p>Reads the account-stored obtained/total slot counts (overall and per tab) via the
 * stable {@link CollectionLogDefinitions} VarPlayer mapping. These counts are {@code 0}
 * until the game has synced the account's Collection Log data; when the overall total is
 * non-positive the section is reported {@code UNAVAILABLE} (data {@code null}) rather than
 * fabricating "0 obtained", so the website can tell "not yet known" apart from "empty".</p>
 *
 * <p>Read-only and interface-free — it never opens the Collection Log UI and never polls;
 * it reads whatever the game has already made available on the client thread.</p>
 */
@Singleton
public class CollectionLogCollector implements AccountDataCollector
{
	@Override
	public SnapshotSectionType type()
	{
		return SnapshotSectionType.COLLECTION_LOG;
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

		final int total = game.getVarpValue(CollectionLogDefinitions.OVERALL_MAX_VARP);
		if (total <= 0)
		{
			// Counts not yet populated by the game (account log data not synced this login).
			// Report UNAVAILABLE rather than a misleading zero.
			return CollectedSection.unavailable(type(), context.getSnapshotTime());
		}

		final int obtained = game.getVarpValue(CollectionLogDefinitions.OVERALL_OBTAINED_VARP);

		final List<CollectionLogData.TabCount> tabs = new ArrayList<>();
		for (CollectionLogDefinitions.TabDef tab : CollectionLogDefinitions.tabs())
		{
			tabs.add(new CollectionLogData.TabCount(
				tab.getId(),
				game.getVarpValue(tab.getObtainedVarpId()),
				game.getVarpValue(tab.getMaxVarpId())));
		}

		final CollectionLogData data = new CollectionLogData(
			obtained, total, Collections.unmodifiableList(tabs));

		return new CollectedSection(type(), SourceFreshness.COMPLETE, context.getSnapshotTime(), data);
	}
}
