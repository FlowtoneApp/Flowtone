# Flowtone 项目状态

## 项目目标

Flowtone 是一个 Android 本地音乐播放器。当前阶段是 Flowtone 0.5 收尾：播放核心链路已迁移到 MediaSessionService，并已通过真机验证具备后台播放核心能力；完整通知栏媒体控件、锁屏媒体区、耳机按钮 / 蓝牙媒体按钮体验留到后续版本。

## 当前技术栈

- Kotlin
- Jetpack Compose
- Material Design 3
- MVVM
- AndroidX Lifecycle ViewModel
- AndroidX Media3 ExoPlayer
- AndroidX Media3 MediaSession
- AndroidX Media3 MediaSessionService
- Gradle Version Catalog

## 已完成基础能力

- 已请求音频读取权限。
- 已使用 `ContentResolver + MediaStore.Audio.Media` 扫描本地音乐。
- 已显示本地歌曲列表。
- 已使用 `PlaybackController` 封装 Media3 ExoPlayer。
- `ExoPlayer` 只在非 Composable 层创建和操作。
- 已将播放控制和播放状态接入 `MusicViewModel`。
- 点击歌曲可以播放本地音频。
- 底部 MiniPlayer 显示当前歌曲标题、艺术家，并支持播放 / 暂停 / 上一曲 / 下一曲。

## Flowtone 0.2 Internal

- 已建立基础播放队列：`playbackQueue`。
- 点击歌曲后会记录当前队列索引：`currentQueueIndex`。
- 已实现下一曲和上一曲。
- 当前歌曲自然结束后会自动播放下一首。
- 最后一首播放结束后停止，不循环。
- 队列逻辑保留在 `MusicViewModel`，不写进 Composable。

## Flowtone 0.3 Internal

- 已整理 UI 状态传递。
- 歌曲列表项已调整为 Material 3 风格。
- 当前播放歌曲在列表中有高亮状态。
- MiniPlayer 已完成视觉优化，保留贴底播放器结构。
- 空状态 / 无权限状态文案已优化。
- 主页面背景、顶栏、列表区域和 MiniPlayer 的视觉层次已统一。
- MiniPlayer 控制按钮已改为 Material 图标按钮。

## Flowtone 0.4 Internal

- 已检查播放架构边界。
- 已新增集中转换入口：`Song.toMediaItem()`。
- `MediaItem` 已携带 `mediaId`、`uri`、标题和歌手 metadata。
- 已接入基础 `MediaSession`。
- 0.4 阶段 `MediaSession` 曾由 `PlaybackController` 内部创建，并绑定内部私有持有的 `ExoPlayer`。
- 0.4 阶段 `PlaybackController.release()` 会释放 `MediaSession` 和 `ExoPlayer`，并已有重复释放防护。
- 0.5.6 后，实际播放已迁移到 `MediaController -> FlowtoneMediaSessionService`。
- 系统媒体控制表现仍需要以真机实测为准。

## Flowtone 0.5 当前进度

### Step 0.5.1

- 只完成设计文档，不修改 Kotlin 播放代码。
- 已新增设计文档：`docs/MEDIA_SESSION_SERVICE_PLAN.md`。
- 文档记录了从当前架构迁移到 `MediaSessionService` 的保守分步方案。
- 尚未新增 Service、通知栏、后台播放或权限。

### Step 0.5.2

- 已新增 `FlowtoneMediaSessionService` 最小骨架。
- 该 Service 继承 `androidx.media3.session.MediaSessionService`。
- 已在 `AndroidManifest.xml` 中声明该 Service 和 `androidx.media3.session.MediaSessionService` action。
- 0.5.2 当时播放仍然由 `PlaybackController` 内部的 ExoPlayer 完成。
- 尚未迁移播放器所有权、队列所有权或 UI 控制链路。
- 尚未实现后台播放、通知栏媒体控件、锁屏媒体区或耳机按钮支持。

### Step 0.5.3

- 已在 `FlowtoneMediaSessionService.onCreate()` 中创建 Service 内部的 `ExoPlayer`。
- 已在 `FlowtoneMediaSessionService.onCreate()` 中基于该播放器创建 Service 内部的 `MediaSession`。
- `onGetSession()` 当前返回 Service 内部持有的 `MediaSession`。
- `onDestroy()` 会释放 Service 内部的 `MediaSession` 和 `ExoPlayer`，并将引用置空。
- 0.5.3 当时 App 内实际播放仍然由 `PlaybackController` 内部的 ExoPlayer 完成。
- 当前是临时双播放器过渡状态：`PlaybackController` 用于现有 App 内播放，`FlowtoneMediaSessionService` 用于验证未来 Service 生命周期。
- 后续步骤应移除 `PlaybackController` 内的播放器所有权，避免长期保留双播放器架构。
- 尚未让 UI / ViewModel 连接 `MediaController`。
- 尚未迁移队列所有权，也未把队列改成 ExoPlayer playlist。
- 尚未实现后台播放、通知栏媒体控件、锁屏媒体区或耳机按钮支持。

### Step 0.5.4

- 已新增 `FlowtoneMediaControllerConnection`，作为 App 侧连接 `FlowtoneMediaSessionService` 的准备层。
- 连接类使用 `SessionToken` 指向 `FlowtoneMediaSessionService`。
- 连接类使用 `MediaController.Builder(...).buildAsync()` 异步创建 `MediaController`。
- 连接类持有 `MediaController` 引用，并在 `release()` 中释放 controller future / controller。
- `PlaybackController` 当前只持有并释放该连接类，不使用它执行播放、暂停、上一曲、下一曲。
- 0.5.4 当时 App 内实际播放仍然由 `PlaybackController` 内部的 ExoPlayer 完成。
- 当前是临时双播放器 / 双控制过渡状态：Service 内播放器用于未来架构验证，`PlaybackController` 内播放器继续负责现有播放，`MediaController` 连接只做准备。
- 尚未让 `MediaController` 接管任何播放行为。
- 后续步骤应逐步把播放控制迁移到 `MediaController -> MediaSessionService`，并移除长期双播放器 / 双控制架构。

### Step 0.5.4.1

- 已修复临时双 `MediaSession` 过渡状态下的 session id 冲突。
- `PlaybackController` 内的过渡期 `MediaSession` 使用显式 id：`flowtone_local_transition_session`。
- `FlowtoneMediaSessionService` 内的 `MediaSession` 使用显式 id：`flowtone_service_session`。
- 修复目标是避免两个 `MediaSession` 都使用默认空 id 导致 `Session ID must be unique` 崩溃。
- 0.5.4.1 当时播放行为不变，实际播放仍由 `PlaybackController` 内部 ExoPlayer 完成。
- 后续 0.5.5 应移除 `PlaybackController` 内旧播放器 / 旧 Session 所有权，避免长期双 Session 架构。

### Step 0.5.5

- 已补齐未来媒体播放前台服务所需的 Manifest 声明。
- 已新增权限：`android.permission.FOREGROUND_SERVICE`。
- 已新增权限：`android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK`。
- 已为 `FlowtoneMediaSessionService` 添加 `android:foregroundServiceType="mediaPlayback"`。
- 保留现有 `androidx.media3.session.MediaSessionService` intent action。
- 未新增 `POST_NOTIFICATIONS` 权限，通知权限留到通知栏阶段处理。
- 本步只是播放迁移前的 Manifest 准备。
- 0.5.5 当时实际播放仍由 `PlaybackController` 内部 ExoPlayer 完成。
- 尚未实现通知栏媒体控件或真正后台播放。

### Step 0.5.6

- 已将 `PlaybackController` 的实际播放控制迁移到 `MediaController`。
- `PlaybackController` 不再创建本地 `ExoPlayer`。
- `PlaybackController` 不再创建本地 `MediaSession`。
- `FlowtoneMediaSessionService` 是当前唯一持有 `ExoPlayer` 和 `MediaSession` 的位置。
- `play(song)` 通过 `Song.toMediaItem()` 创建 `MediaItem`，再调用 `MediaController.setMediaItem()`、`prepare()`、`play()`。
- `pause()` 通过 `MediaController.pause()` 执行。
- `play()` / 恢复播放通过 `MediaController.play()` 执行。
- 播放结束监听已迁移到 `MediaController` 的 `Player.Listener`，进入 `Player.STATE_ENDED` 时继续调用 `MusicViewModel` 提供的 `onPlaybackEnded` 回调。
- 如果 `MediaController` 尚未连接完成，`play(song)` 会暂存最近一次要播放的歌曲，连接完成后自动执行。
- `MusicViewModel` 仍然负责播放队列、当前索引、上一曲、下一曲和自动下一首。
- 队列尚未迁移到 `MediaSessionService`，也未改成 ExoPlayer playlist。
- 双播放器过渡状态已结束，但当时通知栏媒体控件和后台播放真机验证尚未完成。

### Step 0.5.7

- 本步是 0.5.6 播放迁移后的真机验证和 0.5 收尾记录步骤。
- 已检查 `PlaybackController` 与 `FlowtoneMediaControllerConnection` 生命周期。
- `PlaybackController.release()` 只释放 `MediaController` 连接资源，不直接释放 Service 内 ExoPlayer。
- `FlowtoneMediaSessionService.onDestroy()` 负责释放 Service 内部的 `MediaSession` 和 `ExoPlayer`。
- `Player.STATE_ENDED` 仍通过 `PlaybackController` 中挂到 `MediaController` 的 `Player.Listener` 通知 `MusicViewModel` 自动下一首。
- 已做一个小修复：`PlaybackController.release()` 时清空 `pendingSong`，避免释放后保留待播歌曲引用。
- 真机验证 App 内播放正常。
- 真机已观察到 Android 系统媒体控件能够识别 Flowtone 播放会话。
- 系统媒体控件中的暂停键可用。
- App 切到后台后播放继续。
- 从最近任务中划掉 Flowtone 后，音乐仍继续播放。
- 只有在系统“已开启的应用”中手动关闭 Flowtone 后，播放服务才会停止。
- 系统媒体控件暂未完成上一曲 / 下一曲控制。
- 系统媒体控件目前可能出现类似 seek to start 的按钮。
- 该现象可能与当前队列仍由 `MusicViewModel` 管理，而 `MediaSessionService` / ExoPlayer 只持有当前单曲 `MediaItem` 有关。
- 该现象不阻塞 `v0.5-internal`，因为 0.5 目标是完成播放核心迁移到 `MediaSessionService`，不是完成通知栏媒体控件体验。
- `v0.5-internal` 已完成后台播放核心链路：`MusicViewModel -> PlaybackController -> MediaController -> MediaSessionService -> ExoPlayer`。

## 当前架构

```text
Composable -> MusicViewModel -> PlaybackController -> MediaController -> MediaSessionService -> ExoPlayer
```

当前职责：

- `Composable`：显示 UI，触发 ViewModel 方法。
- `MusicViewModel`：持有歌曲列表、播放队列、当前索引，处理上一曲、下一曲、自动下一首。
- `PlaybackController`：通过 `MediaController` 控制播放，并向 ViewModel 暴露播放状态。
- `FlowtoneMediaSessionService`：当前唯一持有 `ExoPlayer` 和 `MediaSession` 的位置。
- `ExoPlayer`：在 `FlowtoneMediaSessionService` 内执行实际播放。

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
|   |-- FlowtoneMediaControllerConnection.kt
|   |-- FlowtoneMediaSessionService.kt
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

- Android 通知栏媒体控件。
- 锁屏媒体区稳定支持。
- 耳机按钮 / 蓝牙媒体按钮。
- 系统媒体控件中的上一曲 / 下一曲控制。
- 搜索。
- 歌单 / 收藏。
- 专辑封面。
- 歌词。
- 随机播放。
- 单曲循环 / 列表循环。
- 播放进度条。
- 动画和更现代的交互过渡。

## 下一阶段建议

1. 可以将当前状态作为 `v0.5-internal` 收尾状态，继续真机验证 App 内播放和后台播放核心链路。
2. 后续再处理通知栏媒体控件和系统上一曲 / 下一曲控制。
3. 通知栏稳定后，再处理后台播放生命周期策略、锁屏媒体区、耳机按钮和蓝牙媒体按钮。
4. 队列是否迁移到 `MediaSessionService` 或 ExoPlayer playlist，应在外部媒体控制验证后再决定。

## 禁止事项

- 不要在 Composable 中创建或直接操作 ExoPlayer。
- 不要在 Composable 中创建或直接操作 MediaSession。
- 不要把播放队列逻辑写进 Composable。
- 不要把 UI、权限、扫描、播放控制混在同一个文件里。
- UI 优化应保持简单，不要为了视觉效果引入复杂框架。
- 不要引入 Hilt、Room、Navigation 等额外框架，除非后续有明确需求。
- 不要一次性实现完整后台播放。
- 不要在没有分步验证前迁移播放队列所有权。
