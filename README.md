# LeafBoard

LeafBoard 把闲置的 BOOX Leaf2 变成常驻电子墨水信息屏。它提供一套通用 Card JSON 协议、一个 macOS 发布 Hub 和一个 Leaf2 Android Reader。

公共仓库不内置任何人的真实数据源或维护者个人适配器。每个用户根据自己的需要实现 Producer，生成协议 Card 后交给 Hub 发布。

项目采用 MIT License。仓库不包含真实账号、密码、令牌、卡片缓存或企业内部接口。

## 组成

```text
用户自己的 Producer
API / CLI / 本地数据库 / 文件
              |
              v
       LeafBoard Card JSON
              |
              v
      LeafBoard Hub (macOS)
      校验 -> 缓存 -> WebDAV 发布
              |
              v
         坚果云 WebDAV
              |
              v
       LeafBoard (BOOX Leaf2)
       同步 -> 缓存 -> 本地布局
```

- `protocol/`：Card、Producer Catalog 和设备布局协议。
- `macos/`：接收合规 Card JSON并发布到 WebDAV，不读取任何预设业务数据源。
- `android/`：同步已配置 Producer 的全部 Catalog Card，并在设备本地管理显示、尺寸和顺序。
- `.agents/skills/leafboard-card-init/`：帮助 AI 从已确认的数据源设计新卡片。

## 准备坚果云 WebDAV

1. 注册并登录[坚果云](https://www.jianguoyun.com/)。
2. 打开“账户信息” → “安全选项” → “第三方应用管理”。
3. 添加名为 `LeafBoard` 的应用密码。
4. 不要使用坚果云登录密码。

LeafBoard 两端使用相同配置：

```text
WebDAV 根地址：https://dav.jianguoyun.com/dav/leafboard
用户名：坚果云注册邮箱
密码：LeafBoard 专用第三方应用密码
```

坚果云官方步骤见[第三方应用授权 WebDAV 开启方法](https://help.jianguoyun.com/?p=2064)。应用密码只能输入 LeafBoard 本地设置页，不要把它粘贴到 AI 对话、Issue、日志、截图或命令中。

## 本地构建 Mac Hub

环境：macOS 14 或更高版本，以及包含 Swift 6 的 Xcode。

```bash
cd macos
scripts/setup-local-signing.sh
scripts/package-app.sh
```

输出：

```text
macos/dist/LeafBoard Hub.app
```

`setup-local-signing.sh` 在当前 Mac 的登录钥匙串中创建一次固定本地签名身份。以后只需重复运行 `package-app.sh`，主程序和凭证助手会继续使用同一身份，避免重建后反复请求 Keychain 授权。

打开 Hub 后，在“设置”中填写 WebDAV 配置。首次配置必须输入应用密码；保存成功后，后续修改其他设置时可以把密码留空。Keychain 授权最多出现一次，也可能因为凭证由同一个固定助手创建和读取而不弹窗；后续重新打包应继续复用该访问权限。

## Codex Card 格式示例

下面只是一个使用虚构数值的 Card JSON 示例，用来说明“某个 Producer 读取 Codex 后可以生成什么”。LeafBoard 不内置 Codex 读取器，也不会自动读取任何业务应用。

```json
{
  "schemaVersion": "1.0",
  "producerId": "example-producer",
  "cardId": "codex-usage",
  "revision": 1,
  "type": "metric",
  "updatedAt": "2026-09-01T12:00:00+08:00",
  "expiresAt": "2026-09-01T14:00:00+08:00",
  "content": {
    "title": "Codex 额度（示例）",
    "fields": [
      {
        "key": "five-hour-remaining",
        "label": "短窗口剩余",
        "value": 78,
        "format": "percent",
        "role": "primary",
        "minSize": "small"
      },
      {
        "key": "weekly-remaining",
        "label": "周剩余",
        "value": 43,
        "format": "percent",
        "role": "detail",
        "minSize": "small"
      },
      {
        "key": "weekly-reset",
        "label": "周重置",
        "value": "2026-09-05T08:00:00+08:00",
        "format": "datetime",
        "role": "detail",
        "minSize": "small"
      },
      {
        "key": "last-refresh",
        "label": "采集时间",
        "value": "2026-09-01T12:00:00+08:00",
        "format": "datetime",
        "role": "detail",
        "minSize": "small"
      }
    ]
  },
  "presentation": {
    "icon": "quota",
    "preferredSize": "medium",
    "allowedSizes": ["small", "medium", "large"],
    "status": "normal"
  }
}
```

仓库中的可校验文件见 [`protocol/examples/quota-card.json`](protocol/examples/quota-card.json)。真正的 Producer 必须自行解决数据读取、授权、revision 递增和失败时保留最后有效 Card。

## 把 Card 交给 Hub

Hub 只监听本机 `127.0.0.1:8766`。把 Card 保存为 `card.json` 后，可以提交到本机 API：

```bash
curl --fail-with-body \
  -H 'Content-Type: application/json' \
  --data-binary @card.json \
  http://127.0.0.1:8766/api/cards
```

Hub 会先按协议校验，再写入本地缓存。随后在菜单栏点击“发布已保存卡片”，或等待配置的发布间隔。Hub 按 `producerId` 生成 Catalog，并在最后写入 Catalog。

## 安装 Leaf2 App

环境：Android SDK、ADB、JDK 17 或更高版本。macOS 可使用 Android Studio 自带 JBR：

```bash
cd android
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Android 应用 ID：`io.github.rickybtang.leafboard`。

安装后：

1. 启动 LeafBoard，从右边缘向左滑进入设置。
2. 填写 WebDAV 根地址、账号和应用密码。
3. 在“数据来源 ID”中填写你自己的 `producerId`，例如示例中的 `example-producer`。
4. 保存并返回看板完成第一次同步。
5. 再次打开设置；“卡片布局”会列出该 Producer Catalog 中全部有效 Card。
6. 在设备本地控制启用状态、大小和顺序。

Producer 不能远程覆盖 Leaf2 的本地布局。同步、Catalog、Card 或哈希校验失败时，Reader 保留最后有效缓存。

## 用 AI 设计自己的卡片

先让 AI 读取项目 Skill：

```text
请先读取 .agents/skills/leafboard-card-init/SKILL.md，
按照来源确认和卡片初始化流程，帮我接入 <数据源名称>。
```

Skill 会先确认稳定、非浏览器的结构化读取路径，再给出小、中、大三种布局和字段合同。没有稳定读取路径时不会伪造“接入完成”。

## 安全边界

- 不读取浏览器 Cookie、密码库、完整 Authorization Header 或不受控网页 DOM。
- 数据源凭证由用户自己的 Producer 安全保存，不进入 Card、Catalog 或 Hub 日志。
- WebDAV 密码由 macOS Keychain 和 Android Keystore 本地保护。
- 协议示例只能作为示例，不得推到真实 WebDAV 或长期使用的 Leaf2。
- LeafBoard 不 Root、不解锁 Bootloader、不替换 BOOX 系统桌面，也不修改系统分区。

## 验证层级

- Schema 有效/无效示例验证。
- macOS `swift test`、release 构建、本地签名验证。
- Android 单元测试和 `assembleDebug`。
- ADB 安装、前台、手势、布局和保存实机验证。
- 自定义 Producer → Card → Hub → Catalog → WebDAV → Leaf2 真实链路验证。
- 用户验收。

编译成功不等于实机或真实云端链路验收。

## 文档

- [文档入口与权威顺序](docs/README.md)
- [产品需求](docs/requirements.md)
- [系统架构](docs/architecture.md)
- [协议规范](protocol/protocol.md)
- [新数据源与卡片接入指南](docs/integration-guide.md)
- [安全、凭证与本地构建规范](docs/security-release.md)

## License

[MIT](LICENSE)
