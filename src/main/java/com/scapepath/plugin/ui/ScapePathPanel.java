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
import java.util.LinkedHashMap;
import java.util.Map;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.time.Duration;
import java.time.Instant;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

/**
 * Local, development-oriented diagnostic panel proving the plugin reads account state.
 *
 * <p>Strictly local: it renders only the cached {@link AccountSnapshot} and transmits
 * nothing. All mutation happens on the Swing EDT.</p>
 */
public class ScapePathPanel extends PluginPanel
{
	private final JLabel statusLabel = new JLabel();
	private final JPanel identityPanel = new JPanel();
	private final JPanel skillsPanel = new JPanel();
	private final JPanel extrasPanel = new JPanel();
	private final JPanel syncPanel = new JPanel();

	private final SnapshotPayloadSerializer serializer;
	private AccountSnapshot lastSnapshot;

	private Runnable refreshHandler = () -> { };

	public ScapePathPanel(SnapshotPayloadSerializer serializer)
	{
		super(false);
		this.serializer = serializer;
		setLayout(new BorderLayout());
		setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

		final JPanel content = new JPanel();
		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

		content.add(title("ScapePath (local only)"));
		content.add(statusLabel);
		content.add(sectionSpacer());

		final JButton refreshButton = new JButton("Refresh now");
		refreshButton.addActionListener(e -> refreshHandler.run());
		refreshButton.setAlignmentX(Component.LEFT_ALIGNMENT);
		content.add(refreshButton);
		content.add(sectionSpacer());

		// Local "what would be sent" preview. Nothing is transmitted.
		content.add(title("ScapePath Account Sync"));
		syncPanel.setLayout(new BoxLayout(syncPanel, BoxLayout.Y_AXIS));
		syncPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		content.add(syncPanel);
		final JButton viewJsonButton = new JButton("View payload JSON");
		viewJsonButton.addActionListener(e -> showPayloadJson());
		viewJsonButton.setAlignmentX(Component.LEFT_ALIGNMENT);
		content.add(viewJsonButton);
		content.add(sectionSpacer());

		content.add(title("Account"));
		identityPanel.setLayout(new BoxLayout(identityPanel, BoxLayout.Y_AXIS));
		identityPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		content.add(identityPanel);
		content.add(sectionSpacer());

		// Inventory / Equipment / Bank / Wealth diagnostics.
		extrasPanel.setLayout(new BoxLayout(extrasPanel, BoxLayout.Y_AXIS));
		extrasPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		content.add(extrasPanel);
		content.add(sectionSpacer());

		content.add(title("Skills"));
		skillsPanel.setLayout(new GridLayout(0, 2, 4, 2));
		skillsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		content.add(skillsPanel);

		add(content, BorderLayout.NORTH);

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
		identityPanel.removeAll();
		skillsPanel.removeAll();
		extrasPanel.removeAll();
		syncPanel.removeAll();

		if (snapshot == null)
		{
			renderEmpty();
			return;
		}

		renderSyncPreview(snapshot);

		final CollectedSection identity = snapshot.getSection(SnapshotSectionType.IDENTITY);
		final CollectedSection skills = snapshot.getSection(SnapshotSectionType.SKILLS);

		final boolean loggedIn = identity != null && identity.getData() instanceof IdentityData;
		statusLabel.setText(loggedIn ? "Status: Local only — logged in" : "Status: Local only — not logged in");

		if (loggedIn)
		{
			final IdentityData id = (IdentityData) identity.getData();
			identityPanel.add(kv("RSN", id.getRsn() == null ? "-" : id.getRsn()));
			identityPanel.add(kv("World", String.valueOf(id.getWorld())));
			identityPanel.add(kv("Type", id.getAccountType() == null ? "-" : id.getAccountType()));
		}
		else
		{
			identityPanel.add(new JLabel("Not logged in"));
		}

		if (skills != null && skills.getData() instanceof SkillsData)
		{
			final SkillsData sd = (SkillsData) skills.getData();
			identityPanel.add(kv("Combat", String.valueOf(sd.getCombatLevel())));
			identityPanel.add(kv("Total level", String.valueOf(sd.getTotalLevel())));
			for (SkillData skill : sd.getSkills())
			{
				skillsPanel.add(new JLabel(skill.getName()));
				skillsPanel.add(new JLabel(String.valueOf(skill.getLevel())));
			}
		}
		else
		{
			skillsPanel.add(new JLabel("-"));
			skillsPanel.add(new JLabel(""));
		}

		renderQuests(snapshot);
		renderDiaries(snapshot);
		renderInventory(snapshot);
		renderEquipment(snapshot);
		renderWealth(snapshot);
		renderBank(snapshot);

		revalidate();
		repaint();
	}

	private void renderQuests(AccountSnapshot snapshot)
	{
		extrasPanel.add(title("Quests"));
		final CollectedSection s = snapshot.getSection(SnapshotSectionType.QUESTS);
		if (s != null && s.getData() instanceof QuestsData)
		{
			final QuestsData q = (QuestsData) s.getData();
			extrasPanel.add(kv("Complete", q.getCompletedCount() + " / " + q.getTotalCount()));
			extrasPanel.add(kv("Quest points", String.valueOf(q.getQuestPoints())));
		}
		else
		{
			extrasPanel.add(new JLabel("Not available"));
		}
		extrasPanel.add(sectionSpacer());
	}

	private void renderDiaries(AccountSnapshot snapshot)
	{
		extrasPanel.add(title("Achievement Diaries"));
		final CollectedSection s = snapshot.getSection(SnapshotSectionType.ACHIEVEMENT_DIARIES);
		if (s == null || !(s.getData() instanceof AchievementDiaryData))
		{
			extrasPanel.add(new JLabel("Not available"));
			extrasPanel.add(sectionSpacer());
			return;
		}

		final AchievementDiaryData d = (AchievementDiaryData) s.getData();
		// Per-tier completed / total across all regions.
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
			extrasPanel.add(kv(e.getKey(), e.getValue()[0] + " / " + e.getValue()[1]));
		}
		extrasPanel.add(kv("Total tiers", d.getCompletedTiers() + " / " + d.getTotalTiers()));
		extrasPanel.add(sectionSpacer());
	}

	private void renderInventory(AccountSnapshot snapshot)
	{
		extrasPanel.add(title("Inventory"));
		final CollectedSection s = snapshot.getSection(SnapshotSectionType.INVENTORY);
		if (s != null && s.getData() instanceof InventoryData)
		{
			extrasPanel.add(kv("Occupied slots", ((InventoryData) s.getData()).getOccupiedSlots() + " / 28"));
		}
		else
		{
			extrasPanel.add(new JLabel("Not available"));
		}
		extrasPanel.add(sectionSpacer());
	}

	private void renderEquipment(AccountSnapshot snapshot)
	{
		extrasPanel.add(title("Equipment"));
		final CollectedSection s = snapshot.getSection(SnapshotSectionType.EQUIPMENT);
		if (s != null && s.getData() instanceof EquipmentData)
		{
			extrasPanel.add(kv("Equipped items", String.valueOf(((EquipmentData) s.getData()).getItems().size())));
		}
		else
		{
			extrasPanel.add(new JLabel("Not available"));
		}
		extrasPanel.add(sectionSpacer());
	}

	private void renderWealth(AccountSnapshot snapshot)
	{
		extrasPanel.add(title("Wealth"));
		final CollectedSection s = snapshot.getSection(SnapshotSectionType.WEALTH);
		if (s != null && s.getData() instanceof WealthData)
		{
			final WealthData w = (WealthData) s.getData();
			extrasPanel.add(kv("GP on hand", formatGp(w.getGpOnHand())));
			extrasPanel.add(kv("Bank GP", w.getBankGp() == null ? "—" : formatGp(w.getBankGp())));
			extrasPanel.add(kv("Est. bank value",
				w.getEstimatedBankValue() == null ? "—" : "~" + formatGp(w.getEstimatedBankValue())));
		}
		else
		{
			extrasPanel.add(new JLabel("Not available"));
		}
		extrasPanel.add(sectionSpacer());
	}

	private void renderBank(AccountSnapshot snapshot)
	{
		extrasPanel.add(title("Bank"));
		final CollectedSection s = snapshot.getSection(SnapshotSectionType.BANK);
		final boolean synced = s != null && s.getData() instanceof BankData;
		if (!synced)
		{
			extrasPanel.add(new JLabel("Status: Not synced"));
			extrasPanel.add(new JLabel("Open your bank to sync it."));
			return;
		}

		final BankData bank = (BankData) s.getData();
		final boolean current = s.getFreshness() == SourceFreshness.COMPLETE;
		extrasPanel.add(kv("Status", current ? "Synced (current)" : "Cached (stale)"));
		extrasPanel.add(kv("Last opened", relativeTime(s.getCollectedAt())));
		extrasPanel.add(kv("Items", String.valueOf(bank.getUniqueItems())));
		extrasPanel.add(kv("Est. value", "~" + formatGp(bank.getEstimatedValue())));
	}

	private void renderEmpty()
	{
		statusLabel.setText("Status: Local only — no snapshot yet");
		identityPanel.add(new JLabel("Not logged in"));
		extrasPanel.removeAll();
		syncPanel.removeAll();
		syncPanel.add(new JLabel("Nothing is currently being transmitted."));
		skillsPanel.add(new JLabel("-"));
		skillsPanel.add(new JLabel(""));
	}

	private void renderSyncPreview(AccountSnapshot snapshot)
	{
		final PayloadPreview preview = serializer.preview(snapshot);

		syncPanel.add(new JLabel("Nothing is currently being transmitted."));
		syncPanel.add(new JLabel("Future sync would include:"));
		for (PayloadPreview.SectionSummary s : preview.getSections())
		{
			syncPanel.add(kv(s.getKey(), s.getFreshness()));
		}
		syncPanel.add(sectionSpacer());
		syncPanel.add(kv("Schema version", String.valueOf(preview.getSchemaVersion())));
		syncPanel.add(kv("Plugin version", preview.getPluginVersion()));
		syncPanel.add(kv("Snapshot", preview.getTimestamp() == null ? "-" : preview.getTimestamp()));
		syncPanel.add(kv("Payload size", formatBytes(preview.getByteSize())));
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

	private static String formatBytes(int bytes)
	{
		if (bytes >= 1024)
		{
			return String.format("%.1f KB (%,d bytes)", bytes / 1024.0, bytes);
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

	private static JLabel title(String text)
	{
		final JLabel label = new JLabel(text);
		label.setFont(label.getFont().deriveFont(Font.BOLD));
		label.setForeground(ColorScheme.BRAND_ORANGE);
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		return label;
	}

	private static JPanel kv(String key, String value)
	{
		final JPanel row = new JPanel(new BorderLayout());
		row.add(new JLabel(key), BorderLayout.WEST);
		final JLabel v = new JLabel(value);
		row.add(v, BorderLayout.EAST);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		return row;
	}

	private static Component sectionSpacer()
	{
		final JPanel spacer = new JPanel();
		spacer.setPreferredSize(new java.awt.Dimension(1, 8));
		spacer.setAlignmentX(Component.LEFT_ALIGNMENT);
		return spacer;
	}
}
