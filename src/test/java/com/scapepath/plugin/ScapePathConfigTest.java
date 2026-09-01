/*
 * Copyright (c) 2026, ScapePath
 * All rights reserved. BSD 2-Clause. See LICENSE file.
 */
package com.scapepath.plugin;

import java.lang.reflect.Method;
import net.runelite.client.config.ConfigItem;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import org.junit.Test;

/**
 * Verifies the Plugin Hub compliance requirements for the account-data sync setting:
 * an entirely new config key (not the historical {@code syncEnabled}), a default of
 * {@code false} (explicit opt-in), and the exact reviewer-supplied warning text.
 */
public class ScapePathConfigTest
{
	private static final String REQUIRED_WARNING =
		"This plugin submits your IP address, and may submit various account data, to a 3rd-party server not controlled or verified by Runelite developers.";

	private ConfigItem syncConfigItem() throws NoSuchMethodException
	{
		final Method m = ScapePathConfig.class.getMethod("syncEnabled");
		final ConfigItem item = m.getAnnotation(ConfigItem.class);
		assertNotNull("syncEnabled() must be annotated with @ConfigItem", item);
		return item;
	}

	@Test
	public void newConfigKeyIsEntirelySeparateFromOldKey()
	{
		assertEquals("scapePathDataSyncEnabled", ScapePathConfig.KEY_SYNC_ENABLED);
		assertNotEquals("syncEnabled", ScapePathConfig.KEY_SYNC_ENABLED);
	}

	@Test
	public void configItemUsesTheNewKey() throws NoSuchMethodException
	{
		assertEquals(ScapePathConfig.KEY_SYNC_ENABLED, syncConfigItem().keyName());
		assertNotEquals("syncEnabled", syncConfigItem().keyName());
	}

	@Test
	public void defaultIsFalseRequiringExplicitOptIn()
	{
		final ScapePathConfig config = new ScapePathConfig() {};
		assertFalse("New sync setting must default to false (opt-in)", config.syncEnabled());
	}

	@Test
	public void exactRequiredWarningIsPresent() throws NoSuchMethodException
	{
		assertEquals(REQUIRED_WARNING, syncConfigItem().warning());
	}
}
