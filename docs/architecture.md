# LeafBoard 系统架构 V1

## 1. 组件

```text
用户自己的数据源
API / CLI / 数据库 / 文件
          |
          v
   自定义 Producer
          |
          v
 LeafBoard Card JSON ──> LeafBoard Hub (macOS)
                         ├─> 协议校验
                         ├─> 本地预览/缓存
                         └─> 坚果云 WebDAV
                                  |
                                  v
                           LeafBoard Android
                           - Catalog 增量同步
                           - 本地布局与缓存
                           - 常亮/夜间暗屏
                           - 分页电子墨水渲染
```

开发阶段另有一条 USB 通道：

```text
Mac 生成 JSON -> adb push -> Leaf2 导入目录 -> 同一协议解析器
```

## 2. 协议与存储解耦

业务层只认识 `Card`、`ProducerCatalog` 和 `DeviceLayout`。WebDAV、ADB 和本地文件均实现相同的存储接口。

```text
CardProducer -> ProtocolValidator -> CardStore
                                     |- WebDavCardStore
                                     |- LocalCardStore
                                     `- AdbImportStore
```

## 3. WebDAV 目录

```text
/leafboard/v1/producers/{producerId}/catalog.json
/leafboard/v1/producers/{producerId}/cards/{cardId}.json
```

Leaf2 按本地配置的 Producer ID 读取 Catalog；日常轮询只检查 Catalog。Catalog revision 未变化时停止，不获取卡片、不触发渲染。

设置页的布局清单只读取这些 Producer 的最后有效 Catalog，并再次核对 Card 的标识、revision 和 SHA-256。Card 是否展示、尺寸和顺序保存在设备本地；USB 开发导入不会进入正常云端布局清单。

发布顺序为：校验卡片、写卡片、最后写 Producer Catalog。条件写入和临时文件替换能力以坚果云实测结果为准；不支持时依靠单写入者约束和下载后校验保证安全。

## 4. Android 前台模型

- 主 Activity 使用 `FLAG_KEEP_SCREEN_ON`，仅在 Activity 可见时生效。
- V1 不启动后台 Service 或 Foreground Service。
- 前台协程根据本地设置每 1～60 分钟调用一次 `syncOnce()`。
- `onResume` 立即同步一次。
- 夜间时段不退出 Activity：窗口亮度设为 0，显示极简夜间页面；离开夜间时段恢复用户设置亮度。
- 系统 Home 能正常退出，App 不注册 Launcher/Home 角色。

## 5. 增量同步

```text
读取 Catalog
  |- 请求失败：保留本地缓存并展示最后更新时间
  |- revision 未变：结束
  `- revision 变化：
       |- 获取 revision/hash 变化的 Card
       |- Schema 校验
       |- 原子替换本地有效缓存
       |- 移除新版 Catalog 中已不存在的卡片
       `- 只重绘实际变化的卡片
```

## 6. macOS Hub

macOS 应用采用 SwiftUI 菜单栏形态：

- 通过仅本机 `POST /api/cards` 接收 Card JSON并执行协议校验。
- 保存 WebDAV 配置；固定的 `LeafBoardCredentialHelper` 独占 WebDAV Keychain 凭证访问，Hub 通过本机进程管道读取。
- 发布 Card 和 Catalog。
- 提供只监听 `127.0.0.1` 的本机管理页，展示已保存 Card 摘要和折叠的协议 JSON。
- 展示最近校验、发布状态和错误，不记录密码或完整 Authorization Header。
- 冷启动只异步加载本地有效 Card 并启动管理页，不读取 Keychain、不访问外部业务数据源、不立即发布；定时任务先等待一个完整配置间隔。手动“发布已保存卡片”不受影响。

公共 Hub 不知道 Card 来自哪个具体业务系统。来源失败和最后有效数据由自定义 Producer 处理；非法或空 Card 不得覆盖 Hub 已保存的有效 Card。

## 7. 失败语义

- Producer 读取失败：不覆盖上一份有效卡片，不得伪造额度。
- WebDAV 发布失败：保留待发布内容并显示失败，不自动高频重试。
- Card 校验失败：拒绝发布；Leaf2 拒绝替换本地有效缓存。
- Catalog 获取失败：不得据此删除本地卡片。
- 不支持的协议主版本：显示明确升级提示，不白屏。
