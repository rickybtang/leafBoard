# LeafBoard 开发说明

## 1. 环境

- macOS 14 或更高版本；使用包含 Swift 6 的 Xcode。
- Android SDK 已安装，Leaf2 为 Android 11 / API 30。
- Android 构建使用 Android Studio 内置 JDK 21。
- ADB 设备序列号必须通过 `adb devices` 在运行时确认，不得在代码或公共文档中硬编码。

## 2. 目录

```text
android/                 Leaf2 Android App
macos/                   macOS Swift 菜单栏 Hub
protocol/schemas/        JSON Schema Draft 2020-12
protocol/examples/       可执行验证示例
docs/                    产品、架构和开发文档
server.py + web/         早期 USB 浏览器诊断工具
```

## 3. 凭证

坚果云配置由用户在 macOS App 设置页和 Android App 设置页本地输入。开发、测试和日志中使用占位值：

```text
WebDAV URL: https://dav.jianguoyun.com/dav/leafboard/
Username: <local-only>
App password: <keychain-only>
```

禁止把真实凭证写入环境示例、测试快照或命令历史。

macOS 打包使用登录钥匙串中的固定代码签名身份 `LeafBoard Local Signing`。WebDAV 密码只由独立的 `LeafBoardCredentialHelper` 访问；日常重建 Hub 不改变凭证访问者，避免反复询问 Keychain 权限。

公开应用标识固定为：Android `io.github.rickybtang.leafboard`、macOS Hub `io.github.rickybtang.leafboard.hub`、凭证助手 `io.github.rickybtang.leafboard.credential-helper`。标识确定后不得随意改变，否则会影响 Android 升级和 macOS Keychain 授权连续性。

首次源码构建执行：

```text
macos/scripts/setup-local-signing.sh
```

脚本只在当前用户登录钥匙串创建一次本地代码签名身份。以后执行 `macos/scripts/package-app.sh` 时，主程序和凭证助手都会继续使用这个身份签名。

本地授权流程很简单：

1. 一台 Mac 首次构建时运行一次 `setup-local-signing.sh`。
2. 运行 `package-app.sh` 生成并签名 `macos/dist/LeafBoard Hub.app`。
3. 在 Hub 设置页输入 WebDAV 配置；首次配置必须输入应用密码。Keychain 授权最多出现一次，也可能不弹窗。
4. 后续重新打包继续复用同一签名身份，不重新创建证书，也不复制私钥。
5. 连续重新打包并重启两次，确认不再弹授权窗口且 WebDAV 仍能发布。

换到另一台 Mac 时，在那台机器重新执行一次 `setup-local-signing.sh` 并重新录入本地凭证即可。

本地验收必须包含：首次配置允许出现一次 Keychain 授权；随后重新打包并重启 Hub 两次，均不得再次出现授权弹窗，且 WebDAV 发布成功。

## 4. 协议变更

- 先修改 `protocol.md` 和 JSON Schema。
- 增加有效与无效示例测试。
- 再修改 macOS Producer 与 Android Reader。
- V1.x 只能添加兼容的可选行为；不兼容变更升级主版本。

## 5. 验证

每次交付至少执行：

```text
协议示例校验
macOS swift test
macOS release build
Android unit test
Android assembleDebug
ADB 安装与 Leaf2 实机启动
本地文件/USB 导入验证
WebDAV 真实发布与读取验证
```

真实 WebDAV 验证必须由用户在本地 App 界面输入凭证，避免凭证出现在终端输出。

自定义 Producer 不进入 Hub 默认 target。Producer 生成 Card 后，通过仅本机 `POST /api/cards` 接入；来源凭证和测试留在 Producer 自己的安全边界内。
