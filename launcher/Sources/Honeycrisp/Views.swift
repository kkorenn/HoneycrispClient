import HoneycrispCore
import SwiftUI

struct RootView: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        @Bindable var model = model
        let page = model.selection ?? .home
        GeometryReader { geo in
            let collapsed = model.sidebarCollapsed || geo.size.width < Theme.sidebarAutoCollapse
            ZStack(alignment: .topLeading) {
                Backdrop()
                HStack(spacing: 0) {
                    // Starts below the traffic lights (WindowChrome centers them at y 20), so they sit above the sidebar, not on it.
                    Sidebar(collapsed: collapsed)
                        .padding([.horizontal, .bottom], Theme.windowInset).padding(.top, 40)
                    // Every page stays built and only fades in or out: rebuilding one on each switch (Mods re-reads
                    // its folder, Home restarts its tasks) stalled the first frames of the transition.
                    ZStack {
                        pageLayer(.home) { HomeView() }
                        pageLayer(.versions) { VersionsView() }
                        pageLayer(.mods) { ModsView() }
                        pageLayer(.log) { LogView() }
                        pageLayer(.settings) { SettingsView() }
                    }
                    .glassButtonStyle()
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                }
                // Top of the sidebar, lined up with its icons; same in both states.
                Button { model.sidebarCollapsed.toggle() } label: {
                    Image(systemName: "sidebar.left").font(.system(size: 14, weight: .medium)).frame(width: 28, height: 22)
                }
                .buttonStyle(.plain).foregroundStyle(.secondary).contentShape(Rectangle())
                .help(collapsed ? "Expand Sidebar" : "Collapse Sidebar")
                .padding(.leading, Theme.windowInset + 8 + 10 + (22 - 28) / 2).padding(.top, 48)
            }
            .animation(Theme.Motion.spring, value: page)
            .animation(Theme.Motion.spring, value: collapsed)
        }
        .ignoresSafeArea()
        .tint(model.settings.accent.color ?? .accentColor)
        .sheet(isPresented: $model.showAccount) { AccountView().glassButtonStyle() }
    }
}

extension RootView {
    func pageLayer<V: View>(_ item: SidebarItem, @ViewBuilder _ content: () -> V) -> some View {
        let shown = (model.selection ?? .home) == item
        return content()
            .opacity(shown ? 1 : 0)
            .offset(y: shown ? 0 : 10)
            .allowsHitTesting(shown)
            .accessibilityHidden(!shown)
    }
}

// MARK: - Sidebar

struct Sidebar: View {
    @Environment(AppModel.self) private var model
    var collapsed: Bool

    var body: some View {
        @Bindable var model = model
        VStack(alignment: .leading, spacing: 2) {
            // The sidebar toggle floats here from RootView so it never moves.
            brand
                .padding(.top, 42).padding(.bottom, 18)

            // One glass pill behind the items that slides to the selected row, like a system tab bar.
            let index = CGFloat(SidebarItem.allCases.firstIndex(of: model.selection ?? .home) ?? 0)
            VStack(alignment: .leading, spacing: SidebarButton.spacing) {
                ForEach(SidebarItem.allCases) { item in
                    SidebarButton(title: item.rawValue, symbol: item.symbol, selected: (model.selection ?? .home) == item,
                                  collapsed: collapsed, badge: item == .log && model.crashReport != nil) {
                        withAnimation(Theme.Motion.spring) { model.selection = item }
                    }
                }
            }
            .background(alignment: .topLeading) {
                SelectionPill()
                    .frame(width: collapsed ? SidebarButton.height : nil, height: SidebarButton.height)
                    .frame(maxWidth: collapsed ? nil : .infinity, alignment: .leading)
                    .padding(.leading, collapsed ? 4 : 0)
                    .offset(y: index * (SidebarButton.height + SidebarButton.spacing))
                    .allowsHitTesting(false)
            }

            Spacer(minLength: 12)

            AccountChip(collapsed: collapsed)
                .padding(.top, 6)
        }
        .padding(.horizontal, 8).padding(.bottom, 8)
        .frame(width: collapsed ? Theme.sidebarCollapsedWidth : Theme.sidebarWidth)
        .frame(maxHeight: .infinity)
        .glassCard(Theme.sidebarRadius)
        .cardEdge(Theme.sidebarRadius)
    }

    var brand: some View {
        HStack(spacing: 9) {
            HoneycrispLogo(size: 28)
            if !collapsed {
                VStack(alignment: .leading, spacing: 0) {
                    Text("Honeycrisp").font(.system(size: 15, weight: .bold))
                    Text("Client").font(.system(size: 11, weight: .medium)).foregroundStyle(.secondary)
                }
                .transition(.opacity)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 8)
    }
}

/// The selected sidebar item's background: Liquid Glass on macOS 26, a tinted shape before.
struct SelectionPill: View {
    var body: some View {
        let shape = RoundedRectangle(cornerRadius: 10, style: .continuous)
        if #available(macOS 26, *), Theme.liquidGlass {
            Color.clear.glassEffect(.regular, in: shape)
        } else {
            shape.fill(.primary.opacity(0.10)).overlay { shape.strokeBorder(.primary.opacity(0.06)) }
        }
    }
}

struct SidebarButton: View {
    static let height: CGFloat = 34
    static let spacing: CGFloat = 2
    var title: String
    var symbol: String
    var selected: Bool
    var collapsed: Bool
    var badge = false
    var action: () -> Void
    @State private var hover = false

    var body: some View {
        Button(action: action) {
            HStack(spacing: 10) {
                Image(systemName: symbol)
                    .font(.system(size: 15, weight: .semibold))
                    .symbolRenderingMode(.hierarchical)
                    .foregroundStyle(selected ? AnyShapeStyle(Theme.Palette.brand) : hover ? AnyShapeStyle(.primary) : AnyShapeStyle(.secondary))
                    .frame(width: 22)
                    .overlay(alignment: .topTrailing) {
                        if badge { Circle().fill(.red).frame(width: 7, height: 7).offset(x: 3, y: -2) }
                    }
                if !collapsed {
                    Text(title).font(.system(size: 13.5, weight: selected ? .semibold : .medium))
                        .foregroundStyle(selected || hover ? .primary : .secondary)
                        .lineLimit(1)
                }
                if !collapsed { Spacer(minLength: 0) }
            }
            // Collapsed: a 34 pt square around the icon (icon stays where it is expanded).
            .padding(.horizontal, collapsed ? 6 : 10)
            .frame(maxWidth: collapsed ? Self.height : .infinity, minHeight: Self.height)
            .background {
                if !selected && hover {
                    RoundedRectangle(cornerRadius: 10, style: .continuous).fill(.white.opacity(0.12))
                }
            }
            .contentShape(.rect)
        }
        .buttonStyle(.plain)
        .padding(.leading, collapsed ? 4 : 0)
        .frame(maxWidth: .infinity, alignment: .leading)
        .help(title)
        .onHover { h in withAnimation(Theme.Motion.hover) { hover = h } }
        .animation(Theme.Motion.spring, value: selected)
    }
}

// MARK: - Account chip

struct Avatar: View {
    @Environment(AppModel.self) private var model
    var account: Account?
    var size = Theme.avatarSize

    var body: some View {
        Group {
            if let a = account, !model.isPreview, let url = Theme.avatarURL(a.uuid) {
                AsyncImage(url: url) { img in img.resizable().interpolation(.none) } placeholder: { monogram }
            } else {
                monogram
            }
        }
        .frame(width: size, height: size)
        .clipShape(.rect(cornerRadius: size * 0.22, style: .continuous))
        .overlay { RoundedRectangle(cornerRadius: size * 0.22, style: .continuous).strokeBorder(.white.opacity(0.18), lineWidth: 0.5) }
    }

    /// Drawn fallback: initial on a warm gradient picked from the name.
    var monogram: some View {
        let hues: [(Color, Color)] = [(Theme.Palette.amber, Theme.Palette.red), (Theme.Palette.gold, Theme.Palette.ember),
                                       (Color(red: 0.95, green: 0.42, blue: 0.48), Theme.Palette.crimson), (Color(red: 0.55, green: 0.62, blue: 0.98), Color(red: 0.36, green: 0.30, blue: 0.80))]
        let h = hues[abs((account?.name ?? "").unicodeScalars.reduce(0) { $0 &+ Int($1.value) }) % hues.count]
        return ZStack {
            h.0
            if let a = account {
                Text(a.name.prefix(1).uppercased()).font(.system(size: size * 0.5, weight: .bold, design: .rounded)).foregroundStyle(.white)
            } else {
                Image(systemName: "person.fill").font(.system(size: size * 0.5)).foregroundStyle(.white.opacity(0.9))
            }
        }
    }
}

struct AccountChip: View {
    @Environment(AppModel.self) private var model
    var collapsed = false

    var body: some View {
        Button { model.showAccount = true } label: {
            HStack(spacing: 9) {
                Avatar(account: model.account, size: collapsed ? 32 : Theme.avatarSize)
                if !collapsed {
                    VStack(alignment: .leading, spacing: 1) {
                        Text(model.account?.name ?? "Sign In").font(.system(size: 13, weight: .semibold)).lineLimit(1)
                        Text(model.account.map { $0.kind == .microsoft ? "Microsoft" : "Offline" } ?? "No account")
                            .font(.system(size: 11)).foregroundStyle(.secondary)
                    }
                    Spacer(minLength: 0)
                    Image(systemName: "chevron.up.chevron.down").font(.system(size: 10, weight: .semibold)).foregroundStyle(.tertiary)
                }
            }
            .padding(collapsed ? 4 : 7)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(RoundedRectangle(cornerRadius: 12, style: .continuous).fill(.primary.opacity(0.06)))
            .contentShape(.rect)
        }
        .buttonStyle(.plain)
        .hoverable(12)
        .help("Accounts")
    }
}

// MARK: - Home

struct HomeView: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 34) {
                hero
                ReleaseNotesSection()
                MinecraftUpdatesRow()
            }
            .padding(.bottom, Theme.s6)
        }
        .scrollIndicators(.hidden)
        .overlay(alignment: .top) {
            if let banner = model.banner {
                Toast(
                    text: banner,
                    crash: model.crashReport != nil,
                    playOffline: model.canOfferPlayOffline ? { model.playOffline() } : nil,
                    showLog: { withAnimation(Theme.Motion.spring) { model.selection = .log } },
                    dismiss: { model.banner = nil; model.canOfferPlayOffline = false }
                )
                .padding(.top, 14)
                .transition(.move(edge: .top).combined(with: .opacity))
            }
        }
        .overlay(alignment: .topTrailing) { HeaderButtons().padding(.top, 14).padding(.trailing, 16) }
        .animation(Theme.Motion.spring, value: model.banner)
        .task { await model.refreshReleases() }
        .task { await model.loadNews() }
    }

    var hero: some View {
        ZStack(alignment: .bottomLeading) {
            HeroArt()
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topTrailing)
                .offset(x: 70, y: -10)
                .allowsHitTesting(false)
            VStack(alignment: .leading, spacing: 0) {
                Text("Honeycrisp Client" + (model.releases[.honeycrisp].map { " · \($0.tag_name)" } ?? ""))
                    .eyebrow().foregroundStyle(.white.opacity(0.75))
                Text("Honeycrisp")
                    .font(Theme.Fonts.wordmark)
                    .kerning(-1.5)
                    .foregroundStyle(.white)
                    .padding(.top, 4)
                Text("Minecraft, native on Apple silicon.\nMetal rendering, shaders, and a HUD built for PvP.")
                    .font(.system(size: 16, weight: .medium))
                    .foregroundStyle(.white.opacity(0.82))
                    .lineSpacing(3)
                    .padding(.top, 8)
                InstanceFacts(spec: model.spec).padding(.top, 16)
                    .environment(\.colorScheme, .dark)
                PlayBar().padding(.top, 26)
            }
            .padding(.horizontal, Theme.pageInset - Theme.windowInset)
            .padding(.bottom, 4)
        }
        .frame(height: Theme.heroHeight)
    }
}

/// Folder and log buttons floating top-right of Home.
struct HeaderButtons: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        HStack(spacing: 8) {
            if model.checkingReleases { ProgressView().controlSize(.small).padding(.trailing, 4) }
            GlassButton(symbol: "folder", help: "Open Game Folder", large: true, circle: true) { model.openGameFolder() }
            GlassButton(symbol: "text.alignleft", help: "Game Log", large: true, circle: true) {
                withAnimation(Theme.Motion.spring) { model.selection = .log }
            }
        }
    }
}

/// "Fabric · Graft 1.4.0 · Shaders · Sodium · Java 25" under the tagline.
struct InstanceFacts: View {
    @Environment(AppModel.self) private var model
    var spec: InstanceSpec

    var body: some View {
        HStack(spacing: 6) {
            Badge(text: spec.loader.rawValue, symbol: spec.isLegacy ? "hammer.fill" : "square.stack.3d.up.fill", color: .white)
            if model.hasShaders(spec) { Badge(text: "Graft Shaders", symbol: "sparkles", color: .white) }
            if spec.sodiumSupported, model.settings.sodium || model.hasShaders(spec) { Badge(text: "Sodium", symbol: "bolt.fill", color: .white) }
            Badge(text: "Java \(spec.minJava)", color: .white)
            Badge(text: String(format: "%g GB", Double(model.settings.memoryMB) / 1024), symbol: "memorychip", color: .white)
        }
    }
}

struct Toast: View {
    var text: String
    var crash: Bool
    var playOffline: (() -> Void)? = nil
    var showLog: () -> Void
    var dismiss: () -> Void

    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: crash ? "exclamationmark.octagon.fill" : "exclamationmark.triangle.fill")
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(crash ? .red : Theme.warning)
            Text(text).font(.system(size: 13, weight: .medium)).lineLimit(2)
            if let playOffline {
                Button("Play Offline", action: playOffline)
                    .buttonStyle(.plain).font(.system(size: 13, weight: .semibold)).foregroundStyle(.tint)
            }
            Button(crash ? "View Crash Report" : "Show Log", action: showLog)
                .buttonStyle(.plain).font(.system(size: 13, weight: .semibold)).foregroundStyle(.tint)
            Button(action: dismiss) { Image(systemName: "xmark").font(.system(size: 10, weight: .bold)) }
                .buttonStyle(.plain).foregroundStyle(.secondary).help("Dismiss")
        }
        .padding(.leading, 14).padding(.trailing, 16).padding(.vertical, 10)
        .glassCapsule()
        .frame(maxWidth: 620)
    }
}

// MARK: - Play bar

struct PlayBar: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        let spec = model.spec
        HStack(spacing: 14) {
            VersionMenuButton(selection: Bindable(model).settings.lastVersion)
                .disabled(model.isBusy)
            status(spec)
                .frame(width: 232, alignment: .leading)
            action(spec)
        }
        .padding(8)
        .padding(.leading, 0)
        .glassCard(Theme.playHeight / 2 + 8)
        .cardEdge(Theme.playHeight / 2 + 8)
        .glassGroup(spacing: 12)
        .animation(Theme.Motion.spring, value: model.phase(spec))
    }

    @ViewBuilder func status(_ spec: InstanceSpec) -> some View {
        VStack(alignment: .leading, spacing: 3) {
            switch model.phase(spec) {
            case .working(let f, let message):
                Text(message).font(.system(size: 13, weight: .semibold)).lineLimit(1)
                Text(model.timeLeft(spec.mc, fraction: f) ?? "Estimating time left…")
                    .font(.system(size: 12)).foregroundStyle(.secondary).monospacedDigit()
            case .running:
                HStack(spacing: 6) {
                    PulsingDot()
                    Text("Running").font(.system(size: 13, weight: .semibold))
                }
                Text("Playing as \(model.account?.name ?? "—")").font(.system(size: 12)).foregroundStyle(.secondary).lineLimit(1)
            case .idle:
                let (title, detail) = statusText(spec)
                Text(title).font(.system(size: 13, weight: .semibold)).lineLimit(1)
                Text(detail).font(.system(size: 12)).foregroundStyle(.secondary).lineLimit(1)
            }
        }
    }

    func statusText(_ spec: InstanceSpec) -> (String, String) {
        let who = model.account.map { "as \($0.name)" } ?? "· sign in first"
        switch model.status(spec) {
        case .ready: return ("Ready to play", "Playing \(who)")
        case .needsUpdate: return ("Update available", model.releases[.honeycrisp].map { "Honeycrisp \($0.tag_name) and friends" } ?? "New client jars")
        case .notInstalled: return ("Not installed", "Downloads Minecraft, \(spec.loader.rawValue) and Graft")
        }
    }

    @ViewBuilder func action(_ spec: InstanceSpec) -> some View {
        switch model.phase(spec) {
        case .working(let f, _):
            Button {} label: {
                HStack(spacing: 10) {
                    ProgressRing(fraction: f).frame(width: 22, height: 22)
                    Text("\(Int(f * 100))%").monospacedDigit().contentTransition(.numericText())
                }
            }
            .buttonStyle(PlayButtonStyle())
            .allowsHitTesting(false)
        case .running:
            Button(role: .destructive) { model.stop() } label: {
                Label("Stop", systemImage: "stop.fill")
                    .labelStyle(PlayLabelStyle())
            }
            .buttonStyle(PlayButtonStyle(running: true))
        case .idle:
            let (title, symbol) = playLabel(spec)
            Button { model.play() } label: { Label(title, systemImage: symbol).labelStyle(PlayLabelStyle()) }
                .buttonStyle(PlayButtonStyle())
                .disabled(model.isBusy)
                .keyboardShortcut(.defaultAction)
        }
    }

    func playLabel(_ spec: InstanceSpec) -> (String, String) {
        switch model.status(spec) {
        case .notInstalled: ("Install", "arrow.down")
        case .needsUpdate: ("Update", "arrow.triangle.2.circlepath")
        case .ready: ("Play", "play.fill")
        }
    }
}

struct PlayLabelStyle: LabelStyle {
    func makeBody(configuration: Configuration) -> some View {
        HStack(spacing: 10) { configuration.icon.font(.system(size: 16, weight: .black)); configuration.title }
    }
}

struct ProgressRing: View {
    var fraction: Double
    var lineWidth: CGFloat = 3

    var body: some View {
        ZStack {
            Circle().stroke(.white.opacity(0.25), lineWidth: lineWidth)
            Circle().trim(from: 0, to: max(0.02, fraction))
                .stroke(.white, style: StrokeStyle(lineWidth: lineWidth, lineCap: .round))
                .rotationEffect(.degrees(-90))
                .animation(Theme.Motion.spring, value: fraction)
        }
    }
}

struct PulsingDot: View {
    @State private var on = false
    var body: some View {
        Circle().fill(Theme.Palette.running).frame(width: 8, height: 8)
            .background { Circle().fill(Theme.Palette.running.opacity(0.45)).scaleEffect(on ? 2.4 : 1).opacity(on ? 0 : 1) }
            .onAppear { withAnimation(.easeOut(duration: 1.4).repeatForever(autoreverses: false)) { on = true } }
    }
}

// MARK: - Version menu

/// Glass button showing the big version number and loader; opens a popover list of every version.
struct VersionMenuButton: View {
    @Environment(AppModel.self) private var model
    @Binding var selection: String
    var compact = false
    @State private var open = false

    var body: some View {
        let spec = Catalog.spec(selection) ?? Catalog.instances[0]
        Button { open.toggle() } label: {
            HStack(spacing: 12) {
                VStack(alignment: .leading, spacing: 1) {
                    Text("Version").eyebrow().foregroundStyle(.secondary)
                    HStack(alignment: .firstTextBaseline, spacing: 7) {
                        Text(spec.mc).font(Theme.Fonts.numeral(compact ? 17 : 21)).monospacedDigit()
                        Text(spec.loader.rawValue).font(.system(size: 11, weight: .semibold)).foregroundStyle(.secondary)
                    }
                }
                Image(systemName: "chevron.up.chevron.down").font(.system(size: 11, weight: .bold)).foregroundStyle(.secondary)
            }
            .padding(.leading, 18).padding(.trailing, 14)
            .frame(height: compact ? 46 : Theme.playHeight)
            .contentShape(.capsule)
        }
        .buttonStyle(.plain)
        .glassCapsule(interactive: true)
        .hoverable(Theme.playHeight / 2)
        .popover(isPresented: $open, arrowEdge: .top) {
            VersionList(selection: $selection) { open = false }
        }
        .help("Choose Version")
    }
}

struct VersionList: View {
    @Environment(AppModel.self) private var model
    @Binding var selection: String
    var done: () -> Void

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 2) {
                group("Modern", Catalog.instances.filter { !$0.isLegacy })
                group("Legacy", Catalog.instances.filter(\.isLegacy)).padding(.top, 8)
            }
            .padding(8)
        }
        .frame(width: 300, height: 430)
    }

    func group(_ title: String, _ specs: [InstanceSpec]) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(title).eyebrow().foregroundStyle(.secondary).padding(.horizontal, 10).padding(.vertical, 4)
            ForEach(specs) { s in
                Button { selection = s.mc; done() } label: {
                    HStack(spacing: 10) {
                        Text(s.mc).font(Theme.Fonts.numeral(16, .semibold)).monospacedDigit().frame(width: 64, alignment: .leading)
                        Badge(text: s.loader.rawValue, color: Theme.loaderColor(s.loader))
                        if model.hasShaders(s) { Image(systemName: "sparkles").font(.system(size: 11)).foregroundStyle(Theme.shaders).help("Graft Shaders") }
                        Spacer()
                        StatusGlyph(spec: s)
                        Image(systemName: "checkmark").font(.system(size: 12, weight: .bold)).foregroundStyle(.tint)
                            .opacity(s.mc == selection ? 1 : 0)
                    }
                    .padding(.horizontal, 10).frame(height: 34)
                    .background(RoundedRectangle(cornerRadius: 9, style: .continuous).fill(s.mc == selection ? Color.primary.opacity(0.08) : .clear))
                    .contentShape(.rect)
                }
                .buttonStyle(.plain)
                .hoverable(9)
            }
        }
    }
}

struct StatusGlyph: View {
    @Environment(AppModel.self) private var model
    var spec: InstanceSpec

    var body: some View {
        Group {
            switch model.phase(spec) {
            case .working(let f, _): ProgressRing(fraction: f, lineWidth: 2).frame(width: 13, height: 13).foregroundStyle(.tint)
            case .running: PulsingDot()
            case .idle:
                switch model.status(spec) {
                case .ready: Image(systemName: "checkmark.circle.fill").foregroundStyle(.green)
                case .needsUpdate: Image(systemName: "arrow.down.circle.fill").foregroundStyle(Theme.Palette.amber)
                case .notInstalled: Image(systemName: "arrow.down.circle").foregroundStyle(.tertiary)
                }
            }
        }
        .font(.system(size: 13))
    }
}

// MARK: - What's New

/// Release notes from the latest Graft, Graft Shaders and Honeycrisp GitHub releases.
struct ReleaseNotesSection: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            SectionHeader(title: "What's New", subtitle: "Latest releases of the client, renderer and shaders")
            HStack(alignment: .top, spacing: 14) {
                ForEach([GitHub.Project.honeycrisp, .graft, .graftshaders], id: \.self) { p in
                    ReleaseCard(project: p, release: model.releases[p])
                }
            }
            if let w = model.releaseWarning {
                Label(w, systemImage: "wifi.exclamationmark").font(.callout).foregroundStyle(.secondary)
            }
        }
        .padding(.horizontal, Theme.pageInset - Theme.windowInset)
        .padding(.trailing, Theme.windowInset)
    }
}

struct SectionHeader<Trailing: View>: View {
    var title: String
    var subtitle: String?
    @ViewBuilder var trailing: Trailing

    var body: some View {
        HStack(alignment: .lastTextBaseline) {
            VStack(alignment: .leading, spacing: 2) {
                Text(title).font(Theme.Fonts.sectionTitle)
                if let subtitle { Text(subtitle).font(.system(size: 12.5)).foregroundStyle(.secondary) }
            }
            Spacer()
            trailing
        }
    }
}

extension SectionHeader where Trailing == EmptyView {
    init(title: String, subtitle: String? = nil) { self.init(title: title, subtitle: subtitle) { EmptyView() } }
}

struct ProjectGlyph: View {
    var project: GitHub.Project
    var size: CGFloat = 36

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: size * 0.28, style: .continuous)
        ZStack {
            switch project {
            case .honeycrisp:
                HoneycrispLogo(size: size)
            case .graft:
                shape.fill(Color(red: 0.80, green: 0.10, blue: 0.16))
                Image(systemName: "cube.transparent.fill").font(.system(size: size * 0.48, weight: .semibold)).foregroundStyle(.white)
            case .graftshaders:
                shape.fill(Color(red: 0.95, green: 0.32, blue: 0.36))
                Image(systemName: "sparkles").font(.system(size: size * 0.48, weight: .semibold)).foregroundStyle(.white)
            }
        }
        .frame(width: size, height: size)
        .overlay { shape.strokeBorder(.white.opacity(0.2), lineWidth: 0.5) }
    }
}

struct ReleaseCard: View {
    @Environment(\.openURL) private var openURL
    var project: GitHub.Project
    var release: GitHub.Release?

    var body: some View {
        Button {
            if let u = release?.html_url.flatMap(URL.init(string:)) { openURL(u) }
        } label: {
            VStack(alignment: .leading, spacing: 12) {
                HStack(spacing: 10) {
                    ProjectGlyph(project: project)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(project.title).font(.system(size: 14, weight: .semibold)).lineLimit(1)
                        Text(date ?? (release == nil ? "Checking…" : "Latest release")).font(.system(size: 11.5)).foregroundStyle(.secondary)
                    }
                    Spacer(minLength: 0)
                    if let tag = release?.tag_name { Badge(text: tag, color: Theme.Palette.amber, uppercase: false) }
                }
                VStack(alignment: .leading, spacing: 6) {
                    if let release {
                        ForEach(Array(Self.highlights(release.body).enumerated()), id: \.offset) { _, line in
                            HStack(alignment: .firstTextBaseline, spacing: 7) {
                                Circle().fill(Theme.Palette.amber).frame(width: 4, height: 4).offset(y: -2)
                                Text(line).font(.system(size: 12.5)).foregroundStyle(.secondary).lineLimit(2)
                            }
                        }
                    } else {
                        ForEach(0..<3, id: \.self) { i in
                            RoundedRectangle(cornerRadius: 4).fill(.primary.opacity(0.08)).frame(width: [180, 150, 120][i], height: 9)
                        }
                    }
                }
                .frame(maxHeight: .infinity, alignment: .top)
                HStack(spacing: 4) {
                    Text("Release notes")
                    Image(systemName: "arrow.up.right")
                }
                .font(.system(size: 12, weight: .semibold)).foregroundStyle(.tint)
                .opacity(release?.html_url == nil ? 0 : 1)
            }
            .padding(16)
            .frame(maxWidth: .infinity, minHeight: 196, alignment: .topLeading)
            .contentShape(.rect)
        }
        .buttonStyle(.plain)
        .glassCard()
        .cardEdge()
        .hoverable(Theme.cardRadius, lift: true)
    }

    var date: String? {
        guard let s = release?.published_at, let d = try? Date(s, strategy: .iso8601) else { return nil }
        return d.formatted(.dateTime.month(.abbreviated).day().year())
    }

    /// First bullet points of a GitHub release body (Markdown), headings and blank lines skipped.
    static func highlights(_ body: String?) -> [String] {
        let lines = (body ?? "").components(separatedBy: .newlines).map { $0.trimmingCharacters(in: .whitespaces) }
        let bullets = lines.filter { $0.hasPrefix("- ") || $0.hasPrefix("* ") }.map { String($0.dropFirst(2)) }
        let picked = bullets.isEmpty ? lines.filter { !$0.isEmpty && !$0.hasPrefix("#") } : bullets
        return Array(picked.prefix(3)).map { $0.replacingOccurrences(of: "**", with: "").replacingOccurrences(of: "`", with: "") }
            .ifEmpty(["No notes for this release."])
    }
}

extension Array {
    func ifEmpty(_ fallback: Self) -> Self { isEmpty ? fallback : self }
}

/// Small secondary row of recent Minecraft releases (snapshots left out).
struct MinecraftUpdatesRow: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        let releases = model.news.filter { $0.type == "release" }.prefix(4)
        if !releases.isEmpty {
            VStack(alignment: .leading, spacing: 10) {
                Text("Minecraft updates").eyebrow().foregroundStyle(.secondary)
                HStack(spacing: 8) {
                    ForEach(Array(releases)) { e in
                        HStack(spacing: 6) {
                            Text(e.id).font(Theme.Fonts.numeral(13, .semibold))
                            Text(e.releaseTime, format: .dateTime.month(.abbreviated).day()).font(.system(size: 11.5)).foregroundStyle(.secondary)
                            if Catalog.spec(e.id) != nil {
                                Image(systemName: "checkmark.seal.fill").font(.system(size: 11)).foregroundStyle(Theme.Palette.amber).help("Supported by Honeycrisp")
                            }
                        }
                        .padding(.horizontal, 12).padding(.vertical, 7)
                        .glassCapsule()
                    }
                }
                .glassGroup(spacing: 8)
            }
            .padding(.horizontal, Theme.pageInset - Theme.windowInset)
        }
    }
}

// MARK: - Page chrome

/// Large left-aligned page title with an optional trailing control, used by Versions, Mods and Settings.
struct PageHeader<Trailing: View>: View {
    var title: String
    var subtitle: String
    @ViewBuilder var trailing: Trailing

    var body: some View {
        HStack(alignment: .bottom) {
            VStack(alignment: .leading, spacing: 4) {
                Text(title).font(Theme.Fonts.pageTitle).kerning(-0.5)
                Text(subtitle).font(.system(size: 13.5)).foregroundStyle(.secondary)
            }
            Spacer()
            trailing
        }
        .padding(.top, 46)
    }
}

/// Scrollable page body with consistent insets.
struct Page<Content: View>: View {
    @ViewBuilder var content: Content
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 26) { content }
                .padding(.leading, Theme.pageInset - Theme.windowInset)
                .padding(.trailing, Theme.pageInset)
                .padding(.bottom, Theme.s7)
                .frame(maxWidth: 980, alignment: .leading)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
        .scrollIndicators(.hidden)
    }
}

// MARK: - Versions

extension AppModel {
    /// Graft Shaders exists for this version: from the latest release when known, else the documented list.
    func hasShaders(_ s: InstanceSpec) -> Bool {
        if let r = releases[.graftshaders] { return r.jar(.graftshaders, mc: s.mc) != nil }
        return ["1.21.8", "1.21.10", "1.21.11", "26.2", "26.3"].contains(s.mc)
    }

    /// Download size of the client jars for this version from the latest releases.
    func clientJarBytes(_ s: InstanceSpec) -> Int {
        GitHub.Project.allCases.compactMap { releases[$0]?.jar($0, mc: s.mc)?.size }.reduce(0, +)
    }
}

struct VersionsView: View {
    @Environment(AppModel.self) private var model
    @State private var showInstall = false

    var body: some View {
        let installed = Catalog.instances.filter { model.status($0) != .notInstalled }.count
        Page {
            PageHeader(title: "Versions", subtitle: "\(Catalog.instances.count) supported versions · \(installed) installed") {
                GlassButton(title: "Install Version…", symbol: "plus", large: true) { showInstall = true }
            }
            if !model.installWarnings.isEmpty {
                VStack(alignment: .leading, spacing: 6) {
                    ForEach(model.installWarnings, id: \.self) { w in
                        Label(w, systemImage: "exclamationmark.triangle.fill").foregroundStyle(Theme.warning)
                    }
                }
                .font(.callout)
                .padding(14).frame(maxWidth: .infinity, alignment: .leading)
                .glassCard(16, tint: nil)
                .overlay { RoundedRectangle(cornerRadius: 16, style: .continuous).fill(Theme.warning.opacity(0.10)).allowsHitTesting(false) }
            }
            group("Modern", "Fabric · Graft · Sodium · Java 25", Catalog.instances.filter { !$0.isLegacy })
            group("Legacy", "Forge · LWJGL 2 on Apple silicon · Java 8", Catalog.instances.filter(\.isLegacy))
        }
        .sheet(isPresented: $showInstall) { InstallSheet() }
    }

    func group(_ title: String, _ subtitle: String, _ specs: [InstanceSpec]) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            SectionHeader(title: title, subtitle: subtitle)
            // Three equal columns, 14 pt between cards both ways (adaptive columns left uneven gaps).
            LazyVGrid(columns: Array(repeating: GridItem(.flexible(minimum: 180), spacing: 14), count: 3), spacing: 14) {
                ForEach(specs) { VersionCard(spec: $0) }
            }
        }
    }
}

struct VersionCard: View {
    @Environment(AppModel.self) private var model
    var spec: InstanceSpec

    var body: some View {
        let selected = model.settings.lastVersion == spec.mc
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                Badge(text: spec.loader.rawValue, color: Theme.loaderColor(spec.loader))
                Spacer()
                StatusPill(spec: spec)
            }
            Text(spec.mc)
                .font(Theme.Fonts.numeral(36, .heavy))
                .kerning(-0.5)
                .monospacedDigit()
                .padding(.top, 10)
            Spacer(minLength: 14)
            if case .working(let f, let m) = model.phase(spec) {
                VStack(alignment: .leading, spacing: 4) {
                    ProgressView(value: f).tint(Theme.Palette.amber)
                    Text(m).font(.system(size: 11)).foregroundStyle(.secondary).lineLimit(1)
                }
            } else {
                HStack(spacing: 6) {
                    let bytes = model.clientJarBytes(spec)
                    Text(bytes > 0 ? "Client jars " + bytes.formatted(.byteCount(style: .file)) : (model.status(spec) == .notInstalled ? "Not installed" : "Installed"))
                        .font(.system(size: 11.5)).foregroundStyle(.secondary)
                    Spacer()
                    actions
                }
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)  // every card fills its grid cell, whatever its badges
        .frame(height: 148)
        .contentShape(.rect)
        .onTapGesture { model.settings.lastVersion = spec.mc }
        .glassCard(20)
        .overlay {
            RoundedRectangle(cornerRadius: 20, style: .continuous)
                .strokeBorder(selected ? AnyShapeStyle(Theme.Palette.brand) : AnyShapeStyle(.white.opacity(0.08)), lineWidth: selected ? 2 : 0.75)
        }
        .hoverable(20, lift: true)
        .animation(Theme.Motion.spring, value: selected)
    }

    var actions: some View {
        HStack(spacing: 4) {
            switch model.status(spec) {
            case .notInstalled:
                Button("Install") { Task { _ = await model.install(spec) } }.disabled(model.isBusy)
                    .glassProminentStyle()
            case .needsUpdate:
                Button("Update") { Task { _ = await model.install(spec) } }.disabled(model.isBusy)
                    .glassProminentStyle()
            case .ready:
                EmptyView()
            }
            Menu {
                Button("Select for Play") { model.settings.lastVersion = spec.mc }
                Button(model.status(spec) == .notInstalled ? "Install" : "Reinstall / Update") {
                    Task { _ = await model.install(spec) }
                }
                .disabled(model.isBusy)
                Divider()
                Button("Open Game Folder") { model.openGameFolder(spec) }
                Button("Open Mods Folder") {
                    try? FileManager.default.createDirectory(at: model.paths.mods(spec.mc), withIntermediateDirectories: true)
                    NSWorkspace.shared.open(model.paths.mods(spec.mc))
                }
            } label: { Image(systemName: "ellipsis").frame(width: 22, height: 22) }
            .menuStyle(.borderlessButton)
            .menuIndicator(.hidden)
            .fixedSize()
        }
        .font(.system(size: 12, weight: .semibold))
        .glassButtonStyle()
        .controlSize(.small)
    }
}

struct StatusPill: View {
    @Environment(AppModel.self) private var model
    var spec: InstanceSpec

    var body: some View {
        switch model.phase(spec) {
        case .working(let f, _): Badge(text: "\(Int(f * 100))%", symbol: "arrow.down", color: Theme.Palette.amber)
        case .running: Badge(text: "Running", symbol: "play.fill", color: Theme.Palette.running)
        case .idle:
            switch model.status(spec) {
            case .ready: Badge(text: "Installed", symbol: "checkmark", color: .green)
            case .needsUpdate: Badge(text: "Update", symbol: "arrow.down", color: Theme.Palette.amber, filled: true)
            case .notInstalled: EmptyView()
            }
        }
    }
}

struct InstallSheet: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        let pending = Catalog.instances.filter { model.status($0) != .ready }
        VStack(alignment: .leading, spacing: 16) {
            HStack(spacing: 12) {
                Image(systemName: "square.stack.3d.up.fill").font(.system(size: 22)).foregroundStyle(Theme.Palette.brand)
                    .frame(width: 44, height: 44).background(.primary.opacity(0.06), in: .rect(cornerRadius: 12, style: .continuous))
                VStack(alignment: .leading, spacing: 2) {
                    Text("Install a Version").font(.system(size: 18, weight: .bold))
                    Text("Minecraft, the loader, Java and the Honeycrisp jars, verified.").font(.callout).foregroundStyle(.secondary)
                }
            }
            if pending.isEmpty {
                Label("Every supported version is installed and up to date.", systemImage: "checkmark.seal.fill").foregroundStyle(.green)
            }
            ScrollView {
                VStack(spacing: 2) {
                    ForEach(pending) { s in
                        HStack(spacing: 10) {
                            Text(s.mc).font(Theme.Fonts.numeral(16, .semibold)).monospacedDigit().frame(width: 70, alignment: .leading)
                            Badge(text: s.loader.rawValue, color: Theme.loaderColor(s.loader))
                            Spacer()
                            StatusPill(spec: s)
                            Button(model.status(s) == .needsUpdate ? "Update" : "Install") { Task { _ = await model.install(s) } }
                                .glassProminentStyle()
                                .disabled(model.isBusy)
                                .controlSize(.small)
                        }
                        .padding(.horizontal, 10).frame(height: 40)
                        .hoverable(10)
                    }
                }
            }
            .frame(minHeight: 260)
            HStack { Spacer(); Button("Done") { dismiss() }.keyboardShortcut(.defaultAction).controlSize(.large) }
        }
        .glassButtonStyle()
        .padding(22)
        .frame(width: 480)
    }
}

// MARK: - Log

struct LogView: View {
    enum Level: String, CaseIterable { case all = "All", warnings = "Warnings", errors = "Errors" }

    @Environment(AppModel.self) private var model
    @State private var tab = 0
    @State private var query = ""
    @State private var level = Level.all

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            PageHeader(title: "Game Log", subtitle: "\(filtered.count) of \(model.log.count) lines") {
                HStack(spacing: 8) {
                    GlassButton(title: "Copy", symbol: "doc.on.doc", large: true) {
                        NSPasteboard.general.clearContents()
                        NSPasteboard.general.setString(tab == 1 ? model.crashReport ?? "" : filtered.map(\.1).joined(separator: "\n"), forType: .string)
                    }
                    GlassButton(title: "Clear", symbol: "trash", large: true) { model.log.removeAll() }
                }
            }
            bar
            ScrollView {
                if tab == 1, let report = model.crashReport {
                    Text(report).frame(maxWidth: .infinity, alignment: .leading).padding(14)
                } else {
                    LazyVStack(alignment: .leading, spacing: 1) {
                        ForEach(filtered, id: \.0) { i, line in
                            HStack(alignment: .firstTextBaseline, spacing: 12) {
                                Text("\(i + 1)").foregroundStyle(.tertiary).frame(width: 40, alignment: .trailing)
                                Text(line).foregroundStyle(Self.color(line))
                                    .frame(maxWidth: .infinity, alignment: .leading)
                            }
                            .padding(.vertical, 1.5)
                            .background(Self.isError(line) ? Color.red.opacity(0.08) : .clear)
                        }
                    }
                    .padding(.vertical, 10).padding(.trailing, 14)
                }
            }
            .defaultScrollAnchor(.bottom)
            .font(.system(size: 12, design: .monospaced))
            .textSelection(.enabled)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .glassCard()
        }
        .padding(.leading, Theme.pageInset - Theme.windowInset)
        .padding(.trailing, Theme.pageInset)
        .padding(.bottom, Theme.windowInset + 8)
        .onChange(of: model.crashReport) { tab = model.crashReport == nil ? 0 : 1 }
    }

    var bar: some View {
        HStack(spacing: 10) {
            if model.crashReport != nil {
                GlassSegmented(selection: $tab, options: [(0, "Log"), (1, "Crash Report")])
            }
            GlassSegmented(selection: $level, options: Level.allCases.map { ($0, $0.rawValue) })
                .disabled(tab == 1)
            HStack(spacing: 6) {
                Image(systemName: "magnifyingglass").foregroundStyle(.secondary)
                TextField("Filter", text: $query).textFieldStyle(.plain)
                if !query.isEmpty {
                    Button { query = "" } label: { Image(systemName: "xmark.circle.fill") }.buttonStyle(.plain).foregroundStyle(.tertiary)
                }
            }
            .padding(.horizontal, 10).frame(width: 220, height: 28)
            .glassCapsule(interactive: true)
            Spacer()
        }
        .controlSize(.regular)
    }

    var filtered: [(Int, String)] {
        model.log.enumerated().filter { _, line in
            (query.isEmpty || line.localizedCaseInsensitiveContains(query)) && {
                switch level {
                case .all: true
                case .warnings: Self.isError(line) || line.contains("WARN")
                case .errors: Self.isError(line)
                }
            }()
        }.map { ($0.offset, $0.element) }
    }

    static func isError(_ line: String) -> Bool { line.contains("ERROR") || line.contains("Exception") || line.hasPrefix("\tat ") }

    static func color(_ line: String) -> Color {
        if isError(line) { return .red }
        if line.contains("WARN") { return .orange }
        if line.hasPrefix("$ ") { return .secondary }
        return .primary
    }
}
