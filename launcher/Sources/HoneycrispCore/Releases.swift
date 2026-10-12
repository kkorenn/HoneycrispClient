import Foundation

/// A Graft / Graft Shaders / Honeycrisp jar the launcher put into an instance, and where it came from.
public struct ModJar: Codable, Sendable, Hashable {
    public var project: String   // GitHub.Project raw value, or the jar family for a local folder
    public var file: String
    public var tag: String?      // GitHub release tag; nil = local folder
    public var source: String    // "kkorenn/Graft" or the local folder path

    public init(project: String, file: String, tag: String?, source: String) {
        self.project = project; self.file = file; self.tag = tag; self.source = source
    }

    /// "graft-1.21.11-1.0.0.jar" → "1.0.0".
    public var version: String { file.replacingOccurrences(of: ".jar", with: "").split(separator: "-").last.map(String.init) ?? file }
    public var sourceDescription: String { tag.map { "GitHub \(source) \($0)" } ?? "Local folder \(source)" }
}

/// Latest releases of the three project repos (public, unauthenticated REST API: 60 requests/hour per IP).
public enum GitHub {
    public enum Project: String, CaseIterable, Sendable {
        case graft, graftshaders, honeycrisp

        public var repo: String {
            switch self {
            case .graft: "kkorenn/Graft"
            case .graftshaders: "kkorenn/GraftShaders"
            case .honeycrisp: "kkorenn/HoneycrispClient"
            }
        }
        public var title: String {
            switch self {
            case .graft: "Graft"
            case .graftshaders: "Graft Shaders"
            case .honeycrisp: "Honeycrisp"
            }
        }
        public var latestURL: URL { URL(string: "https://api.github.com/repos/\(repo)/releases/latest")! }
    }

    public struct Asset: Decodable, Sendable {
        public var name: String
        public var size: Int
        public var browser_download_url: String
        public var digest: String?

        public var checksum: Checksum? { GitHub.sha256(digest).map(Checksum.sha256) }
    }

    public struct Release: Decodable, Sendable {
        public var tag_name: String
        public var assets: [Asset]
        /// Release-notes fields for the launcher's What's New cards (display only).
        public var name: String?
        public var body: String?
        public var published_at: String?
        public var html_url: String?

        /// "<project>-<mc>-….jar": the trailing "-" keeps 1.21.1 off 1.21.10 and "graft-" off "graftshaders-".
        public func jar(_ p: Project, mc: String) -> Asset? {
            assets.first { $0.name.hasPrefix("\(p.rawValue)-\(mc)-") && $0.name.hasSuffix(".jar") && !$0.name.hasSuffix("-sources.jar") }
        }
    }

    /// "sha256:<64 hex>" → lowercase hex; anything else → nil (then the asset size is checked instead).
    public static func sha256(_ digest: String?) -> String? {
        guard let d = digest?.lowercased(), d.hasPrefix("sha256:") else { return nil }
        let hex = d.dropFirst("sha256:".count)
        return hex.count == 64 && hex.allSatisfy(\.isHexDigit) ? String(hex) : nil
    }

    /// True when a latest release has a jar for this version that differs (name or tag) from the installed one.
    /// A project whose latest release could not be fetched, or has no jar for `mc`, never counts.
    public static func updateAvailable(installed: [ModJar], latest: [Project: Release], mc: String) -> Bool {
        latest.contains { p, release in
            guard let asset = release.jar(p, mc: mc) else { return false }
            let cur = installed.first { $0.project == p.rawValue }
            return cur?.file != asset.name || cur?.tag != release.tag_name
        }
    }

    public static func describe(_ error: Error) -> String {
        if let h = error as? HTTPError {
            switch h.status {
            case 403, 429: return "GitHub rate limit reached, try again later"
            case 404: return "no release published yet"
            default: return "GitHub returned HTTP \(h.status)"
            }
        }
        if error is URLError { return "can't reach GitHub (offline?)" }
        return error.localizedDescription
    }

    actor Cache {
        var releases: [Project: Release] = [:]
        func set(_ p: Project, _ r: Release) { releases[p] = r }
        func clear() { releases = [:] }
    }
    static let cache = Cache()

    /// Cached for the launcher session; failures are not cached so the next refresh retries.
    public static func latest(_ p: Project) async throws -> Release {
        if let r = await cache.releases[p] { return r }
        let r: Release = try await HTTP.get(p.latestURL, headers: ["Accept": "application/vnd.github+json"])
        await cache.set(p, r)
        return r
    }

    public static func clearCache() async { await cache.clear() }
}
