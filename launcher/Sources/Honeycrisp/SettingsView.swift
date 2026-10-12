import AuthenticationServices
import CoreImage.CIFilterBuiltins
import HoneycrispCore
import SwiftUI

// MARK: - Settings building blocks

/// System Settings-style grouped card: a title above a glass inset holding rows separated by hairlines.
struct SettingsGroup<Content: View>: View {
    var title: String
    var footer: String?
    @ViewBuilder var content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            if !title.isEmpty { Text(title).font(.system(size: 13, weight: .semibold)).foregroundStyle(.secondary).padding(.leading, 6) }
            VStack(spacing: 0) {
                if #available(macOS 15, *) {
                    Group(subviews: content) { rows in
                        ForEach(Array(rows.enumerated()), id: \.offset) { i, row in
                            if i > 0 { Divider().padding(.leading, 52).opacity(0.6) }
                            row
                        }
                    }
                } else {
                    content
                }
            }
            .padding(.vertical, 4)
            .glassCard(16)
            .cardEdge(16)
            if let footer {
                Text(footer).font(.system(size: 11.5)).foregroundStyle(.secondary).padding(.horizontal, 6).fixedSize(horizontal: false, vertical: true)
            }
        }
    }
}

/// One settings row: colored symbol tile, title, optional subtitle, control on the right.
struct SettingRow<Control: View>: View {
    var symbol: String
    var color: Color
    var title: String
    var subtitle: String?
    @ViewBuilder var control: Control

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: symbol)
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(.white)
                .frame(width: 26, height: 26)
                .background(color, in: .rect(cornerRadius: 7, style: .continuous))
            VStack(alignment: .leading, spacing: 2) {
                Text(title).font(.system(size: 13, weight: .medium))
                if let subtitle {
                    Text(subtitle).font(.system(size: 11.5)).foregroundStyle(.secondary).lineLimit(2).truncationMode(.middle)
                }
            }
            .layoutPriority(1)
            Spacer(minLength: 12)
            control
        }
        .padding(.horizontal, 14).padding(.vertical, 10)
        .frame(minHeight: 50)
    }
}

extension SettingRow where Control == EmptyView {
    init(symbol: String, color: Color, title: String, subtitle: String? = nil) {
        self.init(symbol: symbol, color: color, title: title, subtitle: subtitle) { EmptyView() }
    }
}

// MARK: - Settings

struct SettingsView: View {
    @Environment(AppModel.self) private var model
    @State private var developerOpen = false

    var body: some View {
        @Bindable var model = model
        Page {
            PageHeader(title: "Settings", subtitle: "Game, Java, accounts and appearance") { EmptyView() }

            VStack(alignment: .leading, spacing: 24) {
                SettingsGroup(title: "Game") {
                    MemoryRow(memoryMB: $model.settings.memoryMB)
                    SettingRow(symbol: "macwindow", color: .blue, title: "Window size", subtitle: "0 × 0 uses the game's default") {
                        HStack(spacing: 6) {
                            TextField("Width", value: $model.settings.windowWidth, format: .number).glassField(width: 72)
                            Text("×").foregroundStyle(.secondary)
                            TextField("Height", value: $model.settings.windowHeight, format: .number).glassField(width: 72)
                        }
                        .labelsHidden().multilineTextAlignment(.trailing)
                    }
                    SettingRow(symbol: "rectangle.on.rectangle", color: .indigo, title: "Keep launcher open while playing") {
                        Toggle("", isOn: $model.settings.keepLauncherOpen).toggleStyle(.switch).labelsHidden().controlSize(.small)
                    }
                    SettingRow(symbol: "folder.fill", color: .cyan, title: "Game directory", subtitle: model.paths.instances.path) {
                        Button("Show in Finder") { NSWorkspace.shared.open(model.paths.root) }.controlSize(.small).fixedSize()
                    }
                }

                SettingsGroup(title: "Java", footer: "Automatic picks Mojang's Apple Silicon runtime that meets the version's needs (Java 25 for Graft on 1.21.x and 26.x, Java 8 for Forge versions). A custom JDK is used for every version.") {
                    SettingRow(symbol: "cup.and.saucer.fill", color: Theme.Palette.crimson, title: "Java runtime",
                               subtitle: model.settings.javaOverride.isEmpty ? "Automatic (Mojang runtime)" : model.settings.javaOverride) {
                        HStack(spacing: 6) {
                            if !model.settings.javaOverride.isEmpty { Button("Reset") { model.settings.javaOverride = "" } }
                            Button("Choose…") { chooseJava() }
                        }
                        .controlSize(.small)
                    }
                    SettingRow(symbol: "terminal.fill", color: .gray, title: "Extra JVM arguments") {
                        TextField("", text: $model.settings.extraJVMArgs, prompt: Text("-XX:+UseZGC"))
                            .font(.system(size: 12, design: .monospaced)).glassField(width: 240)
                    }
                }

                SettingsGroup(title: "Account", footer: "Offline mode works for singleplayer and offline-mode servers without Microsoft sign-in.") {
                    SettingRow(symbol: "person.crop.circle.fill", color: .pink, title: model.account?.name ?? "Not signed in",
                               subtitle: model.account.map { $0.kind == .microsoft ? "Microsoft account" : "Offline account" } ?? "Add a Microsoft or offline account") {
                        Button("Manage Accounts…") { model.showAccount = true }.controlSize(.small)
                    }
                    ClientIDStatusRow()
                }

                SettingsGroup(title: "Honeycrisp Client jars", footer: "Graft, Graft Shaders and Honeycrisp are downloaded from their latest GitHub releases and checked against GitHub's SHA-256. Graft Shaders only exists for 1.21.8, 1.21.10, 1.21.11, 26.2 and 26.3. Sodium (the build Graft supports) is installed wherever Graft Shaders is, never on 1.21.5.") {
                    SettingRow(symbol: "arrow.triangle.2.circlepath", color: Theme.Palette.amber, title: "Updates",
                               subtitle: model.releaseWarning ?? (model.releases.isEmpty ? "Not checked yet" : "Latest: " + GitHub.Project.allCases.compactMap { p in model.releases[p].map { "\(p.title) \($0.tag_name)" } }.joined(separator: " · "))) {
                        HStack(spacing: 8) {
                            if model.checkingReleases { ProgressView().controlSize(.small) }
                            Button("Check for Updates") { Task { await model.refreshReleases(force: true) } }.disabled(model.checkingReleases)
                        }
                        .controlSize(.small)
                    }
                    SettingRow(symbol: "bolt.fill", color: .green, title: "Install Sodium where supported", subtitle: "Always installed alongside Graft Shaders") {
                        Toggle("", isOn: $model.settings.sodium).toggleStyle(.switch).labelsHidden().controlSize(.small)
                    }
                    ForEach(Catalog.instances.filter { !model.installedJars($0.mc).isEmpty }) { s in
                        SettingRow(symbol: "shippingbox.fill", color: Theme.loaderColor(s.loader), title: s.title,
                                   subtitle: model.installedJars(s.mc).map { "\(GitHub.Project(rawValue: $0.project)?.title ?? $0.project) \($0.version)" }.joined(separator: " · "))
                    }
                }

                SettingsGroup(title: "Appearance") {
                    SettingRow(symbol: "circle.lefthalf.filled", color: Color(white: 0.4), title: "Theme") {
                        GlassSegmented(selection: $model.settings.appearance,
                                       options: LauncherSettings.Appearance.allCases.map { ($0, $0.rawValue) })
                    }
                    SettingRow(symbol: "paintpalette.fill", color: Theme.Palette.red, title: "Accent", subtitle: model.settings.accent.rawValue) {
                        AccentPicker(selection: $model.settings.accent)
                    }
                }

                developer

                about
            }
            .frame(maxWidth: 720, alignment: .leading)
        }
    }

    var developer: some View {
        @Bindable var model = model
        return VStack(alignment: .leading, spacing: 8) {
            Button { withAnimation(Theme.Motion.spring) { developerOpen.toggle() } } label: {
                HStack(spacing: 6) {
                    Text("Developer").font(.system(size: 13, weight: .semibold))
                    Image(systemName: "chevron.right").font(.system(size: 10, weight: .bold)).rotationEffect(.degrees(developerOpen ? 90 : 0))
                    if model.settings.devJarFolder != nil { Badge(text: "Local jars on", color: .purple) }
                }
                .foregroundStyle(.secondary)
                .padding(.leading, 6)
                .contentShape(.rect)
            }
            .buttonStyle(.plain)
            if developerOpen {
                SettingsGroup(title: "", footer: "A local jar folder replaces GitHub releases with your own builds (e.g. Graft/dist). Register your own Azure app and request Minecraft API access from Mojang; see launcher/README.md.") {
                    SettingRow(symbol: "hammer.fill", color: .purple, title: "Use local jar folder", subtitle: model.settings.devJarFolder ?? "Off (GitHub Releases)") {
                        HStack(spacing: 6) {
                            if model.settings.devJarFolder != nil { Button("Clear") { model.settings.devJarFolder = nil } }
                            Button("Choose…") { chooseFolder() }
                        }
                        .controlSize(.small)
                    }
                    SettingRow(symbol: "key.fill", color: .teal, title: "Azure client ID") {
                        TextField("", text: $model.settings.clientID,
                                  prompt: Text(Bundle.main.object(forInfoDictionaryKey: "MCClientID") as? String ?? "00000000-0000-0000-0000-000000000000"))
                            .font(.system(size: 12, design: .monospaced)).glassField(width: 290)
                    }
                }
                .transition(.opacity.combined(with: .move(edge: .top)))
            }
        }
    }

    var about: some View {
        HStack(spacing: 14) {
            HoneycrispLogo(size: 44)
            VStack(alignment: .leading, spacing: 2) {
                Text("Honeycrisp").font(.system(size: 15, weight: .bold))
                Text("Version \(AppModel.version) · Graft, Graft Shaders and Honeycrisp are LGPL-3.0").font(.system(size: 12)).foregroundStyle(.secondary)
            }
            Spacer()
            GlassButton(title: "Data Folder", symbol: "folder") { NSWorkspace.shared.open(model.paths.root) }
        }
        .padding(14)
        .glassCard(16)
    }

    func chooseJava() {
        let p = NSOpenPanel()
        p.message = "Choose a java executable (…/Contents/Home/bin/java) or a JDK folder"
        p.canChooseDirectories = true
        p.treatsFilePackagesAsDirectories = true
        guard p.runModal() == .OK, let u = p.url else { return }
        let candidates = [u, u.appending(path: "bin/java"), u.appending(path: "Contents/Home/bin/java")]
        if let java = candidates.first(where: { !$0.hasDirectoryPath && FileManager.default.isExecutableFile(atPath: $0.path) }) {
            model.settings.javaOverride = java.path
        }
    }

    func chooseFolder() {
        let p = NSOpenPanel()
        p.canChooseFiles = false
        p.canChooseDirectories = true
        if p.runModal() == .OK, let u = p.url { model.settings.devJarFolder = u.path }
    }
}

/// Memory slider with GB tick labels and the Mac's installed memory for context.
struct MemoryRow: View {
    @Binding var memoryMB: Int

    var body: some View {
        let gb = Double(memoryMB) / 1024
        VStack(alignment: .leading, spacing: 8) {
            SettingRow(symbol: "memorychip.fill", color: .green, title: "Memory",
                       subtitle: "Of \(ProcessInfo.processInfo.physicalMemory / (1 << 30)) GB in this Mac") {
                Text(String(format: gb.truncatingRemainder(dividingBy: 1) == 0 ? "%.0f GB" : "%.1f GB", gb))
                    .font(Theme.Fonts.numeral(20)).monospacedDigit().contentTransition(.numericText())
            }
            VStack(spacing: 2) {
                Slider(value: Binding(get: { Double(memoryMB) }, set: { memoryMB = Int($0) }), in: 2048...8192, step: 512)
                HStack {
                    ForEach([2, 4, 6, 8], id: \.self) { g in
                        Text("\(g) GB").font(.system(size: 10.5, weight: .medium)).foregroundStyle(.secondary)
                        if g != 8 { Spacer() }
                    }
                }
            }
            .padding(.leading, 52).padding(.trailing, 14).padding(.bottom, 12)
        }
        .animation(Theme.Motion.snappy, value: memoryMB)
    }
}

/// Microsoft sign-in state, phrased for people: "Waiting for Mojang approval" rather than a 403.
struct ClientIDStatusRow: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        let (symbol, color, title, detail) = info
        SettingRow(symbol: symbol, color: color, title: title, subtitle: detail)
    }

    var info: (String, Color, String, String) {
        switch model.clientIDState {
        case .missing: ("person.badge.key.fill", .gray, "Microsoft sign-in off", "No Azure client ID set. Offline mode is available.")
        case .notApproved: ("hourglass", Theme.Palette.amber, "Waiting for Mojang approval",
                            "Microsoft sign-in unlocks once Mojang approves this app. Use Offline mode until then.")
        case .unknown: ("checkmark.shield.fill", .blue, "Microsoft sign-in ready", "Approval is confirmed on the first sign-in.")
        case .approved: ("checkmark.seal.fill", .green, "Microsoft sign-in approved", "Signed-in accounts refresh automatically before launch.")
        }
    }
}

struct AccentPicker: View {
    @Binding var selection: Theme.Accent

    var body: some View {
        HStack(spacing: 7) {
            ForEach(Theme.Accent.allCases) { a in
                Button { withAnimation(Theme.Motion.snappy) { selection = a } } label: {
                    Circle()
                        .fill(a == .honeycrisp ? AnyShapeStyle(Theme.Palette.brand)
                              : AnyShapeStyle(a.color ?? .accentColor))
                        .frame(width: 18, height: 18)
                        .overlay { Circle().strokeBorder(.black.opacity(0.12), lineWidth: 0.5) }
                        .overlay { if selection == a { Circle().fill(.white).frame(width: 6, height: 6) } }
                        .overlay { Circle().stroke(.primary.opacity(selection == a ? 0.45 : 0), lineWidth: 2).padding(-3) }
                }
                .buttonStyle(.plain)
                .help(a.rawValue)
                .accessibilityLabel(a.rawValue)
            }
        }
    }
}

// MARK: - Account sheet

struct AccountView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.webAuthenticationSession) private var webAuth
    @Environment(\.openURL) private var openURL
    @Environment(\.dismiss) private var dismiss
    @State private var offlineName = ""
    @State private var busy = false
    @State private var error: String?
    @State private var deviceCode: MicrosoftAuth.DeviceCode?
    @State private var copied = false

    init(deviceCode: MicrosoftAuth.DeviceCode? = nil) { _deviceCode = State(initialValue: deviceCode) }

    var body: some View {
        VStack(alignment: .leading, spacing: 18) {
            HStack(spacing: 12) {
                HoneycrispLogo(size: 40)
                VStack(alignment: .leading, spacing: 1) {
                    Text("Accounts").font(.system(size: 20, weight: .bold))
                    Text("Choose who plays, or add an account.").font(.callout).foregroundStyle(.secondary)
                }
                Spacer()
                Button { dismiss() } label: { Image(systemName: "xmark").font(.system(size: 11, weight: .bold)).frame(width: 22, height: 22) }
                    .buttonStyle(.plain).foregroundStyle(.secondary)
                    .glass(Circle(), interactive: true)
                    .keyboardShortcut(.cancelAction)
            }

            if !model.store.accounts.isEmpty {
                VStack(spacing: 2) {
                    ForEach(model.store.accounts, id: \.uuid) { a in
                        let active = a.uuid == model.account?.uuid
                        HStack(spacing: 12) {
                            Avatar(account: a, size: 36)
                            VStack(alignment: .leading, spacing: 2) {
                                Text(a.name).font(.system(size: 14, weight: .semibold))
                                Badge(text: a.kind == .microsoft ? "Microsoft" : "Offline",
                                      symbol: a.kind == .microsoft ? "checkmark.shield.fill" : "wifi.slash",
                                      color: a.kind == .microsoft ? .green : .secondary)
                            }
                            Spacer()
                            if active {
                                Image(systemName: "checkmark.circle.fill").font(.system(size: 17)).foregroundStyle(.tint)
                            }
                            Button { model.signOut(a) } label: { Image(systemName: "rectangle.portrait.and.arrow.right") }
                                .buttonStyle(.borderless).help("Sign Out")
                        }
                        .padding(.horizontal, 10).padding(.vertical, 8)
                        .background(RoundedRectangle(cornerRadius: 12, style: .continuous).fill(active ? Color.primary.opacity(0.07) : .clear))
                        .contentShape(.rect)
                        .onTapGesture { model.select(a) }
                        .hoverable(12)
                    }
                }
            }

            // Microsoft
            VStack(alignment: .leading, spacing: 12) {
                ClientIDStatusRow()
                    .padding(.horizontal, -14).padding(.vertical, -10)
                if let dc = deviceCode {
                    deviceCodePanel(dc)
                } else {
                    HStack(spacing: 8) {
                        Button { Task { await signIn() } } label: {
                            Label("Sign in with Microsoft", systemImage: "person.crop.circle.badge.plus").frame(maxWidth: .infinity)
                        }
                        .glassProminentStyle().controlSize(.large)
                        Button("Use a Code") { Task { await signInWithCode() } }.controlSize(.large)
                    }
                    .disabled(busy || model.clientIDState == .missing)
                    if busy { ProgressView().controlSize(.small) }
                }
            }
            .padding(14)
            .glassCard(16)

            // Offline
            VStack(alignment: .leading, spacing: 8) {
                Text("Offline account").font(.system(size: 13, weight: .semibold))
                HStack(spacing: 8) {
                    TextField("Username", text: $offlineName).onSubmit(addOffline).glassField()
                    Button("Add", action: addOffline).disabled(!Self.validName(offlineName))
                }
                Text("Singleplayer and offline-mode servers only. 3–16 letters, digits or _.").font(.caption).foregroundStyle(.secondary)
            }

            if let error {
                Label(error, systemImage: "exclamationmark.triangle.fill").foregroundStyle(Theme.warning).font(.callout)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
        .padding(22)
        .frame(width: 440)
    }

    func deviceCodePanel(_ dc: MicrosoftAuth.DeviceCode) -> some View {
        HStack(alignment: .top, spacing: 16) {
            VStack(alignment: .leading, spacing: 10) {
                Text("Enter this code at").font(.callout).foregroundStyle(.secondary)
                + Text(" " + dc.verification_uri.replacingOccurrences(of: "https://", with: "")).font(.callout.weight(.semibold))
                HStack(spacing: 4) {
                    ForEach(Array(dc.user_code.enumerated()), id: \.offset) { _, ch in
                        Text(String(ch))
                            .font(.system(size: 20, weight: .bold, design: .monospaced))
                            .frame(width: ch == "-" ? 12 : 23, height: 36)
                            .background(.primary.opacity(ch == "-" ? 0 : 0.08), in: .rect(cornerRadius: 7, style: .continuous))
                    }
                }
                .textSelection(.enabled)
                HStack(spacing: 8) {
                    Button {
                        NSPasteboard.general.clearContents()
                        NSPasteboard.general.setString(dc.user_code, forType: .string)
                        copied = true
                    } label: { Label(copied ? "Copied" : "Copy", systemImage: copied ? "checkmark" : "doc.on.doc").fixedSize() }
                    Button { if let u = URL(string: dc.verification_uri) { openURL(u) } } label: { Label("Open", systemImage: "arrow.up.right").fixedSize() }
                        .glassProminentStyle()
                }
                .controlSize(.regular)
                HStack(spacing: 6) {
                    ProgressView().controlSize(.mini)
                    Text("Waiting for you to finish signing in…").font(.caption).foregroundStyle(.secondary).fixedSize()
                }
            }
            Spacer(minLength: 0)
            if let qr = Self.qr(dc.verification_uri) {
                qr.interpolation(.none).resizable().frame(width: 92, height: 92)
                    .padding(6).background(.white, in: .rect(cornerRadius: 10, style: .continuous))
                    .help("Scan to open \(dc.verification_uri)")
            }
        }
    }

    static func qr(_ text: String) -> Image? {
        let f = CIFilter.qrCodeGenerator()
        f.message = Data(text.utf8)
        f.correctionLevel = "M"
        guard let out = f.outputImage, let cg = CIContext().createCGImage(out, from: out.extent) else { return nil }
        return Image(decorative: cg, scale: 1)
    }

    static func validName(_ n: String) -> Bool { n.range(of: "^[A-Za-z0-9_]{3,16}$", options: .regularExpression) != nil }

    func addOffline() {
        guard Self.validName(offlineName) else { return }
        model.saveAccount(.offline(offlineName))
        offlineName = ""
    }

    func signIn() async {
        busy = true; error = nil
        defer { busy = false }
        let auth = MicrosoftAuth(clientID: model.effectiveClientID)
        let pkce = PKCE()
        let state = UUID().uuidString
        do {
            let callback = try await webAuth.authenticate(using: auth.authorizeURL(pkce: pkce, state: state),
                                                          callbackURLScheme: MicrosoftAuth.callbackScheme,
                                                          preferredBrowserSession: .ephemeral)
            let account = try await auth.redeem(code: MicrosoftAuth.code(from: callback, state: state), pkce: pkce)
            model.noteAuth(nil)
            model.saveAccount(account)
        } catch let e as ASWebAuthenticationSessionError where e.code == .canceledLogin {
            return
        } catch {
            model.noteAuth(error)
            self.error = error.localizedDescription
        }
    }

    func signInWithCode() async {
        busy = true; error = nil
        defer { busy = false; deviceCode = nil }
        let auth = MicrosoftAuth(clientID: model.effectiveClientID)
        do {
            let dc = try await auth.startDeviceCode()
            deviceCode = dc
            let account = try await auth.pollDeviceCode(dc)
            model.noteAuth(nil)
            model.saveAccount(account)
        } catch {
            model.noteAuth(error)
            self.error = error.localizedDescription
        }
    }
}
