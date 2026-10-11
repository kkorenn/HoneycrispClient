import Foundation
import Testing
@testable import HoneycrispCore

func fixture(_ name: String) throws -> Data {
    try Data(contentsOf: #require(Bundle.module.url(forResource: name, withExtension: "json", subdirectory: "Fixtures")))
}
func version(_ name: String) throws -> VersionJSON { try JSONDecoder().decode(VersionJSON.self, from: fixture(name)) }

let mac = RuleEnvironment(osVersion: "14.5.0")

func context(classpath: [String] = ["/l/a.jar"]) -> LaunchContext {
    LaunchContext(javaPath: "/j/java", gameDir: "/g", assetsRoot: "/a", librariesDir: "/l", nativesDir: "/n", clientJar: "/v/c.jar",
                  classpath: classpath, playerName: "Steve", uuid: "u", accessToken: "tok", userType: "msa",
                  memoryMB: 3072, loggingConfigPath: "/a/log.xml")
}

@Test func rules() {
    #expect(mac.allows(nil))
    #expect(mac.allows([Rule(action: "allow", os: OSRule(name: "osx"))]))
    #expect(!mac.allows([Rule(action: "allow", os: OSRule(name: "windows"))]))
    #expect(!mac.allows([Rule(action: "allow"), Rule(action: "disallow", os: OSRule(name: "osx"))]))
    #expect(mac.allows([Rule(action: "allow", os: OSRule(name: "osx", arch: "arm64"))]))
    #expect(!mac.allows([Rule(action: "allow", os: OSRule(arch: "x86"))]))
    #expect(mac.allows([Rule(action: "allow", os: OSRule(name: "osx", version: "^14\\."))]))
    #expect(!mac.allows([Rule(action: "allow", os: OSRule(name: "osx", version: "^10\\.5\\."))]))
    #expect(!mac.allows([Rule(action: "allow", features: ["is_demo_user": true])]))
    #expect(RuleEnvironment(osVersion: "14", features: ["is_demo_user": true]).allows([Rule(action: "allow", features: ["is_demo_user": true])]))
}

@Test func modernLibraries() throws {
    let libs = LibraryResolver.resolve(try version("modern").libraries!, env: mac)
    #expect(libs.map(\.path) == [
        "com/mojang/brigadier/1.3.10/brigadier-1.3.10.jar",
        "org/lwjgl/lwjgl/3.3.3/lwjgl-3.3.3.jar",
        "org/lwjgl/lwjgl/3.3.3/lwjgl-3.3.3-natives-macos-arm64.jar",
    ])
    #expect(libs.allSatisfy { !$0.isNative })
    #expect(libs[0].sha1 == "b1" && libs[0].size == 10)
}

@Test func legacyNatives() throws {
    var intel = mac
    intel.arch = "x86_64"
    let x86 = LibraryResolver.resolve(try version("legacy").libraries!, env: intel)
    #expect(x86.map(\.path) == [
        "com/google/guava/guava/17.0/guava-17.0.jar",
        "org/lwjgl/lwjgl/lwjgl/2.9.2-nightly-20140822/lwjgl-2.9.2-nightly-20140822.jar",
        "org/lwjgl/lwjgl/lwjgl-platform/2.9.2-nightly-20140822/lwjgl-platform-2.9.2-nightly-20140822-natives-osx.jar",
        // twitch-platform has an osx native but no download entry: falls back to Maven path on the repos.
        "tv/twitch/twitch-platform/6.5/twitch-platform-6.5-natives-osx.jar",
    ])
    #expect(x86.map(\.isNative) == [false, false, true, true])
    #expect(x86[3].urls.first?.absoluteString == "https://libraries.minecraft.net/tv/twitch/twitch-platform/6.5/twitch-platform-6.5-natives-osx.jar")

    // Apple Silicon: the osx-only 2.9.2 entries become 2.9.4 with MinecraftMachina's arm64 natives; twitch-platform is dropped.
    let arm = LibraryResolver.resolve(try version("legacy").libraries!, env: mac)
    #expect(arm.map(\.path) == [
        "com/google/guava/guava/17.0/guava-17.0.jar",
        "org/lwjgl/lwjgl/lwjgl/2.9.4-nightly-20150209/lwjgl-2.9.4-nightly-20150209.jar",
        "org/lwjgl/lwjgl/lwjgl-platform/2.9.4-nightly-20150209/lwjgl-platform-2.9.4-nightly-20150209-natives-osx-arm64.jar",
    ])
    #expect(arm[1].sha1 == "697517568c68e78ae0b4544145af031c81082dfe" && arm[1].size == 1047168)
    #expect(arm[2].isNative && arm[2].sha1 == "eff546c0b319d6ffc7a835652124c18089c67f36" && arm[2].size == 488316)
    #expect(arm[2].urls == [URL(string: "https://github.com/MinecraftMachina/lwjgl/releases/download/2.9.4-20150209-mmachina.2/lwjgl-platform-2.9.4-nightly-20150209-natives-osx.jar")!])
}

@Test func arm64Overrides() throws {
    let libs = LibraryResolver.resolve(try version("legacy_arm64").libraries!, env: mac)
    #expect(libs.map(\.path) == [
        "org/lwjgl/lwjgl/lwjgl/2.9.4-nightly-20150209/lwjgl-2.9.4-nightly-20150209.jar",
        "org/lwjgl/lwjgl/lwjgl_util/2.9.4-nightly-20150209/lwjgl_util-2.9.4-nightly-20150209.jar",
        "org/lwjgl/lwjgl/lwjgl-platform/2.9.4-nightly-20150209/lwjgl-platform-2.9.4-nightly-20150209-natives-osx-arm64.jar",
        "net/java/jinput/jinput-platform/2.0.5/jinput-platform-2.0.5-natives-osx-arm64.jar",
        "net/java/jinput/jinput/2.0.5/jinput-2.0.5.jar",                                    // untouched
        "com/mojang/text2speech/1.11.3/text2speech-1.11.3.jar",
        "ca/weblite/java-objc-bridge/1.1.0-mmachina.1/java-objc-bridge-1.1.0-mmachina.1.jar",  // both 1.0.0 entries → one
        "org/lwjgl/lwjgl/3.3.3/lwjgl-3.3.3.jar",                                            // LWJGL 3 untouched
    ])
    #expect(libs.map(\.isNative) == [false, false, true, true, false, false, false, false])
    // Every substituted file is pinned by SHA-1 + size, so the downloader verifies it.
    for l in libs.prefix(4) + libs[5...6] { #expect(l.sha1?.count == 40 && l.size != nil && l.urls.count == 1, "\(l.path)") }
    #expect(libs[3].sha1 == "5189eb40db3087fb11ca063b68fa4f4c20b199dd")
    #expect(libs[6].sha1 == "369a83621e3c65496348491e533cb97fe5f2f37d")
}

@Test func azulFallback() throws {
    let url = Azul.packagesURL(major: 8)
    let q = Dictionary(uniqueKeysWithValues: (URLComponents(url: url, resolvingAgainstBaseURL: false)?.queryItems ?? []).map { ($0.name, $0.value ?? "") })
    #expect(url.host() == "api.azul.com" && url.path() == "/metadata/v1/zulu/packages/")
    #expect(q == ["java_version": "8", "os": "macos", "arch": "aarch64", "java_package_type": "jre", "archive_type": "zip",
                  "javafx_bundled": "false", "crac_supported": "false", "latest": "true", "release_status": "ga"])
    #expect(Azul.detailURL("9e8e").absoluteString == "https://api.azul.com/metadata/v1/zulu/packages/9e8e")

    let pkgs = try JSONDecoder().decode([Azul.Package].self, from: fixture("azul_packages"))
    let pkg = try #require(Azul.newest(pkgs, major: 8))
    #expect(pkg.package_uuid == "9e8e474a-a7e1-46ae-8f6a-ddfc0157aae9" && pkg.java_version == [8, 0, 504])
    #expect(Azul.newest(pkgs, major: 17) == nil)
    let d = try JSONDecoder().decode(Azul.Detail.self, from: fixture("azul_detail"))
    #expect(d.sha256_hash == "1769ea082fa13ba29403363425716451b04b10f5ae6dba07f7f0a69666197f15" && d.size == 40334100)
    #expect(d.download_url.hasSuffix("macosx_aarch64.zip"))

    // Mojang's real mac-os-arm64 index has jre-legacy = []: Java 8 must not be "upgraded" to 17 → fall back to Azul.
    let idx = try JSONDecoder().decode(JavaRuntimeIndex.self, from: Data("""
        {"mac-os-arm64": {"jre-legacy": [], "java-runtime-gamma": [{"manifest": {"sha1": "g", "size": 1, "url": "https://x/g.json"}, "version": {"name": "17.0.15"}}]}}
        """.utf8))
    #expect(idx.pick(platform: "mac-os-arm64", component: "jre-legacy", minMajor: 8, maxMajor: 8) == nil)
    #expect(idx.pick(platform: "mac-os-arm64", component: "jre-legacy", minMajor: 8)?.component == "java-runtime-gamma")
}

@Test func findZuluJava() throws {
    let root = FileManager.default.temporaryDirectory.appending(path: "zulu-test-\(UUID().uuidString)")
    defer { try? FileManager.default.removeItem(at: root) }
    let bin = root.appending(path: "zulu17.x-macosx_aarch64/zulu-17.jre/Contents/Home/bin")
    try FileManager.default.createDirectory(at: bin, withIntermediateDirectories: true)
    #expect(Installer.findJava(in: root) == nil)
    FileManager.default.createFile(atPath: bin.appending(path: "java").path, contents: Data(), attributes: [.posixPermissions: 0o755])
    #expect(Installer.findJava(in: root)?.hasSuffix("/zulu17.x-macosx_aarch64/zulu-17.jre/Contents/Home/bin/java") == true)
}

@Test func forgeMergeAndRepos() throws {
    let merged = try version("forge189").merged(over: version("legacy"))
    #expect(merged.id == "1.8.9-Forge11.15.1.2318-1.8.9")
    #expect(merged.mainClass == "net.minecraft.launchwrapper.Launch")
    #expect(merged.assetIndex?.id == "1.8")
    #expect(merged.javaVersion?.majorVersion == 8)
    let names = merged.libraries!.map(\.name)
    #expect(names.filter { $0.hasPrefix("com.google.guava:guava:") }.count == 1)   // child wins, no duplicate
    #expect(names.first == "net.minecraftforge:forge:1.8.9-11.15.1.2318-1.8.9")
    let libs = LibraryResolver.resolve(merged.libraries!, env: mac)
    let forge = try #require(libs.first)
    #expect(forge.urls.first?.absoluteString == "https://maven.minecraftforge.net/net/minecraftforge/forge/1.8.9-11.15.1.2318-1.8.9/forge-1.8.9-11.15.1.2318-1.8.9.jar")
    let scala = try #require(libs.first { $0.path.contains("scala-library") })
    #expect(scala.urls.map(\.host) == ["maven.minecraftforge.net", "libraries.minecraft.net", "repo1.maven.org"])
}

@Test func modernArguments() throws {
    let argv = try LaunchCommand.build(version("modern"), context(), env: mac)
    #expect(argv.first == "/j/java")
    #expect(argv.contains("-XstartOnFirstThread"))
    #expect(!argv.contains("-Xss1M") && !argv.contains("--demo") && !argv.contains("--width"))
    #expect(argv.contains("-Xmx3072M"))
    #expect(argv.contains("-Dlog4j.configurationFile=/a/log.xml"))
    let cp = try #require(argv.firstIndex(of: "-cp"))
    #expect(argv[cp + 1] == "/l/a.jar:/v/c.jar")
    #expect(argv.contains("-Djava.library.path=/n"))
    let main = try #require(argv.firstIndex(of: "net.minecraft.client.main.Main"))
    #expect(Array(argv[(main + 1)...].prefix(4)) == ["--username", "Steve", "--version", "1.21.5"])
    #expect(argv.contains("24"))   // asset index id
}

@Test func legacyArguments() throws {
    let merged = try version("forge189").merged(over: version("legacy"))
    let argv = try LaunchCommand.build(merged, context(), env: mac)
    #expect(!argv.contains("-XstartOnFirstThread"))
    #expect(argv.contains("-Djava.library.path=/n"))
    let main = try #require(argv.firstIndex(of: "net.minecraft.launchwrapper.Launch"))
    let game = Array(argv[(main + 1)...])
    #expect(game.suffix(4) == ["--tweakClass", "net.minecraftforge.fml.common.launcher.FMLTweaker", "--versionType", "Forge"])
    #expect(game.contains("{}") && game.contains("1.8"))
}

@Test func manifestAndRuntime() throws {
    let m = try VersionManifest.decode(fixture("manifest"))
    #expect(m.latest.release == "1.21.11")
    #expect(m.versions.map(\.id) == ["26.3", "1.8.9"])
    #expect(m.versions[1].sha1 == "def")

    let idx = try JSONDecoder().decode(JavaRuntimeIndex.self, from: fixture("runtimes"))
    #expect(JavaRuntimeIndex.major("1.8.0_74") == 8 && JavaRuntimeIndex.major("8u462") == 8 && JavaRuntimeIndex.major("25.0.1") == 25)
    #expect(idx.pick(platform: "mac-os-arm64", component: "java-runtime-delta", minMajor: 21)?.component == "java-runtime-delta")
    // Graft needs 25 on 1.21.x although the JSON asks for delta (21).
    #expect(idx.pick(platform: "mac-os-arm64", component: "java-runtime-delta", minMajor: 25)?.component == "java-runtime-epsilon")
    #expect(idx.pick(platform: "mac-os-arm64", component: "jre-legacy", minMajor: 8)?.component == "jre-legacy")
    #expect(idx.pick(platform: "mac-os-arm64", component: "x", minMajor: 30) == nil)
}

@Test func pkceAndAuthURL() throws {
    // RFC 7636 appendix B.
    #expect(PKCE.challenge(for: "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk") == "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM")
    #expect(PKCE().verifier.count == 43)
    let pkce = PKCE(verifier: "v")
    let url = MicrosoftAuth(clientID: "abc").authorizeURL(pkce: pkce, state: "s1")
    let q = Dictionary(uniqueKeysWithValues: (URLComponents(url: url, resolvingAgainstBaseURL: false)?.queryItems ?? []).map { ($0.name, $0.value ?? "") })
    #expect(url.host() == "login.microsoftonline.com" && url.path() == "/consumers/oauth2/v2.0/authorize")
    #expect(q["client_id"] == "abc" && q["code_challenge"] == pkce.challenge && q["code_challenge_method"] == "S256")
    #expect(q["redirect_uri"] == "honeycrisp://auth" && q["scope"] == "XboxLive.signin offline_access" && q["state"] == "s1")
    #expect(try MicrosoftAuth.code(from: URL(string: "honeycrisp://auth?code=C1&state=s1")!, state: "s1") == "C1")
    #expect(throws: AuthError.cancelled) { try MicrosoftAuth.code(from: URL(string: "honeycrisp://auth?code=C1&state=evil")!, state: "s1") }
    #expect(HTTP.formEncode(["b": "x y", "a": "1&2"]) == "a=1%262&b=x%20y")
}

@Test func offlineUUID() {
    #expect(LaunchCommand.offlineUUID("Notch").uuidString.lowercased() == "b50ad385-829d-3141-a216-7e7d7539ba7f")
    #expect(Account.offline("Notch").uuid == "b50ad385829d3141a2167e7d7539ba7f")
}

@Test func distJarMatching() {
    #expect(Catalog.jar("graft-1.21.1-1.0.0.jar", matches: "1.21.1"))
    #expect(!Catalog.jar("graft-1.21.10-1.0.0.jar", matches: "1.21.1"))
    #expect(Catalog.jar("graftshaders-fabric-1.10.8-snapshot+mc1.21.11-local.jar", matches: "1.21.11"))
    #expect(!Catalog.jar("graftshaders-fabric-1.10.8-snapshot+mc1.21.11-local.jar", matches: "1.21.1"))
    #expect(Catalog.jar("honeycrisp-26.3.jar", matches: "26.3"))
    #expect(!Catalog.jar("graft-26.3-1.0.0.jar.disabled", matches: "26.3"))
    #expect(Catalog.family("graftshaders-fabric-1.9.7-snapshot+mc1.21.8-local.jar") == "graftshaders-fabric")
    #expect(Catalog.family("graft-1.8.9-1.0.0.jar") == "graft")
    #expect(Modrinth.versionsURL(slug: "sodium", mc: "1.21.8", loader: "fabric").absoluteString
            == "https://api.modrinth.com/v2/project/sodium/version?game_versions=%5B%221.21.8%22%5D&loaders=%5B%22fabric%22%5D")
}

@Test func customResolution() throws {
    var c = context()
    c.windowSize = (1280, 720)
    let modern = try LaunchCommand.build(version("modern"), c, env: mac)
    #expect(modern.suffix(2) == ["--width", "1280"])
    let legacy = try LaunchCommand.build(version("forge189").merged(over: version("legacy")), c, env: mac)
    #expect(legacy.suffix(4) == ["--width", "1280", "--height", "720"])
}

// MARK: GitHub releases + pinned Sodium (fixtures, no network)

func release() throws -> GitHub.Release { try JSONDecoder().decode(GitHub.Release.self, from: fixture("github_release")) }

@Test func githubAssetSelection() throws {
    let r = try release()
    #expect(r.tag_name == "v1.0.0")
    // "graft-" must not pick the graftshaders jar listed first, and 1.21.1 must not pick 1.21.11.
    #expect(r.jar(.graft, mc: "1.21.11")?.name == "graft-1.21.11-1.0.0.jar")
    #expect(r.jar(.graftshaders, mc: "1.21.11")?.name == "graftshaders-1.21.11-1.0.0.jar")
    #expect(r.jar(.graft, mc: "1.21.1")?.name == "graft-1.21.1-1.0.0.jar")
    #expect(r.jar(.graft, mc: "26.3")?.name == "graft-26.3-1.0.0.jar")   // not the -sources jar
    #expect(r.jar(.graftshaders, mc: "26.3") == nil)                      // missing version
    #expect(r.jar(.graft, mc: "1.8.9") == nil)                            // only a .sha256 file, not a jar
    #expect(r.jar(.honeycrisp, mc: "1.21.11") == nil)
}

@Test func githubDigest() throws {
    let r = try release()
    let hello = "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824"
    #expect(r.jar(.graft, mc: "1.21.11")?.checksum == .sha256(hello))
    #expect(r.jar(.graftshaders, mc: "1.21.11")?.checksum == .sha256(String(repeating: "a", count: 64)))   // lowercased
    #expect(r.jar(.graft, mc: "1.21.1")?.checksum == nil)    // "digest": null → size check
    #expect(r.jar(.graft, mc: "26.3")?.checksum == nil)      // no digest key
    #expect(GitHub.sha256("sha256:abc") == nil)
    #expect(GitHub.sha256("md5:" + hello) == nil)
    #expect(GitHub.sha256(nil) == nil)

    let f = FileManager.default.temporaryDirectory.appending(path: "hc-digest-\(UUID().uuidString)")
    try Data("hello".utf8).write(to: f)
    defer { try? FileManager.default.removeItem(at: f) }
    #expect(Checksum.sha256(hello).matches(f))
    #expect(!Checksum.sha256(String(repeating: "0", count: 64)).matches(f))
}

@Test func githubUpdateComparison() throws {
    let latest: [GitHub.Project: GitHub.Release] = [.graft: try release()]
    func jar(_ file: String, _ tag: String?) -> ModJar { ModJar(project: "graft", file: file, tag: tag, source: "kkorenn/Graft") }
    #expect(!GitHub.updateAvailable(installed: [jar("graft-1.21.11-1.0.0.jar", "v1.0.0")], latest: latest, mc: "1.21.11"))
    #expect(GitHub.updateAvailable(installed: [jar("graft-1.21.11-0.9.0.jar", "v0.9.0")], latest: latest, mc: "1.21.11"))
    #expect(GitHub.updateAvailable(installed: [jar("graft-1.21.11-1.0.0.jar", "v0.9.9")], latest: latest, mc: "1.21.11"))
    #expect(GitHub.updateAvailable(installed: [], latest: latest, mc: "1.21.11"))
    #expect(!GitHub.updateAvailable(installed: [], latest: latest, mc: "1.8.9"))     // nothing to install for this version
    #expect(!GitHub.updateAvailable(installed: [], latest: [:], mc: "1.21.11"))     // offline / rate limited
    #expect(jar("graft-1.21.11-1.0.0.jar", "v1.0.0").version == "1.0.0")
}

@Test func pinnedSodium() throws {
    let v = try JSONDecoder().decode(Modrinth.Version.self, from: fixture("modrinth_version"))
    let f = try Modrinth.file(v, mc: "1.21.7")
    #expect(f.filename == "sodium-fabric-0.7.3+mc1.21.8.jar")
    #expect(f.url.absoluteString == "https://cdn.modrinth.com/data/AANobbMI/versions/7pwil2dy/sodium-fabric-0.7.3%2Bmc1.21.8.jar")
    #expect(throws: InstallError.self) { try Modrinth.file(v, mc: "1.21.9") }

    let item = f.downloadItem(to: URL(filePath: "/tmp/x.jar"))
    #expect(item.sha1 == nil && item.size == 5)   // SHA-512 preferred over SHA-1
    let tmp = FileManager.default.temporaryDirectory.appending(path: "hc-sodium-\(UUID().uuidString)")
    try Data("hello".utf8).write(to: tmp)
    defer { try? FileManager.default.removeItem(at: tmp) }
    #expect(item.checksum?.matches(tmp) == true)
    try Data("hellp".utf8).write(to: tmp)
    #expect(item.checksum?.matches(tmp) == false)

    #expect(Catalog.spec("1.21.9")?.sodium == "sFfidWgd")
    #expect(Catalog.spec("1.21.5")?.sodiumSupported == false)
    #expect(Catalog.instances.filter(\.isLegacy).allSatisfy { $0.sodium == nil })
}
