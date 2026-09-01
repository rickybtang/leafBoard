import Foundation

public enum CardValidationError: LocalizedError, Equatable {
    case invalid(String)

    public var errorDescription: String? {
        guard case let .invalid(message) = self else { return nil }
        return message
    }
}

public enum CardValidator {
    private static let idExpression = try! NSRegularExpression(pattern: "^[a-z0-9][a-z0-9._-]{0,63}$")
    private static let formats = Set(["text", "number", "percent", "money", "datetime", "duration", "boolean"])
    private static let dualValueFormats = Set(["text", "number", "percent", "money", "duration"])
    private static let secondaryFormats = Set(["number", "percent", "money", "duration"])
    private static let roles = Set(["primary", "secondary", "detail", "badge"])
    private static let presentationStatuses = Set(["normal", "warning", "error", "unknown"])
    private static let currencyExpression = try! NSRegularExpression(pattern: "^[A-Z]{3}$")

    public static func validate(_ card: LeafBoardCard) throws {
        try require(card.schemaVersion == "1.0", "不支持的 schemaVersion")
        try validateID(card.producerId, name: "producerId")
        try validateID(card.cardId, name: "cardId")
        try require(card.revision >= 0, "revision 必须非负")
        try require(!card.content.title.isEmpty && card.content.title.count <= 24, "title 长度必须为 1 到 24")
        try require(!card.presentation.allowedSizes.isEmpty, "allowedSizes 不能为空")
        try require(card.presentation.allowedSizes.contains(card.presentation.preferredSize), "preferredSize 必须包含在 allowedSizes")
        try require(presentationStatuses.contains(card.presentation.status), "不支持的 presentation.status")

        switch card.type {
        case .metric:
            try require(card.content.items == nil, "metric 不能包含 items")
            try validateFields(card.content.fields, minimum: 1)
        case .status:
            try require(card.content.items == nil, "status 不能包含 items")
            try require(!(card.content.state ?? "").isEmpty, "status 必须包含 state")
            try require(card.content.fields != nil, "status 必须包含 fields")
            try validateFields(card.content.fields, minimum: 1)
        case .list:
            try require(card.content.fields == nil && card.content.state == nil, "list 只能包含 items")
            try require(card.content.items != nil, "list 必须包含 items")
            let items = card.content.items ?? []
            try require(items.count <= 50, "list 最多 50 项")
            for item in items {
                try validateID(item.id, name: "item.id")
                try require(!item.text.isEmpty && item.text.count <= 80, "item.text 长度必须为 1 到 80")
                if let priority = item.priority { try require((1...4).contains(priority), "priority 必须为 1 到 4") }
            }
        }
    }

    private static func validateFields(_ fields: [CardField]?, minimum: Int) throws {
        let values = fields ?? []
        try require(values.count >= minimum && values.count <= 8, "fields 数量不合法")
        try require(values.filter { $0.role == "primary" }.count == 1, "metric/status 必须且只能包含一个 primary 字段")
        for field in values {
            try validateID(field.key, name: "field.key")
            try require(!field.label.isEmpty && field.label.count <= 24, "field.label 长度必须为 1 到 24")
            try require(formats.contains(field.format), "不支持的 field.format")
            try require(roles.contains(field.role), "不支持的 field.role")
            try require((field.unit ?? "").count <= 12, "field.unit 最多 12 字符")
            if field.role == "primary" {
                try require(field.label.count <= 8, "primary.label 最多 8 字符")
                if field.format == "text" {
                    guard case let .string(value) = field.value else {
                        throw CardValidationError.invalid("text primary.value 必须是字符串")
                    }
                    try require(!value.isEmpty && value.count <= 12, "text primary.value 长度必须为 1 到 12")
                }
            }
            if field.format == "money" {
                try validateCurrency(field.unit)
            }
            if field.format == "duration" {
                guard case let .number(value) = field.value, value >= 0 else {
                    throw CardValidationError.invalid("duration.value 必须是非负秒数")
                }
                try require(field.unit == "s", "duration.unit 必须是 s")
            }
            if case let .string(value) = field.value { try require(value.count <= 80, "field.value 最多 80 字符") }
            if let secondary = field.secondary {
                try require(field.role == "detail" && field.minSize == .large, "双值字段只能是 large detail")
                try require(field.label.count <= 8, "双值字段 label 最多 8 字符")
                try require(dualValueFormats.contains(field.format), "双值字段不支持第一个值的 format")
                if field.format == "text" {
                    guard case let .string(value) = field.value else {
                        throw CardValidationError.invalid("text 双值字段第一个值必须是字符串")
                    }
                    try require(!value.isEmpty && value.count <= 12, "双值字段第一个文本值长度必须为 1 到 12")
                } else {
                    guard case .number = field.value else {
                        throw CardValidationError.invalid("数值双值字段第一个值必须是数字")
                    }
                }
                try require(secondaryFormats.contains(secondary.format), "双值字段不支持 secondary.format")
                guard case .number = secondary.value else {
                    throw CardValidationError.invalid("双值字段 secondary.value 必须是数字")
                }
                try require((secondary.unit ?? "").count <= 12, "secondary.unit 最多 12 字符")
                if secondary.format == "money" { try validateCurrency(secondary.unit) }
                if secondary.format == "duration" {
                    guard case let .number(value) = secondary.value, value >= 0 else {
                        throw CardValidationError.invalid("secondary duration.value 必须是非负秒数")
                    }
                    try require(secondary.unit == "s", "secondary duration.unit 必须是 s")
                }
            }
        }
    }

    private static func validateCurrency(_ candidate: String?) throws {
        let unit = candidate ?? ""
        let range = NSRange(unit.startIndex..<unit.endIndex, in: unit)
        try require(currencyExpression.firstMatch(in: unit, range: range) != nil, "money.unit 必须是 ISO 4217 三字母币种")
    }

    private static func validateID(_ value: String, name: String) throws {
        let range = NSRange(value.startIndex..<value.endIndex, in: value)
        try require(idExpression.firstMatch(in: value, range: range) != nil, "\(name) 不合法")
    }

    private static func require(_ condition: @autoclosure () -> Bool, _ message: String) throws {
        if !condition() { throw CardValidationError.invalid(message) }
    }
}
