/*
 * Copyright (c) 2026, ScapePath
 * All rights reserved. BSD 2-Clause. See LICENSE file.
 */
package com.scapepath.plugin.snapshot;

import java.time.Instant;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import javax.annotation.Nullable;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;

/**
 * A normalized, immutable snapshot of an account's state at a point in time.
 *
 * <p>This is the foundation model ScapePath will eventually transmit (over HTTPS,
 * with explicit player disclosure). Session 1 defines the shape only: no real account
 * data is read, and instances produced this session contain no sections.</p>
 *
 * <p>Sections are keyed by {@link SnapshotSectionType} so the model extends to new
 * data categories without changing this class.</p>
 */
@Value
@Builder
public class AccountSnapshot
{
	/** When this snapshot was assembled. */
	Instant timestamp;

	/** ScapePath plugin version that produced the snapshot. */
	String pluginVersion;

	/** Account display name (RSN), or {@code null} when not logged in / unknown. */
	@Nullable
	String rsn;

	/** Collected sections, keyed by category. May be empty. */
	@Singular
	Map<SnapshotSectionType, CollectedSection> sections;

	@Nullable
	public CollectedSection getSection(SnapshotSectionType type)
	{
		return sections.get(type);
	}

	/**
	 * An empty snapshot &mdash; the only kind Session 1 can legitimately produce.
	 */
	public static AccountSnapshot empty(String pluginVersion, Instant at, @Nullable String rsn)
	{
		return AccountSnapshot.builder()
			.timestamp(at)
			.pluginVersion(pluginVersion)
			.rsn(rsn)
			.sections(Collections.unmodifiableMap(new EnumMap<>(SnapshotSectionType.class)))
			.build();
	}
}
