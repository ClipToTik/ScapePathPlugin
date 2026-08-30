/*
 * Copyright (c) 2026, ScapePath
 * All rights reserved. BSD 2-Clause. See LICENSE file.
 */
package com.scapepath.plugin.snapshot.data;

import lombok.Value;

/**
 * A single quest's state.
 *
 * <p>{@code id} is the stable RuneLite quest id and is the source of truth; {@code name}
 * is included for readability only. {@code state} is the normalized
 * {@code QuestState} name: {@code NOT_STARTED}, {@code IN_PROGRESS}, or {@code FINISHED}.</p>
 */
@Value
public class QuestSnapshot
{
	/** Stable RuneLite quest id ({@code Quest.getId()}). */
	int id;

	/** Quest display name (secondary; ScapePath keys on {@code id}). */
	String name;

	/** Normalized state: NOT_STARTED / IN_PROGRESS / FINISHED. */
	String state;
}
