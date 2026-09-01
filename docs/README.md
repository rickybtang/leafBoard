# LeafBoard 文档入口

新对话先阅读工作区根目录的 `AGENTS.md`，再按本页选择所需文档。

## 权威顺序

| 层级 | 文档 | 负责内容 |
|---|---|---|
| 1 | `protocol/schemas/*.schema.json` | 机器可校验的 Card、Catalog、Layout 结构 |
| 2 | `protocol/protocol.md` | 协议语义、标识、生命周期、兼容与 WebDAV 规则 |
| 3 | `docs/requirements.md` | 产品目标、交互边界、非功能要求与验收标准 |
| 4 | `docs/architecture.md` | 组件职责、数据流、缓存、失败语义 |
| 5 | `docs/development.md` | 环境、构建、签名、安装和验证流程 |

下列文档用于快速理解和执行；发现冲突时以上述权威文档为准：

- [项目背景与当前状态](project-context.md)
- [新数据源与卡片接入指南](integration-guide.md)
- [安全、凭证与本地构建规范](security-release.md)
- [新对话交接模板](new-conversation-brief.md)

## 按任务选择

- 新卡片或新数据源：`project-context.md` → `integration-guide.md` → `protocol/protocol.md` → Schema。
- Leaf2 看板或设置交互：`requirements.md` → `architecture.md` → Android 代码。
- Mac Hub、WebDAV 或本地管理页：`architecture.md` → `development.md` → Mac 代码。
- 密码、Keychain 或本地签名：`security-release.md` → `development.md`。
- 仅了解当前交付状态：`project-context.md`。

## 一致性原则

- Schema 与 `protocol.md` 必须一致。
- Producer 生成的 JSON 必须同时通过 Schema 和 Swift `CardValidator`。
- Android Reader 不得接受 Producer 被禁止生成的结构。
- 快速上下文文档不得包含账号、密码、令牌、真实请求头或易过期的额度数字。
