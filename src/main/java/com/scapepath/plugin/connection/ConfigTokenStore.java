/*
 * Copyright (c) 2026, ScapePath
 * All rights reserved. BSD 2-Clause. See LICENSE file.
 */
package com.scapepath.plugin.connection;

import com.scapepath.plugin.ScapePath;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.config.ConfigManager;

/**
 * {@link TokenStore} backed by RuneLite's {@link ConfigManager}, under a key that is
 * intentionally NOT a {@code @ConfigItem} — so the token never appears in the plugin's
 * settings UI. It persists to the user's local RuneLite profile and survives restarts.
 * Nothing here logs the value.
 */
@Singleton
public class ConfigTokenStore implements TokenStore
{
	private final ConfigManager configManager;

	@Inject
	public ConfigTokenStore(ConfigManager configManager)
	{
		this.configManager = configManager;
	}

	@Override
	@Nullable
	public String get()
	{
		return configManager.getConfiguration(ScapePath.CONFIG_GROUP, ScapePath.KEY_DEVICE_TOKEN);
	}

	@Override
	public void set(String token)
	{
		configManager.setConfiguration(ScapePath.CONFIG_GROUP, ScapePath.KEY_DEVICE_TOKEN, token);
	}

	@Override
	public void clear()
	{
		configManager.unsetConfiguration(ScapePath.CONFIG_GROUP, ScapePath.KEY_DEVICE_TOKEN);
	}
}
