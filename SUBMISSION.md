# ScapePath — Plugin Hub submission preparation

> Prepared, **not submitted.** Nothing here has been pushed or committed. Fill the
> `<...>` placeholders once the repository is public and a release commit exists.

## What Plugin Hub requires (verified against the current plugin-hub README)

1. **Public GitHub repository** for the plugin source.
2. **BSD 2-Clause "Simplified" License** at repo root — present (`LICENSE`).
3. **`runelite-plugin.properties`** at repo root — present (see below).
4. **`build=standard`** — set. This means the Plugin Hub replaces `build.gradle` /
   `settings.gradle` at build time, so our dev-only `run`/`shadowJar` tasks are irrelevant
   to the hub build. `runeLiteVersion = 'latest.release'` is set (required).
5. **Optional `icon.png` at repo root, ≤ 48×72 px** — present (`icon.png`, 48×48).
6. **README** — present.
7. **No non-transitive dependencies** → **no Gradle dependency-verification metadata
   needed.** (Only `net.runelite:client` compileOnly + `junit`/`jshell` test deps, all
   transitive of runelite-client or test-scoped. Nothing ships in the plugin jar.)
8. The submission itself is a one-line marker file added to the **plugin-hub** repo via PR.

## Current manifest (`runelite-plugin.properties`)

```
displayName=ScapePath
author=Magic Muck
description=Read-only OSRS account progression companion: view your skills, quests, achievement diaries, inventory, equipment, bank, and wealth, and optionally sync them to your ScapePath account
tags=account,progression,skills,quests,diary,bank,sync,scapepath
version=
plugins=com.scapepath.plugin.ScapePathPlugin
build=standard
```

## The Plugin Hub PR marker file (prepared — do NOT commit yet)

Submission is a fork of `runelite/plugin-hub` adding one file `plugins/scapepath`:

```
repository=<https clone url of the public ScapePath plugin repo, ending in .git>
commit=<full 40-character commit hash of the release commit>
```

Both values require: (a) the plugin repo pushed public, and (b) a chosen release commit.
Neither exists yet — see the checklist. Example shape only:

```
repository=https://github.com/<user>/scapepath-runelite.git
commit=0000000000000000000000000000000000000000
```

## Pre-submission checklist

- [x] `./gradlew clean build` passes; 83 tests, 0 failures/0 errors.
- [x] BSD 2-Clause `LICENSE` at root.
- [x] `runelite-plugin.properties` complete; `build=standard`; `latest.release`.
- [x] `icon.png` (48×48) at root, no copyrighted artwork.
- [x] README describes features and accurately documents opt-in HTTPS sync vs local view.
- [x] No reflection / native / exec / extra dependencies; networking uses RuneLite's
      bundled OkHttp, HTTPS-only to the fixed ScapePath origin (re-audited this session).
- [x] `author=Magic Muck` set in `runelite-plugin.properties`.
- [x] Opt-in HTTPS transport implemented (link/sync/disconnect).
- [ ] Create a **public** GitHub repo and push (requires explicit authorization; this dir
      is not yet a git repository).
- [ ] Pick the release commit hash and fill the marker file above.
- [ ] Fork `runelite/plugin-hub`, add `plugins/scapepath`, open PR, pass CI + reviewer checks.

## Notes for the reviewer (draft PR description)

> ScapePath is a passive, read-only OSRS account-progression companion. It reads the local
> player's own account state via public RuneLite APIs and displays it, plus a local preview
> of the exact payload that is synced. Syncing to ScapePath is **opt-in over HTTPS**: no
> network request is made until the user connects with a one-time code, and the only
> destination is the fixed ScapePath production origin (`https://www.scapepath.com`).
> It collects **no credentials/passwords/cookies**, performs **no automation or input**,
> and uses **no reflection or native code**. Only the local player's own account state is
> read (never other players' data). Network I/O uses RuneLite's bundled OkHttp off the
> client thread. See `PAYLOAD.md` for the full data disclosure.
