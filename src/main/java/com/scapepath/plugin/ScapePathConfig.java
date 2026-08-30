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
 * <p>Connecting and disconnecting are driven from the plugin's side panel (you enter a
 * one-time code generated on the ScapePath website), so there is no "connect" toggle
 * here. This config exposes only a read-only status line and the automatic-sync
 * preference. The device token is stored under a hidden key and is never shown here.</p>
 */
@ConfigGroup(ScapePath.CONFIG_GROUP)
public interface ScapePathConfig extends Config
{
	String KEY_SYNC_ENABLED = "syncEnabled";

	@ConfigSection(
		name = "Connection",
		description = "Connect this RuneLite client to your ScapePath account (use the side panel)",
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
		description = "Current ScapePath connection status (read-only). Connect from the side panel.",
		section = connectionSection,
		position = 0
	)
	default String connectionStatus()
	{
		// Placeholder. The live status is surfaced by the plugin via ConfigManager.
		return "Not connected";
	}

	@ConfigItem(
		keyName = KEY_SYNC_ENABLED,
		name = "Automatic sync",
		description = "When on and connected, ScapePath periodically syncs your verified account "
			+ "state (skills, quests, diaries, inventory, equipment, bank, wealth) over HTTPS. "
			+ "Turn off to sync only when you press \"Sync now\". Nothing but your own account "
			+ "state is ever sent; no passwords, cookies or Jagex credentials.",
		section = accountSyncSection,
		position = 0
	)
	default boolean syncEnabled()
	{
		return true;
	}
}
