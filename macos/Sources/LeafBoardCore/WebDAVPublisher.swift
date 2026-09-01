import CryptoKit
import Foundation

public struct WebDAVCredentials: Sendable {
    public let rootURL: URL
    public let username: String
    public let password: String

    public init(rootURL: URL, username: String, password: String) {
        self.rootURL = rootURL
        self.username = username
        self.password = password
    }
}

public enum WebDAVPublisherError: LocalizedError {
    case invalidCards
    case http(Int, String)

    public var errorDescription: String? {
        switch self {
        case .invalidCards: "一次发布只能包含同一 Producer 的卡片"
        case let .http(status, resource): "WebDAV \(resource) 返回 HTTP \(status)"
        }
    }
}

public struct WebDAVPublisher: Sendable {
    public init() {}

    public func prepare(credentials: WebDAVCredentials, producerId: String) async throws {
        let root = credentials.rootURL
        let v1 = root.appendingPathComponent("v1", isDirectory: true)
        let producers = v1.appendingPathComponent("producers", isDirectory: true)
        let producer = producers.appendingPathComponent(producerId, isDirectory: true)
        let cardsDirectory = producer.appendingPathComponent("cards", isDirectory: true)
        for directory in [root, v1, producers, producer, cardsDirectory] {
            try await makeDirectory(directory, credentials: credentials)
        }
    }

    public func publish(
        cards: [LeafBoardCard],
        catalogRevision: Int,
        credentials: WebDAVCredentials
    ) async throws -> ProducerCatalog {
        guard let producerId = cards.first?.producerId,
              cards.allSatisfy({ $0.producerId == producerId }) else {
            throw WebDAVPublisherError.invalidCards
        }
        for card in cards { try CardValidator.validate(card) }

        try await prepare(credentials: credentials, producerId: producerId)
        let producer = credentials.rootURL
            .appendingPathComponent("v1", isDirectory: true)
            .appendingPathComponent("producers", isDirectory: true)
            .appendingPathComponent(producerId, isDirectory: true)
        let cardsDirectory = producer.appendingPathComponent("cards", isDirectory: true)

        var entries: [CatalogEntry] = []
        for card in cards.sorted(by: { $0.cardId < $1.cardId }) {
            let data = try ProtocolJSON.encoder.encode(card)
            let destination = cardsDirectory.appendingPathComponent("\(card.cardId).json")
            try await put(data, at: destination, credentials: credentials)
            entries.append(CatalogEntry(
                cardId: card.cardId,
                path: "cards/\(card.cardId).json",
                revision: card.revision,
                sha256: SHA256.hash(data: data).map { String(format: "%02x", $0) }.joined()
            ))
        }

        let catalog = ProducerCatalog(
            producerId: producerId,
            revision: catalogRevision,
            updatedAt: ProtocolJSON.iso8601(),
            cards: entries
        )
        try await put(
            ProtocolJSON.encoder.encode(catalog),
            at: producer.appendingPathComponent("catalog.json"),
            credentials: credentials
        )
        return catalog
    }

    private func makeDirectory(_ url: URL, credentials: WebDAVCredentials) async throws {
        var components = URLComponents(url: url, resolvingAgainstBaseURL: false)!
        if !components.path.hasSuffix("/") { components.path += "/" }
        var request = authorizedRequest(url: components.url!, method: "MKCOL", credentials: credentials)
        request.setValue("0", forHTTPHeaderField: "Content-Length")
        let (_, response) = try await URLSession.shared.data(for: request)
        let status = try httpStatus(response)
        guard status == 201 || status == 405 else {
            throw WebDAVPublisherError.http(status, url.lastPathComponent)
        }
    }

    private func put(_ data: Data, at url: URL, credentials: WebDAVCredentials) async throws {
        var request = authorizedRequest(url: url, method: "PUT", credentials: credentials)
        request.setValue("application/json; charset=utf-8", forHTTPHeaderField: "Content-Type")
        request.httpBody = data
        let (_, response) = try await URLSession.shared.data(for: request)
        let status = try httpStatus(response)
        guard [200, 201, 204].contains(status) else {
            throw WebDAVPublisherError.http(status, url.lastPathComponent)
        }
    }

    private func authorizedRequest(url: URL, method: String, credentials: WebDAVCredentials) -> URLRequest {
        var request = URLRequest(url: url, timeoutInterval: 20)
        request.httpMethod = method
        let token = Data("\(credentials.username):\(credentials.password)".utf8).base64EncodedString()
        request.setValue("Basic \(token)", forHTTPHeaderField: "Authorization")
        request.setValue("LeafBoardHub/0.1", forHTTPHeaderField: "User-Agent")
        return request
    }

    private func httpStatus(_ response: URLResponse) throws -> Int {
        guard let response = response as? HTTPURLResponse else {
            throw WebDAVPublisherError.http(0, "invalid response")
        }
        return response.statusCode
    }
}
