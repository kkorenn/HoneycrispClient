import HoneycrispCore
import SwiftUI
import UniformTypeIdentifiers

struct ModsView: View {
    enum ImportKind { case mods, shaders, resourcepacks }

    @Environment(AppModel.self) private var model
    @State private var mc = ""
    @State private var config: ModuleConfig?
    @State private var jars: [URL] = []
    @State private var shaderPacks: [URL] = []
    @State private var resourcePacks: [URL] = []
    @State private var importKind: ImportKind?
    @State private var dropTargeted = false
    @State private var shaderDropTargeted = false
    @State private var resourceDropTargeted = false
    @State private var pendingTrashCoreJar: URL?

    var spec: InstanceSpec { Catalog.spec(mc) ?? model.spec }
    var modsDir: URL { model.paths.mods(spec.mc) }
    var shaderpacksDir: URL { model.paths.shaderpacks(spec.mc) }
    var resourcepacksDir: URL { model.paths.resourcepacks(spec.mc) }
    var managed: Set<String> { Set(model.installer.stamp(spec.mc)?.managedMods ?? []) }

    /// Shader packs apply when Graft Shaders is supported for this version or Forge has OptiFine installed.
    var supportsShaders: Bool {
        Catalog.hasShaders(spec) || model.hasShaders(spec) || (spec.loader == .forge && jars.contains {
            $0.lastPathComponent.localizedCaseInsensitiveContains("optifine")
        })
    }

    var body: some View {
        Page {
            PageHeader(title: "Mods", subtitle: "Client jars, your mods, packs and Honeycrisp modules for each version") {
                VersionMenuButton(selection: $mc, compact: true)
            }
            clientJars
            yourMods
            packsSection
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
        .dropDestination(for: URL.self) { urls, _ in handlePageDrop(urls) } isTargeted: { dropTargeted = $0 }
        .fileImporter(
            isPresented: Binding(get: { importKind != nil }, set: { if !$0 { importKind = nil } }),
            allowedContentTypes: allowedTypes(for: importKind ?? .mods),
            allowsMultipleSelection: true
        ) { result in
            let kind = importKind ?? .mods
            importKind = nil
            if case .success(let urls) = result {
                switch kind {
                case .mods: _ = addMods(urls)
                case .shaders: _ = addPacks(urls, to: shaderpacksDir)
                case .resourcepacks: _ = addPacks(urls, to: resourcepacksDir)
                }
            }
        }
        .alert(
            "Remove Core Client Jar?",
            isPresented: Binding(get: { pendingTrashCoreJar != nil }, set: { if !$0 { pendingTrashCoreJar = nil } }),
            presenting: pendingTrashCoreJar
        ) { jar in
            Button("Move to Trash", role: .destructive) {
                try? FileManager.default.trashItem(at: jar, resultingItemURL: nil)
                pendingTrashCoreJar = nil
                reload()
                model.statusTick &+= 1
            }
            Button("Cancel", role: .cancel) { pendingTrashCoreJar = nil }
        } message: { jar in
            Text("\(Self.stripDisabled(jar.lastPathComponent)) is a core Honeycrisp file. Moving it to Trash will require an Update before launching \(spec.mc).")
        }
        .onAppear { if mc.isEmpty { mc = model.spec.mc } }
        .onChange(of: mc, initial: true) { reload() }
        .onChange(of: model.statusTick) { reload() }
    }

    func allowedTypes(for kind: ImportKind) -> [UTType] {
        switch kind {
        case .mods: [UTType(filenameExtension: "jar") ?? .data]
        case .shaders, .resourcepacks: [.zip, .folder]
        }
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
                    let status = model.status(spec)
                    if status != .ready {
                        GlassButton(title: status == .notInstalled ? "Install" : "Update", symbol: "arrow.down") { Task { _ = await model.install(spec) } }
                            .disabled(model.isBusy)
                    }
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
                    GlassButton(title: "Add Mods…", symbol: "plus") { importKind = .mods }
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

    /// Strips only a trailing `.disabled` suffix, preserving any internal substring.
    static func stripDisabled(_ name: String) -> String {
        name.hasSuffix(".disabled") ? String(name.dropLast(".disabled".count)) : name
    }

    /// True for launcher-managed core jars (`graft-`, `graftshaders-`, `honeycrisp-`).
    static func isCoreJar(_ base: String) -> Bool {
        base.hasPrefix("graft-") || base.hasPrefix("graftshaders-") || base.hasPrefix("honeycrisp-")
    }

    func jarRow(_ jar: URL) -> some View {
        let name = jar.lastPathComponent
        let base = Self.stripDisabled(name)
        let enabled = !name.hasSuffix(".disabled")
        let isCore = Self.isCoreJar(base)
        let source = source(base)
        return HStack(spacing: 12) {
            ModTile(name: base)
                .saturation(enabled ? 1 : 0)
                .opacity(enabled ? 1 : 0.5)
            VStack(alignment: .leading, spacing: 3) {
                HStack(spacing: 6) {
                    Text(Self.displayName(base)).font(.system(size: 13.5, weight: .semibold)).lineLimit(1)
                        .foregroundStyle(enabled ? .primary : .secondary)
                    if isCore {
                        Badge(text: "Core", color: Theme.Palette.brand)
                    }
                }
                HStack(spacing: 6) {
                    Badge(text: source.0, symbol: source.1, color: source.2, uppercase: false)
                    Text(base).font(.system(size: 11)).foregroundStyle(.tertiary).lineLimit(1).truncationMode(.middle)
                }
            }
            Spacer()
            Button(role: .destructive) {
                if isCore {
                    pendingTrashCoreJar = jar
                } else {
                    try? FileManager.default.trashItem(at: jar, resultingItemURL: nil)
                    reload()
                    model.statusTick &+= 1
                }
            } label: {
                Image(systemName: "trash").font(.system(size: 12))
            }
            .buttonStyle(.borderless)
            .foregroundStyle(.secondary)
            .help(isCore ? "Remove Core Jar…" : "Move to Trash")
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

    // MARK: - Shader Packs & Resource Packs

    var packsSection: some View {
        HStack(alignment: .top, spacing: 12) {
            if supportsShaders {
                packCard(
                    title: "Shader Packs",
                    subtitle: "\(shaderPacks.count) in \(spec.mc)/shaderpacks",
                    symbol: "sparkles",
                    color: Theme.shaders,
                    items: shaderPacks,
                    dir: shaderpacksDir,
                    kind: .shaders,
                    targeted: $shaderDropTargeted
                )
            }
            packCard(
                title: "Resource Packs",
                subtitle: "\(resourcePacks.count) in \(spec.mc)/resourcepacks",
                symbol: "photo.on.rectangle.angled",
                color: Theme.Palette.amber,
                items: resourcePacks,
                dir: resourcepacksDir,
                kind: .resourcepacks,
                targeted: $resourceDropTargeted
            )
        }
    }

    func packCard(title: String, subtitle: String, symbol: String, color: Color, items: [URL], dir: URL, kind: ImportKind, targeted: Binding<Bool>) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .center, spacing: 8) {
                Image(systemName: symbol)
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(color)
                    .frame(width: 26, height: 26)
                    .background(color.opacity(0.14), in: .rect(cornerRadius: 7, style: .continuous))
                VStack(alignment: .leading, spacing: 1) {
                    Text(title).font(.system(size: 14, weight: .semibold))
                    Text(subtitle).font(.system(size: 11.5)).foregroundStyle(.secondary)
                }
                Spacer(minLength: 4)
                GlassButton(title: "Add…", symbol: "plus") { importKind = kind }
                GlassButton(symbol: "folder", help: "Open \(title) Folder") {
                    try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
                    NSWorkspace.shared.open(dir)
                }
            }
            if items.isEmpty {
                HStack(spacing: 8) {
                    Image(systemName: "arrow.down.doc")
                        .font(.system(size: 13))
                        .foregroundStyle(.secondary)
                    Text("Drop .zip or pack folders here")
                        .font(.system(size: 12))
                        .foregroundStyle(.secondary)
                }
                .frame(maxWidth: .infinity, minHeight: 54)
                .background {
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .strokeBorder(.primary.opacity(0.14), style: StrokeStyle(lineWidth: 1, dash: [5, 4]))
                }
            } else {
                VStack(spacing: 0) {
                    ForEach(Array(items.enumerated()), id: \.element) { i, pack in
                        if i > 0 { Divider().padding(.leading, 30).opacity(0.5) }
                        HStack(spacing: 8) {
                            Image(systemName: pack.hasDirectoryPath ? "folder.fill" : "doc.zipper")
                                .font(.system(size: 12))
                                .foregroundStyle(.secondary)
                                .frame(width: 20)
                            Text(pack.lastPathComponent.replacingOccurrences(of: ".zip", with: ""))
                                .font(.system(size: 12.5, weight: .medium))
                                .lineLimit(1)
                            Spacer(minLength: 4)
                            Button(role: .destructive) {
                                try? FileManager.default.trashItem(at: pack, resultingItemURL: nil)
                                reload()
                            } label: {
                                Image(systemName: "trash").font(.system(size: 11))
                            }
                            .buttonStyle(.borderless)
                            .foregroundStyle(.secondary)
                            .help("Move to Trash")
                        }
                        .padding(.horizontal, 8).padding(.vertical, 6)
                        .hoverable(8)
                    }
                }
            }
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .topLeading)
        .glassCard(18)
        .cardEdge(18)
        .overlay {
            if targeted.wrappedValue {
                RoundedRectangle(cornerRadius: 18, style: .continuous)
                    .strokeBorder(Theme.Palette.brand, style: StrokeStyle(lineWidth: 2, dash: [6, 4]))
            }
        }
        .dropDestination(for: URL.self) { urls, _ in addPacks(urls, to: dir) } isTargeted: { targeted.wrappedValue = $0 }
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
                            let unavailable = m.unavailableReason(on: spec.mc)
                            let available = unavailable == nil
                            let on = available && (config?.enabled(m) ?? m.defaultOn)
                            HStack(spacing: 10) {
                                Image(systemName: m.symbol)
                                    .font(.system(size: 14, weight: .semibold))
                                    .foregroundStyle(on ? .white : .secondary)
                                    .frame(width: 30, height: 30)
                                    .background(on ? AnyShapeStyle(Theme.Palette.brand) : AnyShapeStyle(.primary.opacity(0.08)),
                                                in: .rect(cornerRadius: 8, style: .continuous))
                                VStack(alignment: .leading, spacing: 1) {
                                    Text(m.name).font(.system(size: 13, weight: .medium)).lineLimit(1)
                                    if let unavailable {
                                        Text(unavailable).font(.system(size: 10, weight: .semibold)).foregroundStyle(.secondary).lineLimit(1)
                                    }
                                }
                                Spacer(minLength: 4)
                                Toggle(m.name, isOn: Binding(get: { on }, set: { config?.set(m, $0) }))
                                    .toggleStyle(.switch).labelsHidden().controlSize(.mini)
                                    .disabled(!available)
                            }
                            .padding(10)
                            .glassCard(14)
                            .opacity(available ? 1 : 0.55)
                            .help(unavailable ?? m.summary)
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
        shaderPacks = listPacks(in: shaderpacksDir)
        resourcePacks = listPacks(in: resourcepacksDir)
    }

    func listPacks(in dir: URL) -> [URL] {
        ((try? FileManager.default.contentsOfDirectory(at: dir, includingPropertiesForKeys: [.isDirectoryKey], options: [.skipsHiddenFiles])) ?? [])
            .filter { $0.pathExtension.lowercased() == "zip" || $0.hasDirectoryPath }
            .sorted { $0.lastPathComponent.localizedStandardCompare($1.lastPathComponent) == .orderedAscending }
    }

    func handlePageDrop(_ urls: [URL]) -> Bool {
        let jarURLs = urls.filter { $0.pathExtension.lowercased() == "jar" }
        let packURLs = urls.filter { $0.pathExtension.lowercased() == "zip" || $0.hasDirectoryPath }
        var ok = false
        if !jarURLs.isEmpty { ok = addMods(jarURLs) || ok }
        for p in packURLs {
            let isShader = supportsShaders && p.lastPathComponent.localizedCaseInsensitiveContains("shader")
            ok = addPacks([p], to: isShader ? shaderpacksDir : resourcepacksDir) || ok
        }
        return ok
    }

    func addMods(_ urls: [URL]) -> Bool {
        let fm = FileManager.default
        try? fm.createDirectory(at: modsDir, withIntermediateDirectories: true)
        var added = false
        for u in urls where u.pathExtension.lowercased() == "jar" {
            let ok = u.startAccessingSecurityScopedResource()
            defer { if ok { u.stopAccessingSecurityScopedResource() } }
            let dest = modsDir.appending(path: u.lastPathComponent)
            try? fm.removeItem(at: dest)
            added = (try? fm.copyItem(at: u, to: dest)) != nil || added
        }
        reload()
        model.statusTick &+= 1
        return added
    }

    func addPacks(_ urls: [URL], to destDir: URL) -> Bool {
        let fm = FileManager.default
        try? fm.createDirectory(at: destDir, withIntermediateDirectories: true)
        var added = false
        for u in urls where u.pathExtension.lowercased() == "zip" || u.hasDirectoryPath {
            let ok = u.startAccessingSecurityScopedResource()
            defer { if ok { u.stopAccessingSecurityScopedResource() } }
            let dest = destDir.appending(path: u.lastPathComponent)
            try? fm.removeItem(at: dest)
            added = (try? fm.copyItem(at: u, to: dest)) != nil || added
        }
        reload()
        return added
    }

    func toggle(_ jar: URL, _ on: Bool) {
        let name = jar.lastPathComponent
        let target = on ? Self.stripDisabled(name) : (name.hasSuffix(".disabled") ? name : name + ".disabled")
        try? FileManager.default.moveItem(at: jar, to: jar.deletingLastPathComponent().appending(path: target))
        reload()
        model.statusTick &+= 1
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
