# Honeycrisp launcher

Native SwiftUI launcher for macOS 14+ on Apple Silicon (arm64 only). Swift Package, no third-party dependencies.

```
make build     # swift build -c release --arch arm64
make test      # swift test: rules, libraries, arm64 overrides, Azul parsing, arguments, manifests, PKCE, offline UUID,
               # GitHub asset selection/digests/update check, pinned Sodium (fixtures, no network)
make app       # → build/Honeycrisp.app (Info.plist, drawn app icon, ad-hoc codesign)
make snapshots # offscreen PNGs of every screen, dark + light → build/snapshots/ (never opens a window)
make app CLIENT_ID=<azure-application-id>   # bakes MCClientID into Info.plist
```

## Layout

```
Package.swift
Sources/HoneycrispCore/       pure logic + networking (tested)
  VersionJSON.swift           version JSON model, rule evaluation, inheritsFrom merge, library resolution
  LaunchCommand.swift         JVM/game argument building, offline UUID
  Installer.swift             vanilla / Fabric / Forge / assets / Java runtime / mods install; launch prep
  Auth.swift                  Microsoft → Xbox Live → XSTS → Minecraft, PKCE, device code, account file
  Net.swift                   HTTP helpers, parallel SHA-1/SHA-256/SHA-512-verified downloader
  Releases.swift              GitHub latest releases: asset matching, digests, update check, session cache
  Catalog.swift               supported versions + pinned Sodium builds (edit here), URLs, on-disk paths
Sources/Honeycrisp/           SwiftUI app
  Theme.swift                 every look value: palette, spacing, radii, type, motion, Liquid Glass helpers (material fallback < 26)
  Logo.swift                  the pixel-apple mark and the app icon view (make-icon.swift renders it)
  Backdrop.swift              full-window animated mesh-gradient art + grain, hero art
  Model.swift                 AppModel: settings, Play state machine, process + log + crash detection
  Views.swift                 window, glass sidebar, Home (hero, Play bar, release notes), Versions, Log
  ModsView.swift              per-instance mod jars (drag & drop) + Honeycrisp Client module switches
  SettingsView.swift          Settings, Account sheet (device code + QR)
Tests/HoneycrispCoreTests/    tests + Fixtures/*.json
Tests/HoneycrispSnapshots/    offscreen screen renders from a mock model (temp data dir, no account file/UserDefaults/network)
scripts/bundle.sh, make-icon.swift
```

Data lives in `~/Library/Application Support/Honeycrisp/`: `instances/<version>/` (game dir: mods, config, saves,
resourcepacks, options.txt), and shared `libraries/`, `assets/`, `versions/`, `runtimes/`, `cache/`.

## How installing works

- **Vanilla**: version manifest v2 → version JSON (SHA-1 checked) → client jar, libraries (rules evaluated for
  `osx`/`arm64`), asset index + objects (32 parallel downloads, SHA-1 verified, finished files skipped on re-run),
  Mojang's patched log4j config.
- **Fabric** (1.21.5–1.21.11, 26.2, 26.3): newest stable loader from `meta.fabricmc.net`, its profile JSON is a version JSON
  that `inheritsFrom` vanilla and gets merged.
- **Forge** (1.7.10, 1.8.9, 1.12.2): the installer is downloaded but **not run**. 1.7.10/1.8.9 installers are the
  v1 format, which has no headless client install at all; 1.12.2 is the v2 format without client processors. In
  both cases `install_profile.json` holds everything: v1 `versionInfo` is the version JSON and `install.filePath`
  is the universal jar; v2 `json` names the version JSON and Forge's jar sits under `maven/` in the installer.
  Retired `files.minecraftforge.net` URLs are rewritten, and libraries fall back to libraries.minecraft.net →
  maven.minecraftforge.net → Maven Central. A profile that needs client processors (1.13+) is rejected with an error.
  Forge builds are pinned in `Catalog.swift`.
- **Java**: Mojang's runtime index for `mac-os-arm64`. The launcher uses the component the version JSON asks for
  if it meets `max(javaVersion.majorVersion, Catalog minJava)`, otherwise the oldest component that does
  (Graft needs Java 25 on 1.21.x even though Mojang asks for 21). Java 8 means exactly 8, because
  LaunchWrapper-era Forge breaks on 9+. Mojang's `mac-os-arm64` `jre-legacy` list is empty, so the Forge versions
  always take the fallback: when Mojang has nothing in range, the launcher asks Azul's metadata API
  (`api.azul.com/metadata/v1/zulu/packages/?java_version=N&os=macos&arch=aarch64&java_package_type=jre&archive_type=zip&javafx_bundled=false&crac_supported=false&latest=true&release_status=ga`),
  picks the newest `java_version`, fetches `/packages/<uuid>` for `sha256_hash`, downloads the zip, verifies
  SHA-256, and unpacks it to `runtimes/zulu-N/<package name>/` (`…/Contents/Home/bin/java`). An already-unpacked
  package is reused without downloading. Settings → Java still overrides all of this.
- **Natives / Apple Silicon**: 1.21+ ship `natives-macos-arm64` LWJGL 3 jars on the classpath, and the JSON adds
  `-XstartOnFirstThread`. 1.7.10–1.12.2 use LWJGL 2, whose Mojang `natives-osx` are x86_64-only. On macOS arm64,
  `LibraryResolver.arm64Overrides` swaps them after rule evaluation (so install and launch both get it) for the
  same artifacts Prism Launcher and Modrinth App use, each pinned by SHA-1 and size:
  - `lwjgl`, `lwjgl_util` 2.x → 2.9.4-nightly-20150209 (Mojang). 1.7.10's 2.9.1 is upgraded too, as Prism does.
  - `lwjgl-platform` 2.x → MinecraftMachina `2.9.4-20150209-mmachina.2` natives (arm64 `liblwjgl.dylib` +
    `openal.dylib`), so LWJGL's OpenAL sound loads on an arm64 JVM.
  - `jinput-platform` 2.0.5 → r58Playz/jinput-m1 (universal `libjinput-osx.jnilib`).
  - 1.12.2: `java-objc-bridge` 1.0.0 (x86_64 `libjcocoa`) → MinecraftMachina 1.1.0-mmachina.1 (universal), and
    `text2speech` 1.10.x → 1.11.3, the same pair Prism's `osx-arm64` rules select.

  Graft's legacy module replaces Display/Keyboard/Mouse with its own Metal window and native library, which
  dispatches to the AppKit main queue, so legacy launches do **not** get `-XstartOnFirstThread`.
  Install stamp revision 2 makes older legacy installs re-run so the new jars get downloaded.

  Sources: Prism meta `https://meta.prismlauncher.org/v1/org.lwjgl/2.9.4-nightly-20150209.json` and
  `…/v1/net.minecraft/1.12.2.json`. Modrinth App's own 1.8.9 version JSON lists the same URLs and SHA-1s, and its
  extracted natives are byte-identical to the MinecraftMachina jar.
- **Mods**: Graft, Graft Shaders and Honeycrisp come from the latest GitHub release of `kkorenn/Graft`,
  `kkorenn/GraftShaders` and `kkorenn/HoneycrispClient` (REST API, unauthenticated, `Releases.swift`). The asset for an
  instance is the one named exactly `<project>-<mc>-….jar` (`graft-` never matches `graftshaders-`, 1.21.1 never
  matches 1.21.10), downloaded from `browser_download_url` and checked against the asset's `digest` (`sha256:<hex>`),
  or its `size` when GitHub gives no digest. Graft Shaders only has jars for 1.21.8, 1.21.10, 1.21.11, 26.2 and 26.3;
  other versions skip it quietly. Release JSON is cached for the launcher session. If GitHub is rate limited (403/429),
  has no release yet (404) or is unreachable, the instance keeps the jars it already has and the install shows a
  warning instead of failing. Fabric instances also get the newest Fabric API from Modrinth, plus Sodium pinned to the
  build Graft's Sodium compat targets (Modrinth version IDs in `Catalog.swift`, resolved via
  `api.modrinth.com/v2/version/<id>`, SHA-512 checked, refused if its `game_versions` lacks the instance). Sodium is
  installed wherever Graft Shaders is, opt-in elsewhere (Settings toggle), and never on 1.21.5 or Forge.
  Jars the launcher installed are recorded (file, release tag, source) and replaced on update; jars the user drops in stay.
  Mods and Settings show each instance's installed versions and source.
  **Developer override**: Settings → "Developer: use local jar folder" (off by default). When set, the newest
  `*-<mc>-*.jar` / `*+mc<mc>-*.jar` per family in that folder is copied in instead, as before; for your own builds
  (e.g. `Graft/dist`).
- **Play button**: Install (no stamp) → Update (a newer GitHub release jar for this version, local-folder jars,
  jar source, Sodium or Java choice changed since install) → Play → Running (with Stop). Releases are fetched when Home
  opens and on "Check for Updates" (Mods / Settings), not on every render; the rest of the check is local.

## Microsoft sign-in: getting a client ID

Minecraft's login API only accepts Azure apps that Mojang has approved. Until then sign-in fails at
`login_with_xbox` with 403, and the launcher shows **"Client ID not approved yet"**. **Offline mode**
(username only, for singleplayer and offline-mode servers) works without any of this.

1. Go to <https://portal.azure.com> → **Microsoft Entra ID** → **App registrations** → **New registration**.
   - Name: `Honeycrisp Client`.
   - Supported account types: **Personal Microsoft accounts only** (or "Accounts in any organizational directory and
     personal Microsoft accounts"). Personal accounts must be included; the launcher uses the `consumers` endpoint.
   - Redirect URI: platform **Public client/native (mobile & desktop)**, URI **`honeycrisp://auth`**.
2. After you create the app, copy the **Application (client) ID** from Overview.
3. **Authentication** → Advanced settings → set **Allow public client flows** to **Yes**. The "Use Code"
   (device code) fallback needs this. No client secret is needed because the app uses PKCE.
4. Leave **API permissions** at their defaults. The launcher requests the `XboxLive.signin offline_access` scopes at sign-in.
5. Submit Mojang's **Minecraft services API access** review form for the app (the short link Mojang publishes is
   <https://aka.ms/mce-reviewappid>; if it moves, search the Minecraft help center for "app registration"). Enter the
   Application (client) ID and the tenant ID. Mojang reviews it by hand, and approval can take a while.
6. Put the client ID in **Settings → Microsoft sign-in**, or build with `make app CLIENT_ID=<id>` (Info.plist key
   `MCClientID`; the Settings field takes precedence).
7. Click **Add Microsoft Account** in the account popover (bottom of the sidebar). On success the launcher marks the client
   ID approved. A 403 marks it "not approved yet".

The flow: authorization code + PKCE in `ASWebAuthenticationSession` (ephemeral) → `login.microsoftonline.com/consumers` token →
Xbox Live user token → XSTS (`rp://api.minecraftservices.com/`) → `login_with_xbox` → entitlement check
(`game_minecraft`/`product_minecraft`) → profile. Accounts (including the Microsoft refresh token) are stored in
`~/Library/Application Support/Honeycrisp/accounts.json` (mode 0600). The Minecraft token is refreshed automatically before launch when it is within 5 minutes of expiry.

## Not verified yet (requires network or a real launch)

Everything that touches the network or starts a process has only been compile-checked: Microsoft/Xbox/Minecraft
auth, all downloads (Mojang, Fabric meta, Forge installers, Modrinth, GitHub releases, Java runtimes), the Forge profile
parsing against real installers, and launching and stopping the game with crash-report detection.

Checked by hand outside the app: the Azul API response shape, the Zulu 8 zip's SHA-256 and layout (unzipped
`bin/java -version` → 1.8.0_504 aarch64), the SHA-1s and arm64 contents of every override jar, and that
Mojang's real 1.7.10/1.8.9/1.12.2 JSONs resolve to only the arm64 artifacts. Still unverified: `installZulu`
running end to end inside the launcher, and real legacy sound in game. Mojang's `twitch-platform` natives
(1.7.10/1.8.9) are still x86_64. Vanilla only loads them for Twitch streaming and catches the failure.
