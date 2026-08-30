/*
 * Copyright (c) 2026, ScapePath
 * All rights reserved. BSD 2-Clause. See LICENSE file.
 */
package com.scapepath.plugin.snapshot.data;

import com.scapepath.plugin.snapshot.SectionData;
import java.util.List;
import lombok.Value;

/**
 * Typed payload for the {@code EQUIPMENT} section: currently worn items.
 *
 * <p>Each {@link ItemSnapshot#getSlot()} is the equipment slot index
 * (see {@code net.runelite.api.EquipmentInventorySlot}); empty slots are omitted.</p>
 */
@Value
public class EquipmentData implements SectionData
{
	/** One entry per occupied equipment slot. */
	List<ItemSnapshot> items;
}
