/*
 * Copyright (c) 2026, ScapePath
 * All rights reserved. BSD 2-Clause. See LICENSE file.
 */
package com.scapepath.plugin.snapshot;

import java.time.Instant;
import javax.annotation.Nullable;
import lombok.Value;

/**
 * One section of an {@link AccountSnapshot}: a typed payload plus the metadata
 * describing when and how completely it was collected.
 *
 * <p>In Session 1 {@link #data} is always {@code null}: the contract exists but no
 * collector populates it.</p>
 */
@Value
public class CollectedSection
{
	SnapshotSectionType type;

	SourceFreshness freshness;

	Instant collectedAt;

	/** The typed payload, or {@code null} when unavailable / not yet implemented. */
	@Nullable
	SectionData data;

	/**
	 * Convenience factory for a section that could not be (or was not) collected.
	 */
	public static CollectedSection unavailable(SnapshotSectionType type, Instant at)
	{
		return new CollectedSection(type, SourceFreshness.UNAVAILABLE, at, null);
	}
}
