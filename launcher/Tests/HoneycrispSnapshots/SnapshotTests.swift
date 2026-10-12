import AppKit
@testable import Honeycrisp
import HoneycrispCore
import SwiftUI
import XCTest

/// Renders every screen offscreen to build/snapshots/<name>-<dark|light>.png.
/// The window is never ordered front and the process never activates, so nothing appears on screen.
/// `SNAPSHOT_DIR=…` overrides the output folder.
@MainActor
final class SnapshotTests: XCTestCase {
    static let size = CGSize(width: 1100, height: 720)

    var outDir: URL {
        if let d = ProcessInfo.processInfo.environment["SNAPSHOT_DIR"] { return URL(filePath: d) }
        return URL(filePath: #filePath).deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent()
            .appending(path: "build/snapshots")
    }

    override func setUp() {
        NSApplication.shared.setActivationPolicy(.prohibited)
    }

    /// Liquid Glass can't sample its backdrop offscreen (it renders near-clear and flips text dark), so the main
    /// set uses the macOS 14–15 material fallback; build/snapshots/glass/ keeps a real-glass reference set.
    func testRenderScreens() throws {
        try FileManager.default.createDirectory(at: outDir, withIntermediateDirectories: true)
        let main = outDir
        defer { Theme.liquidGlass = true }
        Theme.liquidGlass = false
        try renderAll(names: nil)
        Theme.liquidGlass = true
        glassDir = main.appending(path: "glass")
        try FileManager.default.createDirectory(at: glassDir!, withIntermediateDirectories: true)
        try renderAll(names: ["home-ready", "versions", "mods", "settings"])
    }

    var glassDir: URL?

    /// Stop, Cmd-Q, SIGTERM (143) and Ctrl-C (130) are not crashes; anything else non-zero is.
    func testExitClassification() {
        XCTAssertTrue(AppModel.isNormalExit(code: 0, signaled: false, stopRequested: false))
        XCTAssertTrue(AppModel.isNormalExit(code: 143, signaled: false, stopRequested: false))
        XCTAssertTrue(AppModel.isNormalExit(code: 130, signaled: false, stopRequested: false))
        XCTAssertTrue(AppModel.isNormalExit(code: 15, signaled: true, stopRequested: false))
        XCTAssertTrue(AppModel.isNormalExit(code: 1, signaled: false, stopRequested: true))
        XCTAssertFalse(AppModel.isNormalExit(code: 1, signaled: false, stopRequested: false))
        XCTAssertFalse(AppModel.isNormalExit(code: 11, signaled: true, stopRequested: false))
    }

    func renderAll(names: Set<String>?) throws {
        for dark in [true, false] {
            for (name, configure) in Self.scenarios where names?.contains(name) ?? true {
                let model = try Mock.model()
                configure(model)
                render(RootView().environment(model), name: name, dark: dark, model: model)
            }
            if names != nil { continue }
            let log = try Mock.model()
            log.log = Mock.log
            render(LogView().environment(log), name: "log", dark: dark, model: log, size: CGSize(width: 900, height: 560))
            let acct = try Mock.model()
            render(AccountView().environment(acct), name: "account", dark: dark, model: acct, size: CGSize(width: 440, height: 620))
            let none = try Mock.model(accounts: false)
            none.clientApproved = false
            render(AccountView().environment(none), name: "account-empty", dark: dark, model: none, size: CGSize(width: 440, height: 520))
            let code = try Mock.model()
            let dc = try JSONDecoder().decode(MicrosoftAuth.DeviceCode.self, from: Data(#"{"device_code":"x","user_code":"HNY-CR1SP","verification_uri":"https://microsoft.com/link","expires_in":900,"interval":5}"#.utf8))
            render(AccountView(deviceCode: dc).environment(code), name: "account-code", dark: dark, model: code, size: CGSize(width: 440, height: 620))
            // Below the fold: tall renders of the long pages.
            for (page, h) in [(SidebarItem.settings, 1640.0), (.mods, 1500), (.versions, 1000), (.home, 900)] {
                let tall = try Mock.model()
                tall.selection = page
                tall.settings.lastVersion = "1.21.11"
                tall.settings.devJarFolder = page == .settings ? "~/Documents/HoneycrispEco/Graft/dist" : nil
                render(RootView().environment(tall), name: "\(page.rawValue.lowercased())-full", dark: dark, model: tall,
                       size: CGSize(width: 1100, height: h))
            }
            let narrow = try Mock.model()
            narrow.settings.lastVersion = "1.21.11"
            render(RootView().environment(narrow), name: "home-narrow", dark: dark, model: narrow, size: CGSize(width: 880, height: 600))
            let sheet = try Mock.model()
            render(InstallSheet().environment(sheet), name: "install-sheet", dark: dark, model: sheet, size: CGSize(width: 520, height: 480))
        }
    }

    static let scenarios: [(String, (AppModel) -> Void)] = [
        ("home-ready", { m in m.settings.lastVersion = "1.21.11" }),
        ("home-update", { m in m.settings.lastVersion = "26.2" }),
        ("home-notinstalled", { m in m.settings.lastVersion = "1.12.2" }),
        ("home-installing", { m in
            m.settings.lastVersion = "1.8.9"
            m.phase["1.8.9"] = .working(0.42, "Downloading assets (1,204 / 2,870)")
            m.workStarted["1.8.9"] = Date().addingTimeInterval(-95)
        }),
        ("home-running", { m in m.settings.lastVersion = "1.21.11"; m.runningVersion = "1.21.11" }),
        ("home-crashed", { m in
            m.settings.lastVersion = "1.21.11"
            m.banner = "Minecraft 1.21.11 crashed (exit code 1)."
            m.crashReport = "---- Minecraft Crash Report ----"
        }),
        ("versions", { m in m.selection = .versions; m.installWarnings = ["Graft Shaders has no build for 1.12.2 yet."] }),
        ("mods", { m in m.selection = .mods }),
        ("settings", { m in m.selection = .settings }),
        ("settings-dev", { m in m.selection = .settings; m.clientApproved = false; m.settings.devJarFolder = "~/Documents/HoneycrispEco/Graft/dist" }),
    ]

    func render<V: View>(_ view: V, name: String, dark: Bool, model: AppModel, size: CGSize = size) {
        let host = NSHostingView(rootView: view.frame(width: size.width, height: size.height))
        let window = NSWindow(contentRect: CGRect(origin: .zero, size: size),
                              styleMask: [.titled, .closable, .miniaturizable, .resizable, .fullSizeContentView],
                              backing: .buffered, defer: false)
        window.titlebarAppearsTransparent = true
        window.titleVisibility = .hidden
        window.isReleasedWhenClosed = false
        window.appearance = NSAppearance(named: dark ? .darkAqua : .aqua)
        window.contentView = host
        for _ in 0..<6 {
            host.layoutSubtreeIfNeeded()
            RunLoop.main.run(until: Date().addingTimeInterval(0.08))
        }
        let target = window.contentView?.superview ?? host
        guard let rep = target.bitmapImageRepForCachingDisplay(in: target.bounds) else { return XCTFail("no bitmap for \(name)") }
        target.cacheDisplay(in: target.bounds, to: rep)
        let url = (glassDir ?? outDir).appending(path: "\(name)-\(dark ? "dark" : "light").png")
        XCTAssertNoThrow(try rep.representation(using: .png, properties: [:])?.write(to: url))
        window.contentView = nil
        window.close()
    }
}

/// Fake data: a temp Application Support tree with install stamps and mod jars, fake accounts and releases.
@MainActor
enum Mock {
    static let installed: [String: String] = ["1.21.11": "v1.4.0", "26.2": "v1.3.0", "1.8.9": "v1.4.0", "26.3": "v1.4.0"]

    static func model(accounts: Bool = true) throws -> AppModel {
        let root = FileManager.default.temporaryDirectory.appending(path: "honeycrisp-snapshots-\(UUID().uuidString)")
        let paths = Paths(root: root)
        let fm = FileManager.default
        for (mc, tag) in installed {
            let spec = Catalog.spec(mc)!
            let jars = projects(for: mc).map { p in
                ModJar(project: p.rawValue, file: "\(p.rawValue)-\(mc)-\(tag.dropFirst()).jar", tag: tag, source: p.repo)
            }
            let sodium = spec.sodiumSupported
            let stamp: [String: Any] = ["revision": InstallStamp.revision, "versionID": mc, "javaPath": "/java",
                                        "managedMods": jars.map(\.file) + (mc == "1.21.11" ? ["sodium-fabric-0.8.14+mc1.21.11.jar", "fabric-api-0.141.1+1.21.11.jar"] : []), "fingerprint": "github;sodium=\(sodium);java=", "warnings": [String](),
                                        "jars": jars.map { ["project": $0.project, "file": $0.file, "tag": $0.tag ?? "", "source": $0.source] }]
            let file = paths.stamp(mc)
            try fm.createDirectory(at: file.deletingLastPathComponent(), withIntermediateDirectories: true)
            try JSONSerialization.data(withJSONObject: stamp).write(to: file)
            try fm.createDirectory(at: paths.mods(mc), withIntermediateDirectories: true)
            for j in jars { try Data().write(to: paths.mods(mc).appending(path: j.file)) }
            if mc == "1.21.11" {
                for f in ["sodium-fabric-0.8.14+mc1.21.11.jar", "fabric-api-0.141.1+1.21.11.jar", "modmenu-17.0.0.jar",
                          "lithium-fabric-0.21.0+mc1.21.11.jar.disabled", "xaeros-minimap-25.2.jar"] {
                    try Data().write(to: paths.mods(mc).appending(path: f))
                }
            }
        }
        var store = AccountStore()
        if accounts {
            var ms = Account.offline("kkoren")
            ms.kind = .microsoft
            ms.expires = .distantFuture
            store.upsert(ms)
            store.upsert(.offline("TestPilot"))
            store.activeUUID = ms.uuid
        }
        var settings = LauncherSettings()
        settings.clientID = "cc83698e-daaf-4e70-843f-fde5c5245a42"
        let m = AppModel(previewPaths: paths, settings: settings, store: store, clientApproved: nil)
        m.releases = releases
        m.news = news
        return m
    }

    static func projects(for mc: String) -> [GitHub.Project] {
        ["1.21.8", "1.21.10", "1.21.11", "26.2", "26.3"].contains(mc) ? GitHub.Project.allCases : [.graft, .honeycrisp]
    }

    static var releases: [GitHub.Project: GitHub.Release] {
        var out: [GitHub.Project: GitHub.Release] = [:]
        for p in GitHub.Project.allCases {
            let assets = Catalog.instances.filter { projects(for: $0.mc).contains(p) }.map { s in
                #"{"name":"\#(p.rawValue)-\#(s.mc)-1.4.0.jar","size":\#(2_400_000 + s.mc.count * 91_000),"browser_download_url":"https://example.invalid"}"#
            }
            let notes = switch p {
            case .graft: "## Graft 1.4.0\\n- Metal 4 argument buffers: ~18% faster chunk rendering on M-series\\n- Fixed water shimmer on 1.21.11\\n- Lower memory use with large render distances"
            case .graftshaders: "## Graft Shaders 1.4.0\\n- Complementary Reimagined and BSL now compile on Metal\\n- Shadow cascades tuned for Apple GPUs"
            case .honeycrisp: "## Honeycrisp 1.4.0\\n- New Keystrokes mod with CPS\\n- Liquid Glass HUD editor\\n- Freelook on legacy versions"
            }
            let json = #"{"tag_name":"v1.4.0","name":"\#(p.title) 1.4.0","body":"\#(notes)","published_at":"2026-10-0\#(p == .graft ? 9 : p == .graftshaders ? 7 : 8)T18:00:00Z","html_url":"https://github.com/\#(p.repo)/releases/tag/v1.4.0","assets":[\#(assets.joined(separator: ","))]}"#
            out[p] = try? JSONDecoder().decode(GitHub.Release.self, from: Data(json.utf8))
        }
        return out
    }

    static var news: [VersionManifest.Entry] {
        let json = #"""
        {"latest":{"release":"1.21.11","snapshot":"26.4-snapshot-3"},"versions":[
        {"id":"26.4-snapshot-3","type":"snapshot","url":"x","releaseTime":"2026-10-08T12:00:00+00:00"},
        {"id":"26.3","type":"release","url":"x","releaseTime":"2026-09-16T12:00:00+00:00"},
        {"id":"26.2","type":"release","url":"x","releaseTime":"2026-06-17T12:00:00+00:00"},
        {"id":"1.21.11","type":"release","url":"x","releaseTime":"2025-12-09T12:00:00+00:00"}]}
        """#
        return (try? VersionManifest.decode(Data(json.utf8)).versions) ?? []
    }

    static let log: [String] = [
        "$ /runtimes/java-runtime-epsilon/bin/java -Xmx4096M -XstartOnFirstThread -cp … net.fabricmc.loader.impl.launch.knot.KnotClient",
        "[18:02:11] [main/INFO]: Loading Minecraft 1.21.11 with Fabric Loader 0.17.3",
        "[18:02:11] [main/INFO]: Loading 9 mods: graft 1.4.0, graftshaders 1.4.0, honeycrisp 1.4.0, sodium 0.8.14, fabric-api 0.141.1",
        "[18:02:12] [main/INFO]: [Graft] Metal device: Apple M4 Max (unified memory 64 GB)",
        "[18:02:12] [main/WARN]: Mod 'modmenu' uses deprecated API",
        "[18:02:13] [Render thread/INFO]: [Honeycrisp] 23 modules loaded",
        "[18:02:13] [Render thread/INFO]: Backend library: LWJGL version 3.3.3",
        "[18:02:14] [Render thread/ERROR]: Failed to load texture: minecraft:textures/missing.png",
        "java.io.FileNotFoundException: minecraft:textures/missing.png",
        "\tat net.minecraft.client.texture.ResourceTexture.load(ResourceTexture.java:42)",
        "[18:02:15] [Render thread/INFO]: Sound engine started",
        "[18:02:16] [Render thread/INFO]: Created: 4096x2048x4 minecraft:textures/atlas/blocks.png-atlas",
        "[18:02:17] [Server thread/INFO]: Starting integrated minecraft server version 1.21.11",
        "[18:02:18] [Server thread/INFO]: Preparing start region for dimension minecraft:overworld",
        "[18:02:20] [Render thread/INFO]: [Graft] Frame pacing: 120 Hz ProMotion",
    ]
}
