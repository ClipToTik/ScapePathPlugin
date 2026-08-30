/*
 * Copyright (c) 2026, ScapePath
 * All rights reserved. BSD 2-Clause. See LICENSE file.
 */
package com.scapepath.plugin.snapshot.data;

import com.scapepath.plugin.snapshot.SectionData;
import java.util.List;
import lombok.Value;

/**
 * Typed payload for the {@code INVENTORY} section: the occupied inventory slots.
 */
@Value
public class InventoryData implements SectionData
{
	/** One entry per occupied slot (empty slots omitted). */
	List<ItemSnapshot> items;

	/** Number of occupied slots (0&ndash;28). */
	int occupiedSlots;
}
