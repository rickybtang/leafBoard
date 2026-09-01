import Foundation
import Security

private let service = "io.github.rickybtang.leafboard.hub.webdav"
private let account = "jianguoyun"

private func fail(_ status: OSStatus) -> Never {
    FileHandle.standardError.write(Data("Keychain 操作失败（\(status)）".utf8))
    exit(1)
}

guard let operation = CommandLine.arguments.dropFirst().first,
      operation == "read" || operation == "save" else { exit(2) }

let identity: [String: Any] = [
    kSecClass as String: kSecClassGenericPassword,
    kSecAttrService as String: service,
    kSecAttrAccount as String: account
]

if operation == "read" {
    var query = identity
    query[kSecReturnData as String] = true
    query[kSecMatchLimit as String] = kSecMatchLimitOne
    var result: CFTypeRef?
    let status = SecItemCopyMatching(query as CFDictionary, &result)
    if status == errSecItemNotFound { exit(3) }
    guard status == errSecSuccess, let data = result as? Data else { fail(status) }
    FileHandle.standardOutput.write(data)
} else {
    let password = FileHandle.standardInput.readDataToEndOfFile()
    guard !password.isEmpty else { exit(4) }
    let update = SecItemUpdate(identity as CFDictionary, [kSecValueData as String: password] as CFDictionary)
    if update == errSecSuccess { exit(0) }
    guard update == errSecItemNotFound else { fail(update) }
    var item = identity
    item[kSecValueData as String] = password
    let status = SecItemAdd(item as CFDictionary, nil)
    guard status == errSecSuccess else { fail(status) }
}
