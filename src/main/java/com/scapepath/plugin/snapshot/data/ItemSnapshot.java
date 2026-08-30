/*
 * Copyright (c) 2026, ScapePath
 * All rights reserved. BSD 2-Clause. See LICENSE file.
 */
package com.scapepath.plugin.snapshot.data;

import lombok.Value;

/**
 * A single stack of items within a container (inventory, equipment, or bank).
 *
 * <p>Canonical item id and quantity are the source of truth; names are intentionally
 * not stored here. {@code slot} is the container slot index (inventory slot, equipment
 * slot, or bank position) the stack occupies.</p>
 */
@Value
public class ItemSnapshot
{
	/** Canonical item id (RuneScape item id). */
	int id;

	/** Stack quantity (always &gt;= 1 for a real holding). */
	int quantity;

	/** Slot index within the container. */
	int slot;
}
