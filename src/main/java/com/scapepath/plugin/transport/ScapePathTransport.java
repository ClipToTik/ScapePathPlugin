/*
 * Copyright (c) 2026, ScapePath
 * All rights reserved. BSD 2-Clause. See LICENSE file.
 */
package com.scapepath.plugin.transport;

import javax.annotation.Nullable;

/**
 * The HTTPS transport seam between the plugin and the ScapePath API.
 *
 * <p>Kept as an interface so the {@link com.scapepath.plugin.connection.ConnectionManager}
 * can be unit-tested with a fake, and so the ONE class that touches the network
 * ({@link OkHttpScapePathTransport}) stays small and isolated. Implementations must:
 * use HTTPS only; never log the device token, link code, or payload; apply sane
 * timeouts; and translate HTTP status codes into the typed outcomes below rather than
 * throwing. All methods are blocking and MUST be called off the client/UI thread.</p>
 */
public interface ScapePathTransport
{
	/** Redeem a one-time link code for a device token. */
	LinkOutcome link(String code, @Nullable Long accountHash, @Nullable String rsn,
		@Nullable String accountType);

	/** Upload a serialized snapshot payload, authenticated by the device token. */
	SyncOutcome sync(String deviceToken, String payloadJson);

	/** Best-effort server-side revoke. Never throws; failures are swallowed. */
	void disconnect(String deviceToken);

	/* --------------------------------- results -------------------------------- */

	/** Outcome of a link attempt. On success {@link #getDeviceToken()} is non-null. */
	final class LinkOutcome
	{
		public enum Kind { SUCCESS, INVALID_CODE, CONFLICT, NETWORK_ERROR, SERVER_ERROR }

		private final Kind kind;
		@Nullable private final String deviceToken;
		@Nullable private final String rsn;

		private LinkOutcome(Kind kind, @Nullable String deviceToken, @Nullable String rsn)
		{
			this.kind = kind;
			this.deviceToken = deviceToken;
			this.rsn = rsn;
		}

		public static LinkOutcome success(String deviceToken, @Nullable String rsn)
		{
			return new LinkOutcome(Kind.SUCCESS, deviceToken, rsn);
		}

		public static LinkOutcome of(Kind kind)
		{
			return new LinkOutcome(kind, null, null);
		}

		public Kind getKind()
		{
			return kind;
		}

		@Nullable
		public String getDeviceToken()
		{
			return deviceToken;
		}

		@Nullable
		public String getRsn()
		{
			return rsn;
		}
	}

	/** Outcome of a sync attempt. */
	final class SyncOutcome
	{
		public enum Kind { SUCCESS, RATE_LIMITED, UNAUTHORIZED, INVALID, NETWORK_ERROR, SERVER_ERROR }

		private final Kind kind;
		private final long retryAfterSeconds;

		private SyncOutcome(Kind kind, long retryAfterSeconds)
		{
			this.kind = kind;
			this.retryAfterSeconds = retryAfterSeconds;
		}

		public static SyncOutcome of(Kind kind)
		{
			return new SyncOutcome(kind, 0);
		}

		public static SyncOutcome rateLimited(long retryAfterSeconds)
		{
			return new SyncOutcome(Kind.RATE_LIMITED, Math.max(0, retryAfterSeconds));
		}

		public Kind getKind()
		{
			return kind;
		}

		/** Seconds the server asked us to wait (429 Retry-After); 0 when not provided. */
		public long getRetryAfterSeconds()
		{
			return retryAfterSeconds;
		}
	}
}
