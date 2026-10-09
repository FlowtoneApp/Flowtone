package ink.tenqui.flowtone.ui.screens

import ink.tenqui.flowtone.data.online.credential.CredentialFieldId
import ink.tenqui.flowtone.data.online.credential.CredentialGrant
import ink.tenqui.flowtone.data.online.credential.CredentialGrantEvaluation
import ink.tenqui.flowtone.data.online.credential.CredentialGrantInvalidReason
import ink.tenqui.flowtone.data.online.credential.CredentialRequestContractSnapshot
import ink.tenqui.flowtone.data.online.credential.CredentialRequestDefinition
import ink.tenqui.flowtone.data.online.credential.CredentialSecretState
import ink.tenqui.flowtone.data.online.credential.CredentialSource
import ink.tenqui.flowtone.data.online.credential.CredentialSourceMatcher
import ink.tenqui.flowtone.data.online.credential.CredentialSourceMatch
import ink.tenqui.flowtone.data.online.credential.CredentialType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CredentialManagementPresentationTest {
    private val cookieRequest = CredentialRequestDefinition(
        id = "session",
        credentialType = CredentialType.GenericAccount,
        label = "账户会话",
        realm = "music.example.com",
        genericFieldIds = listOf(CredentialFieldId.Account, CredentialFieldId.Cookie)
    )

    private fun account(
        type: CredentialType = CredentialType.GenericAccount,
        fields: Map<CredentialFieldId, String> = mapOf(CredentialFieldId.Account to "fixture"),
        states: Map<CredentialFieldId, CredentialSecretState> = emptyMap()
    ) = CredentialSource(
        id = "cs_fixture123",
        credentialType = type,
        label = "测试凭据",
        realm = if (type == CredentialType.GenericAccount) "music.example.com" else null,
        publicFields = fields,
        secretFieldStates = states
    )

    @Test
    fun listStatusDistinguishesMissingPartialAndUnavailableSecrets() {
        assertEquals("敏感信息尚未设置", credentialSourceStatus(account()))
        assertEquals(
            "已保存部分敏感信息",
            credentialSourceStatus(account(states = mapOf(CredentialFieldId.Cookie to CredentialSecretState.Configured)))
        )
        assertEquals(
            "敏感信息不可用，需重新设置",
            credentialSourceStatus(account(states = mapOf(CredentialFieldId.Cookie to CredentialSecretState.Unavailable)))
        )
        assertEquals("需要补齐公开信息", credentialSourceStatus(account(
            type = CredentialType.WebDav,
            fields = mapOf(CredentialFieldId.Endpoint to "https://example.com")
        )))
    }

    @Test
    fun candidateReasonsFollowMatcherAndNameTheMissingField() {
        val missingAccount = account(fields = emptyMap(), states = mapOf(CredentialFieldId.Cookie to CredentialSecretState.Configured))
        assertTrue(candidateReason(missingAccount, cookieRequest, CredentialSourceMatcher.match(missingAccount, cookieRequest)).contains("账号"))

        val missingCookie = account()
        assertTrue(candidateReason(missingCookie, cookieRequest, CredentialSourceMatcher.match(missingCookie, cookieRequest)).contains("Cookie"))

        val brokenCookie = account(states = mapOf(CredentialFieldId.Cookie to CredentialSecretState.Unavailable))
        assertTrue(candidateReason(brokenCookie, cookieRequest, CredentialSourceMatcher.match(brokenCookie, cookieRequest)).contains("不可用"))

        val wrongType = account(type = CredentialType.AccountPassword)
        assertTrue(candidateReason(wrongType, cookieRequest, CredentialSourceMatcher.match(wrongType, cookieRequest)).contains("类型"))

        val ready = account(states = mapOf(CredentialFieldId.Cookie to CredentialSecretState.Configured))
        assertTrue(CredentialSourceMatcher.match(ready, cookieRequest).fullyReady)
        assertTrue(candidateReason(ready, cookieRequest, CredentialSourceMatcher.match(ready, cookieRequest)).contains("可选择"))
    }

    @Test
    fun grantStatusSeparatesAuthorizationFromReadinessAndInvalidity() {
        val grant = CredentialGrant(
            extensionId = "test.extension",
            extensionInstanceId = "instance-1",
            credentialRequestId = "session",
            credentialSourceId = "cs_fixture123",
            requestContract = requireNotNull(CredentialRequestContractSnapshot.from(cookieRequest)),
            createdAtEpochMillis = 1L
        )
        assertEquals("未授权", credentialGrantStatus(CredentialGrantEvaluation(null, false)))
        assertEquals("已授权且可用", credentialGrantStatus(CredentialGrantEvaluation(
            grant, true, CredentialSourceMatch(true, true, true)
        )))
        assertEquals("已授权，凭据当前不可用", credentialGrantStatus(CredentialGrantEvaluation(
            grant, true, CredentialSourceMatch(true, true, false)
        )))
        assertEquals("请求范围已变化，需重新授权", credentialGrantStatus(CredentialGrantEvaluation(
            grant, false, invalidReason = CredentialGrantInvalidReason.RequestContractChanged
        )))
        assertEquals("扩展已重新安装，需重新授权", credentialGrantStatus(CredentialGrantEvaluation(
            grant, false, invalidReason = CredentialGrantInvalidReason.ExtensionInstallationChanged
        )))
    }
}
