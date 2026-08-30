/*
 * Copyright (c) 2026, ScapePath
 * All rights reserved. BSD 2-Clause. See LICENSE file.
 */
package com.scapepath.plugin.snapshot.data;

import lombok.Value;

/**
 * A single skill's state within {@link SkillsData}.
 *
 * <p>Normalized (skill name as a String) so it is decoupled from RuneLite API types.</p>
 */
@Value
public class SkillData
{
	/** RuneLite skill name, e.g. {@code "Attack"}. */
	String name;

	/** Real (unboosted) level. */
	int level;

	/** Total experience. */
	int xp;
}
