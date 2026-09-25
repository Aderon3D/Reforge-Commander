# Upstream Sync Process

> **Personal project.** This sync exists so my fork can keep pulling card scripts and rules fixes from
> upstream while I keep my own changes merge-friendly. It is not a community maintainer workflow and
> carries no guarantees. See the [README](../README.md) for the project disclaimer.

## How sync works

A daily GitHub Action (`.github/workflows/sync-upstream.yml`) pulls from `Card-Forge/forge` at 04:00 UTC and opens a PR when the merge is green. The required checks (Java 17/21 tests, CodeQL, doc-status, platform-parity) must pass before merge.

**Never** add a remote literally named `upstream` to any workflow — it makes `gh pr create` misattribute the repo.

## Upstream-merge preference (soft constraint)

New Reforge code should extend upstream classes rather than modifying them; every Reforge class carries a
`REFORGE COMMANDER EXTENSION` header. This is a *preference*, not a prohibition: a small, clearly-marked direct
edit to an upstream file is acceptable when it's the minimal correct change and a workaround would cost more than
the resulting merge friction. The table below tracks which upstream files currently carry direct Reforge edits so a
future upstream refactor of any of them is known to risk a merge conflict.

## Touched upstream files (sync-conflict risk)

These upstream files are modified directly by Reforge (not extended). A future upstream refactor of any of these will produce a merge conflict. Re-verify this list periodically against `git diff upstream/master..master --name-only`.

> **Why direct edits exist:** these are pre-existing modifications made before the preference was adopted; extending
> the upstream class was not possible for the integration points (e.g. the zone entry path in `TokenEffectBase`, the
> static-eval loop in `GameAction`). Each is minimized and isolated. New work should still prefer a
> `REFORGE COMMANDER EXTENSION` class; refactor any table row into an extension class opportunistically.

| Upstream file | Why touched | Reforge items |
|---------------|-------------|---------------|
| `forge-game/src/main/java/forge/game/card/TokenEffectBase.java` | Token burst suppression wrapper + `tryStackToken` wiring | 1a, 1b |
| `forge-game/src/main/java/forge/game/zone/PlayerZone.java` | `setSuppressViewUpdate` to batch view refreshes | 1b |
| `forge-game/src/main/java/forge/game/zone/PlayerZoneBattlefield.java` | `tryStackToken`, `expandStacks`, `getCardsUnexpanded` | 1b, 1g, 1c |
| `forge-game/src/main/java/forge/game/zone/Zone.java` | Stack-aware card count/iterator | 1b |
| `forge-game/src/main/java/forge/game/Game.java` | `forEachCardInGameUnexpanded` for unexpanded battlefield iteration | 1c |
| `forge-game/src/main/java/forge/game/GameAction.java` | `checkStaticAbilities` uses unexpanded battlefield | 1c |
| `forge-gui/src/main/java/forge/gui/FSkin.java` | `applyCommanderDarkTheme` | 6a |
| `forge-gui-desktop/src/main/java/forge/screens/home/VHomeUI.java` | `reforge.commander.mode` menu gating | commander-mode |
| `forge-gui-desktop/src/main/java/forge/screens/home/EMenuGroup.java` | Reorder PLAY before GAUNTLET | 2c (sync-conflict risk) |
| `forge-gui-desktop/src/test/java/forge/FCollectionTest.java` | Test adaptation | build (sync-conflict risk) |
| `forge-gui-desktop/src/main/java/forge/gui/GuiDesktop.java` | Commander-mode init | commander-mode |
| `forge-gui-desktop/pom.xml` | FlatLaf dependency | 6a |
| Root `pom.xml` | Java 17 enforcement | build |

**New Reforge-only files** (no sync risk):
- `StackedTokenCard.java`, `ReforgeCommanderApp.java`, `VSubmenuPlayCommander.java`, `CSubmenuPlayCommander.java`, `ReforgeTheme.java`

## Sync Log

Notable upstream changes worth tracking (may affect fork behavior or require follow-up).

### 2026-08-25 — Sync PR #139 (4 content-absent commits)

| Commit | File(s) | Notes |
|--------|---------|-------|
| `Fix NPE (#11683)` | (AI) | Bugfix — likely safe, no fork impact expected |
| `Edition updates: PSPL,PZ2,SLD,SLZ,YMKM` | card data | Card script additions — auto-merged |
| `Don't create token copies that just die (#11690)` | (game engine) | Token creation logic change — may interact with `TokenEffectBase` (item 1a/1b) |
| `Add Japanese translations (#11666)` | language files | Localization — auto-merged |

### 2026-09-02 — Fast-forward (68 files, major upstream drift)

Key upstream changes landed in this window — review for fork-relevant impacts:

- **`CardFactoryUtil.java` (47 lines)** — Ascend mechanic refactored; `AscendEffect.java` deleted, logic inlined. No fork edits to AscendEffect, so safe.
- **`Card.java` (47 lines)** — Significant refactor. Our fork doesn't edit Card.java directly (risk: low).
- **`ComputerUtilMana.java` (141 lines)** — AI mana management rewrite. Fork doesn't edit this (risk: low).
- **`Graphics.java` (36 lines)** — Rendering changes in mobile GUI. Fork doesn't edit (risk: low).
- **`RewardActor.java` (102 lines)** — Major adventure-mode UI overhaul. Fork doesn't edit (risk: low).
- **`AiCardMemory.java` (25 lines)** — AI memory refactor. Fork doesn't edit (risk: low).
- **Storage lands** (7 card files) — Text/ability updates. Auto-merged.
- **`PS_HOB1.pzl`** — New puzzle mode file. Hidden by default per product vision.

**Fork collision risk:** `EMenuGroup.java` (already in table above) and `FCollectionTest.java` (not tracked) conflicted during merge. Add `FCollectionTest.java` to the table:

| Upstream file | Why touched | Reforge items |
|---------------|-------------|---------------|
| `forge-gui-desktop/src/test/java/forge/FCollectionTest.java` | Test adaptation | build |