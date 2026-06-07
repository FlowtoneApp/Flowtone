# MediaSessionService 迁移方案

## 当前 v0.5 收尾架构

当前播放链路：

```text
Composable -> MusicViewModel -> PlaybackController -> MediaController -> MediaSessionService -> ExoPlayer
```

当前职责：

- `Composable`：显示 UI，触发 ViewModel 方法。
- `MusicViewModel`：持有歌曲列表、播放队列、当前索引，处理上一曲、下一曲、自动下一首。
- `PlaybackController`：通过 `MediaController` 控制播放，并维护 UI 所需的播放状态。
- `FlowtoneMediaControllerConnection`：负责连接 `FlowtoneMediaSessionService` 并提供 `MediaController`。
- `FlowtoneMediaSessionService`：唯一持有 `ExoPlayer` 和 `MediaSession` 的位置。
- `ExoPlayer`：在 Service 内执行实际播放。

当前边界是清楚的：UI 不直接接触 `ExoPlayer` 或 `MediaSession`。

0.5.6 已结束临时双播放器过渡状态：

- `PlaybackController` 不再创建本地 `ExoPlayer`。
- `PlaybackController` 不再创建本地 `MediaSession`。
- 实际播放由 `FlowtoneMediaSessionService` 内部 ExoPlayer 执行。
- 队列逻辑仍保留在 `MusicViewModel`。

0.5.7 真机验证后，Flowtone 已具备后台播放核心链路：

- App 内播放正常。
- Android 系统媒体控件可以识别 Flowtone。
- 系统媒体控件暂停键可用。
- App 切到后台后播放继续。
- 从最近任务中划掉 Flowtone 后，音乐仍继续播放。
- 只有在系统“已开启的应用”中手动关闭 Flowtone 后，播放服务才会停止。
- 当前系统媒体控件还没有完整上一曲 / 下一曲体验，可能出现类似 seek to start 的按钮。

## 0.6.1 System Media Controls Investigation

本节记录 Flowtone 0.6.1 对系统媒体控件现状的代码阅读结论。本步不修改播放逻辑，不修改 UI，不迁移队列。

### 当前职责边界

- `FlowtoneMediaSessionService`：唯一持有 `ExoPlayer + MediaSession`，负责 Service 生命周期内播放器创建、会话暴露和释放。
- `FlowtoneMediaControllerConnection`：使用 `SessionToken` 指向 `FlowtoneMediaSessionService`，异步创建并释放 `MediaController`。
- `PlaybackController`：通过 `MediaController` 执行 `setMediaItem()`、`prepare()`、`play()`、`pause()`，并监听 `Player.STATE_ENDED` 通知 `MusicViewModel`。
- `MusicViewModel`：仍然持有 `playbackQueue` 和 `currentQueueIndex`，负责 App 内上一曲、下一曲、自动下一首。
- `Song.toMediaItem()`：只负责把当前 `Song` 转成带 `mediaId`、`uri`、标题、歌手 metadata 的单个 `MediaItem`。

### 当前 MediaItem / 队列状态

- `PlaybackController.play(song)` 每次只调用 `MediaController.setMediaItem(mediaItem)`。
- 当前没有调用 `setMediaItems(...)`。
- 当前没有把 `MusicViewModel.playbackQueue` 同步到 `ExoPlayer` playlist。
- 当前 `FlowtoneMediaSessionService` / ExoPlayer 只知道当前播放的单曲 `MediaItem`。
- 当前完整播放队列仍只存在于 `MusicViewModel`。

### 系统上一曲 / 下一曲不完整的判断

系统媒体控件通常依赖 MediaSession 暴露的 Player 能力、当前 timeline、playlist 以及可用命令来决定显示哪些按钮。

当前 Flowtone 对系统侧暴露的是“当前单曲”而不是“完整播放队列”，因此系统无法稳定推导：

- 当前歌曲前面是否有上一首。
- 当前歌曲后面是否有下一首。
- previous / next 应该跳到哪个媒体项。

App 内上一曲 / 下一曲仍然正常，是因为它们由 `MusicViewModel` 的 `playbackQueue + currentQueueIndex` 驱动，不依赖 ExoPlayer playlist。

### seek to start 类按钮的可能原因

当前 MediaSession / ExoPlayer 只持有单曲时，系统可能优先展示单曲级控制能力，例如暂停、播放、seek、seek to previous position 或回到开头。

当系统无法确认存在完整上一曲 / 下一曲队列时，它可能不展示稳定的 previous / next，而显示类似 seek to start 的按钮。

这与当前现象一致：暂停键可用，但上一曲 / 下一曲体验不完整。

### 后续方向

后续如果要完善系统媒体控件上一曲 / 下一曲，需要二选一或分阶段验证：

1. 将 `MusicViewModel` 队列同步到 `MediaController / ExoPlayer playlist`，让系统能看到完整 timeline。
2. 保持队列在 `MusicViewModel`，通过 MediaSession 自定义命令或回调把系统 previous / next 转回 ViewModel 队列逻辑。

保守建议：

- 先不要立即迁移完整队列所有权。
- 先设计最小验证方案，确认系统媒体控件按钮展示和命令回调行为。
- 再决定是否把队列迁移到 Service / ExoPlayer playlist。

## 0.6.2 Previous / Next 方案设计

本节记录 Flowtone 0.6 对系统媒体控件上一曲 / 下一曲的保守实现方案。本步只更新文档，不修改 Kotlin 播放逻辑，不修改 UI。

### 方案选择

0.6 采用以下方案：

```text
MusicViewModel 继续拥有业务队列
PlaybackController 同步一份队列副本给 MediaController / ExoPlayer playlist
FlowtoneMediaSessionService 继续只持有 ExoPlayer + MediaSession
```

目标不是在 0.6 彻底迁移队列所有权，而是让系统媒体控件能基于 ExoPlayer playlist 获得稳定的 previous / next 能力。

### 为什么暂时不迁移完整队列所有权到 Service

- 当前 `MusicViewModel` 已经稳定维护 `playbackQueue` 和 `currentQueueIndex`。
- App 内上一曲、下一曲、自动下一首都依赖这套逻辑。
- 直接把队列所有权迁移到 `FlowtoneMediaSessionService` 会同时影响播放控制、UI 高亮、MiniPlayer 状态、自动下一首和后续生命周期。
- 对当前阶段来说，完整迁移风险大于收益。
- 0.6 的首要目标是验证系统媒体控件 previous / next，不是重构业务队列归属。

### 为什么暂时不做 MediaSession 自定义 command 回调 ViewModel

- 自定义 command 需要把系统媒体命令从 `MediaSessionService` 回传到 App 侧 ViewModel 队列逻辑。
- 这会引入 Service 到 ViewModel 的反向通道，生命周期边界更复杂。
- App 可能在后台、前台、被划掉、重新进入等状态下接收系统命令，处理不当容易造成状态不同步。
- 对 Android 初学者维护来说，自定义命令方案更抽象，调试成本更高。
- 0.6 更适合先验证 ExoPlayer playlist 这种标准 Player timeline 方案。

### 0.6 阶段职责边界

`MusicViewModel` 继续负责：

- `playbackQueue`
- `currentQueueIndex`
- App 内上一曲
- App 内下一曲
- 当前歌曲自然结束后的自动下一首

`PlaybackController` 后续新增队列播放入口，例如：

```text
playQueue(songs, startIndex)
```

`playQueue` 的未来职责：

- 将 `List<Song>` 转为 `List<MediaItem>`。
- 调用 `MediaController.setMediaItems(mediaItems, startIndex, C.TIME_UNSET)`。
- 调用 `prepare()`。
- 调用 `play()`。

`FlowtoneMediaSessionService` 暂时仍只负责：

- 持有 `ExoPlayer`。
- 持有 `MediaSession`。
- 暴露 `MediaSession` 给系统和 `MediaController`。
- 不主动管理业务队列。

### 分步计划

#### Step 0.6.3

- 新增 `PlaybackController.playQueue(songs, startIndex)`。
- 只建立队列播放入口。
- 暂时不接 UI。
- 暂时不修改 `MusicViewModel` 点击歌曲逻辑。
- 验证 build 通过。

#### Step 0.6.4

- 让 `MusicViewModel` 点击歌曲时调用 `playQueue(songs, startIndex)`。
- 保留 `MusicViewModel` 的 `playbackQueue` 和 `currentQueueIndex`。
- 验证 App 内点击播放、上一曲、下一曲、自动下一首。
- 验证系统媒体控件是否开始展示 previous / next。

#### Step 0.6.5

- 处理系统媒体控件切歌后 App UI 状态同步。
- 重点检查当前歌曲高亮和 MiniPlayer 当前歌曲信息。
- 判断是否需要从 `MediaController` 当前 `MediaItem.mediaId` 反推 `Song`。
- 不在 0.6.5 之前处理复杂 UI 改造。

### 当前禁止事项

- 不迁移队列所有权到 Service。
- 不把业务队列直接交给 Composable。
- 不让 `MusicViewModel` 直接持有 `ExoPlayer` 或 `MediaSession`。
- 不做 MediaSession 自定义 command 回调 ViewModel。
- 不为了 previous / next 引入 Hilt、Room、Navigation 等新框架。

## 为什么 0.5 需要考虑 MediaSessionService

基础 `MediaSession` 已能让播放器具备系统媒体会话的基础结构，但它仍然跟随当前应用界面和 ViewModel 生命周期。

如果后续要稳定支持以下能力，需要引入 `MediaSessionService`：

- 后台播放
- 通知栏媒体控件
- 锁屏媒体区
- 耳机按钮 / 蓝牙媒体按钮
- 系统媒体控制器连接
- App 前台界面销毁后仍保留播放控制入口

## MediaSessionService 应负责什么

未来 `MediaSessionService` 应成为系统媒体播放入口，负责：

- 创建并持有 `ExoPlayer`
- 创建并持有 `MediaSession`
- 暴露 `MediaSession` 给系统和 `MediaController`
- 处理系统媒体控制命令
- 管理播放器释放
- 为后续通知栏媒体控件和后台播放提供基础

不建议在一开始就让它处理所有业务逻辑。队列迁移可以分阶段进行。

## ExoPlayer 所有权未来应迁移到哪里

未来 `ExoPlayer` 所有权应从 `PlaybackController` 迁移到 `MediaSessionService`。

迁移后建议结构：

```text
Composable -> MusicViewModel -> MediaController -> MediaSessionService -> ExoPlayer
```

迁移原则：

- `ExoPlayer` 仍然不能进入 Composable。
- `MusicViewModel` 不应直接持有 `ExoPlayer`。
- `MusicViewModel` 可以持有 `MediaController`，或通过轻量控制接口调用 `MediaController`。
- `MediaSessionService` 是播放器生命周期的主要所有者。

## MusicViewModel 未来是否应该直接操作 PlaybackController

长期看不建议。

当前阶段 `MusicViewModel -> PlaybackController` 是合理的，因为还没有服务化播放。

进入 0.5 后，建议逐步改为：

```text
MusicViewModel -> PlaybackClient / MediaController -> MediaSessionService
```

可以先做一个很小的控制接口，避免 ViewModel 直接依赖过多 Media3 细节。

## Activity / ViewModel 如何通过 MediaController 控制播放

未来流程建议：

1. Activity 或 ViewModel 创建 / 获取 `MediaController`。
2. `MediaController` 连接到 `MediaSessionService`。
3. ViewModel 调用 `MediaController.setMediaItem()`、`prepare()`、`play()`、`pause()` 等。
4. UI 继续只观察 ViewModel 暴露的状态。
5. 状态可以来自 `MediaController` 的 player events，再映射成现有 `PlaybackState`。

注意：不要让 Composable 直接持有 `MediaController`。

## 队列逻辑如何处理

保守建议：先留在 `MusicViewModel`，后续逐步迁移。

原因：

- 当前队列逻辑已经能工作。
- 一次性迁移到 ExoPlayer playlist 风险较大。
- 后台播放、通知栏、锁屏控制本身已经有足够复杂度。

推荐路线：

1. 0.5 初期：队列仍在 ViewModel，Service 只负责播放器和 MediaSession。
2. 稳定后：评估是否把队列同步到 MediaSession / ExoPlayer playlist。
3. 如果要支持系统上一曲 / 下一曲按钮，应让 MediaSession 的命令能回到统一队列逻辑。
4. 最终是否迁移到 ExoPlayer playlist，需要等通知栏和耳机按钮行为验证后再决定。

## 推荐迁移顺序

### Step 0.5.2：新增 MediaSessionService 骨架

状态：已完成。

目标：

- 新增 `FlowtoneMediaSessionService`。
- 该类继承 `androidx.media3.session.MediaSessionService`。
- 在 Manifest 注册服务和 `androidx.media3.session.MediaSessionService` action。
- `onGetSession()` 暂时返回 `null`。
- 不在 Service 中创建 `ExoPlayer`。
- 不在 Service 中创建 `MediaSession`。
- 不迁移现有播放逻辑。
- 不改 UI。

风险点：

- Manifest 配置错误导致服务不可连接。
- 后续如果过早接管播放，可能引入生命周期问题。

验收标准：

- App build 成功。
- App 内现有播放行为不变。
- 0.5.2 当时播放仍然由 `PlaybackController` 内部 ExoPlayer 完成。
- Service 只是骨架，不承担核心播放。

### Step 0.5.3：将 ExoPlayer / MediaSession 所有权迁移到 Service

状态：部分完成。

当前 0.5.3 已完成：

- Service 已在 `onCreate()` 中创建内部 `ExoPlayer`。
- Service 已在 `onCreate()` 中创建内部 `MediaSession`。
- `onGetSession()` 已返回 Service 内部 `MediaSession`。
- Service 已在 `onDestroy()` 中释放 `MediaSession` 和 `ExoPlayer`，并将引用置空。

当前 0.5.3 尚未完成完整迁移：

- App 内实际播放仍由 `PlaybackController` 完成。
- `MusicViewModel` 尚未通过 `MediaController` 控制 Service。
- 队列所有权仍在 `MusicViewModel`。
- 尚未把队列改成 ExoPlayer playlist。

目标：

- 完成 Service 创建并持有 `ExoPlayer`。
- 完成 Service 创建并持有 `MediaSession`。
- 后续步骤让 `PlaybackController` 不再拥有播放器，或被改造成 Service 内部控制器。

风险点：

- 播放器生命周期变化导致播放中断。
- ViewModel 清理时不应错误释放 Service 内播放器。
- 现有 `PlaybackState` 可能需要重新映射。

验收标准：

- 当前阶段 App build 成功。
- 当前阶段 App 内现有播放行为不变。
- 当前阶段 App 前后台切换不崩溃。
- 后续完整迁移后，点击歌曲、播放 / 暂停、上一曲 / 下一曲、自动下一首都应正常。

### Step 0.5.4：ViewModel 通过 MediaController 控制播放

状态：准备层已完成，控制迁移尚未开始。

当前 0.5.4 已完成：

- 已新增 `FlowtoneMediaControllerConnection`。
- 该连接类使用 `SessionToken` 指向 `FlowtoneMediaSessionService`。
- 该连接类异步创建 `MediaController`。
- 该连接类保存 controller 引用。
- 该连接类提供 `release()` 释放 controller future / controller。
- `PlaybackController` 当前只持有并释放该连接类。

当前 0.5.4 尚未完成完整控制迁移：

- `play(song)` 尚未切到 `MediaController`。
- `pause()` / `play()` / `togglePlayPause()` 尚未切到 `MediaController`。
- 上一曲 / 下一曲 / 自动下一首仍由 `MusicViewModel` 和现有 `PlaybackController` 播放器完成。
- `MusicViewModel` 不直接持有 `MediaController`。
- Composable 不接触 `MediaController`。

目标：

- ViewModel 不直接调用旧 `PlaybackController`。
- ViewModel 通过 `MediaController` 或轻量控制接口控制播放。
- UI 仍然只调用 ViewModel。

风险点：

- MediaController 连接是异步的。
- Controller 未连接时的点击行为需要处理。
- 状态同步可能出现延迟。

验收标准：

- 当前阶段 App build 成功。
- 当前阶段 App 内现有播放行为不变。
- 当前阶段 Controller 连接失败时 App 不崩溃。
- 后续完整迁移后，Controller 连接成功时所有 App 内控制正常。
- 后续完整迁移后，Controller 未连接时不崩溃。
- 后续完整迁移后，UI 状态和播放状态基本一致。

### Step 0.5.4.1：修复临时双 MediaSession 的 session id 冲突

状态：已完成。

已完成内容：

- `PlaybackController` 内的过渡期 `MediaSession` 设置显式 id：`flowtone_local_transition_session`。
- `FlowtoneMediaSessionService` 内的 `MediaSession` 设置显式 id：`flowtone_service_session`。
- 修复两个 `MediaSession` 都使用默认空 id 时触发的 `Session ID must be unique` 崩溃。
- 当前播放控制仍然没有切到 `MediaController`。
- 0.5.4.1 当时实际播放仍由 `PlaybackController` 内部 ExoPlayer 完成。

过渡期约束：

- 只要 App 内同时存在多个 `MediaSession`，就必须保证 session id 唯一。
- 后续 0.5.5 迁移播放控制后，应移除 `PlaybackController` 内旧播放器 / 旧 Session 所有权。
- 不应长期保留双播放器 / 双 Session / 双控制架构。

验收标准：

- App build 成功。
- 真机启动不再因 `Session ID must be unique` 崩溃。
- App 内播放、暂停、上一曲、下一曲、自动下一首行为不变。

### Step 0.5.5：补齐媒体播放前台服务 Manifest 声明

状态：已完成。

目标：

- 在实际迁移播放前，让 Manifest 先符合未来媒体播放前台服务要求。
- 添加 `android.permission.FOREGROUND_SERVICE`。
- 添加 `android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK`。
- 为 `FlowtoneMediaSessionService` 添加 `android:foregroundServiceType="mediaPlayback"`。
- 保留 `androidx.media3.session.MediaSessionService` intent action。
- 不新增 `POST_NOTIFICATIONS` 权限，通知权限留到通知栏阶段处理。
- 不迁移现有播放逻辑。
- 不新增通知栏媒体控件。
- 不新增后台播放。

风险点：

- Android 版本对前台服务类型和权限要求不同。
- 如果后续真正启动前台服务，还需要严格处理通知和服务生命周期。
- 本步只是声明准备，不代表已经具备后台播放能力。

验收标准：

- App build 成功。
- App 内现有播放行为不变。
- 0.5.5 当时实际播放仍由 `PlaybackController` 内部 ExoPlayer 完成。
- UI 肉眼表现不变。

### Step 0.5.6：处理通知栏媒体控件

状态：播放迁移已完成，通知栏媒体控件尚未开始。

当前 0.5.6 已完成：

- `PlaybackController` 的实际播放控制已迁移到 `MediaController`。
- `play(song)` 会通过 `Song.toMediaItem()` 得到 `MediaItem`。
- `play(song)` 通过 `MediaController.setMediaItem()`、`prepare()`、`play()` 播放。
- `pause()` 通过 `MediaController.pause()` 执行。
- `play()` / 恢复播放通过 `MediaController.play()` 执行。
- 播放结束监听迁移到 `MediaController` 的 `Player.Listener`。
- `Player.STATE_ENDED` 时仍调用现有 `onPlaybackEnded`，让 `MusicViewModel` 继续负责自动下一首。
- 如果 `MediaController` 尚未连接完成，`PlaybackController` 会暂存最近一次要播放的歌曲，并在连接完成后播放。
- 双播放器过渡状态已结束。

当前仍未完成：

- 尚未实现通知栏媒体控件。
- 当时尚未完成后台播放真机验证。
- 尚未迁移队列所有权。
- 尚未把队列改成 ExoPlayer playlist。

目标：

- 基于 MediaSessionService 支持通知栏媒体控件。
- 验证标题、歌手、播放 / 暂停显示正确。

风险点：

- Android 版本差异。
- 通知权限和前台服务要求。
- 通知生命周期处理不当。

验收标准：

- 当前阶段 App build 成功。
- 当前阶段 App 内播放、暂停、上一曲、下一曲、自动下一首正常。
- 当前阶段 `FlowtoneMediaSessionService` 是唯一持有 `ExoPlayer` 和 `MediaSession` 的位置。
- 前台播放时通知栏媒体控件可见。
- 播放 / 暂停可控制。
- 标题 / 歌手正确。
- 不影响 App 内 MiniPlayer。

### Step 0.5.7：记录 Service 播放迁移后的系统媒体行为

状态：已完成验证记录，并收尾 `v0.5-internal`。

目标：

- 记录 0.5.6 播放迁移到 `MediaController -> MediaSessionService` 后的系统媒体表现。
- 检查 `PlaybackController`、`FlowtoneMediaControllerConnection` 和 `FlowtoneMediaSessionService` 的生命周期边界。
- 不新增通知栏 UI。
- 不自定义系统媒体按钮。
- 不迁移队列所有权。

已检查内容：

- `PlaybackController.release()` 不直接释放 Service 内 ExoPlayer。
- `FlowtoneMediaSessionService.onDestroy()` 会释放 `MediaSession` 和 `ExoPlayer`。
- `Player.STATE_ENDED` 仍通过 `PlaybackController` 的 `Player.Listener` 通知 `MusicViewModel` 自动下一首。
- `pendingSong` 逻辑保持简单，只暂存最近一次播放请求。
- `PlaybackController.release()` 会清空 `pendingSong`。

真机观察：

- App 内播放正常。
- Android 系统媒体控件能够识别 Flowtone 的播放会话。
- 系统媒体控件中的暂停键可用。
- App 切到后台后播放继续。
- 从最近任务中划掉 Flowtone 后，音乐仍继续播放。
- 只有在系统“已开启的应用”中手动关闭 Flowtone 后，播放服务才会停止。
- 系统媒体控件暂未完成上一曲 / 下一曲控制。
- 当前可能出现类似 seek to start 的按钮。
- 该现象可能与当前队列仍由 `MusicViewModel` 管理，而 `MediaSessionService` / ExoPlayer 只持有当前单曲 `MediaItem` 有关。

结论：

- 该现象不阻塞 `v0.5-internal`。
- `v0.5-internal` 的目标是完成播放核心迁移到 `MediaSessionService`，并验证后台播放核心链路。
- `v0.5-internal` 已完成核心链路：`MusicViewModel -> PlaybackController -> MediaController -> MediaSessionService -> ExoPlayer`。
- 通知栏媒体控件体验、系统上一曲 / 下一曲、队列同步应进入后续系统媒体控件 / 队列同步阶段处理。

风险点：

- 外部上一曲 / 下一曲与当前 ViewModel 队列逻辑尚未打通。
- 如果后续要让系统媒体控件完整展示上一曲 / 下一曲，可能需要同步队列或自定义 MediaSession 命令。
- 通知栏、锁屏和耳机按钮行为仍需后续真机验证。

验收标准：

- App build 成功。
- App 内播放、暂停、上一曲、下一曲、自动下一首正常。
- 最后一首结束后仍然停止，不循环。
- `FlowtoneMediaSessionService` 仍是唯一持有 `ExoPlayer` 和 `MediaSession` 的位置。
- 后台播放核心链路已通过真机验证。

### Step 0.5.8：系统媒体控件和外部按钮验证

目标：

- 继续细化后台播放生命周期策略。
- 验证锁屏媒体区。
- 验证耳机按钮 / 蓝牙媒体按钮。
- 处理系统媒体控件上一曲 / 下一曲。

风险点：

- 前台服务类型和权限。
- App 被划掉后的预期行为。
- 外部上一曲 / 下一曲与当前队列逻辑不一致。

验收标准：

- 后台播放生命周期策略稳定。
- 锁屏媒体区显示正确。
- 耳机或蓝牙播放 / 暂停可用。
- App 退出行为符合预期。

## 结论

不建议一次性完成完整系统媒体体验。

推荐保守路线：

1. 先引入 `MediaSessionService` 骨架。
2. 再迁移 `ExoPlayer` 和 `MediaSession` 所有权。
3. 再让 UI 层通过 ViewModel / MediaController 控制播放。
4. 再处理通知栏媒体控件。
5. 最后处理后台播放生命周期策略、锁屏和耳机 / 蓝牙按钮。

每一步都应保持项目可编译，并手动验证现有播放行为不回退。
