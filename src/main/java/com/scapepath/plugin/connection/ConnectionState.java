/*
 * Copyright (c) 2026, ScapePath
 * All rights reserved. BSD 2-Clause. See LICENSE file.
 */
package com.scapepath.plugin.connection;

/**
 * Local, client-side view of the ScapePath connection.
 *
 * <p>These states drive the side-panel connection UI and reflect the real
 * {@link ConnectionManager} lifecycle: {@link #CONNECTING}/{@link #SYNCING} occur only
 * during an actual HTTPS exchange, and {@link #DISCONNECTED} means no device token is
 * held and no authenticated traffic is sent.</p>
 */
public enum ConnectionState
{
	/** Not connected. Default state. */
	DISCONNECTED("Not connected"),
	/** A connection attempt is in progress (link-code exchange over HTTPS). */
	CONNECTING("Connecting…"),
	/** Connected to ScapePath. */
	CONNECTED("Connected"),
	/** Connected and a snapshot upload is in flight. */
	SYNCING("Syncing…"),
	/** Connected but the last sync could not reach ScapePath (network down). */
	OFFLINE("Offline — will retry"),
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
