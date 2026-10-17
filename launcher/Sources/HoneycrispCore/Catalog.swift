import Foundation

public enum Loader: String, Codable, Sendable { case fabric = "Fabric", forge = "Forge" }

/// One supported Minecraft version. Edit this table to add versions or bump Forge builds.
public struct InstanceSpec: Identifiable, Hashable, Sendable {
    public var id: String { mc }
    public var mc: String
    public var loader: Loader
    /// Forge only: the full Maven version ("1.8.9-11.15.1.2318-1.8.9").
    public var forge: String?
    /// Graft's own Java floor (1.21.x builds need Java 25 though Mojang asks for 21). The launcher picks the
    /// Mojang runtime that satisfies max(version JSON, this).
    public var minJava: Int
    /// Modrinth version ID of the Sodium build Graft's Sodium compat targets (nil: no Sodium, e.g. 1.21.5 and Forge).
    /// Graft Shaders (Iris fork) needs it; otherwise it is opt-in. Bump together with Graft releases.
    public var sodium: String?

    public var sodiumSupported: Bool { sodium != nil }

    public var isLegacy: Bool { loader == .forge }
}

public enum Catalog {
    public static let instances: [InstanceSpec] = [
        InstanceSpec(mc: "26.3", loader: .fabric, minJava: 25, sodium: "bAZQdGpg"),     // sodium-fabric-0.9.2+mc26.3
        InstanceSpec(mc: "26.2", loader: .fabric, minJava: 25, sodium: "xJZxADzI"),     // sodium-fabric-0.9.2+mc26.2
        InstanceSpec(mc: "1.21.11", loader: .fabric, minJava: 25, sodium: "rkdTcxoT"),  // sodium-fabric-0.8.14+mc1.21.11
        InstanceSpec(mc: "1.21.10", loader: .fabric, minJava: 25, sodium: "sFfidWgd"),  // sodium-fabric-0.7.3+mc1.21.10
        InstanceSpec(mc: "1.21.9", loader: .fabric, minJava: 25, sodium: "sFfidWgd"),
        InstanceSpec(mc: "1.21.8", loader: .fabric, minJava: 25, sodium: "7pwil2dy"),   // sodium-fabric-0.7.3+mc1.21.8
        InstanceSpec(mc: "1.21.7", loader: .fabric, minJava: 25, sodium: "7pwil2dy"),
        InstanceSpec(mc: "1.21.6", loader: .fabric, minJava: 25, sodium: "7pwil2dy"),
        InstanceSpec(mc: "1.21.5", loader: .fabric, minJava: 25),
        InstanceSpec(mc: "1.12.2", loader: .forge, forge: "1.12.2-14.23.5.2860", minJava: 8),
        InstanceSpec(mc: "1.8.9", loader: .forge, forge: "1.8.9-11.15.1.2318-1.8.9", minJava: 8),
        InstanceSpec(mc: "1.7.10", loader: .forge, forge: "1.7.10-10.13.4.1614-1.7.10", minJava: 8),
    ]

    public static func spec(_ mc: String) -> InstanceSpec? { instances.first { $0.mc == mc } }

    public static let versionManifest = URL(string: "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json")!
    public static let javaRuntimes = URL(string: "https://launchermeta.mojang.com/v1/products/java-runtime/2ec0cc96c44e5a76b9c8b7c39df7210883d12871/all.json")!
    public static let runtimePlatform = "mac-os-arm64"

    public static func forgeInstaller(_ full: String) -> URL {
        URL(string: "https://maven.minecraftforge.net/net/minecraftforge/forge/\(full)/forge-\(full)-installer.jar")!
    }

    public static let shaderVersions: Set<String> = ["1.21.8", "1.21.10", "1.21.11", "26.2", "26.3"]

    public static func hasShaders(_ spec: InstanceSpec) -> Bool {
        shaderVersions.contains(spec.mc)
    }

    /// Does a dist jar's file name belong to this Minecraft version? "graft-1.21.1-…" must not match 1.21.10.
    /// Accepts "-1.21.8-", "+mc1.21.8-", "_1.21.8.jar" style tokens.
    public static func jar(_ fileName: String, matches mc: String) -> Bool {
        guard fileName.hasSuffix(".jar") else { return false }
        let v = NSRegularExpression.escapedPattern(for: mc)
        return fileName.range(of: "(^|[-+_])(mc)?\(v)([-+_]|\\.jar$)", options: .regularExpression) != nil
    }

    /// Jar family ("graft", "graftshaders-fabric"): the part before the version token. Newer files replace older ones.
    public static func family(_ fileName: String) -> String {
        let parts = fileName.split(whereSeparator: { $0 == "-" || $0 == "+" || $0 == "_" })
        return parts.prefix { !($0.first?.isNumber ?? false) && !$0.hasPrefix("mc") }.joined(separator: "-")
    }
}

/// On-disk layout under ~/Library/Application Support/Honeycrisp.
public struct Paths: Sendable {
    public var root: URL

    public init(root: URL = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0].appending(path: "Honeycrisp")) {
        self.root = root
    }

    public var instances: URL { root.appending(path: "instances") }
    public var libraries: URL { root.appending(path: "libraries") }
    public var assets: URL { root.appending(path: "assets") }
    public var versions: URL { root.appending(path: "versions") }
    public var runtimes: URL { root.appending(path: "runtimes") }
    public var cache: URL { root.appending(path: "cache") }

    public func instance(_ mc: String) -> URL { instances.appending(path: mc) }
    public func instanceDir(_ mc: String) -> URL { instance(mc) }
    public func mods(_ mc: String) -> URL { instance(mc).appending(path: "mods") }
    public func modsDir(_ mc: String) -> URL { mods(mc) }
    public func shaderpacks(_ mc: String) -> URL { instance(mc).appending(path: "shaderpacks") }
    public func resourcepacks(_ mc: String) -> URL { instance(mc).appending(path: "resourcepacks") }
    public func stamp(_ mc: String) -> URL { instance(mc).appending(path: ".honeycrisp/install.json") }
    public func natives(_ mc: String) -> URL { instance(mc).appending(path: ".honeycrisp/natives") }
    public func versionJSON(_ id: String) -> URL { versions.appending(path: "\(id)/\(id).json") }
    public func clientJar(_ mc: String) -> URL { versions.appending(path: "\(mc)/\(mc).jar") }

    /// Computes total regular-file bytes inside a directory tree.
    public static func directorySize(at url: URL) -> Int64 {
        let fm = FileManager.default
        guard let e = fm.enumerator(at: url, includingPropertiesForKeys: [.isRegularFileKey, .fileSizeKey], options: [.skipsHiddenFiles]) else {
            return 0
        }
        var total: Int64 = 0
        for case let file as URL in e {
            guard let vals = try? file.resourceValues(forKeys: [.isRegularFileKey, .fileSizeKey]),
                  vals.isRegularFile == true, let size = vals.fileSize else { continue }
            total += Int64(size)
        }
        return total
    }
}

/// The in-game module list (core modules/Features.java & DESIGN.md §§5.3, 6), mirrored for the launcher's switches.
public struct ModuleInfo: Identifiable, Sendable, Equatable {
    public var id: String
    public var name: String
    public var symbol: String
    public var category: String
    public var summary: String
    public var defaultOn: Bool
    public var nativeOn: Set<String>

    public var onByDefault: Bool { defaultOn }

    public init(id: String, name: String, symbol: String, category: String, summary: String = "", defaultOn: Bool = false, nativeOn: Set<String> = []) {
        self.id = id
        self.name = name
        self.symbol = symbol
        self.category = category
        self.summary = summary
        self.defaultOn = defaultOn
        self.nativeOn = nativeOn
    }

    public func isAvailable(on mc: String) -> Bool { !nativeOn.contains(mc) }

    public func unavailableReason(on mc: String) -> String? {
        nativeOn.contains(mc) ? "Native in \(mc)" : nil
    }

    public static let all: [ModuleInfo] = [
        .init(id: "fps", name: "FPS", symbol: "speedometer", category: "HUD", summary: "Shows your frames per second", defaultOn: true),
        .init(id: "cps", name: "CPS", symbol: "cursorarrow.click.2", category: "HUD", summary: "Counts left and right clicks per second", defaultOn: true),
        .init(id: "keystrokes", name: "Keystrokes", symbol: "keyboard", category: "HUD", summary: "Shows WASD, jump and mouse buttons with CPS"),
        .init(id: "coords", name: "Coordinates", symbol: "mappin.and.ellipse", category: "HUD", summary: "Shows X, Y, Z, plus facing and biome", defaultOn: true),
        .init(id: "compass", name: "Compass", symbol: "location.north.circle", category: "HUD", summary: "A compass strip showing which way you're facing"),
        .init(id: "ping", name: "Ping", symbol: "wifi", category: "HUD", summary: "Your latency to the server, color-coded"),
        .init(id: "armor", name: "Armor Status", symbol: "shield.lefthalf.filled", category: "HUD", summary: "Worn armor and held item, with durability"),
        .init(id: "potions", name: "Potion Effects", symbol: "flask", category: "HUD", summary: "Active effects with level and time left"),
        .init(id: "clock", name: "Clock", symbol: "clock", category: "HUD", summary: "Real-world time in 12 or 24 hour format"),
        .init(id: "server", name: "Server Address", symbol: "server.rack", category: "HUD", summary: "Shows the server you're connected to"),
        .init(id: "speed", name: "Speed", symbol: "gauge.with.dots.needle.67percent", category: "HUD", summary: "Your horizontal speed in blocks per second"),
        .init(id: "daycounter", name: "Day Counter", symbol: "sun.horizon", category: "HUD", summary: "Shows the in-game day number", defaultOn: false),
        .init(id: "togglesprint", name: "Toggle Sprint", symbol: "figure.run", category: "PvP", summary: "Sprint stays on until you press the key again"),
        .init(id: "togglesneak", name: "Toggle Sneak", symbol: "figure.walk", category: "PvP", summary: "Sneak stays on until you press the key again"),
        .init(id: "oldanimations", name: "Old Animations", symbol: "clock.arrow.circlepath", category: "PvP", summary: "1.7-style blocking, rod, bow and armor-damage animations", nativeOn: ["1.7.10"]),
        .init(id: "hitcolor", name: "Hit Color", symbol: "burst", category: "PvP", summary: "Changes the damage tint shown on entities you hit"),
        .init(id: "zoom", name: "Zoom", symbol: "plus.magnifyingglass", category: "Camera", summary: "Hold C to zoom. Scroll to adjust", defaultOn: true),
        .init(id: "freelook", name: "Freelook", symbol: "eye", category: "Camera", summary: "Hold Left Alt to look around without turning"),
        .init(id: "crosshair", name: "Crosshair", symbol: "scope", category: "Visual", summary: "Draw your own crosshair: shape, size, gap and color"),
        .init(id: "fullbright", name: "Fullbright", symbol: "sun.max", category: "Visual", summary: "Maximum brightness everywhere"),
        .init(id: "timechanger", name: "Time Changer", symbol: "moon.stars", category: "Visual", summary: "Sets the client-side time of day"),
        .init(id: "itemphysics", name: "Item Physics", symbol: "cube", category: "Visual", summary: "Dropped items fall flat and tumble instead of spinning"),
        .init(id: "chat", name: "Chat", symbol: "bubble.left.and.bubble.right", category: "Visual", summary: "Timestamps, stacked duplicates, background opacity"),
        .init(id: "scoreboard", name: "Scoreboard", symbol: "list.number", category: "Visual", summary: "Hide red numbers, rescale, or move the sidebar"),
    ]
    public static let categories = ["HUD", "PvP", "Camera", "Visual"]
}

/// <game>/config/honeycrisp/config.json: {"version": 1, "modules": {"<id>": {"enabled": bool, …}}}.
/// Only targeted keys ("enabled", or "ui.theme" / "ui.accent") are touched; every other key is preserved.
public struct ModuleConfig {
    public var file: URL
    public var root: [String: Any]

    public init(gameDir: URL) {
        file = gameDir.appending(path: "config/honeycrisp/config.json")
        root = (try? Data(contentsOf: file)).flatMap { try? JSONSerialization.jsonObject(with: $0) as? [String: Any] } ?? ["version": 1]
    }

    public var existsOnDisk: Bool {
        FileManager.default.fileExists(atPath: file.path)
    }

    public func enabled(_ m: ModuleInfo) -> Bool {
        ((root["modules"] as? [String: Any])?[m.id] as? [String: Any])?["enabled"] as? Bool ?? m.defaultOn
    }

    public mutating func set(_ m: ModuleInfo, _ on: Bool) {
        var modules = root["modules"] as? [String: Any] ?? [:]
        var entry = modules[m.id] as? [String: Any] ?? [:]
        entry["enabled"] = on
        modules[m.id] = entry
        root["modules"] = modules
        save()
    }

    public var uiTheme: String? {
        ((root["modules"] as? [String: Any])?["ui"] as? [String: Any])?["theme"] as? String
    }

    public var uiAccent: String? {
        ((root["modules"] as? [String: Any])?["ui"] as? [String: Any])?["accent"] as? String
    }

    public mutating func setUI(theme: String? = nil, accent: String? = nil) {
        var modules = root["modules"] as? [String: Any] ?? [:]
        var entry = modules["ui"] as? [String: Any] ?? [:]
        if entry["enabled"] == nil { entry["enabled"] = true }
        if let theme { entry["theme"] = theme }
        if let accent { entry["accent"] = accent }
        modules["ui"] = entry
        root["modules"] = modules
        save()
    }

    public func save() {
        try? FileManager.default.createDirectory(at: file.deletingLastPathComponent(), withIntermediateDirectories: true)
        try? JSONSerialization.data(withJSONObject: root, options: [.prettyPrinted, .sortedKeys]).write(to: file, options: .atomic)
    }
}
