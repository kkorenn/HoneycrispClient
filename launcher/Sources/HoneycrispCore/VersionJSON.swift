import Foundation

// Mojang version JSON (also what Fabric meta and Forge installers produce).

public struct OSRule: Codable, Sendable, Equatable {
    public var name: String?
    public var arch: String?
    public var version: String?
}

public struct Rule: Codable, Sendable, Equatable {
    public var action: String
    public var os: OSRule?
    public var features: [String: Bool]?
}

/// What the rules are evaluated against. Defaults describe this launcher: macOS on arm64.
public struct RuleEnvironment: Sendable {
    public var osName = "osx"
    public var arch = "arm64"
    public var osVersion: String
    public var features: [String: Bool]

    public init(osVersion: String = RuleEnvironment.currentOSVersion, features: [String: Bool] = [:]) {
        self.osVersion = osVersion
        self.features = features
    }

    public static var currentOSVersion: String {
        let v = ProcessInfo.processInfo.operatingSystemVersion
        return "\(v.majorVersion).\(v.minorVersion).\(v.patchVersion)"
    }

    func matches(_ os: OSRule) -> Bool {
        if let name = os.name, !(name == osName || (osName == "osx" && name == "macos")) { return false }
        if let arch = os.arch {
            let isArm = arch == "arm64" || arch == "aarch64"
            if isArm != (self.arch == "arm64") || arch == "x86" { return false }
        }
        if let pattern = os.version, osVersion.range(of: pattern, options: .regularExpression) == nil { return false }
        return true
    }

    /// Mojang semantics: no rules = allowed; otherwise disallowed unless the last matching rule allows.
    public func allows(_ rules: [Rule]?) -> Bool {
        guard let rules, !rules.isEmpty else { return true }
        var allowed = false
        for rule in rules {
            if let os = rule.os, !matches(os) { continue }
            if let f = rule.features, !f.allSatisfy({ features[$0.key, default: false] == $0.value }) { continue }
            allowed = rule.action == "allow"
        }
        return allowed
    }
}

public enum Argument: Codable, Sendable, Equatable {
    case plain(String)
    case conditional(rules: [Rule], values: [String])

    public init(from decoder: Decoder) throws {
        let c = try decoder.singleValueContainer()
        if let s = try? c.decode(String.self) { self = .plain(s); return }
        struct Obj: Decodable { var rules: [Rule]?; var value: Value }
        enum Value: Decodable {
            case one(String), many([String])
            init(from decoder: Decoder) throws {
                let c = try decoder.singleValueContainer()
                if let s = try? c.decode(String.self) { self = .one(s) } else { self = .many(try c.decode([String].self)) }
            }
        }
        let o = try c.decode(Obj.self)
        switch o.value {
        case .one(let s): self = .conditional(rules: o.rules ?? [], values: [s])
        case .many(let a): self = .conditional(rules: o.rules ?? [], values: a)
        }
    }

    public func encode(to encoder: Encoder) throws {
        var c = encoder.singleValueContainer()
        switch self {
        case .plain(let s): try c.encode(s)
        case .conditional(let rules, let values):
            struct Obj: Encodable { var rules: [Rule]; var value: [String] }
            try c.encode(Obj(rules: rules, value: values))
        }
    }

    public func resolve(_ env: RuleEnvironment) -> [String] {
        switch self {
        case .plain(let s): return [s]
        case .conditional(let rules, let values): return env.allows(rules) ? values : []
        }
    }
}

public struct Artifact: Codable, Sendable, Equatable {
    public var path: String?
    public var sha1: String?
    public var size: Int?
    public var url: String
}

public struct Library: Codable, Sendable, Equatable {
    public struct Downloads: Codable, Sendable, Equatable {
        public var artifact: Artifact?
        public var classifiers: [String: Artifact]?
    }
    public struct Extract: Codable, Sendable, Equatable { public var exclude: [String]? }

    public var name: String
    public var downloads: Downloads?
    public var url: String?
    public var sha1: String?
    public var size: Int?
    public var rules: [Rule]?
    public var natives: [String: String]?
    public var extract: Extract?

    public init(name: String, url: String? = nil) { self.name = name; self.url = url }
}

public struct AssetIndexRef: Codable, Sendable, Equatable {
    public var id: String
    public var sha1: String?
    public var size: Int?
    public var url: String
}

public struct JavaVersionReq: Codable, Sendable, Equatable {
    public var component: String
    public var majorVersion: Int
}

public struct LoggingConfig: Codable, Sendable, Equatable {
    public struct File: Codable, Sendable, Equatable { public var id: String; public var sha1: String?; public var size: Int?; public var url: String }
    public var argument: String
    public var file: File
}

public struct VersionJSON: Codable, Sendable, Equatable {
    public struct Arguments: Codable, Sendable, Equatable {
        public var game: [Argument]?
        public var jvm: [Argument]?
    }

    public var id: String
    public var inheritsFrom: String?
    public var type: String?
    public var mainClass: String?
    public var minecraftArguments: String?
    public var arguments: Arguments?
    public var libraries: [Library]?
    public var assetIndex: AssetIndexRef?
    public var assets: String?
    public var downloads: [String: Artifact]?
    public var javaVersion: JavaVersionReq?
    public var logging: [String: LoggingConfig]?

    /// Child (Fabric/Forge profile) layered over its parent (vanilla), like the official launcher's inheritsFrom.
    public func merged(over parent: VersionJSON) -> VersionJSON {
        var m = parent
        m.id = id
        m.inheritsFrom = nil
        m.type = type ?? parent.type
        m.mainClass = mainClass ?? parent.mainClass
        m.minecraftArguments = minecraftArguments ?? parent.minecraftArguments
        if arguments != nil || parent.arguments != nil {
            m.arguments = Arguments(
                game: (parent.arguments?.game ?? []) + (arguments?.game ?? []),
                jvm: (parent.arguments?.jvm ?? []) + (arguments?.jvm ?? []))
        }
        let mine = libraries ?? []
        let keys = Set(mine.map { MavenCoordinate($0.name)?.dedupKey ?? $0.name })
        m.libraries = mine + (parent.libraries ?? []).filter { !keys.contains(MavenCoordinate($0.name)?.dedupKey ?? $0.name) }
        m.assetIndex = assetIndex ?? parent.assetIndex
        m.assets = assets ?? parent.assets
        m.downloads = downloads ?? parent.downloads
        m.javaVersion = javaVersion ?? parent.javaVersion
        m.logging = logging ?? parent.logging
        return m
    }
}

public struct MavenCoordinate: Sendable, Equatable {
    public var group, artifact, version: String
    public var classifier: String?
    public var ext = "jar"

    public init?(_ name: String) {
        var n = Substring(name)
        if let at = n.lastIndex(of: "@") { ext = String(n[n.index(after: at)...]); n = n[..<at] }
        let p = n.split(separator: ":", omittingEmptySubsequences: false).map(String.init)
        guard p.count >= 3 else { return nil }
        group = p[0]; artifact = p[1]; version = p[2]
        classifier = p.count > 3 ? p[3] : nil
    }

    public func path(classifier override: String? = nil) -> String {
        let c = override ?? classifier
        return "\(group.replacingOccurrences(of: ".", with: "/"))/\(artifact)/\(version)/\(artifact)-\(version)\(c.map { "-\($0)" } ?? "").\(ext)"
    }

    /// Same library regardless of version: the child's copy wins when merging.
    public var dedupKey: String { "\(group):\(artifact):\(classifier ?? "")" }
}

/// A library file this launcher needs on disk.
public struct ResolvedLibrary: Sendable, Equatable {
    public var path: String          // relative to libraries/
    public var urls: [URL]           // tried in order
    public var sha1: String?
    public var size: Int?
    public var isNative: Bool        // old-style natives jar: extract, don't put on classpath
}

public enum LibraryResolver {
    public static let fallbackRepos = [
        "https://libraries.minecraft.net/",
        "https://maven.minecraftforge.net/",
        "https://repo1.maven.org/maven2/",
    ]

    /// Old Forge profiles still point at retired http://files.minecraftforge.net/maven/.
    static func normalizeRepo(_ s: String) -> String {
        var r = s.replacingOccurrences(of: "http://", with: "https://")
        r = r.replacingOccurrences(of: "https://files.minecraftforge.net/maven/", with: "https://maven.minecraftforge.net/")
        return r.hasSuffix("/") ? r : r + "/"
    }

    /// Apple Silicon replacements for 1.7.10–1.12.2 libraries whose macOS natives are x86_64-only: LWJGL 2 (liblwjgl +
    /// openal.dylib), JInput, and 1.12.2's java-objc-bridge/text2speech. Same artifacts and SHA-1s as Prism Launcher's
    /// meta (org.lwjgl 2.9.4-nightly-20150209 "natives-osx-arm64", net.minecraft 1.12.2 "osx-arm64" rules), which
    /// Modrinth App's meta uses too. Key: "group:artifact" → (version prefix it replaces, replacement).
    static let arm64Overrides: [String: (prefix: String, lib: Library)] = {
        let json = #"""
        [
          {"name": "org.lwjgl.lwjgl:lwjgl:2.9.4-nightly-20150209", "downloads": {"artifact": {
            "path": "org/lwjgl/lwjgl/lwjgl/2.9.4-nightly-20150209/lwjgl-2.9.4-nightly-20150209.jar",
            "sha1": "697517568c68e78ae0b4544145af031c81082dfe", "size": 1047168,
            "url": "https://libraries.minecraft.net/org/lwjgl/lwjgl/lwjgl/2.9.4-nightly-20150209/lwjgl-2.9.4-nightly-20150209.jar"}}},
          {"name": "org.lwjgl.lwjgl:lwjgl_util:2.9.4-nightly-20150209", "downloads": {"artifact": {
            "path": "org/lwjgl/lwjgl/lwjgl_util/2.9.4-nightly-20150209/lwjgl_util-2.9.4-nightly-20150209.jar",
            "sha1": "d51a7c040a721d13efdfbd34f8b257b2df882ad0", "size": 173887,
            "url": "https://libraries.minecraft.net/org/lwjgl/lwjgl/lwjgl_util/2.9.4-nightly-20150209/lwjgl_util-2.9.4-nightly-20150209.jar"}}},
          {"name": "org.lwjgl.lwjgl:lwjgl-platform:2.9.4-nightly-20150209", "natives": {"osx": "natives-osx-arm64"},
           "extract": {"exclude": ["META-INF/"]}, "downloads": {"classifiers": {"natives-osx-arm64": {
            "path": "org/lwjgl/lwjgl/lwjgl-platform/2.9.4-nightly-20150209/lwjgl-platform-2.9.4-nightly-20150209-natives-osx-arm64.jar",
            "sha1": "eff546c0b319d6ffc7a835652124c18089c67f36", "size": 488316,
            "url": "https://github.com/MinecraftMachina/lwjgl/releases/download/2.9.4-20150209-mmachina.2/lwjgl-platform-2.9.4-nightly-20150209-natives-osx.jar"}}}},
          {"name": "net.java.jinput:jinput-platform:2.0.5", "natives": {"osx": "natives-osx-arm64"},
           "extract": {"exclude": ["META-INF/"]}, "downloads": {"classifiers": {"natives-osx-arm64": {
            "path": "net/java/jinput/jinput-platform/2.0.5/jinput-platform-2.0.5-natives-osx-arm64.jar",
            "sha1": "5189eb40db3087fb11ca063b68fa4f4c20b199dd", "size": 10031,
            "url": "https://github.com/r58Playz/jinput-m1/raw/main/plugins/OSX/bin/jinput-platform-2.0.5.jar"}}}},
          {"name": "ca.weblite:java-objc-bridge:1.1.0-mmachina.1", "downloads": {"artifact": {
            "path": "ca/weblite/java-objc-bridge/1.1.0-mmachina.1/java-objc-bridge-1.1.0-mmachina.1.jar",
            "sha1": "369a83621e3c65496348491e533cb97fe5f2f37d", "size": 91947,
            "url": "https://github.com/MinecraftMachina/Java-Objective-C-Bridge/releases/download/1.1.0-mmachina.1/java-objc-bridge-1.1.jar"}}},
          {"name": "com.mojang:text2speech:1.11.3", "downloads": {"artifact": {
            "path": "com/mojang/text2speech/1.11.3/text2speech-1.11.3.jar",
            "sha1": "f378f889797edd7df8d32272c06ca80a1b6b0f58", "size": 13164,
            "url": "https://libraries.minecraft.net/com/mojang/text2speech/1.11.3/text2speech-1.11.3.jar"}}}
        ]
        """#
        let libs = try! JSONDecoder().decode([Library].self, from: Data(json.utf8))
        let prefixes = ["lwjgl": "2.", "lwjgl_util": "2.", "lwjgl-platform": "2.", "jinput-platform": "2.",
                        "java-objc-bridge": "1.0.", "text2speech": "1.10."]
        return Dictionary(uniqueKeysWithValues: libs.map { lib in
            let c = MavenCoordinate(lib.name)!
            return ("\(c.group):\(c.artifact)", (prefixes[c.artifact]!, lib))
        })
    }()

    /// The arm64 replacement for a library that passed its rules, or the library itself.
    static func arm64Override(_ lib: Library, env: RuleEnvironment) -> Library {
        guard env.osName == "osx", env.arch == "arm64", let c = MavenCoordinate(lib.name),
              let o = arm64Overrides["\(c.group):\(c.artifact)"], c.version.hasPrefix(o.prefix) else { return lib }
        return o.lib
    }

    public static func resolve(_ libs: [Library], env: RuleEnvironment) -> [ResolvedLibrary] {
        var out: [ResolvedLibrary] = []
        // Mojang's twitch-platform natives are x86_64-only and unused (Twitch streaming is long gone): skipped on arm64.
        let isArm = env.osName == "osx" && env.arch == "arm64"
        for original in libs where env.allows(original.rules) && !(isArm && original.name.hasPrefix("tv.twitch:twitch-platform:")) {
            let lib = arm64Override(original, env: env)
            guard let coord = MavenCoordinate(lib.name) else { continue }
            let repos = ([lib.url.map(normalizeRepo)].compactMap { $0 } + fallbackRepos)
            func fromRepos(_ path: String) -> [URL] {
                var seen = Set<String>()
                return repos.filter { seen.insert($0).inserted }.compactMap { URL(string: $0 + path) }
            }

            if let nativeKey = lib.natives?[env.osName] {
                let classifier = nativeKey.replacingOccurrences(of: "${arch}", with: "64")
                if let a = lib.downloads?.classifiers?[classifier] {
                    out.append(ResolvedLibrary(path: a.path ?? coord.path(classifier: classifier), urls: [URL(string: a.url)].compactMap { $0 },
                                               sha1: a.sha1, size: a.size, isNative: true))
                } else {
                    let p = coord.path(classifier: classifier)
                    out.append(ResolvedLibrary(path: p, urls: fromRepos(p), sha1: nil, size: nil, isNative: true))
                }
                // Natives-only entries (LWJGL 2 "-platform" libs) carry no main artifact.
                guard let a = lib.downloads?.artifact else { continue }
                out.append(ResolvedLibrary(path: a.path ?? coord.path(), urls: [URL(string: a.url)].compactMap { $0 }, sha1: a.sha1, size: a.size, isNative: false))
                continue
            }

            if let a = lib.downloads?.artifact {
                // Empty url = shipped inside an installer (Forge's own jar); the installer step puts it in place.
                out.append(ResolvedLibrary(path: a.path ?? coord.path(), urls: a.url.isEmpty ? [] : [URL(string: a.url)].compactMap { $0 },
                                           sha1: a.sha1, size: a.size, isNative: false))
            } else {
                let p = coord.path()
                out.append(ResolvedLibrary(path: p, urls: fromRepos(p), sha1: lib.sha1, size: lib.size, isNative: false))
            }
        }
        var seen = Set<String>()   // two originals can map to one override (java-objc-bridge has a natives and a plain entry)
        return out.filter { seen.insert($0.path).inserted }
    }
}
