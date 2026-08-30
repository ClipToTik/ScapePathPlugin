/*
 * Copyright (c) 2026, ScapePath
 * All rights reserved. BSD 2-Clause. See LICENSE file.
 */
package com.scapepath.plugin.transport;

import com.google.gson.JsonObject;
import com.scapepath.plugin.ScapePath;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * The single class that performs network I/O. Uses RuneLite's bundled OkHttp client
 * (injected — no new dependency) with a bounded per-call timeout so a hung request can
 * never wedge the caller. HTTPS only. It logs nothing sensitive: never the token, the
 * link code, or any payload byte — only coarse status categories at debug level.
 */
@Slf4j
@Singleton
public class OkHttpScapePathTransport implements ScapePathTransport
{
	private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
	private static final Duration CALL_TIMEOUT = Duration.ofSeconds(20);

	private final OkHttpClient client;

	@Inject
	public OkHttpScapePathTransport(OkHttpClient runeliteClient)
	{
		// Derive a client that shares RuneLite's connection pool but enforces our own
		// bounded timeouts, so a slow ScapePath response never blocks anything.
		this.client = runeliteClient.newBuilder()
			.callTimeout(CALL_TIMEOUT)
			.connectTimeout(10, TimeUnit.SECONDS)
			.readTimeout(15, TimeUnit.SECONDS)
			.build();
	}

	private static HttpUrl url(String path)
	{
		final HttpUrl base = HttpUrl.get(ScapePath.API_BASE_URL);
		return base.newBuilder().addPathSegments(path).build();
	}

	@Override
	public LinkOutcome link(String code, @Nullable Long accountHash, @Nullable String rsn,
		@Nullable String accountType)
	{
		final JsonObject body = new JsonObject();
		body.addProperty("code", code);
		if (accountHash != null && accountHash != -1L)
		{
			// accountHash is a 64-bit id; send as a string to avoid any JSON numeric
			// precision concerns end-to-end. The server accepts either.
			body.addProperty("accountHash", String.valueOf(accountHash));
		}
		if (rsn != null)
		{
			body.addProperty("rsn", rsn);
		}
		if (accountType != null)
		{
			body.addProperty("accountType", accountType);
		}
		body.addProperty("pluginVersion", ScapePath.VERSION);
		body.addProperty("schemaVersion", ScapePath.SCHEMA_VERSION);

		final Request request = new Request.Builder()
			.url(url("api/runelite/link"))
			.post(RequestBody.create(JSON, body.toString()))
			.build();

		try (Response response = client.newCall(request).execute())
		{
			final int codeStatus = response.code();
			if (codeStatus == 200)
			{
				final JsonObject json = parse(response);
				final String token = json != null && json.has("deviceToken")
					? json.get("deviceToken").getAsString() : null;
				if (token == null || token.isEmpty())
				{
					log.debug("ScapePath link: 200 without token");
					return LinkOutcome.of(LinkOutcome.Kind.SERVER_ERROR);
				}
				final String outRsn = json.has("rsn") && !json.get("rsn").isJsonNull()
					? json.get("rsn").getAsString() : null;
				return LinkOutcome.success(token, outRsn);
			}
			if (codeStatus == 401)
			{
				return LinkOutcome.of(LinkOutcome.Kind.INVALID_CODE);
			}
			if (codeStatus == 409)
			{
				return LinkOutcome.of(LinkOutcome.Kind.CONFLICT);
			}
			log.debug("ScapePath link: unexpected status {}", codeStatus);
			return LinkOutcome.of(LinkOutcome.Kind.SERVER_ERROR);
		}
		catch (IOException e)
		{
			log.debug("ScapePath link: network error");
			return LinkOutcome.of(LinkOutcome.Kind.NETWORK_ERROR);
		}
	}

	@Override
	public SyncOutcome sync(String deviceToken, String payloadJson)
	{
		final Request request = new Request.Builder()
			.url(url("api/runelite/sync"))
			.header("Authorization", "Bearer " + deviceToken)
			.post(RequestBody.create(JSON, payloadJson))
			.build();

		try (Response response = client.newCall(request).execute())
		{
			final int status = response.code();
			if (status == 200)
			{
				return SyncOutcome.of(SyncOutcome.Kind.SUCCESS);
			}
			if (status == 401 || status == 403)
			{
				return SyncOutcome.of(SyncOutcome.Kind.UNAUTHORIZED);
			}
			if (status == 429)
			{
				return SyncOutcome.rateLimited(parseRetryAfter(response.header("Retry-After")));
			}
			if (status == 409 || status == 422 || status == 413 || status == 400)
			{
				return SyncOutcome.of(SyncOutcome.Kind.INVALID);
			}
			log.debug("ScapePath sync: unexpected status {}", status);
			return SyncOutcome.of(SyncOutcome.Kind.SERVER_ERROR);
		}
		catch (IOException e)
		{
			log.debug("ScapePath sync: network error");
			return SyncOutcome.of(SyncOutcome.Kind.NETWORK_ERROR);
		}
	}

	@Override
	public void disconnect(String deviceToken)
	{
		final Request request = new Request.Builder()
			.url(url("api/runelite/disconnect"))
			.header("Authorization", "Bearer " + deviceToken)
			.post(RequestBody.create(JSON, "{}"))
			.build();

		try (Response response = client.newCall(request).execute())
		{
			// Best-effort: we don't care about the body. Local revoke happens regardless.
			log.debug("ScapePath disconnect: status {}", response.code());
		}
		catch (IOException e)
		{
			log.debug("ScapePath disconnect: network error (local revoke still applies)");
		}
	}

	@Nullable
	private static JsonObject parse(Response response) throws IOException
	{
		if (response.body() == null)
		{
			return null;
		}
		try
		{
			return new com.google.gson.JsonParser().parse(response.body().string()).getAsJsonObject();
		}
		catch (RuntimeException e)
		{
			return null;
		}
	}

	private static long parseRetryAfter(@Nullable String header)
	{
		if (header == null)
		{
			return 0;
		}
		try
		{
			return Long.parseLong(header.trim());
		}
		catch (NumberFormatException e)
		{
			return 0;
		}
	}
}
