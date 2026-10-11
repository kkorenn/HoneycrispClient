import AppKit
import HoneycrispCore
import SwiftUI

struct LauncherSettings: Codable, Equatable {
    enum Appearance: String, Codable, CaseIterable { case system = "System", dark = "Dark", light = "Light" }

    var memoryMB = 4096
    var javaOverride = ""
    var extraJVMArgs = ""
    var clientID = ""
    /// Developer override: local folder whose jars win over GitHub Releases. Optional so older saved settings still decode.
    var devJarFolder: String?
    var sodium = true
    var keepLauncherOpen = true
    var appearance = Appearance.system
    var accent = Theme.Accent.system
    var windowWidth = 0          // 0 = game default
    var windowHeight = 0
    var lastVersion = Catalog.instances[0].mc

    static let key = "settings"
    static func load() -> LauncherSettings {
        UserDefaults.standard.data(forKey: key).flatMap { try? JSONDecoder().decode(LauncherSettings.self, from: $0) } ?? LauncherSettings()
    }
    func save() { UserDefaults.standard.set(try? JSONEncoder().encode(self), forKey: Self.key) }
}

enum SidebarItem: String, CaseIterable, Identifiable {
    case home = "Home", versions = "Versions", mods = "Mods", settings = "Settings"
    var id: Self { self }
    var symbol: String {
        switch self {
        case .home: "house"
        case .versions: "square.stack.3d.up"
        case .mods: "puzzlepiece.extension"
        case .settings: "gearshape"
        }
    }
}

enum InstanceStatus { case notInstalled, needsUpdate, ready }

/// Play button state machine: Install → Update → Play → (working) → Running.
enum Phase: Equatable {
    case idle
    case working(Double, String)
    case running
}

enum ClientIDState { case missing, notApproved, unknown, approved }

@MainActor @Observable
final class AppModel {
    var settings = LauncherSettings.load() { didSet { if settings != oldValue { settings.save(); statusTick += 1 } } }
    var selection: SidebarItem? = .home
    var phase: [String: Phase] = [:]
    var runningVersion: String?
    var log: [String] = []
    var crashReport: String?
    var banner: String?
    var installWarnings: [String] = []
    var store = Keychain.load()
    var showAccount = false
    var news: [VersionManifest.Entry] = []
    var clientApproved: Bool? = UserDefaults.standard.object(forKey: "clientApproved") as? Bool {
        didSet { UserDefaults.standard.set(clientApproved, forKey: "clientApproved") }
    }
    /// Bumped to make views re-read on-disk state (install stamps, mod folders).
    var statusTick = 0
    /// Latest GitHub releases, fetched on launch and on "Check for Updates", never per render.
    var releases: [GitHub.Project: GitHub.Release] = [:]
    var releaseWarning: String?
    var checkingReleases = false

    let installer = Installer()
    var paths: Paths { installer.paths }
    @ObservationIgnored private var process: Process?
    @ObservationIgnored private var task: Task<Void, Never>?

    var account: Account? { store.active }

    var spec: InstanceSpec { Catalog.spec(settings.lastVersion) ?? Catalog.instances[0] }

    var effectiveClientID: String {
        let s = settings.clientID.trimmingCharacters(in: .whitespaces)
        return s.isEmpty ? (Bundle.main.object(forInfoDictionaryKey: "MCClientID") as? String ?? "") : s
    }

    var clientIDState: ClientIDState {
        if effectiveClientID.isEmpty { return .missing }
        switch clientApproved { case true?: return .approved; case false?: return .notApproved; case nil: return .unknown }
    }

    var options: InstallOptions {
        let dev = (settings.devJarFolder ?? "").trimmingCharacters(in: .whitespaces)
        return InstallOptions(localJars: dev.isEmpty ? nil : URL(filePath: (dev as NSString).expandingTildeInPath),
                              sodium: settings.sodium, javaOverride: settings.javaOverride.isEmpty ? nil : settings.javaOverride)
    }

    func status(_ s: InstanceSpec) -> InstanceStatus {
        _ = statusTick
        guard let stamp = installer.stamp(s.mc) else { return .notInstalled }
        if installer.needsUpdate(s, options) { return .needsUpdate }
        if options.localJars == nil, GitHub.updateAvailable(installed: stamp.jars ?? [], latest: releases, mc: s.mc) { return .needsUpdate }
        return .ready
    }

    func installedJars(_ mc: String) -> [ModJar] {
        _ = statusTick
        return installer.stamp(mc)?.jars ?? []
    }

    /// Network check for newer Graft / Graft Shaders / Honeycrisp releases. Failures (offline, rate limit) only warn.
    func refreshReleases(force: Bool = false) async {
        guard !checkingReleases else { return }
        checkingReleases = true
        defer { checkingReleases = false; statusTick += 1 }
        if force { await GitHub.clearCache() }
        var problems: [String] = []
        for p in GitHub.Project.allCases {
            do { releases[p] = try await GitHub.latest(p) } catch { problems.append("\(p.title): \(GitHub.describe(error))") }
        }
        releaseWarning = problems.isEmpty ? nil : "Couldn't check for updates. " + problems.joined(separator: "; ")
    }

    func phase(_ s: InstanceSpec) -> Phase {
        if runningVersion == s.mc { return .running }
        return phase[s.mc] ?? .idle
    }

    var isBusy: Bool { runningVersion != nil || phase.values.contains { if case .working = $0 { true } else { false } } }

    // MARK: Accounts

    func saveAccount(_ a: Account) {
        store.upsert(a)
        Keychain.save(store)
    }

    func select(_ a: Account) {
        store.activeUUID = a.uuid
        Keychain.save(store)
    }

    func signOut(_ a: Account) {
        store.accounts.removeAll { $0.uuid == a.uuid }
        if store.activeUUID == a.uuid { store.activeUUID = store.accounts.first?.uuid }
        Keychain.save(store)
    }

    /// Records the outcome so the UI can say "Client ID not approved yet" instead of a raw 403.
    func noteAuth(_ error: Error?) {
        if error == nil { clientApproved = true } else if (error as? AuthError) == .clientNotApproved { clientApproved = false }
    }

    func freshAccount() async throws -> Account {
        guard let a = account else { throw AuthError.cancelled }
        guard a.needsRefresh else { return a }
        do {
            let r = try await MicrosoftAuth(clientID: effectiveClientID).refresh(a)
            noteAuth(nil)
            saveAccount(r)
            return r
        } catch {
            noteAuth(error)
            throw error
        }
    }

    // MARK: Install / launch

    func install(_ s: InstanceSpec) async -> Bool {
        phase[s.mc] = .working(0, "Preparing…")
        defer { if case .working = phase[s.mc] { phase[s.mc] = .idle }; statusTick += 1 }
        do {
            let stamp = try await installer.install(s, options) { [weak self] f, m in
                Task { @MainActor in
                    if case .working = self?.phase[s.mc] { self?.phase[s.mc] = .working(f, m) }
                }
            }
            installWarnings = stamp.warnings
            return true
        } catch {
            banner = "Install failed: \(error.localizedDescription)"
            return false
        }
    }

    func play() {
        guard !isBusy else { return }
        let s = spec
        banner = nil
        task = Task {
            guard account != nil else {
                banner = "Sign in with Microsoft or choose Offline mode first."
                showAccount = true
                return
            }
            if status(s) != .ready, !(await install(s)) { return }
            do {
                let acct = try await freshAccount()
                try await launch(s, acct)
            } catch {
                banner = error.localizedDescription
            }
        }
    }

    private func launch(_ s: InstanceSpec, _ acct: Account) async throws {
        let p = try await installer.prepareLaunch(s)
        let w = settings.windowWidth, h = settings.windowHeight
        var c = LaunchContext(
            javaPath: settings.javaOverride.isEmpty ? p.javaPath : settings.javaOverride,
            gameDir: paths.instance(s.mc).path, assetsRoot: paths.assets.path, librariesDir: paths.libraries.path,
            nativesDir: paths.natives(s.mc).path, clientJar: paths.clientJar(s.mc).path, classpath: p.classpath,
            playerName: acct.name, uuid: acct.uuid, accessToken: acct.accessToken,
            userType: acct.kind == .microsoft ? "msa" : "legacy", xuid: acct.xuid ?? "0", clientID: effectiveClientID,
            memoryMB: settings.memoryMB, extraJVMArgs: settings.extraJVMArgs.split(separator: " ").map(String.init),
            loggingConfigPath: p.loggingConfig, launcherVersion: Self.version)
        if w > 0, h > 0 { c.windowSize = (w, h) }
        let argv = try LaunchCommand.build(p.version, c)

        let proc = Process()
        proc.executableURL = URL(filePath: argv[0])
        proc.arguments = Array(argv.dropFirst())
        proc.currentDirectoryURL = paths.instance(s.mc)
        let pipe = Pipe()
        proc.standardOutput = pipe
        proc.standardError = pipe
        let started = Date()
        let lines = LineSplitter()
        pipe.fileHandleForReading.readabilityHandler = { [weak self] h in
            let out = lines.feed(h.availableData)
            guard !out.isEmpty else { return }
            Task { @MainActor in self?.appendLog(out) }
        }
        proc.terminationHandler = { [weak self] p in
            let code = p.terminationStatus
            Task { @MainActor in self?.finished(s, code: code, started: started) }
        }
        log = ["$ " + argv.map { $0.contains(" ") ? "'\($0)'" : $0 }.joined(separator: " ")
                    .replacingOccurrences(of: acct.accessToken, with: "<token>")]
        crashReport = nil
        try proc.run()
        process = proc
        runningVersion = s.mc
        if !settings.keepLauncherOpen { NSApp.hide(nil) }
    }

    func appendLog(_ lines: [String]) {
        log.append(contentsOf: lines)
        if log.count > 20_000 { log.removeFirst(log.count - 20_000) }
    }

    private func finished(_ s: InstanceSpec, code: Int32, started: Date) {
        (process?.standardOutput as? Pipe)?.fileHandleForReading.readabilityHandler = nil
        process = nil
        runningVersion = nil
        guard code != 0 else { return }
        crashReport = Self.newestCrashReport(in: paths.instance(s.mc), since: started)
        banner = "Minecraft \(s.mc) exited with code \(code)."
        NSApp.unhide(nil)
        NSApp.activate()
    }

    /// crash-reports/*.txt first, then a JVM hs_err_pid*.log, newer than the launch.
    static func newestCrashReport(in game: URL, since: Date) -> String? {
        let fm = FileManager.default
        let candidates = [game.appending(path: "crash-reports"), game].flatMap { dir in
            ((try? fm.contentsOfDirectory(at: dir, includingPropertiesForKeys: [.contentModificationDateKey])) ?? [])
                .filter { ($0.pathExtension == "txt" && dir.lastPathComponent == "crash-reports") || $0.lastPathComponent.hasPrefix("hs_err_pid") }
        }
        let newest = candidates
            .compactMap { u in (try? u.resourceValues(forKeys: [.contentModificationDateKey]).contentModificationDate).map { (u, $0) } }
            .filter { $0.1 >= since }
            .max { $0.1 < $1.1 }
        return newest.flatMap { try? String(contentsOf: $0.0, encoding: .utf8) }
    }

    func stop() {
        process?.terminate()
        task?.cancel()
    }

    // MARK: Misc

    func loadNews() async {
        guard news.isEmpty, let d = try? await HTTP.data(URLRequest(url: Catalog.versionManifest)),
              let m = try? VersionManifest.decode(d) else { return }
        news = Array(m.versions.prefix(12))
    }

    func openGameFolder(_ s: InstanceSpec? = nil) {
        let dir = paths.instance((s ?? spec).mc)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        NSWorkspace.shared.open(dir)
    }

    static let version = Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "dev"
}

/// Splits pipe chunks into lines; called on the pipe's background queue only.
final class LineSplitter: @unchecked Sendable {
    private var buffer = Data()
    func feed(_ d: Data) -> [String] {
        buffer.append(d)
        guard let last = buffer.lastIndex(of: 0x0A) else { return [] }
        let chunk = buffer[..<last]
        buffer = Data(buffer[buffer.index(after: last)...])
        return String(decoding: chunk, as: UTF8.self).components(separatedBy: "\n")
    }
}
