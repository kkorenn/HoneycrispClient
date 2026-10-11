// Draws the placeholder app icon (original artwork, no SF Symbols) into a 1024×1024 PNG.
import AppKit

let size = 1024.0
let img = NSImage(size: NSSize(width: size, height: size))
img.lockFocus()
let rect = NSRect(x: 100, y: 100, width: 824, height: 824)
let path = NSBezierPath(roundedRect: rect, xRadius: 185, yRadius: 185)
NSGradient(colors: [NSColor(calibratedRed: 0.20, green: 0.22, blue: 0.27, alpha: 1),
                    NSColor(calibratedRed: 0.05, green: 0.06, blue: 0.08, alpha: 1)])!.draw(in: path, angle: -90)
NSColor(white: 1, alpha: 0.18).setStroke()
path.lineWidth = 6
path.stroke()
let m = NSBezierPath()
m.move(to: NSPoint(x: 300, y: 300)); m.line(to: NSPoint(x: 300, y: 724)); m.line(to: NSPoint(x: 512, y: 480))
m.line(to: NSPoint(x: 724, y: 724)); m.line(to: NSPoint(x: 724, y: 300))
m.lineWidth = 90; m.lineJoinStyle = .round; m.lineCapStyle = .round
NSColor(calibratedRed: 0.80, green: 0.84, blue: 0.92, alpha: 1).setStroke()
m.stroke()
img.unlockFocus()
let rep = NSBitmapImageRep(data: img.tiffRepresentation!)!
try! rep.representation(using: .png, properties: [:])!.write(to: URL(fileURLWithPath: CommandLine.arguments[1]))
