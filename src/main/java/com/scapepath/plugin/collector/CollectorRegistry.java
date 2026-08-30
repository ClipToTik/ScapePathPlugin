/*
 * Copyright (c) 2026, ScapePath
 * All rights reserved. BSD 2-Clause. See LICENSE file.
 */
package com.scapepath.plugin.collector;

import com.scapepath.plugin.game.GameStateAccessor;
import com.scapepath.plugin.snapshot.AccountSnapshot;
import com.scapepath.plugin.snapshot.CollectedSection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Holds the set of registered {@link AccountDataCollector}s and assembles them into an
 * {@link AccountSnapshot}.
 *
 * <p>Session 1 registers <b>no</b> collectors, so {@link #buildSnapshot} always returns
 * an empty snapshot. The registry exists to give future collectors a single place to
 * plug in, and to keep snapshot assembly out of the plugin class.</p>
 */
@Slf4j
@Singleton
public class CollectorRegistry
{
	private final List<AccountDataCollector> collectors = new ArrayList<>();

	public void register(AccountDataCollector collector)
	{
		collectors.add(collector);
		log.debug("Registered ScapePath collector: {}", collector.type());
	}

	public List<AccountDataCollector> getCollectors()
	{
		return Collections.unmodifiableList(collectors);
	}

	/**
	 * Assemble a snapshot from the currently registered collectors, reading the given
	 * game accessor.
	 *
	 * <p>Performs no network I/O. Must be called on the client thread when {@code game}
	 * is the live accessor. Top-level RSN is derived from the accessor; each collector
	 * contributes its own section (or an {@code UNAVAILABLE} section when not ready).</p>
	 */
	public AccountSnapshot buildSnapshot(GameStateAccessor game)
	{
		final CollectorContext context = CollectorContext.of(game);
		final String rsn = game.isLoggedIn() ? game.getPlayerName() : null;
		final AccountSnapshot.AccountSnapshotBuilder builder = AccountSnapshot.builder()
			.timestamp(context.getSnapshotTime())
			.pluginVersion(context.getPluginVersion())
			.rsn(rsn);

		for (AccountDataCollector collector : collectors)
		{
			final CollectedSection section = collector.isReady(context)
				? collector.collect(context)
				: CollectedSection.unavailable(collector.type(), context.getSnapshotTime());
			builder.section(section.getType(), section);
		}

		return builder.build();
	}
}
