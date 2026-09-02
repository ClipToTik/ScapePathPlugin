/*
 * Copyright (c) 2026, ScapePath
 * All rights reserved. BSD 2-Clause. See LICENSE file.
 */
package com.scapepath.plugin.transport;

import javax.annotation.Nullable;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

/**
 * Proves the authoritative networking gate at the transport boundary: with the opt-in
 * disabled, {@link GatedScapePathTransport} never invokes the underlying (networking)
 * transport for ANY method — even when called directly, bypassing the ConnectionManager.
 * This is the property the maintainer requires: config false => zero outbound networking.
 */
public class GatedScapePathTransportTest
{
	/** Counts delegate invocations; a call here would mean a real network request fired. */
	private static final class CountingTransport implements ScapePathTransport
	{
		int linkCalls;
		int syncCalls;
		int disconnectCalls;

		@Override
		public LinkOutcome link(String code, @Nullable Long accountHash, @Nullable String rsn,
			@Nullable String accountType)
		{
			linkCalls++;
			return LinkOutcome.success("token", rsn);
		}

		@Override
		public SyncOutcome sync(String deviceToken, String payloadJson)
		{
			syncCalls++;
			return SyncOutcome.of(SyncOutcome.Kind.SUCCESS);
		}

		@Override
		public void disconnect(String deviceToken)
		{
			disconnectCalls++;
		}
	}

	@Test
	public void disabledBlocksEveryNetworkMethod()
	{
		final CountingTransport delegate = new CountingTransport();
		final GatedScapePathTransport gated = new GatedScapePathTransport(delegate, () -> false);

		final ScapePathTransport.LinkOutcome link = gated.link("CODE", 1L, "rsn", "IRONMAN");
		final ScapePathTransport.SyncOutcome sync = gated.sync("token", "{}");
		gated.disconnect("token");

		// The real transport was never touched — no request left the process.
		assertEquals(0, delegate.linkCalls);
		assertEquals(0, delegate.syncCalls);
		assertEquals(0, delegate.disconnectCalls);

		// And callers get safe, non-authenticating outcomes.
		assertEquals(ScapePathTransport.LinkOutcome.Kind.NETWORK_ERROR, link.getKind());
		assertEquals(ScapePathTransport.SyncOutcome.Kind.NETWORK_ERROR, sync.getKind());
	}

	@Test
	public void gateIsReReadPerCallNotCachedAtConstruction()
	{
		final CountingTransport delegate = new CountingTransport();
		final boolean[] allowed = {false};
		final GatedScapePathTransport gated = new GatedScapePathTransport(delegate, () -> allowed[0]);

		gated.sync("token", "{}");
		assertEquals(0, delegate.syncCalls); // disabled: blocked

		allowed[0] = true;                   // user opts in
		gated.sync("token", "{}");
		assertEquals(1, delegate.syncCalls); // now permitted

		allowed[0] = false;                  // user opts back out
		gated.sync("token", "{}");
		assertEquals(1, delegate.syncCalls); // blocked again, no cached "allow"
	}

	@Test
	public void enabledPassesThroughToDelegate()
	{
		final CountingTransport delegate = new CountingTransport();
		final GatedScapePathTransport gated = new GatedScapePathTransport(delegate, () -> true);

		gated.link("CODE", 1L, "rsn", "IRONMAN");
		gated.sync("token", "{}");
		gated.disconnect("token");

		assertEquals(1, delegate.linkCalls);
		assertEquals(1, delegate.syncCalls);
		assertEquals(1, delegate.disconnectCalls);
	}
}
