import HoneycrispCore
import SwiftUI

/// Every look value of the launcher: palette, radii, spacing, type, motion, and the glass helpers.
/// Views read only from here.
enum Theme {
    enum Accent: String, Codable, CaseIterable, Identifiable {
        case honeycrisp = "Honeycrisp", system = "System", blue = "Blue", purple = "Purple", pink = "Pink", red = "Red",
             orange = "Orange", yellow = "Yellow", green = "Green", graphite = "Graphite"
        var id: Self { self }

        /// nil = follow the macOS accent color.
        var color: Color? {
            switch self {
            case .honeycrisp: Palette.amber
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

    /// Honeycrisp reds: deep wine → crimson → red. `ember`/`amber`/`gold` keep their names but are reds now.
    enum Palette {
        static let wine = Color(red: 0.24, green: 0.02, blue: 0.06)
        static let crimson = Color(red: 0.70, green: 0.06, blue: 0.14)
        static let red = Color(red: 0.90, green: 0.20, blue: 0.18)
        static let ember = Color(red: 0.88, green: 0.14, blue: 0.20)
        static let amber = Color(red: 0.94, green: 0.24, blue: 0.28)
        static let gold = Color(red: 1.00, green: 0.48, blue: 0.52)
        static let cream = Color(red: 1.00, green: 0.95, blue: 0.86)
        static let night = Color(red: 0.075, green: 0.035, blue: 0.045)
        static let paper = Color(red: 0.985, green: 0.955, blue: 0.93)
        static let running = Color(red: 0.24, green: 0.80, blue: 0.48)

        static let play = ember
        static let brand = amber
    }

    static func loaderColor(_ l: Loader) -> Color {
        l == .fabric ? adaptive(light: Color(red: 0.62, green: 0.38, blue: 0.10), dark: Color(red: 0.93, green: 0.72, blue: 0.45))
                     : adaptive(light: Color(red: 0.24, green: 0.36, blue: 0.86), dark: Color(red: 0.50, green: 0.62, blue: 1.0))
    }

    /// Graft Shaders badge color, dark enough to read on light cards.
    static let shaders = adaptive(light: Color(red: 0.72, green: 0.08, blue: 0.16), dark: Palette.gold)

    static func adaptive(light: Color, dark: Color) -> Color {
        Color(nsColor: NSColor(name: nil) { $0.bestMatch(from: [.darkAqua, .aqua]) == .darkAqua ? NSColor(dark) : NSColor(light) })
    }

    // Window
    static let windowDefault = CGSize(width: 1100, height: 720)
    static let windowMin = CGSize(width: 860, height: 580)
    static let sidebarWidth: CGFloat = 216
    static let sidebarCollapsedWidth: CGFloat = 58  // 34 pt icon square + 12 pt each side
    /// Below this window width the sidebar collapses to icons.
    static let sidebarAutoCollapse: CGFloat = 960
    /// Room for the traffic lights above the sidebar items.
    static let titlebarInset: CGFloat = 50

    // Spacing (4-pt grid)
    static let s1: CGFloat = 4, s2: CGFloat = 8, s3: CGFloat = 12, s4: CGFloat = 16, s5: CGFloat = 24, s6: CGFloat = 32, s7: CGFloat = 40
    static let pageInset: CGFloat = 36
    static let windowInset: CGFloat = 8

    // Radii (concentric: inner = outer − padding)
    static let windowRadius: CGFloat = 26
    static let sidebarRadius: CGFloat = windowRadius - windowInset
    static let cardRadius: CGFloat = 22
    static let rowRadius: CGFloat = 12
    static let chipRadius: CGFloat = 10

    // Home
    static let heroHeight: CGFloat = 446
    static let playHeight: CGFloat = 56
    static let playMinWidth: CGFloat = 188
    static let avatarSize: CGFloat = 28

    // Type
    enum Fonts {
        static let wordmark = Font.system(size: 60, weight: .heavy)
        static let pageTitle = Font.system(size: 32, weight: .bold)
        static let eyebrow = Font.system(size: 11, weight: .semibold)
        static func numeral(_ size: CGFloat, _ weight: Font.Weight = .bold) -> Font { .system(size: size, weight: weight, design: .rounded) }
        static let sectionTitle = Font.system(size: 19, weight: .bold)
        static let play = Font.system(size: 19, weight: .bold, design: .rounded)
    }

    // Motion
    enum Motion {
        static let spring = Animation.spring(response: 0.38, dampingFraction: 0.82)
        static let snappy = Animation.spring(response: 0.25, dampingFraction: 0.9)
        static let hover = Animation.easeOut(duration: 0.16)
    }

    static let warning = Color.orange

    /// Off: draw the macOS 14–15 material fallback even on 26 (offscreen snapshots, where glass can't sample its backdrop).
    @MainActor static var liquidGlass = true

    /// Player head for the account chip. Third-party render service; falls back to a drawn monogram.
    static func avatarURL(_ uuid: String) -> URL? { URL(string: "https://mc-heads.net/avatar/\(uuid)/64") }
}

// MARK: - Glass

extension View {
    /// Liquid Glass on macOS 26+, a vibrant material with a hairline edge before.
    @ViewBuilder func glass<S: Shape>(_ shape: S, tint: Color? = nil, interactive: Bool = false) -> some View {
        if #available(macOS 26, *), Theme.liquidGlass {
            self.glassEffect(Glass.regular.tint(tint).interactive(interactive), in: shape)
        } else {
            self.background { FrostedSurface(shape: shape, tint: tint) }
        }
    }

    func glassCapsule(tint: Color? = nil, interactive: Bool = false) -> some View { glass(Capsule(), tint: tint, interactive: interactive) }
    func glassCard(_ radius: CGFloat = Theme.cardRadius, tint: Color? = nil, interactive: Bool = false) -> some View {
        glass(RoundedRectangle(cornerRadius: radius, style: .continuous), tint: tint, interactive: interactive)
    }

    /// Groups nearby glass shapes so they blend and morph (macOS 26); a no-op before.
    @ViewBuilder func glassGroup(spacing: CGFloat = 16) -> some View {
        if #available(macOS 26, *), Theme.liquidGlass { GlassEffectContainer(spacing: spacing) { self } } else { self }
    }

    /// System glass button on macOS 26, a material capsule before.
    @ViewBuilder func glassButtonStyle() -> some View {
        // Not the system `.glass` style: on macOS that draws an old push button filled with the accent color.
        if #available(macOS 26, *), Theme.liquidGlass { self.buttonStyle(GlassPillStyle()) } else { self.buttonStyle(MaterialButtonStyle()) }
    }

    /// Hairline highlight on a card's top edge, lifting it off the backdrop even where glass renders flat.
    func cardEdge(_ radius: CGFloat = Theme.cardRadius) -> some View {
        overlay {
            RoundedRectangle(cornerRadius: radius, style: .continuous)
                .strokeBorder(.white.opacity(0.14), lineWidth: 0.75)
                .allowsHitTesting(false)
        }
    }

    func hoverable(_ radius: CGFloat = Theme.rowRadius, lift: Bool = false) -> some View { modifier(Hoverable(radius: radius, lift: lift)) }

    func eyebrow() -> some View { font(Theme.Fonts.eyebrow).textCase(.uppercase).kerning(1.2) }
}

/// Pre-26 glass: material plus a light wash and hairline, so it still reads as a surface if the material renders flat.
struct FrostedSurface<S: Shape>: View {
    var shape: S
    var tint: Color?
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        shape.fill(.ultraThinMaterial)
            .overlay { shape.fill(scheme == .dark ? Color.white.opacity(0.06) : Color.white.opacity(0.45)) }
            .overlay { if let tint { shape.fill(tint.opacity(0.22)) } }
            .overlay { shape.stroke(scheme == .dark ? Color.white.opacity(0.12) : Color.black.opacity(0.06), lineWidth: 0.5) }
    }
}

/// Capsule button on a real Liquid Glass surface: interactive glass for the press response, the glass brightens on
/// hover, and the pill squishes a little while held. Sized from the control size.
@available(macOS 26, *)
struct GlassPillStyle: ButtonStyle {
    var tint: Color? = nil

    func makeBody(configuration: Configuration) -> some View { PillBody(configuration: configuration, tint: tint) }

    private struct PillBody: View {
        let configuration: Configuration
        let tint: Color?
        @State private var hover = false
        @Environment(\.isEnabled) private var enabled
        @Environment(\.controlSize) private var size

        var body: some View {
            let (h, pad, font): (CGFloat, CGFloat, CGFloat) = switch size {
            case .mini, .small: (24, 10, 12)
            case .large, .extraLarge: (34, 16, 13.5)
            default: (28, 12, 13)
            }
            let lit = hover && enabled
            let glassTint: Color? = tint.map { $0.opacity(lit ? 1 : 0.85) } ?? (lit ? .white.opacity(0.10) : nil)
            configuration.label
                .font(.system(size: font, weight: tint == nil ? .medium : .semibold))
                .foregroundStyle(tint == nil ? AnyShapeStyle(.primary) : AnyShapeStyle(.white))
                .lineLimit(1)
                .padding(.horizontal, pad)
                .frame(minHeight: h)
                .contentShape(Capsule())
                .glassEffect(.regular.tint(glassTint).interactive(), in: Capsule())
                .opacity(enabled ? 1 : 0.45)
                .scaleEffect(configuration.isPressed ? 0.97 : 1)
                .animation(Theme.Motion.snappy, value: configuration.isPressed)
                .animation(Theme.Motion.hover, value: lit)
                .onHover { hover = $0 }
        }
    }
}

struct MaterialButtonStyle: ButtonStyle {
    @State private var hover = false
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .padding(.horizontal, 12).padding(.vertical, 6)
            .background(.regularMaterial, in: .capsule)
            .overlay { Capsule().strokeBorder(.white.opacity(hover ? 0.25 : 0.12), lineWidth: 0.5) }
            .scaleEffect(configuration.isPressed ? 0.96 : 1)
            .animation(Theme.Motion.snappy, value: configuration.isPressed)
            .onHover { h in withAnimation(Theme.Motion.hover) { hover = h } }
    }
}

/// The single primary action: solid pill with an inner highlight, hover lift and press squish.
struct PlayButtonStyle: ButtonStyle {
    var running = false
    @Environment(\.isEnabled) private var enabled
    @State private var hover = false

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(Theme.Fonts.play)
            .foregroundStyle(.white)
            .padding(.horizontal, 28)
            .frame(minWidth: Theme.playMinWidth, minHeight: Theme.playHeight)
            .background {
                Capsule().fill(running ? Theme.Palette.red : Theme.Palette.play)
                    .overlay { Capsule().fill(.white.opacity(hover && enabled ? 0.10 : 0)) }
                    .overlay {
                        Capsule().strokeBorder(.white.opacity(0.25), lineWidth: 1)
                    }
            }
            .saturation(enabled ? 1 : 0.4)
            .opacity(enabled ? 1 : 0.75)
            .scaleEffect(configuration.isPressed ? 0.96 : (hover && enabled ? 1.02 : 1))
            .animation(Theme.Motion.snappy, value: configuration.isPressed)
            .onHover { h in withAnimation(Theme.Motion.hover) { hover = h } }
    }
}

/// Small rounded label: loader, source, state.
struct Badge: View {
    var text: String
    var symbol: String?
    var color: Color = .secondary
    var filled = false
    var uppercase = true

    var body: some View {
        HStack(spacing: 3) {
            if let symbol { Image(systemName: symbol).font(.system(size: 9, weight: .bold)) }
            Text(text)
        }
        .font(.system(size: 10, weight: .bold))
        .textCase(uppercase ? .uppercase : nil)
        .kerning(uppercase ? 0.5 : 0.1)
        .lineLimit(1)
        .fixedSize()
        .padding(.horizontal, 7).padding(.vertical, 3.5)
        .foregroundStyle(filled ? AnyShapeStyle(.white) : AnyShapeStyle(color))
        .background(filled ? AnyShapeStyle(color) : AnyShapeStyle(color.opacity(0.16)), in: .rect(cornerRadius: 6, style: .continuous))
    }
}

/// Hover highlight for rows and cards.
struct Hoverable: ViewModifier {
    var radius: CGFloat = Theme.rowRadius
    var lift = false
    @State private var hover = false

    func body(content: Content) -> some View {
        content
            .background(RoundedRectangle(cornerRadius: radius, style: .continuous).fill(.primary.opacity(hover ? 0.06 : 0)))
            .scaleEffect(lift && hover ? 1.015 : 1)
            .animation(Theme.Motion.hover, value: hover)
            .onHover { hover = $0 }
    }
}

// MARK: - Glass button

/// Capsule (or circle) button on a Liquid Glass surface. Interactive glass gives the system press response; on hover
/// the glass itself brightens a little, like system glass controls. (A native `NSButton` glass bezel drew no
/// background when hosted in SwiftUI.) Before macOS 26: the material style.
struct GlassButton: View {
    var title: String? = nil
    var symbol: String
    var help: String? = nil
    var large = false
    var circle = false
    var action: () -> Void
    @State private var hover = false
    @Environment(\.isEnabled) private var enabled

    var body: some View {
        let height: CGFloat = large ? 34 : 28
        Button(action: action) {
            Group {
                if let title { Label(title, systemImage: symbol) } else { Image(systemName: symbol) }
            }
            .font(.system(size: large ? 13.5 : 12.5, weight: .medium))
            .foregroundStyle(enabled ? .primary : .tertiary)
            .padding(.horizontal, circle ? 0 : (large ? 16 : 12))
            .frame(width: circle ? height : nil, height: height)
            .contentShape(Capsule())
        }
        .buttonStyle(.plain)
        .modifier(GlassSurface(hover: hover && enabled))
        .onHover { h in withAnimation(Theme.Motion.hover) { hover = h } }
        .help(help ?? title ?? "")
    }
}

private struct GlassSurface: ViewModifier {
    var hover: Bool
    func body(content: Content) -> some View {
        if #available(macOS 26, *), Theme.liquidGlass {
            content.glassEffect(.regular.tint(.white.opacity(hover ? 0.10 : 0)).interactive(), in: Capsule())
        } else {
            content.background { FrostedSurface(shape: Capsule(), tint: hover ? .white.opacity(0.08) : nil) }
        }
    }
}

// MARK: - Glass controls

extension View {
    /// Primary action: glass tinted solid red (macOS 26); bordered-prominent before.
    @ViewBuilder func glassProminentStyle(_ tint: Color = Theme.Palette.amber) -> some View {
        if #available(macOS 26, *), Theme.liquidGlass { self.buttonStyle(GlassPillStyle(tint: tint)) } else { self.buttonStyle(.borderedProminent).tint(tint) }
    }

    /// Text field on a glass capsule instead of the old sunken rounded border.
    func glassField(width: CGFloat? = nil) -> some View {
        textFieldStyle(.plain)
            .padding(.horizontal, 10)
            .frame(width: width, height: 28)
            .glassCapsule(interactive: true)
    }
}

/// Segmented control as a glass capsule with a pill that slides to the selected segment (the sidebar's motion).
struct GlassSegmented<T: Hashable>: View {
    @Binding var selection: T
    var options: [(T, String)]
    @Namespace private var ns
    @Environment(\.isEnabled) private var enabled

    var body: some View {
        HStack(spacing: 2) {
            ForEach(options, id: \.0) { value, title in
                let on = value == selection
                Button { withAnimation(Theme.Motion.spring) { selection = value } } label: {
                    Text(title)
                        .font(.system(size: 12.5, weight: on ? .semibold : .medium))
                        .foregroundStyle(on ? .primary : .secondary)
                        .padding(.horizontal, 12)
                        .frame(height: 24)
                        .background {
                            if on { Capsule().fill(.white.opacity(0.16)).matchedGeometryEffect(id: "pill", in: ns) }
                        }
                        .contentShape(Capsule())
                }
                .buttonStyle(.plain)
            }
        }
        .padding(2)
        .glassCapsule()
        .opacity(enabled ? 1 : 0.5)
        .fixedSize()
    }
}
