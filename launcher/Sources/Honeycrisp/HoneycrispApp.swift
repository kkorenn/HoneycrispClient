import HoneycrispCore
import SwiftUI

@main
struct HoneycrispApp: App {
    @State private var model = AppModel()

    var body: some Scene {
        Window("Honeycrisp", id: "main") {
            RootView()
                .background(WindowChrome())
                .environment(model)
                .frame(minWidth: Theme.windowMin.width, minHeight: Theme.windowMin.height)
                .tint(model.settings.accent.color)
                .preferredColorScheme(model.settings.appearance.scheme)
        }
        .defaultSize(Theme.windowDefault)
        .windowStyle(.hiddenTitleBar)
        .commands { LauncherCommands(model: model) }
    }
}

extension LauncherSettings.Appearance {
    var scheme: ColorScheme? {
        switch self { case .system: nil; case .dark: .dark; case .light: .light }
    }
}

struct LauncherCommands: Commands {
    let model: AppModel

    var body: some Commands {
        CommandGroup(replacing: .newItem) {}
        CommandGroup(replacing: .appSettings) {
            Button("Settings…") { model.selection = .settings }.keyboardShortcut(",")
        }
        CommandGroup(before: .sidebar) {
            ForEach(Array(SidebarItem.allCases.enumerated()), id: \.element) { i, item in
                Button(item.rawValue) { model.selection = item }
                    .keyboardShortcut(KeyEquivalent(Character("\(i + 1)")), modifiers: .command)
            }
            Divider()
            Button(model.sidebarCollapsed ? "Expand Sidebar" : "Collapse Sidebar") { model.sidebarCollapsed.toggle() }
                .keyboardShortcut("s", modifiers: [.command, .control])
            Divider()
        }
        CommandMenu("Game") {
            Button(model.status(model.spec) == .ready ? "Play" : "Install and Play") { model.play() }
                .keyboardShortcut(.return, modifiers: .command)
                .disabled(model.isBusy)
            Button("Stop Game") { model.stop() }
                .keyboardShortcut(".", modifiers: .command)
                .disabled(model.runningVersion == nil)
            Divider()
            Menu("Version") {
                Picker("Version", selection: Bindable(model).settings.lastVersion) {
                    ForEach(Catalog.instances) { Text($0.title).tag($0.mc) }
                }
                .pickerStyle(.inline)
            }
            Divider()
            Button("Open Game Folder") { model.openGameFolder() }
                .keyboardShortcut("o", modifiers: [.command, .shift])
            Button("Accounts…") { model.showAccount = true }
                .keyboardShortcut("a", modifiers: [.command, .shift])
        }
    }
}

extension InstanceSpec {
    var title: String { "\(mc) · \(loader.rawValue)" }
}
