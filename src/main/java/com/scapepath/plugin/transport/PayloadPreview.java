/*
 * Copyright (c) 2026, ScapePath
 * All rights reserved. BSD 2-Clause. See LICENSE file.
 */
package com.scapepath.plugin.transport;

import java.util.List;
import javax.annotation.Nullable;
import lombok.Value;

/**
 * A local, read-only description of what the future sync payload would contain.
 *
 * <p>Purely diagnostic: it is produced from the current cached snapshot and is never
 * transmitted. Used by the panel to disclose the payload to the player.</p>
 */
@Value
public class PayloadPreview
{
	int schemaVersion;
	String pluginVersion;

	/** Snapshot timestamp (ISO-8601 UTC), or {@code null} if no snapshot yet. */
	@Nullable
	String timestamp;

	/** The full deterministic JSON that would be sent. */
	String json;

	/** UTF-8 size of {@link #json} in bytes. */
	int byteSize;

	/** One line per section that would be included, in canonical order. */
	List<SectionSummary> sections;

	@Value
	public static class SectionSummary
	{
		/** Stable section key (e.g. {@code "bank"}). */
		String key;
		/** Freshness name (e.g. {@code "COMPLETE"}, {@code "UNAVAILABLE"}). */
		String freshness;
	}
}
