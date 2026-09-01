# LeafBoard 项目背景与当前状态

更新日期：2026-09-01。

## 1. 项目目标

LeafBoard 把淘汰的 BOOX Leaf2 变成专用电子墨水信息屏。用户自己的 Producer 读取所需数据并生成统一 Card JSON；macOS Hub 负责校验、缓存和发布；Leaf2 负责同步、最后有效缓存、本地布局和显示。

产品强调低侵入：不修改 BOOX 系统，不 Root，不替换桌面。用户可退出 App 回到原系统，卸载后无需恢复系统设置。

## 2. 公共链路

```text
用户自己的数据源
       |
       v
自定义 Producer -> Card JSON -> LeafBoard Hub
                                  |
                                  v
                         坚果云 WebDAV
                                  |
                                  v
                           LeafBoard Android
```

公共仓库不内置维护者当前使用的任何真实数据源。Codex 只在 README 和协议示例中以虚构数值展示 Card 格式，不包含读取实现。

## 3. 已实现能力

### Leaf2 Android App

- Android 11/API 30，针对 Leaf2 1264×1680 电子墨水屏。
- 全屏、常亮、前台运行；夜间时段窗口亮度降为 0。
- 右边缘向左滑进入设置；设置页左边缘向右滑返回。
- 四列分页网格；小、中、大卡片分别为 1×1、2×1、2×2。
- 设置页布局清单来自已配置 Producer 的最后有效 Catalog；卡片名称不写死在 Android 代码中。
- 是否显示、尺寸和顺序只保存在设备本地。
- WebDAV 增量同步、最后有效缓存、过期标记、本地时区显示和开发用 USB inbox 已实现。

### macOS LeafBoard Hub

- SwiftUI 菜单栏应用。
- `POST /api/cards` 只在 `127.0.0.1:8766` 接收 Card JSON。
- Card 模型校验、本地存储、Catalog 生成和 WebDAV 发布已实现。
- 本机管理页只展示已保存 Card 摘要和协议诊断，不包含数据源账号配置。
- WebDAV 密码仅由固定签名的 `LeafBoardCredentialHelper` 从 Keychain 读取。
- 启动时只加载本地 Card；首次自动发布在一个完整配置间隔后执行。

### 协议和 Skill

- 当前唯一协议版本为 `schemaVersion: "1.0"`。
- 三份 JSON Schema、有效/无效示例、Swift 校验器和 Android Parser 保持一致。
- `.agents/skills/leafboard-card-init/SKILL.md` 用于从确认的数据源设计 Card，不代表仓库内置该数据源。

## 4. 关键产品决定

- 公共项目只提供通用协议、Hub、Reader 和 Skill，不提供维护者个人 Producer。
- Hub 接收 Card，不读取业务来源；Producer 的来源授权和失败语义由实现者负责。
- Leaf2 使用拉取：按间隔检查已配置 Producer 的 Catalog，只有变化时下载 Card。
- 布局只保存在设备本地；Producer 只能提供 `preferredSize`。
- WebDAV、Catalog 或 Card 失败时保留最后有效缓存。
- 示例只使用虚构值，并明确标注“示例”。

## 5. 当前环境事实

- 工作区使用 Git 管理；操作前检查当前分支、状态和精确变更范围。
- ADB 设备序列号属于运行时环境事实，不写入代码或公共文档。
- Android APK：`android/app/build/outputs/apk/debug/app-debug.apk`。
- macOS App：`macos/dist/LeafBoard Hub.app`。
- `server.py` 与 `web/` 是早期 USB 诊断工具，不属于正式运行架构。

## 6. 仍需完成的本地验收

- 新公开命名空间下重新完成首次 Keychain 授权和连续两次重新打包验证。
- 安装新 Android 包名后重新验证 Leaf2 前台、手势、Catalog 布局清单和保存。
- 使用一个用户自建 Producer 完成 Card → Hub → Catalog → WebDAV → Leaf2 的真实链路。

本文件不记录易过期的额度、登录态或设备连接结果；这些事实必须在当前任务中实时确认。
