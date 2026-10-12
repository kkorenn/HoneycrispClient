import AppKit
import SwiftUI

/// Transparent, untitled title bar. The traffic lights are moved to line up with the sidebar: the close button starts
/// near the sidebar's left edge, the row centered in the 40 pt band above the sidebar (see RootView).
/// AppKit puts them back on title bar layout, so they are re-placed on resize, full screen and key changes.
struct WindowChrome: NSViewRepresentable {
    static let closeMinX: CGFloat = Theme.windowInset + 4
    static let centerY: CGFloat = 20

    func makeNSView(context: Context) -> NSView { ChromeView() }
    func updateNSView(_ nsView: NSView, context: Context) {}

    final class ChromeView: NSView {
        private var observers: [NSObjectProtocol] = []

        override func viewDidMoveToWindow() {
            super.viewDidMoveToWindow()
            observers.forEach(NotificationCenter.default.removeObserver)
            observers = []
            guard let window else { return }
            if window.toolbar == nil {
                window.toolbar = NSToolbar(identifier: "honeycrisp.main")
                window.toolbarStyle = .unified
                window.titleVisibility = .hidden
                window.titlebarAppearsTransparent = true
            }
            for name in [NSWindow.didResizeNotification, NSWindow.didEndLiveResizeNotification, NSWindow.didExitFullScreenNotification,
                         NSWindow.didBecomeKeyNotification, NSWindow.didResignKeyNotification] {
                observers.append(NotificationCenter.default.addObserver(forName: name, object: window, queue: .main) { [weak self] _ in
                    MainActor.assumeIsolated { self?.placeTrafficLights() }
                })
            }
            DispatchQueue.main.async { [weak self] in self?.placeTrafficLights() }
        }

        private func placeTrafficLights() {
            guard let window, !window.styleMask.contains(.fullScreen) else { return }
            let buttons = [NSWindow.ButtonType.closeButton, .miniaturizeButton, .zoomButton].compactMap { window.standardWindowButton($0) }
            guard buttons.count == 3, let bar = buttons[0].superview else { return }
            let spacing = buttons[1].frame.minX - buttons[0].frame.minX
            let size = buttons[0].frame.size
            let x0 = WindowChrome.closeMinX
            for (i, button) in buttons.enumerated() {
                // Window coordinates (origin bottom-left), converted into the title bar view's.
                let inWindow = NSPoint(x: x0 + CGFloat(i) * spacing, y: window.frame.height - WindowChrome.centerY - size.height / 2)
                button.setFrameOrigin(bar.convert(inWindow, from: nil))
            }
        }
    }
}
