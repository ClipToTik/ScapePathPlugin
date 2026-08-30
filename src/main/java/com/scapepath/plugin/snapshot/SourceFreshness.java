/*
 * Copyright (c) 2026, ScapePath
 * All rights reserved. BSD 2-Clause. See LICENSE file.
 */
package com.scapepath.plugin.snapshot;

/**
 * Freshness/source metadata for a collected section.
 *
 * <p>Some RuneScape data (e.g. bank, collection log) is only visible to the client
 * when the player opens the relevant interface. Such sections must be modelled as
 * partial/event-driven rather than assumed complete &mdash; hence this qualifier.</p>
 */
public enum SourceFreshness
{
	/** Data fully reflects current account state. */
	COMPLETE,
	/** Only part of the section is known (e.g. bank not fully opened). */
	PARTIAL,
	/** Data was captured earlier and may be out of date. */
	STALE,
	/** The section could not be read at all in this snapshot. */
	UNAVAILABLE
}
