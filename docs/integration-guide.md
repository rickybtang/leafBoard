# LeafBoard 新数据源与卡片接入指南

本指南用于把新的额度、待办、快递、日程或状态数据接入现有链路。协议事实以 `protocol/protocol.md` 和 Schema 为准。

## 1. 先回答五个问题

1. 数据从哪里读取？是否有结构化 API、本地数据库、CLI 或应用服务？
2. 谁是唯一写入者？确定稳定的 `producerId`。
3. 一条业务信息对应哪张卡？确定稳定的 `cardId`。
4. 卡片是 `metric`、`list` 还是 `status`？
5. 小、中、大分别必须显示什么？先定义字段包含关系，再写 UI。

如果真实接口、授权边界或字段含义尚未确认，只做只读调查，不生成假数据冒充接入完成。

## 2. 标识与所有权

- 标识格式：小写字母、数字、点、下划线、短横线，最长 64 字符。
- 完整引用：`{producerId}/{cardId}`。
- 一个 Card 只能有一个 Producer 写入。
- 多设备或多应用可以写同一 WebDAV，但必须使用不同 `producerId/cardId`，不得依赖“最后写入覆盖”。
- `revision` 在同一 Card 内严格递增；每次值或展示字段变化时递增。

示例：

```text
producerId: example-producer
cardId: example-metric
cardRef: example-producer/example-metric
```

## 3. 卡片尺寸与字段包含

| 大小 | 网格占用 | 面积 | 使用建议 |
|---|---:|---:|---|
| `small` | 1×1 | 1 | 标题 + 单行主信息 + 最多三组紧凑详情 |
| `medium` | 2×1 | 2 | 左侧单行主信息 + 右侧最多三组完整键值详情 |
| `large` | 2×2 | 4 | 单行主信息 + 五至六行详情，可包含大卡专用双值行 |

字段通过 `minSize` 控制：

- `small`：小、中、大都显示。
- `medium`：中、大显示。
- `large`：仅大显示。

不要分别定义三份互相独立的字段集合；使用一份字段列表和 `minSize` 才能保证包含关系。

## 4. 字段规范

- `key`：稳定机器标识；不要包含显示文案。
- `label`：面向用户，最多 24 字符。
- `value`：保留原始语义值，不预先拼接说明文案。
- `format`：仅允许 `text`、`number`、`percent`、`money`、`datetime`、`duration`、`boolean`。
- `role`：`primary`、`secondary`、`detail`、`badge`。
- 每个 `metric/status` 必须且只能有一个 `primary`。主标签最多 8 字符；文本主值最多 12 字符且不换行。
- 大卡双值详情使用结构化 `secondary`，不能由 Producer 把两个值拼成一个字符串。双值字段只能是 `large detail`，字段名最多 8 字符；第一值可以是最多 12 字符的文本，也可以是带明确格式的数字，第二值必须是带明确格式的数字。
- `datetime`：带时区 ISO 8601，例如 `2026-08-30T10:37:33Z`；Reader 转为本地时区。
- `percent`：存数值 `54`，不要存字符串 `"54%"`。
- `duration`：存非负秒数并使用 `unit=s`，由 Reader 本地化为时、分、秒。
- `money`：存数值并把 `unit` 设为 ISO 4217，例如 `CNY`；Reader 可显示为“元”。
- `updatedAt`：当前 Card 值的生产时间。
- `expiresAt`：过期标记时间；过期后仍展示最后有效值，但必须标记过期。

## 5. 展示状态

`presentation.status` 只能是：

- `normal`：数据正常。
- `warning`：数据陈旧、接近阈值或需要关注。
- `error`：业务值明确表示错误；采集失败本身通常不覆盖旧卡片。
- `unknown`：来源无法给出可靠状态。

来源请求失败时不要生成一张“0%”或空值卡覆盖旧值。保留最后有效卡，并在 Hub 状态中报告采集失败。

## 6. 自定义 Producer 接入步骤

公共仓库不要求把来源 Client 写进 Hub。推荐把 Producer 保持为独立脚本、CLI 或应用：

1. 通过稳定的结构化路径只读获取真实来源。
2. 把来源响应转换为 Protocol 1.0 Card JSON。
3. 在 Producer 本地保存每个 Card 的 revision；值或展示字段变化时严格递增。
4. 来源读取失败时不提交零值、空值或错误 Card，保留 Hub 中的最后有效 Card。
5. 把 JSON 提交到 Hub 本机 `POST http://127.0.0.1:8766/api/cards`。
6. Hub 校验并缓存 Card，再由统一 `WebDAVPublisher` 生成 Card 和 Catalog。
7. 为来源字段、聚合公式、空值和失败分支增加 Producer 自己的测试。

认证优先级：官方/本地结构化接口 > 已安装应用的只读本地会话 > 用户显式配置的 API 凭证 > 页面抓取。不得读取浏览器 Cookie 或把令牌写入日志。

Producer 若用 OAuth 或其他本机 SDK 建立 Access Token 会话，只能只读复用结构化会话并请求原服务；不得把浏览器 Cookie 或本地存储导出到 Producer。若结构化接口缺少某类明细，保留字段缺口，不以网页 DOM 抓取补齐。

动态名称接入前必须提交真实样本、已知最长值、短名称映射和未知值处理方式。若无法证明能在协议长度内稳定显示，先向用户确认；不得依赖 Reader 换行、覆盖或静默截断。

## 7. Producer 直接写 WebDAV

需要自行实现 Catalog 和哈希逻辑的 Producer 也可以不经过 Mac Hub，直接按协议写 WebDAV：

```text
/leafboard/v1/producers/{producerId}/cards/{cardId}.json
/leafboard/v1/producers/{producerId}/catalog.json
```

顺序必须是：

1. 生成 Card 并通过 Schema 校验。
2. 写入或替换 Card。
3. 计算 Card SHA-256。
4. 最后写入 revision 更高的 Catalog。

Reader 只把 Catalog 当作该 Producer 卡片集合的权威清单。不要先写 Catalog 再写 Card。

## 8. Leaf2 接入检查

- 在设置页 `数据来源 ID` 中包含新的 `producerId`。
- 第一次同步后，新卡片默认使用 `preferredSize` 并允许用户调整。
- 布局设置会自动列出该 Producer 的最后有效 Catalog 中全部 Card，不需要在 Android 代码中增加卡片名称。
- 顺序 UI 从 1 开始且不重复；内部存储仍可使用从 0 开始的排序值。
- 实机验证小、中、大字段包含关系以及 1264×1680 下的裁切。
- 验证数据不变时不重绘，断网时保留旧卡，时间按本地时区显示。
- 不把协议示例或测试 Card 留在真实 inbox、缓存或云端 Catalog 中。
- USB inbox 仅用于开发验证，不进入正常云端布局清单。

## 9. 完成定义

新数据源只有同时满足以下条件才算“已接入”：

- 真实来源读取成功，且授权方式明确。
- Card 与 Catalog 通过 Schema 和应用校验器。
- 单元测试覆盖关键字段和异常分支。
- Mac 本地卡片、WebDAV 发布、Leaf2 下载缓存均成功。
- Leaf2 实机显示正确，三种尺寸符合字段包含关系。
- 不输出凭证，不发布 mock，不因来源失败覆盖最后有效值。

只完成其中一段时，必须明确写成“Client 完成”“Card 生成通过”或“等待真实链路验收”，不得统称“接入完成”。
