# Honeycrisp Client — architecture

A Lunar-style client for Apple Silicon Macs: an in-game mod (HUD mods, zoom, freelook, PvP tweaks, menus, own
cosmetics) for every Minecraft version Graft supports, plus a native SwiftUI launcher. It renders through
Graft (Metal), so the mod requires Graft and macOS on arm64.

## Layout

```
HoneycrispClient/
  core/            version-independent Java (compiled with --release 8: loads on 1.7.10 … 26.3)
    src/main/java/dev/honeycrisp/
      Honeycrisp.java       entry point / singletons (init(Platform), hooks(), modules())
      platform/             interfaces each version adapter implements (Platform, Renderer, Texture, GameInfo, Keys)
      hooks/                Hooks: what adapters ask the core at their injection points (zoom FOV, freelook, ...)
      module/               module system (Module, HudModule, settings), registry, config (JSON, own tiny codec)
      modules/              every feature (FPS, CPS, Keystrokes, ..., Zoom, Freelook, OldAnimations, ...)
      ui/                   design system + screens (drawn ONLY through platform.Renderer)
      mac/                  JNI bridge to libhoneycrisp (CoreText text, SF Symbols, shapes, accent color)
    src/main/native/        honeycrisp.m → libhoneycrisp.dylib (arm64), packaged into the jar
    src/test/java/          headless tests incl. a Java2D Renderer that renders screens to PNG (no window)
  mc26_3/, mc1_8_9/, …      thin version adapters (Fabric for 1.21.5+/26.x, Forge for 1.7.10/1.8.9/1.12.2)
  launcher/                 SwiftUI macOS app (SwiftPM), arm64 only
  DESIGN.md                 design spec (Apple HIG / current macOS material language, laid out like Lunar)
```

## The contract between core and adapters

Adapters stay tiny: they hand the core a minimal 2D renderer, input events and game state, and call `Hooks` from
mixins. Everything visual (text, icons, rounded shapes, blur-less glass, animation) is built in core from textures.

- **Coordinates**: GUI-scaled pixels (Minecraft's scaled GUI coordinate space), floats, origin top-left.
  `Platform.guiScale()` gives scaled→framebuffer pixels; core rasterizes text/icons at `guiScale` (already framebuffer px)
  so they are pixel-sharp.
- **Colors**: ARGB ints, non-premultiplied. Textures uploaded by core are RGBA8, **premultiplied alpha**;
  adapters draw them with blend (ONE, ONE_MINUS_SRC_ALPHA).
- **Keys**: GLFW key codes everywhere in core (`platform.Keys`). LWJGL2 adapters translate.
- **Threads**: everything on the render thread.

### platform.Renderer (per version)
```java
Texture createTexture(int width, int height, ByteBuffer premultipliedRgba); // upload; core caches
void deleteTexture(Texture t);
void fill(float x, float y, float w, float h, int argb);                    // solid rect
void texture(Texture t, float x, float y, float w, float h,
             float u0, float v0, float u1, float v1, int argbTint);         // tint multiplies (incl. alpha)
void pushClip(float x, float y, float w, float h);                          // nested, intersecting
void popClip();
boolean blur(float x, float y, float w, float h, float radius);             // optional backdrop blur; false = unsupported
```

### platform.Platform (per version)
```java
String minecraftVersion();
Renderer renderer();
GameInfo game();
java.nio.file.Path configDir();
int scaledWidth(); int scaledHeight(); double guiScale(); double backingScale();
void openScreen(dev.honeycrisp.ui.Screen screen);  // wraps it in a version Screen that forwards events
void closeScreen();
boolean isKeyDown(int glfwKey);
String keyName(int glfwKey);
```

### platform.GameInfo (per version; HUD data, nulls/defaults when not in a world)
fps, ping, x/y/z, facing, biome, server address, day time, held item + durability, armor (4) + durability,
potion effects (name, amplifier, seconds, icon key), player name/UUID, sprinting/sneaking, inWorld, …

### hooks.Hooks (core → adapters call these from mixins)
- `onClientTick()`, `onRenderHud(float partialTick)` (after vanilla HUD), `onKey(int glfwKey, int action)`,
  `onMouseButton(int button, int action)`, `onAttack()` (CPS)
- `double fovMultiplier(double partialTick)` (zoom: ×; 1.0 = off)
- `boolean freelookActive()`, `boolean consumeMouseLook(double dx, double dy)`, `float freelookYaw/Pitch()`
- `boolean oldAnimation(OldAnimation which)`, `int hitColor()` (ARGB, alpha=strength), `boolean itemPhysics()`
- `boolean sprintHeld()/sneakHeld()` (toggle sprint/sneak), `Crosshair crosshair()` (null = vanilla)
- `boolean replaceMainMenu()`, `void openMainMenu()`, `void openModMenu()` (right shift by default)

## Rules
- core is Java 8 language/bytecode, no dependencies besides the JDK (no Gson etc.: own JSON codec).
- macOS/arm64 only; the dylib loads from the jar (extracted to the config dir's natives/).
- SF Pro and SF Symbols are never bundled: rendered at runtime through AppKit/CoreText on the user's Mac.
- No version-specific code in core; no feature logic in adapters (adapters only translate).

## Core drawing primitives (dev.honeycrisp.ui.draw) — used by all UI and HUD code

```java
// SF Pro via CoreText at runtime; sizes in GUI-scaled px; weight 100–900; MONO = SF Mono.
public final class Font {
    public final float size; public final int weight; public final boolean mono;
    public static Font of(float size, int weight); public static Font mono(float size, int weight);
    // presets (macOS text styles scaled for GUI px): LARGE_TITLE, TITLE, TITLE2, TITLE3, HEADLINE, BODY,
    // CALLOUT, SUBHEADLINE, FOOTNOTE, CAPTION, CAPTION2, HUD (compact, for in-game HUD), HUD_BOLD
    public Font withWeight(int weight); public Font withSize(float size);
}
public final class Draw {
    static void text(Renderer r, String s, float x, float y, Font f, int argb);   // (x, y) = top-left of the line box
    static void textCentered(Renderer r, String s, float cx, float y, Font f, int argb);
    static float textWidth(String s, Font f);
    static float lineHeight(Font f);
    static void icon(Renderer r, String sfSymbol, float x, float y, float size, int argb); // SF Symbol in a size×size box
    static void roundRect(Renderer r, float x, float y, float w, float h, float radius, int argb);   // continuous corners
    static void roundRectStroke(Renderer r, float x, float y, float w, float h, float radius, float lineWidth, int argb);
    static void shadow(Renderer r, float x, float y, float w, float h, float radius, float blur, int argb);
    static void glass(Renderer r, float x, float y, float w, float h, float radius, GlassStyle style); // material panel:
        // backdrop blur when Renderer.blur() works, else tinted translucent fill; highlight edge + shadow per DESIGN.md
    static int accent();             // macOS accent color (NSColor.controlAccentColor), ARGB
    static boolean darkAppearance(); // macOS appearance
    static void releaseAll(Renderer r); // drop cached textures (resource reload / resize)
}
```
Caching: text runs, icons and shape nine-slices are rasterized once per (content, scale) by libhoneycrisp and
cached as textures; `Draw` must be cheap per frame.
