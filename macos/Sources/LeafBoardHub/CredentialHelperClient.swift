import Foundation

enum CredentialHelperError: LocalizedError {
    case missing
    case failed(String)

    var errorDescription: String? {
        switch self {
        case .missing: "LeafBoard 凭证助手缺失"
        case let .failed(message): message.isEmpty ? "LeafBoard 凭证助手执行失败" : message
        }
    }
}

struct CredentialHelperClient {
    func readPassword() throws -> String? {
        let result = try run(operation: "read")
        if result.status == 3 { return nil }
        guard result.status == 0 else { throw CredentialHelperError.failed(result.error) }
        return String(data: result.output, encoding: .utf8)
    }

    func savePassword(_ password: String) throws {
        let result = try run(operation: "save", input: Data(password.utf8))
        guard result.status == 0 else { throw CredentialHelperError.failed(result.error) }
    }

    private func run(operation: String, input: Data? = nil) throws -> (status: Int32, output: Data, error: String) {
        let helper = Bundle.main.bundleURL.appendingPathComponent("Contents/Helpers/LeafBoardCredentialHelper")
        guard FileManager.default.isExecutableFile(atPath: helper.path) else { throw CredentialHelperError.missing }

        let process = Process()
        let output = Pipe()
        let errors = Pipe()
        process.executableURL = helper
        process.arguments = [operation]
        process.standardOutput = output
        process.standardError = errors
        if let input {
            let source = Pipe()
            process.standardInput = source
            try process.run()
            source.fileHandleForWriting.write(input)
            try source.fileHandleForWriting.close()
        } else {
            try process.run()
        }
        process.waitUntilExit()
        return (
            process.terminationStatus,
            output.fileHandleForReading.readDataToEndOfFile(),
            String(decoding: errors.fileHandleForReading.readDataToEndOfFile(), as: UTF8.self)
        )
    }
}
