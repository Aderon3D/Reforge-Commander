# Field Layout: Zone Presets & Window UX (issue #88)

Design research for the two biggest playtest complaints:

1. **No zone presets** — especially with more than 2 players, battlefield zones were
   left unplaced or poorly arranged.
2. **Clunky, unreliable window resize/move** — dragging field windows to resize and
   rearrange felt janky.

Goal (per request): take Blender's resizable/custom-window layout as the reference,
keep the good Forge bits (draggable name tag, docking look), and deliver a document
plus the best additive implementation.

---

## 1. Where Forge stands today (verified in code)

Forge already ships a Blender-*like* docking framework — we are not writing one; we
are fixing its gaps. All of it lives in `forge-gui-desktop/.../forge/gui/framework/`
and is **upstream-pristine** (see `docs/upstream-sync.md`), so we can only extend, not edit.

- **`DragCell`** — one layout rectangle; holds tabbed docs; has borders
  (`getBorderRight()`, `getBorderBottom()`).
- **`DragTab`** — the draggable name-tag (players like this). Paints selected/inactive
  rounded pills; adds `SRearrangingUtil.getRearrangeClickEvent()` + motion listener.
- **`SRearrangingUtil`** — move: on press computes a drop zone (BODY/RIGHT/TOP/
  BOTTOM/LEFT) from cursor vs each cell's bounds, shows a bounds preview overlay,
  then splits/merges cells on release; grows neighbours to fill holes; persists.
- **`SResizingUtil`** — resize: 5px edge panels change cursor to E/N-resize; drag moves
  the whole group sharing the edge; min-sizes W=100/H=50; pixel-perfect rounding on
  end. Uses `MouseUtil.lockCursor()` (the janky part).
- **`SLayoutIO`** — persistence. Per-screen XML (`<layout>` → `<cell x/y/w/h>` →
  `<doc>NAME</doc>`). Match = `ForgeConstants.MATCH_LAYOUT_FILE`. `revertLayout()`
  reloads from the user file; `save()` debounced 100ms.

What's actually missing vs Blender, matching the player complaints:
- **Presets / workspaces** — Blender's *Workspaces* = named layouts. Forge has exactly
  one static default (`match.xml`, hard-coded for **2 players**: only `FIELD_0` and
  `FIELD_1` are placed in the XML). At 3-4 players the extra `FIELD_n` docs fall into a
  stump int cell — the direct root cause of complaint #1.
- **Player-count canonical arrangements** — the "resizing the 4+ board" table in
  `docs/development.md` (2×2 grid, triangle, etc.) is designed but never implemented.
- **Cancel affordance** — Blender cancels rearrange/join on `Esc`/`RMB`. No `Esc`
  abort in the current drag/resize.
- **Smoothness** — `MouseUtil.lockCursor()` + 5px edge-only resize zones are the janky
  parts; there is no hit-testing with a double-headed arrow that always shows, no
  `Ctrl`-snap / shift-move-aligned, and no snapping.

---

## 2. Design targets (Blender-inspired)

- **Area = a rectangle you can always see.** Non-overlapping; never modal-blocked.
- **Resize**: hover border → double-arrow cursor (blinking), LMB drag. `Ctrl` snaps to
  convenient sizes; `Shift` drags aligned borders together.
- **Dock**: corner "cross" cursor. Drag inward to split (direction by drag), drag into
  another cell to join, drag into a cell's center to replace, drag outward to a new window.
  `Esc`/`RMB` cancels.
- **Workspaces = presets**: named, one-click, save-current-as.
- **Maximize/Restore** (`Ctrl+Space`) — focus a single field, restore later.

Only the *preset* pillar and the *player-count arrangement* are additive today
(they only read/write `match.user` XML, which is exactly the `SLayoutIO` contract).
The dock mechanics (split/join/replace, thick hit-zones, snap, Esc-cancel, maximize)
require **editing upstream framework classes** — out of additive scope, shaped as an
upstream-facing RUG proposal in §5.

---

## 3. The additive implementation (shipped here)

Cannot touch `gui/framework/*` or `VField`/`CMatchUI`/`VMatchUI`. So this slice
ships **data-level presets** — the highest-impact, lowest-risk fix for complaint #1:

- **`forge.gui.reforge.ReforgeMatchLayoutPresets`** (new, `REFORGE COMMANDER EXTENSION`):
  - `layoutFor(int players)` → canonical `match.xml` XML with **N battlefield zones
    stacked** across the field band, every `FIELD_0..FIELD_{n-1}` placed, one `HAND_0`
    band below, and the stock left/right rails unchanged. Generalizes the 2-player
    default exactly (n=2 reproduces the proportions of the hard-coded file).
  - `apply(players)` writes the generated XML to `MATCH_LAYOUT_FILE.userPrefLoc`
    (it carries `serial=""`, equal to the default file's missing serial, so
    `SLayoutIO.loadLayout` never the "older template" reset prompt).
  - `restoreDefault()` deletes the user file → stock 2-player layout returns.
- **Menu**: `CSubmenuPlayCommander.getMenus()` (already an `IMenuProvider`, previously
  returned an empty list) now exposes **Battlefield Reformate → "2 Players … 8 Players"
  and "Restore Default Layout"**. Additive edit to a Reforge-owned class.

Because the pre-match `update()` installs this menu provider whenever the Commander
lobby is shown, the player picks the layout for their pod size *before* the match; the
match then loads it on start. `VSubmenuPlayCommander` wraps a shared
`VSubmenuConstructed` lobby — no dependencies on it.

Limits (honest):
- It's a **pre-match** chooser; live hot-swap mid-match needs a match-screen hook
  (the screen is upstream) — Phase 2 below.
- It applies a *fixed canonical arrangement* per player count; per-cell drag/dock
  already lets the player refine inside a match (the framework handles that).
- `VMatchUI`/`VField` (fixed 85%-width proportions) are unchanged; the Per-Player
  *card scaling* idea in `dev.md` 9c still needs the upstream layout change.

---

## 4. Roadmap notes (docstatus)

- Marker `// doc:12a PARTIAL` on `ReforgeMatchLayoutPresets.layoutFor`.
- Dev-doc row added matching that status.
- Priority: **P2** (confidence UX/lo-valued); real 4-player scaling remains 9c.

---

## 5. Upstream proposal (when we can touch the framework)

Full Blender-grade UX belongs in the shared classes. Written here so the plan is
captured even though today's hardware is additive-only:

1. **Thicker, always-live resize hit-zones** (≥ 8px, no per-cell border toggle), cursor
   set on `mouseMoved` not just `mouseEntered`.
2. **Replace `MouseUtil.lockCursor()`** with progressive resistance + min-total–size
   clamping so borders never fall off-screen (the "unreliable" fix).
3. **`Ctrl`-snap pass + `Shift`-linked-edge** during drag in `SResizingUtil.resizeX/Y`.
4. **`Esc`/`RMB` cancel** in `SRearrangingUtil` (a cancel mouse listener added in
   `rearrange()` start; abort restores prior rough bounds).
5. **Workspace presets UI**: a small "View ▸ Layout ▸ Preset…" popup in the match bar
   (or `CSubmenuPlayCommandCenter` if ever extended) wired to `SLayoutIO.saveWindowLayout()`
   snapshot + the generator here.
6. **Per-player autosize** in `VField`/`SResizingUtil.resizeWindow()`: derive
   `FIELD_*` cell h/w from the number of board docs so 3-4 players get a 2-3 →
   triangle / 2×2 automatically — closes dev.md 9c.

Each is small and localized; none changes file formats or network state. File them as
upstream sync *extension* so the sync stays conflict-free (additive classes only).

---

## 6. Files touched (additive audit)

| File | Change | Status |
|------|--------|--------|
| `forge-gui-desktop/.../gui/reforge/ReforgeMatchLayoutPresets.java` | new (write schedule in exact SLayoutIO schema) | done |
| `forge-gui-desktop/.../home/playcommander/CSubmenuPlayCommander.java getMenus()` | added "Battlefield Layout" menu | done |
| `docs/development.md` | §12 + priority row | done |
| upstream `gui/framework/*`, `VField`, `CMatchUI`, `VMatchUI`, `match.xml` | **UNTOUCHED** | by policy |

---

## 7. Verification

- XML produced by `layoutFor(n)` round-trips through `SLayoutIO.readLayout`'s schema
  (checked: cell attrs `x/y/w/h`, `<doc>` per doc id).
- `serial=""` equals the default file's resolved serial → no reset prompt.
- No local `mvn` in this repo; CI (`test-build`, both JDK matrix) + CodeQL are the
  gate. A tiny TestNG case can be added under
  `forge-gui-desktop/src/test/.../ReforgeMatchLayoutPresetsTest.java` asserting
  `layoutFor(n)` contains exactly n `FIELD_<0..n-1>` docs and valid Cell entries — the
  "smallest thing that fails if the logic breaks."