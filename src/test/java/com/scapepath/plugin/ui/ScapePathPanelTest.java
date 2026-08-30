/*
 * Copyright (c) 2026, ScapePath
 * All rights reserved. BSD 2-Clause. See LICENSE file.
 */
package com.scapepath.plugin.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;
import com.scapepath.plugin.collector.CollectorRegistry;
import com.scapepath.plugin.collector.IdentityCollector;
import com.scapepath.plugin.collector.SkillsCollector;
import com.scapepath.plugin.game.FakeGameStateAccessor;
import com.scapepath.plugin.snapshot.AccountSnapshot;
import com.scapepath.plugin.transport.SnapshotPayloadSerializer;
import java.time.Instant;
import javax.swing.SwingUtilities;
import net.runelite.client.ui.PluginPanel;
import org.junit.Test;

/**
 * UI regression tests for the panel's layout contract. These assert structural behavior
 * (native wrapping and fixed width) rather than pixel-perfect rendering, so they are not
 * brittle.
 */
public class ScapePathPanelTest
{
	private static ScapePathPanel panel()
	{
		return new ScapePathPanel(new SnapshotPayloadSerializer());
	}

	@Test
	public void usesNativeWrappingWithScrollPane()
	{
		ScapePathPanel panel = panel();
		// wrap=true means RuneLite wraps the content in its scroll pane, so the wrapped
		// panel is a different component than the panel itself.
		assertNotSame("panel must use RuneLite's native scroll wrapping (wrap=true)",
			panel, panel.getWrappedPanel());
	}

	@Test
	public void reportsFixedSidebarWidthNeverOversized()
	{
		ScapePathPanel panel = panel();
		// The panel must report the fixed RuneLite sidebar width, never a width dictated
		// by its widest child (which previously distorted the client).
		assertEquals(PluginPanel.PANEL_WIDTH, panel.getPreferredSize().width);
		assertEquals(PluginPanel.PANEL_WIDTH, panel.getMinimumSize().width);
	}

	@Test
	public void rendersNullAndPopulatedSnapshotsWithoutError() throws Exception
	{
		ScapePathPanel panel = panel();

		panel.update((AccountSnapshot) null);
		drainEdt();

		panel.update(AccountSnapshot.empty("0.1.0", Instant.now(), null));
		drainEdt();

		CollectorRegistry registry = new CollectorRegistry();
		registry.register(new IdentityCollector());
		registry.register(new SkillsCollector());
		FakeGameStateAccessor game = new FakeGameStateAccessor()
			.loggedIn("Zezima", 302, 0, 7L).allSkills(70, 737627);
		panel.update(registry.buildSnapshot(game));
		drainEdt();

		// If we got here without an exception on the EDT, rendering is stable. The wrapped
		// content should contain components after a populated render.
		assertTrue(panel.getComponentCount() > 0);
	}

	private static void drainEdt() throws Exception
	{
		SwingUtilities.invokeAndWait(() -> { });
	}
}
