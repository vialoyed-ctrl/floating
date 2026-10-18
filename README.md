# 悬浮球增强 · FloatBallPlus

为 ColorOS 系统悬浮球增加自定义应用入口和外观设置的 LSPosed 模块，保留原有手势识别及动效。

## 功能

- **手势快捷启动**：单击、双击、长按可分别打开指定应用；未配置时跟随系统原设置。
- **应用菜单**：保留系统菜单，新增“应用”入口，第二层最多配置 5 个应用，空项自动隐藏。
- **三种外观**：iOS 原版、iOS 反色、系统原版；两种 iOS 风格均为圆形同心圆。
- **大小与透明度**：大小支持原尺寸的 70%–200%，iOS 风格透明度支持 15%–100%，提供外观预览。
- **贴边适配**：按图标可见边缘和缩放后的窗口尺寸计算停靠位置。
- **一键重启**：设置页可通过 root 重启系统悬浮球并应用设置。

## 安装

1. 从 [Releases](https://github.com/vialoyed-ctrl/floating/releases) 下载并安装 APK。
2. 在 LSPosed 中启用“悬浮球增强”，作用域勾选 **悬浮球（`com.coloros.floatassistant`）**。
3. 重启手机或系统悬浮球进程，进入模块设置页配置。
4. 修改外观后，点击“重启悬浮球并应用设置”；首次使用该按钮需授予模块 root 权限。

## 兼容与状态

当前针对 **OnePlus PLK110 / Android 17 / ColorOS 悬浮球 17.1.14** 开发，需要 root 和 LSPosed。模块依赖系统内部类，其他机型或版本尚未验证。

当前版本 **0.2.14** 已通过编译、APK 签名校验及 9432 项贴边几何检查；本次贴边修复仍待真机验证。发布的 APK 为调试构建。

## 构建

准备 Java 21、Android SDK 36、Gradle 8.14.3，设置 `JAVA_HOME` 和 `ANDROID_HOME`，在项目目录运行：

```sh
gradle assembleDebug
```

APK 输出：`app/build/outputs/apk/debug/app-debug.apk`。

配置保存在手机本地，配置读取接口仅允许目标系统悬浮球应用访问。
