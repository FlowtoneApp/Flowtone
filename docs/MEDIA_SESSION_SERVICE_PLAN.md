# MediaSessionService 迁移方案

## 当前 v0.4 架构

当前播放链路：

```text
Composable -> MusicViewModel -> PlaybackController -> ExoPlayer
```

当前职责：

- `Composable`：显示 UI，触发 ViewModel 方法。
- `MusicViewModel`：持有歌曲列表、播放队列、当前索引，处理上一曲、下一曲、自动下一首。
- `PlaybackController`：私有持有 `ExoPlayer` 和基础 `MediaSession`，负责播放、暂停、释放播放器。
- `ExoPlayer`：执行实际播放。

当前边界是清楚的：UI 不直接接触 `ExoPlayer` 或 `MediaSession`。

## 为什么 0.5 要考虑 MediaSessionService

基础 `MediaSession` 已能让播放器具备系统媒体会话的基础结构，但它仍然跟随当前应用进程和 ViewModel 生命周期。

如果后续要稳定支持以下能力，需要引入 `MediaSessionService`：

- 后台播放
- 通知栏媒体控件
- 锁屏媒体区
- 耳机按钮 / 蓝牙媒体按钮
- 系统媒体控制器连接
- App 前台界面销毁后仍保持播放控制入口

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
- `MusicViewModel` 可以持有 `MediaController` 或通过轻量控制接口调用 `MediaController`。
- `MediaSessionService` 是播放器生命周期的主要所有者。

## MusicViewModel 未来是否应直接操作 PlaybackController

长期看不建议。

当前阶段 `MusicViewModel -> PlaybackController` 是合理的，因为还没有服务化播放。

进入 0.5 后，建议逐步改为：

```text
MusicViewModel -> PlaybackClient/MediaController -> MediaSessionService
```

可以先做一个很小的控制接口，避免 ViewModel 直接依赖 Media3 细节过多。

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

目标：

- 新增 `MediaSessionService` 类。
- 在 Manifest 注册服务。
- 不迁移现有播放逻辑。
- 不改 UI。

风险点：

- Manifest 配置错误导致服务不可连接。
- Service 生命周期不清楚。

验收标准：

- App build 成功。
- App 内现有播放行为不变。
- Service 可创建但不承担核心播放。

### Step 0.5.3：将 ExoPlayer / MediaSession 所有权迁移到 Service

目标：

- Service 创建并持有 `ExoPlayer`。
- Service 创建并持有 `MediaSession`。
- `PlaybackController` 不再拥有播放器，或被改造成 Service 内部控制器。

风险点：

- 播放器生命周期变化导致播放中断。
- ViewModel 清理时不应错误释放 Service 内播放器。
- 现有 `PlaybackState` 可能需要重新映射。

验收标准：

- 点击歌曲播放正常。
- 播放 / 暂停正常。
- 上一曲 / 下一曲正常。
- 自动下一首正常。
- App 前后台切换不崩溃。

### Step 0.5.4：ViewModel 通过 MediaController 控制播放

目标：

- ViewModel 不直接调用旧 `PlaybackController`。
- ViewModel 通过 `MediaController` 或轻量控制接口控制播放。
- UI 仍然只调用 ViewModel。

风险点：

- MediaController 连接是异步的。
- Controller 未连接时的点击行为需要处理。
- 状态同步可能出现延迟。

验收标准：

- Controller 连接成功后，所有 App 内控制正常。
- Controller 未连接时不崩溃。
- UI 状态和播放状态基本一致。

### Step 0.5.5：处理通知栏媒体控件

目标：

- 基于 MediaSessionService 支持通知栏媒体控件。
- 验证标题、歌手、播放 / 暂停显示正确。

风险点：

- Android 版本差异。
- 通知权限和前台服务要求。
- 通知生命周期处理不当。

验收标准：

- 前台播放时通知栏媒体控件可见。
- 播放 / 暂停可控制。
- 标题 / 歌手正确。
- 不影响 App 内 MiniPlayer。

### Step 0.5.6：后台播放和外部按钮验证

目标：

- 验证后台播放。
- 验证锁屏媒体区。
- 验证耳机按钮 / 蓝牙媒体按钮。

风险点：

- 前台服务类型和权限。
- App 被划掉后的预期行为。
- 外部上一曲 / 下一曲与当前队列逻辑不一致。

验收标准：

- 后台播放稳定。
- 锁屏媒体区显示正确。
- 耳机或蓝牙播放 / 暂停可用。
- App 退出行为符合预期。

## 结论

不建议一次性完成完整后台播放。

推荐保守路线：

1. 先引入 `MediaSessionService` 骨架。
2. 再迁移 `ExoPlayer` 和 `MediaSession` 所有权。
3. 再让 UI 层通过 ViewModel / MediaController 控制播放。
4. 再处理通知栏媒体控件。
5. 最后处理后台播放、锁屏和耳机 / 蓝牙按钮。

每一步都应保持项目可编译，并手动验证现有播放行为不回退。
