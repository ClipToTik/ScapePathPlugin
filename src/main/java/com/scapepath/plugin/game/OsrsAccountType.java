/*
 * Copyright (c) 2026, ScapePath
 * All rights reserved. BSD 2-Clause. See LICENSE file.
 */
package com.scapepath.plugin.game;

/**
 * Normalized account type, derived from the {@code Varbits.ACCOUNT_TYPE} varbit.
 *
 * <p>This replaces the deprecated {@link net.runelite.api.vars.AccountType} enum. The
 * varbit value equals the ordinal of the corresponding type. Unknown/out-of-range
 * values (including logged-out {@code -1}) map to {@link #UNKNOWN} rather than being
 * guessed.</p>
 */
public enum OsrsAccountType
{
	NORMAL,
	IRONMAN,
	ULTIMATE_IRONMAN,
	HARDCORE_IRONMAN,
	GROUP_IRONMAN,
	HARDCORE_GROUP_IRONMAN,
	UNKNOWN;

	private static final OsrsAccountType[] BY_VARBIT = {
		NORMAL,
		IRONMAN,
		ULTIMATE_IRONMAN,
		HARDCORE_IRONMAN,
		GROUP_IRONMAN,
		HARDCORE_GROUP_IRONMAN
	};

	public static OsrsAccountType fromVarbit(int value)
	{
		if (value < 0 || value >= BY_VARBIT.length)
		{
			return UNKNOWN;
		}
		return BY_VARBIT[value];
	}
}
