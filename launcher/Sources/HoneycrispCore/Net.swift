import CryptoKit
import Foundation

public enum Hash {
    public static func md5(_ d: Data) -> Data { Data(Insecure.MD5.hash(data: d)) }

    public static func sha1(file url: URL) -> String? { hex(Insecure.SHA1(), file: url) }
    public static func sha256(file url: URL) -> String? { hex(SHA256(), file: url) }
    public static func sha512(file url: URL) -> String? { hex(SHA512(), file: url) }

    static func hex<H: HashFunction>(_ hasher: H, file url: URL) -> String? {
        guard let h = try? FileHandle(forReadingFrom: url) else { return nil }
        defer { try? h.close() }
        var sha = hasher
        while let chunk = try? h.read(upToCount: 1 << 20), !chunk.isEmpty { sha.update(data: chunk) }
        return sha.finalize().map { String(format: "%02x", $0) }.joined()
    }
}

public struct HTTPError: LocalizedError {
    public var status: Int
    public var url: URL?
    public var body: String
    public var errorDescription: String? { "HTTP \(status) from \(url?.host() ?? "?"): \(body.prefix(300))" }
}

public enum HTTP {
    static let session: URLSession = {
        let c = URLSessionConfiguration.default
        c.httpMaximumConnectionsPerHost = 16
        c.timeoutIntervalForRequest = 60
        return URLSession(configuration: c)
    }()

    public static func data(_ req: URLRequest) async throws -> Data {
        var r = req
        r.setValue("Honeycrisp-Launcher/1.0", forHTTPHeaderField: "User-Agent")
        let (data, resp) = try await session.data(for: r)
        let status = (resp as? HTTPURLResponse)?.statusCode ?? 0
        guard (200..<300).contains(status) else {
            throw HTTPError(status: status, url: req.url, body: String(decoding: data, as: UTF8.self))
        }
        return data
    }

    public static func get<T: Decodable>(_ url: URL, as: T.Type = T.self, headers: [String: String] = [:]) async throws -> T {
        var r = URLRequest(url: url)
        headers.forEach { r.setValue($0.value, forHTTPHeaderField: $0.key) }
        return try JSONDecoder().decode(T.self, from: try await data(r))
    }

    public static func postJSON<T: Decodable>(_ url: URL, body: some Encodable, headers: [String: String] = [:], as: T.Type = T.self) async throws -> T {
        var r = URLRequest(url: url)
        r.httpMethod = "POST"
        r.httpBody = try JSONEncoder().encode(body)
        r.setValue("application/json", forHTTPHeaderField: "Content-Type")
        r.setValue("application/json", forHTTPHeaderField: "Accept")
        headers.forEach { r.setValue($0.value, forHTTPHeaderField: $0.key) }
        return try JSONDecoder().decode(T.self, from: try await data(r))
    }

    public static func postForm<T: Decodable>(_ url: URL, _ form: [String: String], as: T.Type = T.self) async throws -> T {
        var r = URLRequest(url: url)
        r.httpMethod = "POST"
        r.httpBody = Data(formEncode(form).utf8)
        r.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")
        return try JSONDecoder().decode(T.self, from: try await data(r))
    }

    public static func formEncode(_ form: [String: String]) -> String {
        var allowed = CharacterSet.alphanumerics
        allowed.insert(charactersIn: "-._~")
        return form.sorted { $0.key < $1.key }
            .map { "\($0.key)=\($0.value.addingPercentEncoding(withAllowedCharacters: allowed) ?? $0.value)" }
            .joined(separator: "&")
    }
}

/// A stronger hash than the SHA-1 most Mojang/Modrinth metadata carries (GitHub asset digests, Modrinth sha512).
public enum Checksum: Sendable, Equatable {
    case sha256(String), sha512(String)

    public func matches(_ file: URL) -> Bool {
        switch self {
        case .sha256(let h): Hash.sha256(file: file) == h.lowercased()
        case .sha512(let h): Hash.sha512(file: file) == h.lowercased()
        }
    }
}

public struct DownloadItem: Sendable {
    public var urls: [URL]
    public var dest: URL
    public var sha1: String?
    public var size: Int?
    public var checksum: Checksum?

    public init(urls: [URL], dest: URL, sha1: String? = nil, size: Int? = nil, checksum: Checksum? = nil) {
        self.urls = urls; self.dest = dest; self.sha1 = sha1; self.size = size; self.checksum = checksum
    }
    public init(url: URL, dest: URL, sha1: String? = nil, size: Int? = nil, checksum: Checksum? = nil) {
        self.init(urls: [url], dest: dest, sha1: sha1, size: size, checksum: checksum)
    }

    /// Present files count as done when their size matches (or, without a size, their hash). Cheap enough to run before every launch.
    var isPresent: Bool {
        guard let attrs = try? FileManager.default.attributesOfItem(atPath: dest.path), let len = attrs[.size] as? Int, len > 0 else { return false }
        if let checksum { return checksum.matches(dest) }
        if let size { return len == size }
        if let sha1 { return Hash.sha1(file: dest) == sha1 }
        return true
    }
}

public enum Downloader {
    public struct Failure: LocalizedError {
        public var item: URL
        public var reason: String
        public var errorDescription: String? { "Download failed: \(item.lastPathComponent) (\(reason))" }
    }

    /// Parallel, SHA-1 verified. Resumable at file granularity: finished files are skipped on the next run.
    /// ponytail: no byte-range resume; a half-downloaded file restarts (largest single file is the ~30 MB client jar).
    public static func run(_ items: [DownloadItem], concurrency: Int = 16,
                           progress: @escaping @Sendable (_ done: Int, _ total: Int) -> Void = { _, _ in }) async throws {
        var seen = Set<String>()
        let items = items.filter { seen.insert($0.dest.path).inserted }   // two writers to one path would race
        let total = items.count
        guard total > 0 else { return }
        var done = 0
        try await withThrowingTaskGroup(of: Void.self) { group in
            var it = items.makeIterator()
            func next() -> Bool {
                guard let item = it.next() else { return false }
                group.addTask { try await fetch(item) }
                return true
            }
            for _ in 0..<concurrency where !next() { break }
            while try await group.next() != nil {
                done += 1
                progress(done, total)
                _ = next()
            }
        }
    }

    static func fetch(_ item: DownloadItem) async throws {
        if item.isPresent { return }
        let fm = FileManager.default
        try fm.createDirectory(at: item.dest.deletingLastPathComponent(), withIntermediateDirectories: true)
        var lastError = "no URL"
        for url in item.urls {
            for attempt in 0..<3 {
                if attempt > 0 { try await Task.sleep(for: .milliseconds(500 * attempt)) }
                do {
                    let (tmp, resp) = try await HTTP.session.download(from: url)
                    defer { try? fm.removeItem(at: tmp) }
                    let status = (resp as? HTTPURLResponse)?.statusCode ?? 0
                    if status == 404 { lastError = "404"; break }   // try the next mirror
                    guard (200..<300).contains(status) else { lastError = "HTTP \(status)"; continue }
                    if let sha1 = item.sha1, Hash.sha1(file: tmp) != sha1 { lastError = "SHA-1 mismatch"; continue }
                    if let c = item.checksum, !c.matches(tmp) { lastError = "checksum mismatch"; continue }
                    _ = try? fm.removeItem(at: item.dest)
                    try fm.moveItem(at: tmp, to: item.dest)
                    return
                } catch is CancellationError {
                    throw CancellationError()
                } catch {
                    lastError = error.localizedDescription
                }
            }
        }
        throw Failure(item: item.urls.first ?? item.dest, reason: lastError)
    }
}
