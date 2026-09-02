/*
 * Copyright (c) 2026, ScapePath
 * All rights reserved. BSD 2-Clause. See LICENSE file.
 */
package com.scapepath.plugin.transport;

import com.scapepath.plugin.ScapePathConfig;
import java.util.function.BooleanSupplier;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * The authoritative, defence-in-depth networking gate.
 *
 * <p>This is the ONE choke point every outbound request must pass through: the
 * {@link com.scapepath.plugin.connection.ConnectionManager} and everything else are wired
 * to {@link ScapePathTransport}, and this class is the bound implementation of that seam.
 * It re-reads the opt-in config ({@code scapePathDataSyncEnabled}) <em>at the instant each
 * method runs</em> and, when the setting is off, returns a benign no-network outcome without
 * ever touching the real HTTP transport. Because the check happens here — at the network
 * boundary, at execution time — it holds no matter which caller invokes it, holds for any
 * future method added to the transport, and closes the async race where a request scheduled
 * while enabled would otherwise fire after the user disables the setting.</p>
 *
 * <p>Consequence: with {@code scapePathDataSyncEnabled == false} the plugin cannot perform
 * any outbound network request through any code path.</p>
 */
@Slf4j
@Singleton
public class GatedScapePathTransport implements ScapePathTransport
{
	private final ScapePathTransport delegate;
	private final BooleanSupplier networkAllowed;

	@Inject
	public GatedScapePathTransport(OkHttpScapePathTransport delegate, ScapePathConfig config)
	{
		// Bind to the concrete networking transport; read the live opt-in each call.
		this(delegate, config::syncEnabled);
	}

	/** Test seam: supply the gate directly. */
	GatedScapePathTransport(ScapePathTransport delegate, BooleanSupplier networkAllowed)
	{
		this.delegate = delegate;
		this.networkAllowed = networkAllowed;
	}

	private boolean blocked()
	{
		if (networkAllowed.getAsBoolean())
		{
			return false;
		}
		log.debug("ScapePath networking is disabled by config; suppressing request");
		return true;
	}

	@Override
	public LinkOutcome link(String code, @Nullable Long accountHash, @Nullable String rsn,
		@Nullable String accountType)
	{
		if (blocked())
		{
			return LinkOutcome.of(LinkOutcome.Kind.NETWORK_ERROR);
		}
		return delegate.link(code, accountHash, rsn, accountType);
	}

	@Override
	public SyncOutcome sync(String deviceToken, String payloadJson)
	{
		if (blocked())
		{
			return SyncOutcome.of(SyncOutcome.Kind.NETWORK_ERROR);
		}
		return delegate.sync(deviceToken, payloadJson);
	}

	@Override
	public void disconnect(String deviceToken)
	{
		if (blocked())
		{
			return;
		}
		delegate.disconnect(deviceToken);
	}
}
