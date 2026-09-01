import Foundation

public enum CardType: String, Codable, Sendable {
    case metric
    case list
    case status
}

public enum CardSize: String, Codable, Sendable, CaseIterable {
    case small
    case medium
    case large

    public var rank: Int {
        switch self {
        case .small: 0
        case .medium: 1
        case .large: 2
        }
    }
}

public enum CardValue: Codable, Sendable, Equatable {
    case string(String)
    case number(Double)
    case boolean(Bool)
    case null

    public init(from decoder: Decoder) throws {
        let container = try decoder.singleValueContainer()
        if container.decodeNil() { self = .null }
        else if let value = try? container.decode(Bool.self) { self = .boolean(value) }
        else if let value = try? container.decode(Double.self) { self = .number(value) }
        else { self = .string(try container.decode(String.self)) }
    }

    public func encode(to encoder: Encoder) throws {
        var container = encoder.singleValueContainer()
        switch self {
        case let .string(value): try container.encode(value)
        case let .number(value): try container.encode(value)
        case let .boolean(value): try container.encode(value)
        case .null: try container.encodeNil()
        }
    }
}

public struct CardField: Codable, Sendable, Equatable {
    public let key: String
    public let label: String
    public let value: CardValue
    public let format: String
    public let unit: String?
    public let role: String
    public let minSize: CardSize
    public let secondary: CardFieldSecondary?

    public init(key: String, label: String, value: CardValue, format: String, unit: String? = nil, role: String, minSize: CardSize, secondary: CardFieldSecondary? = nil) {
        self.key = key
        self.label = label
        self.value = value
        self.format = format
        self.unit = unit
        self.role = role
        self.minSize = minSize
        self.secondary = secondary
    }
}

public struct CardFieldSecondary: Codable, Sendable, Equatable {
    public let value: CardValue
    public let format: String
    public let unit: String?

    public init(value: CardValue, format: String, unit: String? = nil) {
        self.value = value
        self.format = format
        self.unit = unit
    }
}

public struct CardListItem: Codable, Sendable, Equatable {
    public let id: String
    public let text: String
    public let checked: Bool?
    public let dueAt: String?
    public let priority: Int?
}

public struct CardContent: Codable, Sendable, Equatable {
    public let title: String
    public let state: String?
    public let fields: [CardField]?
    public let items: [CardListItem]?

    public init(title: String, state: String? = nil, fields: [CardField]? = nil, items: [CardListItem]? = nil) {
        self.title = title
        self.state = state
        self.fields = fields
        self.items = items
    }
}

public struct CardPresentation: Codable, Sendable, Equatable {
    public let icon: String
    public let preferredSize: CardSize
    public let allowedSizes: [CardSize]
    public let status: String

    public init(icon: String, preferredSize: CardSize, allowedSizes: [CardSize], status: String) {
        self.icon = icon
        self.preferredSize = preferredSize
        self.allowedSizes = allowedSizes
        self.status = status
    }
}

public struct LeafBoardCard: Codable, Sendable, Equatable, Identifiable {
    public let schemaVersion: String
    public let producerId: String
    public let cardId: String
    public let revision: Int
    public let type: CardType
    public let updatedAt: String
    public let expiresAt: String?
    public let content: CardContent
    public let presentation: CardPresentation

    public var id: String { "\(producerId)/\(cardId)" }

    public init(
        schemaVersion: String = "1.0",
        producerId: String,
        cardId: String,
        revision: Int,
        type: CardType,
        updatedAt: String,
        expiresAt: String? = nil,
        content: CardContent,
        presentation: CardPresentation
    ) {
        self.schemaVersion = schemaVersion
        self.producerId = producerId
        self.cardId = cardId
        self.revision = revision
        self.type = type
        self.updatedAt = updatedAt
        self.expiresAt = expiresAt
        self.content = content
        self.presentation = presentation
    }
}

public struct CatalogEntry: Codable, Sendable, Equatable {
    public let cardId: String
    public let path: String
    public let revision: Int
    public let sha256: String
}

public struct ProducerCatalog: Codable, Sendable, Equatable {
    public let schemaVersion: String
    public let producerId: String
    public let revision: Int
    public let updatedAt: String
    public let cards: [CatalogEntry]

    public init(producerId: String, revision: Int, updatedAt: String, cards: [CatalogEntry]) {
        self.schemaVersion = "1.0"
        self.producerId = producerId
        self.revision = revision
        self.updatedAt = updatedAt
        self.cards = cards
    }
}

public enum ProtocolJSON {
    public static let encoder: JSONEncoder = {
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys, .withoutEscapingSlashes]
        return encoder
    }()

    public static let decoder = JSONDecoder()

    public static func iso8601(_ date: Date = .now) -> String {
        ISO8601DateFormatter().string(from: date)
    }
}
