// Renders the app icon (AppIconView in Sources/Honeycrisp/Logo.swift) to a 1024×1024 PNG.
// Build: swiftc -parse-as-library scripts/make-icon.swift Sources/Honeycrisp/Logo.swift -o build/make-icon
import AppKit
import SwiftUI

@main
struct MakeIcon {
    @MainActor static func main() throws {
        let renderer = ImageRenderer(content: AppIconView())
        renderer.scale = 1
        guard let cg = renderer.cgImage else { throw CocoaError(.fileWriteUnknown) }
        let png = NSBitmapImageRep(cgImage: cg).representation(using: .png, properties: [:])
        try png?.write(to: URL(fileURLWithPath: CommandLine.arguments[1]))
    }
}
