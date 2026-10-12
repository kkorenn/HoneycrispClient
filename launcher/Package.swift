// swift-tools-version: 6.0
import PackageDescription

let package = Package(
    name: "Honeycrisp",
    platforms: [.macOS(.v14)],
    targets: [
        .target(name: "HoneycrispCore"),
        .executableTarget(name: "Honeycrisp", dependencies: ["HoneycrispCore"]),
        .testTarget(name: "HoneycrispCoreTests", dependencies: ["HoneycrispCore"], resources: [.copy("Fixtures")]),
        // Offscreen PNG renders of every screen (never shows a window): build/snapshots/*.png
        .testTarget(name: "HoneycrispSnapshots", dependencies: ["Honeycrisp", "HoneycrispCore"]),
    ]
)
