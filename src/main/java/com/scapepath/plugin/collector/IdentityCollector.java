/*
 * Copyright (c) 2026, ScapePath
 * All rights reserved. BSD 2-Clause. See LICENSE file.
 */
package com.scapepath.plugin.collector;

import com.scapepath.plugin.game.GameStateAccessor;
import com.scapepath.plugin.game.OsrsAccountType;
import com.scapepath.plugin.snapshot.CollectedSection;
import com.scapepath.plugin.snapshot.SnapshotSectionType;
import com.scapepath.plugin.snapshot.SourceFreshness;
import com.scapepath.plugin.snapshot.data.IdentityData;
import javax.inject.Singleton;

/**
 * Collects basic account identity from the live client.
 *
 * <p>Fields: account hash (stable id, not a credential), RSN (public display name),
 * account type, and world. Purely read-only.</p>
 */
@Singleton
public class IdentityCollector implements AccountDataCollector
{
	@Override
	public SnapshotSectionType type()
	{
		return SnapshotSectionType.IDENTITY;
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

		final OsrsAccountType accountType = OsrsAccountType.fromVarbit(game.getAccountTypeVarbit());
		final IdentityData data = new IdentityData(
			true,
			game.getAccountHash(),
			game.getPlayerName(),
			accountType.name(),
			game.getWorld()
		);

		return new CollectedSection(type(), SourceFreshness.COMPLETE, context.getSnapshotTime(), data);
	}
}
