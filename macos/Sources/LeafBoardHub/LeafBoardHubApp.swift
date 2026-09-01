import AppKit
import LeafBoardCore
import SwiftUI

@main
struct LeafBoardHubApp: App {
    @StateObject private var model = HubModel()

    var body: some Scene {
        MenuBarExtra("LeafBoard", systemImage: "rectangle.grid.2x2") {
            VStack(alignment: .leading, spacing: 10) {
                Text(model.status)
                    .font(.headline)
                    .frame(maxWidth: 320, alignment: .leading)
                if let date = model.lastUpdated {
                    Text("更新：\(date.formatted(date: .omitted, time: .standard))")
                        .foregroundStyle(.secondary)
                }
                Divider()
                Button(model.isPublishing ? "正在发布…" : "发布已保存卡片") {
                    Task { await model.publishStoredCards() }
                }
                .disabled(model.isPublishing)
                Button("打开本机管理页") { model.openAdminPage() }
                SettingsLink { Text("设置") }
                    .simultaneousGesture(TapGesture().onEnded {
                        SettingsWindowPresenter.bringToFront()
                    })
                Divider()
                Button("退出 LeafBoard Hub") { NSApplication.shared.terminate(nil) }
            }
            .padding(12)
        }

        WindowGroup("LeafBoard Hub") {
            DashboardWindow(model: model)
                .frame(minWidth: 680, minHeight: 460)
        }

        Settings {
            SettingsView(model: model)
                .frame(width: 560, height: 390)
                .background(SettingsWindowRegistrar())
        }
    }
}

private enum SettingsWindowPresenter {
    static let identifier = NSUserInterfaceItemIdentifier("LeafBoardHubSettings")

    static func bringToFront() {
        DispatchQueue.main.async {
            guard let window = NSApp.windows.first(where: { $0.identifier == identifier }) else { return }
            NSApp.activate(ignoringOtherApps: true)
            window.makeKeyAndOrderFront(nil)
        }
    }
}

private struct SettingsWindowRegistrar: NSViewRepresentable {
    func makeNSView(context: Context) -> NSView { SettingsWindowView() }
    func updateNSView(_ nsView: NSView, context: Context) {}

    private final class SettingsWindowView: NSView {
        override func viewDidMoveToWindow() {
            super.viewDidMoveToWindow()
            guard let window else { return }
            window.identifier = SettingsWindowPresenter.identifier
            SettingsWindowPresenter.bringToFront()
        }
    }
}

private struct DashboardWindow: View {
    @ObservedObject var model: HubModel

    var body: some View {
        VStack(spacing: 16) {
            HStack(alignment: .firstTextBaseline) {
                VStack(alignment: .leading, spacing: 4) {
                    Text("LeafBoard Hub").font(.largeTitle.bold())
                    Text(model.status).foregroundStyle(.secondary)
                }
                Spacer()
                Button("本机管理页") { model.openAdminPage() }
                Button(model.isPublishing ? "正在发布…" : "发布已保存卡片") {
                    Task { await model.publishStoredCards() }
                }
                .disabled(model.isPublishing)
            }
            CardsView(model: model)
        }
        .padding(22)
    }
}

private struct CardsView: View {
    @ObservedObject var model: HubModel
    private let columns = [GridItem(.adaptive(minimum: 250), spacing: 12)]

    var body: some View {
        ScrollView {
            if model.cards.isEmpty {
                ContentUnavailableView(
                    "尚未写入卡片",
                    systemImage: "rectangle.grid.2x2",
                    description: Text("通过本机 API 写入符合协议的 Card JSON。")
                )
                .padding(.top, 80)
            } else {
                LazyVGrid(columns: columns, spacing: 12) {
                    ForEach(model.cards) { card in CardOverviewTile(card: card) }
                }
                .padding(.vertical, 4)
            }
        }
    }
}

private struct CardOverviewTile: View {
    let card: LeafBoardCard

    var body: some View {
        let primary = card.content.fields?.first(where: { $0.role == "primary" })
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text(card.content.title).font(.headline)
                Spacer()
                Text(card.presentation.status).font(.caption).foregroundStyle(.secondary)
            }
            Text(primary.map { cardValue($0.value) } ?? card.content.state ?? "—")
                .font(.system(size: 28, weight: .bold, design: .rounded))
                .lineLimit(1)
                .minimumScaleFactor(0.7)
            Text(primary?.label ?? card.type.rawValue).font(.subheadline).foregroundStyle(.secondary)
            Divider()
            Text("\(card.id) · r\(card.revision)").font(.caption).foregroundStyle(.secondary)
            Text("建议尺寸：\(card.presentation.preferredSize.rawValue)").font(.caption).foregroundStyle(.secondary)
        }
        .padding(15)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(.background, in: RoundedRectangle(cornerRadius: 12))
        .overlay(RoundedRectangle(cornerRadius: 12).stroke(.quaternary))
    }

    private func cardValue(_ value: CardValue) -> String {
        switch value {
        case let .string(text): text
        case let .number(number): number.formatted(.number.precision(.fractionLength(0...2)))
        case let .boolean(value): value ? "是" : "否"
        case .null: "—"
        }
    }
}

private struct SettingsView: View {
    @ObservedObject var model: HubModel
    @State private var password = ""
    @State private var message = ""
    private let intervals = [1, 3, 5, 10, 15, 30, 60]

    var body: some View {
        Form {
            TextField("WebDAV 根地址", text: $model.webDAVURL)
            TextField("坚果云账号", text: $model.webDAVUsername)
            SecureField("应用密码（首次必填；以后留空保持不变）", text: $password)
            Picker("发布已保存卡片的间隔", selection: $model.intervalMinutes) {
                ForEach(intervals, id: \.self) { Text("\($0) 分钟").tag($0) }
            }
            Text("密码只写入 macOS Keychain，不进入 JSON、日志或仓库。")
                .font(.caption)
                .foregroundStyle(.secondary)
            HStack {
                Button("保存") {
                    do {
                        try model.saveSettings(password: password)
                        password = ""
                        message = "已保存"
                    } catch {
                        message = error.localizedDescription
                    }
                }
                Button("打开管理页") { model.openAdminPage() }
                Spacer()
                Text(message).foregroundStyle(message == "已保存" ? .green : .red)
            }
        }
        .padding(24)
    }
}
