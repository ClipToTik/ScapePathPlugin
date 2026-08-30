/*
 * Copyright (c) 2026, ScapePath
 * All rights reserved. BSD 2-Clause. See LICENSE file.
 */
package com.scapepath.plugin.connection;

import com.scapepath.plugin.snapshot.AccountSnapshot;
import com.scapepath.plugin.snapshot.SnapshotSectionType;
import com.scapepath.plugin.snapshot.data.IdentityData;
import com.scapepath.plugin.transport.ScapePathTransport;
import com.scapepath.plugin.transport.SnapshotPayloadSerializer;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Owns the ScapePath connection lifecycle: link-code exchange, device-token storage,
 * and snapshot syncing. All network work is dispatched to a background {@link Executor};
 * public methods return immediately so the RuneLite UI/client thread is never blocked,
 * and a ScapePath outage can never crash or stall the client.
 *
 * <p>Security posture: the only credential here is the opaque, revocable ScapePath device
 * token, held via {@link TokenStore} and never logged or shown. No Jagex/RuneLite
 * credential, cookie, or session token is ever read or sent. The server derives the
 * ScapePath user from the token; the plugin never declares a user id.</p>
 */
@Slf4j
@Singleton
public class ConnectionManager
{
	/** Minimum gap between automatic syncs. Manual "Sync now" bypasses this. */
	static final Duration AUTO_SYNC_MIN_GAP = Duration.ofMinutes(5);

	private final TokenStore tokenStore;
	private final ScapePathTransport transport;
	private final SnapshotPayloadSerializer serializer;
	private final Executor executor;

	private volatile ConnectionState state = ConnectionState.DISCONNECTED;
	@Nullable private volatile String lastError;
	@Nullable private volatile Instant lastSyncAt;
	private volatile Instant retryCooldownUntil = Instant.EPOCH;
	private volatile Instant lastAutoSyncAttempt = Instant.EPOCH;
	/** Guards against two syncs running at once (e.g. manual "Sync now" during an auto-sync). */
	private final java.util.concurrent.atomic.AtomicBoolean syncInFlight =
		new java.util.concurrent.atomic.AtomicBoolean(false);

	@Nullable private volatile Consumer<ConnectionState> stateListener;
	private volatile Supplier<AccountSnapshot> snapshotSupplier = () -> null;

	@Inject
	public ConnectionManager(TokenStore tokenStore, ScapePathTransport transport,
		SnapshotPayloadSerializer serializer, ScheduledExecutorService executor)
	{
		this(tokenStore, transport, serializer, (Executor) executor);
	}

	/** Test seam: inject a synchronous executor for deterministic unit tests. */
	ConnectionManager(TokenStore tokenStore, ScapePathTransport transport,
		SnapshotPayloadSerializer serializer, Executor executor)
	{
		this.tokenStore = tokenStore;
		this.transport = transport;
		this.serializer = serializer;
		this.executor = executor;
	}

	/* --------------------------------- wiring --------------------------------- */

	public void setStateListener(@Nullable Consumer<ConnectionState> listener)
	{
		this.stateListener = listener;
	}

	/** Supplies the most recent locally-built snapshot to serialize and upload. */
	public void setSnapshotSupplier(@Nullable Supplier<AccountSnapshot> supplier)
	{
		this.snapshotSupplier = supplier == null ? () -> null : supplier;
	}

	/* --------------------------------- getters -------------------------------- */

	public ConnectionState getState()
	{
		return state;
	}

	/** True whenever a device token is stored (linked), regardless of transient sync state. */
	public boolean isConnected()
	{
		return tokenStore.has();
	}

	@Nullable
	public String getLastError()
	{
		return lastError;
	}

	@Nullable
	public Instant getLastSyncAt()
	{
		return lastSyncAt;
	}

	/* -------------------------------- lifecycle ------------------------------- */

	/**
	 * Restore connection state on startup from local storage — WITHOUT any network
	 * call. If a token is present we are (optimistically) connected; the first sync
	 * will confirm or, on 401, tear the link down and ask the user to reconnect.
	 */
	public synchronized void restore()
	{
		transition(tokenStore.has() ? ConnectionState.CONNECTED : ConnectionState.DISCONNECTED);
	}

	/**
	 * Redeem a link code entered by the user. Runs the HTTPS exchange off-thread; on
	 * success stores the token and fires an initial sync. The code is used once and
	 * never stored.
	 */
	public synchronized void link(String rawCode)
	{
		final String code = rawCode == null ? "" : rawCode.trim();
		if (code.isEmpty())
		{
			fail("Enter your ScapePath connection code.");
			return;
		}
		if (state == ConnectionState.CONNECTING)
		{
			return;
		}
		lastError = null;
		transition(ConnectionState.CONNECTING);
		executor.execute(() -> doLink(code));
	}

	private void doLink(String code)
	{
		Long accountHash = null;
		String rsn = null;
		String accountType = null;
		final AccountSnapshot snap = snapshotSupplier.get();
		if (snap != null && snap.getSection(SnapshotSectionType.IDENTITY) != null
			&& snap.getSection(SnapshotSectionType.IDENTITY).getData() instanceof IdentityData)
		{
			final IdentityData id = (IdentityData) snap.getSection(SnapshotSectionType.IDENTITY).getData();
			accountHash = id.getAccountHash();
			rsn = id.getRsn();
			accountType = id.getAccountType();
		}

		final ScapePathTransport.LinkOutcome outcome = transport.link(code, accountHash, rsn, accountType);
		switch (outcome.getKind())
		{
			case SUCCESS:
				tokenStore.set(outcome.getDeviceToken()); // never logged
				lastError = null;
				transition(ConnectionState.CONNECTED);
				log.debug("ScapePath linked; running initial sync");
				doSync(); // initial sync
				break;
			case INVALID_CODE:
				fail("That code is invalid or has expired. Generate a new one on the ScapePath website.");
				break;
			case CONFLICT:
				fail("This OSRS account is already linked to another ScapePath account.");
				break;
			case NETWORK_ERROR:
				fail("Could not reach ScapePath. Check your connection and try again.");
				break;
			case SERVER_ERROR:
			default:
				fail("ScapePath had a problem. Please try again shortly.");
				break;
		}
	}

	/** Manual "Sync now". No-op (no request) when not linked. */
	public void syncNow()
	{
		if (!tokenStore.has())
		{
			return;
		}
		executor.execute(this::doSync);
	}

	/**
	 * Automatic sync hook, called by the plugin after a snapshot rebuild. Throttled to
	 * {@link #AUTO_SYNC_MIN_GAP} and suppressed during a server-requested cooldown.
	 */
	public void maybeAutoSync(boolean enabled)
	{
		if (!enabled || !tokenStore.has())
		{
			return;
		}
		final Instant now = Instant.now();
		if (now.isBefore(retryCooldownUntil))
		{
			return;
		}
		if (Duration.between(lastAutoSyncAttempt, now).compareTo(AUTO_SYNC_MIN_GAP) < 0)
		{
			return;
		}
		lastAutoSyncAttempt = now;
		executor.execute(this::doSync);
	}

	private void doSync()
	{
		final String token = tokenStore.get();
		if (token == null || token.isEmpty())
		{
			return; // never send an unauthenticated request
		}
		if (Instant.now().isBefore(retryCooldownUntil))
		{
			return;
		}
		final AccountSnapshot snapshot = snapshotSupplier.get();
		if (snapshot == null)
		{
			return; // nothing to send yet
		}
		// Never let two uploads overlap (a manual "Sync now" fired during an in-flight
		// auto-sync, or a double click). Whoever loses the CAS simply skips this round.
		if (!syncInFlight.compareAndSet(false, true))
		{
			return;
		}

		try
		{
			final String json = serializer.toJson(snapshot);
			transition(ConnectionState.SYNCING);
			final ScapePathTransport.SyncOutcome outcome = transport.sync(token, json);
			switch (outcome.getKind())
			{
				case SUCCESS:
					lastSyncAt = Instant.now();
					lastError = null;
					transition(ConnectionState.CONNECTED);
					break;
				case RATE_LIMITED:
					retryCooldownUntil = Instant.now().plusSeconds(
						outcome.getRetryAfterSeconds() > 0 ? outcome.getRetryAfterSeconds() : 30);
					transition(ConnectionState.CONNECTED); // not an error; just back off
					break;
				case UNAUTHORIZED:
					// Server revoked the token (or it is otherwise invalid). Tear down locally
					// and ask the user to reconnect.
					tokenStore.clear();
					lastSyncAt = null;
					lastError = "ScapePath disconnected this client. Reconnect from your profile.";
					transition(ConnectionState.DISCONNECTED);
					break;
				case NETWORK_ERROR:
					transition(ConnectionState.OFFLINE);
					break;
				case INVALID:
					lastError = "ScapePath could not accept the last snapshot.";
					transition(ConnectionState.CONNECTED);
					break;
				case SERVER_ERROR:
				default:
					lastError = "ScapePath sync failed. Will retry.";
					transition(ConnectionState.CONNECTED);
					break;
			}
		}
		finally
		{
			syncInFlight.set(false);
		}
	}

	/**
	 * Disconnect: best-effort server revoke, then ALWAYS clear the local token. We revoke
	 * locally even if the network call fails — leaving a token that cannot be cleared
	 * server-side is the worse outcome, and the server rejects a stale token anyway.
	 */
	public synchronized void disconnect()
	{
		final String token = tokenStore.get();
		if (token != null && !token.isEmpty())
		{
			executor.execute(() -> transport.disconnect(token));
		}
		tokenStore.clear();
		lastSyncAt = null;
		lastError = null;
		retryCooldownUntil = Instant.EPOCH;
		transition(ConnectionState.DISCONNECTED);
	}

	/* --------------------------------- helpers -------------------------------- */

	private void fail(String message)
	{
		this.lastError = message;
		transition(ConnectionState.ERROR);
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
