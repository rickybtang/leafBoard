import Foundation
import Testing
@testable import LeafBoardCore

@Test func metricCardValidatesAndRoundTrips() throws {
    let card = validMetricCard()
    try CardValidator.validate(card)
    let data = try ProtocolJSON.encoder.encode(card)
    #expect(try ProtocolJSON.decoder.decode(LeafBoardCard.self, from: data) == card)
}

@Test func localStoreSavesAndLoadsValidatedCards() async throws {
    let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString, isDirectory: true)
    try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
    defer { try? FileManager.default.removeItem(at: directory) }
    let store = try LocalCardStore(directory: directory)

    try await store.save(validMetricCard())
    let cards = try await store.allCards()

    #expect(cards.count == 1)
    #expect(cards.first?.id == "example-producer/example-metric")
}

@Test func validatorRejectsPreferredSizeOutsideAllowedSizes() {
    var card = validMetricCard()
    card = LeafBoardCard(
        producerId: card.producerId,
        cardId: card.cardId,
        revision: card.revision,
        type: card.type,
        updatedAt: card.updatedAt,
        content: card.content,
        presentation: CardPresentation(icon: "metric", preferredSize: .large, allowedSizes: [.small], status: "normal")
    )
    #expect(throws: CardValidationError.self) { try CardValidator.validate(card) }
}

@Test func validatorRejectsNonStandardMoneyUnit() {
    let card = LeafBoardCard(
        producerId: "example-producer",
        cardId: "money",
        revision: 1,
        type: .metric,
        updatedAt: ProtocolJSON.iso8601(),
        content: CardContent(title: "金额", fields: [
            CardField(key: "balance", label: "余额", value: .number(10), format: "money", unit: "元", role: "primary", minSize: .small)
        ]),
        presentation: CardPresentation(icon: "money", preferredSize: .small, allowedSizes: [.small], status: "normal")
    )
    #expect(throws: CardValidationError.self) { try CardValidator.validate(card) }
}

@Test func validatorRequiresExactlyOnePrimaryField() {
    let missing = LeafBoardCard(
        producerId: "example-producer",
        cardId: "missing-primary",
        revision: 1,
        type: .metric,
        updatedAt: ProtocolJSON.iso8601(),
        content: CardContent(title: "指标", fields: [
            CardField(key: "value", label: "值", value: .number(1), format: "number", role: "detail", minSize: .small)
        ]),
        presentation: CardPresentation(icon: "metric", preferredSize: .small, allowedSizes: [.small], status: "normal")
    )
    let duplicate = LeafBoardCard(
        producerId: "example-producer",
        cardId: "duplicate-primary",
        revision: 1,
        type: .metric,
        updatedAt: ProtocolJSON.iso8601(),
        content: CardContent(title: "指标", fields: [
            CardField(key: "first", label: "第一项", value: .number(1), format: "number", role: "primary", minSize: .small),
            CardField(key: "second", label: "第二项", value: .number(2), format: "number", role: "primary", minSize: .small)
        ]),
        presentation: CardPresentation(icon: "metric", preferredSize: .small, allowedSizes: [.small], status: "normal")
    )
    #expect(throws: CardValidationError.self) { try CardValidator.validate(missing) }
    #expect(throws: CardValidationError.self) { try CardValidator.validate(duplicate) }
}

@Test func validatorAcceptsStructuredLargeDetail() throws {
    let card = LeafBoardCard(
        producerId: "example-producer",
        cardId: "dual-value",
        revision: 1,
        type: .metric,
        updatedAt: ProtocolJSON.iso8601(),
        content: CardContent(title: "用量", fields: [
            CardField(key: "remaining", label: "剩余", value: .number(42), format: "percent", role: "primary", minSize: .small),
            CardField(
                key: "breakdown",
                label: "明细",
                value: .string("模型 A"),
                format: "text",
                role: "detail",
                minSize: .large,
                secondary: CardFieldSecondary(value: .number(1_250_000), format: "number", unit: "token")
            )
        ]),
        presentation: CardPresentation(icon: "metric", preferredSize: .large, allowedSizes: CardSize.allCases, status: "normal")
    )
    try CardValidator.validate(card)
}

@Test func validatorRejectsOverflowingPrimaryAndMisplacedDualValue() {
    let longPrimary = LeafBoardCard(
        producerId: "example-producer",
        cardId: "long-primary",
        revision: 1,
        type: .metric,
        updatedAt: ProtocolJSON.iso8601(),
        content: CardContent(title: "状态", fields: [
            CardField(key: "state", label: "当前状态", value: .string("这是一个超过十二字符的动态主字段"), format: "text", role: "primary", minSize: .small)
        ]),
        presentation: CardPresentation(icon: "status", preferredSize: .medium, allowedSizes: [.medium], status: "normal")
    )
    let misplacedDual = LeafBoardCard(
        producerId: "example-producer",
        cardId: "misplaced-dual",
        revision: 1,
        type: .metric,
        updatedAt: ProtocolJSON.iso8601(),
        content: CardContent(title: "用量", fields: [
            CardField(key: "remaining", label: "剩余", value: .number(42), format: "percent", role: "primary", minSize: .small),
            CardField(
                key: "breakdown",
                label: "明细",
                value: .string("模型 A"),
                format: "text",
                role: "detail",
                minSize: .medium,
                secondary: CardFieldSecondary(value: .number(1_250_000), format: "number", unit: "token")
            )
        ]),
        presentation: CardPresentation(icon: "metric", preferredSize: .large, allowedSizes: CardSize.allCases, status: "normal")
    )
    #expect(throws: CardValidationError.self) { try CardValidator.validate(longPrimary) }
    #expect(throws: CardValidationError.self) { try CardValidator.validate(misplacedDual) }
}

private func validMetricCard() -> LeafBoardCard {
    LeafBoardCard(
        producerId: "example-producer",
        cardId: "example-metric",
        revision: 1,
        type: .metric,
        updatedAt: "2026-09-01T12:00:00+08:00",
        content: CardContent(title: "示例指标", fields: [
            CardField(key: "remaining", label: "剩余", value: .number(72), format: "percent", role: "primary", minSize: .small),
            CardField(key: "last-refresh", label: "采集时间", value: .string("2026-09-01T12:00:00+08:00"), format: "datetime", role: "detail", minSize: .small)
        ]),
        presentation: CardPresentation(icon: "metric", preferredSize: .medium, allowedSizes: CardSize.allCases, status: "normal")
    )
}
