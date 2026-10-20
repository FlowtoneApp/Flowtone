package ink.tenqui.flowtone.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ink.tenqui.flowtone.data.online.credential.CredentialFieldId
import ink.tenqui.flowtone.data.online.credential.CredentialSecretFailure
import ink.tenqui.flowtone.data.online.credential.CredentialSecretReadResult
import kotlinx.coroutines.CancellationException

/** 仅在当前编辑页存活的主动查看状态；隐藏时使未完成的读取结果失效。 */
internal class CredentialSecretRevealState {
    var field by mutableStateOf<CredentialFieldId?>(null)
        private set
    var result by mutableStateOf<CredentialSecretReadResult?>(null)
        private set
    var loading by mutableStateOf(false)
        private set

    private var generation = 0L
    private var closed = false

    suspend fun reveal(
        fieldId: CredentialFieldId,
        read: suspend (CredentialFieldId) -> CredentialSecretReadResult
    ) {
        if (closed) return
        val request = ++generation
        field = fieldId
        result = null
        loading = true
        val loaded = try {
            read(fieldId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            CredentialSecretReadResult.Unavailable(CredentialSecretFailure.StorageUnavailable)
        }
        if (!closed && generation == request && field == fieldId) {
            result = loaded
            loading = false
        }
    }

    fun hide() {
        generation++
        field = null
        result = null
        loading = false
    }

    fun close() {
        closed = true
        hide()
    }
}
