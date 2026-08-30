/*
 * Copyright (c) 2026, ScapePath
 * All rights reserved. BSD 2-Clause. See LICENSE file.
 */
package com.scapepath.plugin.connection;

/**
 * Local, client-side view of the ScapePath connection.
 *
 * <p>In Session 1 these states are purely local UI state. No state here implies
 * any network activity has occurred &mdash; the transport layer is intentionally
 * unimplemented.</p>
 */
public enum ConnectionState
{
	/** Not connected. Default state. */
	DISCONNECTED("Not connected"),
	/** A connection attempt is in progress (future: token validation over HTTPS). */
	CONNECTING("Connecting…"),
	/** Connected to ScapePath. */
	CONNECTED("Connected"),
	/** The last connection attempt failed. */
	ERROR("Connection error");

	private final String displayText;

	ConnectionState(String displayText)
	{
		this.displayText = displayText;
	}

	public String getDisplayText()
	{
		return displayText;
	}
}
