/*
 * Copyright (c) 2026, ScapePath
 * All rights reserved. BSD 2-Clause. See LICENSE file.
 */
package com.scapepath.plugin.transport;

import static org.junit.Assert.assertTrue;
import com.google.gson.JsonParser;
import com.scapepath.plugin.collector.AchievementDiaryCollector;
import com.scapepath.plugin.collector.BankCollector;
import com.scapepath.plugin.collector.CollectorRegistry;
import com.scapepath.plugin.collector.EquipmentCollector;
import com.scapepath.plugin.collector.IdentityCollector;
import com.scapepath.plugin.collector.InventoryCollector;
import com.scapepath.plugin.collector.QuestCollector;
import com.scapepath.plugin.collector.SkillsCollector;
import com.scapepath.plugin.collector.WealthCollector;
import com.scapepath.plugin.game.BankTracker;
import com.scapepath.plugin.game.FakeGameStateAccessor;
import com.scapepath.plugin.snapshot.AccountSnapshot;
import com.scapepath.plugin.snapshot.data.ItemSnapshot;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import net.runelite.api.gameval.InventoryID;
import org.junit.Test;

/**
 * Measures realistic serialized payload sizes and asserts they are reasonable. Sizes are
 * printed so the session can document them.
 */
public class PayloadSizeTest
{
	private final SnapshotPayloadSerializer serializer = new SnapshotPayloadSerializer();

	private static CollectorRegistry fullRegistry(BankTracker tracker)
	{
		CollectorRegistry r = new CollectorRegistry();
		r.register(new IdentityCollector());
		r.register(new SkillsCollector());
		r.register(new QuestCollector());
		r.register(new AchievementDiaryCollector());
		r.register(new InventoryCollector());
		r.register(new EquipmentCollector());
		r.register(new BankCollector(tracker));
		r.register(new WealthCollector(tracker));
		return r;
	}

	private static List<ItemSnapshot> bankOf(int distinctItems)
	{
		List<ItemSnapshot> items = new ArrayList<>();
		for (int i = 0; i < distinctItems; i++)
		{
			items.add(new ItemSnapshot(1000 + i, 1000 + i, i));
		}
		return items;
	}

	private int measure(String label, AccountSnapshot snap)
	{
		String json = serializer.toJson(snap);
		// Validity gate.
		new JsonParser().parse(json);
		int bytes = json.getBytes(StandardCharsets.UTF_8).length;
		System.out.println("[payload-size] " + label + ": " + bytes + " bytes (" +
			String.format("%.1f", bytes / 1024.0) + " KB)");
		return bytes;
	}

	@Test
	public void measuresRepresentativePayloads()
	{
		// Normal account: logged in, all skills, quests, diaries, small inventory/equipment,
		// no bank synced.
		BankTracker noBank = new BankTracker();
		FakeGameStateAccessor normal = new FakeGameStateAccessor()
			.loggedIn("Zezima", 302, 0, 123456789L)
			.allSkills(70, 737627)
			.varp(net.runelite.api.gameval.VarPlayerID.QP, 200)
			.container(InventoryID.INV, bankOf(20))
			.container(InventoryID.WORN, bankOf(11));
		int normalBytes = measure("normal (no bank)", fullRegistry(noBank).buildSnapshot(normal));

		// Full account with a large bank (~800 distinct stacks).
		BankTracker bigBank = new BankTracker();
		bigBank.updateItems(bankOf(800), Instant.parse("2026-08-29T21:40:31Z"));
		bigBank.setOpen(true);
		FakeGameStateAccessor rich = new FakeGameStateAccessor()
			.loggedIn("Zezima", 302, 0, 123456789L)
			.allSkills(99, 13034431)
			.varp(net.runelite.api.gameval.VarPlayerID.QP, 300)
			.container(InventoryID.INV, bankOf(28))
			.container(InventoryID.WORN, bankOf(11));
		int bigBytes = measure("full + large bank (800 stacks)", fullRegistry(bigBank).buildSnapshot(rich));

		// Sanity bounds (not tight — just guard against pathological blow-ups).
		assertTrue("normal payload should be well under 64KB", normalBytes < 64 * 1024);
		assertTrue("large-bank payload should be under 512KB", bigBytes < 512 * 1024);
		assertTrue("large bank should be bigger than normal", bigBytes > normalBytes);
	}
}
