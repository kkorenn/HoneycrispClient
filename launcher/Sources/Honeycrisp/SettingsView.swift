import AuthenticationServices
import HoneycrispCore
import SwiftUI

struct SettingsView: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        @Bindable var model = model
        Form {
            Section("Game") {
                LabeledContent("Memory") {
                    HStack {
                        Slider(value: Binding(get: { Double(model.settings.memoryMB) }, set: { model.settings.memoryMB = Int($0) }),
                               in: 2048...8192, step: 512)
                            .frame(minWidth: 160)
                        Text(String(format: "%.1f GB", Double(model.settings.memoryMB) / 1024)).monospacedDigit().frame(width: 56, alignment: .trailing)
                    }
                }
                LabeledContent("Window size") {
                    HStack {
                        TextField("Width", value: $model.settings.windowWidth, format: .number).frame(width: 70)
                        Text("×")
                        TextField("Height", value: $model.settings.windowHeight, format: .number).frame(width: 70)
                    }
                    .labelsHidden()
                }
                Toggle("Keep launcher open while playing", isOn: $model.settings.keepLauncherOpen)
                LabeledContent("Game directory") {
                    HStack {
                        Text(model.paths.instances.path).lineLimit(1).truncationMode(.middle).foregroundStyle(.secondary)
                        Button { NSWorkspace.shared.open(model.paths.root) } label: { Image(systemName: "folder") }.buttonStyle(.borderless)
                    }
                }
            }

            Section {
                LabeledContent("Java runtime") {
                    HStack {
                        Text(model.settings.javaOverride.isEmpty ? "Automatic (Mojang runtime)" : model.settings.javaOverride)
                            .lineLimit(1).truncationMode(.middle).foregroundStyle(.secondary)
                        Button("Choose…") { chooseJava() }
                        if !model.settings.javaOverride.isEmpty { Button("Reset") { model.settings.javaOverride = "" } }
                    }
                }
                TextField("Extra JVM arguments", text: $model.settings.extraJVMArgs, prompt: Text("-XX:+UseZGC"))
            } header: { Text("Java") } footer: {
                Text("Automatic picks Mojang's Apple Silicon runtime that meets the version's needs (Java 25 for Graft on 1.21.x and 26.x, Java 8 for Forge versions). A custom JDK is used for every version.")
            }

            Section {
                ForEach(Catalog.instances.filter { !model.installedJars($0.mc).isEmpty }) { s in
                    LabeledContent(s.title) {
                        VStack(alignment: .trailing, spacing: 2) {
                            ForEach(model.installedJars(s.mc), id: \.self) { j in
                                Text("\(GitHub.Project(rawValue: j.project)?.title ?? j.project) \(j.version) · \(j.tag ?? "local")")
                            }
                        }
                        .foregroundStyle(.secondary)
                    }
                }
                HStack {
                    Button("Check for Updates") { Task { await model.refreshReleases(force: true) } }.disabled(model.checkingReleases)
                    if model.checkingReleases { ProgressView().controlSize(.small) }
                }
                if let w = model.releaseWarning {
                    Label(w, systemImage: "exclamationmark.triangle.fill").foregroundStyle(Theme.warning)
                }
                Toggle("Install Sodium where supported", isOn: $model.settings.sodium)
                LabeledContent("Developer: use local jar folder") {
                    HStack {
                        Text(model.settings.devJarFolder ?? "Off (GitHub Releases)").lineLimit(1).truncationMode(.middle).foregroundStyle(.secondary)
                        Button("Choose…") { chooseFolder() }
                        if model.settings.devJarFolder != nil { Button("Clear") { model.settings.devJarFolder = nil } }
                    }
                }
            } header: { Text("Honeycrisp Client jars") } footer: {
                Text("Graft, Graft Shaders and Honeycrisp are downloaded from their latest GitHub releases and checked against GitHub's SHA-256. Graft Shaders only exists for 1.21.8, 1.21.10, 1.21.11, 26.2 and 26.3. Sodium (the build Graft supports) is installed wherever Graft Shaders is, never on 1.21.5. A local jar folder replaces GitHub for your own builds.")
            }

            Section {
                TextField("Azure client ID", text: $model.settings.clientID, prompt: Text(Bundle.main.object(forInfoDictionaryKey: "MCClientID") as? String ?? "00000000-0000-0000-0000-000000000000"))
                ClientIDStatus()
            } header: { Text("Microsoft sign-in") } footer: {
                Text("Register your own Azure app and request Minecraft API access from Mojang; see launcher/README.md. Offline mode works without it.")
            }

            Section("Appearance") {
                Picker("Theme", selection: $model.settings.appearance) {
                    ForEach(LauncherSettings.Appearance.allCases, id: \.self) { Text($0.rawValue) }
                }
                .pickerStyle(.segmented)
                LabeledContent("Accent") { AccentPicker(selection: $model.settings.accent) }
            }

            Section("About") {
                LabeledContent("Version", value: AppModel.version)
                LabeledContent("Data folder") {
                    Button { NSWorkspace.shared.open(model.paths.root) } label: { Label("Open", systemImage: "folder") }
                }
            }
        }
        .formStyle(.grouped)
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

struct ClientIDStatus: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        switch model.clientIDState {
        case .missing: Label("No client ID: Microsoft sign-in is off. Offline mode is available.", systemImage: "info.circle").foregroundStyle(.secondary)
        case .notApproved: Label("Client ID not approved yet. Mojang must enable Minecraft services for this app; use Offline mode until then.", systemImage: "exclamationmark.triangle.fill").foregroundStyle(Theme.warning)
        case .unknown: Label("Client ID set. Approval is confirmed on the first sign-in.", systemImage: "questionmark.circle").foregroundStyle(.secondary)
        case .approved: Label("Client ID approved.", systemImage: "checkmark.circle.fill").foregroundStyle(.green)
        }
    }
}

struct AccentPicker: View {
    @Binding var selection: Theme.Accent

    var body: some View {
        HStack(spacing: 6) {
            ForEach(Theme.Accent.allCases) { a in
                Button { selection = a } label: {
                    Circle()
                        .fill(a.color.map { AnyShapeStyle($0) } ?? AnyShapeStyle(AngularGradient(colors: [.red, .yellow, .green, .blue, .purple, .red], center: .center)))
                        .frame(width: 20, height: 20)
                        .overlay { if selection == a { Image(systemName: "checkmark").font(.system(size: 9, weight: .bold)).foregroundStyle(.white) } }
                        .overlay { Circle().stroke(.primary.opacity(selection == a ? 0.6 : 0), lineWidth: 2).padding(-3) }
                }
                .buttonStyle(.plain)
                .help(a.rawValue)
                .accessibilityLabel(a.rawValue)
            }
        }
    }
}

// MARK: - Account

struct AccountView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.webAuthenticationSession) private var webAuth
    @Environment(\.openURL) private var openURL
    @State private var offlineName = ""
    @State private var busy = false
    @State private var error: String?
    @State private var deviceCode: MicrosoftAuth.DeviceCode?

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Accounts").font(.headline)
            ForEach(model.store.accounts, id: \.uuid) { a in
                HStack {
                    Avatar(account: a)
                    VStack(alignment: .leading, spacing: 0) {
                        Text(a.name)
                        Text(a.kind == .microsoft ? "Microsoft" : "Offline").font(.caption).foregroundStyle(.secondary)
                    }
                    Spacer()
                    if a.uuid == model.account?.uuid { Image(systemName: "checkmark").foregroundStyle(.tint) }
                    Button { model.signOut(a) } label: { Image(systemName: "rectangle.portrait.and.arrow.right") }
                        .buttonStyle(.borderless).help("Sign Out")
                }
                .contentShape(.rect)
                .onTapGesture { model.select(a) }
            }

            Divider()
            ClientIDStatus().font(.callout)
            HStack {
                Button { Task { await signIn() } } label: { Label("Add Microsoft Account", systemImage: "person.crop.circle.badge.plus") }
                    .buttonStyle(.borderedProminent)
                Button("Use Code") { Task { await signInWithCode() } }
            }
            .disabled(busy || model.clientIDState == .missing)

            if let dc = deviceCode {
                VStack(alignment: .leading, spacing: 6) {
                    Text("Enter this code at \(dc.verification_uri):").font(.callout)
                    HStack {
                        Text(dc.user_code).font(.title2.monospaced().weight(.semibold)).textSelection(.enabled)
                        Button { NSPasteboard.general.clearContents(); NSPasteboard.general.setString(dc.user_code, forType: .string) } label: {
                            Image(systemName: "doc.on.doc")
                        }.buttonStyle(.borderless)
                        Button("Open Page") { if let u = URL(string: dc.verification_uri) { openURL(u) } }
                    }
                    ProgressView().controlSize(.small)
                }
            }
            if busy && deviceCode == nil { ProgressView().controlSize(.small) }

            Divider()
            Text("Offline mode").font(.subheadline.weight(.semibold))
            HStack {
                TextField("Username", text: $offlineName).onSubmit(addOffline)
                Button("Add", action: addOffline).disabled(!Self.validName(offlineName))
            }
            Text("Singleplayer and offline-mode servers only.").font(.caption).foregroundStyle(.secondary)

            if let error {
                Label(error, systemImage: "exclamationmark.triangle.fill").foregroundStyle(Theme.warning).font(.callout)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
        .padding(Theme.padding)
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
