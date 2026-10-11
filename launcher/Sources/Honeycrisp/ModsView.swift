import HoneycrispCore
import SwiftUI
import UniformTypeIdentifiers

/// The in-game module list (core modules/Features.java), mirrored for the launcher's switches.
struct ModuleInfo: Identifiable {
    var id: String
    var name: String
    var symbol: String
    var category: String
    var onByDefault = false

    static let all: [ModuleInfo] = [
        .init(id: "fps", name: "FPS", symbol: "speedometer", category: "HUD", onByDefault: true),
        .init(id: "cps", name: "CPS", symbol: "cursorarrow.click", category: "HUD", onByDefault: true),
        .init(id: "keystrokes", name: "Keystrokes", symbol: "keyboard", category: "HUD"),
        .init(id: "coords", name: "Coordinates", symbol: "location", category: "HUD", onByDefault: true),
        .init(id: "compass", name: "Compass", symbol: "safari", category: "HUD"),
        .init(id: "ping", name: "Ping", symbol: "wifi", category: "HUD"),
        .init(id: "armor", name: "Armor Status", symbol: "shield.lefthalf.filled", category: "HUD"),
        .init(id: "potions", name: "Potion Effects", symbol: "flask", category: "HUD"),
        .init(id: "clock", name: "Clock", symbol: "clock", category: "HUD"),
        .init(id: "server", name: "Server Address", symbol: "server.rack", category: "HUD"),
        .init(id: "speed", name: "Speed", symbol: "hare", category: "HUD"),
        .init(id: "togglesprint", name: "Toggle Sprint", symbol: "figure.run", category: "PvP"),
        .init(id: "togglesneak", name: "Toggle Sneak", symbol: "figure.walk", category: "PvP"),
        .init(id: "oldanimations", name: "Old Animations", symbol: "clock.arrow.circlepath", category: "PvP"),
        .init(id: "hitcolor", name: "Hit Color", symbol: "drop.fill", category: "PvP"),
        .init(id: "crosshair", name: "Crosshair", symbol: "scope", category: "PvP"),
        .init(id: "zoom", name: "Zoom", symbol: "plus.magnifyingglass", category: "Camera", onByDefault: true),
        .init(id: "freelook", name: "Freelook", symbol: "eye.circle", category: "Camera"),
        .init(id: "fullbright", name: "Fullbright", symbol: "sun.max", category: "Visual"),
        .init(id: "timechanger", name: "Time Changer", symbol: "moon.stars", category: "Visual"),
        .init(id: "itemphysics", name: "Item Physics", symbol: "cube.transparent", category: "Visual"),
        .init(id: "chat", name: "Chat", symbol: "bubble.left", category: "Visual"),
        .init(id: "scoreboard", name: "Scoreboard", symbol: "list.number", category: "Visual"),
    ]
    static let categories = ["HUD", "PvP", "Camera", "Visual"]
}

/// <game>/config/honeycrisp/config.json: {"version": 1, "modules": {"<id>": {"enabled": bool, …}}}.
/// Only "enabled" is touched; every other key is preserved.
struct ModuleConfig {
    var file: URL
    var root: [String: Any]

    init(gameDir: URL) {
        file = gameDir.appending(path: "config/honeycrisp/config.json")
        root = (try? Data(contentsOf: file)).flatMap { try? JSONSerialization.jsonObject(with: $0) as? [String: Any] } ?? ["version": 1]
    }

    func enabled(_ m: ModuleInfo) -> Bool {
        ((root["modules"] as? [String: Any])?[m.id] as? [String: Any])?["enabled"] as? Bool ?? m.onByDefault
    }

    mutating func set(_ m: ModuleInfo, _ on: Bool) {
        var modules = root["modules"] as? [String: Any] ?? [:]
        var entry = modules[m.id] as? [String: Any] ?? [:]
        entry["enabled"] = on
        modules[m.id] = entry
        root["modules"] = modules
        try? FileManager.default.createDirectory(at: file.deletingLastPathComponent(), withIntermediateDirectories: true)
        try? JSONSerialization.data(withJSONObject: root, options: [.prettyPrinted, .sortedKeys]).write(to: file, options: .atomic)
    }
}

struct ModsView: View {
    @Environment(AppModel.self) private var model
    @State private var mc = ""
    @State private var config: ModuleConfig?
    @State private var jars: [URL] = []
    @State private var importing = false
    @State private var dropTargeted = false

    var spec: InstanceSpec { Catalog.spec(mc) ?? model.spec }
    var modsDir: URL { model.paths.mods(spec.mc) }
    var managed: Set<String> { Set(model.installer.stamp(spec.mc)?.managedMods ?? []) }

    var body: some View {
        Form {
            Section {
                Picker("Instance", selection: $mc) {
                    ForEach(Catalog.instances) { Text($0.title).tag($0.mc) }
                }
            }
            Section {
                let installed = model.installedJars(spec.mc)
                ForEach(GitHub.Project.allCases, id: \.self) { p in
                    let jar = installed.first { $0.project.hasPrefix(p.rawValue) && (p != .graft || !$0.project.hasPrefix("graftshaders")) }
                    let latest = model.releases[p]?.jar(p, mc: spec.mc)
                    LabeledContent(p.title) {
                        VStack(alignment: .trailing, spacing: 2) {
                            Text(jar.map { "\($0.version) · \($0.sourceDescription)" } ?? (latest == nil && model.releases[p] != nil ? "Not available for \(spec.mc)" : "Not installed"))
                                .lineLimit(1).truncationMode(.middle)
                            if let latest, let tag = model.releases[p]?.tag_name, jar?.file != latest.name || jar?.tag != tag, model.options.localJars == nil {
                                Text("Update available: \(latest.name) (\(tag))").font(.caption).foregroundStyle(.tint)
                            }
                        }
                    }
                }
                if let w = model.releaseWarning { Label(w, systemImage: "exclamationmark.triangle.fill").foregroundStyle(Theme.warning) }
            } header: {
                HStack {
                    Text("Client jars")
                    Spacer()
                    Button("Check for Updates") { Task { await model.refreshReleases(force: true) } }.disabled(model.checkingReleases)
                }
            } footer: {
                Text(model.options.localJars == nil ? "From the latest GitHub releases. Use Update on Home or Versions to install newer ones." : "Developer override: jars come from the local folder set in Settings.")
            }
            Section {
                ForEach(jars, id: \.self) { jar in jarRow(jar) }
                if jars.isEmpty {
                    Label("Drop .jar files here to add mods", systemImage: "puzzlepiece.extension")
                        .foregroundStyle(.secondary)
                }
            } header: {
                HStack {
                    Text("Mods")
                    Spacer()
                    Button("Add Mods…") { importing = true }
                    Button { NSWorkspace.shared.open(modsDir) } label: { Label("Open Folder", systemImage: "folder") }
                }
            } footer: {
                Text("Drag .jar files onto this page. Jars marked Managed are installed and replaced by the launcher on update.")
            }
            ForEach(ModuleInfo.categories, id: \.self) { cat in
                Section(cat == "HUD" ? "Honeycrisp · HUD" : cat) {
                    ForEach(ModuleInfo.all.filter { $0.category == cat }) { m in
                        Toggle(isOn: Binding(get: { config?.enabled(m) ?? m.onByDefault }, set: { config?.set(m, $0) })) {
                            Label(m.name, systemImage: m.symbol)
                        }
                    }
                }
            }
            Section {} footer: { Text("Changes apply on next launch.") }
        }
        .formStyle(.grouped)
        .overlay {
            if dropTargeted { RoundedRectangle(cornerRadius: 12).stroke(.tint, lineWidth: 3).padding(4).allowsHitTesting(false) }
        }
        .dropDestination(for: URL.self) { urls, _ in add(urls) } isTargeted: { dropTargeted = $0 }
        .fileImporter(isPresented: $importing, allowedContentTypes: [UTType(filenameExtension: "jar") ?? .data], allowsMultipleSelection: true) {
            if case .success(let urls) = $0 { _ = add(urls) }
        }
        .onAppear { if mc.isEmpty { mc = model.spec.mc } }
        .onChange(of: mc, initial: true) { reload() }
        .onChange(of: model.statusTick) { reload() }
    }

    func jarRow(_ jar: URL) -> some View {
        let name = jar.lastPathComponent
        let enabled = !name.hasSuffix(".disabled")
        return HStack {
            Toggle(isOn: Binding(get: { enabled }, set: { toggle(jar, $0) })) {
                VStack(alignment: .leading) {
                    Text(name.replacingOccurrences(of: ".disabled", with: "")).lineLimit(1).truncationMode(.middle)
                    if managed.contains(name) || managed.contains(name.replacingOccurrences(of: ".disabled", with: "")) {
                        Text("Managed").font(.caption).foregroundStyle(.secondary)
                    }
                }
            }
            Button(role: .destructive) { try? FileManager.default.trashItem(at: jar, resultingItemURL: nil); reload() } label: {
                Image(systemName: "trash")
            }
            .buttonStyle(.borderless)
            .help("Move to Trash")
        }
    }

    func reload() {
        config = ModuleConfig(gameDir: model.paths.instance(spec.mc))
        jars = ((try? FileManager.default.contentsOfDirectory(at: modsDir, includingPropertiesForKeys: nil)) ?? [])
            .filter { $0.pathExtension == "jar" || $0.lastPathComponent.hasSuffix(".jar.disabled") }
            .sorted { $0.lastPathComponent.localizedStandardCompare($1.lastPathComponent) == .orderedAscending }
    }

    func add(_ urls: [URL]) -> Bool {
        let fm = FileManager.default
        try? fm.createDirectory(at: modsDir, withIntermediateDirectories: true)
        var added = false
        for u in urls where u.pathExtension == "jar" {
            let ok = u.startAccessingSecurityScopedResource()
            defer { if ok { u.stopAccessingSecurityScopedResource() } }
            let dest = modsDir.appending(path: u.lastPathComponent)
            try? fm.removeItem(at: dest)
            added = (try? fm.copyItem(at: u, to: dest)) != nil || added
        }
        reload()
        return added
    }

    func toggle(_ jar: URL, _ on: Bool) {
        let name = jar.lastPathComponent
        let target = on ? String(name.dropLast(".disabled".count)) : name + ".disabled"
        try? FileManager.default.moveItem(at: jar, to: jar.deletingLastPathComponent().appending(path: target))
        reload()
    }
}
