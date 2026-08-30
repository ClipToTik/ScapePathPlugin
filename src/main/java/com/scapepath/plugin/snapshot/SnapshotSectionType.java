/*
 * Copyright (c) 2026, ScapePath
 * All rights reserved. BSD 2-Clause. See LICENSE file.
 */
package com.scapepath.plugin.snapshot;

/**
 * The categories of account state a ScapePath snapshot may eventually contain.
 *
 * <p>This enum is the contract that keeps collectors modular: each future
 * {@link com.scapepath.plugin.collector.AccountDataCollector} declares exactly one
 * type it is responsible for. No data is collected in Session 1.</p>
 */
public enum SnapshotSectionType
{
	IDENTITY("Identity"),
	SKILLS("Skills"),
	QUESTS("Quests"),
	ACHIEVEMENT_DIARIES("Achievement Diaries"),
	COMBAT_ACHIEVEMENTS("Combat Achievements"),
	INVENTORY("Inventory"),
	EQUIPMENT("Equipment"),
	BANK("Bank"),
	WEALTH("Wealth"),
	COLLECTION_LOG("Collection Log"),
	BOSSES("Bosses"),
	MINIGAMES("Minigames"),
	SLAYER("Slayer"),
	UNLOCKS("Unlocks");

	private final String displayName;

	SnapshotSectionType(String displayName)
	{
		this.displayName = displayName;
	}

	public String getDisplayName()
	{
		return displayName;
	}
}
