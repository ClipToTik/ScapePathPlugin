/*
 * Copyright (c) 2026, ScapePath
 * All rights reserved. BSD 2-Clause. See LICENSE file.
 */
package com.scapepath.plugin.snapshot;

import com.scapepath.plugin.collector.CollectorRegistry;
import com.scapepath.plugin.game.GameStateAccessor;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Holds the most recent {@link AccountSnapshot} and rebuilds it on demand from the
 * registered collectors.
 *
 * <p>This keeps snapshot assembly and caching out of the plugin class. It performs no
 * network I/O. {@link #rebuild()} reads the live game accessor and therefore must be
 * called on the client thread; the orchestration layer guarantees this.</p>
 */
@Slf4j
@Singleton
public class SnapshotService
{
	private final CollectorRegistry registry;
	private final GameStateAccessor game;

	private volatile AccountSnapshot latest;
	private volatile Consumer<AccountSnapshot> listener;

	@Inject
	SnapshotService(CollectorRegistry registry, GameStateAccessor game)
	{
		this.registry = registry;
		this.game = game;
	}

	/** The most recently built snapshot, or {@code null} if none has been built yet. */
	@Nullable
	public AccountSnapshot getLatest()
	{
		return latest;
	}

	/** Register a single listener (e.g. the diagnostic panel) notified on each rebuild. */
	public void setListener(@Nullable Consumer<AccountSnapshot> listener)
	{
		this.listener = listener;
	}

	/**
	 * Rebuild the snapshot from the live client and cache it. Must run on the client
	 * thread. Returns the new snapshot.
	 */
	public AccountSnapshot rebuild()
	{
		final AccountSnapshot snapshot = registry.buildSnapshot(game);
		this.latest = snapshot;
		log.debug("Rebuilt ScapePath snapshot: rsn={}, sections={}",
			snapshot.getRsn(), snapshot.getSections().keySet());

		final Consumer<AccountSnapshot> l = this.listener;
		if (l != null)
		{
			l.accept(snapshot);
		}
		return snapshot;
	}
}
