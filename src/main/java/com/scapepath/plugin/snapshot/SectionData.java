/*
 * Copyright (c) 2026, ScapePath
 * All rights reserved. BSD 2-Clause. See LICENSE file.
 */
package com.scapepath.plugin.snapshot;

/**
 * Marker interface for the typed payload of a snapshot section.
 *
 * <p>Concrete, immutable payload types (e.g. {@code SkillsData}, {@code QuestData},
 * {@code BankData}) will implement this in later sessions. Session 1 defines only the
 * contract &mdash; there are no implementations and no data is read.</p>
 */
public interface SectionData
{
}
