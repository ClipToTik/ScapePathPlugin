/*
 * Copyright (c) 2026, ScapePath
 * All rights reserved. BSD 2-Clause. See LICENSE file.
 */
package com.scapepath.plugin;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

/**
 * Developer launcher: starts RuneLite with the ScapePath plugin side-loaded.
 * Run via {@code ./gradlew run}. Not part of the shipped plugin.
 */
public class ScapePathPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(ScapePathPlugin.class);
		RuneLite.main(args);
	}
}
