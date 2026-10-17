import AppKit
import HoneycrispCore
import SwiftUI

struct LauncherSettings: Codable, Equatable {
    enum Appearance: String, Codable, CaseIterable {
        case system = "System", dark = "Dark", light = "Light"

        var configValue: String { rawValue.uppercased() }

        init?(configValue: String) {
            switch configValue.uppercased() {
            case "SYSTEM": self = .system
            case "DARK": self = .dark
            case "LIGHT": self = .light
            default: return nil
            }
        }
    }

    var memoryMB = 4096
    var javaOverride = ""
    var extraJVMArgs = ""
    var clientID = ""
    /// Developer override: local folder whose jars win over GitHub Releases. Optional so older saved settings still decode.
    var devJarFolder: String?
    var sodium = true
    var keepLauncherOpen = true
    var appearance = Appearance.system
    var accent = Theme.Accent.honeycrisp
    var windowWidth = 0          // 0 = game default
    var windowHeight = 0
    var lastVersion = Catalog.instances[0].mc

    static let key = "settings"
    static func load() -> LauncherSettings {
        UserDefaults.standard.data(forKey: key).flatMap { try? JSONDecoder().decode(LauncherSettings.self, from: $0) } ?? LauncherSettings()
    }
    func save() { UserDefaults.standard.set(try? JSONEncoder().encode(self), forKey: Self.key) }
}

extension Theme.Accent {
    var configValue: String { self == .honeycrisp ? "RED" : rawValue.uppercased() }

    static func from(configValue: String, current: Theme.Accent = .honeycrisp) -> Theme.Accent? {
        switch configValue.uppercased() {
        case "SYSTEM": .system
        case "BLUE": .blue
        case "PURPLE": .purple
        case "PINK": .pink
        case "RED": current == .honeycrisp ? .honeycrisp : .red
        case "ORANGE": .orange
        case "YELLOW": .yellow
        case "GREEN": .green
        case "GRAPHITE": .graphite
        default: nil
        }
    }
}

enum SidebarItem: String, CaseIterable, Identifiable {
    case home = "Home", versions = "Versions", mods = "Mods", log = "Game Log", settings = "Settings"
    var id: Self { self }
    var symbol: String {
        switch self {
        case .home: "house.fill"
        case .versions: "square.stack.3d.up.fill"
        case .mods: "puzzlepiece.extension.fill"
        case .log: "text.alignleft"
        case .settings: "gearshape.fill"
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
    var settings: LauncherSettings {
        didSet {
            guard settings != oldValue else { return }
            if !isPreview { settings.save() }
            if !isSyncingUI, settings.appearance != oldValue.appearance || settings.accent != oldValue.accent {
                syncUIToConfigs()
            }
            if settings.lastVersion != oldValue.lastVersion {
                syncUIFromConfig(mc: settings.lastVersion)
                if runningVersion == nil {
                    loadDiskLogIfNeeded(for: settings.lastVersion)
                }
            }
            statusTick &+= 1
        }
    }
    var selection: SidebarItem? = .home
    var sidebarCollapsed = false
    /// When each running install started, for the Play bar's time-left estimate.
    var workStarted: [String: Date] = [:]
    var phase: [String: Phase] = [:]
    var runningVersion: String?
    var log: [String] = []
    var logIsFromDisk = false
    var crashReport: String?
    var crashReportURL: URL?
    var banner: String?
    var canOfferPlayOffline = false
    var installWarnings: [String] = []
    var store: AccountStore
    var showAccount = false
    var news: [VersionManifest.Entry] = []
    var clientApproved: Bool? {
        didSet { if !isPreview { UserDefaults.standard.set(clientApproved, forKey: "clientApproved") } }
    }
    /// Bumped to make views re-read on-disk state (install stamps, mod folders).
    var statusTick = 0
    /// Cached on-disk byte counts for installed instances.
    var instanceSizes: [String: Int64] = [:]
    /// Latest GitHub releases, fetched on launch and on "Check for Updates", never per render.
    var releases: [GitHub.Project: GitHub.Release] = [:]
    var releaseWarning: String?
    var checkingReleases = false

    let installer: Installer
    /// Snapshot/preview model: never reads or writes UserDefaults or the account file, never touches the network.
    let isPreview: Bool
    @ObservationIgnored private var isSyncingUI = false
    @ObservationIgnored private var diskLogVersion: String?

    init() {
        installer = Installer()
        isPreview = false
        settings = LauncherSettings.load()
        store = AccountFile.load()
        clientApproved = UserDefaults.standard.object(forKey: "clientApproved") as? Bool
        syncUIFromConfig(mc: settings.lastVersion)
        loadDiskLogIfNeeded(for: settings.lastVersion)
    }

    init(previewPaths: Paths, settings: LauncherSettings, store: AccountStore, clientApproved: Bool?) {
        installer = Installer(paths: previewPaths)
        isPreview = true
        self.settings = settings
        self.store = store
        self.clientApproved = clientApproved
        syncUIFromConfig(mc: settings.lastVersion)
        loadDiskLogIfNeeded(for: settings.lastVersion)
    }
    var paths: Paths { installer.paths }
    /// The running game: the "Honeycrisp Client" stub app (which runs Java), its log pipe and exit watcher.
    @ObservationIgnored private var game: (app: NSRunningApplication, log: FileHandle, watch: NSKeyValueObservation)?
    @ObservationIgnored private var task: Task<Void, Never>?
    /// Set by Stop so the exit (SIGTERM / 143) isn't reported as a crash.
    @ObservationIgnored private var stopRequested = false

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

    // MARK: - Appearance & Config Sync

    /// Writes the launcher's current appearance and accent into the selected instance's config.json and any other
    /// installed instances that already have a config.json.
    func syncUIToConfigs() {
        let theme = settings.appearance.configValue
        let accent = settings.accent.configValue
        let currentMC = spec.mc
        for s in Catalog.instances {
            let dir = paths.instance(s.mc)
            var cfg = ModuleConfig(gameDir: dir)
            if s.mc == currentMC || cfg.existsOnDisk {
                cfg.setUI(theme: theme, accent: accent)
            }
        }
    }

    /// Reads the instance's config.json and updates launcher appearance/accent if changed in-game.
    func syncUIFromConfig(mc: String) {
        let cfg = ModuleConfig(gameDir: paths.instance(mc))
        guard cfg.existsOnDisk else { return }
        isSyncingUI = true
        defer { isSyncingUI = false }
        if let t = cfg.uiTheme, let app = LauncherSettings.Appearance(configValue: t), settings.appearance != app {
            settings.appearance = app
        }
        if let a = cfg.uiAccent, let acc = Theme.Accent.from(configValue: a, current: settings.accent), settings.accent != acc {
            settings.accent = acc
        }
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
        guard !checkingReleases, !isPreview else { return }
        checkingReleases = true
        defer { checkingReleases = false; statusTick &+= 1 }
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

    // MARK: - Instance Management

    func instanceDiskBytes(_ s: InstanceSpec) -> Int64 {
        _ = statusTick
        if let cached = instanceSizes[s.mc] { return cached }
        let bytes = Paths.directorySize(at: paths.instanceDir(s.id)) + Paths.directorySize(at: paths.versions.appending(path: s.id))
        return bytes
    }

    func refreshInstanceSizes() {
        let rootPaths = paths
        let specs = Catalog.instances.filter { installer.stamp($0.mc) != nil }
        Task.detached(priority: .utility) {
            var sizes: [String: Int64] = [:]
            for s in specs {
                let instBytes = Paths.directorySize(at: rootPaths.instanceDir(s.id))
                let verBytes = Paths.directorySize(at: rootPaths.versions.appending(path: s.id))
                sizes[s.mc] = instBytes + verBytes
            }
            await MainActor.run { [weak self] in
                self?.instanceSizes = sizes
            }
        }
    }

    func verifyAndRepair(_ s: InstanceSpec) async -> Bool {
        await install(s)
    }

    /// Removes `versions/<id>`, `.honeycrisp`, and `mods/` while keeping `saves/` and `screenshots/` untouched.
    func uninstall(_ s: InstanceSpec) {
        guard !isBusy else { return }
        let fm = FileManager.default
        if let stamp = installer.stamp(s.mc), stamp.versionID != s.mc {
            try? fm.removeItem(at: paths.versions.appending(path: stamp.versionID))
        }
        try? fm.removeItem(at: paths.versions.appending(path: s.mc))
        try? fm.removeItem(at: paths.instance(s.mc).appending(path: ".honeycrisp"))
        try? fm.removeItem(at: paths.mods(s.mc))
        instanceSizes[s.mc] = nil
        statusTick &+= 1
    }

    // MARK: Accounts

    func saveAccount(_ a: Account) {
        store.upsert(a)
        if !isPreview { AccountFile.save(store) }
    }

    func select(_ a: Account) {
        store.activeUUID = a.uuid
        if !isPreview { AccountFile.save(store) }
    }

    func signOut(_ a: Account) {
        store.accounts.removeAll { $0.uuid == a.uuid }
        if store.activeUUID == a.uuid { store.activeUUID = store.accounts.first?.uuid }
        if !isPreview { AccountFile.save(store) }
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
        workStarted[s.mc] = Date()
        defer {
            if case .working = phase[s.mc] { phase[s.mc] = .idle }
            workStarted[s.mc] = nil
            statusTick &+= 1
            refreshInstanceSizes()
        }
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
        canOfferPlayOffline = false
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
                if account?.kind == .microsoft, (error as? AuthError) != .clientNotApproved, (error as? AuthError) != .cancelled {
                    canOfferPlayOffline = true
                }
            }
        }
    }

    func playOffline() {
        guard !isBusy else { return }
        let s = spec
        let fallback = Account.offline(account?.name ?? "Player")
        banner = nil
        canOfferPlayOffline = false
        task = Task {
            if status(s) != .ready, !(await install(s)) { return }
            do {
                try await launch(s, fallback)
            } catch {
                banner = error.localizedDescription
            }
        }
    }

    private func launch(_ s: InstanceSpec, _ acct: Account) async throws {
        syncUIToConfigs()
        let p = try await installer.prepareLaunch(s)
        let w = settings.windowWidth, h = settings.windowHeight
        var c = LaunchContext(
            javaPath: settings.javaOverride.isEmpty ? p.javaPath : settings.javaOverride,
            gameDir: paths.instance(s.mc).path, assetsRoot: paths.assets.path, librariesDir: paths.libraries.path,
            nativesDir: paths.natives(s.mc).path, clientJar: paths.clientJar(s.mc).path, classpath: p.classpath,
            playerName: acct.name, uuid: acct.uuid, accessToken: acct.accessToken,
            userType: acct.kind == .microsoft ? "msa" : "legacy", xuid: acct.xuid ?? "0", clientID: effectiveClientID,
            memoryMB: settings.memoryMB, extraJVMArgs: LaunchCommand.splitArgs(settings.extraJVMArgs),
            loggingConfigPath: p.loggingConfig, launcherVersion: Self.version)
        if w > 0, h > 0 { c.windowSize = (w, h) }
        let argv = try LaunchCommand.build(p.version, c)

        // macOS ties a process to the app that spawned it, so Java started here would show up as "Honeycrisp" (one
        // merged Dock icon). Instead LaunchServices opens the stub "Honeycrisp Client.app", whose script runs Java:
        // the game gets that name and icon. The script execs Java (one process, one Dock icon); output comes back through
        // a FIFO.
        let run = paths.root.appending(path: "run")
        try FileManager.default.createDirectory(at: run, withIntermediateDirectories: true)
        let fifo = run.appending(path: "game.log")
        try? FileManager.default.removeItem(at: fifo)
        guard mkfifo(fifo.path, 0o600) == 0, let pipe = FileHandle(forUpdatingAtPath: fifo.path) else {
            throw CocoaError(.fileWriteUnknown, userInfo: [NSLocalizedDescriptionKey: "Couldn't create the game log pipe"])
        }
        let stub: URL
        do {
            stub = try gameApp()
        } catch {
            try? pipe.close()
            try? FileManager.default.removeItem(at: fifo)
            throw error
        }
        let config = NSWorkspace.OpenConfiguration()
        config.createsNewApplicationInstance = true
        config.arguments = [fifo.path, paths.instance(s.mc).path] + argv
        var env = ProcessInfo.processInfo.environment
        env["__CFBundleIdentifier"] = nil
        env["XPC_SERVICE_NAME"] = nil
        config.environment = env

        let started = Date()
        let lines = LineSplitter()
        pipe.readabilityHandler = { [weak self] h in
            let out = lines.feed(h.availableData)
            guard !out.isEmpty else { return }
            Task { @MainActor in self?.appendLog(out) }
        }
        let cmd = argv.map { $0.contains(" ") ? "'\($0)'" : $0 }.joined(separator: " ")
        // Offline token is "0": redacting it would mangle every zero in the command line.
        log = ["$ " + (acct.kind == .microsoft ? cmd.replacingOccurrences(of: acct.accessToken, with: "<token>") : cmd)]
        logIsFromDisk = false
        diskLogVersion = s.mc
        crashReport = nil
        crashReportURL = nil
        stopRequested = false
        // Busy before the (multi-second) open, so Play can't start a second copy meanwhile.
        runningVersion = s.mc
        let app: NSRunningApplication
        do { app = try await NSWorkspace.shared.openApplication(at: stub, configuration: config) }
        catch {
            pipe.readabilityHandler = nil
            try? pipe.close()
            try? FileManager.default.removeItem(at: fifo)
            runningVersion = nil
            throw error
        }
        // The game isn't our child, so there's no exit code: a crash is a crash report written during the run.
        let game = paths.instance(s.mc)
        let watch = app.observe(\.isTerminated, options: [.initial, .new]) { [weak self] app, _ in
            guard app.isTerminated else { return }
            Task { @MainActor in
                let crashed = Self.newestCrashReport(in: game, since: started) != nil
                self?.finished(s, code: crashed ? 1 : 0, signaled: false, started: started)
            }
        }
        self.game = (app, pipe, watch)
        bringToFront(app)
    }

    static let gameBundleID = "dev.honeycrisp.client"

    /// The stub "Honeycrisp Client.app" (name, icon, and a script that runs Java), rewritten and registered with
    /// LaunchServices before each launch so it always matches this launcher version.
    private func gameApp() throws -> URL {
        let bundle = paths.root.appending(path: "Honeycrisp Client.app")
        let app = bundle.appending(path: "Contents")
        let fm = FileManager.default
        try fm.createDirectory(at: app.appending(path: "MacOS"), withIntermediateDirectories: true)
        try fm.createDirectory(at: app.appending(path: "Resources"), withIntermediateDirectories: true)
        let plist: [String: Any] = ["CFBundleIdentifier": Self.gameBundleID, "CFBundleName": "Honeycrisp Client",
                                    "CFBundleDisplayName": "Honeycrisp Client", "CFBundleExecutable": "client",
                                    "CFBundlePackageType": "APPL", "CFBundleIconFile": "GameIcon",
                                    "CFBundleShortVersionString": Self.version, "LSMinimumSystemVersion": "14.0",
                                    "NSHighResolutionCapable": true]
        try PropertyListSerialization.data(fromPropertyList: plist, format: .xml, options: 0)
            .write(to: app.appending(path: "Info.plist"))
        let exe = app.appending(path: "MacOS/client")
        try """
        #!/bin/sh
        # Honeycrisp Client: opened by the Honeycrisp launcher so the game gets this app's name and icon.
        # Args: <log fifo> <game dir> <java> <java args...>. exec: Java takes over this process, so it is this app.
        log="$1"; cd "$2" || exit 1; shift 2
        exec "$@" >"$log" 2>&1 </dev/null

        """.write(to: exe, atomically: true, encoding: .utf8)
        try fm.setAttributes([.posixPermissions: 0o755], ofItemAtPath: exe.path)
        if let icon = Bundle.main.url(forResource: "GameIcon", withExtension: "icns") {
            let dst = app.appending(path: "Resources/GameIcon.icns")
            try? fm.removeItem(at: dst)
            try fm.copyItem(at: icon, to: dst)
        }
        LSRegisterURL(bundle as CFURL, true)
        return bundle
    }

    /// Hand focus to the game once its window exists. macOS (14+) won't let a background process activate itself, and
    /// hiding the launcher before the window existed left the game behind every other app. The launcher is frontmost
    /// (the user just pressed Play), so it can yield activation to the game.
    private func bringToFront(_ app: NSRunningApplication) {
        Task { @MainActor [weak self] in
            for _ in 0..<240 {  // ponytail: polls up to 2 minutes for the window; a slow modpack start just stays behind.
                try? await Task.sleep(for: .milliseconds(500))
                guard !app.isTerminated else { return }
                guard app.activationPolicy == .regular else { continue }
                NSApp.yieldActivation(to: app)
                app.activate()
                if self?.settings.keepLauncherOpen == false { NSApp.hide(nil) }
                return
            }
        }
    }

    func appendLog(_ lines: [String]) {
        log.append(contentsOf: lines)
        if log.count > 20_000 { log.removeFirst(log.count - 20_000) }
    }

    private func finished(_ s: InstanceSpec, code: Int32, signaled: Bool, started: Date) {
        game?.log.readabilityHandler = nil
        try? game?.log.close()
        try? FileManager.default.removeItem(at: paths.root.appending(path: "run/game.log"))
        game?.watch.invalidate()
        game = nil
        runningVersion = nil
        syncUIFromConfig(mc: s.mc)
        statusTick &+= 1
        refreshInstanceSizes()
        let stopped = stopRequested
        stopRequested = false
        guard !Self.isNormalExit(code: code, signaled: signaled, stopRequested: stopped) else { return }
        if let found = Self.newestCrashReportFile(in: paths.instance(s.mc), since: started) {
            crashReport = found.content
            crashReportURL = found.url
        } else {
            crashReport = nil
            crashReportURL = nil
        }
        banner = "Minecraft \(s.mc) crashed (exit code \(code))."
        NSApp.unhide(nil)
        NSApp.activate()
    }

    /// Loads `<instance>/logs/latest.log` and the newest crash report from disk when no session log is active.
    func loadDiskLogIfNeeded(for mc: String? = nil, force: Bool = false) {
        guard runningVersion == nil else { return }
        let target = mc ?? spec.mc
        let game = paths.instance(target)
        let shouldReload = force || log.isEmpty || (logIsFromDisk && diskLogVersion != target)
        if shouldReload {
            let latest = game.appending(path: "logs/latest.log")
            if let content = try? String(contentsOf: latest, encoding: .utf8), !content.isEmpty {
                var lines = content.components(separatedBy: .newlines)
                if lines.last == "" { lines.removeLast() }
                log = lines
                logIsFromDisk = true
            } else if logIsFromDisk && diskLogVersion != target {
                log = []
                logIsFromDisk = false
            }
        }
        if shouldReload || (crashReport == nil && diskLogVersion != target) {
            if let found = Self.newestCrashReportFile(in: game, since: nil) {
                crashReport = found.content
                crashReportURL = found.url
                logIsFromDisk = true
            } else if logIsFromDisk && diskLogVersion != target {
                crashReport = nil
                crashReportURL = nil
            }
        }
        diskLogVersion = target
    }

    /// crash-reports/*.txt first, then a JVM hs_err_pid*.log, optionally newer than `since`.
    static func newestCrashReportFile(in game: URL, since: Date? = nil) -> (url: URL, content: String)? {
        let fm = FileManager.default
        let candidates = [game.appending(path: "crash-reports"), game].flatMap { dir in
            ((try? fm.contentsOfDirectory(at: dir, includingPropertiesForKeys: [.contentModificationDateKey])) ?? [])
                .filter { ($0.pathExtension == "txt" && dir.lastPathComponent == "crash-reports") || $0.lastPathComponent.hasPrefix("hs_err_pid") }
        }
        let newest = candidates
            .compactMap { u in (try? u.resourceValues(forKeys: [.contentModificationDateKey]).contentModificationDate).map { (u, $0) } }
            .filter { since == nil || $0.1 >= since! }
            .max { $0.1 < $1.1 }
        guard let (url, _) = newest, let content = try? String(contentsOf: url, encoding: .utf8) else { return nil }
        return (url, content)
    }

    static func newestCrashReport(in game: URL, since: Date) -> String? {
        newestCrashReportFile(in: game, since: since)?.content
    }

    /// One-line human-readable diagnosis for common crash causes.
    static func diagnoseCrash(_ report: String) -> String? {
        if report.contains("OutOfMemoryError") || report.contains("There is insufficient memory") {
            return "Out of memory — try raising the Memory slider in Settings → Game."
        }
        if report.contains("UnsupportedClassVersionError") {
            return "Incompatible Java version — check or reset the Java runtime override in Settings → Java."
        }
        if report.contains("ModResolutionException") || report.contains("DuplicateModsFoundException")
            || report.contains("Incompatible mod set") || report.contains("MissingModsException")
            || report.contains("LoaderException") || report.contains("MixinApplyError") {
            return "Incompatible or duplicate mods detected — check your installed jars in the Mods tab."
        }
        return nil
    }

    /// Quitting, Stop, Ctrl-C or SIGTERM (JVM exits 128+15 = 143, 128+2 = 130) are not crashes.
    nonisolated static func isNormalExit(code: Int32, signaled: Bool, stopRequested: Bool) -> Bool {
        code == 0 || stopRequested || [130, 143].contains(code) || (signaled && [2, 15].contains(code))
    }

    /// "About 2 min left", from elapsed time and the install fraction; nil until there is enough signal.
    func timeLeft(_ mc: String, fraction f: Double) -> String? {
        guard let start = workStarted[mc], f > 0.04, f < 1 else { return nil }
        let left = Date().timeIntervalSince(start) / f * (1 - f)
        if left < 60 { return "Less than a minute left" }
        return "About \(Int((left / 60).rounded())) min left"
    }

    func stop() {
        stopRequested = game != nil
        if let pid = game?.app.processIdentifier { kill(pid, SIGTERM) }  // the stub exec'd Java: this is the game
        task?.cancel()
    }

    // MARK: Misc

    func loadNews() async {
        guard news.isEmpty, !isPreview, let d = try? await HTTP.data(URLRequest(url: Catalog.versionManifest)),
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
