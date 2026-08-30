/*
 * Copyright (c) 2026, ScapePath
 * All rights reserved. BSD 2-Clause. See LICENSE file.
 */
package com.scapepath.plugin.connection;

import com.scapepath.plugin.snapshot.AccountSnapshot;
import com.scapepath.plugin.snapshot.CollectedSection;
import com.scapepath.plugin.snapshot.SnapshotSectionType;
import com.scapepath.plugin.snapshot.SourceFreshness;
import com.scapepath.plugin.snapshot.data.IdentityData;
import com.scapepath.plugin.snapshot.data.SkillData;
import com.scapepath.plugin.snapshot.data.SkillsData;
import com.scapepath.plugin.transport.ScapePathTransport;
import com.scapepath.plugin.transport.SnapshotPayloadSerializer;
import java.time.Instant;
import java.util.Collections;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * Behavioural tests for the real (networked) {@link ConnectionManager}, using a fake
 * transport + token store and a synchronous executor so the async flow is deterministic.
 * These cover the link/sync/disconnect state machine, token handling, 401/429 handling,
 * offline behaviour, and auto-sync throttling.
 */
public class ConnectionManagerTest
{
	/** Runs submitted work inline — makes the async manager deterministic in tests. */
	private static final Executor DIRECT = Runnable::run;

	private static final SnapshotPayloadSerializer SERIALIZER = new SnapshotPayloadSerializer();

	/** A minimal but valid snapshot (identity + skills) for the supplier. */
	private static Supplier<AccountSnapshot> snapshotSupplier()
	{
		final Instant t = Instant.parse("2026-08-30T00:00:00Z");
		final IdentityData identity = new IdentityData(true, 123456789L, "Zezima", "IRONMAN", 330);
		final SkillsData skills = new SkillsData(
			Collections.singletonList(new SkillData("Attack", 80, 2_000_000)), 1500, 50_000_000L, 100);
		final AccountSnapshot snap = AccountSnapshot.builder()
			.timestamp(t)
			.pluginVersion("0.2.1")
			.rsn("Zezima")
			.section(SnapshotSectionType.IDENTITY,
				new CollectedSection(SnapshotSectionType.IDENTITY, SourceFreshness.COMPLETE, t, identity))
			.section(SnapshotSectionType.SKILLS,
				new CollectedSection(SnapshotSectionType.SKILLS, SourceFreshness.COMPLETE, t, skills))
			.build();
		return () -> snap;
	}

	/* ------------------------------ fakes ------------------------------ */

	private static final class FakeTokenStore implements TokenStore
	{
		private String token;

		FakeTokenStore(String initial)
		{
			this.token = initial;
		}

		@Override public String get() { return token; }
		@Override public void set(String t) { this.token = t; }
		@Override public void clear() { this.token = null; }
	}

	private static final class FakeTransport implements ScapePathTransport
	{
		LinkOutcome linkResult = LinkOutcome.success("device-token-abc", "Zezima");
		SyncOutcome syncResult = SyncOutcome.of(SyncOutcome.Kind.SUCCESS);
		int linkCalls;
		int syncCalls;
		int disconnectCalls;
		String lastTokenSeen;
		String lastPayloadSeen;

		@Override
		public LinkOutcome link(String code, Long accountHash, String rsn, String accountType)
		{
			linkCalls++;
			return linkResult;
		}

		@Override
		public SyncOutcome sync(String deviceToken, String payloadJson)
		{
			syncCalls++;
			lastTokenSeen = deviceToken;
			lastPayloadSeen = payloadJson;
			return syncResult;
		}

		@Override
		public void disconnect(String deviceToken)
		{
			disconnectCalls++;
		}
	}

	private ConnectionManager manager(FakeTokenStore store, FakeTransport transport)
	{
		final ConnectionManager cm = new ConnectionManager(store, transport, SERIALIZER, DIRECT);
		cm.setSnapshotSupplier(snapshotSupplier());
		return cm;
	}

	/* ------------------------------ tests ------------------------------ */

	@Test
	public void restoreConnectedWhenTokenPresent()
	{
		final ConnectionManager cm = manager(new FakeTokenStore("existing-token"), new FakeTransport());
		cm.restore();
		assertEquals(ConnectionState.CONNECTED, cm.getState());
		assertTrue(cm.isConnected());
	}

	@Test
	public void restoreDisconnectedWhenNoToken()
	{
		final ConnectionManager cm = manager(new FakeTokenStore(null), new FakeTransport());
		cm.restore();
		assertEquals(ConnectionState.DISCONNECTED, cm.getState());
		assertFalse(cm.isConnected());
	}

	@Test
	public void linkSuccessStoresTokenAndRunsInitialSync()
	{
		final FakeTokenStore store = new FakeTokenStore(null);
		final FakeTransport transport = new FakeTransport();
		final ConnectionManager cm = manager(store, transport);

		cm.link("ABCD-2345");

		assertEquals(1, transport.linkCalls);
		assertEquals("device-token-abc", store.get());
		assertTrue(cm.isConnected());
		assertEquals(1, transport.syncCalls); // initial sync fired
		assertEquals("device-token-abc", transport.lastTokenSeen);
		assertEquals(ConnectionState.CONNECTED, cm.getState());
	}

	@Test
	public void linkInvalidCodeDoesNotStoreToken()
	{
		final FakeTokenStore store = new FakeTokenStore(null);
		final FakeTransport transport = new FakeTransport();
		transport.linkResult = ScapePathTransport.LinkOutcome.of(ScapePathTransport.LinkOutcome.Kind.INVALID_CODE);
		final ConnectionManager cm = manager(store, transport);

		cm.link("WRONG-CODE");

		assertNull(store.get());
		assertFalse(cm.isConnected());
		assertEquals(ConnectionState.ERROR, cm.getState());
		assertEquals(0, transport.syncCalls);
	}

	@Test
	public void linkConflictIsAnError()
	{
		final FakeTransport transport = new FakeTransport();
		transport.linkResult = ScapePathTransport.LinkOutcome.of(ScapePathTransport.LinkOutcome.Kind.CONFLICT);
		final ConnectionManager cm = manager(new FakeTokenStore(null), transport);
		cm.link("ABCD-2345");
		assertEquals(ConnectionState.ERROR, cm.getState());
	}

	@Test
	public void linkNetworkErrorIsAnError()
	{
		final FakeTransport transport = new FakeTransport();
		transport.linkResult = ScapePathTransport.LinkOutcome.of(ScapePathTransport.LinkOutcome.Kind.NETWORK_ERROR);
		final ConnectionManager cm = manager(new FakeTokenStore(null), transport);
		cm.link("ABCD-2345");
		assertEquals(ConnectionState.ERROR, cm.getState());
		assertFalse(cm.isConnected());
	}

	@Test
	public void emptyCodeMakesNoNetworkCall()
	{
		final FakeTransport transport = new FakeTransport();
		final ConnectionManager cm = manager(new FakeTokenStore(null), transport);
		cm.link("   ");
		assertEquals(0, transport.linkCalls);
		assertEquals(ConnectionState.ERROR, cm.getState());
	}

	@Test
	public void syncNowWithoutTokenMakesNoRequest()
	{
		final FakeTransport transport = new FakeTransport();
		final ConnectionManager cm = manager(new FakeTokenStore(null), transport);
		cm.syncNow();
		assertEquals(0, transport.syncCalls);
	}

	@Test
	public void syncSuccessUpdatesLastSync()
	{
		final FakeTransport transport = new FakeTransport();
		final ConnectionManager cm = manager(new FakeTokenStore("t"), transport);
		cm.syncNow();
		assertEquals(1, transport.syncCalls);
		assertEquals(ConnectionState.CONNECTED, cm.getState());
		assertTrue(cm.getLastSyncAt() != null);
	}

	@Test
	public void unauthorizedSyncClearsTokenAndDisconnects()
	{
		final FakeTokenStore store = new FakeTokenStore("stale-token");
		final FakeTransport transport = new FakeTransport();
		transport.syncResult = ScapePathTransport.SyncOutcome.of(ScapePathTransport.SyncOutcome.Kind.UNAUTHORIZED);
		final ConnectionManager cm = manager(store, transport);

		cm.syncNow();

		assertNull(store.get()); // local token cleared
		assertFalse(cm.isConnected());
		assertEquals(ConnectionState.DISCONNECTED, cm.getState());
	}

	@Test
	public void rateLimitedSyncSetsCooldownAndSuppressesNextSync()
	{
		final FakeTransport transport = new FakeTransport();
		transport.syncResult = ScapePathTransport.SyncOutcome.rateLimited(60);
		final ConnectionManager cm = manager(new FakeTokenStore("t"), transport);

		cm.syncNow();
		assertEquals(1, transport.syncCalls);
		assertEquals(ConnectionState.CONNECTED, cm.getState()); // not an error

		// Within the cooldown, another sync is suppressed before hitting the network.
		cm.syncNow();
		assertEquals(1, transport.syncCalls);
	}

	@Test
	public void networkErrorSyncGoesOffline()
	{
		final FakeTransport transport = new FakeTransport();
		transport.syncResult = ScapePathTransport.SyncOutcome.of(ScapePathTransport.SyncOutcome.Kind.NETWORK_ERROR);
		final ConnectionManager cm = manager(new FakeTokenStore("t"), transport);
		cm.syncNow();
		assertEquals(ConnectionState.OFFLINE, cm.getState());
		assertTrue(cm.isConnected()); // still linked; will retry
	}

	@Test
	public void disconnectRevokesAndClearsToken()
	{
		final FakeTokenStore store = new FakeTokenStore("t");
		final FakeTransport transport = new FakeTransport();
		final ConnectionManager cm = manager(store, transport);

		cm.disconnect();

		assertEquals(1, transport.disconnectCalls);
		assertNull(store.get());
		assertFalse(cm.isConnected());
		assertEquals(ConnectionState.DISCONNECTED, cm.getState());
	}

	@Test
	public void autoSyncIsThrottled()
	{
		final FakeTransport transport = new FakeTransport();
		final ConnectionManager cm = manager(new FakeTokenStore("t"), transport);

		cm.maybeAutoSync(true);
		assertEquals(1, transport.syncCalls);

		// Immediately again: throttled well under AUTO_SYNC_MIN_GAP.
		cm.maybeAutoSync(true);
		assertEquals(1, transport.syncCalls);
	}

	@Test
	public void autoSyncDisabledDoesNothing()
	{
		final FakeTransport transport = new FakeTransport();
		final ConnectionManager cm = manager(new FakeTokenStore("t"), transport);
		cm.maybeAutoSync(false);
		assertEquals(0, transport.syncCalls);
	}

	@Test
	public void syncSendsSchemaVersion1Payload()
	{
		final FakeTransport transport = new FakeTransport();
		final ConnectionManager cm = manager(new FakeTokenStore("t"), transport);
		cm.syncNow();
		assertTrue(transport.lastPayloadSeen.contains("\"schemaVersion\":1"));
	}

	@Test
	public void disconnectStopsFutureAutomaticSync()
	{
		final FakeTokenStore store = new FakeTokenStore("t");
		final FakeTransport transport = new FakeTransport();
		final ConnectionManager cm = manager(store, transport);

		cm.disconnect();
		final int afterDisconnect = transport.syncCalls;

		// No token means auto-sync and manual sync are both no-ops.
		cm.maybeAutoSync(true);
		cm.syncNow();
		assertEquals(afterDisconnect, transport.syncCalls);
	}

	@Test
	public void unauthorizedSyncStopsFurtherSyncing()
	{
		final FakeTokenStore store = new FakeTokenStore("stale-token");
		final FakeTransport transport = new FakeTransport();
		transport.syncResult = ScapePathTransport.SyncOutcome.of(ScapePathTransport.SyncOutcome.Kind.UNAUTHORIZED);
		final ConnectionManager cm = manager(store, transport);

		cm.syncNow();
		assertEquals(1, transport.syncCalls);

		// Token was cleared by the 401; no further authenticated requests are made.
		cm.syncNow();
		cm.maybeAutoSync(true);
		assertEquals(1, transport.syncCalls);
	}

	@Test
	public void reconnectAfterDisconnectLinksAgain()
	{
		final FakeTokenStore store = new FakeTokenStore("t");
		final FakeTransport transport = new FakeTransport();
		final ConnectionManager cm = manager(store, transport);

		cm.disconnect();
		assertFalse(cm.isConnected());

		cm.link("ABCD-2345");
		assertTrue(cm.isConnected());
		assertEquals("device-token-abc", store.get());
		assertEquals(ConnectionState.CONNECTED, cm.getState());
	}

	@Test
	public void syncWithNullSnapshotMakesNoRequest()
	{
		final FakeTransport transport = new FakeTransport();
		final ConnectionManager cm = new ConnectionManager(
			new FakeTokenStore("t"), transport, SERIALIZER, DIRECT);
		cm.setSnapshotSupplier(() -> null); // nothing collected yet
		cm.syncNow();
		assertEquals(0, transport.syncCalls);
	}
}
