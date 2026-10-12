import SwiftUI

/// Full-window background behind the sidebar and every page: a solid red with film grain. Glass floats over it.
struct Backdrop: View {
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        let dark = scheme == .dark
        ZStack {
            // Deep red in dark mode; a lighter red in light mode so dark text stays readable.
            dark ? Color(red: 0.38, green: 0.04, blue: 0.08) : Color(red: 0.95, green: 0.55, blue: 0.52)
            Self.grain.resizable(resizingMode: .tile)
                .opacity(dark ? 0.07 : 0.05)
                .blendMode(dark ? .overlay : .multiply)
        }
        .ignoresSafeArea()
        .allowsHitTesting(false)
    }

    /// 128 × 128 monochrome noise, tiled.
    static let grain: Image = {
        let n = 128
        var px = [UInt8](repeating: 255, count: n * n * 4)
        var rng = SplitMix(seed: 0x48_6f_6e_65)
        for i in 0..<(n * n) {
            let v = UInt8(truncatingIfNeeded: rng.next() >> 56)
            px[i * 4] = v; px[i * 4 + 1] = v; px[i * 4 + 2] = v
        }
        let provider = CGDataProvider(data: Data(px) as CFData)!
        let cg = CGImage(width: n, height: n, bitsPerComponent: 8, bitsPerPixel: 32, bytesPerRow: n * 4,
                         space: CGColorSpaceCreateDeviceRGB(), bitmapInfo: CGBitmapInfo(rawValue: CGImageAlphaInfo.noneSkipLast.rawValue),
                         provider: provider, decode: nil, shouldInterpolate: false, intent: .defaultIntent)!
        return Image(decorative: cg, scale: 2)
    }()
}

/// Deterministic noise so the grain is identical across launches and snapshots.
struct SplitMix: RandomNumberGenerator {
    var state: UInt64
    init(seed: UInt64) { state = seed }
    mutating func next() -> UInt64 {
        state &+= 0x9E37_79B9_7F4A_7C15
        var z = state
        z = (z ^ (z >> 30)) &* 0xBF58_476D_1CE4_E5B9
        z = (z ^ (z >> 27)) &* 0x94D0_49BB_1331_11EB
        return z ^ (z >> 31)
    }
}

/// Hero focal art: the mark, large.
struct HeroArt: View {
    var body: some View {
        ZStack {
            HoneycrispLogo(size: 250)
                .rotationEffect(.degrees(8))
        }
        .frame(width: 520, height: 470)
    }
}
