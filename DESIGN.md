# Honeycrisp Client: design spec

This is the one design spec for the in-game UI (core `ui/`, drawn only with `dev.honeycrisp.ui.draw`) and for the
SwiftUI launcher. It follows Apple's Human Interface Guidelines and the current macOS material language
(Liquid Glass, macOS 26 and later), with Lunar Client's information architecture: right-shift mod menu,
category sidebar, mod cards and a drag-and-snap HUD editor.

Honeycrisp Client is an independent project. It is not an Apple product, and nothing in the UI should suggest
that it is. The Apple logo and Apple product names never appear in the UI. SF Pro and SF Symbols are drawn at
runtime from the user's own macOS install through CoreText/AppKit, and are never bundled or exported as images.
The SF Symbols license also forbids using symbols in an app icon or logo, so the Honeycrisp Client wordmark and app
icon must be original artwork.

---

## 0. Units and conventions

- **u (UI unit)**: every dimension in this document is in u. One u is one macOS point multiplied by the user's
  UI-scale setting. In GUI-scaled px, `1u = backingScale / guiScale × uiScale`. At guiScale 2 on a Retina
  display (backingScale 2) with uiScale 1.0, **1u = 1 GUI px**, so the numbers below can be used as written.
  Menus and screens compute `k = backingScale / guiScale × uiScale` once per frame and multiply by it. HUD
  modules don't: they scale with Minecraft's guiScale like the vanilla HUD, times each module's own scale.
- **Hairline**: one framebuffer pixel, which is `1 / (guiScale × backingScale)` GUI px. Separators and edge
  strokes marked "hairline" use this width.
- **Colors** are ARGB hex `0xAARRGGBB`, non-premultiplied, matching `Renderer.fill`.
- **Shadow offsets**: `Draw.shadow` takes no offset parameter, so "y+N" means shifting the rect down by N before
  the call.
- **Gradients**: there's no gradient primitive. Where this spec calls for a fade (scroll edges, color picker),
  draw N stacked 2u strips with stepped alpha, or upload a cached 1×N texture once and stretch it with
  `Renderer.texture`.
- **Font presets** are named `Font.X` as in ARCHITECTURE.md. Sizes are in u.

---

## 1. Principles

1. **Content first, chrome floats.** The game world (or the menu panorama) is the content layer. Menus are a
   glass layer floating above it, with the world still visible. Never draw glass on glass: inner groups use
   plain tints, not a second blurred panel.
2. **One accent, used sparingly.** The macOS accent color (`Draw.accent()`) marks selection, the "on" state and
   the single primary action on a screen. Everything else is greyscale and translucency.
3. **Concentric geometry.** Each nested shape's radius is its container's radius minus the padding between
   them. Large standalone actions are capsules. Dense controls are rounded rects.
4. **Type and symbols match.** Every SF Symbol is drawn at the weight of the text next to it and centered on
   its cap height. Text is left-aligned, except the main-menu wordmark and centered button labels.
5. **Physical, interruptible motion.** Every animation is a spring that can be retargeted mid-flight. Nothing
   blocks input while it animates. Durations stay short: feedback under 150 ms, transitions under 350 ms.
6. **The HUD stays out of the way.** HUD modules are small, low-contrast containers holding high-contrast text,
   with no animation during gameplay apart from value changes.
7. **Lunar's layout, macOS's details.** Users should find things where Lunar puts them (right shift, sidebar
   categories, card grid, HUD editor). Control behavior should match macOS: Esc cancels, Return confirms, and
   ⌘F / typing goes to search.

---

## 2. Design tokens

### 2.1 Color: dark (primary appearance)

| Token | ARGB | Use |
|---|---|---|
| `bg.base` | `0xFF0E0F12` | Behind everything when no world or panorama is available |
| `bg.scrim` | `0x59000000` | 35% dimming layer behind modal sheets and the in-game mod menu |
| `bg.scrim.menu` | `0x40000000` | Over the blurred main-menu panorama |
| `label.primary` | `0xF2FFFFFF` | Titles and body text |
| `label.secondary` | `0x99EBEBF5` | Descriptions and values |
| `label.tertiary` | `0x4DEBEBF5` | Placeholders and disabled text |
| `label.quaternary` | `0x29EBEBF5` | Watermarks. Never put this on glass. |
| `fill.primary` | `0x5C787880` | Switch track (off), slider track |
| `fill.secondary` | `0x52787880` | Button and key-cap backgrounds |
| `fill.tertiary` | `0x3D767680` | Text fields, segmented track |
| `fill.quaternary` | `0x2E747480` | Row hover |
| `separator` | `0x33FFFFFF` | Hairline separators on glass |
| `separator.opaque` | `0xFF38383A` | Separators when "Reduce transparency" is on |
| `stroke.highlight` | `0x26FFFFFF` | Glass edge, full perimeter |
| `stroke.specular` | `0x4DFFFFFF` | Glass edge, top third only |
| `system.red` | `0xFFFF453A` | Destructive actions, errors, low durability |
| `system.orange` | `0xFFFF9F0A` | Warnings, mid durability, ping 80–149 ms |
| `system.yellow` | `0xFFFFD60A` | Accent preset |
| `system.green` | `0xFF32D74B` | Success, good ping, full durability |
| `system.blue` | `0xFF0A84FF` | Fallback accent |
| `system.purple` | `0xFFBF5AF2` | Accent preset |
| `system.pink` | `0xFFFF375F` | Accent preset |
| `system.graphite` | `0xFF8C8C8C` | Accent preset |

### 2.2 Color: light

| Token | ARGB |
|---|---|
| `bg.base` | `0xFFF2F2F7` |
| `bg.scrim` | `0x33000000` |
| `bg.scrim.menu` | `0x26000000` |
| `label.primary` | `0xF2000000` |
| `label.secondary` | `0x993C3C43` |
| `label.tertiary` | `0x4D3C3C43` |
| `label.quaternary` | `0x2E3C3C43` |
| `fill.primary` | `0x33787880` |
| `fill.secondary` | `0x29787880` |
| `fill.tertiary` | `0x1F767680` |
| `fill.quaternary` | `0x14747480` |
| `separator` | `0x1F000000` |
| `separator.opaque` | `0xFFC6C6C8` |
| `stroke.highlight` | `0x99FFFFFF` |
| `stroke.specular` | `0xE6FFFFFF` |
| `system.red` / `orange` / `yellow` / `green` / `blue` / `purple` / `pink` / `graphite` | `0xFFFF3B30` / `0xFFFF9500` / `0xFFFFCC00` / `0xFF28CD41` / `0xFF007AFF` / `0xFFAF52DE` / `0xFFFF2D55` / `0xFF8C8C8C` |

### 2.3 Accent usage

- **Resolving the accent**: if Settings → Accent is "System", use `Draw.accent()`. Otherwise use the chosen
  preset from the `system.*` tokens. Read the system accent once per frame; it's cheap and stays live.
- **Derived accent values**:
  - `accent.fill`: the accent at 100%. Used for the switch "on" state, primary buttons and slider fill.
  - `accent.tint`: the accent at 22% alpha. Used for the selected sidebar row and selected tile backgrounds.
  - `accent.ring`: the accent at 50% alpha. Used for focus rings.
- **Text on accent**: always `0xFFFFFFFF`. If the accent is yellow or graphite, use `0xE6000000` instead
  (luminance > 0.6).
- **Where the accent appears**: selection, "on" states, the primary button, focus rings, snap guides, the text
  caret and links. Nowhere else, and never as a large background fill.

### 2.4 Glass material recipes

`Draw.glass(r, x, y, w, h, radius, GlassStyle)` implements these. Each recipe is an ordered set of steps:
outer shadows, then backdrop blur (if `Renderer.blur` returns true), then the tint fill, then the edge strokes.
The **No-blur tint** column applies when blur is unsupported or disabled. It is more opaque so the panel stays
legible without blur.

| Level | Used for | Blur radius | Tint (with blur) | No-blur tint | Edge | Outer shadow |
|---|---|---|---|---|---|---|
| **L1 Window** (regular, thick) | Mod menu window, sheets, context menus, toasts, popovers | 24u | dark `0xB31C1C1E`, light `0xB8F6F6F8` | dark `0xEB1C1C1E`, light `0xF2F6F6F8` | `stroke.highlight` 1u on the full perimeter, plus `stroke.specular` 1u clipped (`pushClip`) to the top 33% of the height. Plus an outer hairline (`0x80000000` dark / `0x1F000000` light) to separate the panel from bright content. | Two layers: y+16, blur 48, `0x66000000` (light: `0x2E000000`); and y+1, blur 3, `0x40000000` (light: `0x1A000000`) |
| **L2 Sidebar / group** (inside L1, no second blur) | Floating sidebar, grouped form boxes, mod cards | none | dark `0x14FFFFFF`, light `0x99FFFFFF` | same | `stroke.highlight` 1u. Top-third specular at half the L1 alpha. | Cards only: y+2, blur 8, `0x33000000` (light `0x14000000`) |
| **L3 Control** | Button bodies, switch knobs, slider thumbs, segmented thumb, search capsule | none | dark `0x2EFFFFFF`, light `0xD9FFFFFF` | same | 1u `0x33FFFFFF` (light `0xFFFFFFFF`), plus top-third specular `0x59FFFFFF` | y+1, blur 4, `0x40000000` (light `0x26000000`) |
| **Clear** (main menu only, over the panorama) | Main-menu buttons, account chip | 12u | `0x2EFFFFFF` over a `bg.scrim.menu` screen dim | `0x59101114` | Same as L1 | y+8, blur 24, `0x40000000` |
| **HUD** | In-game HUD modules (see 4.4) | none by default (blur is opt-in per module) | `0x66000000` | `0x66000000` | none, or hairline `0x1AFFFFFF` when "Outline" is on | none |

Inner shadows: there's no primitive for these. For "pressed" depth, draw a second stroke 1u inset, clipped to
the top 33%, at `0x26000000`. That is the only inner-shadow effect in the system.

**Reduce transparency** (setting, plus the system value later if the native bridge exposes it): every level
uses its no-blur tint with alpha raised to `0xF5`, specular strokes are off, and separators switch to
`separator.opaque`.

**Increase contrast**: edges become 1u `label.tertiary`, and secondary labels use `label.primary` at 80%.

### 2.5 Corner radii

| Token | Radius | Used for |
|---|---|---|
| `r.xs` | 4u | Tick marks, small chips, keystroke key insets |
| `r.sm` | 6u | Small buttons (24u), key-bind buttons, tooltips |
| `r.md` | 8u | Regular buttons (28u), text fields, pop-up buttons, inner sidebar rows |
| `r.lg` | 12u | Grouped form boxes, context menus, icon wells (fallback) |
| `r.xl` | 16u | Floating sidebar, popovers, toasts (multi-line) |
| `r.2xl` | 20u | Mod cards |
| `r.window` | 24u | Mod menu window, sheets |
| `r.capsule` | h/2 | Large/x-large buttons, search field, switches, slider track and thumb, HUD pills, toolbar clusters |

**Concentric rule**: for any element whose corner sits within `2 × outerRadius` of its container's corner,
`inner = max(outer − padding, 4u)`. If the result is below 4u, increase the padding instead of going lower.
Elements that don't sit near a container corner use their fixed token. Worked examples:

- Window (24) − 8 inset = sidebar (16)
- Sidebar (16) − 8 = sidebar row (8)
- Card (20) − 12 = icon well (8)
- Context menu (12) − 5 = menu item highlight (7)
- Sheet (24) − 16 = footer button radius 8 (`r.md`)

### 2.6 Spacing (4u grid, with a 2u half-step for fine alignment)

| Token | Value | Typical use |
|---|---|---|
| `s.0.5` | 2u | Icon-to-label nudges, segmented thumb inset, switch knob inset |
| `s.1` | 4u | Intra-control gaps |
| `s.2` | 8u | Sidebar inset, row gap, HUD module padding |
| `s.3` | 12u | Card padding, grid gutter, list row horizontal padding |
| `s.4` | 16u | Window content padding, sheet padding |
| `s.5` | 20u | Space above a section header |
| `s.6` | 24u | Space between groups |
| `s.8` | 32u | Main-menu button stack offset |
| `s.10` | 40u | — |
| `s.12` | 48u | Main-menu wordmark-to-buttons gap |

Hit targets are at least 24u × 24u, even when the visual element is smaller. Expand the hit rect, not the
drawing.

### 2.7 Typography (SF Pro via CoreText)

Sizes follow the macOS text styles. Weights are bumped one step for titles, matching the bolder macOS 26
typography.

| Preset | Size (u) | Weight | Line height | Use |
|---|---|---|---|---|
| `LARGE_TITLE` | 26 | 700 | 32 | Screen titles (Settings, Cosmetics) |
| `TITLE` | 22 | 700 | 26 | Sheet titles |
| `TITLE2` | 17 | 600 | 22 | Toolbar title (current category) |
| `TITLE3` | 15 | 600 | 20 | Group titles, toast titles |
| `HEADLINE` | 13 | 700 | 16 | Mod card name, section headers |
| `BODY` | 13 | 400 | 16 | Row labels, sidebar items (selected rows use 600) |
| `CALLOUT` | 12 | 400 | 15 | Button labels (use 500), segmented labels |
| `SUBHEADLINE` | 11 | 400 | 14 | Card descriptions, tooltips |
| `FOOTNOTE` | 10 | 400 | 13 | Metadata, version strings |
| `CAPTION` | 10 | 400 | 13 | Badges |
| `CAPTION2` | 10 | 500 | 13 | Key-cap legends |
| `HUD` | 9 | 600 | 11 | HUD module text (GUI px at module scale 1.0) |
| `HUD_BOLD` | 9 | 700 | 11 | HUD values and emphasized numbers |
| Wordmark | 48 | 800 | 56 | Main-menu "Honeycrisp" (`Font.of(48, 800)`) |

- **Numbers that change every frame** (FPS, CPS, coordinates, ping, slider values) use tabular figures so they
  don't jitter. Have libhoneycrisp enable `kNumberSpacingType / kMonospacedNumbersSelector` for the HUD
  presets, or fall back to `Font.mono` at the same size and weight minus 100.
- **Text shadows**: none in menus. HUD text shadows are covered in 4.4.
- **Truncation**: tail-truncate with "…", never wrap labels. Descriptions wrap to at most 2 lines, then
  truncate.

### 2.8 Motion

All animation runs through one interruptible spring integrator per animated value: `value`, `velocity` and
`target`. Changing the target mid-flight keeps the current velocity.

**Integrator.** Run once per frame, using semi-implicit Euler. Clamp `dtTotal` to 1/20 s and use fixed
substeps of `h = 1/240` s.

```
omega = 2π / response          // natural frequency
k = omega²;  c = 2 · dampingFraction · omega
repeat (dtTotal / h) times:
    a = −k · (value − target) − c · velocity
    velocity += a · h
    value    += velocity · h
settled when |value − target| < 0.001 · range and |velocity| < 0.01 · range  → snap value = target
```

**Closed form** for fire-and-forget animations that won't be retargeted. Progress `p(t)` runs from 0 to 1.

- Critically damped (ζ = 1): `p(t) = 1 − e^(−ω t) · (1 + ω t)`
- Under-damped (ζ < 1): with `ωd = ω √(1−ζ²)`,
  `p(t) = 1 − e^(−ζ ω t) · (cos(ωd t) + (ζ ω / ωd) · sin(ωd t))`

**Presets**

| Name | response (s) | dampingFraction | Use |
|---|---|---|---|
| `spring.quick` | 0.20 | 1.0 | Hover fills, row highlights, focus rings |
| `spring.smooth` | 0.40 | 1.0 | Window, sheet and screen transitions, sidebar selection |
| `spring.snappy` | 0.30 | 0.85 | Switch knob, segmented thumb, card lift, toast enter |
| `spring.bouncy` | 0.45 | 0.70 | HUD module drop after dragging, cosmetic equip confirmation |

**Plain fades** use a time-based ease-out, `p = 1 − (1 − t/d)³`. Durations:

- Tooltip in: 0.12 s. Tooltip out: 0.08 s.
- Context menu out: 0.10 s. Context menu in: 0 s (macOS menus appear instantly; only alpha 0→1 over 0.06 s).
- Scroller fade out: 0.30 s, after 0.8 s idle.

**Transitions**

| Transition | Behavior |
|---|---|
| Open mod menu | Scrim alpha 0 → 1 (`smooth`). Window scale 0.96 → 1.0, alpha 0 → 1, y +12 → 0 (`smooth`). Close is the reverse, at 0.7× response. |
| Sheet | Same as opening the mod menu, layered over the window. The window behind dims with an extra `0x26000000` scrim. |
| Sidebar selection | Highlight slides to the new row's y (`snappy`). Content cross-fades over 0.15 s, with y +6 → 0. |
| Toast | x +24 → 0, alpha 0 → 1 (`snappy`). Exit: alpha → 0 over 0.2 s. |

**Reduce motion** (Settings → Appearance; mirrors the system value once the bridge exposes it): replace every
scale and translate with a 0.15 s cross-fade. Springs on switches and sliders stay, because they are direct
manipulation.

### 2.9 Interaction states

Applied on top of each component's base. All transitions use `spring.quick`.

| State | Dark | Light |
|---|---|---|
| Hover | Overlay `0x0FFFFFFF` (+6% white) on the control's shape | Overlay `0x0A000000` |
| Pressed | Overlay `0x1FFFFFFF`, and content scales 0.97 around the center | Overlay `0x14000000`, scale 0.97 |
| Selected | `accent.tint` fill, icon in accent, label weight +100 | Same |
| Disabled | Whole control drawn at 40% alpha. No hover or press. | Same |
| Keyboard focus | 3u ring of `accent.ring`, drawn 1u outside the shape, radius = shape radius + 2u. Only after a Tab/arrow key press, never on mouse click. | Same |
| Dragging | Lifted: shadow y+8, blur 24, `0x59000000`. Scale 1.03. | Shadow `0x33000000` |

---

## 3. Components

Dimensions are W × H in u. "Glyph" means `Draw.icon`. Each glyph is centered in its box and drawn at the size
given, which equals the symbol's point size.

### 3.1 Sidebar item
- **Row**: 184 × 30 inside a 200u sidebar with 8u inset. Radius 8 (concentric with sidebar 16 − 8). Rows are
  2u apart.
- **Contents**: glyph 16 box at x+10, centered vertically, colored `label.secondary`. Label `BODY` at x+34 in
  `label.primary`. Optional count badge at the right edge in `CAPTION`, `label.tertiary`.
- **States**:
  - Hover: `fill.quaternary`.
  - Selected: `accent.tint` fill. Glyph switches to the `.fill` variant (if one exists) in `accent.fill`.
    Label at weight 600.
  - Pressed: standard pressed overlay.
- **Group header** (optional): `SUBHEADLINE` 600 in `label.tertiary`, at x+10, with 12u above and 4u below.

### 3.2 Segmented control
- **Track**: height 28, capsule, `fill.tertiary`. Width is the sum of the segments. Each segment is at least
  64u wide, or label width + 24.
- **Thumb**: L3 glass capsule inset 2u, height 24. It slides between segments with `spring.snappy`.
- **Labels**: `CALLOUT` 500 in `label.secondary`. The selected label is 600 in `label.primary`. Segments may
  use a glyph (14 box) instead of text, at medium weight.
- **Dividers**: hairline, 12u tall, `separator`. Hidden next to the thumb.

### 3.3 Toggle switch
- **Regular**: track 38 × 22, capsule. Knob 18 × 18 inset 2u, color `0xFFFFFFFF`, shadow y+1, blur 3,
  `0x40000000`.
- **Mini**: track 26 × 15, knob 11 × 11. Used for subordinate settings within a group.
- **Track colors**: off uses `fill.primary`. On uses `accent.fill`. The color cross-fades with the knob
  position.
- **Knob position**: animated with `spring.snappy` from x = 2 to x = W − knobW − 2.
- **Pressed**: the knob stretches to 24 × 18 (mini: 15 × 11) toward the travel direction and switches to L3
  glass (translucent). The press is the moment it "becomes glass".
- **Interaction**: clicking anywhere on the track or its row toggles. Dragging the knob past the midpoint
  toggles. Space toggles while focused.
- **Placement**: switches belong only in list rows and on mod cards. Elsewhere, use a toggle-style button.

### 3.4 Slider
- **Track**: height 4, capsule, `fill.primary`. The filled part (min → value) is `accent.fill`.
- **Thumb**: 22 × 14 capsule, white, shadow like the switch knob. Pressed: scales to 1.15 and switches to L3
  glass.
- **Hit area**: the track is 24u tall.
- **Ticks** (optional, for ≤ 11 discrete values): 2 × 6 rects in `label.tertiary`, 3u below the track. The
  value snaps to ticks.
- **Value label**: right of the slider, `CALLOUT` with tabular figures, min width 40u, right-aligned, in
  `label.secondary`. Format: integers as-is, percentages with "%", scales as "1.25×".
- **Keyboard**: ←/→ moves one step. ⇧ moves ten steps.
- **Precision drag**: holding ⌥ while dragging moves at 0.25× speed.

### 3.5 Stepper
- **Shape**: two 22 × 22 buttons joined into a 44 × 22 rounded rect (r 6) on `fill.secondary`, with a
  hairline divider between them.
- **Glyphs**: `minus` and `plus`, 10 box, semibold.
- **Value field**: sits to the left (see 3.6). Width 48, centered tabular figures.
- **Auto-repeat**: after 0.4 s held, then every 0.08 s.

### 3.6 Text field
- **Shape**: height 28, radius 8. Fill `fill.tertiary`, hairline `separator` stroke. Padding 8u.
- **Text**: `BODY` in `label.primary`. Placeholder in `label.tertiary`.
- **Focused**: stroke becomes 1u `accent.fill`, plus the focus ring.
- **Caret**: 1.5 × 16, `accent.fill`. Blinks on a 1.06 s cycle (0.53 s on, 0.53 s off). It stays solid for 0.5 s
  after each keystroke.
- **Selection**: `accent.tint` at 35% alpha.
- **Shortcuts**: ⌘A, ⌘C, ⌘V, ⌘X, ⌥←/→ (word), ⌘←/→ (line).

### 3.7 Key-bind button
- **Shape**: 24 high, min width 64, radius 6. Fill `fill.secondary`, with a 1u bottom inner stroke
  `0x33000000` for a key-cap look.
- **Legend**: `CAPTION2` 600 in `label.primary`, from `Platform.keyName`. Modifiers render as glyphs:
  `command`, `option`, `control`, `shift`.
- **Listening**: label changes to "Press a key…" in `label.secondary`. Stroke is 1.5u `accent.fill` pulsing
  between 40% and 100% alpha over a 1.0 s sine.
  - Esc cancels.
  - ⌫ sets "None".
  - A mouse button can be bound ("Mouse 4").
- **Conflicts**: the legend turns `system.orange`, and a tooltip names the other binding.

### 3.8 Buttons

| Size | Height | Shape | Font | Horizontal padding | Glyph |
|---|---|---|---|---|---|
| Small | 24 | r 6 | `CALLOUT` 500 | 10 | 12 box |
| Regular | 28 | r 8 | `CALLOUT` 500 | 14 | 14 box |
| Large | 36 | capsule | `BODY` 600 | 20 | 16 box |
| X-Large | 48 | capsule | `TITLE3` 600 | 28 | 20 box |

**Styles**

| Style | Fill | Label | Hover | Pressed |
|---|---|---|---|---|
| Primary | `accent.fill` + L3 edge strokes | white | +8% white overlay | darkened `0x26000000`, scale 0.97 |
| Secondary | L3 glass (`fill.secondary` inside a window) | `label.primary` | standard | standard |
| Destructive | L3 glass / `fill.secondary` | `system.red` | standard | standard |
| Destructive (confirm) | `system.red` fill | white | standard | standard |
| Plain | none | `accent.fill` (links) or `label.secondary` (icon buttons) | `fill.quaternary` in the button shape | standard |
| Circular icon | 28 × 28 L3 glass circle | glyph 14 box, semibold | standard | standard |

**Usage**
- One primary button per view. It goes last (rightmost) in a button row, with 8u gaps.
- Return triggers the primary button. Esc triggers Cancel.
- Glyph-plus-text buttons put the glyph first, with a 6u gap. Don't put a glyph-only button in the same group
  as a text button.

### 3.9 Search field
- **Shape**: 32 high, capsule, L3 glass. Width 240 (mod menu toolbar).
- **Contents**:
  - `magnifyingglass` glyph, 14 box at x+10, `label.secondary`.
  - Text at x+30, `BODY`. Placeholder "Search" in `label.tertiary`.
- **Clear button**: when there's text, show `xmark.circle.fill` (14 box, `label.tertiary`) at the right. It
  clears the field.
- **Focus**: ⌘F focuses the field. Typing any printable character while no other field is focused also
  focuses it and inserts that character.
- **Filtering**: results filter live (no submit). Matches are on name, description and category. Matching
  characters in a card name are drawn at weight +200.

### 3.10 Mod card

```
┌──────────────────────────────────────┐  216 × 120, r 20, L2 glass + card shadow
│ ┌──────┐                    ╭──◯╮    │  icon well 36×36 r 8 @ (12,12)
│ │ glyph│                    ╰───╯    │  switch 38×22 @ (W−12−38, 19)
│ └──────┘                             │
│ Keystrokes                           │  HEADLINE @ y 58
│ Shows WASD, jump and mouse buttons   │  SUBHEADLINE, label.secondary, ≤2 lines @ y 76
│ with CPS.                       (⚙)  │  gear: circular icon button 24×24 @ (W−12−24, H−12−24)
└──────────────────────────────────────┘
```

- **Icon well**:
  - Enabled: `accent.fill` well, white glyph (20 box, semibold, monochrome).
  - Disabled: `fill.secondary` well, glyph in `label.secondary`.
  - The well color cross-fades with `spring.snappy`.
- **Clicks**:
  - Clicking the card body toggles the module, like a Control Center tile. The switch mirrors the state.
  - The gear (`gearshape`, 14 box) opens the module's settings sheet.
  - Right-click opens a context menu: Settings…, Reset to Defaults, Edit Position (HUD modules only).
- **Hover**: border becomes 1u `stroke.specular`. Card scales 1.0 → 1.015 and the shadow grows to y+6, blur 16
  (`spring.snappy`).
- **Badges**:
  - "NEW": a `CAPTION` capsule, 16 high, `accent.tint` fill with accent text, top-left inside the well row at
    x 56.
  - "BETA": the same capsule with an orange tint.
- **Not available on this version** (e.g. Old Animations on 1.7.10 where it is native): card at 40% alpha. The
  switch is disabled and a tooltip explains why.

### 3.11 List rows and grouped forms
- **Group box**: radius 12, L2 tint, full width minus 32u.
- **Rows**: 36 high. Padding 12u left and right.
- **Row contents**:
  - Optional glyph (16 box) at x+12, label at x+38.
  - Without a glyph, the label sits at x+12.
  - Label: `BODY`, `label.primary`.
  - Secondary text: `SUBHEADLINE`, `label.secondary`, on a second line. Row height becomes 48.
- **Trailing control**: right-aligned, vertically centered (switch, pop-up, slider ≥ 160u wide, key-bind,
  stepper, or `chevron.right` 10 box in `label.tertiary` for navigation).
- **Separators**: hairline `separator`, inset to start at the label's x. None after the last row.
- **Hover**: `fill.quaternary` across the whole row (clipped to the group radius at the ends) for navigation
  rows only.

### 3.12 Section headers
- **Title**: `HEADLINE` 700 in `label.primary`, aligned with the group box's left edge + 4u. 20u above, 8u below.
- **Footer** (optional, below a group): `SUBHEADLINE` in `label.secondary`, 6u below the group, wrapping at the
  group width.

### 3.13 Tooltip
- **Timing**: appears 0.7 s after the pointer rests. Once one tooltip is showing, moving to another control
  shows its tooltip with no delay for the next 1.5 s.
- **Shape**: L1 glass with radius 6 (no blur needed; use the no-blur tint). Padding 8 × 5. Max width 280.
- **Text**: `SUBHEADLINE` in `label.primary`.
- **Position**: 8u below the pointer, flipped to stay inside the screen with a 4u margin. Fades in 0.12 s and
  hides as soon as the pointer moves off the control.

### 3.14 Toast / notification
- **Placement**: top-right, 16u from the screen edges, 300 wide. Height 44 when the toast has one line
  (capsule); otherwise a radius 16 rect that grows with content. L1 glass.
- **Contents**:
  - Glyph 20 box at x+14, colored by kind: info `accent.fill`, success `system.green`, warning
    `system.orange`, error `system.red`.
  - Title `HEADLINE`. Optional body `SUBHEADLINE` in `label.secondary`.
- **Lifetime**: auto-dismisses after 4 s (8 s for errors). Hovering pauses the timer. Clicking dismisses it.
- **Stacking**: at most 3 visible. Newer toasts go on top, gap 8. Older ones slide down with `spring.snappy`.
- **In-game**: shown only on menus or when the HUD is visible. Never shown while the mod menu is open; queue
  them instead.

### 3.15 Context menu
- **Panel**: L1 glass, radius 12, padding 5. Width is max(180, widest item + 48).
- **Items**: 24 high, highlight radius 7.
  - Glyph 14 box at x+10 (optional). Label `BODY` at x+32 (x+12 if no item in the menu has a glyph).
  - Shortcut right-aligned in `label.tertiary` (e.g. "⌘R").
  - Submenu items show `chevron.right` (10 box).
- **Highlight**: `accent.fill` fill. Label and glyph turn white. Destructive items use `system.red` text, and
  their highlight fill is `system.red`.
- **Separator**: hairline `separator`, inset 10u, with 5u above and below.
- **Opening**:
  - Opens at the pointer and flips at the screen edges.
  - Clicking outside or pressing Esc closes it.
  - Arrow keys move the highlight and Return activates.
  - A press–drag–release over an item selects that item.

### 3.16 Color picker (simple)
- **Popover**: L1 glass, radius 16, padding 12, width 248. Attached to a 44 × 24 color well button (r 6, with
  the current color filling an inset 3u at r 3, and a checkerboard under alpha).
- **Layout, top to bottom**:
  1. **Swatch row(s)**: 12 presets, as 2 rows × 6 circles of 24u with 12u gaps. Presets: the 8 `system.*`
     colors, white `0xFFFFFFFF`, light grey `0xFFAEAEB2`, dark grey `0xFF3A3A3C`, and black `0xFF000000`. The
     selected swatch has a 2u ring of `label.primary` 2u outside it.
  2. **Saturation/brightness square**: 224 × 120, r 8. Drawn as a cached texture, rebuilt only when the hue
     changes. The handle is a 12u circle, white 2u stroke plus shadow.
  3. **Hue slider**: as in 3.4, but the track is 10u tall and shows a hue-strip texture. The thumb is a white
     ring 14u.
  4. **Alpha slider**: shown only when the setting allows alpha. Checkerboard track with a color ramp.
  5. **Hex field**: 3.6 at width 100, `Font.mono` 12/500, formatted `#AARRGGBB` (or `#RRGGBB` when alpha is
     fixed). The ⟲ "Use accent" plain button sits to its right.
- Changes apply live. Esc reverts to the value from when the popover opened.

### 3.17 Pop-up button (dropdown)
- **Shape**: like a regular secondary button (28 high, r 8). Label left-aligned.
  `chevron.up.chevron.down` (10 box, semibold, `label.secondary`) at x = W − 18. Min width 120.
- **Opening**: opens a context menu (3.15) positioned so the selected item sits exactly over the button. That
  item shows `checkmark` (12 box) in its glyph column.
- **Pull-down variant** (actions rather than a value): `chevron.down`. The menu opens below the button.

### 3.18 Scroll views (overlay scrollers)
- **Scroller**: no permanent track. The thumb is 6u wide (expanding to 10u while hovered or dragged, with the
  track at `fill.quaternary`), capsule, `0x80FFFFFF` dark / `0x80000000` light. Inset 3u from the right edge,
  min length 32u.
- **Visibility**: shows on scroll or when the pointer enters the rightmost 16u. Fades out 0.8 s after the last
  scroll.
- **Wheel and trackpad scrolling**:
  - Target delta = wheel delta × 40u (pixel deltas pass through).
  - The position follows with `spring.quick`.
  - Trackpad inertia: velocity × 0.92 each 1/60 s step, stopping below 0.5u/frame.
- **Rubber band**: past the edges, `displayed = (1 − 1 / (overshoot × 0.55 / D + 1)) × D`, where D is the
  viewport height. On release it springs back with `spring.smooth`.
- **Scroll edge effect** (hard style): when content is scrolled under a pinned toolbar or header, draw a 16u
  fade below that bar, from the bar's tint to transparent, plus a hairline `separator`. Use one per pane, and
  only where something floats above the content.
- **Clipping**: use `Renderer.pushClip` to the viewport.

### 3.19 Modal sheet
- **Panel**: centered, width min(560u, 80% of the screen), height fits content up to 85% of the screen, then
  scrolls. L1 glass, radius 24. Over `bg.scrim`.
- **Header**: 52u. Title `TITLE3` 600 at x+16, centered vertically.
  - Close button: circular icon button (`xmark`, 28u) at the top-right, 12u inset. Radius stays circular.
  - Optional leading glyph well: 32u, r 8.
- **Body**: padding 16u, grouped form (3.11).
- **Footer**: 56u, hairline top separator. Buttons are right-aligned regular buttons, primary last. An optional
  destructive plain "Reset to Defaults" button sits on the left.
- **Behavior**: enter/exit per 2.8. Esc / the close button closes. Clicking the scrim closes the sheet only if
  there are no unsaved edits; settings apply live, so normally there are none.

---

## 4. Screens

Sizes are in u, and as fractions of the scaled screen (S = screen width × height in u).

### 4.1 Main menu (replaces the vanilla title when `Hooks.replaceMainMenu()`)

```
┌──────────────────────────────────────────────────────────────────────────┐
│ Minecraft 1.8.9 · Honeycrisp 0.1                      ( ◉ Steve  ⌄ )     │  account chip: Clear glass capsule 32h, top-right 16u inset
│                                                                          │
│                                                                          │
│                            H O N E Y C R I S P                           │  wordmark 48/800, centered, top at 26% H
│                                                                          │
│                      (  ▶︎  Singleplayer           )                     │  X-Large Clear-glass capsules, 300 × 48
│                      (  ⌘  Multiplayer            )                     │  10u gap
│                                                                          │
│                      ( 🧩 Mods ) ( ⚙ Settings ) ( ⏻ )                    │  Large 36h: 140, 116, 36 (icon-only quit), 8u gaps
│                                                                          │
│ (✦) (🌐)                                    Copyright Mojang AB. Do not… │  bottom-left circular buttons: Cosmetics, Language
└──────────────────────────────────────────────────────────────────────────┘
```

- **Background**: the vanilla panorama (or the last world screenshot if available). `Renderer.blur(full
  screen, 12u)`, then `bg.scrim.menu`. If blur isn't supported, use the panorama with a `0x66000000` scrim.
  The panorama keeps its slow vanilla rotation.
- **Wordmark**: "Honeycrisp" in SF Pro 48/800 `label.primary`, centered horizontally. No subtitle. The
  version string goes top-left (`FOOTNOTE`, `label.secondary`, 12u inset).
- **Button stack**: starts 48u below the wordmark baseline.
  - Glyphs: `play.fill` Singleplayer, `person.2.fill` Multiplayer, `puzzlepiece.extension` Mods, `gearshape`
    Settings, `power` Quit.
  - The primary visual weight is on Singleplayer and Multiplayer. None of these buttons are accent-filled.
    The accent appears only on keyboard focus.
- **Account chip**: 24u circle avatar (player-head texture from the skin, or `person.crop.circle.fill`).
  Name in `CALLOUT` 600, then `chevron.down` 10. Its menu: account name (disabled header), "Manage Accounts in
  Launcher…", "Copy UUID". Account switching itself lives in the launcher.
- **Bottom row**:
  - Left: circular icon buttons for Cosmetics (`sparkles`) and Language (`globe`), plus Accessibility
    (`accessibility`¹) if the version has it.
  - Right: vanilla's copyright line in `FOOTNOTE` `label.tertiary`. Keep the legally required vanilla text.
- **Narrow screens** (S width < 480u): the stack shrinks to 260 wide and the wordmark to 36u.

### 4.2 Mod menu (Right Shift in-game, or Mods on the main menu)

```
┌───────────────────────────────────────────────────────────────────────────────┐ window: L1, r 24
│ ┌───────────────┐  HUD                       ( 🔍 Search          )  (▭ Edit HUD) │ toolbar 52h
│ │ ▦  All     24 │ ─────────────────────────────────────────────────────────────── │ hard scroll-edge when scrolled
│ │ ▭  HUD     11 │  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐      │
│ │ ⚔  PvP      5 │  │ card     │  │ card     │  │ card     │  │ card     │      │ grid of 3.10 cards
│ │ 📷 Camera   2 │  └──────────┘  └──────────┘  └──────────┘  └──────────┘      │
│ │ ✨ Visual   6 │  ┌──────────┐  ┌──────────┐  …                                │
│ │ 👕 Cosmetics  │  └──────────┘  └──────────┘                                    │
│ │               │                                                                 │
│ │ ⚙  Settings   │                                                                 │
│ │ ◉ Steve       │  (sidebar footer: account row 36h)                              │
│ └───────────────┘                                                                 │
└───────────────────────────────────────────────────────────────────────────────────┘
```

- **Window**: centered, size `clamp(78% W, 720, 1120) × clamp(80% H, 460, 760)`. The game behind keeps
  rendering; the window gets `Renderer.blur`, the rest of the screen gets `bg.scrim` (no blur). The game is
  not paused in multiplayer; in singleplayer it pauses as vanilla's menu does.
- **Sidebar**:
  - Floating L2, inset 8u from the window's top, left and bottom. Width 200, radius 16.
  - Top padding 12u, then the items from 3.1:
    - All `square.grid.2x2`
    - HUD `rectangle.3.group`
    - PvP `shield.lefthalf.filled`
    - Camera `camera`
    - Visual `paintpalette`
    - Cosmetics `tshirt`
  - A flexible spacer, then Settings `gearshape`.
  - Footer: account row with avatar 20u, name `BODY`, and a hairline above.
- **Toolbar** (content area, x from the sidebar's right edge + 16u):
  - Category title `TITLE2`.
  - Right-aligned, 8u apart: search (3.9), then Edit HUD (Regular secondary button with `rectangle.3.group`).
  - In the All and HUD categories, Edit HUD is promoted to primary when no mod is focused.
- **Grid**:
  - Card min width 216, gutter 12.
  - `columns = max(1, floor((contentW + 12) / 228))`. Card width = `(contentW − 12 × (columns − 1)) /
    columns`. Height stays 120.
  - Sorted with enabled modules first, then alphabetical. With an active search, sorted by match score.
  - Content padding: 16u. Vertical scroll per 3.18.
- **Empty search**: `magnifyingglass` 32 box in `label.tertiary`, "No mods match “query”" `TITLE3`, centered.
- **Per-mod settings**: sheet (3.19) over the window.
  - Header: well 32u with the mod glyph, mod name, and the enable switch in the header's right before the
    close button.
  - Body: grouped forms generated from the module's settings. Control mapping: boolean → switch, number range →
    slider, enum ≤ 4 → segmented, enum > 4 → pop-up, color → color well, key → key-bind.
  - HUD modules always get a first group, "Appearance": Style (Pill / Shadow / Plain) segmented, Scale slider
    50–200% (step 5), Text color, Background color, Show icon switch, Brackets switch.
- **Keyboard**:
  - Esc closes the sheet, then the menu.
  - ⌘F focuses search.
  - Tab/arrow keys move between cards. Space toggles the focused card, and Return opens its settings.
  - ⌘1–⌘7 select sidebar items.
- **Settings category**: selecting Settings replaces the grid with the settings forms (4.5). Cosmetics shows
  4.6. Both reuse the same window.

### 4.3 HUD editor

Opened from Edit HUD, or a configurable key (default unbound). The world stays visible.

```
┌──────────────────────────────────────────────────────────────────────────┐
│ ┌─────────┐                         ┆                                    │  every HUD module drawn live with an
│ │ 120 FPS │◀─ hover: 1u accent       ┆                       ┌────────┐  │  outline: 1u 0x59FFFFFF, r = module r + 2
│ └─────────┘                          ┆                       │ ⛨ ⛨ ⛨  │  │
│ ○───────────○                        ┆ ← center guide        └────────┘  │
│ │ W  A S D │  ← selected: 1.5u accent outline + 4 corner handles         │
│ ○───────────○                        ┆                                    │
│- - - - - - - - - - - - - - - - - - - ┼ - - - - - - - - - - - - - - - - - -│  guides: 1u accent, full length, shown only while snapping
│                                      ┆                                    │
│              ( ▦ Grid | ⌖ Snap | ⟲ Reset | 🧩 Mods |  Done )             │  floating toolbar: L1 capsule 40h, bottom-center, 16u up
└──────────────────────────────────────────────────────────────────────────┘
```

- **Backdrop**: `0x33000000` scrim over the world, with no blur so the layout reads true. The vanilla HUD
  (hotbar, health) is still drawn so the user can avoid overlapping it.
- **Grid**:
  - Toggle in the toolbar, default off.
  - Lines every 8 GUI px in `0x14FFFFFF`, with every 4th line at `0x26FFFFFF`. Screen center lines are always
    shown at `0x33FFFFFF` while dragging.
  - When the grid is on, positions snap to it, with the snap threshold applied to grid lines.
- **Selection**:
  - Click selects. ⇧-click adds to the selection. Drag on empty space draws a marquee (`accent.tint` fill,
    1u accent stroke).
  - ⌘A selects all.
  - Selected modules get 4 corner handles: 8u white circles with a 1.5u accent stroke and a hit area of 16u.
- **Dragging**:
  - Moves the module live. The lifted state (2.9) applies, and on drop it settles with `spring.bouncy`.
  - Holding ⇧ locks to the dominant axis.
  - Arrow keys nudge by 1 GUI px, ⇧+arrow by 10.
- **Snapping**:
  - Candidates: screen edges inset 4 GUI px, screen center X/Y, and the edges and centers of every other
    visible module.
  - Threshold: 4 GUI px, checking each axis independently.
  - While snapped, draw a 1u `accent.fill` guide line spanning the screen on that axis.
  - Holding ⌥ disables snapping.
  - The vanilla HUD's rects (hotbar, chat, scoreboard, boss bar) are also snap candidates and show a faint
    `0x26FFFFFF` outline while dragging.
- **Scaling**: drag a corner handle. The opposite corner stays fixed. Scale 0.5–2.0, quantized to 0.05.
  Holding ⇧ scales around the center. The current scale shows as a `CAPTION` chip ("1.25×") above the
  module.
- **Anchoring**:
  - On drop, each module stores its position relative to the nearest of 9 anchors (corners, edge midpoints,
    center), chosen by which third of the screen the module's center falls in.
  - The offset is in GUI px from that anchor, so layouts survive window resizes and guiScale changes.
  - Modules are clamped fully onscreen.
- **Context menu** on a module: Settings…, Reset Position, Reset Scale, Hide.
- **Double-click** a module to open its settings sheet.
- **Toolbar**:
  - Segmented-style cluster of circular buttons: Grid (`square.grid.3x3`, toggle), Snap
    (`dot.scope`¹, fallback `circle.dashed`), Reset All (`arrow.counterclockwise`;
    confirms with a destructive sheet), Mods (`puzzlepiece.extension`, returns to the mod menu).
  - Then a primary small capsule button "Done".
- **Shortcuts**: Esc = Done. ⌘Z / ⇧⌘Z undo and redo moves (keep a 50-step history).

### 4.4 In-game HUD module styles

Readable over any terrain, small, and static. Values are in GUI px at module scale 1.0. HUD modules follow
Minecraft's guiScale.

**Styles**

| Style | Default? | Recipe |
|---|---|---|
| Pill | yes | HUD glass (`0x66000000`), capsule (h/2). Height 14, padding 6u horizontal. Text `HUD` in `0xFFFFFFFF`. Optional glyph 8 box, `0xCCFFFFFF`, 3u before the text. |
| Shadow | | No background. Text `HUD_BOLD` white, with a soft shadow: the same string drawn first at (+0.5, +0.5) GUI px in `0x99000000`, and again at (+1, +1) in `0x40000000`. Looks like vanilla but smoother. |
| Plain | | No background, no shadow. For players who frame the text themselves. |

**Global HUD settings** (Settings → Appearance)
- Text color: default white.
- Background opacity: 0–100%, default 40%.
- Corner style: Capsule (default) / Rounded 4u / Square.
- Light HUD: background `0xB3FFFFFF`, text `0xF2000000`.
- Blur behind HUD modules: default off. Each blur costs a pass per module per frame.

**Labels**
- Label/value format: "120 FPS" (value `HUD_BOLD`, unit `HUD` at 70% alpha).
- With "Brackets" on: "[FPS: 120]".
- Values that change color:
  - Ping: < 80 ms `system.green`, 80–149 `system.orange`, ≥ 150 `system.red`.
  - Armor durability: > 50% white, 25–50% orange, < 25% red.
- **No animation** on HUD values. Exception: Keystrokes and CPS key presses, which fill `0xE6FFFFFF` with
  black text instantly on press and fade back over 0.08 s.

**Per-module defaults**

| Module | Default layout | Default anchor and offset |
|---|---|---|
| Keystrokes | Keys 22 × 22, r 4, gap 2. Space bar 70 × 12. LMB/RMB keys 34 × 18, each with its CPS in `CAPTION`. | top-left (4, 40) |
| Coordinates | Pill with 3 rows (X/Y/Z), 12 px line height, r 6 rect (not capsule) when multi-line. Optional facing/biome rows. | top-left (4, 4) |
| Armor Status | Vertical stack of 4 items (16 px icon via the adapter's item renderer) plus durability text. | bottom-right, above the hotbar |
| Potion Effects | Rows of 18 px vanilla effect icon, name `HUD`, time `HUD_BOLD`. Turns `system.red` under 10 s. | right-center |
| FPS | Pill | top-left stack, 2 GUI px gaps |
| CPS | Pill | top-left stack |
| Ping | Pill | top-left stack |
| Clock | Pill | top-right (−4, 4) |
| Direction | Compass strip: 120 × 14 pill, tick every 15°, N/E/S/W letters, center caret in accent. | top-center (0, 4) |

### 4.5 Settings (sidebar → Settings)

Grouped forms (3.11) in the mod-menu content area, max width 640u, centered. Each section is a group.

| Section | Rows |
|---|---|
| **General** | Open Mod Menu: key-bind (Right Shift) · Edit HUD: key-bind (unbound) · Replace main menu: switch (on) · Show notifications: switch (on) · Pause singleplayer when menu is open: switch (on) |
| **Appearance** | Theme: segmented System / Dark / Light (default System → `Draw.darkAppearance()`) · Accent: row of 9 swatches 20u (System first: a multicolor wheel built from 4 quarter-arcs, then Blue, Purple, Pink, Red, Orange, Yellow, Green, Graphite); selected swatch gets a 2u ring and a `checkmark` 10u inside · UI scale: slider 75–150%, step 5, live preview · Blur behind menus: switch (on; disabled with footnote "Not supported on this Minecraft version" when `blur()` returns false) · Reduce transparency: switch · Reduce motion: switch |
| **HUD** | Default style: segmented Pill / Shadow / Plain · Background opacity: slider · Text color: color well · Light HUD: switch · Hide HUD in F1: switch (on) |
| **Keybinds** | Search field at the top. One row per bindable action, grouped by module: label plus key-bind button. Footer: "Reset All Keybinds" destructive plain button. |
| **About** | Version, Minecraft version, renderer ("Metal via Graft"), "Open Config Folder" button (`folder`). |

### 4.6 Cosmetics (sidebar → Cosmetics, or ✦ on the main menu)

```
┌───────────────┬──────────────────────────────────────────────────────────┐
│  sidebar      │  Cosmetics        ( Capes | Hats | Effects )             │ toolbar: segmented control
│               │ ┌────────────────┐  ┌────┐ ┌────┐ ┌────┐ ┌────┐          │
│               │ │                │  │    │ │ ✓  │ │    │ │    │          │ tiles 96×96 r 14 (fixed; not at
│               │ │  player model  │  └────┘ └────┘ └────┘ └────┘          │ a container corner), L2, gap 12
│               │ │  (drag rotate) │  ┌────┐ ┌────┐ …                       │
│               │ │                │  └────┘ └────┘                        │
│               │ └────────────────┘                                        │
│               │   ( Unequip )                                             │
└───────────────┴──────────────────────────────────────────────────────────┘
```

- **Preview**: 34% of the content width (min 220). L2 group, radius 16. Shows the player model rendered by the
  adapter (needs a platform hook; until then, show a flat front/back skin preview). Drag rotates the yaw,
  releasing coasts with `spring.smooth`. The "Unequip" secondary button sits below.
- **Tiles**: 96 × 96, radius 14.
  - Item preview fills the inset 8u. Name `SUBHEADLINE` below the tile (tile cell height 120).
  - Equipped: 2u `accent.fill` ring, plus `checkmark.circle.fill` 16 box at the top-right (8u inset; white
    disc behind the accent glyph).
  - Click equips with a `spring.bouncy` scale pulse 1.0 → 1.06 → 1.0.
- **Empty category**: `sparkles` 32 box in `label.tertiary`, "No capes yet" `TITLE3`.

---

## 5. Iconography

**Rendering rules**

- **Weight** matches the adjacent text: `BODY` 400 → regular, sidebar → medium, 600+ → semibold. Scale is
  medium unless noted.
- **Size**: the glyph box equals the font's point size + 3u (13u text → 16u box), centered on the cap height.
- **Variants**: outline by default. Use `.fill` for selected sidebar items, "on" states and tab-like contexts.
  Use `.slash` for disabled or hidden.
- **Primitive gap**: `Draw.icon(r, name, x, y, size, argb)` draws **monochrome only** at a fixed weight.
  Monochrome is the v1 rendering mode everywhere; the table below gives the target mode per context.
  Hierarchical and palette need a native overload,
  `icon(..., weight, scale, mode, secondaryArgb)`, in libhoneycrisp via `NSImage.SymbolConfiguration`. Until
  then, render the hierarchical cases as monochrome at full color.
- **Symbol names**: all names below were checked against this Mac's SF Symbols availability table
  (`CoreGlyphs.bundle/…/name_availability.plist`). Unless marked ¹, each is available on macOS 13 or earlier.
  ¹ means macOS 14+. The launcher targets macOS 14, so ¹ symbols are safe there. If the in-game client ever
  runs on macOS 13, swap each ¹ symbol for its listed fallback, or rely on the runtime fallback below.
- **Fallback**: if a name fails to resolve at runtime, draw `questionmark.square.dashed` (macOS 11+) and log the
  missing name once.

### 5.1 Rendering mode and weight per context

| Context | Mode (target / v1) | Weight | Box |
|---|---|---|---|
| Sidebar items | hierarchical / mono | medium | 16 |
| Toolbar and circular icon buttons | monochrome | semibold | 14 |
| Mod-card icon well | monochrome (white on accent) | semibold | 20 |
| List-row leading glyphs | hierarchical / mono | regular | 16 |
| Context-menu items | monochrome | regular | 14 |
| HUD modules | monochrome | semibold | 8–10 |
| Toast kind glyph | palette (white glyph on kind color) / mono in kind color | semibold | 20 |
| Empty states | hierarchical / mono in `label.tertiary` | light | 32 |

### 5.2 UI actions

| Action | Symbol |
|---|---|
| Mods | `puzzlepiece.extension` |
| Settings | `gearshape` (`gearshape.fill` when selected) |
| All mods | `square.grid.2x2` |
| HUD category / Edit HUD | `rectangle.3.group` |
| PvP category | `shield.lefthalf.filled` |
| Camera category | `camera` |
| Visual category | `paintpalette` |
| Cosmetics | `sparkles` (main menu) / `tshirt` (sidebar) |
| Search | `magnifyingglass` |
| Clear field | `xmark.circle.fill` |
| Close sheet | `xmark` |
| Module settings | `gearshape` |
| More / context | `ellipsis.circle` |
| Reset to defaults | `arrow.counterclockwise` |
| Undo | `arrow.uturn.backward` |
| Grid toggle | `square.grid.3x3` |
| Snap toggle | `dot.scope`¹ (fallback `circle.dashed`) |
| Scale handle tooltip | `arrow.up.left.and.arrow.down.right` |
| Move | `arrow.up.and.down.and.arrow.left.and.right` |
| Hide | `eye.slash` |
| Navigate / disclosure | `chevron.right`, `chevron.down` |
| Pop-up indicator | `chevron.up.chevron.down` |
| Selected | `checkmark` |
| Equipped | `checkmark.circle.fill` |
| Add / remove | `plus`, `minus` |
| Delete | `trash` |
| Singleplayer | `play.fill` |
| Multiplayer | `person.2.fill` |
| Quit | `power` |
| Language | `globe` |
| Account | `person.crop.circle` (`.fill` when signed in) |
| Sign out | `rectangle.portrait.and.arrow.right` |
| Keybinds | `keyboard` |
| Appearance | `circle.lefthalf.filled` |
| Color | `eyedropper` (picker), `paintpalette` |
| Info | `info.circle` |
| Warning | `exclamationmark.triangle.fill` |
| Success | `checkmark.circle.fill` |
| Notifications | `bell` |
| News | `newspaper` |
| Download / update | `arrow.down.circle` |
| Open folder | `folder` |
| Versions (launcher) | `square.stack.3d.up` |
| Home (launcher) | `house` |

### 5.3 Modules

| Module | Symbol |
|---|---|
| FPS | `speedometer` |
| CPS | `cursorarrow.click.2` |
| Keystrokes | `keyboard` |
| Coordinates | `mappin.and.ellipse` |
| Direction / Compass | `location.north.circle` |
| Ping | `wifi` |
| Armor Status | `shield.lefthalf.filled` |
| Potion Effects | `flask`¹ (fallback `cross.vial`) |
| Clock | `clock` |
| Server Address | `server.rack` |
| Speed | `gauge.with.dots.needle.67percent`¹ (fallback `speedometer`) |
| Day Counter | `sun.horizon`¹ (fallback `sunrise`) |
| Zoom | `plus.magnifyingglass` |
| Freelook | `eye` |
| Toggle Sprint | `figure.run` |
| Toggle Sneak | `figure.walk` |
| Old Animations | `clock.arrow.circlepath` |
| Hit Color | `burst` |
| Item Physics | `cube` |
| Custom Crosshair | `scope` |
| Fullbright | `sun.max` |
| Time Changer | `moon.stars` |
| Scoreboard | `list.number` |
| Chat | `bubble.left.and.bubble.right` |
| Nametags | `person.text.rectangle` |

---

## 6. Module list (mod menu)

**v1** means must-have for the first release. Every v1 module is backed by a hook already listed in
ARCHITECTURE.md or by `GameInfo`.

| # | Module | Category | Symbol | Description (card text) | v1 |
|---|---|---|---|---|---|
| 1 | FPS | HUD | `speedometer` | Shows your frames per second. | ✔ |
| 2 | CPS | HUD | `cursorarrow.click.2` | Counts left and right clicks per second. | ✔ |
| 3 | Keystrokes | HUD | `keyboard` | Shows WASD, jump and mouse buttons with CPS. | ✔ |
| 4 | Coordinates | HUD | `mappin.and.ellipse` | Shows X, Y, Z, plus facing and biome if you want them. | ✔ |
| 5 | Direction | HUD | `location.north.circle` | A compass strip showing which way you're facing. | ✔ |
| 6 | Ping | HUD | `wifi` | Your latency to the server, color-coded. | ✔ |
| 7 | Armor Status | HUD | `shield.lefthalf.filled` | Worn armor and held item, with durability. | ✔ |
| 8 | Potion Effects | HUD | `flask` | Active effects with level and time left. | ✔ |
| 9 | Clock | HUD | `clock` | Real-world time in 12 or 24 hour format. | ✔ |
| 10 | Server Address | HUD | `server.rack` | Shows the server you're connected to. | |
| 11 | Speed | HUD | `gauge.with.dots.needle.67percent` | Your horizontal speed in blocks per second. | |
| 12 | Day Counter | HUD | `sun.horizon` | Shows the in-game day number. | |
| 13 | Zoom | Camera | `plus.magnifyingglass` | Hold C to zoom. Scroll to adjust, with optional smooth zoom. | ✔ |
| 14 | Freelook | Camera | `eye` | Hold Left Alt to look around without turning. | ✔ |
| 15 | Toggle Sprint | PvP | `figure.run` | Sprint stays on until you press the key again. | ✔ |
| 16 | Toggle Sneak | PvP | `figure.walk` | Sneak stays on until you press the key again. | ✔ |
| 17 | Old Animations | PvP | `clock.arrow.circlepath` | 1.7-style blocking, rod, bow and armor-damage animations. | ✔ |
| 18 | Hit Color | PvP | `burst` | Changes the damage tint shown on entities you hit. | ✔ |
| 19 | Custom Crosshair | Visual | `scope` | Draw your own crosshair: shape, size, gap and color. | ✔ |
| 20 | Item Physics | Visual | `cube` | Dropped items fall flat and tumble instead of spinning. | ✔ |
| 21 | Fullbright | Visual | `sun.max` | Maximum brightness everywhere. Needs a gamma hook. | |
| 22 | Time Changer | Visual | `moon.stars` | Sets the client-side time of day. | |
| 23 | Scoreboard | Visual | `list.number` | Hide red numbers, rescale, or move the sidebar. | |
| 24 | Chat | Visual | `bubble.left.and.bubble.right` | Timestamps, stacked duplicates, background opacity. | |

Skipped: Motion Blur. It needs a framebuffer feedback pass that the 2D `Renderer` contract doesn't expose.
Revisit if Graft adds a post-process hook. Nametags is left out of v1 because it needs entity-render
hooks; it's #25 when those exist (`person.text.rectangle`).

---

## 7. Launcher (SwiftUI, macOS 14+, arm64)

The launcher uses only system components, so on macOS 26+ it gets Liquid Glass automatically. On macOS 14–15
it gets the standard vibrant materials. No custom glass imitation is needed in the launcher.

- **Window**:
  - `WindowGroup` with a single main window: default 1040 × 680, minimum 860 × 560. Title "Honeycrisp".
  - Toolbar style: unified. The title is hidden; the sidebar selection title shows instead.
- **Structure**: `NavigationSplitView` (two columns).
  - **Sidebar**: width 200–240, `List(selection:)` with `.listStyle(.sidebar)`.
    - Home (`house`)
    - Versions (`square.stack.3d.up`)
    - Mods (`puzzlepiece.extension`)
    - Settings (`gearshape`)
    - Footer: an Account row via `safeAreaInset(edge: .bottom)`, showing the avatar (24u player head) and
      the username. It opens an account popover: switch accounts, add a Microsoft account, sign out.
  - Sidebar glyphs use the system sidebar size (medium), with no custom tinting. Selection follows the system
    accent, so it stays consistent with the in-game "System" accent.
- **Home**:
  - **Hero**: full-bleed panorama/screenshot image, extended under the sidebar using
    `backgroundExtensionEffect()` on macOS 26 (on 14–15 it simply starts at the sidebar edge). Wordmark
    bottom-left at 34pt heavy. Over the hero, a floating bottom cluster:
    - version picker: `Picker` in `.menu` style, showing "1.8.9 · Forge", with a `chevron.up.chevron.down`
      look from the system;
    - the Play button: `.borderedProminent`, accent tint, `.controlSize(.extraLarge)` on macOS 26 (capsule),
      `.large` on 14–15. Label "Play" with `play.fill`. Min width 180.
    - The cluster sits in a `GlassEffectContainer` with `.glassEffect()` on macOS 26, and on a
      `.regularMaterial` capsule (r 24) on 14–15.
  - **Launch states**: Play → progress (the button turns into a determinate capsule progress with "Downloading
    assets 42%") → "Running" with a Stop secondary button. Errors appear as an inline banner above the
    cluster: `exclamationmark.triangle.fill`, `system.orange`, with a "Show Log" link.
  - **News**: below the hero, "What's New" `title3` semibold header, then a horizontally scrolling row of 280 ×
    160 cards (r 16, `.thinMaterial` background on 14–15; plain content cards on 26). Each card has an image,
    a title and a date.
- **Versions**: `Table` or `List` with columns Version, Loader, Status (Installed / Update `arrow.down.circle`
  / Not installed), and a trailing ••• menu. Toolbar button "Install Version…" (`plus`).
- **Mods**: a read-only mirror of the in-game module list (section 6) with switches. It edits the same JSON
  config and says "Changes apply on next launch" in the footer. Uses `Form` with `.formStyle(.grouped)`.
- **Settings**: `Form(.grouped)` with Java/memory slider (2–8 GB), game directory, window size, keep launcher
  open, and the same Theme/Accent choices as in-game (written to the shared config, so both stay in sync).
- **Toolbar**: trailing items: account avatar menu, then an "Open Game Folder" `folder` button. No custom
  toolbar backgrounds; on 26 the system draws grouped glass. Items are grouped by function, and text buttons
  are not grouped with icons.
- **How it echoes the game**: same SF Symbols per feature (sections 5 and 6), same category names and order,
  same accent logic, same wordmark, and Play stays the single tinted action, as Singleplayer/Multiplayer are
  the only emphasized buttons in-game. Launcher corner radii come from the system; in-game radii (24 window /
  16 sidebar) are chosen to match macOS 26 window and sidebar proportions.

---

## Sources

- Apple HIG: Materials (Liquid Glass regular vs. clear, 35% dimming layer, no glass in the content layer, use
  it sparingly). https://developer.apple.com/design/human-interface-guidelines/materials
- Apple HIG: SF Symbols (rendering modes, 9 weights matching SF, 3 scales, variants, license restriction on
  logos). https://developer.apple.com/design/human-interface-guidelines/sf-symbols
- Apple HIG: Toggles (switches in list rows only, mini switches in grouped forms, accent instead of green
  allowed). https://developer.apple.com/design/human-interface-guidelines/toggles
- Apple HIG: Sidebars (float in the glass layer, extend content beneath, familiar symbols).
  https://developer.apple.com/design/human-interface-guidelines/sidebars
- Apple HIG: Typography (SF Pro is the macOS system font, never embed system fonts; macOS text styles).
  https://developer.apple.com/design/human-interface-guidelines/typography
- Apple HIG: Sliders, Menus, Motion, Layout, Color (tick marks on macOS, brief and precise feedback motion,
  system colors). https://developer.apple.com/design/human-interface-guidelines/
- WWDC25 session 356, "Get to know the new design system" (concentric = parent radius − padding, capsule = h/2,
  macOS mini/small/medium rounded rects vs. large/x-large capsules, hard vs. soft scroll-edge effects, one
  tinted primary action, bolder left-aligned type). https://developer.apple.com/videos/play/wwdc2025/356/
- Lunar Client: "Getting started with Lunar Client" (Right Shift mod menu, Lunar logo bottom-left of the main
  menu, cosmetics on the main menu, Settings/Controls tabs, per-mod size/color/background settings).
  https://www.lunarclient.com/news/getting-started-with-lunar-client
- Lunar Client: "Lunar Client's Keystrokes mod explained" and "The best HUD setup for Lunar Client" (HUD drag
  and resize, Keystrokes CPS options, Coordinates/FPS/Clock modules).
  https://www.lunarclient.com/news/lunar-clients-keystrokes-mod-explained ·
  https://www.lunarclient.com/news/the-best-hud-setup-for-lunar-client
- SF Symbols name and OS availability: verified locally against
  `/System/Library/CoreServices/CoreGlyphs.bundle/Contents/Resources/name_availability.plist`.
- Spring formulation (response / dampingFraction): SwiftUI `Animation.spring(response:dampingFraction:)`.
  Rubber-band constant 0.55 from UIScrollView's documented behavior.
