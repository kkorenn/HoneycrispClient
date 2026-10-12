import SwiftUI

// The Honeycrisp mark: a pixel-art apple, drawn from a pixel grid.
// Self-contained (SwiftUI only) so scripts/make-icon.swift compiles against this file to draw the app icon.

/// Pointy-top hexagon grid filling the rect; `cell` is the hexagon radius as a fraction of the width.
struct HoneycombShape: Shape {
    var cell: CGFloat = 0.1
    var inset: CGFloat = 0.12   // gap between cells, fraction of the radius

    func path(in r: CGRect) -> Path {
        let rad = cell * r.width
        let w = sqrt(3) * rad, step = 1.5 * rad
        let hex = rad * (1 - inset)
        var path = Path()
        var row = 0
        var y = r.minY
        while y < r.maxY + rad {
            var x = r.minX + (row.isMultiple(of: 2) ? 0 : w / 2)
            while x < r.maxX + w {
                path.addLines((0..<6).map { i in
                    let a = CGFloat(i) * .pi / 3 + .pi / 6
                    return CGPoint(x: x + hex * cos(a), y: y + hex * sin(a))
                })
                path.closeSubpath()
                x += w
            }
            y += step
            row += 1
        }
        return path
    }
}

struct HoneycrispLogo: View {
    var size: CGFloat = 64

    /// 14×14 pixel apple; "." is transparent.
    static let pixels = [
        "........B.....",
        ".......bc.....",
        ".......B......",
        "....mmBcBB....",
        "..mrPrcmRrBB..",
        ".mrFpPPPpFrBB.",
        ".mrFFFFFFRFrB.",
        ".mrRFRFRRRFRD.",
        ".BrrRRrrRrFRD.",
        ".BrrrrrRrrFrD.",
        "..BrrRrrrRrD..",
        "..BmrrrRRrmD..",
        "...DmrmmrmD...",
        "....DBBBDD....",
    ]
    static let palette: [Character: Color] = [
        "B": rgb(0x893600), "b": rgb(0x934711), "c": rgb(0x683107), "D": rgb(0x680F0E), "m": rgb(0xAD201C),
        "r": rgb(0xC32727), "R": rgb(0xE62F30), "F": rgb(0xFF3738), "p": rgb(0xFF777C), "P": rgb(0xFFAAAF),
    ]

    static func rgb(_ v: Int) -> Color {
        Color(red: Double(v >> 16 & 0xFF) / 255, green: Double(v >> 8 & 0xFF) / 255, blue: Double(v & 0xFF) / 255)
    }

    @Environment(\.displayScale) private var scale

    var body: some View {
        let scale = scale
        return Canvas { ctx, canvas in
            let px = canvas.width / CGFloat(Self.pixels.count)
            // Pixel edges snapped to the screen's pixel grid: small sizes stay crisp instead of blurring between pixels.
            func edge(_ i: Int) -> CGFloat { (CGFloat(i) * px * scale).rounded() / scale }
            for (y, row) in Self.pixels.enumerated() {
                for (x, key) in row.enumerated() {
                    guard let color = Self.palette[key] else { continue }
                    ctx.fill(Path(CGRect(x: edge(x), y: edge(y), width: edge(x + 1) - edge(x), height: edge(y + 1) - edge(y))), with: .color(color))
                }
            }
        }
        .frame(width: size, height: size)
    }
}

/// The app icon: the pixel apple alone on a transparent background, inside the macOS icon grid's 824 of 1024.
struct AppIconView: View {
    var body: some View {
        HoneycrispLogo(size: 824)
            .frame(width: 1024, height: 1024)
    }
}
