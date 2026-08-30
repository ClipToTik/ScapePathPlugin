/*
 * Copyright (c) 2026, ScapePath
 * All rights reserved. BSD 2-Clause. See LICENSE file.
 */
package com.scapepath.plugin.snapshot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import com.scapepath.plugin.collector.CollectorRegistry;
import com.scapepath.plugin.collector.IdentityCollector;
import com.scapepath.plugin.collector.SkillsCollector;
import com.scapepath.plugin.game.FakeGameStateAccessor;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;

public class SnapshotServiceTest
{
	private static SnapshotService service(FakeGameStateAccessor game)
	{
		CollectorRegistry registry = new CollectorRegistry();
		registry.register(new IdentityCollector());
		registry.register(new SkillsCollector());
		return new SnapshotService(registry, game);
	}

	@Test
	public void latestIsNullUntilFirstRebuild()
	{
		SnapshotService service = service(new FakeGameStateAccessor().loggedOut());
		assertNull(service.getLatest());
	}

	@Test
	public void rebuildCachesAndNotifiesListener()
	{
		FakeGameStateAccessor game = new FakeGameStateAccessor()
			.loggedIn("Zezima", 302, 0, 7L).allSkills(1, 0);
		SnapshotService service = service(game);

		AtomicReference<AccountSnapshot> notified = new AtomicReference<>();
		service.setListener(notified::set);

		AccountSnapshot built = service.rebuild();

		assertSame(built, service.getLatest());
		assertSame(built, notified.get());
		assertEquals("Zezima", built.getRsn());
		assertEquals(2, built.getSections().size());
	}
}
