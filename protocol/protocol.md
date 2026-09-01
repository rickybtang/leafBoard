# LeafBoard Protocol 1.0

## 1. 规范文件

- `schemas/card.schema.json`
- `schemas/producer-catalog.schema.json`
- `schemas/layout.schema.json`

均使用 JSON Schema Draft 2020-12。

## 2. 标识

- `producerId` 和 `cardId` 仅允许小写字母、数字、点、下划线和短横线。
- 完整卡片引用为 `{producerId}/{cardId}`。
- 每张卡片只能由一个 Producer 写入。
- `revision` 是 Producer 范围或 Card 范围内严格递增的非负整数；时间戳不得用于并发判定。

## 3. 卡片类型

- `metric`：额度、余额、计数、金额等字段型卡片。
- `list`：待办、日程、提醒等有序列表。
- `status`：快递、服务、构建等当前状态卡片。

`small`、`medium`、`large` 的顺序固定。字段的 `minSize=small` 表示三种尺寸均显示；`medium` 表示中、大显示；`large` 表示仅大卡片显示。

`metric` 和 `status` 必须且只能包含一个 `role=primary` 字段。Reader 将它渲染为“大字报”主信息；其余字段按协议顺序作为详情。`list` 使用独立的列表布局，不适用该字段约束。

### 3.1 大字报单行合同

- 大字报在小、中、大卡片中均为单行，不换行。中卡片左侧固定为主信息区，换行会破坏右侧详情的垂直对齐，因此不能用自动换行兜底。
- `primary.label` 最多 8 个 Unicode 字符。小卡片隐藏该标签，中、大卡片显示。
- `primary.format=text` 时，`primary.value` 必须为 1～12 个 Unicode 字符。Producer 必须提供用户能理解的短名称，不得直接发布不可控长度的内部 ID。
- 数字、百分比、金额和带单位数字仍保存原始类型。Reader 以 64px 为目标字号、52px 为最小字号做单行缩放；缩到下限仍放不下表示输入不符合展示合同，不能依赖省略号掩盖。
- 动态文本、第三方名称或单位长度没有稳定上界时，接入前必须拿真实最大样本与用户确认短名称和最坏情况；没有确认前不得把该字段设为 `primary`。

### 3.2 大卡片双值详情

大卡片允许详情字段使用可选的 `secondary`，表达“一个字段名 + 两个值”。它不是拼接文本，而是三个独立布局槽位：字段名、第一值、第二值。

```json
{
  "key": "top-model-today",
  "label": "今日最多",
  "value": "模型 A",
  "format": "text",
  "role": "detail",
  "minSize": "large",
  "secondary": {
    "value": 1250000,
    "format": "number",
    "unit": "token"
  }
}
```

约束如下：

- 只能用于 `role=detail` 且 `minSize=large` 的字段；中、小卡片不渲染双值行。
- `label` 最多 8 字符；第一值可以是 1～12 字符的 `text`，也可以是 `number`、`percent`、`money`、`duration` 数值。文本适用于模型等短名称；数值必须保持原始类型。
- `secondary.value` 必须为数字，`secondary.format` 仅允许 `number`、`percent`、`money`、`duration`；金额继续使用 ISO 4217 币种。
- Reader 使用固定的约 30% / 42% / 28% 三段布局，三段分别测量和缩放，禁止互相覆盖，也不允许任一值换行。
- Producer 负责把内部 ID 转为稳定、可读的短显示名，例如把较长内部名称映射为 `模型 A`。Producer 不得把两个值拼成 `"模型 A · 1.25M token"`；数值必须保持类型，由 Reader 统一压缩为 `K/M/B`、本地化金额和单位。
- 对无法预知长度的模型名或第三方名称，Producer 必须维护明确的短名称映射；未知名称先拒绝发布并请求用户确认，不得临时截断后发布。

V1 四列网格中，`small`、`medium`、`large` 分别占用 1×1、2×1、2×2，面积比例为 1:2:4。Producer 通过 `preferredSize` 提供建议，Reader 的本地布局选择优先。

## 4. 格式

`format` 只描述语义，保留原始值：`text`、`number`、`percent`、`money`、`datetime`、`duration`、`boolean`。`duration` 使用非负秒数并将 `unit` 设为 `s`，Reader 负责显示为本地化的时、分、秒，不得由 Producer 预先拼成文本。

时间使用带时区偏移的 ISO 8601 字符串。金额必须同时提供 ISO 4217 三字母币种到 `unit`，例如 `CNY`。Renderer 负责把时间和币种本地化，不得要求 Producer 写入“元”或已经格式化的时间文本。

`presentation.status` 仅允许 `normal`、`warning`、`error`、`unknown`。来源请求失败时通常保留最后有效 Card，不得用伪造的零值或空 Card 覆盖；数据本身陈旧时可以使用 `warning`。

## 5. 生命周期

- `updatedAt` 表示生产者生成当前值的时间。
- `expiresAt` 表示超过该时间后 UI 必须标记为过期，但仍可展示最后有效值。
- Catalog 是 Producer 当前卡片集合的权威清单。
- Catalog 中每个条目记录 `cardId`、相对路径、Card revision 和小写 SHA-256。
- 只有成功读取更高 revision 的 Catalog 后，Reader 才能移除 Catalog 中消失的缓存卡片。
- Catalog 请求、Card 下载、哈希或 Schema 校验失败时，Reader 必须保留最后有效缓存。

## 6. 扩展与兼容

- `schemaVersion=1.0` 是 V1 唯一支持的版本。
- 未识别的主版本必须拒绝渲染并显示升级提示。
- 平台特定信息只能放入 `extensions`，键使用反向域名或稳定命名空间。
- 不允许 HTML、CSS、JavaScript 或可执行内容。

## 7. WebDAV

```text
/leafboard/v1/producers/{producerId}/catalog.json
/leafboard/v1/producers/{producerId}/cards/{cardId}.json
```

推荐写入顺序：发布端完成 Schema 校验；写入或替换 Card；最后写入 Catalog；Reader 下载后再次校验，失败时保留旧缓存。

## 8. 布局

- 布局默认只保存在设备本地。
- Producer 的 `preferredSize` 是建议，设备选择优先。
- V1 使用四列网格和分页。
- 相同 `cardRef` 在一个 Layout 中只能出现一次。
- Layout JSON 的 `order` 从 0 开始；面向用户的设置界面从 1 开始显示并写回为内部值。
- Reader 按 `order` 升序处理卡片，再从左到右、从上到下寻找能容纳当前尺寸的位置。
- 夜间时段跨越午夜时，例如 `23:00` 到 `07:00`，按两个自然日区间解释。
