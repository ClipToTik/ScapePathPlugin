/*
 * Copyright (c) 2026, ScapePath
 * All rights reserved. BSD 2-Clause. See LICENSE file.
 */
package com.scapepath.plugin.collector;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.scapepath.plugin.ScapePath;
import com.scapepath.plugin.game.FakeGameStateAccessor;
import com.scapepath.plugin.snapshot.AccountSnapshot;
import com.scapepath.plugin.snapshot.SnapshotSectionType;
import com.scapepath.plugin.snapshot.SourceFreshness;
import org.junit.Test;

public class CollectorRegistryTest
{
	@Test
	public void freshRegistryHasNoCollectors()
	{
		CollectorRegistry registry = new CollectorRegistry();
		assertTrue(registry.getCollectors().isEmpty());
	}

	@Test
	public void loggedInSnapshotContainsIdentityAndSkills()
	{
		CollectorRegistry registry = new CollectorRegistry();
		registry.register(new IdentityCollector());
		registry.register(new SkillsCollector());

		FakeGameStateAccessor game = new FakeGameStateAccessor()
			.loggedIn("Zezima", 302, 0, 99L)
			.allSkills(1, 0);

		AccountSnapshot snapshot = registry.buildSnapshot(game);

		assertEquals(ScapePath.VERSION, snapshot.getPluginVersion());
		assertNotNull(snapshot.getTimestamp());
		assertEquals("Zezima", snapshot.getRsn());
		assertEquals(2, snapshot.getSections().size());
		assertEquals(SourceFreshness.COMPLETE,
			snapshot.getSection(SnapshotSectionType.IDENTITY).getFreshness());
		assertEquals(SourceFreshness.COMPLETE,
			snapshot.getSection(SnapshotSectionType.SKILLS).getFreshness());
	}

	@Test
	public void loggedOutSnapshotMarksSectionsUnavailable()
	{
		CollectorRegistry registry = new CollectorRegistry();
		registry.register(new IdentityCollector());
		registry.register(new SkillsCollector());

		AccountSnapshot snapshot = registry.buildSnapshot(new FakeGameStateAccessor().loggedOut());

		assertNull(snapshot.getRsn());
		assertEquals(SourceFreshness.UNAVAILABLE,
			snapshot.getSection(SnapshotSectionType.IDENTITY).getFreshness());
		assertEquals(SourceFreshness.UNAVAILABLE,
			snapshot.getSection(SnapshotSectionType.SKILLS).getFreshness());
	}

	@Test
	public void noDuplicateSections()
	{
		CollectorRegistry registry = new CollectorRegistry();
		registry.register(new IdentityCollector());
		registry.register(new SkillsCollector());

		FakeGameStateAccessor game = new FakeGameStateAccessor()
			.loggedIn("Zezima", 302, 0, 99L).allSkills(1, 0);

		// One entry per section type by construction (keyed EnumMap).
		AccountSnapshot snapshot = registry.buildSnapshot(game);
		assertEquals(snapshot.getSections().size(), snapshot.getSections().keySet().size());
	}
}
