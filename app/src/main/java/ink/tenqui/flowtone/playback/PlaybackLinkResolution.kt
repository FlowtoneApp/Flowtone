package ink.tenqui.flowtone.playback

import kotlinx.coroutines.CancellationException

internal const val PlaybackLinkFailureMessage = "获取播放链接失败"

/** 链接解析异常只用于诊断，不能进入面向用户的错误详情。 */
internal class PlaybackLinkResolutionFailure(cause: Throwable) :
    Exception(PlaybackLinkFailureMessage, cause)

internal suspend fun <T : Any> resolvePlaybackLink(resolve: suspend () -> T?): T = try {
    resolve() ?: throw IllegalStateException("扩展未返回有效的播放资源")
} catch (error: CancellationException) {
    throw error
} catch (error: Throwable) {
    throw PlaybackLinkResolutionFailure(error)
}

internal fun playbackFailureDetails(error: Throwable): String =
    if (error is PlaybackLinkResolutionFailure) PlaybackLinkFailureMessage
    else error.stackTraceToString()
