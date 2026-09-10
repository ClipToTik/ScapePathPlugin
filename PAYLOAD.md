# ScapePath payload contract & privacy disclosure

**Status: opt-in HTTPS sync.** This document describes the JSON payload the plugin sends
to the ScapePath ingestion API (`POST /api/runelite/sync`) **only after** you explicitly
connect your ScapePath account with a one-time code. Until you connect, nothing is
transmitted; the payload below is also shown locally in the plugin panel as a preview. The
same payload is produced by the deterministic serializer whether or not you sync.

## Schema

- `schemaVersion` (int) — currently **1**. A stable contract between plugin and server;
  incremented on any breaking change to shape or field semantics.
- The JSON uses stable semantic field names only. It contains **no Java class names**,
  no RuneLite implementation objects, and no reflection-derived structure. It is produced
  by a hand-written deterministic serializer (`transport/SnapshotPayloadSerializer`).

### Top-level shape

```json
{
  "schemaVersion": 1,
  "pluginVersion": "0.2.1",
  "timestamp": "2026-08-29T21:40:31Z",
  "account": { "rsn": "Zezima" },
  "sections": {
    "identity":           { "freshness": "COMPLETE",    "collectedAt": "…", "data": { … } },
    "skills":             { "freshness": "COMPLETE",    "collectedAt": "…", "data": { … } },
    "quests":             { "freshness": "COMPLETE",    "collectedAt": "…", "data": { … } },
    "achievementDiaries": { "freshness": "COMPLETE",    "collectedAt": "…", "data": { … } },
    "combatAchievements": { "freshness": "COMPLETE",    "collectedAt": "…", "data": { … } },
    "inventory":          { "freshness": "COMPLETE",    "collectedAt": "…", "data": { … } },
    "equipment":          { "freshness": "COMPLETE",    "collectedAt": "…", "data": { … } },
    "bank":               { "freshness": "UNAVAILABLE", "collectedAt": "…", "data": null   },
    "wealth":             { "freshness": "COMPLETE",    "collectedAt": "…", "data": { … } },
    "collectionLog":      { "freshness": "UNAVAILABLE", "collectedAt": "…", "data": null   }
  }
}
```

### Achievement diaries (V2 — additive, task-level)

`schemaVersion` stays **1**: the V2 change is purely additive, so V1 consumers keep working.

The `achievementDiaries.data` shape is unchanged at the top level — `completedTiers`,
`totalTiers`, and a `tiers` array of `{ region, tier, completed }`. **New in V2:** a tier may
additionally carry an optional `tasks` object of `{ "<stableTaskId>": true|false }`, giving
exact per-task completion:

```json
{
  "region": "Karamja",
  "tier": "Easy",
  "completed": false,
  "tasks": {
    "ATJUN_EASY_BANANA": true,
    "ATJUN_EASY_GOLD": false
  }
}
```

- **Task identity.** The task id is the RuneLite `VarbitID` **constant name** (e.g.
  `ATJUN_EASY_BANANA`), not a human-facing description. It is stable across game updates
  and independent of wiki wording. The website should key on this id, never on a display name.
- **Where `tasks` appears.** RuneLite's `gameval` only enumerates individual task varbits for
  **Karamja** (Easy 10, Medium 19, Hard 10). Every other region — and Karamja Elite — exposes
  only tier completion, so their tier objects **omit the `tasks` key entirely**. Omission
  means "no reliable per-task state exposed"; it is never faked as all-false. `tasks` is thus
  present today only on Karamja Easy/Medium/Hard.
- **Reconstruction.** For a tier with `tasks`, the website can reconstruct completed/remaining
  tasks and cross-check the tier `completed` flag. For a tier without `tasks`, only tier-level
  completion is known — exactly the V1 guarantee.

### Combat achievements (per-task, tier aggregates)

`schemaVersion` stays **1** — additive. The `combatAchievements.data` reports player **state**
only; ScapePath owns all static knowledge (which task belongs to which tier/boss, its point
value, and the full task universe used to compute "missing").

```json
{
  "points": 435,
  "completedCount": 33,
  "enumeratedTasks": 398,
  "tiers": [
    { "tier": "EASY",        "completed": 33, "status": 2, "threshold": 33 },
    { "tier": "MEDIUM",      "completed": 0,  "status": 0, "threshold": 115 },
    { "tier": "HARD",        "completed": 0,  "status": 0, "threshold": 304 },
    { "tier": "ELITE",       "completed": 0,  "status": 0, "threshold": 820 },
    { "tier": "MASTER",      "completed": 0,  "status": 0, "threshold": 1465 },
    { "tier": "GRANDMASTER", "completed": 0,  "status": 0, "threshold": 2005 }
  ],
  "completedTaskIds": [
    "CA_TASK_ARMADYL_KILLCOUNT_1_COMPLETED",
    "CA_TASK_BANDOS_KILLCOUNT_1_COMPLETED"
  ]
}
```

- **Task identity.** Unlike the Collection Log, RuneLite's `gameval` exposes a stable, named
  per-task completion varbit for **every** Combat Achievement task
  (`CA_TASK_<ACTIVITY>_<TYPE>_<N>_COMPLETED`). The task id is that `VarbitID` **constant name**;
  the website must key on it, never on a display string. These are account-stored varbits (not
  interface-gated) and reliable whenever logged in.
- **`completedTaskIds`.** Only completed tasks are listed, so the array scales with progress
  (empty for a new account, up to `enumeratedTasks` for a maxed one). The website derives the
  **missing** set by subtracting this from its own canonical task list.
- **Tier aggregates.** `completed`, `status` (raw game value — ScapePath interprets it), and
  `threshold` (points needed for the tier) come straight from account varbits, so "almost
  complete this tier" reasoning works without the website's task→tier map being perfectly in
  sync with the current game update. `points` is total CA points; `completedCount` is the sum
  of the per-tier counts (game-authoritative).
- **`enumeratedTasks`** is the number of tasks *this plugin build* knows about (398), not the
  authoritative game total — it lets the website detect drift when a game update adds tasks the
  plugin has not yet been rebuilt against.
- **Availability.** `COMPLETE` whenever logged in (a genuine "0 completed" on a fresh account
  is real state); `UNAVAILABLE`, `data: null` when logged out.

### Collection log (foundation — progress counts)

The `collectionLog` section reports the account-stored Collection Log **slot counts** using
stable RuneLite `VarPlayerID`s — overall and per tab. It carries **no per-item detail** and
no item metadata (names, prices, sources are ScapePath's responsibility).

```json
{
  "obtained": 573,
  "total": 1500,
  "tabs": [
    { "id": "BOSSES",    "obtained": 100, "total": 400 },
    { "id": "RAIDS",     "obtained":  30, "total": 120 },
    { "id": "CLUES",     "obtained": 150, "total": 300 },
    { "id": "MINIGAMES", "obtained":  40, "total": 180 },
    { "id": "OTHER",     "obtained": 253, "total": 500 }
  ]
}
```

- **Availability.** These counts are `0` until the game syncs the account's Collection Log
  data. When the overall total is non-positive the section is `freshness: UNAVAILABLE`,
  `data: null` — "not yet known", kept distinct from a genuine "0 obtained".
- **Deferred.** Full per-item obtained/missing enumeration is **interface-gated** in RuneLite
  (only reliably populated after the player opens the Collection Log) and `gameval` names only
  a small fraction of items. It is intentionally **not** implemented here; the counts are the
  authoritative, compact foundation. Per-item capture is a documented follow-up.

### Determinism & null rules

- Field order, section order, enum names (`COMPLETE`/`STALE`/`UNAVAILABLE`/…) and ISO-8601
  UTC timestamps are fixed → the same snapshot serializes byte-for-byte identically.
- Nullable scalars (`account.rsn`, `identity.rsn`, `identity.accountType`,
  `wealth.bankGp`, `wealth.estimatedBankValue`) serialize as JSON `null`.
- Empty collections serialize as `[]`.
- **A section's `data` is `null` when the section is `UNAVAILABLE`** — never an empty
  object. This keeps three bank states distinct:
  - never opened → `freshness: UNAVAILABLE`, `data: null`
  - opened (even empty) → `freshness: COMPLETE`, `data: { items: [], … }`
  - opened then closed → `freshness: STALE`, `data: { … }`, original `collectedAt`

## DATA WE SEND (account state only, after you connect)

RSN · account hash · account type · world · per-skill level & XP, total level/XP, combat
level · every quest (stable id, name, state) + quest points + counts · every achievement
diary region/tier completion + counts, plus exact per-task completion where RuneLite exposes
it (Karamja) keyed by stable task id · Combat Achievement total points, per-tier
completed-task counts/status/threshold, and the stable ids of every completed task ·
Collection Log slot counts (overall + per tab) ·
inventory items (id/qty/slot) · equipment (id/qty/slot) · bank items + unique count + bank
coins + estimated value + freshness + timestamp + source · GP on hand, bank GP, estimated
bank value · plugin version, schema version, snapshot timestamp, per-section
freshness/collectedAt.

## DATA WE NEVER SEND

RuneScape password · Google password · RuneLite credentials · session cookies · Jagex
auth tokens · Google OAuth credentials · local machine username · local filesystem paths ·
environment variables · private keys · unrelated system information. None of these are
read by any collector, so none can appear in the payload. A unit test asserts the payload
never contains `password`, `cookie`, `token`, `oauth`, `session`, `credential`, `jagex`,
`email`, or a filesystem path.

## Account hash — what it is and why

`accountHash` is RuneLite's stable per-account identifier
(`OAuthApi.getAccountHash()`; `-1` when logged out). It is **not a credential** and grants
no access to the account. Its purpose is to let ScapePath key progression to the correct
account even if the RSN is changed. The ingestion API needs a stable account key;
`accountHash` fills that role. If the server later prefers to key on RSN alone or a
server-issued id, `accountHash` can be dropped from the contract (a `schemaVersion` bump)
— it is included deliberately, not merely because RuneLite exposes it.

It is serialized as a **JSON string** (e.g. `"6291812345678901234"`), never a bare number,
so a 64-bit value above 2^53 survives the round-trip without precision loss. The value the
server pins when redeeming the link code and the value it sees on every sync are then
identical; emitting it as a number would round both differently and every sync would be
rejected as an account mismatch. `-1` (logged out) is emitted as `null`.

## Measured payload sizes

Deterministic serializer, UTF-8 (from `PayloadSizeTest`):

| Scenario | Size |
|---|---|
| Normal account (all skills, 211 quests, 48 diary tiers **+ 39 Karamja tasks**, CA tier scaffold + 0 tasks done, small inv/equip, no bank, collectionLog UNAVAILABLE) | ~20.0 KB (20,431 bytes) |
| Full account + large bank (~800 distinct stacks) + **all 398 CA tasks complete** + Karamja tasks + collectionLog counts | ~67.7 KB (69,365 bytes) |

Quests (211) and diaries dominate the baseline; bank scales with distinct stacks, and the
`combatAchievements.completedTaskIds` array scales with CA progress. The CA section adds only
the small tier scaffold (~0.6 KB) to a new account and ~17 KB at the theoretical maximum (all
398 task ids). Since only completed ids are sent, a typical account is far below that. No
optimization needed at this stage.
