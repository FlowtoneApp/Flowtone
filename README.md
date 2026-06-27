# Flowtone

Flowtone（声流）是一款 Android 音乐播放器，旨在提供舒适、稳定、轻量的本地音乐播放体验。

项目目前仍处于早期开发阶段，功能、界面与架构都在快速迭代中。

Flowtone 当前首先专注于本地音乐播放。未来，项目会继续探索扩展生态，以拥抱开源音乐库与音乐记录社区。

## 当前版本

当前最新开发 tag：`0.9.0`

当前最新公开预览版：`0.9.0`

如果你希望体验相对稳定的版本，建议下载 Releases 页面中的 Release / Pre-release 版本。

如果你希望查看最新开发进度，可以使用 `main` 分支或最新 tag。

请注意：`main` 分支和最新开发 tag 可能包含尚未充分验证的开发改动。

## 当前功能

Flowtone 目前已经支持：

### 本地音乐

* 扫描本地音乐
* 本地歌曲列表展示
* 本地曲库入口
* 专辑封面显示
* 基于封面取色的视觉背景

### 播放体验

* 本地音乐播放
* 播放队列
* 上一曲 / 下一曲
* 暂停 / 继续播放
* 真实播放进度显示
* 拖动进度条 seek
* 后台播放
* Android 系统媒体控件基础支持
* 播放顺序切换：

  * 顺序播放
  * 单曲循环
  * 随机播放

### 界面

* MiniPlayer
* 展开式播放界面
* 首页 / 曲库 / 我的 三个主页面
* 路径式顶栏标题
* 设置页
* 关于页
* 开源组件入口

### 设置

* 设置应用启动时默认进入的页面
* 可选择隐藏二级页面返回按钮，并通过右滑返回上一级页面

## 当前未实现

Flowtone 当前仍未接入真实在线音乐服务。

以下能力暂未实现：

* 搜索
* 歌词
* 歌单系统
* 收藏持久化

## 下载

你可以在 Releases 页面下载当前公开预览版本。

由于 Flowtone 仍处于早期开发阶段，建议优先使用 Release / Pre-release 中提供的 APK，而不是直接使用 `main` 分支自行构建。

## 权限与隐私

Flowtone 当前主要处理设备上的本地音乐文件。

当前版本会请求读取本地音频所需的权限，并使用媒体播放前台服务来支持后台播放和系统媒体控件。

## 技术栈

Flowtone 当前主要使用：

* Kotlin
* Jetpack Compose
* Material Design 3
* AndroidX Lifecycle ViewModel
* AndroidX Media3 ExoPlayer
* AndroidX Media3 MediaSession
* AndroidX Media3 MediaSessionService
* Coil
* AndroidX Palette
* Gradle Version Catalog

## 构建

你可以使用 Gradle Wrapper 构建项目。

Debug 构建：

```bash
./gradlew assembleDebug
```

Release 构建：

```bash
./gradlew assembleRelease
```

Windows PowerShell 下可以使用：

```powershell
.\gradlew.bat assembleDebug
```

```powershell
.\gradlew.bat assembleRelease
```

Release 签名配置支持通过环境变量或本地 `keystore.properties` 提供。请不要将 keystore、密码或 `keystore.properties` 提交到仓库。

## 开发说明

Flowtone 在开发过程中大量使用 AI 辅助，但项目方向、功能取舍、测试验证与发布决策由作者人工完成。

本项目仍在快速变化中，代码结构和功能设计可能会随版本推进而调整。

## License

This project is licensed under the GNU General Public License v3.0.

You may use, modify, and distribute this project under the terms of GPLv3.

If you want to use this project in a proprietary or closed-source product, or need a license that is not compatible with GPLv3, please contact the author for a separate commercial license.

## 许可证

本项目基于 GNU General Public License v3.0 开源。

你可以在 GPLv3 条款下使用、修改和分发本项目。

如果你希望将本项目用于闭源/专有软件产品，或需要不受 GPLv3 约束的单独授权，请联系作者获取商业授权。
