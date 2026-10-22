package ink.tenqui.flowtone.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ink.tenqui.flowtone.BuildConfig
import ink.tenqui.flowtone.data.update.AppRelease
import ink.tenqui.flowtone.data.update.AppVersion
import ink.tenqui.flowtone.data.update.GitHubReleaseRepository
import ink.tenqui.flowtone.data.update.UpdateCheckException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal enum class UpdateVersionStatus { Latest, Available, Ahead, Unknown }

internal data class AppUpdateState(
    val checking: Boolean = false,
    val release: AppRelease? = null,
    val versionStatus: UpdateVersionStatus = UpdateVersionStatus.Unknown,
    val error: String? = null
)

internal class AppUpdateViewModel : ViewModel() {
    val currentVersion: String = BuildConfig.VERSION_NAME
    private val repository = GitHubReleaseRepository()
    private val mutableState = MutableStateFlow(AppUpdateState())
    val state = mutableState.asStateFlow()
    private var request: Job? = null

    fun checkForUpdates() {
        if (request?.isActive == true) return
        request = viewModelScope.launch {
            mutableState.value = mutableState.value.copy(checking = true, error = null)
            try {
                val release = repository.latestRelease()
                val current = AppVersion.parse(currentVersion)
                val status = when {
                    current == null -> UpdateVersionStatus.Unknown
                    current < release.version -> UpdateVersionStatus.Available
                    current > release.version -> UpdateVersionStatus.Ahead
                    else -> UpdateVersionStatus.Latest
                }
                mutableState.value = AppUpdateState(release = release, versionStatus = status)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableState.value = mutableState.value.copy(
                    checking = false,
                    error = when (error) {
                        is UpdateCheckException -> error.message
                        is java.net.SocketTimeoutException -> "连接超时，请稍后重试"
                        is java.io.IOException -> "无法连接 GitHub，请检查网络后重试"
                        else -> "无法读取更新信息，请稍后重试"
                    }
                )
            }
        }
    }
}
