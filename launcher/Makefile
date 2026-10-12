.PHONY: build test app snapshots clean

build:
	swift build -c release --arch arm64

test:
	swift test

# make app CLIENT_ID=<azure-app-id>   (optional; Settings can set it too)
CLIENT_ID ?= cc83698e-daaf-4e70-843f-fde5c5245a42
app:
	MC_CLIENT_ID="$(CLIENT_ID)" scripts/bundle.sh

# Offscreen PNGs of every screen → build/snapshots/ (no window is ever shown)
snapshots:
	swift test --filter SnapshotTests

clean:
	rm -rf .build build
