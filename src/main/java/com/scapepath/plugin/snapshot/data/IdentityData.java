/*
 * Copyright (c) 2026, ScapePath
 * All rights reserved. BSD 2-Clause. See LICENSE file.
 */
package com.scapepath.plugin.snapshot.data;

import com.scapepath.plugin.snapshot.SectionData;
import javax.annotation.Nullable;
import lombok.Value;

/**
 * Typed payload for the {@code IDENTITY} snapshot section.
 *
 * <p>Normalized and decoupled from RuneLite API types so it is transport-ready in a
 * later session. Contains no credentials and no personal information beyond the public
 * in-game display name.</p>
 */
@Value
public class IdentityData implements SectionData
{
	/** Whether the client was logged in when this was captured. */
	boolean loggedIn;

	/** Stable per-account id, or {@code -1} when unavailable. Not a credential. */
	long accountHash;

	/** In-game display name (RSN), or {@code null} when unavailable. */
	@Nullable
	String rsn;

	/** Normalized account type name (e.g. {@code "NORMAL"}, {@code "IRONMAN"}), or {@code null}. */
	@Nullable
	String accountType;

	/** World number, or {@code 0} when unavailable. */
	int world;
}
