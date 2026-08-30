/*
 * Copyright (c) 2026, ScapePath
 * All rights reserved. BSD 2-Clause. See LICENSE file.
 */
package com.scapepath.plugin.game;

import com.scapepath.plugin.snapshot.data.ItemSnapshot;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import javax.inject.Singleton;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

/**
 * Holds the interface-gated bank state across snapshot rebuilds.
 *
 * <p>The bank container only exists in the client once the player has opened their bank.
 * This tracker records the last known bank contents, when they were read, and whether
 * the bank is currently open, so the {@code BankCollector} can distinguish three states
 * that must never be conflated:</p>
 * <ul>
 *   <li><b>never opened</b> &rarr; unavailable (no data at all);</li>
 *   <li><b>open now</b> &rarr; current;</li>
 *   <li><b>closed but previously opened</b> &rarr; stale (cached).</li>
 * </ul>
 *
 * <p>It is fed by the plugin's event handlers on the client thread and reset on logout
 * so one account's bank is never presented as another's.</p>
 */
@Slf4j
@Singleton
public class BankTracker
{
	@Nullable
	private volatile List<ItemSnapshot> items;
	@Nullable
	private volatile Instant collectedAt;
	private volatile boolean open;

	/** Mark the bank interface as open or closed. */
	public void setOpen(boolean open)
	{
		this.open = open;
	}

	/** Cache a fresh read of the bank contents (taken while the bank is open). */
	public void updateItems(List<ItemSnapshot> bankItems, Instant at)
	{
		this.items = Collections.unmodifiableList(bankItems);
		this.collectedAt = at;
		log.debug("Cached bank snapshot: {} stacks at {}", bankItems.size(), at);
	}

	/** Clear all state (e.g. on logout) so no cross-account data leaks. */
	public void reset()
	{
		this.items = null;
		this.collectedAt = null;
		this.open = false;
	}

	/** {@code true} once the bank has been opened at least once this session. */
	public boolean hasBeenSynced()
	{
		return items != null;
	}

	public boolean isOpen()
	{
		return open;
	}

	/** An immutable view of the current tracker state. */
	public Snapshot view()
	{
		return new Snapshot(items, collectedAt, open);
	}

	@Value
	public static class Snapshot
	{
		@Nullable
		List<ItemSnapshot> items;
		@Nullable
		Instant collectedAt;
		boolean open;

		public boolean hasBeenSynced()
		{
			return items != null;
		}
	}
}
