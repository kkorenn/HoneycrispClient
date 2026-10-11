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
    public func mods(_ mc: String) -> URL { instance(mc).appending(path: "mods") }
    public func stamp(_ mc: String) -> URL { instance(mc).appending(path: ".honeycrisp/install.json") }
    public func natives(_ mc: String) -> URL { instance(mc).appending(path: ".honeycrisp/natives") }
    public func versionJSON(_ id: String) -> URL { versions.appending(path: "\(id)/\(id).json") }
    public func clientJar(_ mc: String) -> URL { versions.appending(path: "\(mc)/\(mc).jar") }
}
