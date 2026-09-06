/*
 * Copyright (c) 2026, ScapePath
 * All rights reserved. BSD 2-Clause. See LICENSE file.
 */
package com.scapepath.plugin.snapshot.data;

import com.scapepath.plugin.snapshot.SectionData;
import java.util.List;
import lombok.Value;

/**
 * Typed payload for the {@code COLLECTION_LOG} section: the account-stored progress counts.
 *
 * <p>This is the Collection Log foundation — authoritative obtained/total slot counts,
 * overall and per tab, using stable RuneLite identifiers. It carries no per-item detail and
 * no item metadata (names, prices, sources are ScapePath's responsibility). Per-item
 * obtained enumeration is interface-gated in RuneLite and is a documented follow-up.</p>
 */
@Value
public class CollectionLogData implements SectionData
{
	/** Number of unique Collection Log slots obtained across all tabs. */
	int obtained;

	/** Total number of Collection Log slots that exist (as reported by the game). */
	int total;

	/** Per-tab breakdown, in a fixed order. */
	List<TabCount> tabs;

	/** Obtained/total counts for one Collection Log tab. */
	@Value
	public static class TabCount
	{
		/** Stable tab identifier, e.g. {@code "BOSSES"}. */
		String id;
		int obtained;
		int total;
	}
}
