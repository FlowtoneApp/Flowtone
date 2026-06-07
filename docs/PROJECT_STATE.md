# Flowtone 项目状态

## 项目目标

Flowtone 是一个 Android 本地音乐播放器。当前阶段是 Flowtone 0.5：设计从基础 MediaSession 迁移到 MediaSessionService 的路线，为后续后台播放、通知栏媒体控件、锁屏媒体区、耳机按钮 / 蓝牙媒体按钮做准备。

## 当前技术栈

- Kotlin
- Jetpack Compose
- Material Design 3
- MVVM
- AndroidX Lifecycle ViewModel
- AndroidX Media3 ExoPlayer
- AndroidX Media3 MediaSession
- Gradle Version Catalog

## 已完成基础能力

- 已请求音频读取权限。
- 已使用 `ContentResolver + MediaStore.Audio.Media` 扫描本地音乐。
- 已显示本地歌曲列表。
- 已使用 `PlaybackController` 封装 Media3 ExoPlayer。
- `ExoPlayer` 只在 `PlaybackController` 中创建，不在 Composable 中创建。
- 已将播放控制和播放状态接入 `MusicViewModel`。
- 点击歌曲可以播放本地音频。
- 底部 MiniPlayer 显示当前歌曲标题、艺术家，并支持播放 / 暂停 / 上一曲 / 下一曲。

## Flowtone 0.2 Internal 已完成内容

- 已建立基础播放队列：`playbackQueue`。
- 点击歌曲后会记录当前队列索引：`currentQueueIndex`。
- 已实现下一曲和上一曲。
- 当前歌曲自然结束后会自动播放下一首。
- 最后一首播放结束后停止，不循环。
- 队列逻辑保留在 `MusicViewModel`，不写进 Composable。

## Flowtone 0.3 Internal 已完成内容

- 已整理 UI 状态传递。
- 歌曲列表项已调整为 Material 3 风格。
- 当前播放歌曲在列表中有更清晰的高亮状态。
- MiniPlayer 已完成视觉优化，保留贴底播放器结构。
- 空状态 / 无权限状态文案已优化。
- 主页面背景、顶栏、列表区域和 MiniPlayer 的视觉层次已统一。
- MiniPlayer 控制按钮已改为 Material 图标按钮。

## Flowtone 0.4 Internal 已完成内容

- 已检查播放架构边界。
- 已新增集中转换入口：`Song.toMediaItem()`。
- `MediaItem` 已携带 `mediaId`、`uri`、标题和歌手 metadata。
- 已接入基础 `MediaSession`。
- `MediaSession` 当前由 `PlaybackController` 内部创建，并绑定内部私有持有的 `ExoPlayer`。
- `PlaybackController.release()` 会释放 `MediaSession` 和 `ExoPlayer`，并已有重复释放防护。
- 尚未实现 `MediaSessionService`、通知栏媒体控件或真正后台播放。
- 系统媒体控制表现仍需要以真机实测为准。

## Flowtone 0.5 Step 0.5.1 完成内容

- 本步只做设计文档，不修改 Kotlin 代码。
- 未修改 Gradle 依赖。
- 未新增 Service。
- 未新增权限。
- 未新增通知栏。
- 未新增后台播放。
- 未迁移队列所有权。
- 未把队列改成 ExoPlayer playlist。
- 已新增设计文档：`docs/MEDIA_SESSION_SERVICE_PLAN.md`。
- 文档记录了从当前架构迁移到 `MediaSessionService` 的保守分步方案。

## 当前架构

```text
Composable -> MusicViewModel -> PlaybackController -> ExoPlayer
```

当前职责：

- `Composable`：显示 UI，触发 ViewModel 方法。
- `MusicViewModel`：持有歌曲列表、播放队列、当前索引，处理上一曲、下一曲、自动下一首。
- `PlaybackController`：私有持有 `ExoPlayer` 和基础 `MediaSession`，负责播放、暂停、释放播放器。
- `ExoPlayer`：执行实际播放。

## 未来目标架构

```text
Composable -> MusicViewModel -> MediaController -> MediaSessionService -> ExoPlayer
```

迁移原则：

- 不让 Composable 直接接触 `ExoPlayer` 或 `MediaSession`。
- 不一次性完成完整后台播放。
- 先新增 `MediaSessionService` 骨架。
- 再迁移 `ExoPlayer` / `MediaSession` 所有权。
- 再让 ViewModel 通过 `MediaController` 控制播放。
- 再处理通知栏、锁屏、耳机按钮和后台播放。

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
|   |-- PlaybackState.kt
|   `-- SongMediaItem.kt
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

- MediaSessionService。
- 后台播放。
- Android 通知栏媒体控件。
- 锁屏媒体区稳定支持。
- 耳机按钮 / 蓝牙媒体按钮。
- 搜索。
- 歌单 / 收藏。
- 专辑封面。
- 歌词。
- 随机播放。
- 单曲循环 / 列表循环。
- 播放进度条。
- 动画和更现代的交互过渡。

## 下一阶段建议

1. 按 `docs/MEDIA_SESSION_SERVICE_PLAN.md` 先新增 `MediaSessionService` 骨架。
2. 骨架稳定后，再迁移 `ExoPlayer` 和 `MediaSession` 所有权。
3. 再让 ViewModel 通过 `MediaController` 或轻量控制接口控制播放。
4. 最后再处理通知栏媒体控件、锁屏媒体区、耳机按钮和后台播放。

## 禁止事项

- 不要在 Composable 中创建或直接操作 ExoPlayer。
- 不要在 Composable 中创建或直接操作 MediaSession。
- 不要把播放队列逻辑写进 Composable。
- 不要把 UI、权限、扫描、播放控制混在同一个文件里。
- UI 优化应保持简单，不要为了视觉效果引入复杂框架。
- 不要引入 Hilt、Room、Navigation 等额外框架，除非后续有明确需求。
- 不要一次性实现完整后台播放。
- 不要在没有分步验证前迁移播放队列所有权。
