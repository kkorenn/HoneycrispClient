#!/bin/zsh
# Builds Honeycrisp.app (arm64, ad-hoc signed) into launcher/build/.
# Usage: scripts/bundle.sh [client-id]   (client ID also settable later in Settings)
set -euo pipefail
cd "${0:A:h}/.."
VERSION="${VERSION:-1.0.0}"
CLIENT_ID="${1:-${MC_CLIENT_ID:-}}"
APP=build/Honeycrisp.app

swift build -c release --arch arm64
BIN="$(swift build -c release --arch arm64 --show-bin-path)/Honeycrisp"

rm -rf "$APP"
mkdir -p "$APP/Contents/MacOS" "$APP/Contents/Resources"
cp "$BIN" "$APP/Contents/MacOS/Honeycrisp"

# Placeholder icon (skipped quietly if it can't be drawn).
ICONSET=build/AppIcon.iconset
if [[ ! -f build/AppIcon.icns ]]; then
  rm -rf "$ICONSET"; mkdir -p "$ICONSET"
  if swift scripts/make-icon.swift build/icon-1024.png 2>/dev/null; then
    for s in 16 32 128 256 512; do
      sips -z $s $s build/icon-1024.png --out "$ICONSET/icon_${s}x${s}.png" >/dev/null
      sips -z $((s*2)) $((s*2)) build/icon-1024.png --out "$ICONSET/icon_${s}x${s}@2x.png" >/dev/null
    done
    iconutil -c icns "$ICONSET" -o build/AppIcon.icns
  fi
fi
[[ -f build/AppIcon.icns ]] && cp build/AppIcon.icns "$APP/Contents/Resources/AppIcon.icns"

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
