/*
 * Copyright (c) 2026, ScapePath
 * All rights reserved. BSD 2-Clause. See LICENSE file.
 */
package com.scapepath.plugin.snapshot.data;

import com.scapepath.plugin.snapshot.SectionData;
import javax.annotation.Nullable;
import lombok.Value;

/**
 * Typed payload for the {@code WEALTH} section.
 *
 * <p>GP on hand is derived from the inventory coin stack. Bank figures are present only
 * when the bank has been synced; when it has not, they are {@code null} (distinct from
 * zero). Bank value is an <em>estimate</em> from locally-cached prices.</p>
 */
@Value
public class WealthData implements SectionData
{
	/** Coins in the inventory (item id 995). */
	long gpOnHand;

	/** Coins in the bank, or {@code null} if the bank has not been synced. */
	@Nullable
	Long bankGp;

	/** Estimated bank value from local prices, or {@code null} if the bank is unsynced. */
	@Nullable
	Long estimatedBankValue;
}
