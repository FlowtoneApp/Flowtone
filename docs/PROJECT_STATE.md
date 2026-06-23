# Flowtone 项目状态

## 当前阶段

Flowtone 当前进入 0.8 收尾阶段。

0.8 主题：**Online-Ready Refactor**。

本阶段目标是整理项目结构和核心边界，为未来在线音乐来源接入做准备，但当前仍然只支持本地音乐播放，未接入任何真实在线 API。

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

## 当前播放架构

```text
Composable -> MusicViewModel -> PlaybackController -> MediaController -> FlowtoneMediaSessionService -> ExoPlayer
```

当前职责：

- `Composable`：显示 UI，触发 `MusicViewModel` 方法。
- `MusicViewModel`：持有歌曲列表、播放队列、当前索引，处理上一曲、下一曲、自动下一首和 UI 状态组装。
- `PlaybackController`：通过 `MediaController` 控制播放，并维护播放状态。
- `FlowtoneMediaSessionService`：唯一持有 `ExoPlayer + MediaSession` 的位置。
- `ExoPlayer`：在 `FlowtoneMediaSessionService` 内执行实际播放。

架构边界：

- Composable 不直接接触 `ExoPlayer`。
- Composable 不直接接触 `MediaSession`。
- Composable 不直接接触 `MediaController`。
- `FlowtoneMediaSessionService` 是唯一持有 `ExoPlayer + MediaSession` 的地方。
- `PlaybackController` 通过 `MediaController` 控制播放。

## Flowtone 0.8 已完成

### 0.8.1：Package 结构整理

- 已整理项目 package 结构。
- 应用入口移动到 `app`。
- 核心模型移动到 `core/model`。
- 本地扫描相关代码移动到 `data/local`。
- 播放器 UI 移动到 `ui/player`。
- 列表页移动到 `ui/library`。
- 播放控制链路保持不变。

### 0.8.2：SourceType

- 已新增 `SourceType`：
  - `Local`
  - `Online`
- `Song` 已新增 `sourceType` 字段。
- 当前本地扫描得到的歌曲统一设置为 `SourceType.Local`。
- 当前仍未创建在线 `Song`。

### 0.8.3：Repository 层

- 已新增 `LocalMusicRepository`。
- 已新增 `MusicRepository`。
- 当前本地歌曲加载链路：

```text
MusicViewModel
-> MusicRepository.loadLocalSongs()
-> LocalMusicRepository.loadSongs()
-> AudioScanner.scanSongs()
-> MediaStore
```

- `MusicViewModel` 不再直接调用 `AudioScanner.scanSongs()`。

### 0.8.4：MediaItemMapper

- 已新增 `MediaItemMapper`。
- `Song -> MediaItem` 映射集中到 `MediaItemMapper.toMediaItem(song)`。
- `MediaItem -> Song` 还原集中到 `MediaItemMapper.toSongOrNull(mediaItem, scannedSongs)`。
- `SongMediaItem.kt` 保留为兼容扩展入口，内部委托给 `MediaItemMapper`。
- 当前本地歌曲仍然使用现有 `Song.uri` 播放。
- `MediaItem` 还原 `Song` 时，兜底 `sourceType` 仍为 `SourceType.Local`。

### 0.8.5：PlaybackController 整理

- 已整理 `PlaybackController` 内部重复逻辑。
- pending 单曲和 pending 队列请求已收敛为内部 `PendingPlaybackRequest`。
- 已保留播放控制入口：
  - `play(song)`
  - `playQueue(songs, index)`
  - `pause()`
  - `resume()`
  - `skipToNext()`
  - `skipToPrevious()`
  - `seekTo(positionMs)`
- 兼容入口仍保留：
  - `play()`
  - `playNext(playWhenReady)`
  - `playPrevious(playWhenReady)`
- controller 未连接时等待可用后继续播放的行为保持不变。
- `onMediaItemTransition` 与 `currentSong` 同步逻辑保持不变。

### 0.8.6：PlayerUiState

- 已新增 `PlayerUiState`。
- `MiniPlayer` 优先接收 `PlayerUiState`，减少直接传递分散播放器状态。
- `PlayerUiState` 当前字段：
  - `currentSong`
  - `isPlaying`
  - `positionMs`
  - `durationMs`
  - `artworkUri`
  - `playbackOrderMode`
  - `hasCurrentSong`
  - `canPlay`
- UI 表现、动画、进度条和播放行为保持不变。

### 0.8.7：MusicProvider 抽象

- 已新增 `MusicProvider` 接口。
- 已新增 `ProviderSong` 数据模型。
- 已新增 `NoopMusicProvider` 空实现。
- `MusicRepository` 默认持有 `NoopMusicProvider`。
- 已预留 `MusicRepository.searchOnlineSongs(keyword)`。
- 当前在线搜索入口返回空结果。
- 当前未接入任何真实在线 Provider。

## 当前文件结构概览

```text
app/src/main/java/ink/tenqui/flowtone/
|-- app/
|   |-- FlowtoneApp.kt
|   `-- MainActivity.kt
|-- core/
|   `-- model/
|       |-- Song.kt
|       `-- SourceType.kt
|-- data/
|   |-- local/
|   |   |-- AudioScanner.kt
|   |   |-- LocalMusicRepository.kt
|   |   `-- PlaybackSettingsStore.kt
|   |-- online/
|   |   |-- MusicProvider.kt
|   |   |-- NoopMusicProvider.kt
|   |   `-- ProviderSong.kt
|   `-- repository/
|       `-- MusicRepository.kt
|-- permissions/
|   `-- AudioPermission.kt
|-- playback/
|   |-- FlowtoneMediaControllerConnection.kt
|   |-- FlowtoneMediaSessionService.kt
|   |-- MediaItemMapper.kt
|   |-- PlaybackController.kt
|   |-- PlaybackOrderMode.kt
|   |-- PlaybackState.kt
|   `-- SongMediaItem.kt
|-- ui/
|   |-- components/
|   |   `-- SongListItem.kt
|   |-- library/
|   |   `-- LibraryScreen.kt
|   |-- player/
|   |   |-- MiniPlayer.kt
|   |   `-- PlayerUiState.kt
|   `-- theme/
|       |-- Color.kt
|       |-- Theme.kt
|       `-- Type.kt
`-- viewmodel/
    `-- MusicViewModel.kt
```

## 当前已完成能力

- 音频读取权限请求。
- 本地音乐扫描。
- 本地歌曲列表展示。
- 点击歌曲播放。
- 播放队列。
- 上一首 / 下一首。
- 暂停 / 继续。
- 后台播放。
- Android 系统媒体控件。
- 系统媒体控件上一首 / 下一首。
- 播放顺序：
  - Sequence
  - RepeatOne
  - Shuffle
- MiniPlayer 展开 / 收起。
- MiniPlayer 展开态封面、背景、进度条、时间文本和播放控件。
- 真实播放进度和 seek。

## 当前仍未实现

- 真实在线音乐 Provider。
- 在线 API。
- 网络请求。
- 登录。
- Cookie / Header / MUSIC_U。
- 在线搜索 UI。
- 在线播放 URL 获取。
- 缓存。
- 下载。
- 歌词。
- 歌单系统。
- 收藏持久化。

## 禁止事项

- 不要在 Composable 中创建或直接操作 `ExoPlayer`。
- 不要在 Composable 中创建或直接操作 `MediaSession`。
- 不要在 Composable 中创建或直接操作 `MediaController`。
- 不要把播放队列逻辑写进 Composable。
- 不要让 `FlowtoneMediaSessionService` 之外的类持有 `ExoPlayer + MediaSession`。
- 不要引入 Hilt、Room、Navigation，除非后续有明确需求。
- 不要在没有分步验证前接入真实在线 API。
- 不要在 0.8 收尾阶段新增功能。

## 下一阶段建议

1. 可以基于当前状态打 0.8 内部里程碑。
2. 后续如果进入在线音乐方向，先从一个 Noop 之外的实验 Provider 开始，不要直接改播放核心。
3. 在线歌曲接入播放前，需要先明确 `ProviderSong -> Song` 或 `ProviderSong -> MediaItem` 的转换边界。
