/*
 * Copyright (c) 2026, ScapePath
 * All rights reserved. BSD 2-Clause. See LICENSE file.
 */
package com.scapepath.plugin.collector;

import com.scapepath.plugin.ScapePath;
import com.scapepath.plugin.game.GameStateAccessor;
import java.time.Instant;
import lombok.Value;

/**
 * Read-only context handed to collectors when a snapshot is assembled.
 *
 * <p>Carries the single {@link GameStateAccessor} seam plus snapshot metadata. Because
 * collectors receive their game access only through this context, they never reach into
 * global state and are trivially testable with a fake accessor.</p>
 *
 * <p><b>Threading:</b> the accessor reads live client state, so a context built from the
 * live accessor must be used on the client thread. The orchestration layer guarantees
 * this.</p>
 */
@Value
public class CollectorContext
{
	/** The moment snapshot assembly began; collectors stamp their sections with it. */
	Instant snapshotTime;

	/** Plugin version producing the snapshot. */
	String pluginVersion;

	/** Read-only view of the game client. */
	GameStateAccessor game;

	public static CollectorContext of(GameStateAccessor game)
	{
		return new CollectorContext(Instant.now(), ScapePath.VERSION, game);
	}
}
