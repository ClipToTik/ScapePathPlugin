# ScapePath (RuneLite plugin)

ScapePath is a passive OSRS account-progression companion. It reads your locally
available account state (skills, quests, achievement diaries, inventory, equipment, bank,
and wealth) and shows it in a side panel, along with a preview of the exact data it
synchronizes to your ScapePath account when you choose to connect.

## How it works

- **Read-only account view.** The plugin reads your account state through public RuneLite
  APIs, builds a normalized snapshot, and displays it — plus a preview of the exact JSON
  it would sync — inside RuneLite. It never automates gameplay, sends input, or reads any
  credential.
- **Opt-in HTTPS sync to ScapePath.** In the plugin's side panel you can connect your
  ScapePath account:
  1. On the ScapePath website Profile, generate a one-time **connection code**.
  2. Enter that code in the plugin panel and press **Connect**.
  3. The plugin exchanges the code for a ScapePath **device token** over HTTPS
     (`POST /api/runelite/link`) and stores only that token locally (never shown again).
  4. While connected, your snapshot is synced over HTTPS (`POST /api/runelite/sync`,
     `Authorization: Bearer <device token>`) — automatically (throttled to at most once
     every few minutes) and via **Sync now**.
  5. **Disconnect** from the panel (or from the website) revokes the connection.
- **Nothing is sent until you connect**, only your own account state is ever sent, and the
  device token is a revocable ScapePath credential — never a RuneScape/Jagex/Google/
  RuneLite credential, password, cookie, or session token. If the network is unavailable
  the plugin keeps working locally and retries later; it never blocks the client. See
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
3. Toggle it on, then open the **ScapePath** side panel (navigation button).
4. Under **ScapePath connection**, paste a connection code from the website Profile and
   press **Connect**, then use **Sync now** / **Disconnect**. The config panel's
   **Account Sync → Automatic sync** toggle controls periodic background syncing.

### B. Side-load a jar into a stock RuneLite (developer mode)

1. `./gradlew shadowJar` → `build/libs/scapepath-<version>-all.jar` (or use the
   plain built classes).
2. Launch RuneLite with `--developer-mode` and use the developer plugin side-loader
   pointed at this project's build output.

## Verify (what to expect)

- Builds cleanly on JDK 11.
- Plugin appears as **ScapePath** in the config panel and starts/stops without errors.
- No stack traces on startup/shutdown.
- Until you connect, no outbound network requests are made by this plugin.
- Account state is read locally and shown in the panel. Once you connect with a one-time
  code, your own account snapshot is synced to ScapePath over HTTPS (and only then).

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
the ScapePath side-panel. It is transmitted only after you connect (see "How it works"),
and only ever your own account's state.

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
│   ├── ConnectionState       DISCONNECTED / CONNECTING / CONNECTED / SYNCING / OFFLINE / ERROR
│   ├── ConnectionManager     link/sync/disconnect lifecycle; dispatches all network off-thread
│   ├── TokenStore            device-token storage seam (testable)
│   └── ConfigTokenStore      TokenStore backed by RuneLite ConfigManager (hidden key)
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
│   ├── SnapshotSectionType   14 categories defined; 8 collected today (Identity, Skills,
│   │                         Quests, Diaries, Inventory, Equipment, Bank, Wealth)
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
│   ├── PayloadPreview        local "what would be sent" description
│   ├── ScapePathTransport    HTTPS transport seam (link/sync/disconnect)
│   └── OkHttpScapePathTransport  the ONE networking class (RuneLite-bundled OkHttp, HTTPS)
└── ui
    └── ScapePathPanel        side panel: connection controls + snapshot view + payload preview
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
session cookies, and never automates gameplay or sends inputs. Syncing is **opt-in**
(nothing is sent until you connect with a one-time code), read-only, HTTPS-only to
`https://www.scapepath.com`, authenticated with a revocable ScapePath device token, and
sends only your own account's state. [PAYLOAD.md](PAYLOAD.md) discloses exactly what is
transmitted. Uses no reflection, native code, `Runtime.exec`, or extra dependencies; the
device token is stored under a hidden config key and never logged or shown.

## License

BSD 2-Clause. See [LICENSE](LICENSE).
