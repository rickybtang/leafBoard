# LeafBoard 安全、凭证与本地构建规范

## 1. 凭证边界

- 坚果云 URL 和账号可保存为普通本地偏好；应用密码属于秘密。
- WebDAV 应用密码和自定义 Producer 的来源凭证不得进入仓库、Card、Catalog、日志、截图、终端输出、命令参数或测试数据。
- macOS 只有固定签名的 `LeafBoardCredentialHelper` 可以读写 WebDAV Keychain 项；Hub 仅通过本机进程管道使用密码。
- Android 使用 Android Keystore 加密本地密码。
- 自定义 Producer 自己负责来源凭证，采用最小只读权限；令牌不得进入 Card、Catalog 或 Hub 日志。
- 不检查浏览器 Cookie、密码库或本地存储来绕过登录。

## 2. 一次授权要求

普通用户的目标体验：首次保存或迁移 WebDAV 凭证时最多授权一次，之后正常升级、重启和主程序重建不再询问。

实现规则：

- Keychain 访问由独立、固定标识 `io.github.rickybtang.leafboard.credential-helper` 执行。
- 凭证助手使用稳定签名；主程序代码变化不得改变助手标识和签名身份。
- 不得把 Keychain 读取重新移回频繁变化的 Hub 主程序。
- 若确实需要升级凭证助手，必须作为显式安全迁移说明可能出现的一次额外授权，不能伪装成普通更新。

公开命名空间迁移后必须重新验收：首次授权凭证助手后连续重新打包并重启 Hub 两次，助手 CDHash 保持不变，均不得再次授权，且 WebDAV 发布成功。

## 3. 源码构建签名

首次在一台 Mac 上构建：

```text
macos/scripts/setup-local-signing.sh
macos/scripts/package-app.sh
```

`setup-local-signing.sh` 在当前用户登录钥匙串创建 `LeafBoard Local Signing`。脚本生成的临时私钥文件在导入后覆盖并删除。

禁止：

- 把本机证书私钥、P12 或密码提交到仓库。
- 为了消除提示而允许所有应用访问 WebDAV 密码。
- 每次构建重新创建不同的签名身份。

## 4. 本地构建和授权

LeafBoard 采用源码本地构建：

1. 每台 Mac 首次构建时运行一次 `setup-local-signing.sh`，创建固定的 `LeafBoard Local Signing`。
2. `package-app.sh` 使用该身份同时签名主程序和 `LeafBoardCredentialHelper`。
3. Bundle ID、凭证助手 identifier 和签名身份保持稳定，主程序重建不改变 Keychain 的凭证访问者。
4. 用户只在 Hub 本地设置页录入密码；首次配置必须输入应用密码。首次访问 Keychain 时最多授权一次，也允许不弹窗。
5. 后续重建只重复运行 `package-app.sh`。连续重建并重启两次，应不再弹授权窗口，且发布仍成功。

换到另一台 Mac 时，在新机器重新创建本地签名身份并重新录入凭证。不得复制或共享原机器的签名私钥。

## 5. 开源仓库检查

提交开源仓库前检查：

- 搜索账号、应用密码、Bearer、Cookie、Authorization、邮箱和内部 URL。
- 删除真实卡片缓存、ADB inbox、截图、日志、SQLite 会话和构建产物。
- 协议示例使用虚构值并在标题中标注“示例”。
- 删除维护者个人数据源的 Client、接口路径、字段映射和测试；公共仓库只保留通用 Hub 与协议示例。
- `android/local.properties`、`.build/`、`dist/`、APK 等按仓库策略忽略。
- 不公开公司内部接口细节、会话格式或可复用令牌；仅适用于维护者环境的适配器不得进入公共仓库历史。
- README 明确源码本地构建和固定本地签名流程。

## 6. 外部服务最小权限

- 坚果云优先使用独立应用密码和专用 `/leafboard/` 根目录。
- Producer 只写自己的目录。
- 新平台只申请读取所需数据和写入 LeafBoard 目录的最小权限。
- 删除远端 Card、Catalog 或凭证前，先解析精确目标并采用可恢复方式；不得对宽泛路径执行递归删除。
