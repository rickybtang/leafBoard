# LeafBoard 新对话交接模板

新开对话时，可把下面内容作为首条消息；具体需求写在模板末尾。

```text
我们正在继续开发当前打开的 LeafBoard 工作区（仓库根目录）。

开始工作前请依次完整阅读：
1. AGENTS.md
2. docs/README.md
3. docs/project-context.md
4. 与本次需求相关的 requirements / architecture / development / protocol 文档

必须遵守：
- Leaf2 是非侵入式、常驻前台、常亮的电子墨水看板。
- 使用 LeafBoard Protocol 1.0；协议变更同步修改 Schema、Producer、Reader、示例和测试。
- 不把真实账号、密码、令牌、Cookie、请求头或 mock 卡片写入仓库、日志、云端或设备。
- 不破坏 WebDAV 最后有效缓存、Keychain 一次授权和本地布局原则。
- 先确认当前代码与实机状态；不要把 docs/project-context.md 中可能变化的环境事实当作实时证据。
- Git 操作前先确认当前目录状态；不要自动初始化 Git，也不要自动创建或切换分支。

本次需求：
[在这里填写需求]

期望验收：
[在这里填写用户可观察结果；若未填写，请按 AGENTS.md 的最低验证要求执行]
```

## 更短版本

```text
请先读取本工作区 AGENTS.md 和 docs/README.md，再处理下面的 LeafBoard 需求。保持 Protocol 1.0、非侵入 Leaf2、电子墨水少滚动、真实数据不使用 mock、凭证不出日志、Keychain 只授权一次。完成后按改动范围做真实链路验收。

需求：[填写]
```

## 新对话应输出的第一份检查结果

开始实现前，新的对话应简短说明：

- 本次需求影响 Android、Mac、协议还是文档。
- 是否涉及真实账号、登录态、外部 API 或破坏性操作。
- 是否需要修改 Schema；如果不需要，明确说明复用哪些现有字段。
- 准备执行的最小验证范围。

不要在交接消息中粘贴坚果云账号、应用密码、自定义 Producer 令牌或任何真实业务数值。
