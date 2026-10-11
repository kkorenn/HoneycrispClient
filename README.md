# Honeycrisp Client

A Minecraft client and launcher for Apple Silicon Macs, in the spirit of Lunar Client, with a native macOS look.

Honeycrisp Client has three parts:

- **Honeycrisp launcher**: a native macOS app written in SwiftUI. It installs and starts Minecraft and signs you in with your Microsoft account.
- **Honeycrisp Client mod**: an in-game PvP and quality-of-life mod with HUD mods, zoom, freelook and more.
- **Graft**: a renderer that draws Minecraft through Apple's Metal graphics API instead of OpenGL, with **Graft Shaders** for shader-pack support.

> **NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT.**
> Honeycrisp Client is an independent project and is not affiliated with Apple Inc.

## Supported Minecraft versions

| Loader | Versions |
|---|---|
| Forge | 1.7.10, 1.8.9, 1.12.2 |
| Fabric | 1.21.5 – 1.21.11, 26.2, 26.3 |

Requires a Mac with Apple Silicon (M1 or later) and macOS 14 or newer.

## Launcher

- One-click install of Minecraft, Forge or Fabric, the Java runtime and the client mods, with every download checked by hash
- Native Apple Silicon libraries for the legacy versions (1.7.10, 1.8.9, 1.12.2)
- **Microsoft account sign-in**: the official Microsoft → Xbox Live → Minecraft services flow, using OAuth 2.0 with PKCE in the system browser sheet, or the device-code flow
- Per-version instances, a drag-and-drop mod manager, a memory slider and a live game log with crash reports
- Offline mode for singleplayer

### Microsoft sign-in and privacy

The launcher uses Microsoft sign-in only to start Minecraft with the account you own.

- You sign in on Microsoft's own page. The launcher never sees your password.
- Tokens stay on your Mac, in the macOS Keychain. They are never sent anywhere except Microsoft, Xbox Live and Minecraft services.
- No analytics and no telemetry. The launcher has no Honeycrisp servers and talks only to:
  - Microsoft, Xbox Live and Minecraft services, to sign you in
  - Mojang, Fabric, Forge, Modrinth and Azul, to download the game, the loaders, mods and Java
- Requested scopes: `XboxLive.signin offline_access`.

## In-game features

| Category | Mods |
|---|---|
| HUD | FPS, CPS, Keystrokes, Coordinates, Compass, Ping, Armor Status, Potion Effects, Clock, Server Address, Speed |
| PvP | Toggle Sprint, Toggle Sneak, 1.7 Old Animations, Hit Color, Custom Crosshair |
| Camera | Zoom (smooth, scroll to adjust), Freelook |
| Visual | Fullbright, Time Changer, Item Physics, Chat and Scoreboard tweaks |

- Press **Right Shift** to open the mod menu.
- The HUD editor lets you drag, snap and scale every HUD element.
- Text and icons use the system font and SF Symbols, drawn by macOS on your Mac. No Apple fonts or icons are bundled.

## Building

```sh
# Mods: needs JDK 25
./gradlew build

# Launcher: needs Xcode 16 or newer
cd launcher && make app        # → launcher/build/Honeycrisp.app
```

## Credits

- Graft Shaders is based on [Iris](https://github.com/IrisShaders/Iris) (LGPL-3.0).
- Works with [Sodium](https://github.com/CaffeineMC/sodium) on Fabric.
- Native libraries for legacy versions on Apple Silicon come from the [MinecraftMachina](https://github.com/MinecraftMachina) builds, the same ones Prism Launcher and the Modrinth App use.
