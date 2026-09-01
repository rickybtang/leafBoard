import AppKit
import Foundation
import LeafBoardCore

@MainActor
final class HubModel: ObservableObject {
    @Published private(set) var cards: [LeafBoardCard] = []
    @Published private(set) var status = "正在启动"
    @Published private(set) var lastUpdated: Date?
    @Published private(set) var isPublishing = false

    @Published var webDAVURL: String
    @Published var webDAVUsername: String
    @Published var intervalMinutes: Int

    let adminURL = URL(string: "http://127.0.0.1:8766")!
    private let defaults = UserDefaults.standard
    private let store: LocalCardStore
    private let server: LocalAdminServer
    private let credentialHelper = CredentialHelperClient()
    private let publisher = WebDAVPublisher()
    private var scheduleTask: Task<Void, Never>?

    init() {
        webDAVURL = defaults.string(forKey: "webdav.url") ?? "https://dav.jianguoyun.com/dav/leafboard"
        webDAVUsername = defaults.string(forKey: "webdav.username") ?? ""
        intervalMinutes = defaults.object(forKey: "publish.interval") as? Int ?? 15
        store = try! LocalCardStore()
        server = LocalAdminServer(store: store)

        do {
            try server.start()
            updateStatus("本机管理页已启动")
        } catch {
            updateStatus("本机管理页启动失败：\(error.localizedDescription)")
        }
        Task { [weak self] in await self?.reloadLocalCards() }
        restartSchedule()
    }

    func restartSchedule() {
        scheduleTask?.cancel()
        scheduleTask = Task { [weak self] in
            guard let self else { return }
            while !Task.isCancelled {
                try? await Task.sleep(for: .seconds(Double(max(1, intervalMinutes) * 60)))
                guard !Task.isCancelled else { return }
                await publishStoredCards()
            }
        }
    }

    func publishStoredCards() async {
        guard !isPublishing else { return }
        isPublishing = true
        updateStatus("正在载入并校验本地卡片")
        defer {
            isPublishing = false
            server.setStatus(status)
        }

        do {
            cards = try await store.allCards()
            guard !cards.isEmpty else {
                updateStatus("尚无卡片；请先通过本机 API 写入合规 Card JSON")
                lastUpdated = .now
                return
            }
            guard let credentials = try credentials() else {
                updateStatus("已有 \(cards.count) 张本地卡片；尚未配置 WebDAV")
                lastUpdated = .now
                return
            }

            let groups = Dictionary(grouping: cards, by: \.producerId)
            for producerId in groups.keys.sorted() {
                guard let group = groups[producerId] else { continue }
                let key = "catalog.revision.\(producerId)"
                let revision = defaults.integer(forKey: key) + 1
                _ = try await publisher.publish(cards: group, catalogRevision: revision, credentials: credentials)
                defaults.set(revision, forKey: key)
            }
            updateStatus("已发布 \(cards.count) 张卡片")
            lastUpdated = .now
        } catch {
            updateStatus("发布失败：\(error.localizedDescription)")
        }
    }

    func reloadLocalCards() async {
        cards = (try? await store.allCards()) ?? []
    }

    func saveSettings(password: String) throws {
        guard let url = URL(string: webDAVURL), url.scheme == "https" else {
            throw CardValidationError.invalid("WebDAV 地址必须是 HTTPS")
        }
        defaults.set(webDAVURL.trimmingCharacters(in: .whitespacesAndNewlines), forKey: "webdav.url")
        defaults.set(webDAVUsername.trimmingCharacters(in: .whitespacesAndNewlines), forKey: "webdav.username")
        defaults.set(intervalMinutes, forKey: "publish.interval")
        if password.isEmpty {
            guard let savedPassword = try credentialHelper.readPassword(), !savedPassword.isEmpty else {
                throw CardValidationError.invalid("首次配置必须输入 WebDAV 应用密码")
            }
        } else {
            try credentialHelper.savePassword(password)
        }
        restartSchedule()
    }

    func openAdminPage() {
        NSWorkspace.shared.open(adminURL)
    }

    private func credentials() throws -> WebDAVCredentials? {
        guard let rootURL = URL(string: webDAVURL),
              !webDAVUsername.isEmpty,
              let password = try credentialHelper.readPassword(),
              !password.isEmpty else { return nil }
        return WebDAVCredentials(rootURL: rootURL, username: webDAVUsername, password: password)
    }

    private func updateStatus(_ value: String) {
        status = value
        server.setStatus(value)
    }
}
