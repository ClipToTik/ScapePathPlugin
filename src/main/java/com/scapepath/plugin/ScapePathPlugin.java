/*
 * Copyright (c) 2026, ScapePath
 * All rights reserved. BSD 2-Clause. See LICENSE file.
 */
package com.scapepath.plugin;

import com.google.inject.Binder;
import com.google.inject.Provides;
import com.scapepath.plugin.collector.AchievementDiaryCollector;
import com.scapepath.plugin.collector.BankCollector;
import com.scapepath.plugin.collector.CollectorRegistry;
import com.scapepath.plugin.collector.EquipmentCollector;
import com.scapepath.plugin.collector.IdentityCollector;
import com.scapepath.plugin.collector.InventoryCollector;
import com.scapepath.plugin.collector.QuestCollector;
import com.scapepath.plugin.collector.SkillsCollector;
import com.scapepath.plugin.collector.WealthCollector;
import com.scapepath.plugin.connection.ConfigTokenStore;
import com.scapepath.plugin.connection.ConnectionManager;
import com.scapepath.plugin.connection.ConnectionState;
import com.scapepath.plugin.connection.TokenStore;
import com.scapepath.plugin.game.BankTracker;
import com.scapepath.plugin.game.DiaryDefinitions;
import com.scapepath.plugin.game.GameStateAccessor;
import com.scapepath.plugin.game.RuneLiteGameStateAccessor;
import com.scapepath.plugin.snapshot.SnapshotService;
import com.scapepath.plugin.transport.OkHttpScapePathTransport;
import com.scapepath.plugin.transport.ScapePathTransport;
import com.scapepath.plugin.transport.SnapshotPayloadSerializer;
import com.scapepath.plugin.ui.ScapePathPanel;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.time.Instant;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.events.WorldChanged;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;

/**
 * ScapePath — account progression companion/integration for RuneLite.
 *
 * <p>The plugin reads identity, skills, quests, diaries, inventory, equipment,
 * interface-gated bank, and derived wealth from the live client and displays them in a
 * side panel. It remains passive and read-only — no gameplay automation, no input. When
 * the user explicitly connects their ScapePath account (entering a one-time code), it
 * syncs the same normalized snapshot over HTTPS via {@link ConnectionManager}; nothing is
 * transmitted otherwise, and only the user's own account state is ever sent.</p>
 *
 * <p>Responsibilities kept here are lifecycle and orchestration only: registering
 * collectors, subscribing to RuneLite events, and driving snapshot rebuilds on the
 * client thread. Reading is delegated to
 * {@link com.scapepath.plugin.game.GameStateAccessor} and the collectors.</p>
 */
@Slf4j
@PluginDescriptor(
	name = "ScapePath",
	description = "Read-only OSRS account progression companion; optionally syncs your own "
		+ "account state to ScapePath over HTTPS after you connect with a one-time code",
	tags = {"account", "progression", "skills", "quests", "diary", "bank", "scapepath"}
)
public class ScapePathPlugin extends Plugin
{
	@Inject
	private ScapePathConfig config;

	@Inject
	private ConfigManager configManager;

	@Inject
	private ConnectionManager connectionManager;

	@Inject
	private CollectorRegistry collectorRegistry;

	@Inject
	private SnapshotService snapshotService;

	@Inject
	private IdentityCollector identityCollector;

	@Inject
	private SkillsCollector skillsCollector;

	@Inject
	private QuestCollector questCollector;

	@Inject
	private AchievementDiaryCollector achievementDiaryCollector;

	@Inject
	private InventoryCollector inventoryCollector;

	@Inject
	private EquipmentCollector equipmentCollector;

	@Inject
	private BankCollector bankCollector;

	@Inject
	private WealthCollector wealthCollector;

	@Inject
	private BankTracker bankTracker;

	@Inject
	private GameStateAccessor game;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private SnapshotPayloadSerializer payloadSerializer;

	private ScapePathPanel panel;
	private NavigationButton navButton;

	/** Coalesces bursts of events into a single rebuild on the next game tick. */
	private volatile boolean snapshotDirty;

	@Override
	public void configure(Binder binder)
	{
		// Bind the read-only client seam to its live implementation.
		binder.bind(GameStateAccessor.class).to(RuneLiteGameStateAccessor.class);
		// Bind the network transport (OkHttp, RuneLite-bundled) and local token store.
		binder.bind(ScapePathTransport.class).to(OkHttpScapePathTransport.class);
		binder.bind(TokenStore.class).to(ConfigTokenStore.class);
	}

	@Override
	protected void startUp()
	{
		log.debug("ScapePath started (build {})", ScapePath.VERSION);

		connectionManager.setStateListener(this::onConnectionStateChanged);
		connectionManager.setSnapshotSupplier(snapshotService::getLatest);

		// Register collectors. Order is irrelevant; each owns one section.
		collectorRegistry.register(identityCollector);
		collectorRegistry.register(skillsCollector);
		collectorRegistry.register(questCollector);
		collectorRegistry.register(achievementDiaryCollector);
		collectorRegistry.register(inventoryCollector);
		collectorRegistry.register(equipmentCollector);
		collectorRegistry.register(bankCollector);
		collectorRegistry.register(wealthCollector);

		// Side panel: snapshot view + connection controls.
		panel = new ScapePathPanel(payloadSerializer);
		panel.setRefreshHandler(this::requestRefresh);
		panel.setConnectionHandlers(
			connectionManager::link,
			connectionManager::syncNow,
			connectionManager::disconnect);
		snapshotService.setListener(panel::update);

		navButton = NavigationButton.builder()
			.tooltip("ScapePath")
			.icon(createIcon())
			.priority(7)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);

		// Restore any prior connection from local storage (no network).
		connectionManager.restore();

		// Build an initial snapshot on the client thread (reflects current login state).
		requestRefresh();
	}

	@Override
	protected void shutDown()
	{
		if (navButton != null)
		{
			clientToolbar.removeNavigation(navButton);
			navButton = null;
		}
		snapshotService.setListener(null);
		panel = null;

		// Detach listeners only. Do NOT disconnect: stopping the plugin (or closing the
		// client) must not revoke the user's ScapePath link — the token persists so the
		// connection survives restarts.
		connectionManager.setStateListener(null);
		connectionManager.setSnapshotSupplier(null);
		bankTracker.reset();
		snapshotDirty = false;
		log.debug("ScapePath stopped");
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGIN_SCREEN)
		{
			// Logged out: clear the cached bank so one account's bank is never shown for
			// another. It reverts to "never synced" until reopened.
			bankTracker.reset();
		}

		// Login (and other transitions) invalidate the snapshot.
		if (event.getGameState() == GameState.LOGGED_IN
			|| event.getGameState() == GameState.LOGIN_SCREEN)
		{
			snapshotDirty = true;
		}
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		final int id = event.getContainerId();
		if (id == InventoryID.BANK)
		{
			// A fresh, authoritative read of the bank (bank is open). Cache it with a
			// timestamp; freshness is decided by the BankCollector from the open flag.
			bankTracker.updateItems(game.readContainer(InventoryID.BANK), Instant.now());
			snapshotDirty = true;
		}
		else if (id == InventoryID.INV || id == InventoryID.WORN)
		{
			snapshotDirty = true;
		}
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (event.getGroupId() == InterfaceID.BANKMAIN)
		{
			bankTracker.setOpen(true);
			snapshotDirty = true;
		}
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed event)
	{
		if (event.getGroupId() == InterfaceID.BANKMAIN)
		{
			// Bank closed: retain the cached contents but mark them stale.
			bankTracker.setOpen(false);
			snapshotDirty = true;
		}
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		// A skill changed; mark dirty and let onGameTick coalesce the rebuild.
		snapshotDirty = true;
	}

	@Subscribe
	public void onWorldChanged(WorldChanged event)
	{
		snapshotDirty = true;
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		// Targeted, not blanket: only rebuild for quest-point (quest completion) or
		// achievement-diary varbit changes, so we don't rebuild on every unrelated varbit.
		if (event.getVarpId() == VarPlayerID.QP
			|| DiaryDefinitions.varbitIds().contains(event.getVarbitId()))
		{
			snapshotDirty = true;
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		// Event handlers run on the client thread, so reading here is safe. This
		// coalesces bursts (e.g. the ~23 StatChanged events fired at login) into one
		// rebuild per tick at most, avoiding any aggressive polling loop.
		if (snapshotDirty)
		{
			snapshotDirty = false;
			snapshotService.rebuild();
			// After a rebuild, offer the fresh snapshot to the sync layer. This is
			// throttled + opt-in inside the manager; it dispatches network work
			// off-thread and never blocks this client tick.
			connectionManager.maybeAutoSync(config.syncEnabled());
		}
	}

	/** Schedule a snapshot rebuild on the client thread (used by the panel button). */
	private void requestRefresh()
	{
		clientThread.invoke(snapshotService::rebuild);
	}

	/** Reflect connection state into the read-only config status + the side panel. */
	private void onConnectionStateChanged(ConnectionState state)
	{
		configManager.setConfiguration(ScapePath.CONFIG_GROUP, "connectionStatus", state.getDisplayText());
		final ScapePathPanel p = panel;
		if (p != null)
		{
			p.updateConnection(state, connectionManager.isConnected(),
				connectionManager.getLastSyncAt(), connectionManager.getLastError());
		}
	}

	private static BufferedImage createIcon()
	{
		// Simple generated 16x16 icon so no image resource needs packaging yet.
		final BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		final java.awt.Graphics2D g = image.createGraphics();
		g.setColor(new Color(0xE8, 0x8A, 0x1A));
		g.fillRoundRect(1, 1, 14, 14, 4, 4);
		g.setColor(Color.WHITE);
		g.drawLine(4, 11, 8, 5);
		g.drawLine(8, 5, 12, 11);
		g.dispose();
		return image;
	}

	@Provides
	ScapePathConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(ScapePathConfig.class);
	}
}
