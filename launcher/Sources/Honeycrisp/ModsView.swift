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
        Page {
            PageHeader(title: "Mods", subtitle: "Client jars, your mods and Honeycrisp modules for each version") {
                VersionMenuButton(selection: $mc, compact: true)
            }
            clientJars
            yourMods
            modules
        }
        .overlay {
            if dropTargeted {
                RoundedRectangle(cornerRadius: Theme.cardRadius, style: .continuous)
                    .strokeBorder(Theme.Palette.brand, style: StrokeStyle(lineWidth: 3, dash: [10, 6]))
                    .background(Theme.Palette.amber.opacity(0.08), in: .rect(cornerRadius: Theme.cardRadius, style: .continuous))
                    .padding(10)
                    .allowsHitTesting(false)
            }
        }
        .animation(Theme.Motion.snappy, value: dropTargeted)
        .dropDestination(for: URL.self) { urls, _ in add(urls) } isTargeted: { dropTargeted = $0 }
        .fileImporter(isPresented: $importing, allowedContentTypes: [UTType(filenameExtension: "jar") ?? .data], allowsMultipleSelection: true) {
            if case .success(let urls) = $0 { _ = add(urls) }
        }
        .onAppear { if mc.isEmpty { mc = model.spec.mc } }
        .onChange(of: mc, initial: true) { reload() }
        .onChange(of: model.statusTick) { reload() }
    }

    // MARK: Client jars

    var clientJars: some View {
        VStack(alignment: .leading, spacing: 12) {
            SectionHeader(title: "Honeycrisp Client", subtitle: model.options.localJars == nil
                          ? "From the latest GitHub releases, SHA-256 verified"
                          : "Developer override: jars come from the local folder set in Settings") {
                HStack(spacing: 8) {
                    if model.checkingReleases { ProgressView().controlSize(.small) }
                    GlassButton(title: "Check for Updates", symbol: "arrow.clockwise") { Task { await model.refreshReleases(force: true) } }
                        .disabled(model.checkingReleases)
                }
            }
            let installed = model.installedJars(spec.mc)
            HStack(spacing: 12) {
                ForEach(GitHub.Project.allCases, id: \.self) { p in
                    let jar = installed.first { $0.project.hasPrefix(p.rawValue) && (p != .graft || !$0.project.hasPrefix("graftshaders")) }
                    let latest = model.releases[p]?.jar(p, mc: spec.mc)
                    let tag = model.releases[p]?.tag_name
                    let update = latest != nil && tag != nil && (jar?.file != latest?.name || jar?.tag != tag) && model.options.localJars == nil
                    VStack(alignment: .leading, spacing: 10) {
                        HStack {
                            ProjectGlyph(project: p, size: 32)
                            Spacer()
                            if update { Badge(text: "Update", symbol: "arrow.down", color: Theme.Palette.amber, filled: true) }
                        }
                        VStack(alignment: .leading, spacing: 2) {
                            Text(p.title).font(.system(size: 14, weight: .semibold))
                            Text(jar.map { $0.version } ?? (latest == nil && model.releases[p] != nil ? "Not available for \(spec.mc)" : "Not installed"))
                                .font(Theme.Fonts.numeral(jar == nil ? 12.5 : 20, jar == nil ? .regular : .bold))
                                .foregroundStyle(jar == nil ? .secondary : .primary)
                                .lineLimit(1)
                        }
                        if let jar {
                            Badge(text: jar.tag.map { "GitHub \($0)" } ?? "Local folder", symbol: jar.tag == nil ? "folder" : "shippingbox",
                                  color: jar.tag == nil ? .purple : .secondary, uppercase: false)
                                .help(jar.sourceDescription)
                        } else if update, let tag {
                            Text("\(tag) available").font(.system(size: 11.5)).foregroundStyle(Theme.Palette.amber)
                        }
                    }
                    .padding(14)
                    .frame(maxWidth: .infinity, minHeight: 132, alignment: .topLeading)
                    .glassCard(18)
                    .cardEdge(18)
                }
            }
            if let w = model.releaseWarning { Label(w, systemImage: "wifi.exclamationmark").font(.callout).foregroundStyle(.secondary) }
        }
    }

    // MARK: Your mods

    var yourMods: some View {
        VStack(alignment: .leading, spacing: 12) {
            SectionHeader(title: "Installed Mods", subtitle: "\(jars.count) jar\(jars.count == 1 ? "" : "s") in \(spec.mc)/mods · drop .jar files anywhere on this page") {
                HStack(spacing: 8) {
                    GlassButton(title: "Add Mods…", symbol: "plus") { importing = true }
                    GlassButton(title: "Open Folder", symbol: "folder") {
                        try? FileManager.default.createDirectory(at: modsDir, withIntermediateDirectories: true)
                        NSWorkspace.shared.open(modsDir)
                    }
                }
            }
            if jars.isEmpty {
                dropZone
            } else {
                VStack(spacing: 0) {
                    ForEach(Array(jars.enumerated()), id: \.element) { i, jar in
                        if i > 0 { Divider().padding(.leading, 58).opacity(0.6) }
                        jarRow(jar)
                    }
                }
                .padding(6)
                .glassCard(18)
                .cardEdge(18)
            }
        }
    }

    var dropZone: some View {
        VStack(spacing: 10) {
            Image(systemName: "shippingbox.and.arrow.backward.fill")
                .font(.system(size: 34))
                .symbolRenderingMode(.hierarchical)
                .foregroundStyle(Theme.Palette.brand)
            Text("Drop mods here").font(.system(size: 15, weight: .semibold))
            Text("Drag .jar files onto this page, or choose Add Mods…").font(.callout).foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity, minHeight: 170)
        .background {
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .strokeBorder(.primary.opacity(0.18), style: StrokeStyle(lineWidth: 1.5, dash: [7, 5]))
        }
    }

    func jarRow(_ jar: URL) -> some View {
        let name = jar.lastPathComponent
        let base = name.replacingOccurrences(of: ".disabled", with: "")
        let enabled = !name.hasSuffix(".disabled")
        let source = source(base)
        return HStack(spacing: 12) {
            ModTile(name: base)
                .saturation(enabled ? 1 : 0)
                .opacity(enabled ? 1 : 0.5)
            VStack(alignment: .leading, spacing: 3) {
                Text(Self.displayName(base)).font(.system(size: 13.5, weight: .semibold)).lineLimit(1)
                    .foregroundStyle(enabled ? .primary : .secondary)
                HStack(spacing: 6) {
                    Badge(text: source.0, symbol: source.1, color: source.2, uppercase: false)
                    Text(base).font(.system(size: 11)).foregroundStyle(.tertiary).lineLimit(1).truncationMode(.middle)
                }
            }
            Spacer()
            Button(role: .destructive) { try? FileManager.default.trashItem(at: jar, resultingItemURL: nil); reload() } label: {
                Image(systemName: "trash").font(.system(size: 12))
            }
            .buttonStyle(.borderless)
            .foregroundStyle(.secondary)
            .help("Move to Trash")
            Toggle("Enabled", isOn: Binding(get: { enabled }, set: { toggle(jar, $0) }))
                .toggleStyle(.switch).labelsHidden().controlSize(.small)
        }
        .padding(.horizontal, 10).padding(.vertical, 8)
        .hoverable(12)
    }

    /// Where a jar came from: a GitHub release (Graft, Graft Shaders, Honeycrisp), Modrinth (Sodium, Fabric API), or the user.
    func source(_ file: String) -> (String, String, Color) {
        if let j = model.installedJars(spec.mc).first(where: { $0.file == file }) {
            return j.tag.map { ("GitHub \($0)", "shippingbox", Color.secondary) } ?? ("Local", "folder", .purple)
        }
        if managed.contains(file) { return ("Modrinth", "arrow.down.circle", Color(red: 0.11, green: 0.75, blue: 0.45)) }
        return ("Added by you", "person.fill", Theme.Palette.amber)
    }

    /// "sodium-fabric-0.8.14+mc1.21.11.jar" → "Sodium Fabric".
    static func displayName(_ file: String) -> String {
        if let p = GitHub.Project.allCases.first(where: { file.hasPrefix($0.rawValue + "-") }) { return p.title }
        let fam = Catalog.family(file.replacingOccurrences(of: ".jar", with: ""))
        let words = (fam.isEmpty ? file : fam).split(whereSeparator: { $0 == "-" || $0 == "_" }).map { $0.prefix(1).uppercased() + $0.dropFirst() }
        return words.joined(separator: " ").replacingOccurrences(of: "Api", with: "API")
    }

    // MARK: Modules

    var modules: some View {
        VStack(alignment: .leading, spacing: 12) {
            SectionHeader(title: "Honeycrisp Modules", subtitle: "Same switches as the in-game menu (Right Shift). Changes apply on next launch.")
            ForEach(ModuleInfo.categories, id: \.self) { cat in
                VStack(alignment: .leading, spacing: 8) {
                    Text(cat).eyebrow().foregroundStyle(.secondary).padding(.leading, 4)
                    LazyVGrid(columns: [GridItem(.adaptive(minimum: 190), spacing: 10)], spacing: 10) {
                        ForEach(ModuleInfo.all.filter { $0.category == cat }) { m in
                            let on = config?.enabled(m) ?? m.onByDefault
                            HStack(spacing: 10) {
                                Image(systemName: m.symbol)
                                    .font(.system(size: 14, weight: .semibold))
                                    .foregroundStyle(on ? .white : .secondary)
                                    .frame(width: 30, height: 30)
                                    .background(on ? AnyShapeStyle(Theme.Palette.brand) : AnyShapeStyle(.primary.opacity(0.08)),
                                                in: .rect(cornerRadius: 8, style: .continuous))
                                Text(m.name).font(.system(size: 13, weight: .medium)).lineLimit(1)
                                Spacer(minLength: 4)
                                Toggle(m.name, isOn: Binding(get: { on }, set: { config?.set(m, $0) }))
                                    .toggleStyle(.switch).labelsHidden().controlSize(.mini)
                            }
                            .padding(10)
                            .glassCard(14)
                            .animation(Theme.Motion.snappy, value: on)
                        }
                    }
                }
            }
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

/// Colored rounded tile with the mod's initials, a stand-in for mod icons.
struct ModTile: View {
    var name: String

    var body: some View {
        let palette: [Color] = [Theme.Palette.amber, Theme.Palette.red, Color(red: 0.36, green: 0.62, blue: 0.98), Color(red: 0.30, green: 0.75, blue: 0.55),
                                Color(red: 0.70, green: 0.45, blue: 0.95), Theme.Palette.gold]
        let c = palette[abs(name.unicodeScalars.reduce(7) { $0 &* 31 &+ Int($1.value) }) % palette.count]
        let letters = ModsView.displayName(name).split(separator: " ").prefix(2).compactMap(\.first).map(String.init).joined()
        Text(letters.isEmpty ? "?" : letters)
            .font(.system(size: 13, weight: .bold, design: .rounded))
            .foregroundStyle(.white)
            .frame(width: 36, height: 36)
            .background(c, in: .rect(cornerRadius: 9, style: .continuous))
    }
}
