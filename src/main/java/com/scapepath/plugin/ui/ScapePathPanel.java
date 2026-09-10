/*
 * Copyright (c) 2026, ScapePath
 * All rights reserved. BSD 2-Clause. See LICENSE file.
 */
package com.scapepath.plugin.ui;

import com.scapepath.plugin.connection.ConnectionState;
import com.scapepath.plugin.snapshot.AccountSnapshot;
import com.scapepath.plugin.snapshot.CollectedSection;
import com.scapepath.plugin.snapshot.SnapshotSectionType;
import com.scapepath.plugin.snapshot.data.IdentityData;
import com.scapepath.plugin.snapshot.data.SkillsData;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

/**
 * ScapePath side panel — a lightweight status &amp; control surface, not a data dashboard.
 *
 * <p>It shows only what a player needs at a glance: whether ScapePath is connected, whether
 * their account is recognized, whether their progression is synced, and a concise path to
 * connect. The rich account state (skills, quests, diaries, combat achievements, bank, …) is
 * synchronized to the ScapePath website, which is the reasoning layer; it is deliberately not
 * mirrored here as a RuneLite dashboard.</p>
 *
 * <p>The panel uses RuneLite's native {@link PluginPanel} wrapping (fixed sidebar width,
 * vertical scrollbar as-needed, no horizontal scroll). All mutation happens on the Swing EDT.
 * The account/progress body re-renders only when a displayed value actually changes, so an
 * XP-gaining play session never churns the UI tick-by-tick.</p>
 */
public class ScapePathPanel extends PluginPanel
{
	/** "Connected" confirmation colour and error colour, defined once. */
	private static final Color CONNECTED_GREEN = new Color(0x4C, 0xAF, 0x50);
	private static final Color ERROR_RED = new Color(0xD0, 0x60, 0x60);

	/**
	 * Core third-party disclosure, shown verbatim in the disconnected connection view.
	 * Kept as constants (not paraphrased) so the exact wording is auditable and testable.
	 */
	private static final String DISCLOSURE_1 =
		"ScapePath is a third-party OSRS progression companion. Connecting is optional and "
			+ "allows this plugin to securely sync your own account data to ScapePath over HTTPS.";
	private static final String DISCLOSURE_2 =
		"Data may include: skills, quests, achievement diaries, combat achievements, inventory, "
			+ "equipment, bank contents, and wealth information.";
	private static final String DISCLOSURE_3 =
		"No Jagex credentials, passwords, or cookies are transmitted.";
	private static final String DISCLOSURE_4 =
		"By connecting, you authorize ScapePath to receive this information from this plugin. "
			+ "You can disconnect at any time.";

	/** Account state that may be transmitted, listed in the "Data shared" disclosure. */
	private static final String[] SHARED_ITEMS = {
		"Skills and XP",
		"Quests and quest points",
		"Achievement diaries",
		"Combat achievements",
		"Inventory",
		"Equipment",
		"Bank contents",
		"Wealth information",
		"RSN / account metadata required for account association",
	};

	/** Categories that are never transmitted, listed in the "Data shared" disclosure. */
	private static final String[] NOT_SHARED_ITEMS = {
		"Jagex credentials",
		"RuneLite credentials",
		"Passwords",
		"Cookies",
		"Session tokens",
		"Other players' information",
		"Gameplay inputs",
		"Keystrokes",
		"Mouse activity",
		"Files from your computer",
		"Telemetry",
		"Advertising / analytics data",
	};

	private AccountSnapshot lastSnapshot;
	/** Signature of the last body render; skips redundant re-renders during play. */
	private String lastBodySignature;

	/** Holds the account/progress body; rebuilt only when its content changes. */
	private final JPanel body = new JPanel();

	/** Connection controls (link/sync/disconnect); rebuilt on each connection update. */
	private final JPanel connectionPanel = new JPanel();

	private java.util.function.Consumer<String> connectHandler = code -> { };
	private Runnable syncHandler = () -> { };
	private Runnable disconnectHandler = () -> { };

	/** Remembers the data-disclosure block's expanded/collapsed state across re-renders. */
	private final Map<String, Boolean> collapsed = new HashMap<>();

	// Latest connection view state (rendered by rebuildConnection on the EDT).
	private ConnectionState connState = ConnectionState.DISCONNECTED;
	private boolean connLinked;
	private Instant connLastSync;
	private String connError;

	public ScapePathPanel()
	{
		// wrap=true: RuneLite provides the scroll pane, fixed width, and viewport-managed
		// height. Do NOT override the layout/border it sets up.
		super(true);

		add(heading("ScapePath"));
		add(tagline("Your OSRS progression companion."));
		add(separator());

		connectionPanel.setLayout(new BoxLayout(connectionPanel, BoxLayout.Y_AXIS));
		connectionPanel.setOpaque(false);
		add(connectionPanel);
		add(separator());

		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
		body.setOpaque(false);
		add(body);

		rebuildConnection();
		renderEmpty();
	}

	/** Wire the connection controls to the ConnectionManager (via the plugin). */
	public void setConnectionHandlers(java.util.function.Consumer<String> onConnect,
		Runnable onSync, Runnable onDisconnect)
	{
		this.connectHandler = onConnect == null ? code -> { } : onConnect;
		this.syncHandler = onSync == null ? () -> { } : onSync;
		this.disconnectHandler = onDisconnect == null ? () -> { } : onDisconnect;
	}

	/** Update the connection area. Safe to call from any thread. */
	public void updateConnection(ConnectionState state, boolean linked, Instant lastSync, String error)
	{
		SwingUtilities.invokeLater(() -> {
			this.connState = state == null ? ConnectionState.DISCONNECTED : state;
			this.connLinked = linked;
			this.connLastSync = lastSync;
			this.connError = error;
			rebuildConnection();
		});
	}

	private void rebuildConnection()
	{
		connectionPanel.removeAll();

		if (connLinked)
		{
			// Connected view: clear confirmation, the account, sync status, and controls.
			final JLabel connected = new JLabel("✓  Connected to ScapePath");
			connected.setFont(connected.getFont().deriveFont(Font.BOLD));
			connected.setForeground(CONNECTED_GREEN);
			connected.setAlignmentX(Component.LEFT_ALIGNMENT);
			connectionPanel.add(connected);
			connectionPanel.add(spacer(2));

			final String rsn = currentRsn();
			connectionPanel.add(kv("Account", rsn == null ? "—" : rsn));
			connectionPanel.add(kv("Sync", syncStatusText()));
			connectionPanel.add(kv("Last sync", relativeTime(connLastSync)));

			connectionPanel.add(spacer(4));
			connectionPanel.add(note(connLastSync == null
				? "ScapePath will receive your progression data on the next sync."
				: "Your progression data is synced with ScapePath."));

			final JButton syncButton = new JButton("Sync now");
			syncButton.setAlignmentX(Component.LEFT_ALIGNMENT);
			syncButton.addActionListener(e -> syncHandler.run());
			connectionPanel.add(spacer(6));
			connectionPanel.add(syncButton);

			final JButton disconnectButton = new JButton("Disconnect");
			disconnectButton.setAlignmentX(Component.LEFT_ALIGNMENT);
			disconnectButton.addActionListener(e -> disconnectHandler.run());
			connectionPanel.add(spacer(2));
			connectionPanel.add(disconnectButton);
		}
		else
		{
			// Disconnected view: a professional third-party disclosure followed by a
			// numbered, unambiguous path for a first-time user.
			final JLabel title = new JLabel("Connect ScapePath");
			title.setFont(title.getFont().deriveFont(Font.BOLD));
			title.setForeground(ColorScheme.BRAND_ORANGE);
			title.setAlignmentX(Component.LEFT_ALIGNMENT);
			connectionPanel.add(title);
			connectionPanel.add(spacer(4));

			// Core disclosure (verbatim). Each paragraph is rendered through note(), which
			// HTML-wraps to the fixed panel width, so nothing overflows horizontally and the
			// user never has to resize the client.
			connectionPanel.add(note(DISCLOSURE_1));
			connectionPanel.add(spacer(4));
			connectionPanel.add(note(DISCLOSURE_2));
			connectionPanel.add(spacer(4));
			connectionPanel.add(note(DISCLOSURE_3));
			connectionPanel.add(spacer(4));
			connectionPanel.add(note(DISCLOSURE_4));

			connectionPanel.add(spacer(8));
			connectionPanel.add(note("1. Open ScapePath → Profile → Generate RuneLite Code."));
			connectionPanel.add(note("2. Enter the one-time code below."));

			connectionPanel.add(spacer(4));
			final JLabel codeLabel = mutedLabel("Code (one-time connection code):");
			codeLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
			connectionPanel.add(codeLabel);

			final JTextField codeField = new JTextField();
			codeField.setMaximumSize(new Dimension(Integer.MAX_VALUE, codeField.getPreferredSize().height + 4));
			codeField.setAlignmentX(Component.LEFT_ALIGNMENT);
			codeField.setToolTipText("Paste your one-time ScapePath connection code — not a password or login");
			// Enter in the field connects, same as the button.
			codeField.addActionListener(e -> connectHandler.accept(codeField.getText()));
			connectionPanel.add(spacer(2));
			connectionPanel.add(codeField);

			final JButton connectButton = new JButton("Connect");
			connectButton.setAlignmentX(Component.LEFT_ALIGNMENT);
			connectButton.addActionListener(e -> connectHandler.accept(codeField.getText()));
			connectionPanel.add(spacer(2));
			connectionPanel.add(connectButton);

			// While a link attempt is in flight, show progress inline.
			if (connState == ConnectionState.CONNECTING)
			{
				connectionPanel.add(spacer(4));
				connectionPanel.add(note("Connecting…"));
			}
		}

		// Transparency block: exactly what is and is not shared. Placed after the controls
		// so Connect / Sync now / Disconnect always stay accessible above it. Collapsed by
		// default to keep the panel compact.
		addDataDisclosure();

		if (connError != null && !connError.isEmpty())
		{
			final JLabel err = new JLabel("<html><body style='width:"
				+ (PluginPanel.PANEL_WIDTH - 30) + "px'>" + escapeHtml(connError) + "</body></html>");
			err.setForeground(ERROR_RED);
			err.setAlignmentX(Component.LEFT_ALIGNMENT);
			connectionPanel.add(spacer(4));
			connectionPanel.add(err);
		}

		connectionPanel.revalidate();
		connectionPanel.repaint();
	}

	/**
	 * Human, non-technical sync status. Never exposes HTTP codes, retry counters, endpoints,
	 * or token details — only what the sync means to the player.
	 */
	private String syncStatusText()
	{
		if (connState == ConnectionState.SYNCING)
		{
			return "Syncing…";
		}
		if (connState == ConnectionState.OFFLINE)
		{
			return "Temporarily unavailable";
		}
		return connLastSync == null ? "Waiting for first sync" : "Synced";
	}

	/**
	 * The "Data shared with ScapePath" transparency block, appended to the connection area.
	 * Lists precisely what may be transmitted and what never is. Claims here mirror the
	 * serializer and collectors — nothing is asserted that the code does not actually do.
	 */
	private void addDataDisclosure()
	{
		final JPanel c = collapsibleInto(connectionPanel, "Data shared with ScapePath", true);
		c.add(note("Only the local player's own account state is synchronized."));
		c.add(spacer(4));
		c.add(mutedBold("Potentially transmitted:"));
		for (String s : SHARED_ITEMS)
		{
			c.add(note("• " + s));
		}
		c.add(spacer(4));
		c.add(mutedBold("Not transmitted:"));
		for (String s : NOT_SHARED_ITEMS)
		{
			c.add(note("• " + s));
		}
		c.add(spacer(4));
		c.add(note("Bank information follows the plugin's freshness rules: it is included "
			+ "only after you open your bank, cached bank data is marked stale, and it is "
			+ "never fabricated when unavailable."));
	}

	/**
	 * Append a collapsible block (clickable header + content panel) to an arbitrary parent,
	 * remembering its expanded/collapsed state in {@link #collapsed}.
	 */
	private JPanel collapsibleInto(JPanel parent, String title, boolean defaultCollapsed)
	{
		final JPanel content = new JPanel();
		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
		content.setOpaque(false);
		content.setAlignmentX(Component.LEFT_ALIGNMENT);

		final boolean startCollapsed = collapsed.getOrDefault(title, defaultCollapsed);
		content.setVisible(!startCollapsed);

		final JLabel header = new JLabel(arrow(!startCollapsed) + title);
		header.setFont(header.getFont().deriveFont(Font.BOLD));
		header.setForeground(ColorScheme.BRAND_ORANGE);
		header.setAlignmentX(Component.LEFT_ALIGNMENT);
		header.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		header.setBorder(BorderFactory.createEmptyBorder(10, 0, 3, 0));
		header.setMaximumSize(new Dimension(Integer.MAX_VALUE, header.getPreferredSize().height + 13));
		header.setToolTipText("Click to expand or collapse");
		header.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				final boolean nowVisible = !content.isVisible();
				content.setVisible(nowVisible);
				collapsed.put(title, !nowVisible);
				header.setText(arrow(nowVisible) + title);
				parent.revalidate();
				parent.repaint();
			}
		});

		parent.add(header);
		parent.add(content);
		return content;
	}

	/** RSN from the latest local snapshot's identity, or null when unknown/logged out. */
	private String currentRsn()
	{
		final IdentityData id = identity();
		return id == null ? null : id.getRsn();
	}

	private IdentityData identity()
	{
		final AccountSnapshot snap = lastSnapshot;
		if (snap == null)
		{
			return null;
		}
		final CollectedSection id = snap.getSection(SnapshotSectionType.IDENTITY);
		return id != null && id.getData() instanceof IdentityData ? (IdentityData) id.getData() : null;
	}

	private static String escapeHtml(String s)
	{
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	/** Update the panel from a snapshot. Safe to call from any thread. */
	public void update(AccountSnapshot snapshot)
	{
		SwingUtilities.invokeLater(() -> render(snapshot));
	}

	private void render(AccountSnapshot snapshot)
	{
		this.lastSnapshot = snapshot;

		if (snapshot == null)
		{
			if (lastBodySignature != null)
			{
				lastBodySignature = null;
				renderEmpty();
			}
			return;
		}

		final String signature = bodySignature(snapshot);
		if (signature.equals(lastBodySignature))
		{
			// Nothing the panel displays has changed — skip the Swing rebuild entirely. The
			// connection area (RSN, sync time) is refreshed by updateConnection separately.
			return;
		}
		lastBodySignature = signature;

		final IdentityData id = identity();
		final boolean loggedIn = id != null;

		body.removeAll();
		if (!loggedIn)
		{
			renderLoggedOutBody();
		}
		else
		{
			renderAccountAndProgress(snapshot, id);
		}

		// Keep the connected-view RSN in step with the latest snapshot.
		rebuildConnection();

		body.revalidate();
		body.repaint();
	}

	/**
	 * A compact fingerprint of everything the body displays. When it is unchanged between
	 * snapshots the panel does not re-render, which prevents per-tick UI churn while training.
	 */
	private String bodySignature(AccountSnapshot snapshot)
	{
		final IdentityData id = identity();
		if (id == null)
		{
			return "out";
		}
		final CollectedSection s = snapshot.getSection(SnapshotSectionType.SKILLS);
		final SkillsData sd = s != null && s.getData() instanceof SkillsData ? (SkillsData) s.getData() : null;
		return "in|" + id.getRsn() + "|" + id.getAccountType() + "|"
			+ (sd == null ? "?" : sd.getTotalLevel() + "|" + sd.getCombatLevel());
	}

	private void renderEmpty()
	{
		body.removeAll();
		renderLoggedOutBody();
		body.revalidate();
		body.repaint();
	}

	private void renderLoggedOutBody()
	{
		body.add(sectionHeader("Account"));
		body.add(note("Log in to OSRS and ScapePath will recognize your account."));
	}

	private void renderAccountAndProgress(AccountSnapshot snapshot, IdentityData id)
	{
		body.add(sectionHeader("Account"));
		body.add(kv("RSN", id.getRsn() == null ? "—" : id.getRsn()));
		if (id.getAccountType() != null)
		{
			body.add(kv("Type", id.getAccountType()));
		}

		final CollectedSection s = snapshot.getSection(SnapshotSectionType.SKILLS);
		if (s != null && s.getData() instanceof SkillsData)
		{
			final SkillsData sd = (SkillsData) s.getData();
			body.add(sectionHeader("Progress"));
			body.add(kv("Total level", String.valueOf(sd.getTotalLevel())));
			body.add(kv("Combat level", String.valueOf(sd.getCombatLevel())));
		}
	}

	/** Disclosure triangle prefix: ▾ when expanded, ▸ when collapsed. */
	private static String arrow(boolean expanded)
	{
		return expanded ? "▾  " : "▸  ";
	}

	private static String relativeTime(Instant when)
	{
		if (when == null)
		{
			return "—";
		}
		final long seconds = Duration.between(when, Instant.now()).getSeconds();
		if (seconds < 60)
		{
			return seconds <= 1 ? "just now" : seconds + " seconds ago";
		}
		final long minutes = seconds / 60;
		if (minutes < 60)
		{
			return minutes + (minutes == 1 ? " minute ago" : " minutes ago");
		}
		final long hours = minutes / 60;
		return hours + (hours == 1 ? " hour ago" : " hours ago");
	}

	// --- Small UI building blocks -----------------------------------------------------

	/** Top-level panel heading in the brand colour. */
	private static JLabel heading(String text)
	{
		final JLabel label = new JLabel(text);
		label.setFont(label.getFont().deriveFont(Font.BOLD, 16f));
		label.setForeground(ColorScheme.BRAND_ORANGE);
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		return label;
	}

	/** A muted one-line subtitle under the heading. */
	private static JLabel tagline(String text)
	{
		final JLabel label = new JLabel(text);
		label.setForeground(Color.LIGHT_GRAY);
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		label.setBorder(BorderFactory.createEmptyBorder(1, 0, 4, 0));
		return label;
	}

	/** A thin horizontal divider. */
	private static Component separator()
	{
		final JPanel line = new JPanel();
		line.setBackground(ColorScheme.MEDIUM_GRAY_COLOR);
		line.setMinimumSize(new Dimension(0, 1));
		line.setPreferredSize(new Dimension(PluginPanel.PANEL_WIDTH, 1));
		return line;
	}

	/** A bold section header stacked in the body; adds a little space above. */
	private static Component sectionHeader(String text)
	{
		final JPanel wrap = new JPanel(new BorderLayout());
		wrap.setOpaque(false);
		wrap.setBorder(BorderFactory.createEmptyBorder(10, 0, 3, 0));
		wrap.setAlignmentX(Component.LEFT_ALIGNMENT);
		final JLabel label = new JLabel(text);
		label.setFont(label.getFont().deriveFont(Font.BOLD));
		label.setForeground(ColorScheme.BRAND_ORANGE);
		wrap.add(label, BorderLayout.WEST);
		// Cap height so BoxLayout never stretches the header vertically.
		wrap.setMaximumSize(new Dimension(Integer.MAX_VALUE, label.getPreferredSize().height + 13));
		return wrap;
	}

	/** A key (left) / value (right) row that fits the fixed panel width. */
	private static Component kv(String key, String value)
	{
		final JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setOpaque(false);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		final JLabel k = mutedLabel(key);
		final JLabel v = new JLabel(value, SwingConstants.RIGHT);
		row.add(k, BorderLayout.WEST);
		row.add(v, BorderLayout.CENTER);
		// Never let a row demand more than one line of height from BoxLayout.
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, v.getPreferredSize().height + 2));
		return row;
	}

	private static JLabel mutedLabel(String text)
	{
		final JLabel label = new JLabel(text);
		label.setForeground(Color.LIGHT_GRAY);
		return label;
	}

	/** A bold, muted sub-heading used inside the data-disclosure block. */
	private static JLabel mutedBold(String text)
	{
		final JLabel label = new JLabel(text);
		label.setForeground(Color.LIGHT_GRAY);
		label.setFont(label.getFont().deriveFont(Font.BOLD));
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		return label;
	}

	private static Component note(String text)
	{
		// HTML-wrap so long notes wrap to the fixed panel width instead of being clipped or
		// forcing horizontal overflow. Width is a little under PANEL_WIDTH to allow insets.
		final JLabel label = new JLabel(
			"<html><body style='width:" + (PluginPanel.PANEL_WIDTH - 30) + "px'>"
				+ escapeHtml(text) + "</body></html>");
		label.setForeground(Color.LIGHT_GRAY);
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		label.setMaximumSize(new Dimension(Integer.MAX_VALUE, label.getPreferredSize().height));
		return label;
	}

	private static Component spacer(int height)
	{
		return javax.swing.Box.createVerticalStrut(height);
	}
}
