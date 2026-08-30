/*
 * Copyright (c) 2026, ScapePath
 * All rights reserved. BSD 2-Clause. See LICENSE file.
 */
package com.scapepath.plugin.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;
import com.scapepath.plugin.collector.CollectorRegistry;
import com.scapepath.plugin.collector.IdentityCollector;
import com.scapepath.plugin.collector.SkillsCollector;
import com.scapepath.plugin.connection.ConnectionState;
import com.scapepath.plugin.game.FakeGameStateAccessor;
import com.scapepath.plugin.snapshot.AccountSnapshot;
import com.scapepath.plugin.transport.SnapshotPayloadSerializer;
import java.awt.Component;
import java.awt.Container;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import javax.swing.AbstractButton;
import javax.swing.JLabel;
import javax.swing.JTextField;
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

	@Test
	public void disconnectedStateShowsVerbatimThirdPartyDisclosure() throws Exception
	{
		final ScapePathPanel panel = panel();
		drainEdt();
		final String text = String.join("\n", allText(panel));

		// The exact, un-paraphrased disclosure wording must be present.
		assertTrue("must state ScapePath is a third-party companion synced over HTTPS",
			text.contains("ScapePath is a third-party OSRS progression companion")
				&& text.contains("over HTTPS"));
		assertTrue("must list the data categories that may be shared",
			text.contains("Data may include")
				&& text.contains("bank")
				&& text.contains("wealth information"));
		assertTrue("must state no Jagex credentials/passwords/cookies are transmitted",
			text.contains("No Jagex credentials, passwords, or cookies are transmitted."));
		assertTrue("must state the user authorizes and can disconnect",
			text.contains("You can disconnect at any time."));
	}

	@Test
	public void disclosureWrapsToPanelWidthAndDoesNotOverflow() throws Exception
	{
		final ScapePathPanel panel = panel();
		drainEdt();
		// Disclosure paragraphs render through note(), which HTML-wraps to a fixed width just
		// under PANEL_WIDTH — the mechanism that prevents horizontal overflow.
		boolean wrapped = false;
		for (JLabel l : labels(panel))
		{
			final String t = l.getText();
			if (t != null && t.contains("third-party OSRS progression companion")
				&& t.startsWith("<html") && t.contains("width:"))
			{
				wrapped = true;
				break;
			}
		}
		assertTrue("disclosure must be width-constrained HTML so it wraps without overflow", wrapped);
		// The panel itself must never report a width wider than the fixed sidebar.
		assertEquals(PluginPanel.PANEL_WIDTH, panel.getPreferredSize().width);
	}

	@Test
	public void disconnectedStateExposesCodeInputAndConnect() throws Exception
	{
		final ScapePathPanel panel = panel();
		drainEdt();
		assertTrue("disconnected view must offer a code input field",
			!fieldsOfType(panel, JTextField.class).isEmpty());
		assertTrue("disconnected view must offer a Connect button", hasButton(panel, "Connect"));
		final String text = String.join("\n", allText(panel));
		assertTrue("must instruct the user to generate a one-time code",
			text.contains("Generate RuneLite Code"));
		assertTrue("must make clear the field takes a one-time connection code",
			text.contains("one-time"));
	}

	@Test
	public void connectedStateExposesSyncNowAndDisconnect() throws Exception
	{
		final ScapePathPanel panel = panel();
		panel.updateConnection(ConnectionState.CONNECTED, true, Instant.now(), null);
		drainEdt();
		assertTrue("connected view must offer Sync now", hasButton(panel, "Sync now"));
		assertTrue("connected view must offer Disconnect", hasButton(panel, "Disconnect"));
		final String text = String.join("\n", allText(panel));
		assertTrue("connected view must confirm the connection", text.contains("Connected"));
	}

	@Test
	public void neverRendersDeviceTokenOrAccountHash() throws Exception
	{
		final ScapePathPanel panel = panel();
		// Both connection states must be free of any secret/internal identifier surface.
		panel.updateConnection(ConnectionState.CONNECTED, true, Instant.now(), null);
		drainEdt();
		assertNoSecretsRendered(panel);
		panel.updateConnection(ConnectionState.DISCONNECTED, false, null, null);
		drainEdt();
		assertNoSecretsRendered(panel);
	}

	private static void assertNoSecretsRendered(ScapePathPanel panel)
	{
		for (String t : allText(panel))
		{
			final String lower = t.toLowerCase();
			assertFalse("device token must never be rendered", lower.contains("devicetoken"));
			assertFalse("account hash must never be rendered", lower.contains("accounthash"));
			assertFalse("bearer token must never be rendered", lower.contains("bearer "));
		}
	}

	// --- component-tree helpers -------------------------------------------------------

	private static List<String> allText(Container root)
	{
		final List<String> out = new ArrayList<>();
		for (JLabel l : labels(root))
		{
			if (l.getText() != null)
			{
				out.add(l.getText());
			}
		}
		collectButtons(root, out);
		return out;
	}

	private static List<JLabel> labels(Container root)
	{
		final List<JLabel> out = new ArrayList<>();
		walk(root, JLabel.class, out);
		return out;
	}

	private static <T> List<T> fieldsOfType(Container root, Class<T> type)
	{
		final List<T> out = new ArrayList<>();
		walk(root, type, out);
		return out;
	}

	private static void collectButtons(Container root, List<String> out)
	{
		for (AbstractButton b : fieldsOfType(root, AbstractButton.class))
		{
			if (b.getText() != null)
			{
				out.add(b.getText());
			}
		}
	}

	private static boolean hasButton(Container root, String label)
	{
		for (AbstractButton b : fieldsOfType(root, AbstractButton.class))
		{
			if (label.equals(b.getText()))
			{
				return true;
			}
		}
		return false;
	}

	@SuppressWarnings("unchecked")
	private static <T> void walk(Container c, Class<T> type, List<T> out)
	{
		for (Component comp : c.getComponents())
		{
			if (type.isInstance(comp))
			{
				out.add((T) comp);
			}
			if (comp instanceof Container)
			{
				walk((Container) comp, type, out);
			}
		}
	}

	private static void drainEdt() throws Exception
	{
		SwingUtilities.invokeAndWait(() -> { });
	}
}
