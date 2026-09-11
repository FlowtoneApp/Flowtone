# Flowtone

**Flowtone（声流）** 是一款 Android 音乐播放器。

它以舒适、流畅的本地音乐播放体验为核心，同时正在构建一套与具体音乐服务解耦的扩展体系，让本地音乐、开放音乐服务和用户自己的音乐来源能够在同一个播放器中共存。

Flowtone 使用 Kotlin 与 Jetpack Compose 开发，目前仍处于活跃开发阶段。功能、交互与内部架构仍会继续调整，但项目已经不再是早期 MVP。

## 功能

### 本地音乐

Flowtone 使用 Android MediaStore 读取设备中的本地音乐，并提供完整的本地媒体库与播放体验。

目前包括：

* 本地歌曲扫描与浏览
* 艺术家浏览与详情页面
* 专辑封面与歌曲元数据
* Mini Player 与展开播放器
* 后台播放
* 系统媒体通知与媒体控制
* 播放队列
* 播放进度与跳转
* 音频焦点处理

播放器本身由独立的 `MediaSessionService` 持有，不依赖 Compose 页面生命周期。

### 歌词

Flowtone 已支持本地 `.lrc` 歌词。

可以在设置中授权歌词目录，Flowtone 会读取与歌曲对应的歌词文件，并在播放器中显示同步歌词。

当前歌词体验包括：

* LRC 时间轴解析
* 自动跟随播放进度
* 当前歌词高亮
* 点击歌词跳转播放位置
* 双语歌词显示
* 歌曲切换时的歌词过渡
* 歌词滚动与播放进度联动
* 可选择的歌词背景样式
* 歌词目录授权与失效状态处理

歌词文件通过 Android Storage Access Framework 读取，Flowtone 不需要获得整个文件系统的访问权限。

### 喜欢与歌单

Flowtone 提供内置的“我喜欢的音乐”和自定义歌单。

目前支持：

* 收藏 / 取消收藏歌曲
* 创建自定义歌单
* 重命名和删除歌单
* 将歌曲添加到歌单
* 自定义歌单视觉色彩
* 歌单详情页
* 歌单封面、标题、作者、歌曲数量等元信息
* 自定义歌单简介
* 本地歌曲与在线歌曲混合保存

Flowtone 使用统一的持久歌曲身份描述本地和在线歌曲，因此安装相应 Provider 后，在线歌曲也可以被收藏、加入歌单并在之后重新解析播放。

## 搜索

Flowtone 提供全局搜索界面。

搜索来源可以在以下范围之间切换：

* 全部
* 本地
* 已安装的音乐 Provider

Provider 可以提供自己的搜索能力、搜索分类、分页结果和搜索首页内容。

搜索界面不会直接依赖某一家音乐服务，具体在线搜索能力由用户安装的扩展提供。

## 扩展与 Provider

Flowtone 已包含实验性的扩展运行体系。

扩展使用 `.flowtone` 包安装，并通过 Manifest 声明自己的身份、能力和网络权限。

当前扩展能力包括：

* `artist_avatar`

    * 为艺术家提供额外头像来源

* `music_provider`

    * 搜索在线歌曲
    * 提供持久歌曲身份
    * 解析在线播放资源

* `song`

    * 通过无参数 `getSongs()` 提供结构化的全量歌曲 collection
    * 只声明 Song entity 能力，不代表歌曲一定可以播放

* `album`

    * 通过无参数 `getAlbums()` 提供结构化的全量专辑 collection
    * 只声明 Album entity 能力，不代表 Provider 提供专辑详情 endpoint

`song` 与 `album` 是可选能力。未声明这些能力、也未实现对应方法的旧 Provider 仍可继续使用搜索、Artist Profile 与既有播放能力。

音乐 Provider 可以与 Flowtone 的搜索、播放、喜欢和歌单系统连接，而播放器本身不需要直接适配特定音乐平台。

### 扩展运行环境

Flowtone 使用 AndroidX JavaScriptEngine 运行 JavaScript 扩展，并由 Host 控制扩展与 Android 应用之间的能力边界。

扩展需要显式声明允许访问的网络 Host。

在线播放资源同样由 Flowtone Host 管理，当前可处理普通媒体流以及 HLS 播放资源。

扩展体系目前仍在持续完善，接口和包格式在未来版本中仍可能发生变化。

## 听歌记录

Flowtone 会在本地记录播放行为，并提供简单的听歌统计。

目前可以查看：

* 今日有效播放次数
* 今日听歌时长
* 累计有效播放次数
* 累计听歌时长
* 主要音乐来源
* 听歌记录详情

这些数据由 Flowtone 在设备本地维护。

## 界面

Flowtone 基于 Material Design 3，但并不完全使用默认 Material 页面表现。

项目目前包含一套自己的视觉与动画系统，包括：

* 深色 / 浅色主题
* 页面背景色彩与云层视觉
* 基于专辑封面的播放器色彩
* Mini Player 展开与收起
* 页面元素错峰动画
* 自定义页面切换
* Incoming / Current / Outgoing 页面生命周期
* 页面返回时的连续视觉状态
* 部分页面切换支持动画中途反向

页面动画与页面内容状态采用统一的状态驱动方式处理，以减少快速操作时的跳变、闪烁和重复创建。

## 下载

推荐从 **Releases** 页面下载已经打包的 Release / Pre-release APK。

`main` 分支包含最新开发进度，可以自行构建，但其中可能存在尚未充分验证的改动，不建议将其视为稳定版本。

## 权限与隐私

### 本地音乐

Flowtone 会根据 Android 版本请求读取本地音频媒体所需的权限。

本地音乐由 Android MediaStore 提供，Flowtone 不会为了维护自己的媒体库而复制整套 MediaStore 数据。

### 本地歌词

歌词目录由用户通过 Android 系统文件选择器主动授权。

Flowtone 仅通过获得的目录访问权限读取歌词文件。

### 在线扩展

Flowtone 核心播放器不绑定特定在线音乐服务。

安装第三方扩展后，扩展可能根据自身功能访问网络。

扩展需要在 Manifest 中声明允许访问的网络 Host，Flowtone 的扩展运行环境会按照这些声明限制网络访问范围。

第三方扩展本身的服务行为与隐私政策应由对应扩展及服务提供者负责。

## 技术栈

Flowtone 当前主要使用：

* Kotlin
* Jetpack Compose
* Material Design 3
* AndroidX Lifecycle ViewModel
* AndroidX Media3

    * ExoPlayer
    * HLS
    * MediaController
    * MediaSession
    * MediaSessionService
* AndroidX JavaScriptEngine
* AndroidX Metrics Performance
* AndroidX Palette
* Coil 3
* TagLib
* Gradle Version Catalog

最低支持 Android 9（API 28）。

## 架构

Flowtone 将 UI、播放、本地媒体、持久化和在线扩展尽量保持在各自的职责边界内。

播放链路大致为：

```text
Composable
    ↓
MusicViewModel
    ↓
PlaybackController
    ↓
MediaController
    ↓
FlowtoneMediaSessionService
    ↓
ExoPlayer
```

`FlowtoneMediaSessionService` 是 ExoPlayer 与 MediaSession 的唯一所有者。

Composable 不直接创建或持有 ExoPlayer。

本地媒体仍以 Android MediaStore 为权威来源，而 Flowtone 自己维护的喜欢、歌单、听歌记录以及扩展数据由独立的数据层负责。

在线音乐则通过 Provider 抽象进入搜索和播放系统，通用 UI 不直接依赖具体音乐服务。

## 构建

使用项目自带的 Gradle Wrapper 即可构建。

### Linux / macOS

Debug：

```bash
./gradlew assembleDebug
```

Release：

```bash
./gradlew assembleRelease
```

### Windows PowerShell

Debug：

```powershell
.\gradlew.bat assembleDebug
```

Release：

```powershell
.\gradlew.bat assembleRelease
```

Release 构建支持通过环境变量或本地 `keystore.properties` 提供签名配置。

请勿将以下内容提交到仓库：

* keystore
* 签名密码
* `keystore.properties`
* 其他私有签名信息

## 开发说明

Flowtone 在开发过程中大量使用 AI 辅助进行代码实现、分析、调试和重构。

项目方向、产品设计、功能取舍、架构决策、测试验证以及发布决定由作者人工完成。

AI 生成的实现不会被直接视为正确结果。进入项目的改动仍需要经过代码检查、编译检查以及实际设备验证。

Flowtone 仍在快速演进，因此部分内部接口、扩展协议和交互设计可能随开发推进而变化。

## License

Flowtone is licensed under the **GNU General Public License v3.0 (GPLv3)**.

You may use, modify, and distribute this project under the terms of GPLv3.

If you want to use Flowtone in a proprietary or closed-source product, or require licensing terms that are not compatible with GPLv3, please contact the author for a separate commercial license.

## 许可证

Flowtone 基于 **GNU General Public License v3.0（GPLv3）** 开源。

你可以按照 GPLv3 的条款使用、修改和分发本项目。

如果你希望将 Flowtone 用于闭源或专有软件产品，或需要与 GPLv3 不兼容的授权方式，请联系作者获取单独的商业授权。
