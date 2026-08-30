/*
 * Copyright (c) 2026, ScapePath
 * All rights reserved. BSD 2-Clause. See LICENSE file.
 */
package com.scapepath.plugin.connection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class ConnectionManagerTest
{
	@Test
	public void startsDisconnected()
	{
		ConnectionManager cm = new ConnectionManager();
		assertEquals(ConnectionState.DISCONNECTED, cm.getState());
		assertFalse(cm.isConnected());
	}

	@Test
	public void connectAndDisconnectTransitionLocalStateOnly()
	{
		ConnectionManager cm = new ConnectionManager();
		cm.connect();
		assertEquals(ConnectionState.CONNECTED, cm.getState());
		assertTrue(cm.isConnected());

		cm.disconnect();
		assertEquals(ConnectionState.DISCONNECTED, cm.getState());
		assertFalse(cm.isConnected());
	}

	@Test
	public void listenerReceivesTransitions()
	{
		ConnectionManager cm = new ConnectionManager();
		List<ConnectionState> seen = new ArrayList<>();
		cm.setStateListener(seen::add);

		cm.connect();
		cm.disconnect();

		assertEquals(2, seen.size());
		assertEquals(ConnectionState.CONNECTED, seen.get(0));
		assertEquals(ConnectionState.DISCONNECTED, seen.get(1));
	}

	@Test
	public void connectIsIdempotent()
	{
		ConnectionManager cm = new ConnectionManager();
		List<ConnectionState> seen = new ArrayList<>();
		cm.setStateListener(seen::add);

		cm.connect();
		cm.connect();

		assertEquals(1, seen.size());
	}
}
