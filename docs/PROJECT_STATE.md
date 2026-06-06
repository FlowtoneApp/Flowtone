# Flowtone 项目状态

## 项目目标

Flowtone 是一个 Android 本地音乐播放器。当前目标是完成 MVP 0.1：本地音乐扫描、列表展示、点击播放、底部 MiniPlayer 播放/暂停。

## 当前技术栈

- Kotlin
- Jetpack Compose
- Material Design 3
- MVVM
- AndroidX Lifecycle ViewModel
- AndroidX Media3 ExoPlayer
- Gradle Version Catalog

## 已完成内容

- `MainActivity` 只负责启动 Compose，并调用 `FlowtoneApp`。
- 已声明并请求音频读取权限。
- 已使用 `ContentResolver + MediaStore.Audio.Media` 扫描本地音乐。
- 已显示本地歌曲列表。
- 已使用 `PlaybackController` 封装 Media3 ExoPlayer。
- 已将播放控制接入 `MusicViewModel`。
- 点击歌曲可以播放本地音频。
- 当前播放歌曲会在列表中高亮。
- 底部 MiniPlayer 显示当前歌曲标题、艺术家，并支持播放/暂停。

## 当前 MVP 0.1 已具备的功能

- 未授权时提示：需要音频权限才能扫描本地音乐。
- 授权后自动扫描本地音乐。
- 扫描中提示：正在扫描本地音乐。
- 空列表提示：没有找到本地音乐。
- 歌曲列表显示标题、艺术家、时长。
- 点击歌曲后使用 Media3 ExoPlayer 播放。
- MiniPlayer 显示当前歌曲信息。
- MiniPlayer 支持播放 / 暂停。

## 当前文件结构概览

```text
app/src/main/java/ink/tenqui/flowtone/
|-- MainActivity.kt
|-- data/
|   `-- AudioScanner.kt
|-- model/
|   `-- Song.kt
|-- permissions/
|   `-- AudioPermission.kt
|-- playback/
|   |-- PlaybackController.kt
|   `-- PlaybackState.kt
|-- ui/
|   |-- FlowtoneApp.kt
|   |-- components/
|   |   |-- MiniPlayer.kt
|   |   `-- SongListItem.kt
|   |-- screens/
|   |   `-- LibraryScreen.kt
|   `-- theme/
`-- viewmodel/
    `-- MusicViewModel.kt
```

## 当前已知限制

- 只支持应用内前台播放。
- 关闭应用或进程被系统回收后不会继续播放。
- 没有 Android 系统媒体通知。
- 没有 MediaSessionService。
- 没有上一曲 / 下一曲。
- 没有播放进度条。
- 没有歌词、封面、播放队列。
- 没有复杂的错误恢复逻辑。

## 下一阶段建议

1. 先手动测试 MVP 0.1：授权、扫描、列表、点击播放、MiniPlayer 播放/暂停。
2. 修正手动测试中发现的崩溃、权限边界或扫描兼容问题。
3. MVP 0.1 稳定后，再考虑后台播放、媒体通知和 MediaSessionService。
4. 在基础播放稳定前，不扩展歌词、封面、播放队列等功能。

## 禁止事项

- 不要在 Composable 中创建或直接操作 ExoPlayer。
- 不要把 UI、权限、扫描、播放控制混在同一个文件里。
- 不要在 MVP 0.1 稳定前实现在线音乐服务。
- 不要添加账号系统。
- 不要添加歌词功能。
- 不要添加插件系统。
- 不要添加复杂播放列表功能。
- 不要引入 Hilt、Room、Navigation 等额外框架，除非后续有明确需求。
- 不要实现后台播放、系统媒体通知或 MediaSessionService，除非进入下一阶段。
