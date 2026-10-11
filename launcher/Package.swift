// swift-tools-version: 6.0
import PackageDescription

let package = Package(
    name: "Honeycrisp",
    platforms: [.macOS(.v14)],
    targets: [
        .target(name: "HoneycrispCore"),
        .executableTarget(name: "Honeycrisp", dependencies: ["HoneycrispCore"]),
        .testTarget(name: "HoneycrispCoreTests", dependencies: ["HoneycrispCore"], resources: [.copy("Fixtures")]),
    ]
)
