/*
 * Copyright (c) 2026, ScapePath
 * All rights reserved. BSD 2-Clause. See LICENSE file.
 */
package com.scapepath.plugin.ui;

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
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.time.Duration;
import java.time.Instant;
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
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

/**
 * Local diagnostic side panel that renders the cached {@link AccountSnapshot} and a local
 * preview of the snapshot payload. Strictly local: it transmits nothing.
 *
 * <p>The panel uses RuneLite's native {@link PluginPanel} wrapping (fixed sidebar width,
 * vertical scrollbar as-needed, no horizontal scroll) so it behaves like a conventional
 * RuneLite side panel and never distorts the game viewport. Content is a single scrolling
 * column of clearly-headed sections. All mutation happens on the Swing EDT.</p>
 */
public class ScapePathPanel extends PluginPanel
{
	private final SnapshotPayloadSerializer serializer;
	private AccountSnapshot lastSnapshot;

	private final JLabel statusLabel = new JLabel();
	/** Holds the dynamic sections; rebuilt on each snapshot update. */
	private final JPanel body = new JPanel();

	private Runnable refreshHandler = () -> { };

	public ScapePathPanel(SnapshotPayloadSerializer serializer)
	{
		// wrap=true: RuneLite provides the scroll pane, fixed width, and viewport-managed
		// height. Do NOT override the layout/border it sets up.
		super(true);
		this.serializer = serializer;

		add(heading("ScapePath"));
		add(separator());
		add(statusLabel);

		final JButton refreshButton = new JButton("Refresh now");
		refreshButton.addActionListener(e -> refreshHandler.run());
		add(refreshButton);

		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
		body.setOpaque(false);
		add(body);

		renderEmpty();
	}

	/** Wire the "Refresh now" button to the orchestration layer. */
	public void setRefreshHandler(Runnable handler)
	{
		this.refreshHandler = handler == null ? () -> { } : handler;
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
		statusLabel.setText(loggedIn ? "Local only — logged in" : "Local only — not logged in");

		renderAccount(snapshot, loggedIn);
		renderSkills(snapshot);
		renderQuests(snapshot);
		renderDiaries(snapshot);
		renderInventory(snapshot);
		renderEquipment(snapshot);
		renderBank(snapshot);
		renderWealth(snapshot);
		renderSnapshot(snapshot);

		body.revalidate();
		body.repaint();
	}

	private void renderEmpty()
	{
		statusLabel.setText("Local only — no snapshot yet");
		body.removeAll();
		body.add(sectionHeader("Account"));
		body.add(note("Not logged in."));
		body.add(sectionHeader("Snapshot"));
		body.add(note("Nothing is transmitted. Log in to view your local snapshot."));
		body.revalidate();
		body.repaint();
	}

	// --- Sections ---------------------------------------------------------------------

	private void renderAccount(AccountSnapshot snapshot, boolean loggedIn)
	{
		body.add(sectionHeader("Account"));
		if (!loggedIn)
		{
			body.add(note("Not logged in."));
			return;
		}
		final IdentityData id = (IdentityData)
			snapshot.getSection(SnapshotSectionType.IDENTITY).getData();
		body.add(kv("RSN", id.getRsn() == null ? "-" : id.getRsn()));
		body.add(kv("World", String.valueOf(id.getWorld())));
		body.add(kv("Type", id.getAccountType() == null ? "-" : id.getAccountType()));
	}

	private void renderSkills(AccountSnapshot snapshot)
	{
		body.add(sectionHeader("Skills"));
		final CollectedSection s = snapshot.getSection(SnapshotSectionType.SKILLS);
		if (s == null || !(s.getData() instanceof SkillsData))
		{
			body.add(note("Not available."));
			return;
		}
		final SkillsData sd = (SkillsData) s.getData();
		body.add(kv("Combat", String.valueOf(sd.getCombatLevel())));
		body.add(kv("Total level", String.valueOf(sd.getTotalLevel())));

		final JPanel grid = new JPanel(new GridLayout(0, 2, 8, 2));
		grid.setOpaque(false);
		grid.setAlignmentX(Component.LEFT_ALIGNMENT);
		for (SkillData skill : sd.getSkills())
		{
			grid.add(mutedLabel(skill.getName()));
			final JLabel lvl = new JLabel(String.valueOf(skill.getLevel()), SwingConstants.RIGHT);
			grid.add(lvl);
		}
		body.add(grid);
	}

	private void renderQuests(AccountSnapshot snapshot)
	{
		body.add(sectionHeader("Quests"));
		final CollectedSection s = snapshot.getSection(SnapshotSectionType.QUESTS);
		if (s != null && s.getData() instanceof QuestsData)
		{
			final QuestsData q = (QuestsData) s.getData();
			body.add(kv("Complete", q.getCompletedCount() + " / " + q.getTotalCount()));
			body.add(kv("Quest points", String.valueOf(q.getQuestPoints())));
		}
		else
		{
			body.add(note("Not available."));
		}
	}

	private void renderDiaries(AccountSnapshot snapshot)
	{
		body.add(sectionHeader("Achievement Diaries"));
		final CollectedSection s = snapshot.getSection(SnapshotSectionType.ACHIEVEMENT_DIARIES);
		if (s == null || !(s.getData() instanceof AchievementDiaryData))
		{
			body.add(note("Not available."));
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
			body.add(kv(e.getKey(), e.getValue()[0] + " / " + e.getValue()[1]));
		}
		body.add(kv("Total tiers", d.getCompletedTiers() + " / " + d.getTotalTiers()));
	}

	private void renderInventory(AccountSnapshot snapshot)
	{
		body.add(sectionHeader("Inventory"));
		final CollectedSection s = snapshot.getSection(SnapshotSectionType.INVENTORY);
		if (s != null && s.getData() instanceof InventoryData)
		{
			body.add(kv("Occupied slots", ((InventoryData) s.getData()).getOccupiedSlots() + " / 28"));
		}
		else
		{
			body.add(note("Not available."));
		}
	}

	private void renderEquipment(AccountSnapshot snapshot)
	{
		body.add(sectionHeader("Equipment"));
		final CollectedSection s = snapshot.getSection(SnapshotSectionType.EQUIPMENT);
		if (s != null && s.getData() instanceof EquipmentData)
		{
			body.add(kv("Equipped items", String.valueOf(((EquipmentData) s.getData()).getItems().size())));
		}
		else
		{
			body.add(note("Not available."));
		}
	}

	private void renderBank(AccountSnapshot snapshot)
	{
		body.add(sectionHeader("Bank"));
		final CollectedSection s = snapshot.getSection(SnapshotSectionType.BANK);
		final boolean synced = s != null && s.getData() instanceof BankData;
		if (!synced)
		{
			body.add(note("Not synced — open your bank to sync it."));
			return;
		}
		final BankData bank = (BankData) s.getData();
		final boolean current = s.getFreshness() == SourceFreshness.COMPLETE;
		body.add(kv("Status", current ? "Synced (current)" : "Cached (stale)"));
		body.add(kv("Last opened", relativeTime(s.getCollectedAt())));
		body.add(kv("Items", String.valueOf(bank.getUniqueItems())));
		body.add(kv("Est. value", "~" + formatGp(bank.getEstimatedValue())));
	}

	private void renderWealth(AccountSnapshot snapshot)
	{
		body.add(sectionHeader("Wealth"));
		final CollectedSection s = snapshot.getSection(SnapshotSectionType.WEALTH);
		if (s != null && s.getData() instanceof WealthData)
		{
			final WealthData w = (WealthData) s.getData();
			body.add(kv("GP on hand", formatGp(w.getGpOnHand())));
			body.add(kv("Bank GP", w.getBankGp() == null ? "—" : formatGp(w.getBankGp())));
			body.add(kv("Est. bank value",
				w.getEstimatedBankValue() == null ? "—" : "~" + formatGp(w.getEstimatedBankValue())));
		}
		else
		{
			body.add(note("Not available."));
		}
	}

	private void renderSnapshot(AccountSnapshot snapshot)
	{
		body.add(sectionHeader("Snapshot"));
		final PayloadPreview preview = serializer.preview(snapshot);

		body.add(note("Nothing is transmitted. This is a local preview only."));
		body.add(kv("Schema version", String.valueOf(preview.getSchemaVersion())));
		body.add(kv("Plugin version", preview.getPluginVersion()));
		body.add(kv("Payload size", formatBytes(preview.getByteSize())));
		for (PayloadPreview.SectionSummary sum : preview.getSections())
		{
			body.add(kv(sum.getKey(), sum.getFreshness()));
		}

		final JButton viewJsonButton = new JButton("View payload JSON");
		viewJsonButton.setAlignmentX(Component.LEFT_ALIGNMENT);
		viewJsonButton.addActionListener(e -> showPayloadJson());
		body.add(spacer(6));
		body.add(viewJsonButton);
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
		final JLabel label = new JLabel(text);
		label.setForeground(Color.LIGHT_GRAY);
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		return label;
	}

	private static Component spacer(int height)
	{
		return javax.swing.Box.createVerticalStrut(height);
	}
}
