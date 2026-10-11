.PHONY: build test app clean

build:
	swift build -c release --arch arm64

test:
	swift test

# make app CLIENT_ID=<azure-app-id>   (optional; Settings can set it too)
CLIENT_ID ?= cc83698e-daaf-4e70-843f-fde5c5245a42
app:
	MC_CLIENT_ID="$(CLIENT_ID)" scripts/bundle.sh

clean:
	rm -rf .build build
