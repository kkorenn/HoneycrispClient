#!/bin/zsh
# Builds Honeycrisp.app (arm64, ad-hoc signed) into launcher/build/.
# Usage: scripts/bundle.sh [client-id]   (client ID also settable later in Settings)
set -euo pipefail
cd "${0:A:h}/.."
VERSION="${VERSION:-0.2.4}"
CLIENT_ID="${1:-${MC_CLIENT_ID:-}}"
APP=build/Honeycrisp.app

swift build -c release --arch arm64
BIN="$(swift build -c release --arch arm64 --show-bin-path)/Honeycrisp"

rm -rf "$APP"
mkdir -p "$APP/Contents/MacOS" "$APP/Contents/Resources"
cp "$BIN" "$APP/Contents/MacOS/Honeycrisp"

# App icon: AppIconView (Sources/Honeycrisp/Logo.swift) rendered by scripts/make-icon.swift; redrawn when either changes.
ICONSET=build/AppIcon.iconset
if [[ ! -f build/AppIcon.icns || scripts/make-icon.swift -nt build/AppIcon.icns || Sources/Honeycrisp/Logo.swift -nt build/AppIcon.icns ]]; then
  rm -rf "$ICONSET"; mkdir -p "$ICONSET"
  if swiftc -O -parse-as-library scripts/make-icon.swift Sources/Honeycrisp/Logo.swift -o build/make-icon && build/make-icon build/icon-1024.png; then
    for s in 16 32 128 256 512; do
      sips -z $s $s build/icon-1024.png --out "$ICONSET/icon_${s}x${s}.png" >/dev/null
      sips -z $((s*2)) $((s*2)) build/icon-1024.png --out "$ICONSET/icon_${s}x${s}@2x.png" >/dev/null
    done
    iconutil -c icns "$ICONSET" -o build/AppIcon.icns
  fi
fi
[[ -f build/AppIcon.icns ]] && cp build/AppIcon.icns "$APP/Contents/Resources/AppIcon.icns"

# Game icon: the running game's Dock icon ("Honeycrisp Client", see AppModel.gameIdentity), from Resources/game-icon.png.
if [[ ! -f build/GameIcon.icns || Resources/game-icon.png -nt build/GameIcon.icns ]]; then
  GSET=build/GameIcon.iconset; rm -rf "$GSET"; mkdir -p "$GSET"
  for s in 16 32 128 256 512; do
    sips -z $s $s Resources/game-icon.png --out "$GSET/icon_${s}x${s}.png" >/dev/null
    sips -z $((s*2)) $((s*2)) Resources/game-icon.png --out "$GSET/icon_${s}x${s}@2x.png" >/dev/null
  done
  iconutil -c icns "$GSET" -o build/GameIcon.icns
fi
cp build/GameIcon.icns "$APP/Contents/Resources/GameIcon.icns"

cat > "$APP/Contents/Info.plist" <<PLIST
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>CFBundleName</key><string>Honeycrisp</string>
  <key>CFBundleDisplayName</key><string>Honeycrisp</string>
  <key>CFBundleIdentifier</key><string>dev.honeycrisp.launcher</string>
  <key>CFBundleExecutable</key><string>Honeycrisp</string>
  <key>CFBundlePackageType</key><string>APPL</string>
  <key>CFBundleShortVersionString</key><string>${VERSION}</string>
  <key>CFBundleVersion</key><string>${VERSION}</string>
  <key>CFBundleIconFile</key><string>AppIcon</string>
  <key>LSMinimumSystemVersion</key><string>14.0</string>
  <key>LSApplicationCategoryType</key><string>public.app-category.games</string>
  <key>LSArchitecturePriority</key><array><string>arm64</string></array>
  <key>NSHighResolutionCapable</key><true/>
  <key>NSSupportsAutomaticTermination</key><false/>
  <key>MCClientID</key><string>${CLIENT_ID}</string>
</dict>
</plist>
PLIST

codesign --force --sign - --timestamp=none "$APP"
codesign --verify --strict "$APP"
echo "Built $PWD/$APP"
