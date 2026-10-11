import CryptoKit
import Foundation
import Security

public struct Account: Codable, Sendable, Equatable {
    public enum Kind: String, Codable, Sendable { case microsoft, offline }
    public var kind: Kind
    public var name: String
    public var uuid: String              // undashed
    public var accessToken: String       // Minecraft services token ("0" offline)
    public var expires: Date
    public var msRefreshToken: String?
    public var xuid: String?

    public static func offline(_ name: String) -> Account {
        Account(kind: .offline, name: name,
                uuid: LaunchCommand.offlineUUID(name).uuidString.replacingOccurrences(of: "-", with: "").lowercased(),
                accessToken: "0", expires: .distantFuture)
    }

    public var needsRefresh: Bool { kind == .microsoft && expires.timeIntervalSinceNow < 300 }
}

/// RFC 7636 PKCE pair.
public struct PKCE: Sendable {
    public var verifier: String
    public var challenge: String { PKCE.challenge(for: verifier) }

    public init(verifier: String? = nil) {
        self.verifier = verifier ?? PKCE.base64url(Data((0..<32).map { _ in UInt8.random(in: 0...255) }))
    }

    public static func challenge(for verifier: String) -> String { base64url(Data(SHA256.hash(data: Data(verifier.utf8)))) }

    public static func base64url(_ d: Data) -> String {
        d.base64EncodedString().replacingOccurrences(of: "+", with: "-").replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
    }
}

public enum AuthError: LocalizedError, Equatable {
    case noClientID
    case clientNotApproved
    case noXboxAccount
    case childAccount
    case xbox(String)
    case noGameOwnership
    case noProfile
    case cancelled
    case deviceCodeExpired

    public var errorDescription: String? {
        switch self {
        case .noClientID: "No Azure client ID set. Add one in Settings (or the MCClientID Info.plist key)."
        case .clientNotApproved: "Client ID not approved yet: Mojang has not enabled Minecraft services for this Azure app. Use Offline mode until the approval arrives."
        case .noXboxAccount: "This Microsoft account has no Xbox profile. Sign in once at minecraft.net to create one."
        case .childAccount: "This is a child account; an adult must add it to a Microsoft family first."
        case .xbox(let s): "Xbox Live sign-in failed (\(s))."
        case .noGameOwnership: "This account does not own Minecraft: Java Edition."
        case .noProfile: "No Minecraft profile yet. Pick a name at minecraft.net first."
        case .cancelled: "Sign-in cancelled."
        case .deviceCodeExpired: "The sign-in code expired. Try again."
        }
    }
}

/// Microsoft → Xbox Live → XSTS → Minecraft services. All endpoints are the public ones documented on wiki.vg / minecraft.wiki.
public struct MicrosoftAuth: Sendable {
    public static let redirectURI = "honeycrisp://auth"
    public static let callbackScheme = "honeycrisp"
    public static let scope = "XboxLive.signin offline_access"
    static let oauth = "https://login.microsoftonline.com/consumers/oauth2/v2.0"

    public var clientID: String
    public init(clientID: String) { self.clientID = clientID }

    public func authorizeURL(pkce: PKCE, state: String) -> URL {
        var c = URLComponents(string: "\(Self.oauth)/authorize")!
        c.queryItems = [
            .init(name: "client_id", value: clientID),
            .init(name: "response_type", value: "code"),
            .init(name: "redirect_uri", value: Self.redirectURI),
            .init(name: "scope", value: Self.scope),
            .init(name: "code_challenge", value: pkce.challenge),
            .init(name: "code_challenge_method", value: "S256"),
            .init(name: "state", value: state),
            .init(name: "prompt", value: "select_account"),
        ]
        return c.url!
    }

    /// Pulls the code out of honeycrisp://auth?code=…&state=…, checking state.
    public static func code(from callback: URL, state: String) throws -> String {
        let items = URLComponents(url: callback, resolvingAgainstBaseURL: false)?.queryItems ?? []
        func q(_ n: String) -> String? { items.first { $0.name == n }?.value }
        if let e = q("error") { throw e == "access_denied" ? AuthError.cancelled : AuthError.xbox(q("error_description") ?? e) }
        guard q("state") == state, let code = q("code") else { throw AuthError.cancelled }
        return code
    }

    struct TokenResponse: Decodable { var access_token: String; var refresh_token: String?; var expires_in: Int? }
    struct OAuthError: Decodable { var error: String }

    public func redeem(code: String, pkce: PKCE) async throws -> Account {
        let t: TokenResponse = try await HTTP.postForm(URL(string: "\(Self.oauth)/token")!, [
            "client_id": clientID, "grant_type": "authorization_code", "code": code,
            "redirect_uri": Self.redirectURI, "code_verifier": pkce.verifier, "scope": Self.scope,
        ])
        return try await minecraft(msToken: t.access_token, refresh: t.refresh_token)
    }

    public func refresh(_ account: Account) async throws -> Account {
        guard let rt = account.msRefreshToken else { throw AuthError.cancelled }
        let t: TokenResponse = try await HTTP.postForm(URL(string: "\(Self.oauth)/token")!, [
            "client_id": clientID, "grant_type": "refresh_token", "refresh_token": rt, "scope": Self.scope,
        ])
        return try await minecraft(msToken: t.access_token, refresh: t.refresh_token ?? rt)
    }

    // MARK: Device code (fallback; needs "Allow public client flows" on the Azure app)

    public struct DeviceCode: Decodable, Sendable {
        public var device_code: String
        public var user_code: String
        public var verification_uri: String
        public var expires_in: Int
        public var interval: Int
        public var message: String?
    }

    public func startDeviceCode() async throws -> DeviceCode {
        try await HTTP.postForm(URL(string: "\(Self.oauth)/devicecode")!, ["client_id": clientID, "scope": Self.scope])
    }

    public func pollDeviceCode(_ dc: DeviceCode) async throws -> Account {
        let deadline = Date().addingTimeInterval(TimeInterval(dc.expires_in))
        var interval = max(dc.interval, 1)
        while Date() < deadline {
            try await Task.sleep(for: .seconds(interval))
            do {
                let t: TokenResponse = try await HTTP.postForm(URL(string: "\(Self.oauth)/token")!, [
                    "client_id": clientID, "grant_type": "urn:ietf:params:oauth:grant-type:device_code", "device_code": dc.device_code,
                ])
                return try await minecraft(msToken: t.access_token, refresh: t.refresh_token)
            } catch let e as HTTPError {
                let err = (try? JSONDecoder().decode(OAuthError.self, from: Data(e.body.utf8)))?.error
                switch err {
                case "authorization_pending": continue
                case "slow_down": interval += 5
                case "authorization_declined": throw AuthError.cancelled
                case "expired_token": throw AuthError.deviceCodeExpired
                default: throw e
                }
            }
        }
        throw AuthError.deviceCodeExpired
    }

    // MARK: Xbox Live → XSTS → Minecraft

    struct XboxResponse: Decodable {
        struct Claims: Decodable { var xui: [[String: String]] }
        var Token: String
        var DisplayClaims: Claims
    }
    struct XErr: Decodable { var XErr: Int? }

    func minecraft(msToken: String, refresh: String?) async throws -> Account {
        struct XBLBody: Encodable {
            struct P: Encodable { var AuthMethod = "RPS"; var SiteName = "user.auth.xboxlive.com"; var RpsTicket: String }
            var Properties: P; var RelyingParty = "http://auth.xboxlive.com"; var TokenType = "JWT"
        }
        let xbl: XboxResponse = try await HTTP.postJSON(URL(string: "https://user.auth.xboxlive.com/user/authenticate")!,
                                                         body: XBLBody(Properties: .init(RpsTicket: "d=\(msToken)")))

        struct XSTSBody: Encodable {
            struct P: Encodable { var SandboxId = "RETAIL"; var UserTokens: [String] }
            var Properties: P; var RelyingParty = "rp://api.minecraftservices.com/"; var TokenType = "JWT"
        }
        let xsts: XboxResponse
        do {
            xsts = try await HTTP.postJSON(URL(string: "https://xsts.auth.xboxlive.com/xsts/authorize")!,
                                           body: XSTSBody(Properties: .init(UserTokens: [xbl.Token])))
        } catch let e as HTTPError where e.status == 401 {
            switch (try? JSONDecoder().decode(XErr.self, from: Data(e.body.utf8)))?.XErr {
            case 2148916233: throw AuthError.noXboxAccount
            case 2148916238: throw AuthError.childAccount
            case let code?: throw AuthError.xbox("XErr \(code)")
            case nil: throw e
            }
        }
        guard let uhs = xsts.DisplayClaims.xui.first?["uhs"] else { throw AuthError.xbox("no user hash") }

        struct MCLogin: Decodable { var access_token: String; var expires_in: Int }
        let mc: MCLogin
        do {
            mc = try await HTTP.postJSON(URL(string: "https://api.minecraftservices.com/authentication/login_with_xbox")!,
                                         body: ["identityToken": "XBL3.0 x=\(uhs);\(xsts.Token)"])
        } catch let e as HTTPError where e.status == 403 {
            // Unapproved Azure apps get 403 "Invalid app registration" here.
            throw AuthError.clientNotApproved
        }
        let bearer = ["Authorization": "Bearer \(mc.access_token)"]

        struct Entitlements: Decodable { struct Item: Decodable { var name: String }; var items: [Item] }
        let ent: Entitlements = try await HTTP.get(URL(string: "https://api.minecraftservices.com/entitlements/mcstore")!, headers: bearer)
        guard ent.items.contains(where: { $0.name == "game_minecraft" || $0.name == "product_minecraft" }) else { throw AuthError.noGameOwnership }

        struct Profile: Decodable { var id: String; var name: String }
        let profile: Profile
        do {
            profile = try await HTTP.get(URL(string: "https://api.minecraftservices.com/minecraft/profile")!, headers: bearer)
        } catch let e as HTTPError where e.status == 404 {
            throw AuthError.noProfile
        }
        return Account(kind: .microsoft, name: profile.name, uuid: profile.id, accessToken: mc.access_token,
                       expires: Date().addingTimeInterval(TimeInterval(mc.expires_in)), msRefreshToken: refresh,
                       xuid: xsts.DisplayClaims.xui.first?["xid"])
    }
}

/// Saved accounts (several, one active), kept in the login keychain as one generic-password item.
public struct AccountStore: Codable, Sendable, Equatable {
    public var accounts: [Account] = []
    public var activeUUID: String?
    public init() {}
    public var active: Account? { accounts.first { $0.uuid == activeUUID } ?? accounts.first }

    public mutating func upsert(_ a: Account) {
        accounts.removeAll { $0.uuid == a.uuid }
        accounts.append(a)
        activeUUID = a.uuid
    }
}

public enum Keychain {
    static let service = "dev.honeycrisp.launcher"
    static let key = "accounts"

    public static func load() -> AccountStore {
        var out: CFTypeRef?
        let q: [String: Any] = [kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: service,
                                kSecAttrAccount as String: key, kSecReturnData as String: true]
        guard SecItemCopyMatching(q as CFDictionary, &out) == errSecSuccess, let d = out as? Data,
              let s = try? JSONDecoder().decode(AccountStore.self, from: d) else { return AccountStore() }
        return s
    }

    @discardableResult
    public static func save(_ store: AccountStore) -> Bool {
        let q: [String: Any] = [kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: service, kSecAttrAccount as String: key]
        guard let d = try? JSONEncoder().encode(store) else { return false }
        if SecItemUpdate(q as CFDictionary, [kSecValueData as String: d] as CFDictionary) == errSecSuccess { return true }
        var add = q
        add[kSecValueData as String] = d
        add[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlock
        return SecItemAdd(add as CFDictionary, nil) == errSecSuccess
    }
}
