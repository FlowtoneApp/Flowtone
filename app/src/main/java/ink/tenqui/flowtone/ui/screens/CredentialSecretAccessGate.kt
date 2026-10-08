package ink.tenqui.flowtone.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ink.tenqui.flowtone.data.online.credential.CredentialFieldId

internal data class CredentialSecretAccessRequest(
    val token: Long,
    val sourceId: String,
    val fieldId: CredentialFieldId
)

/** Keeps one system-auth request scoped to its source and field. */
internal class CredentialSecretAccessGate {
    private enum class Phase { Authenticating, Reading }

    private data class ActiveRequest(
        val request: CredentialSecretAccessRequest,
        val phase: Phase
    )

    private var active by mutableStateOf<ActiveRequest?>(null)
    private var nextToken = 0L
    private var closed = false

    val isBusy: Boolean
        get() = active != null

    val activeRequest: CredentialSecretAccessRequest?
        get() = active?.request

    fun begin(sourceId: String, fieldId: CredentialFieldId): CredentialSecretAccessRequest? {
        if (closed || active != null) return null
        return CredentialSecretAccessRequest(++nextToken, sourceId, fieldId).also { request ->
            active = ActiveRequest(request, Phase.Authenticating)
        }
    }

    fun canCompleteAuthentication(
        request: CredentialSecretAccessRequest,
        currentSourceId: String
    ): Boolean = !closed && active?.let {
        it.request == request &&
            it.phase == Phase.Authenticating &&
            request.sourceId == currentSourceId
    } == true

    fun authorizeRead(
        request: CredentialSecretAccessRequest,
        currentSourceId: String
    ): Boolean {
        if (!canCompleteAuthentication(request, currentSourceId)) return false
        active = ActiveRequest(request, Phase.Reading)
        return true
    }

    fun finishRead(request: CredentialSecretAccessRequest) {
        if (active?.request == request && active?.phase == Phase.Reading) active = null
    }

    fun cancelRead(request: CredentialSecretAccessRequest) {
        if (active?.request == request && active?.phase == Phase.Reading) active = null
    }

    fun cancel(request: CredentialSecretAccessRequest) {
        if (active?.request == request) active = null
    }

    fun cancelAll() {
        nextToken++
        active = null
    }

    fun close() {
        closed = true
        cancelAll()
    }
}
