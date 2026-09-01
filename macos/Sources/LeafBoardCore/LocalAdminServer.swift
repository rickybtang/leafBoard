import Foundation
import Network

private actor LocalServerStatus {
    private var value = "正在启动"
    func set(_ newValue: String) { value = newValue }
    func get() -> String { value }
}

private struct LocalAPIError: Encodable {
    let ok = false
    let error: String
}

public final class LocalAdminServer: @unchecked Sendable {
    public let port: UInt16
    private let store: LocalCardStore
    private let queue = DispatchQueue(label: "LeafBoard.LocalAdminServer")
    private let statusStore = LocalServerStatus()
    private var listener: NWListener?

    public init(store: LocalCardStore, port: UInt16 = 8766) {
        self.store = store
        self.port = port
    }

    public func start() throws {
        guard listener == nil else { return }
        let parameters = NWParameters.tcp
        parameters.requiredLocalEndpoint = .hostPort(host: "127.0.0.1", port: NWEndpoint.Port(rawValue: port)!)
        let listener = try NWListener(using: parameters)
        listener.newConnectionHandler = { [weak self] connection in self?.handle(connection) }
        listener.stateUpdateHandler = { state in
            if case let .failed(error) = state { fputs("LeafBoard local server failed: \(error)\n", stderr) }
        }
        listener.start(queue: queue)
        self.listener = listener
    }

    public func stop() {
        listener?.cancel()
        listener = nil
    }

    public func setStatus(_ value: String) {
        Task { await statusStore.set(value) }
    }

    private func handle(_ connection: NWConnection) {
        connection.start(queue: queue)
        receive(connection, buffer: Data())
    }

    private func receive(_ connection: NWConnection, buffer: Data) {
        connection.receive(minimumIncompleteLength: 1, maximumLength: 1_048_576) { [weak self] data, _, complete, error in
            guard let self else { return }
            var next = buffer
            if let data { next.append(data) }
            if self.requestIsComplete(next) || complete || error != nil {
                Task {
                    let response = await self.process(next)
                    connection.send(content: response, completion: .contentProcessed { _ in connection.cancel() })
                }
            } else {
                self.receive(connection, buffer: next)
            }
        }
    }

    private func requestIsComplete(_ data: Data) -> Bool {
        guard let separator = data.range(of: Data("\r\n\r\n".utf8)) else { return false }
        let header = String(decoding: data[..<separator.lowerBound], as: UTF8.self)
        let length = header
            .split(separator: "\r\n")
            .first { $0.lowercased().hasPrefix("content-length:") }
            .flatMap { Int($0.split(separator: ":", maxSplits: 1)[1].trimmingCharacters(in: .whitespaces)) } ?? 0
        return data.count >= separator.upperBound + length
    }

    private func process(_ request: Data) async -> Data {
        guard let separator = request.range(of: Data("\r\n\r\n".utf8)) else {
            return response(status: 400, contentType: "text/plain; charset=utf-8", body: Data("Bad Request".utf8))
        }
        let header = String(decoding: request[..<separator.lowerBound], as: UTF8.self)
        let firstLine = header.split(separator: "\r\n").first?.split(separator: " ") ?? []
        guard firstLine.count >= 2 else {
            return response(status: 400, contentType: "text/plain; charset=utf-8", body: Data("Bad Request".utf8))
        }
        let method = String(firstLine[0])
        let path = String(firstLine[1]).split(separator: "?").first.map(String.init) ?? "/"
        let body = Data(request[separator.upperBound...])
        if method == "POST",
           let originLine = header.split(separator: "\r\n").first(where: { $0.lowercased().hasPrefix("origin:") }) {
            let origin = originLine.split(separator: ":", maxSplits: 1)[1].trimmingCharacters(in: .whitespaces)
            let allowed = origin == "http://127.0.0.1:\(port)" || origin == "http://localhost:\(port)"
            if !allowed {
                return response(status: 403, contentType: "text/plain; charset=utf-8", body: Data("Forbidden".utf8))
            }
        }

        do {
            switch (method, path) {
            case ("GET", "/"):
                return response(status: 200, contentType: "text/html; charset=utf-8", body: Data(Self.adminPage.utf8))
            case ("GET", "/api/cards"):
                return response(status: 200, contentType: "application/json; charset=utf-8", body: try await store.allCardsJSON())
            case ("GET", "/api/status"):
                let value = await statusStore.get()
                return response(status: 200, contentType: "application/json; charset=utf-8", body: try JSONEncoder().encode(["status": value]))
            case ("POST", "/api/cards"):
                let card = try await store.save(rawJSON: body)
                return response(
                    status: 201,
                    contentType: "application/json; charset=utf-8",
                    body: try JSONEncoder().encode(["cardRef": card.id])
                )
            default:
                return response(status: 404, contentType: "text/plain; charset=utf-8", body: Data("Not Found".utf8))
            }
        } catch {
            return response(
                status: 422,
                contentType: "application/json; charset=utf-8",
                body: (try? JSONEncoder().encode(LocalAPIError(error: error.localizedDescription))) ?? Data("{\"ok\":false}".utf8)
            )
        }
    }

    private func response(status: Int, contentType: String, body: Data) -> Data {
        let reason = status == 200 ? "OK" : status == 201 ? "Created" : status == 403 ? "Forbidden" : status == 404 ? "Not Found" : status == 422 ? "Unprocessable Content" : "Bad Request"
        let header = "HTTP/1.1 \(status) \(reason)\r\nContent-Type: \(contentType)\r\nContent-Length: \(body.count)\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n"
        return Data(header.utf8) + body
    }

    private static let adminPage = #"""
<!doctype html>
<html lang="zh-CN">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width,initial-scale=1">
  <title>LeafBoard Hub</title>
  <style>
    :root{font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif;color:#1a1a1a;background:#f4f3ef}body{max-width:1080px;margin:0 auto;padding:28px 24px 48px}header{display:flex;justify-content:space-between;gap:20px;align-items:start}h1{font-size:30px;margin:5px 0 6px}p{margin:0;color:#68655d}.status{max-width:46ch;text-align:right}.card-grid{display:grid;gap:14px;grid-template-columns:repeat(auto-fit,minmax(min(100%,260px),1fr));margin-top:26px}.tile{background:#fff;border:1px solid #dedcd4;border-radius:14px;padding:17px}.tile-head{display:flex;justify-content:space-between;gap:10px}.tile h2{font-size:19px;margin:0}.tag{font-size:12px;background:#eeece5;border-radius:99px;padding:4px 8px}.primary{font-size:27px;font-weight:700;margin:19px 0 6px}.details,.meta{font-size:13px;color:#706d65}.details{display:grid;gap:5px}.meta{margin-top:14px}.empty{margin-top:26px;background:#fff;border:1px dashed #c9c6bd;border-radius:14px;padding:34px;text-align:center;color:#69665e}details{margin-top:26px;border-top:1px solid #d7d5ce;padding-top:15px}pre{overflow:auto;max-height:420px;padding:14px;background:#262522;color:#edece7;border-radius:10px;font:12px ui-monospace,SFMono-Regular,monospace}@media(max-width:620px){header{display:block}.status{text-align:left;margin-top:12px}}
  </style>
</head>
<body>
  <header><div><small>LOCAL · 127.0.0.1</small><h1>LeafBoard Hub</h1><p>接收、校验、缓存并发布协议 Card。</p></div><div class="status" id="status">正在读取状态…</div></header>
  <main><div class="card-grid" id="card-grid"></div></main>
  <details><summary>协议诊断（原始 JSON）</summary><pre id="raw">未加载</pre></details>
  <script>
    const escape=value=>String(value??'').replace(/[&<>"']/g,char=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[char]));
    const format=value=>typeof value==='number'?new Intl.NumberFormat('zh-CN',{maximumFractionDigits:2}).format(value):String(value??'—');
    const primary=card=>(card.content.fields||[]).find(field=>field.role==='primary');
    function render(cards){const root=document.querySelector('#card-grid');root.innerHTML=cards.length?cards.map(card=>{const field=primary(card);const details=(card.content.fields||[]).filter(item=>item.role!=='primary').slice(0,3).map(item=>`<span>${escape(item.label)} · ${escape(format(item.value))}</span>`).join('');return `<article class="tile"><div class="tile-head"><h2>${escape(card.content.title)}</h2><span class="tag">${escape(card.presentation.status)}</span></div><div class="primary">${field?escape(format(field.value)):'—'}</div><div class="details"><span>${field?escape(field.label):escape(card.type)}</span>${details}</div><p class="meta">${escape(card.producerId)}/${escape(card.cardId)} · r${escape(card.revision)}</p></article>`;}).join(''):'<div class="empty">尚未写入卡片。向 POST /api/cards 提交符合协议的 Card JSON。</div>';}
    async function load(){try{const [cards,status]=await Promise.all([fetch('/api/cards').then(response=>response.json()),fetch('/api/status').then(response=>response.json())]);render(cards);document.querySelector('#status').textContent=status.status;document.querySelector('#raw').textContent=JSON.stringify(cards,null,2);}catch(error){document.querySelector('#status').textContent='本机页面读取失败';document.querySelector('#raw').textContent=error.message;}}
    load();setInterval(load,15000);
  </script>
</body>
</html>
"""#
}
