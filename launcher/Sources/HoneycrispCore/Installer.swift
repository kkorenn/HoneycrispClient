import Foundation

public struct InstallOptions: Sendable {
    /// Developer override: take Graft / Graft Shaders / Honeycrisp jars from this folder instead of GitHub Releases.
    public var localJars: URL?
    public var sodium: Bool
    public var javaOverride: String?

    public init(localJars: URL?, sodium: Bool, javaOverride: String?) {
        self.localJars = localJars; self.sodium = sodium; self.javaOverride = javaOverride
    }
}

/// Written after a successful install; its absence means "Install", a fingerprint change means "Update".
public struct InstallStamp: Codable, Sendable {
    public static let revision = 2   // 2: arm64 LWJGL 2 natives + Zulu fallback; older legacy installs must re-run
    public var revision: Int
    public var versionID: String
    public var javaPath: String
    public var managedMods: [String]
    public var fingerprint: String
    public var warnings: [String]
    public var jars: [ModJar]?   // optional so stamps written before GitHub downloads still decode
}

public struct VersionManifest: Decodable, Sendable {
    public struct Entry: Decodable, Sendable, Identifiable {
        public var id: String
        public var type: String
        public var url: String
        public var sha1: String?
        public var releaseTime: Date
    }
    public struct Latest: Decodable, Sendable { public var release: String; public var snapshot: String }
    public var latest: Latest
    public var versions: [Entry]

    public static func decode(_ d: Data) throws -> VersionManifest {
        let dec = JSONDecoder()
        dec.dateDecodingStrategy = .iso8601
        return try dec.decode(VersionManifest.self, from: d)
    }
}

/// Mojang's java-runtime index: platform → component → builds.
public struct JavaRuntimeIndex: Decodable, Sendable {
    public struct Build: Decodable, Sendable {
        public struct Ref: Decodable, Sendable { public var sha1: String; public var size: Int; public var url: String }
        public struct Version: Decodable, Sendable { public var name: String }
        public var manifest: Ref
        public var version: Version
    }
    public var platforms: [String: [String: [Build]]]

    public init(from decoder: Decoder) throws { platforms = try decoder.singleValueContainer().decode([String: [String: [Build]]].self) }

    /// "1.8.0_74" → 8, "8u51" → 8, "21.0.7" → 21, "25" → 25.
    public static func major(_ name: String) -> Int {
        let s = name.hasPrefix("1.") ? String(name.dropFirst(2)) : name
        return Int(s.prefix { $0.isNumber }) ?? 0
    }

    /// The component the version JSON asks for if it is in range, else the oldest one in `minMajor...maxMajor`.
    public func pick(platform: String, component: String, minMajor: Int, maxMajor: Int = .max) -> (component: String, build: Build)? {
        let comps = platforms[platform] ?? [:]
        let ok = { (b: Build) in (minMajor...maxMajor).contains(Self.major(b.version.name)) }
        if let b = comps[component]?.first, ok(b) { return (component, b) }
        return comps.compactMap { k, v in v.first.map { (k, $0) } }
            .filter { ok($0.1) }
            .min { Self.major($0.1.version.name) < Self.major($1.1.version.name) }
            .map { (component: $0.0, build: $0.1) }
    }
}

/// Azul's metadata API: the Zulu JRE fallback when Mojang has no mac-os-arm64 runtime (always the case for Java 8).
public enum Azul {
    public struct Package: Decodable, Sendable {
        public var package_uuid: String
        public var name: String
        public var download_url: String
        public var java_version: [Int]
    }
    public struct Detail: Decodable, Sendable {
        public var name: String
        public var download_url: String
        public var sha256_hash: String
        public var size: Int?
    }

    public static func packagesURL(major: Int) -> URL {
        var c = URLComponents(string: "https://api.azul.com/metadata/v1/zulu/packages/")!
        c.queryItems = [("java_version", "\(major)"), ("os", "macos"), ("arch", "aarch64"), ("java_package_type", "jre"),
                        ("archive_type", "zip"), ("javafx_bundled", "false"), ("crac_supported", "false"),
                        ("latest", "true"), ("release_status", "ga")].map { URLQueryItem(name: $0.0, value: $0.1) }
        return c.url!
    }

    public static func detailURL(_ uuid: String) -> URL {
        URL(string: "https://api.azul.com/metadata/v1/zulu/packages/")!.appending(path: uuid)
    }

    /// "latest=true" still returns one package per distro line; take the newest Java update of the requested major.
    public static func newest(_ packages: [Package], major: Int) -> Package? {
        packages.filter { $0.java_version.first == major && $0.name.hasSuffix(".zip") }
            .max { $0.java_version.lexicographicallyPrecedes($1.java_version) }
    }
}

public struct InstallError: LocalizedError {
    public var message: String
    public init(_ m: String) { message = m }
    public var errorDescription: String? { message }
}

public actor Installer {
    public typealias Progress = @Sendable (_ fraction: Double, _ message: String) -> Void
    public nonisolated let paths: Paths
    let env = RuleEnvironment()

    public init(paths: Paths = Paths()) { self.paths = paths }

    // MARK: State

    public nonisolated func stamp(_ mc: String) -> InstallStamp? {
        guard let d = try? Data(contentsOf: paths.stamp(mc)), let s = try? JSONDecoder().decode(InstallStamp.self, from: d),
              s.revision == InstallStamp.revision else { return nil }
        return s
    }

    /// Local-only check (no network), cheap enough for every UI refresh. Newer GitHub releases are compared
    /// separately (`GitHub.updateAvailable`) against releases fetched on launch/refresh.
    public nonisolated func needsUpdate(_ spec: InstanceSpec, _ o: InstallOptions) -> Bool {
        guard let s = stamp(spec.mc) else { return true }
        let modsDir = paths.modsDir(spec.id)
        let fm = FileManager.default
        if !s.managedMods.allSatisfy({ fm.fileExists(atPath: modsDir.appending(path: $0).path) }) {
            return true
        }
        let jars = o.localJars.map { localModJars(spec.mc, $0) } ?? s.jars ?? []
        return s.fingerprint != fingerprint(spec, o, jars: jars)
    }

    nonisolated func fingerprint(_ spec: InstanceSpec, _ o: InstallOptions, jars: [ModJar]) -> String {
        let source = o.localJars.map { distJars(spec.mc, [$0]).map { u in
            let m = (try? u.resourceValues(forKeys: [.contentModificationDateKey]).contentModificationDate)?.timeIntervalSince1970 ?? 0
            return "\(u.lastPathComponent)@\(Int(m))"
        } } ?? ["github"]
        return (source + ["sodium=\(wantsSodium(spec, o, jars: jars))", "java=\(o.javaOverride ?? "")"]).joined(separator: ";")
    }

    /// Sodium whenever Graft Shaders is installed (it requires it), opt-in otherwise; only where Catalog pins a build.
    nonisolated func wantsSodium(_ spec: InstanceSpec, _ o: InstallOptions, jars: [ModJar]) -> Bool {
        spec.sodiumSupported && (o.sodium || jars.contains { $0.project.hasPrefix("graftshaders") })
    }

    nonisolated func localModJars(_ mc: String, _ folder: URL) -> [ModJar] {
        distJars(mc, [folder]).map { ModJar(project: Catalog.family($0.lastPathComponent), file: $0.lastPathComponent, tag: nil, source: folder.path) }
    }

    /// Graft / Graft Shaders / Honeycrisp Client jars for this version, newest per family.
    public nonisolated func distJars(_ mc: String, _ folders: [URL]) -> [URL] {
        var best: [String: (URL, Date)] = [:]
        for folder in folders {
            let files = (try? FileManager.default.contentsOfDirectory(at: folder, includingPropertiesForKeys: [.contentModificationDateKey])) ?? []
            for f in files where Catalog.jar(f.lastPathComponent, matches: mc) {
                let date = (try? f.resourceValues(forKeys: [.contentModificationDateKey]).contentModificationDate) ?? .distantPast
                let fam = Catalog.family(f.lastPathComponent)
                if best[fam].map({ date > $0.1 }) ?? true { best[fam] = (f, date) }
            }
        }
        return best.values.map(\.0).sorted { $0.lastPathComponent < $1.lastPathComponent }
    }

    // MARK: Install

    public func install(_ spec: InstanceSpec, _ o: InstallOptions, progress: @escaping Progress) async throws -> InstallStamp {
        let fm = FileManager.default
        let game = paths.instance(spec.mc)
        for d in ["mods", "config", "saves", "resourcepacks", "shaderpacks"] {
            try fm.createDirectory(at: game.appending(path: d), withIntermediateDirectories: true)
        }
        let options = game.appending(path: "options.txt")
        if !fm.fileExists(atPath: options.path) { try Data().write(to: options) }
        var warnings: [String] = []

        progress(0.02, "Fetching Minecraft \(spec.mc)…")
        let manifest = try VersionManifest.decode(try await HTTP.data(URLRequest(url: Catalog.versionManifest)))
        guard let entry = manifest.versions.first(where: { $0.id == spec.mc }), let url = URL(string: entry.url) else {
            throw InstallError("Minecraft \(spec.mc) is not in Mojang's version manifest.")
        }
        try await Downloader.run([DownloadItem(url: url, dest: paths.versionJSON(spec.mc), sha1: entry.sha1)])
        let vanilla = try load(spec.mc)

        progress(0.06, "Installing \(spec.loader.rawValue)…")
        let child: VersionJSON
        var installerJar: URL?
        switch spec.loader {
        case .fabric: child = try await installFabric(spec.mc)
        case .forge: (child, installerJar) = try await installForge(spec)
        }
        let version = child.merged(over: vanilla)

        // Libraries + client jar + logging config
        let libs = LibraryResolver.resolve(version.libraries ?? [], env: env)
        if let installerJar {
            for lib in libs where lib.urls.isEmpty {
                let dest = paths.libraries.appending(path: lib.path)
                if !fm.fileExists(atPath: dest.path) { try unzip(installerJar, member: "maven/\(lib.path)", to: dest) }
            }
        }
        var items = libs.filter { !$0.urls.isEmpty }.map {
            DownloadItem(urls: $0.urls, dest: paths.libraries.appending(path: $0.path), sha1: $0.sha1, size: $0.size)
        }
        guard let client = vanilla.downloads?["client"], let clientURL = URL(string: client.url) else { throw InstallError("No client download for \(spec.mc).") }
        items.append(DownloadItem(url: clientURL, dest: paths.clientJar(spec.mc), sha1: client.sha1, size: client.size))
        if let log = version.logging?["client"], let u = URL(string: log.file.url) {
            items.append(DownloadItem(url: u, dest: paths.assets.appending(path: "log_configs/\(log.file.id)"), sha1: log.file.sha1, size: log.file.size))
        }
        try await Downloader.run(items) { d, t in progress(0.08 + 0.2 * Double(d) / Double(t), "Libraries \(d)/\(t)") }
        for lib in libs where !fm.fileExists(atPath: paths.libraries.appending(path: lib.path).path) {
            throw InstallError("Missing library \(lib.path)")
        }

        // Assets
        guard let ai = version.assetIndex, let aiURL = URL(string: ai.url) else { throw InstallError("No asset index for \(spec.mc).") }
        let indexFile = paths.assets.appending(path: "indexes/\(ai.id).json")
        try await Downloader.run([DownloadItem(url: aiURL, dest: indexFile, sha1: ai.sha1, size: ai.size)])
        struct AssetIndex: Decodable { struct O: Decodable { var hash: String; var size: Int }; var objects: [String: O] }
        // ponytail: "virtual"/"map_to_resources" indexes (pre-1.7.3) are not handled; every supported version uses objects/.
        let objects = try JSONDecoder().decode(AssetIndex.self, from: Data(contentsOf: indexFile)).objects.values
        let sizes = Dictionary(objects.map { ($0.hash, $0.size) }, uniquingKeysWith: { a, _ in a })
        let assetItems = sizes.compactMap { h, size -> DownloadItem? in
            let p = "\(h.prefix(2))/\(h)"
            return URL(string: "https://resources.download.minecraft.net/\(p)").map {
                DownloadItem(url: $0, dest: paths.assets.appending(path: "objects/\(p)"), sha1: h, size: size)
            }
        }
        try await Downloader.run(assetItems, concurrency: 32) { d, t in progress(0.28 + 0.4 * Double(d) / Double(t), "Assets \(d)/\(t)") }

        // Java
        let javaPath: String
        if let custom = o.javaOverride, !custom.isEmpty {
            guard fm.isExecutableFile(atPath: custom) else { throw InstallError("Java override \(custom) is not an executable.") }
            javaPath = custom
        } else {
            let need = max(version.javaVersion?.majorVersion ?? 8, spec.minJava)
            // LaunchWrapper-era Forge breaks on Java 9+, so 8 means exactly 8.
            javaPath = try await installJava(component: version.javaVersion?.component ?? "jre-legacy", minMajor: need,
                                             maxMajor: need == 8 ? 8 : .max) { f, m in
                progress(0.68 + 0.17 * f, m)
            }
        }

        // Mods
        progress(0.86, "Installing mods…")
        let (managed, jars) = try await installMods(spec, o, previous: stamp(spec.mc), warnings: &warnings)

        let s = InstallStamp(revision: InstallStamp.revision, versionID: child.id, javaPath: javaPath, managedMods: managed,
                             fingerprint: fingerprint(spec, o, jars: jars), warnings: warnings, jars: jars)
        try fm.createDirectory(at: paths.stamp(spec.mc).deletingLastPathComponent(), withIntermediateDirectories: true)
        try JSONEncoder().encode(s).write(to: paths.stamp(spec.mc))
        progress(1, "Ready")
        return s
    }

    func load(_ id: String) throws -> VersionJSON {
        try JSONDecoder().decode(VersionJSON.self, from: Data(contentsOf: paths.versionJSON(id)))
    }

    /// Follows inheritsFrom down to vanilla.
    public func loadMerged(_ id: String) throws -> VersionJSON {
        let v = try load(id)
        guard let parent = v.inheritsFrom else { return v }
        return v.merged(over: try loadMerged(parent))
    }

    func save(_ data: Data, id: String) throws {
        let f = paths.versionJSON(id)
        try FileManager.default.createDirectory(at: f.deletingLastPathComponent(), withIntermediateDirectories: true)
        try data.write(to: f)
    }

    // MARK: Fabric (meta.fabricmc.net profile = a version JSON that inheritsFrom vanilla)

    func installFabric(_ mc: String) async throws -> VersionJSON {
        struct Entry: Decodable { struct L: Decodable { var version: String; var stable: Bool? }; var loader: L }
        let entries: [Entry] = try await HTTP.get(URL(string: "https://meta.fabricmc.net/v2/versions/loader/\(mc)")!)
        guard let loader = (entries.first { $0.loader.stable ?? false } ?? entries.first)?.loader.version else {
            throw InstallError("Fabric has no loader for \(mc) yet.")
        }
        let data = try await HTTP.data(URLRequest(url: URL(string: "https://meta.fabricmc.net/v2/versions/loader/\(mc)/\(loader)/profile/json")!))
        var v = try JSONDecoder().decode(VersionJSON.self, from: data)
        v.inheritsFrom = v.inheritsFrom ?? mc
        try save(try JSONEncoder().encode(v), id: v.id)
        return v
    }

    // MARK: Forge
    //
    // We do not run the Forge installer. 1.7.10/1.8.9 installers are the v1 format (no headless client install at all) and
    // the 1.12.2 one is v2 with no client processors, so reading install_profile.json is enough for all three:
    //  v1: versionInfo is the version JSON; install.filePath is the universal jar → libraries/<install.path>.
    //  v2: "json" names the version JSON inside the jar; Forge's own jar sits under maven/ in the installer.
    // A profile with client-side processors (1.13+) is rejected rather than half-installed.

    struct ForgeProfile: Decodable {
        struct Install: Decodable { var path: String; var filePath: String; var minecraft: String }
        struct Processor: Decodable { var sides: [String]? }
        var install: Install?
        var versionInfo: VersionJSON?
        var json: String?
        var processors: [Processor]?
    }

    func installForge(_ spec: InstanceSpec) async throws -> (VersionJSON, URL) {
        guard let full = spec.forge else { throw InstallError("No Forge version for \(spec.mc).") }
        let jar = paths.cache.appending(path: "forge-\(full)-installer.jar")
        try await Downloader.run([DownloadItem(url: Catalog.forgeInstaller(full), dest: jar)])
        let profile = try JSONDecoder().decode(ForgeProfile.self, from: unzipData(jar, member: "install_profile.json"))

        var v: VersionJSON
        if let install = profile.install, let info = profile.versionInfo {
            v = info
            guard let coord = MavenCoordinate(install.path) else { throw InstallError("Bad Forge path \(install.path)") }
            let dest = paths.libraries.appending(path: coord.path())
            if !FileManager.default.fileExists(atPath: dest.path) { try unzip(jar, member: install.filePath, to: dest) }
            v.inheritsFrom = install.minecraft
        } else if let json = profile.json {
            if (profile.processors ?? []).contains(where: { $0.sides?.contains("client") ?? true }) {
                throw InstallError("Forge \(full) needs installer processors; only v1/processor-less profiles are supported.")
            }
            v = try JSONDecoder().decode(VersionJSON.self, from: unzipData(jar, member: String(json.drop { $0 == "/" })))
            v.inheritsFrom = v.inheritsFrom ?? spec.mc
        } else {
            throw InstallError("Unrecognised Forge installer format for \(full).")
        }
        try save(try JSONEncoder().encode(v), id: v.id)
        return (v, jar)
    }

    // MARK: Java (Mojang runtime, mac-os-arm64; Azul Zulu when Mojang has none)

    func installJava(component: String, minMajor: Int, maxMajor: Int,
                     progress: @escaping @Sendable (Double, String) -> Void) async throws -> String {
        let index: JavaRuntimeIndex = try await HTTP.get(Catalog.javaRuntimes)
        guard let (comp, build) = index.pick(platform: Catalog.runtimePlatform, component: component, minMajor: minMajor, maxMajor: maxMajor) else {
            return try await installZulu(major: minMajor, progress: progress)
        }
        struct Files: Decodable {
            struct F: Decodable {
                struct D: Decodable { var raw: Artifact }
                var type: String; var executable: Bool?; var downloads: D?; var target: String?
            }
            var files: [String: F]
        }
        guard let manifestURL = URL(string: build.manifest.url) else { throw URLError(.badURL) }
        let files: Files = try await HTTP.get(manifestURL)
        let root = paths.runtimes.appending(path: comp)
        let fm = FileManager.default
        var items: [DownloadItem] = []
        for (path, f) in files.files {
            let dest = root.appending(path: path)
            switch f.type {
            case "directory": try fm.createDirectory(at: dest, withIntermediateDirectories: true)
            case "file":
                if let raw = f.downloads?.raw, let u = URL(string: raw.url) { items.append(DownloadItem(url: u, dest: dest, sha1: raw.sha1, size: raw.size)) }
            default: break
            }
        }
        try await Downloader.run(items) { d, t in progress(Double(d) / Double(t), "Java \(build.version.name) \(d)/\(t)") }
        for (path, f) in files.files {
            let dest = root.appending(path: path)
            if f.type == "link", let target = f.target, (try? fm.destinationOfSymbolicLink(atPath: dest.path)) == nil {
                try fm.createDirectory(at: dest.deletingLastPathComponent(), withIntermediateDirectories: true)
                try fm.createSymbolicLink(atPath: dest.path, withDestinationPath: target)
            }
            if f.executable == true { try fm.setAttributes([.posixPermissions: 0o755], ofItemAtPath: dest.path) }
        }
        let java = root.appending(path: "jre.bundle/Contents/Home/bin/java")
        guard fm.isExecutableFile(atPath: java.path) else { throw InstallError("Java runtime \(comp) has no bin/java.") }
        return java.path
    }

    func installZulu(major: Int, progress: @escaping @Sendable (Double, String) -> Void) async throws -> String {
        let fail = "No arm64 Java \(major) from Mojang or Azul. Choose a JDK in Settings → Java (an arm64 JDK \(major))."
        let packages: [Azul.Package] = try await HTTP.get(Azul.packagesURL(major: major))
        guard let pkg = Azul.newest(packages, major: major) else { throw InstallError(fail) }
        let fm = FileManager.default
        let home = paths.runtimes.appending(path: "zulu-\(major)/\(pkg.name.dropLast(4))")
        if let java = Self.findJava(in: home) { return java }

        let detail: Azul.Detail = try await HTTP.get(Azul.detailURL(pkg.package_uuid))
        guard let url = URL(string: detail.download_url) else { throw InstallError(fail) }
        let zip = paths.cache.appending(path: detail.name)
        progress(0, "Downloading Azul Zulu \(pkg.java_version.map(String.init).joined(separator: "."))…")
        try await Downloader.run([DownloadItem(url: url, dest: zip, size: detail.size)])
        guard Hash.sha256(file: zip) == detail.sha256_hash.lowercased() else {
            try? fm.removeItem(at: zip)
            throw InstallError("Azul Zulu download \(detail.name) failed its SHA-256 check.")
        }
        progress(0.9, "Unpacking Java \(major)…")
        let tmp = home.deletingLastPathComponent().appending(path: ".unpack-\(UUID().uuidString)")
        defer { try? fm.removeItem(at: tmp) }
        try fm.createDirectory(at: tmp, withIntermediateDirectories: true)
        try runUnzip(["-q", "-o", zip.path, "-d", tmp.path])
        try? fm.removeItem(at: home)
        try fm.moveItem(at: tmp, to: home)
        try? fm.removeItem(at: zip)
        guard let java = Self.findJava(in: home) else { throw InstallError("Azul Zulu \(detail.name) has no bin/java.") }
        return java
    }

    /// Zulu zips are "<name>/Contents/Home/bin/java" (8) or "<name>/zulu-N.jre/Contents/Home/bin/java" (newer).
    static func findJava(in root: URL) -> String? {
        let fm = FileManager.default
        guard let e = fm.enumerator(at: root, includingPropertiesForKeys: nil) else { return nil }
        return e.compactMap { ($0 as? URL)?.path }
            .filter { $0.hasSuffix("/Contents/Home/bin/java") && fm.isExecutableFile(atPath: $0) }
            .min { $0.count < $1.count }
    }

    // MARK: Mods

    func installMods(_ spec: InstanceSpec, _ o: InstallOptions, previous: InstallStamp?,
                     warnings: inout [String]) async throws -> (managed: [String], jars: [ModJar]) {
        let fm = FileManager.default
        let mods = paths.mods(spec.mc)
        var jars: [ModJar] = []

        if let folder = o.localJars {
            jars = localModJars(spec.mc, folder)
            if !jars.contains(where: { $0.project == "graft" }) { warnings.append("No Graft jar for \(spec.mc) in \(folder.path).") }
            for jar in jars {
                let dest = mods.appending(path: jar.file)
                try? fm.removeItem(at: dest)
                try fm.copyItem(at: folder.appending(path: jar.file), to: dest)
            }
        } else {
            // Stamps from before GitHub downloads only list file names; their families still identify the project.
            let before = previous?.jars ?? (previous?.managedMods ?? []).map {
                ModJar(project: Catalog.family($0), file: $0, tag: nil, source: "earlier install")
            }
            for p in GitHub.Project.allCases {
                let old = before.first { $0.project == p.rawValue || $0.project.hasPrefix(p.rawValue + "-") }
                do {
                    let release = try await GitHub.latest(p)
                    guard let asset = release.jar(p, mc: spec.mc) else {
                        // Graft Shaders only exists for some versions; that is expected, not a warning.
                        if p != .graftshaders { warnings.append("\(p.title) \(release.tag_name) has no jar for \(spec.mc).") }
                        continue
                    }
                    guard let url = URL(string: asset.browser_download_url) else { throw InstallError("Bad download URL for \(asset.name)") }
                    let dest = mods.appending(path: asset.name)
                    try await Downloader.run([DownloadItem(url: url, dest: dest, size: asset.size, checksum: asset.checksum)])
                    if asset.checksum == nil, (try? fm.attributesOfItem(atPath: dest.path)[.size] as? Int) != asset.size {
                        try? fm.removeItem(at: dest)
                        throw InstallError("\(asset.name) has the wrong size")
                    }
                    jars.append(ModJar(project: p.rawValue, file: asset.name, tag: release.tag_name, source: p.repo))
                } catch {
                    // Non-fatal: keep whatever this instance already has.
                    warnings.append("\(p.title): \(GitHub.describe(error))" + (old.map { "; kept \($0.file)" } ?? ""))
                    if let old { jars.append(old) }
                }
            }
        }
        var managed = jars.map(\.file)
        // A failed Modrinth fetch must not delete the copy that's already installed.
        func keepPrevious(_ prefix: String) {
            managed += (previous?.managedMods ?? []).filter { $0.hasPrefix(prefix) }
        }

        if spec.loader == .fabric {
            do {
                let f = try await Modrinth.latestFile(slug: "fabric-api", mc: spec.mc, loader: "fabric")
                try await Downloader.run([DownloadItem(url: f.url, dest: mods.appending(path: f.filename), sha1: f.sha1)])
                managed.append(f.filename)
            } catch {
                warnings.append("fabric-api: \(error.localizedDescription)")
                keepPrevious("fabric-api-")
            }
            if let id = spec.sodium, wantsSodium(spec, o, jars: jars) {
                do {
                    let f = try await Modrinth.pinnedFile(id: id, mc: spec.mc)
                    try await Downloader.run([f.downloadItem(to: mods.appending(path: f.filename))])
                    managed.append(f.filename)
                } catch {
                    warnings.append("sodium: \(error.localizedDescription)")
                    keepPrevious("sodium-")
                }
            }
        }

        // Replace older launcher-installed jars; jars the user added are never in `managedMods`.
        for name in previous?.managedMods ?? [] where !managed.contains(name) { try? fm.removeItem(at: mods.appending(path: name)) }
        return (managed, jars)
    }

    // MARK: Launch

    public struct Prepared: Sendable {
        public var version: VersionJSON
        public var classpath: [String]
        public var javaPath: String
        public var loggingConfig: String?
    }

    /// Re-resolves libraries from the saved JSONs and extracts old-style natives. No network.
    public func prepareLaunch(_ spec: InstanceSpec) throws -> Prepared {
        guard let s = stamp(spec.mc) else { throw InstallError("\(spec.mc) is not installed.") }
        let version = try loadMerged(s.versionID)
        let libs = LibraryResolver.resolve(version.libraries ?? [], env: env)
        let natives = paths.natives(spec.mc)
        let fm = FileManager.default
        try? fm.removeItem(at: natives)
        try fm.createDirectory(at: natives, withIntermediateDirectories: true)
        // LWJGL 2 natives (1.7.10–1.12.2) come from LibraryResolver's arm64 overrides (liblwjgl, openal, jinput), so
        // LWJGL's OpenAL works for Graft's legacy module, which replaces only Display/Keyboard/Mouse.
        for lib in libs where lib.isNative {
            try runUnzip(["-o", "-q", paths.libraries.appending(path: lib.path).path, "-x", "META-INF/*", "-d", natives.path])
        }
        let log = version.logging?["client"].map { paths.assets.appending(path: "log_configs/\($0.file.id)").path }
        var seen = Set<String>()
        let cp = libs.filter { !$0.isNative }.map { paths.libraries.appending(path: $0.path).path }.filter { seen.insert($0).inserted }
        return Prepared(version: version, classpath: cp, javaPath: s.javaPath, loggingConfig: log)
    }

    // MARK: zip (via /usr/bin/unzip, always present on macOS)

    func unzipData(_ zip: URL, member: String) throws -> Data {
        let p = Process()
        p.executableURL = URL(filePath: "/usr/bin/unzip")
        p.arguments = ["-p", zip.path, member]
        let out = Pipe()
        p.standardOutput = out
        p.standardError = FileHandle.nullDevice
        try p.run()
        let d = out.fileHandleForReading.readDataToEndOfFile()
        p.waitUntilExit()
        guard p.terminationStatus == 0, !d.isEmpty else { throw InstallError("\(member) not found in \(zip.lastPathComponent)") }
        return d
    }

    func unzip(_ zip: URL, member: String, to dest: URL) throws {
        let d = try unzipData(zip, member: member)
        try FileManager.default.createDirectory(at: dest.deletingLastPathComponent(), withIntermediateDirectories: true)
        try d.write(to: dest)
    }

    func runUnzip(_ args: [String]) throws {
        let p = Process()
        p.executableURL = URL(filePath: "/usr/bin/unzip")
        p.arguments = args
        p.standardOutput = FileHandle.nullDevice
        p.standardError = FileHandle.nullDevice
        try p.run()
        p.waitUntilExit()
        // 11 = "nothing matched" (natives jar that is only META-INF); fine.
        guard [0, 1, 11].contains(p.terminationStatus) else { throw InstallError("unzip failed (\(p.terminationStatus)) for \(args.joined(separator: " "))") }
    }
}

public enum Modrinth {
    public struct File: Sendable {
        public var url: URL
        public var filename: String
        public var sha1: String?
        public var sha512: String? = nil
        public var size: Int? = nil

        /// SHA-512 when Modrinth gives one, else SHA-1.
        public func downloadItem(to dest: URL) -> DownloadItem {
            DownloadItem(url: url, dest: dest, sha1: sha512 == nil ? sha1 : nil, size: size, checksum: sha512.map(Checksum.sha512))
        }
    }

    public struct Version: Decodable, Sendable {
        public struct F: Decodable, Sendable { var url: String; var filename: String; var primary: Bool; var hashes: [String: String]; var size: Int? }
        public var id: String
        public var game_versions: [String]
        public var files: [F]
    }

    public static func versionURL(_ id: String) -> URL { URL(string: "https://api.modrinth.com/v2/version/\(id)")! }

    /// The primary file of a pinned version; refuses a version that does not list `mc`.
    public static func file(_ v: Version, mc: String) throws -> File {
        guard v.game_versions.contains(mc) else { throw InstallError("Modrinth version \(v.id) does not support \(mc)") }
        guard let f = v.files.first(where: \.primary) ?? v.files.first, let u = URL(string: f.url) else {
            throw InstallError("Modrinth version \(v.id) has no files")
        }
        return File(url: u, filename: f.filename, sha1: f.hashes["sha1"], sha512: f.hashes["sha512"], size: f.size)
    }

    public static func pinnedFile(id: String, mc: String) async throws -> File { try file(try await HTTP.get(versionURL(id)), mc: mc) }

    public static func versionsURL(slug: String, mc: String, loader: String) -> URL {
        var c = URLComponents(string: "https://api.modrinth.com/v2/project/\(slug)/version")!
        c.queryItems = [.init(name: "game_versions", value: "[\"\(mc)\"]"), .init(name: "loaders", value: "[\"\(loader)\"]")]
        return c.url!
    }

    public static func latestFile(slug: String, mc: String, loader: String) async throws -> File {
        struct V: Decodable {
            struct F: Decodable { var url: String; var filename: String; var primary: Bool; var hashes: [String: String] }
            var version_type: String; var files: [F]
        }
        let versions: [V] = try await HTTP.get(versionsURL(slug: slug, mc: mc, loader: loader))
        guard let v = versions.first(where: { $0.version_type == "release" }) ?? versions.first,
              let f = v.files.first(where: \.primary) ?? v.files.first, let u = URL(string: f.url) else {
            throw InstallError("no \(loader) build for \(mc) on Modrinth")
        }
        return File(url: u, filename: f.filename, sha1: f.hashes["sha1"])
    }
}
