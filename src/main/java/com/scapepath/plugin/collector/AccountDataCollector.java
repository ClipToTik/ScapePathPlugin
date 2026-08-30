/*
 * Copyright (c) 2026, ScapePath
 * All rights reserved. BSD 2-Clause. See LICENSE file.
 */
package com.scapepath.plugin.collector;

import com.scapepath.plugin.snapshot.CollectedSection;
import com.scapepath.plugin.snapshot.SnapshotSectionType;

/**
 * Contract for a single account-data collector.
 *
 * <p>Each collector is responsible for exactly one {@link SnapshotSectionType}
 * (Skills, Quests, Bank, …). This is the extension point that keeps the plugin
 * modular: future collectors are added by implementing this interface and
 * registering them, without touching the plugin core.</p>
 *
 * <p><b>No collectors are implemented in Session 1.</b> This interface defines the
 * contract only. Every implementation must remain passive and read-only: it may read
 * local account state exposed by the RuneLite API, and must never automate gameplay,
 * send inputs, or read credentials/cookies.</p>
 */
public interface AccountDataCollector
{
	/** The single section this collector produces. */
	SnapshotSectionType type();

	/**
	 * Whether the collector currently has enough context to produce meaningful data
	 * (e.g. the player is logged in, or the relevant interface has been opened).
	 * When {@code false}, {@link #collect} should return an
	 * {@link CollectedSection#unavailable} section.
	 */
	boolean isReady(CollectorContext context);

	/**
	 * Produce this collector's section. Implementations must be side-effect free with
	 * respect to the game (read-only) and must not perform network I/O.
	 */
	CollectedSection collect(CollectorContext context);
}
