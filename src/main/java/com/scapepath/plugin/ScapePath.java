/*
 * Copyright (c) 2026, ScapePath
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES ARE DISCLAIMED. SEE LICENSE FILE.
 */
package com.scapepath.plugin;

/**
 * Project-wide constants for the ScapePath plugin.
 */
public final class ScapePath
{
	/** ConfigManager group key. Also used as the {@link net.runelite.client.config.ConfigGroup} name. */
	public static final String CONFIG_GROUP = "scapepath";

	/**
	 * Plugin version reported in future snapshots. Kept in sync with
	 * {@code runelite-plugin.properties} at release time.
	 */
	public static final String VERSION = "0.1.0-SNAPSHOT";

	/**
	 * Version of the transport payload contract emitted by the serializer. This is a
	 * stable API contract between the plugin and the future ScapePath ingestion API and
	 * is independent of {@link #VERSION}. Increment on any breaking change to the JSON
	 * shape or field semantics.
	 */
	public static final int SCHEMA_VERSION = 1;

	private ScapePath()
	{
	}
}
