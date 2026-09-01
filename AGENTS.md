# LeafBoard 工作区协作规范

本文件适用于整个 `boox-leaf2-reuse` 工作区。新对话、自动化任务和协作者开始工作前必须先阅读本文件。

## 1. 必读顺序

1. `docs/README.md`：文档入口与权威顺序。
2. `docs/project-context.md`：产品背景、已实现能力和当前状态。
3. 与任务相关的权威文档：
   - 产品或交互：`docs/requirements.md`
   - 架构或数据流：`docs/architecture.md`
   - 开发、构建、验证：`docs/development.md`
   - 卡片、Catalog、布局：`protocol/protocol.md` 和 `protocol/schemas/`
4. 新数据源接入前阅读 `docs/integration-guide.md`。
5. 凭证、签名和安全相关工作前阅读 `docs/security-release.md`。
6. 新卡片设计或接入前必须读取 `.agents/skills/leafboard-card-init/SKILL.md`；若当前 Agent 不自动发现项目 Skill，就直接读取该文件，不得跳过来源确认步骤。

若文档与代码不一致，先确认真实运行行为，再同时修正文档、Schema、Producer、Reader 和测试；不得只修其中一层。

## 2. 产品不可破坏边界

- Leaf2 是专用、常驻、常亮的电子墨水看板；正常情况下 App 始终在前台。
- 不 Root、不解锁 Bootloader、不替换系统桌面、不修改系统分区或全局屏幕超时。
- 用户必须能回到原 BOOX 系统；卸载 App 后无需恢复系统配置。
- 夜间模式只把窗口亮度降为 0，应用仍保持前台。
- 看板全屏；右边缘向左滑进入设置，设置页左边缘向右滑返回看板。
- 设置页优先级固定为：同步间隔、卡片布局、夜间暗屏、连接配置、USB 导入。
- 设置页必须为电子墨水优化：紧凑、少滚动、高对比、避免无意义动画；保存按钮固定在底部。

## 3. 协议硬约束

- 当前唯一支持的协议版本是 `schemaVersion: "1.0"`。
- `producerId/cardId` 是卡片唯一标识；每张卡片只能有一个写入者。
- `revision` 严格递增，用于变化判断；不得用时间戳代替 revision。
- 卡片类型仅有 `metric`、`list`、`status`。
- 卡片大小仅有 `small`、`medium`、`large`，占用 1×1、2×1、2×2 网格，面积比例 1:2:4。
- 字段用 `minSize` 表示最小可见尺寸，必须保持小卡片字段包含于中、大卡片的包含关系。
- `datetime` 使用带时区的 ISO 8601；Reader 转换为设备本地时区。
- `money.unit` 使用 ISO 4217 三字母币种，例如 `CNY`；本地化显示由 Reader 完成。
- `presentation.status` 仅允许 `normal`、`warning`、`error`、`unknown`。
- 卡片不得携带 HTML、CSS、JavaScript 或其他可执行内容。
- 布局是设备本地偏好，Producer 只能提供 `preferredSize`，不得远程覆盖用户布局。

任何协议字段变更都必须同时检查：`protocol.md`、三份 Schema、示例、Swift 模型/校验器、Android 解析/渲染和测试。

## 4. 数据源与失败语义

- 公共仓库不内置任何真实业务数据源或维护者个人适配器；Codex 只作为虚构数值的 Card 格式示例。
- 用户自己的 Producer 负责读取来源并生成 Card，最简单的接入方式是向 Hub 本机 `POST /api/cards` 提交合规 JSON。
- 新数据源优先复用本机应用的结构化接口或本地会话，不依赖浏览器 DOM 抓取。
- 不读取或输出浏览器 Cookie、密码、令牌、完整 Authorization Header。
- 单一来源失败时不提交空值或伪造 Card；Hub 和 Reader 保留最后有效数据。
- WebDAV、Catalog 或 Card 校验失败时保留 Reader 的最后有效缓存。
- 禁止把 mock 卡片推到真实 Leaf2 或 WebDAV。协议示例必须明确标注“示例”。
- 公共仓库不得包含企业内接口、内部域名、专属会话格式或任何仅适用于维护者环境的适配器。

## 5. 凭证与本地构建

- 坚果云应用密码不得进入仓库、JSON、日志、终端输出或命令历史。
- macOS WebDAV 密码只能由固定的 `LeafBoardCredentialHelper` 访问。
- Android WebDAV 密码由 Android Keystore 加密保存。
- 普通用户首次配置后，Keychain 授权最多出现一次；正常升级和重建不得重复询问。
- 首次源码构建先运行 `macos/scripts/setup-local-signing.sh`，以后使用同一固定本地签名身份打包。
- 不得发布、复制或共享本地签名私钥。

## 6. 工作方式

- 只实现当前需求所需的最短完整方案，不提前增加未确认的云平台、消息队列、后台保活或兼容层。
- 先做只读检查；涉及账号、权限、删除、签名或系统安全变更时明确边界。
- Git 操作前先用 `git rev-parse` 验证当前状态；若不是 Git 仓库，除非用户明确要求，不得初始化。不要自动创建或切换分支。
- ADB 设备序列号属于当前环境事实，不得硬编码进产品代码。
- 保留用户数据和无关文件。测试产物不得混入真实卡片目录。
- 页面或登录态验证需要浏览器时，只复用用户已经运行的 Chrome；不得启动、重启、替换或杀死 Chrome。

## 7. 最低验证要求

按改动范围执行，不能用编译成功代替真实链路验收：

```text
协议：Schema 校验有效/无效示例
macOS：swift test；release 打包；签名验证
Android：assembleDebug；ADB 覆盖安装；实机前台、手势、布局和保存验证
自定义 Producer：真实读取 -> Card -> Hub -> Catalog -> WebDAV -> Leaf2 缓存与页面
凭证：连续两次重新打包和重启，不再次弹 Keychain，发布仍成功
```

最终状态必须区分：代码完成、自动测试通过、实机验证通过、真实云端链路通过和用户验收通过。

## 8. 文档维护

- 新的长期产品决定写入 `requirements.md`。
- 新的组件职责或失败语义写入 `architecture.md`。
- 新的协议事实先改 Schema 和 `protocol.md`。
- 新的开发、构建或本地授权操作写入 `development.md` 或 `security-release.md`。
- 已实现能力和待办变化更新 `project-context.md`。
- 新对话交接模板只做导航，不复制秘密或易过期的额度数值。
