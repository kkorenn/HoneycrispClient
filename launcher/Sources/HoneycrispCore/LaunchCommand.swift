import Foundation

/// Everything needed to turn a merged version JSON into a java command line. Pure: no I/O.
public struct LaunchContext: Sendable {
    public var javaPath: String
    public var gameDir: String
    public var assetsRoot: String
    public var librariesDir: String
    public var nativesDir: String
    public var clientJar: String
    public var classpath: [String]          // library jars (not the client jar)
    public var playerName: String
    public var uuid: String                 // undashed
    public var accessToken: String
    public var userType: String             // "msa" or "legacy"
    public var xuid: String
    public var clientID: String
    public var memoryMB: Int
    public var extraJVMArgs: [String]
    public var loggingConfigPath: String?
    public var launcherVersion: String
    public var windowSize: (width: Int, height: Int)?

    public init(javaPath: String, gameDir: String, assetsRoot: String, librariesDir: String, nativesDir: String, clientJar: String,
                classpath: [String], playerName: String, uuid: String, accessToken: String, userType: String, xuid: String = "0",
                clientID: String = "", memoryMB: Int = 4096, extraJVMArgs: [String] = [], loggingConfigPath: String? = nil,
                launcherVersion: String = "1.0") {
        self.javaPath = javaPath; self.gameDir = gameDir; self.assetsRoot = assetsRoot; self.librariesDir = librariesDir
        self.nativesDir = nativesDir; self.clientJar = clientJar; self.classpath = classpath; self.playerName = playerName
        self.uuid = uuid; self.accessToken = accessToken; self.userType = userType; self.xuid = xuid; self.clientID = clientID
        self.memoryMB = memoryMB; self.extraJVMArgs = extraJVMArgs; self.loggingConfigPath = loggingConfigPath
        self.launcherVersion = launcherVersion
    }
}

public enum LaunchCommand {
    public enum Failure: Error { case noMainClass }

    /// Returns argv (argv[0] = java).
    public static func build(_ v: VersionJSON, _ c: LaunchContext, env baseEnv: RuleEnvironment = RuleEnvironment()) throws -> [String] {
        guard let mainClass = v.mainClass else { throw Failure.noMainClass }
        var env = baseEnv
        if c.windowSize != nil { env.features["has_custom_resolution"] = true }
        let classpath = (c.classpath + [c.clientJar]).joined(separator: ":")
        let vars: [String: String] = [
            "auth_player_name": c.playerName,
            "version_name": v.id,
            "game_directory": c.gameDir,
            "assets_root": c.assetsRoot,
            "game_assets": c.assetsRoot,
            "assets_index_name": v.assetIndex?.id ?? v.assets ?? "legacy",
            "auth_uuid": c.uuid,
            "auth_access_token": c.accessToken,
            "auth_session": "token:\(c.accessToken):\(c.uuid)",
            "auth_xuid": c.xuid,
            "clientid": c.clientID,
            "user_type": c.userType,
            "user_properties": "{}",
            "version_type": v.type ?? "release",
            "natives_directory": c.nativesDir,
            "library_directory": c.librariesDir,
            "classpath_separator": ":",
            "classpath": classpath,
            "launcher_name": "Honeycrisp",
            "launcher_version": c.launcherVersion,
            "resolution_width": c.windowSize.map { String($0.width) } ?? "",
            "resolution_height": c.windowSize.map { String($0.height) } ?? "",
        ]
        func sub(_ s: String) -> String {
            guard s.contains("${") else { return s }
            var r = s
            for (k, val) in vars { r = r.replacingOccurrences(of: "${\(k)}", with: val) }
            return r
        }

        var jvm: [String]
        if let a = v.arguments?.jvm {
            jvm = a.flatMap { $0.resolve(env) }.map(sub)
        } else {
            // Pre-1.13 JSONs have no jvm arguments: the official launcher's built-in defaults.
            // No -XstartOnFirstThread here: Graft's legacy window dispatches to the AppKit main queue itself.
            jvm = ["-Djava.library.path=\(c.nativesDir)", "-cp", classpath]
        }
        var head = ["-Xms512M", "-Xmx\(c.memoryMB)M", "-Dlog4j2.formatMsgNoLookups=true",
                    "-Xdock:name=Honeycrisp Client", "-Dapple.awt.application.name=Honeycrisp Client", "-Dhoneycrisp.appIcon=true"]
        if let log = v.logging?["client"], let p = c.loggingConfigPath {
            head.append(log.argument.replacingOccurrences(of: "${path}", with: p))
        }
        jvm = head + c.extraJVMArgs + jvm

        var game: [String]
        if let a = v.arguments?.game {
            game = a.flatMap { $0.resolve(env) }.map(sub)
        } else {
            game = (v.minecraftArguments ?? "").split(separator: " ").map { sub(String($0)) }
            if let w = c.windowSize { game += ["--width", String(w.width), "--height", String(w.height)] }
        }
        return [c.javaPath] + jvm + [mainClass] + game
    }

    /// Offline-mode UUID exactly as the vanilla server computes it: UUID.nameUUIDFromBytes("OfflinePlayer:" + name).
    public static func offlineUUID(_ name: String) -> UUID {
        var b = Array(Hash.md5(Data("OfflinePlayer:\(name)".utf8)))
        b[6] = b[6] & 0x0f | 0x30
        b[8] = b[8] & 0x3f | 0x80
        return UUID(uuid: (b[0], b[1], b[2], b[3], b[4], b[5], b[6], b[7], b[8], b[9], b[10], b[11], b[12], b[13], b[14], b[15]))
    }
}
