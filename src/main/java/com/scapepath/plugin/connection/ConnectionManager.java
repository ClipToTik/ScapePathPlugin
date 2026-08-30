/*
 * Copyright (c) 2026, ScapePath
 * All rights reserved. BSD 2-Clause. See LICENSE file.
 */
package com.scapepath.plugin.connection;

import java.util.function.Consumer;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Owns the client-side ScapePath connection lifecycle.
 *
 * <p><b>Session 1 scope:</b> this class manages <em>local</em> {@link ConnectionState}
 * only. It performs no authentication and no network I/O. The methods below are the
 * seams where a future HTTPS transport &mdash; validating a player-entered ScapePath
 * token &mdash; will be wired in. Nothing here reads, holds, or transmits account data,
 * credentials, cookies, or session tokens.</p>
 */
@Slf4j
@Singleton
public class ConnectionManager
{
	private volatile ConnectionState state = ConnectionState.DISCONNECTED;

	/** Optional listener notified on state changes (e.g. to refresh the config UI). */
	private volatile Consumer<ConnectionState> stateListener;

	public ConnectionState getState()
	{
		return state;
	}

	public boolean isConnected()
	{
		return state == ConnectionState.CONNECTED;
	}

	public void setStateListener(Consumer<ConnectionState> listener)
	{
		this.stateListener = listener;
	}

	/**
	 * Begin a connection.
	 *
	 * <p>Session 1: transitions local state only. There is intentionally no token
	 * validation, HTTP request, or handshake here yet. Session 2 will replace the body
	 * with an HTTPS validation of a player-supplied ScapePath token.</p>
	 */
	public synchronized void connect()
	{
		if (state == ConnectionState.CONNECTED)
		{
			return;
		}
		log.debug("ScapePath connect requested (local-only, no network in Session 1)");
		// TODO(Session 2): validate a player-entered token over HTTPS before CONNECTED.
		transition(ConnectionState.CONNECTED);
	}

	/**
	 * Tear down the connection. Local-only in Session 1.
	 */
	public synchronized void disconnect()
	{
		if (state == ConnectionState.DISCONNECTED)
		{
			return;
		}
		log.debug("ScapePath disconnect requested (local-only)");
		transition(ConnectionState.DISCONNECTED);
	}

	private void transition(ConnectionState next)
	{
		this.state = next;
		final Consumer<ConnectionState> listener = this.stateListener;
		if (listener != null)
		{
			listener.accept(next);
		}
	}
}
