/*
 * Copyright (c) 2026, ScapePath
 * All rights reserved. BSD 2-Clause. See LICENSE file.
 */
package com.scapepath.plugin;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

/**
 * ScapePath configuration surface.
 *
 * <p>This establishes the future UX using standard RuneLite config conventions.
 * <b>None of these controls perform authentication or transmit data in Session 1.</b>
 * "Connect" only flips local {@link com.scapepath.plugin.connection.ConnectionState};
 * "Enable account synchronization" only stores a preference.</p>
 */
@ConfigGroup(ScapePath.CONFIG_GROUP)
public interface ScapePathConfig extends Config
{
	String KEY_CONNECT = "connect";
	String KEY_SYNC_ENABLED = "syncEnabled";

	@ConfigSection(
		name = "Connection",
		description = "Connect this RuneLite client to your ScapePath account",
		position = 0
	)
	String connectionSection = "connection";

	@ConfigSection(
		name = "Account Sync",
		description = "Control whether account progression is synchronized with ScapePath",
		position = 1
	)
	String accountSyncSection = "accountSync";

	@ConfigItem(
		keyName = "connectionStatus",
		name = "Status",
		description = "Current ScapePath connection status (read-only)",
		section = connectionSection,
		position = 0
	)
	default String connectionStatus()
	{
		// Placeholder. The live status is surfaced by the plugin via ConfigManager.
		return "Not connected";
	}

	@ConfigItem(
		keyName = KEY_CONNECT,
		name = "Connect ScapePath",
		description = "Intend to connect this client to your ScapePath account. "
			+ "This build is local-only: turning it on does not authenticate and sends "
			+ "nothing over the network. No account data leaves your machine.",
		section = connectionSection,
		position = 1
	)
	default boolean connect()
	{
		return false;
	}

	@ConfigItem(
		keyName = KEY_SYNC_ENABLED,
		name = "Enable account synchronization",
		description = "Preference for whether account progression may be synchronized to "
			+ "ScapePath in a future version. This build transmits nothing; account state "
			+ "is only read locally and shown in the plugin panel.",
		section = accountSyncSection,
		position = 0
	)
	default boolean syncEnabled()
	{
		return false;
	}
}
