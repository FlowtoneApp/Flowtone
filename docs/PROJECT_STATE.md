# Flowtone 项目状态

## 项目目标

Flowtone 是一个 Android 本地音乐播放器。当前阶段是 Flowtone 0.2 internal：在 MVP 0.1 本地播放基础上，整理播放队列并补齐基础队列控制。

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
- `ExoPlayer` 只在 `PlaybackController` 中创建，不在 Composable 中创建。
- 已将播放控制和播放状态接入 `MusicViewModel`。
- 点击歌曲可以播放本地音频。
- 当前播放歌曲会在列表中高亮。
- 底部 MiniPlayer 显示当前歌曲标题、艺术家，并支持播放 / 暂停。

## MVP 0.1 已具备功能

- 未授权时提示：需要音频权限才能扫描本地音乐。
- 授权后自动扫描本地音乐。
- 扫描中提示：正在扫描本地音乐。
- 空列表提示：没有找到本地音乐。
- 歌曲列表显示标题、艺术家、时长。
- 点击歌曲后使用 Media3 ExoPlayer 播放。
- MiniPlayer 显示当前歌曲信息。
- MiniPlayer 支持播放 / 暂停。

## Flowtone 0.2 Internal 已完成内容

- 已建立基础播放队列：`playbackQueue`。
- 点击歌曲后会记录当前队列索引：`currentQueueIndex`。
- 已实现下一曲：播放队列中的下一首。
- 已实现上一曲：播放队列中的上一首。
- 当前歌曲自然结束后会自动播放下一首。
- 最后一首播放结束后停止，不循环。
- 队列为空或索引非法时不会崩溃。
- 队列逻辑保留在 `MusicViewModel`，不写进 Composable。

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

## 当前仍未实现

- 后台播放。
- Android 通知栏媒体控件。
- MediaSessionService。
- 耳机按钮。
- 随机播放。
- 单曲循环。
- 列表循环。
- 歌单 / 收藏。
- 歌词。
- 封面。
- 播放进度条。

## 下一阶段建议

1. 先手动测试 0.2 internal：点击播放、上一曲、下一曲、自动下一首、最后一首停止。
2. 修正手动测试中发现的崩溃、队列索引或权限兼容问题。
3. 队列控制稳定后，再考虑后台播放、通知栏媒体控件和 MediaSessionService。
4. 在后台播放稳定前，不扩展随机播放、循环播放、歌单 / 收藏等功能。

## 禁止事项

- 不要在 Composable 中创建或直接操作 ExoPlayer。
- 不要把播放队列逻辑写进 Composable。
- 不要把 UI、权限、扫描、播放控制混在同一个文件里。
- 不要引入 Hilt、Room、Navigation 等额外框架，除非后续有明确需求。
- 不要实现后台播放、通知栏媒体控件、耳机按钮、随机播放或循环播放，除非进入对应阶段。
- 不要在基础播放和队列控制未稳定前扩展在线音乐、账号、歌词、插件系统或复杂歌单功能。
