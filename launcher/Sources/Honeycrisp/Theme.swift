import SwiftUI

/// Every tunable look value of the launcher (DESIGN.md §7, tokens from §2). Views read only from here.
enum Theme {
    enum Accent: String, Codable, CaseIterable, Identifiable {
        case system = "System", blue = "Blue", purple = "Purple", pink = "Pink", red = "Red",
             orange = "Orange", yellow = "Yellow", green = "Green", graphite = "Graphite"
        var id: Self { self }

        /// nil = follow the macOS accent color.
        var color: Color? {
            switch self {
            case .system: nil
            case .blue: .blue
            case .purple: .purple
            case .pink: .pink
            case .red: .red
            case .orange: .orange
            case .yellow: .yellow
            case .green: .green
            case .graphite: Color(white: 0x8C / 255)
            }
        }
    }

    // Window
    static let windowDefault = CGSize(width: 1040, height: 680)
    static let windowMin = CGSize(width: 860, height: 560)
    static let sidebarWidth: ClosedRange<CGFloat> = 200...240

    // Home
    static let heroHeight: CGFloat = 380
    static let wordmarkSize: CGFloat = 34
    static let playMinWidth: CGFloat = 180
    static let clusterRadius: CGFloat = 24
    static let newsCard = CGSize(width: 280, height: 160)
    static let newsCardRadius: CGFloat = 16
    static let avatarSize: CGFloat = 24
    static let padding: CGFloat = 16

    /// Placeholder hero until real panorama art exists: accent-tinted gradient.
    static func heroBackground(_ accent: Color) -> some View {
        LinearGradient(colors: [accent.opacity(0.55), accent.opacity(0.15), Color(white: 0.06)],
                       startPoint: .topLeading, endPoint: .bottomTrailing)
    }

    static let warning = Color.orange

    /// Player head for the account row. Third-party render service; falls back to the SF Symbol.
    static func avatarURL(_ uuid: String) -> URL? { URL(string: "https://mc-heads.net/avatar/\(uuid)/48") }
}

extension View {
    /// Liquid Glass on macOS 26+, a material capsule before.
    @ViewBuilder func glassCluster() -> some View {
        if #available(macOS 26, *) {
            GlassEffectContainer { self.padding(8).glassEffect(.regular, in: .rect(cornerRadius: Theme.clusterRadius)) }
        } else {
            self.padding(8).background(.regularMaterial, in: .rect(cornerRadius: Theme.clusterRadius))
        }
    }

    @ViewBuilder func newsCardBackground() -> some View {
        if #available(macOS 26, *) {
            self.background(.background.secondary, in: .rect(cornerRadius: Theme.newsCardRadius))
        } else {
            self.background(.thinMaterial, in: .rect(cornerRadius: Theme.newsCardRadius))
        }
    }

    @ViewBuilder func playControlSize() -> some View {
        if #available(macOS 26, *) { self.controlSize(.extraLarge) } else { self.controlSize(.large) }
    }

    @ViewBuilder func heroExtension() -> some View {
        if #available(macOS 26, *) { self.backgroundExtensionEffect() } else { self }
    }
}
