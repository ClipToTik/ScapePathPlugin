/*
 * Copyright (c) 2026, ScapePath
 * All rights reserved. BSD 2-Clause. See LICENSE file.
 */
package com.scapepath.plugin.connection;

import javax.annotation.Nullable;

/**
 * Local, revocable storage for the ScapePath device token.
 *
 * <p>Abstracted behind an interface so {@link ConnectionManager} is testable without
 * RuneLite's {@code ConfigManager}. The token is local to this RuneLite installation,
 * survives restarts, is never a Jagex/RuneLite credential, and is never rendered in the
 * UI or written to a log by any implementation.</p>
 */
public interface TokenStore
{
	/** The stored device token, or {@code null} when not linked. */
	@Nullable
	String get();

	/** Persist the device token (overwrites any previous value). */
	void set(String token);

	/** Remove the stored token (on disconnect / revocation). */
	void clear();

	default boolean has()
	{
		final String t = get();
		return t != null && !t.isEmpty();
	}
}
