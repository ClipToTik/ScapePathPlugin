/*
 * Copyright (c) 2026, ScapePath
 * All rights reserved. BSD 2-Clause. See LICENSE file.
 */
package com.scapepath.plugin.snapshot.data;

import com.scapepath.plugin.snapshot.SectionData;
import java.util.List;
import lombok.Value;

/**
 * Typed payload for the {@code BANK} section.
 *
 * <p>Bank data is interface-gated: it only exists once the player has opened their bank.
 * The <em>freshness</em> (current vs. stale) and the collection timestamp are carried by
 * the enclosing {@link com.scapepath.plugin.snapshot.CollectedSection}. A never-opened
 * bank is represented as an {@code UNAVAILABLE} section with no {@code BankData} at all
 * &mdash; never as an empty {@code BankData}.</p>
 */
@Value
public class BankData implements SectionData
{
	/** Where this data came from; always the in-game bank interface. */
	public static final String SOURCE_BANK_INTERFACE = "BANK_INTERFACE";

	/** One entry per distinct bank stack (may be empty for a genuinely empty bank). */
	List<ItemSnapshot> items;

	/** Number of distinct item stacks. */
	int uniqueItems;

	/** Coins held in the bank (item id 995), or 0 if none. */
	long coins;

	/**
	 * Estimated total bank value from locally-cached prices (quantity &times; price).
	 * An <em>estimate</em>, not a guaranteed liquidation value.
	 */
	long estimatedValue;

	/** Provenance marker; see {@link #SOURCE_BANK_INTERFACE}. */
	String source;
}
