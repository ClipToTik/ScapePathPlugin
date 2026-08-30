/*
 * Copyright (c) 2026, ScapePath
 * All rights reserved. BSD 2-Clause. See LICENSE file.
 */
package com.scapepath.plugin.ui;

import com.scapepath.plugin.connection.ConnectionState;
import com.scapepath.plugin.snapshot.AccountSnapshot;
import com.scapepath.plugin.snapshot.CollectedSection;
import com.scapepath.plugin.snapshot.SnapshotSectionType;
import com.scapepath.plugin.snapshot.SourceFreshness;
import com.scapepath.plugin.snapshot.data.AchievementDiaryData;
import com.scapepath.plugin.snapshot.data.BankData;
import com.scapepath.plugin.snapshot.data.DiaryTierSnapshot;
import com.scapepath.plugin.snapshot.data.EquipmentData;
import com.scapepath.plugin.snapshot.data.IdentityData;
import com.scapepath.plugin.snapshot.data.InventoryData;
import com.scapepath.plugin.snapshot.data.QuestsData;
import com.scapepath.plugin.snapshot.data.SkillData;
import com.scapepath.plugin.snapshot.data.SkillsData;
import com.scapepath.plugin.snapshot.data.WealthData;
import com.scapepath.plugin.transport.PayloadPreview;
import com.scapepath.plugin.transport.SnapshotPayloadSerializer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

/**
 * ScapePath side panel: connection controls (connect with a one-time code, sync now,
 * disconnect) plus a view of the cached {@link AccountSnapshot} and a preview of the
 * exact payload that is synced to ScapePath when connected.
 *
 * <p>The panel uses RuneLite's native {@link PluginPanel} wrapping (fixed sidebar width,
 * vertical scrollbar as-needed, no horizontal scroll) so it behaves like a conventional
 * RuneLite side panel and never distorts the game viewport. Content is a single scrolling
 * column of clearly-headed sections. All mutation happens on the Swing EDT.</p>
 */
public class ScapePathPanel extends PluginPanel
{
	/** "Connected" confirmation colour and error colour, defined once. */
	private static final Color CONNECTED_GREEN = new Color(0x4C, 0xAF, 0x50);
	private static final Color ERROR_RED = new Color(0xD0, 0x60, 0x60);

	private final SnapshotPayloadSerializer serializer;
	private AccountSnapshot lastSnapshot;

	private final JLabel statusLabel = new JLabel();
	/** Holds the dynamic sections; rebuilt on each snapshot update. */
	private final JPanel body = new JPanel();

	/** Connection controls (link/sync/disconnect); rebuilt on each connection update. */
	private final JPanel connectionPanel = new JPanel();

	private Runnable refreshHandler = () -> { };
	private java.util.function.Consumer<String> connectHandler = code -> { };
	private Runnable syncHandler = () -> { };
	private Runnable disconnectHandler = () -> { };

	/**
	 * Remembers each collapsible section's expanded/collapsed state across snapshot
	 * re-renders (which rebuild {@link #body} from scratch), keyed by section title.
	 * Absent ⇒ use the section's default. Lets a user collapse verbose sections and have
	 * that choice stick.
	 */
	private final Map<String, Boolean> collapsed = new HashMap<>();

	// Latest connection view state (rendered by rebuildConnection on the EDT).
	private ConnectionState connState = ConnectionState.DISCONNECTED;
	private boolean connLinked;
	private Instant connLastSync;
	private String connError;

	public ScapePathPanel(SnapshotPayloadSerializer serializer)
	{
		// wrap=true: RuneLite provides the scroll pane, fixed width, and viewport-managed
		// height. Do NOT override the layout/border it sets up.
		super(true);
		this.serializer = serializer;

		add(heading("ScapePath"));
		add(separator());

		connectionPanel.setLayout(new BoxLayout(connectionPanel, BoxLayout.Y_AXIS));
		connectionPanel.setOpaque(false);
		add(connectionPanel);
		add(separator());

		add(statusLabel);

		final JButton refreshButton = new JButton("Refresh now");
		refreshButton.addActionListener(e -> refreshHandler.run());
		add(refreshButton);

		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
		body.setOpaque(false);
		add(body);

		rebuildConnection();
		renderEmpty();
	}

	/** Wire the "Refresh now" button to the orchestration layer. */
	public void setRefreshHandler(Runnable handler)
	{
		this.refreshHandler = handler == null ? () -> { } : handler;
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
			// Connected view: clear confirmation, the account, last sync, and controls.
			final JLabel connected = new JLabel("✓  Connected to ScapePath");
			connected.setFont(connected.getFont().deriveFont(Font.BOLD));
			connected.setForeground(CONNECTED_GREEN);
			connected.setAlignmentX(Component.LEFT_ALIGNMENT);
			connectionPanel.add(connected);
			connectionPanel.add(spacer(2));

			final String rsn = currentRsn();
			connectionPanel.add(kv("Account", rsn == null ? "—" : rsn));
			connectionPanel.add(kv("Status", connState.getDisplayText()));
			connectionPanel.add(kv("Last sync", relativeTime(connLastSync)));

			final JButton syncButton = new JButton("Sync now");
			syncButton.setAlignmentX(Component.LEFT_ALIGNMENT);
			syncButton.addActionListener(e -> syncHandler.run());
			connectionPanel.add(spacer(4));
			connectionPanel.add(syncButton);

			final JButton disconnectButton = new JButton("Disconnect");
			disconnectButton.setAlignmentX(Component.LEFT_ALIGNMENT);
			disconnectButton.addActionListener(e -> disconnectHandler.run());
			connectionPanel.add(spacer(2));
			connectionPanel.add(disconnectButton);
		}
		else
		{
			// Disconnected view: a numbered, unambiguous path for a first-time user.
			final JLabel title = new JLabel("Connect ScapePath");
			title.setFont(title.getFont().deriveFont(Font.BOLD));
			title.setForeground(ColorScheme.BRAND_ORANGE);
			title.setAlignmentX(Component.LEFT_ALIGNMENT);
			connectionPanel.add(title);
			connectionPanel.add(spacer(2));
			connectionPanel.add(note("1. Open ScapePath → Profile → Generate RuneLite Code."));
			connectionPanel.add(note("2. Enter the code below to connect this account."));

			final JTextField codeField = new JTextField();
			codeField.setMaximumSize(new Dimension(Integer.MAX_VALUE, codeField.getPreferredSize().height + 4));
			codeField.setAlignmentX(Component.LEFT_ALIGNMENT);
			codeField.setToolTipText("Paste your one-time ScapePath connection code");
			// Enter in the field connects, same as the button.
			codeField.addActionListener(e -> connectHandler.accept(codeField.getText()));
			connectionPanel.add(spacer(4));
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

	/** RSN from the latest local snapshot's identity, or null when unknown/logged out. */
	private String currentRsn()
	{
		final AccountSnapshot snap = lastSnapshot;
		if (snap == null)
		{
			return null;
		}
		final CollectedSection id = snap.getSection(SnapshotSectionType.IDENTITY);
		if (id != null && id.getData() instanceof IdentityData)
		{
			return ((IdentityData) id.getData()).getRsn();
		}
		return null;
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
		body.removeAll();

		if (snapshot == null)
		{
			renderEmpty();
			return;
		}

		final CollectedSection identity = snapshot.getSection(SnapshotSectionType.IDENTITY);
		final boolean loggedIn = identity != null && identity.getData() instanceof IdentityData;
		statusLabel.setText(loggedIn ? "Logged in" : "Not logged in");

		renderAccount(snapshot, loggedIn);
		renderSkills(snapshot);
		renderQuests(snapshot);
		renderDiaries(snapshot);
		renderInventory(snapshot);
		renderEquipment(snapshot);
		renderBank(snapshot);
		renderWealth(snapshot);
		renderSnapshot(snapshot);

		// Refresh the connection area so the account RSN reflects the latest snapshot.
		rebuildConnection();

		body.revalidate();
		body.repaint();
	}

	private void renderEmpty()
	{
		statusLabel.setText("No snapshot yet");
		body.removeAll();
		body.add(sectionHeader("Account"));
		body.add(note("Not logged in."));
		body.add(sectionHeader("Snapshot"));
		body.add(note("Nothing is transmitted. Log in to view your local snapshot."));
		body.revalidate();
		body.repaint();
	}

	// --- Sections ---------------------------------------------------------------------
	//
	// Every data section is a collapsible block (clickable header toggles it) so the panel
	// stays compact even for a maxed account, and a user's collapse choice is remembered
	// across re-renders. Rows are added to the section's content panel, never to `body`
	// directly, which keeps each section's height bounded and avoids horizontal overflow.

	private void renderAccount(AccountSnapshot snapshot, boolean loggedIn)
	{
		final JPanel c = section("Account", false);
		if (!loggedIn)
		{
			c.add(note("Not logged in."));
			return;
		}
		final IdentityData id = (IdentityData)
			snapshot.getSection(SnapshotSectionType.IDENTITY).getData();
		c.add(kv("RSN", id.getRsn() == null ? "-" : id.getRsn()));
		c.add(kv("World", String.valueOf(id.getWorld())));
		c.add(kv("Type", id.getAccountType() == null ? "-" : id.getAccountType()));
	}

	private void renderSkills(AccountSnapshot snapshot)
	{
		final JPanel c = section("Skills", false);
		final CollectedSection s = snapshot.getSection(SnapshotSectionType.SKILLS);
		if (s == null || !(s.getData() instanceof SkillsData))
		{
			c.add(note("Not available."));
			return;
		}
		final SkillsData sd = (SkillsData) s.getData();
		c.add(kv("Combat", String.valueOf(sd.getCombatLevel())));
		c.add(kv("Total level", String.valueOf(sd.getTotalLevel())));

		final JPanel grid = new JPanel(new GridLayout(0, 2, 8, 2));
		grid.setOpaque(false);
		grid.setAlignmentX(Component.LEFT_ALIGNMENT);
		for (SkillData skill : sd.getSkills())
		{
			grid.add(mutedLabel(skill.getName()));
			final JLabel lvl = new JLabel(String.valueOf(skill.getLevel()), SwingConstants.RIGHT);
			grid.add(lvl);
		}
		// Bound the grid's height so BoxLayout never stretches it vertically.
		grid.setMaximumSize(new Dimension(Integer.MAX_VALUE, grid.getPreferredSize().height));
		c.add(grid);
	}

	private void renderQuests(AccountSnapshot snapshot)
	{
		final JPanel c = section("Quests", false);
		final CollectedSection s = snapshot.getSection(SnapshotSectionType.QUESTS);
		if (s != null && s.getData() instanceof QuestsData)
		{
			final QuestsData q = (QuestsData) s.getData();
			c.add(kv("Complete", q.getCompletedCount() + " / " + q.getTotalCount()));
			c.add(kv("Quest points", String.valueOf(q.getQuestPoints())));
		}
		else
		{
			c.add(note("Not available."));
		}
	}

	private void renderDiaries(AccountSnapshot snapshot)
	{
		final JPanel c = section("Achievement Diaries", false);
		final CollectedSection s = snapshot.getSection(SnapshotSectionType.ACHIEVEMENT_DIARIES);
		if (s == null || !(s.getData() instanceof AchievementDiaryData))
		{
			c.add(note("Not available."));
			return;
		}
		final AchievementDiaryData d = (AchievementDiaryData) s.getData();
		final Map<String, int[]> perTier = new LinkedHashMap<>();
		perTier.put("Easy", new int[2]);
		perTier.put("Medium", new int[2]);
		perTier.put("Hard", new int[2]);
		perTier.put("Elite", new int[2]);
		for (DiaryTierSnapshot t : d.getTiers())
		{
			final int[] counts = perTier.get(t.getTier());
			if (counts != null)
			{
				counts[1]++;
				if (t.isCompleted())
				{
					counts[0]++;
				}
			}
		}
		for (Map.Entry<String, int[]> e : perTier.entrySet())
		{
			c.add(kv(e.getKey(), e.getValue()[0] + " / " + e.getValue()[1]));
		}
		c.add(kv("Total tiers", d.getCompletedTiers() + " / " + d.getTotalTiers()));
	}

	private void renderInventory(AccountSnapshot snapshot)
	{
		final JPanel c = section("Inventory", false);
		final CollectedSection s = snapshot.getSection(SnapshotSectionType.INVENTORY);
		if (s != null && s.getData() instanceof InventoryData)
		{
			c.add(kv("Occupied slots", ((InventoryData) s.getData()).getOccupiedSlots() + " / 28"));
		}
		else
		{
			c.add(note("Not available."));
		}
	}

	private void renderEquipment(AccountSnapshot snapshot)
	{
		final JPanel c = section("Equipment", false);
		final CollectedSection s = snapshot.getSection(SnapshotSectionType.EQUIPMENT);
		if (s != null && s.getData() instanceof EquipmentData)
		{
			c.add(kv("Equipped items", String.valueOf(((EquipmentData) s.getData()).getItems().size())));
		}
		else
		{
			c.add(note("Not available."));
		}
	}

	private void renderBank(AccountSnapshot snapshot)
	{
		final JPanel c = section("Bank", false);
		final CollectedSection s = snapshot.getSection(SnapshotSectionType.BANK);
		final boolean synced = s != null && s.getData() instanceof BankData;
		if (!synced)
		{
			c.add(note("Not synced — open your bank to sync it."));
			return;
		}
		final BankData bank = (BankData) s.getData();
		final boolean current = s.getFreshness() == SourceFreshness.COMPLETE;
		c.add(kv("Status", current ? "Synced (current)" : "Cached (stale)"));
		c.add(kv("Last opened", relativeTime(s.getCollectedAt())));
		c.add(kv("Items", String.valueOf(bank.getUniqueItems())));
		c.add(kv("Est. value", "~" + formatGp(bank.getEstimatedValue())));
	}

	private void renderWealth(AccountSnapshot snapshot)
	{
		final JPanel c = section("Wealth", false);
		final CollectedSection s = snapshot.getSection(SnapshotSectionType.WEALTH);
		if (s != null && s.getData() instanceof WealthData)
		{
			final WealthData w = (WealthData) s.getData();
			c.add(kv("GP on hand", formatGp(w.getGpOnHand())));
			c.add(kv("Bank GP", w.getBankGp() == null ? "—" : formatGp(w.getBankGp())));
			c.add(kv("Est. bank value",
				w.getEstimatedBankValue() == null ? "—" : "~" + formatGp(w.getEstimatedBankValue())));
		}
		else
		{
			c.add(note("Not available."));
		}
	}

	private void renderSnapshot(AccountSnapshot snapshot)
	{
		// The technical sync/preview section — collapsed by default to keep the panel compact.
		final JPanel c = section("Snapshot & sync", true);
		final PayloadPreview preview = serializer.preview(snapshot);

		c.add(note("This is the exact payload synced to ScapePath when connected."));
		c.add(kv("Schema version", String.valueOf(preview.getSchemaVersion())));
		c.add(kv("Plugin version", preview.getPluginVersion()));
		c.add(kv("Payload size", formatBytes(preview.getByteSize())));
		for (PayloadPreview.SectionSummary sum : preview.getSections())
		{
			c.add(kv(sum.getKey(), sum.getFreshness()));
		}

		final JButton viewJsonButton = new JButton("View payload JSON");
		viewJsonButton.setAlignmentX(Component.LEFT_ALIGNMENT);
		viewJsonButton.addActionListener(e -> showPayloadJson());
		c.add(spacer(6));
		c.add(viewJsonButton);
	}

	/**
	 * Create a collapsible section: a clickable header that toggles a content panel,
	 * appended to {@link #body}. Rows are added to the returned content panel. The
	 * expanded/collapsed state is remembered in {@link #collapsed} across re-renders.
	 *
	 * @param title           section heading (also the memory key)
	 * @param defaultCollapsed initial state when the user has not toggled it before
	 * @return the content panel to add rows into
	 */
	private JPanel section(String title, boolean defaultCollapsed)
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
				body.revalidate();
				body.repaint();
			}
		});

		body.add(header);
		body.add(content);
		return content;
	}

	/** Disclosure triangle prefix: ▾ when expanded, ▸ when collapsed. */
	private static String arrow(boolean expanded)
	{
		return expanded ? "▾  " : "▸  ";
	}

	private void showPayloadJson()
	{
		final String json = lastSnapshot == null
			? "No snapshot yet."
			: serializer.toJson(lastSnapshot);

		final JTextArea area = new JTextArea(json, 24, 48);
		area.setEditable(false);
		area.setLineWrap(true);
		area.setWrapStyleWord(false);
		final JScrollPane scroll = new JScrollPane(area);
		scroll.setPreferredSize(new Dimension(480, 480));
		// Local-only diagnostic dialog; makes no network request.
		JOptionPane.showMessageDialog(this, scroll, "ScapePath payload (local preview)",
			JOptionPane.PLAIN_MESSAGE);
	}

	// --- Formatting helpers (unchanged behavior) --------------------------------------

	private static String formatBytes(int bytes)
	{
		if (bytes >= 1024)
		{
			return String.format("%.1f KB", bytes / 1024.0);
		}
		return bytes + " bytes";
	}

	private static String formatGp(long gp)
	{
		final long abs = Math.abs(gp);
		if (abs >= 1_000_000_000L)
		{
			return String.format("%.1fB", gp / 1_000_000_000.0);
		}
		if (abs >= 1_000_000L)
		{
			return String.format("%.1fM", gp / 1_000_000.0);
		}
		if (abs >= 10_000L)
		{
			return String.format("%.1fK", gp / 1_000.0);
		}
		return String.format("%,d", gp);
	}

	private static String relativeTime(Instant when)
	{
		if (when == null)
		{
			return "-";
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
		return label;
	}

	/** A thin horizontal divider under the heading. */
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
