/*
 * Copyright (c) 2026, ScapePath
 * All rights reserved. BSD 2-Clause. See LICENSE file.
 */
package com.scapepath.plugin.snapshot.data;

import com.scapepath.plugin.snapshot.SectionData;
import java.util.List;
import lombok.Value;

/**
 * Typed payload for the {@code QUESTS} section: every quest's state plus aggregates.
 */
@Value
public class QuestsData implements SectionData
{
	/** One entry per quest known to the RuneLite {@code Quest} enum. */
	List<QuestSnapshot> quests;

	/** Number of quests in the {@code FINISHED} state. */
	int completedCount;

	/** Total number of quests represented. */
	int totalCount;

	/** Quest points, from {@code VarPlayerID.QP}. */
	int questPoints;
}
