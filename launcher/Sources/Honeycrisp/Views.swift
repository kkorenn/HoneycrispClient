import HoneycrispCore
import SwiftUI

struct RootView: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        @Bindable var model = model
        NavigationSplitView {
            List(SidebarItem.allCases, selection: $model.selection) { item in
                Label(item.rawValue, systemImage: item.symbol).tag(item)
            }
            .listStyle(.sidebar)
            .navigationSplitViewColumnWidth(min: Theme.sidebarWidth.lowerBound, ideal: Theme.sidebarWidth.lowerBound, max: Theme.sidebarWidth.upperBound)
            .safeAreaInset(edge: .bottom) { AccountRow().padding(8) }
        } detail: {
            Group {
                switch model.selection ?? .home {
                case .home: HomeView()
                case .versions: VersionsView()
                case .mods: ModsView()
                case .settings: SettingsView()
                }
            }
            .navigationTitle((model.selection ?? .home).rawValue)
            .toolbar {
                ToolbarItem(placement: .primaryAction) {
                    Button { model.openGameFolder() } label: { Label("Open Game Folder", systemImage: "folder") }
                        .help("Open Game Folder")
                }
            }
        }
    }
}

// MARK: - Account row (sidebar footer)

struct Avatar: View {
    var account: Account?
    var size = Theme.avatarSize

    var body: some View {
        Group {
            if let a = account, let url = Theme.avatarURL(a.uuid) {
                AsyncImage(url: url) { img in img.resizable().interpolation(.none) } placeholder: { fallback }
            } else {
                fallback
            }
        }
        .frame(width: size, height: size)
        .clipShape(.rect(cornerRadius: 4))
    }

    var fallback: some View {
        Image(systemName: account == nil ? "person.crop.circle" : "person.crop.circle.fill")
            .resizable().foregroundStyle(.secondary)
    }
}

struct AccountRow: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        @Bindable var model = model
        Button { model.showAccount.toggle() } label: {
            HStack(spacing: 8) {
                Avatar(account: model.account)
                VStack(alignment: .leading, spacing: 0) {
                    Text(model.account?.name ?? "Sign In").font(.body.weight(.medium)).lineLimit(1)
                    Text(model.account.map { $0.kind == .microsoft ? "Microsoft" : "Offline" } ?? "No account")
                        .font(.caption).foregroundStyle(.secondary)
                }
                Spacer(minLength: 0)
                Image(systemName: "chevron.up.chevron.down").font(.caption).foregroundStyle(.tertiary)
            }
            .contentShape(.rect)
        }
        .buttonStyle(.plain)
        .padding(6)
        .popover(isPresented: $model.showAccount, arrowEdge: .trailing) { AccountView().frame(width: 340) }
    }
}

// MARK: - Home

struct HomeView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.openWindow) private var openWindow

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                hero
                news
            }
        }
        .ignoresSafeArea(edges: .top)
        .task { await model.refreshReleases() }
        .task { await model.loadNews() }
    }

    var hero: some View {
        ZStack(alignment: .bottomLeading) {
            Theme.heroBackground(model.settings.accent.color ?? .accentColor)
                .heroExtension()
            VStack(alignment: .leading, spacing: 12) {
                Spacer()
                Text("Honeycrisp")
                    .font(.system(size: Theme.wordmarkSize, weight: .heavy))
                    .foregroundStyle(.white)
                    .shadow(radius: 8)
                if let banner = model.banner { Banner(text: banner) { openWindow(id: "log") } }
                PlayCluster()
            }
            .padding(Theme.padding * 1.5)
        }
        .frame(height: Theme.heroHeight)
    }

    var news: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("What's New").font(.title3.weight(.semibold)).padding(.horizontal, Theme.padding)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 12) {
                    if model.news.isEmpty {
                        ProgressView().frame(width: Theme.newsCard.width, height: Theme.newsCard.height)
                    }
                    ForEach(model.news) { NewsCard(entry: $0) }
                }
                .padding(.horizontal, Theme.padding)
            }
        }
        .padding(.bottom, Theme.padding)
    }
}

struct Banner: View {
    var text: String
    var showLog: () -> Void

    var body: some View {
        HStack(spacing: 8) {
            Image(systemName: "exclamationmark.triangle.fill").foregroundStyle(Theme.warning)
            Text(text).lineLimit(2)
            Button("Show Log", action: showLog).buttonStyle(.link)
        }
        .font(.callout)
        .padding(.horizontal, 12).padding(.vertical, 8)
        .background(.regularMaterial, in: .capsule)
    }
}

struct PlayCluster: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        @Bindable var model = model
        HStack(spacing: 12) {
            Picker("Version", selection: $model.settings.lastVersion) {
                ForEach(Catalog.instances) { Text($0.title).tag($0.mc) }
            }
            .labelsHidden()
            .pickerStyle(.menu)
            .fixedSize()
            .disabled(model.isBusy)

            switch model.phase(model.spec) {
            case .working(let f, let message):
                VStack(alignment: .leading, spacing: 4) {
                    Text("\(message) \(Int(f * 100))%").font(.callout).monospacedDigit().lineLimit(1)
                    ProgressView(value: f)
                }
                .frame(width: Theme.playMinWidth + 60)
            case .running:
                Label("Running", systemImage: "play.fill")
                    .font(.headline)
                    .frame(minWidth: Theme.playMinWidth - 60)
                Button("Stop", role: .destructive) { model.stop() }.playControlSize()
            case .idle:
                let (title, symbol) = playLabel
                Button { model.play() } label: {
                    Label(title, systemImage: symbol).font(.headline).frame(minWidth: Theme.playMinWidth - 40)
                }
                .buttonStyle(.borderedProminent)
                .buttonBorderShape(.capsule)
                .playControlSize()
                .disabled(model.isBusy)
                .keyboardShortcut(.defaultAction)
            }
        }
        .glassCluster()
    }

    var playLabel: (String, String) {
        switch model.status(model.spec) {
        case .notInstalled: ("Install", "arrow.down.circle")
        case .needsUpdate: ("Update", "arrow.down.circle")
        case .ready: ("Play", "play.fill")
        }
    }
}

struct NewsCard: View {
    var entry: VersionManifest.Entry

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Image(systemName: entry.type == "release" ? "newspaper" : "hammer")
                .font(.system(size: 28)).foregroundStyle(.tint)
            Spacer()
            Text(entry.type == "release" ? "Minecraft \(entry.id)" : "Snapshot \(entry.id)").font(.headline).lineLimit(1)
            Text(entry.releaseTime, format: .dateTime.day().month(.wide).year()).font(.footnote).foregroundStyle(.secondary)
            if Catalog.spec(entry.id) != nil {
                Text("Supported by Honeycrisp").font(.caption).foregroundStyle(.tint)
            }
        }
        .padding(14)
        .frame(width: Theme.newsCard.width, height: Theme.newsCard.height, alignment: .leading)
        .newsCardBackground()
    }
}

// MARK: - Versions

struct VersionsView: View {
    @Environment(AppModel.self) private var model
    @State private var showInstall = false

    var body: some View {
        VStack(spacing: 0) {
            Table(Catalog.instances) {
                TableColumn("Version") { s in Text(s.mc).fontWeight(s.mc == model.settings.lastVersion ? .semibold : .regular) }
                TableColumn("Loader") { s in Text(s.loader.rawValue) }
                TableColumn("Status") { s in StatusLabel(spec: s) }
                TableColumn("") { s in actions(s) }.width(40)
            }
            if !model.installWarnings.isEmpty {
                VStack(alignment: .leading, spacing: 4) {
                    ForEach(model.installWarnings, id: \.self) { w in
                        Label(w, systemImage: "exclamationmark.triangle.fill").foregroundStyle(Theme.warning)
                    }
                }
                .font(.callout).padding(Theme.padding).frame(maxWidth: .infinity, alignment: .leading)
            }
        }
        .toolbar {
            ToolbarItem { Button { showInstall = true } label: { Label("Install Version…", systemImage: "plus") } }
        }
        .sheet(isPresented: $showInstall) { InstallSheet() }
    }

    func actions(_ s: InstanceSpec) -> some View {
        Menu {
            Button("Select for Play") { model.settings.lastVersion = s.mc }
            Button(model.status(s) == .notInstalled ? "Install" : "Reinstall / Update") {
                Task { _ = await model.install(s) }
            }
            .disabled(model.isBusy)
            Button("Open Game Folder") { model.openGameFolder(s) }
            Button("Open Mods Folder") {
                try? FileManager.default.createDirectory(at: model.paths.mods(s.mc), withIntermediateDirectories: true)
                NSWorkspace.shared.open(model.paths.mods(s.mc))
            }
        } label: { Image(systemName: "ellipsis.circle") }
        .menuStyle(.borderlessButton)
        .menuIndicator(.hidden)
        .fixedSize()
    }
}

struct StatusLabel: View {
    @Environment(AppModel.self) private var model
    var spec: InstanceSpec

    var body: some View {
        switch model.phase(spec) {
        case .working(let f, _): ProgressView(value: f).frame(width: 100)
        case .running: Label("Running", systemImage: "play.fill").foregroundStyle(.tint)
        case .idle:
            switch model.status(spec) {
            case .ready: Label("Installed", systemImage: "checkmark.circle.fill").foregroundStyle(.green)
            case .needsUpdate: Label("Update", systemImage: "arrow.down.circle").foregroundStyle(.tint)
            case .notInstalled: Text("Not installed").foregroundStyle(.secondary)
            }
        }
    }
}

struct InstallSheet: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        let pending = Catalog.instances.filter { model.status($0) != .ready }
        VStack(alignment: .leading, spacing: 12) {
            Text("Install Version").font(.title2.weight(.bold))
            if pending.isEmpty {
                Text("Every supported version is installed and up to date.").foregroundStyle(.secondary)
            }
            List(pending) { s in
                HStack {
                    Text(s.title)
                    Spacer()
                    StatusLabel(spec: s)
                    Button("Install") { Task { _ = await model.install(s) } }.disabled(model.isBusy)
                }
            }
            .frame(minHeight: 240)
            HStack { Spacer(); Button("Done") { dismiss() }.keyboardShortcut(.defaultAction) }
        }
        .padding(Theme.padding)
        .frame(width: 440)
    }
}

// MARK: - Log

struct LogView: View {
    @Environment(AppModel.self) private var model
    @State private var tab = 0

    var body: some View {
        VStack(spacing: 0) {
            if model.crashReport != nil {
                Picker("", selection: $tab) {
                    Text("Log").tag(0)
                    Text("Crash Report").tag(1)
                }
                .pickerStyle(.segmented).labelsHidden().fixedSize().padding(8)
            }
            ScrollView {
                if tab == 1, let report = model.crashReport {
                    Text(report).frame(maxWidth: .infinity, alignment: .leading).padding(8)
                } else {
                    LazyVStack(alignment: .leading, spacing: 0) {
                        ForEach(Array(model.log.enumerated()), id: \.offset) { _, line in
                            Text(line).foregroundStyle(color(line)).frame(maxWidth: .infinity, alignment: .leading)
                        }
                    }
                    .padding(8)
                }
            }
            .defaultScrollAnchor(.bottom)
            .font(.system(.callout, design: .monospaced))
            .textSelection(.enabled)
        }
        .onChange(of: model.crashReport) { tab = model.crashReport == nil ? 0 : 1 }
        .toolbar {
            Button {
                NSPasteboard.general.clearContents()
                NSPasteboard.general.setString(tab == 1 ? model.crashReport ?? "" : model.log.joined(separator: "\n"), forType: .string)
            } label: { Label("Copy", systemImage: "doc.on.doc") }
            Button { model.log.removeAll() } label: { Label("Clear", systemImage: "trash") }
        }
        .navigationTitle("Game Log")
    }

    func color(_ line: String) -> Color {
        if line.contains("ERROR") || line.contains("Exception") || line.hasPrefix("\tat ") { return .red }
        if line.contains("WARN") { return .orange }
        return .primary
    }
}
