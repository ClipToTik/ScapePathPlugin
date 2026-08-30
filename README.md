# ScapePath (RuneLite plugin)

ScapePath is a passive OSRS account-progression companion. It reads your locally
available account state (skills, quests, achievement diaries, inventory, equipment, bank,
and wealth) and shows it in a side panel, along with a local preview of the exact data a
future version would synchronize to your ScapePath account.

## Current behavior vs planned

- **Current build (this repository): completely local.** The plugin reads read-only
  account state through public RuneLite APIs, builds a normalized snapshot, and displays
  it — plus a "what would be sent" JSON preview — inside RuneLite. **It makes no network
  requests and transmits nothing.** The **Connect** / **Enable account synchronization**
  controls are preferences only; they do not authenticate or send anything in this build.
- **Planned (future version): opt-in HTTPS sync.** A later version will let you connect
  your ScapePath account and synchronize this snapshot over HTTPS. Syncing will be
  strictly opt-in, read-only, HTTPS-only, and authenticated with a ScapePath token you
  enter yourself. Nothing will be transmitted until you explicitly connect. See
  [PAYLOAD.md](PAYLOAD.md) for the exact contract and privacy disclosure.

It never collects RuneScape/Jagex, Google, or RuneLite credentials, passwords, or session
cookies; never automates gameplay or sends inputs; uses no reflection, native code, or
extra dependencies.

## Requirements

- JDK 11
- The Gradle wrapper (`./gradlew`) — no separate Gradle install needed.

## Build

```bash
./gradlew build
```

Runs `compileJava`, `compileTestJava`, and the JUnit test suite. Produces
`build/libs/`.

Useful tasks:

```bash
./gradlew test          # unit tests only
./gradlew run           # launch RuneLite with ScapePath side-loaded (dev mode)
./gradlew shadowJar     # fat jar (build/libs/scapepath-<version>-all.jar)
```

## Load / test locally in RuneLite

There are two supported ways to run the plugin against a real client:

### A. `./gradlew run` (recommended for development)

`src/test/java/com/scapepath/plugin/ScapePathPluginTest.java` side-loads the plugin
via `ExternalPluginManager.loadBuiltin(...)` and launches RuneLite with
`--developer-mode --debug`. This pulls the RuneLite client from `repo.runelite.net`
at `latest.release`.

```bash
./gradlew run
```

Then in the client:

1. Open the **Configuration** (wrench) panel.
2. Search **ScapePath** — it appears in the plugin list.
3. Toggle it on; open its settings to see the **Connection** and **Account Sync**
   sections.
4. Toggle **Connect ScapePath** on/off and watch **Status** update
   (Not connected ⇄ Connected). No network activity occurs.

### B. Side-load a jar into a stock RuneLite (developer mode)

1. `./gradlew shadowJar` → `build/libs/scapepath-<version>-all.jar` (or use the
   plain built classes).
2. Launch RuneLite with `--developer-mode` and use the developer plugin side-loader
   pointed at this project's build output.

## Verify (what to expect)

- Builds cleanly on JDK 11.
- Plugin appears as **ScapePath** in the config panel and starts/stops without errors.
- No stack traces on startup/shutdown.
- No outbound network requests are made by this plugin.
- Account state is read locally and shown in the panel; nothing is transmitted.

## What is collected (local only)

- **Identity:** account hash (stable id, not a credential), RSN, account type
  (via `VarbitID.IRONMAN`), world.
- **Skills:** every skill in `Skill.values()` (level + XP), total level, total XP, and
  combat level (via `Experience.getCombatLevel`).
- **Quests:** per-quest `{id, name, state}` over `Quest.values()` (state via
  `Quest.getState`), completed count, total count, and quest points (`VarPlayerID.QP`).
- **Achievement Diaries:** every region/tier `{region, tier, completed}` (12 regions ×
  4 tiers = 48), completed-tier count, total. Completion read from per-tier varbits;
  **Karamja** Easy/Medium/Hard use the `ATJUN_*_DONE` varbits (complete at value 2).
- **Inventory:** occupied slots as `{id, quantity, slot}`.
- **Equipment:** worn items as `{id, quantity, equipment-slot}`.
- **Bank (interface-gated):** stacks `{id, quantity, slot}`, unique-item count, bank
  coins, and an **estimated** value from RuneLite's local price cache. Only available
  once the bank is opened; see the freshness model below.
- **Wealth:** GP on hand (inventory coins), bank GP and estimated bank value (both
  `null` until the bank is synced — never conflated with zero).

Data is rebuilt on login, item-container changes, and bank open/close, and displayed in
the ScapePath side-panel. It is never transmitted.

### Bank freshness

Three states are kept distinct and **"unknown" is never shown as "empty"**:

| State | When | Section |
|---|---|---|
| Not synced | bank never opened this session | `UNAVAILABLE`, no `BankData` |
| Current | bank open right now | `COMPLETE`, `BankData` + timestamp |
| Stale | opened earlier, now closed | `STALE`, cached `BankData` + timestamp |

The cache is reset on logout so one account's bank is never shown for another.

## Project layout

```
com.scapepath.plugin
├── ScapePath                 constants (config group, version)
├── ScapePathPlugin           lifecycle + orchestration (events, client-thread refresh)
├── ScapePathConfig           config panel (Connection, Account Sync)
├── game
│   ├── GameStateAccessor         read-only seam over RuneLite Client (+ containers, prices)
│   ├── RuneLiteGameStateAccessor live impl (client-thread reads; injects ItemManager)
│   ├── OsrsAccountType           normalized account type (from account-type varbit)
│   ├── BankTracker               stateful interface-gated bank cache + freshness
│   └── DiaryDefinitions          auditable region/tier → varbit table (Karamja special)
├── connection
│   ├── ConnectionState       DISCONNECTED / CONNECTING / CONNECTED / ERROR
│   └── ConnectionManager     local-only connect/disconnect (no network)
├── collector
│   ├── AccountDataCollector  contract for one snapshot section
│   ├── CollectorContext      read-only inputs (incl. GameStateAccessor)
│   ├── CollectorRegistry     assembles registered collectors → snapshot
│   ├── IdentityCollector     IDENTITY section
│   ├── SkillsCollector       SKILLS section
│   ├── QuestCollector        QUESTS section (state + quest points)
│   ├── AchievementDiaryCollector  ACHIEVEMENT_DIARIES section
│   ├── InventoryCollector    INVENTORY section
│   ├── EquipmentCollector    EQUIPMENT section
│   ├── BankCollector         BANK section (interface-gated freshness)
│   └── WealthCollector       WEALTH section (GP on hand, bank GP, est. value)
├── snapshot
│   ├── SnapshotSectionType   14 data categories (Skills, Bank, …)
│   ├── SourceFreshness       COMPLETE / PARTIAL / STALE / UNAVAILABLE
│   ├── SectionData           marker for typed payloads
│   ├── CollectedSection      one section + freshness metadata
│   ├── AccountSnapshot       normalized, immutable account snapshot model
│   ├── SnapshotService       holds latest snapshot, rebuilds on demand
│   └── data
│       ├── IdentityData      typed IDENTITY payload
│       ├── SkillData         one skill (name/level/xp)
│       ├── SkillsData        all skills + totals + combat level
│       ├── QuestSnapshot     one quest (id/name/state)
│       ├── QuestsData        all quests + completed/total + quest points
│       ├── DiaryTierSnapshot one region/tier (region/tier/completed)
│       ├── AchievementDiaryData  all tiers + completed/total
│       ├── ItemSnapshot      one stack (id/quantity/slot)
│       ├── InventoryData     occupied inventory slots
│       ├── EquipmentData     worn items
│       ├── BankData          bank stacks + coins + estimated value + source
│       └── WealthData        GP on hand, bank GP, estimated bank value
├── transport
│   ├── JsonWriter            deterministic, reflection-free JSON builder
│   ├── SnapshotPayloadSerializer  AccountSnapshot → versioned JSON (no HTTP)
│   └── PayloadPreview        local "what would be sent" description
└── ui
    └── ScapePathPanel        local diagnostic side-panel + payload preview (no transmission)
```

## Live updates

Updates are event-driven, never a busy poll. `GameStateChanged` (login), `StatChanged`,
`WorldChanged`, `ItemContainerChanged` (inventory/equipment/bank), `WidgetLoaded`/
`WidgetClosed` (bank interface `BANKMAIN`), and a **targeted** `VarbitChanged` (only the
quest-points varp or a known diary varbit) mark the snapshot dirty; `GameTick` rebuilds
once per tick at most, coalescing bursts (e.g. the ~23 `StatChanged` events at login)
into a single rebuild. A `BANK` container change caches the bank into `BankTracker` with
a timestamp; opening/closing the bank flips its current/stale flag; logout resets it.
All rebuilds read the client on the client thread.

## Privacy & compliance

ScapePath never collects RuneScape / Jagex / Google / RuneLite credentials, passwords, or
session cookies, never automates gameplay or sends inputs, and this build transmits
nothing. Future syncing will be opt-in, read-only, HTTPS-only, authenticated with a
player-entered ScapePath token, and will disclose exactly what is transmitted.

## License

BSD 2-Clause. See [LICENSE](LICENSE).
