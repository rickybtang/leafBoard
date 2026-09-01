import Foundation

public actor LocalCardStore {
    public let directory: URL

    public init(directory: URL? = nil) throws {
        if let directory {
            self.directory = directory
        } else {
            let support = try FileManager.default.url(
                for: .applicationSupportDirectory,
                in: .userDomainMask,
                appropriateFor: nil,
                create: true
            )
            self.directory = support.appendingPathComponent("LeafBoardHub/cards", isDirectory: true)
        }
        try FileManager.default.createDirectory(at: self.directory, withIntermediateDirectories: true)
    }

    @discardableResult
    public func save(rawJSON data: Data) throws -> LeafBoardCard {
        let card = try ProtocolJSON.decoder.decode(LeafBoardCard.self, from: data)
        try CardValidator.validate(card)
        try save(card)
        return card
    }

    public func save(_ card: LeafBoardCard) throws {
        try CardValidator.validate(card)
        let producerDirectory = directory.appendingPathComponent(card.producerId, isDirectory: true)
        try FileManager.default.createDirectory(at: producerDirectory, withIntermediateDirectories: true)
        let destination = producerDirectory.appendingPathComponent("\(card.cardId).json")
        let data = try ProtocolJSON.encoder.encode(card)
        try data.write(to: destination, options: .atomic)
    }

    public func allCards() throws -> [LeafBoardCard] {
        guard let producers = try? FileManager.default.contentsOfDirectory(
            at: directory,
            includingPropertiesForKeys: [.isDirectoryKey],
            options: [.skipsHiddenFiles]
        ) else { return [] }
        var result: [LeafBoardCard] = []
        for producer in producers {
            let files = (try? FileManager.default.contentsOfDirectory(
                at: producer,
                includingPropertiesForKeys: nil,
                options: [.skipsHiddenFiles]
            )) ?? []
            for file in files where file.pathExtension == "json" {
                let card = try ProtocolJSON.decoder.decode(LeafBoardCard.self, from: Data(contentsOf: file))
                try CardValidator.validate(card)
                result.append(card)
            }
        }
        return result.sorted { $0.id < $1.id }
    }

    public func allCardsJSON() throws -> Data {
        try ProtocolJSON.encoder.encode(allCards())
    }
}
