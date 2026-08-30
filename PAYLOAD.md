# ScapePath payload contract & privacy disclosure

**Status: local-only build — nothing is transmitted.** This document describes the JSON
payload that a **future, opt-in** version of the plugin would send to a ScapePath HTTPS
ingestion API. The current build contains **no network code** and sends nothing; the
payload below is only built locally and shown in the plugin panel as a preview.

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
  "pluginVersion": "0.1.0-SNAPSHOT",
  "timestamp": "2026-08-29T21:40:31Z",
  "account": { "rsn": "Zezima" },
  "sections": {
    "identity":           { "freshness": "COMPLETE",    "collectedAt": "…", "data": { … } },
    "skills":             { "freshness": "COMPLETE",    "collectedAt": "…", "data": { … } },
    "quests":             { "freshness": "COMPLETE",    "collectedAt": "…", "data": { … } },
    "achievementDiaries": { "freshness": "COMPLETE",    "collectedAt": "…", "data": { … } },
    "inventory":          { "freshness": "COMPLETE",    "collectedAt": "…", "data": { … } },
    "equipment":          { "freshness": "COMPLETE",    "collectedAt": "…", "data": { … } },
    "bank":               { "freshness": "UNAVAILABLE", "collectedAt": "…", "data": null   },
    "wealth":             { "freshness": "COMPLETE",    "collectedAt": "…", "data": { … } }
  }
}
```

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

## DATA WE INTEND TO SEND (account state only)

RSN · account hash · account type · world · per-skill level & XP, total level/XP, combat
level · every quest (stable id, name, state) + quest points + counts · every achievement
diary region/tier completion + counts · inventory items (id/qty/slot) · equipment
(id/qty/slot) · bank items + unique count + bank coins + estimated value + freshness +
timestamp + source · GP on hand, bank GP, estimated bank value · plugin version, schema
version, snapshot timestamp, per-section freshness/collectedAt.

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
account even if the RSN is changed. The future ingestion API needs a stable account key;
`accountHash` fills that role. If the server later prefers to key on RSN alone or a
server-issued id, `accountHash` can be dropped from the contract (a `schemaVersion` bump)
— it is included deliberately, not merely because RuneLite exposes it.

## Measured payload sizes

Deterministic serializer, UTF-8 (from `PayloadSizeTest`):

| Scenario | Size |
|---|---|
| Normal account (all skills, 211 quests, 48 diary tiers, small inv/equip, no bank) | ~18.3 KB (18,745 bytes) |
| Full account + large bank (~800 distinct stacks) | ~49.1 KB (50,250 bytes) |

Quests (211) and diaries (48) dominate the baseline; bank scales with distinct stacks.
Sizes are modest — no optimization needed at this stage.
