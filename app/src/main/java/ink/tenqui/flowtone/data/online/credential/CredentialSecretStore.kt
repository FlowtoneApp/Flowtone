package ink.tenqui.flowtone.data.online.credential

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.GeneralSecurityException
import java.security.InvalidKeyException
import java.security.KeyStore
import java.util.UUID
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

sealed interface CredentialSecretReadResult {
    data class Available(val plaintext: String) : CredentialSecretReadResult {
        override fun toString(): String = "Available([redacted])"
    }
    data object NotConfigured : CredentialSecretReadResult
    data class Unavailable(val reason: CredentialSecretFailure) : CredentialSecretReadResult
}

enum class CredentialSecretFailure {
    KeyUnavailable,
    InvalidEnvelope,
    AuthenticationFailed,
    StorageUnavailable,
    UnsupportedField,
    InvalidInput
}

class CredentialSecretStoreException(
    val reason: CredentialSecretFailure,
    cause: Throwable? = null
) : Exception("Credential Secret storage operation failed", cause)

/** Secret 密文只存于 noBackupFilesDir；Host 定义的普通字段不会被接受。 */
class CredentialSecretStore internal constructor(
    private val root: File,
    private val keyProvider: CredentialSecretKeyProvider = AndroidCredentialSecretKeyProvider()
) {
    fun put(sourceId: String, fieldId: CredentialFieldId, plaintext: String) {
        if (plaintext.isEmpty()) throw CredentialSecretStoreException(CredentialSecretFailure.InvalidInput)
        try {
            val target = secretFile(sourceId, fieldId)
            var cipher = Cipher.getInstance(Transformation)
            try {
                cipher.init(Cipher.ENCRYPT_MODE, keyProvider.getOrCreateKey())
            } catch (_: InvalidKeyException) {
                // 只在用户明确写入新值时轮换不可用 key；既有密文仍会以 Unavailable 表示。
                cipher = Cipher.getInstance(Transformation)
                cipher.init(Cipher.ENCRYPT_MODE, keyProvider.recreateKey())
            }
            val iv = cipher.iv ?: throw GeneralSecurityException()
            cipher.updateAAD(aad(sourceId, fieldId))
            val plaintextBytes = plaintext.toByteArray(StandardCharsets.UTF_8)
            try {
                val ciphertext = cipher.doFinal(plaintextBytes)
                try {
                    val encoded = encodeEnvelope(iv, ciphertext)
                    target.parentFile?.let(::ensureDirectory)
                    atomicWrite(target, encoded)
                } finally {
                    ciphertext.fill(0)
                }
            } finally {
                plaintextBytes.fill(0)
            }
        } catch (e: CredentialSecretStoreException) {
            throw e
        } catch (e: Exception) {
            throw CredentialSecretStoreException(CredentialSecretFailure.StorageUnavailable, e)
        }
    }

    fun get(sourceId: String, fieldId: CredentialFieldId): CredentialSecretReadResult {
        val file = try {
            secretFile(sourceId, fieldId)
        } catch (e: Exception) {
            return CredentialSecretReadResult.Unavailable(failureReason(e))
        }
        return try {
            if (!file.exists()) return CredentialSecretReadResult.NotConfigured
            val bytes = decrypt(file.readBytes(), sourceId, fieldId)
            try {
                CredentialSecretReadResult.Available(String(bytes, StandardCharsets.UTF_8))
            } finally {
                bytes.fill(0)
            }
        } catch (e: SecretReadException) {
            CredentialSecretReadResult.Unavailable(e.reason)
        } catch (_: Exception) {
            CredentialSecretReadResult.Unavailable(CredentialSecretFailure.StorageUnavailable)
        }
    }

    /** 检查密文是否存在且可认证解密，不会把明文构造成 String。 */
    fun state(sourceId: String, fieldId: CredentialFieldId): CredentialSecretState {
        val file = try {
            secretFile(sourceId, fieldId)
        } catch (_: Exception) {
            return CredentialSecretState.Unavailable
        }
        return try {
            if (!file.exists()) return CredentialSecretState.NotConfigured
            val bytes = decrypt(file.readBytes(), sourceId, fieldId)
            bytes.fill(0)
            CredentialSecretState.Configured
        } catch (_: Exception) {
            CredentialSecretState.Unavailable
        }
    }

    /** 只表示密文文件存在；损坏或无法解密时仍为 true，状态请使用 state()。 */
    fun exists(sourceId: String, fieldId: CredentialFieldId): Boolean =
        runCatching { secretFile(sourceId, fieldId).isFile }.getOrDefault(false)

    fun delete(sourceId: String, fieldId: CredentialFieldId) {
        val file = secretFile(sourceId, fieldId)
        try {
            Files.deleteIfExists(file.toPath())
            deleteTemporaryFiles(file)
            removeEmptyParent(file.parentFile)
        } catch (e: Exception) {
            throw CredentialSecretStoreException(CredentialSecretFailure.StorageUnavailable, e)
        }
    }

    fun deleteAll(sourceId: String) {
        validateSourceId(sourceId)
        val directory = sourceDirectory(sourceId)
        if (!directory.exists()) return
        try {
            if (!directory.isDirectory) throw GeneralSecurityException()
            CredentialFieldDefinitions.all
                .filter(CredentialFieldDefinition::isSensitive)
                .forEach { field ->
                    val file = File(directory, "${field.id.value}.secret")
                    Files.deleteIfExists(file.toPath())
                    deleteTemporaryFiles(file)
                }
            val remaining = directory.listFiles().orEmpty()
            if (remaining.isNotEmpty()) throw GeneralSecurityException()
            Files.deleteIfExists(directory.toPath())
        } catch (e: Exception) {
            throw CredentialSecretStoreException(CredentialSecretFailure.StorageUnavailable, e)
        }
    }

    private fun decrypt(envelopeBytes: ByteArray, sourceId: String, fieldId: CredentialFieldId): ByteArray {
        val envelope = try {
            decodeEnvelope(envelopeBytes)
        } catch (e: Exception) {
            throw SecretReadException(CredentialSecretFailure.InvalidEnvelope, e)
        }
        val key = try {
            keyProvider.getExistingKey()
        } catch (e: Exception) {
            throw SecretReadException(CredentialSecretFailure.KeyUnavailable, e)
        } ?: throw SecretReadException(CredentialSecretFailure.KeyUnavailable)
        return try {
            Cipher.getInstance(Transformation).run {
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TagBits, envelope.iv))
                updateAAD(aad(sourceId, fieldId))
                doFinal(envelope.ciphertext)
            }
        } catch (e: AEADBadTagException) {
            throw SecretReadException(CredentialSecretFailure.AuthenticationFailed, e)
        } catch (e: Exception) {
            throw SecretReadException(CredentialSecretFailure.KeyUnavailable, e)
        }
    }

    private fun secretFile(sourceId: String, fieldId: CredentialFieldId): File {
        validateSourceId(sourceId)
        if (!CredentialFieldDefinitions.get(fieldId).isSensitive) {
            throw CredentialSecretStoreException(CredentialSecretFailure.UnsupportedField)
        }
        return File(sourceDirectory(sourceId), "${fieldId.value}.secret")
    }

    private fun sourceDirectory(sourceId: String): File {
        val rootPath = root.canonicalFile
        val directory = File(root, sourceId).canonicalFile
        if (directory.parentFile != rootPath) {
            throw CredentialSecretStoreException(CredentialSecretFailure.UnsupportedField)
        }
        return directory
    }

    private fun validateSourceId(sourceId: String) {
        if (!SafeSourceId.matches(sourceId)) {
            throw CredentialSecretStoreException(CredentialSecretFailure.UnsupportedField)
        }
    }

    private fun failureReason(error: Exception): CredentialSecretFailure =
        (error as? CredentialSecretStoreException)?.reason ?: CredentialSecretFailure.StorageUnavailable

    private fun ensureDirectory(directory: File) {
        if (!directory.exists() && !directory.mkdirs()) throw GeneralSecurityException()
        if (!directory.isDirectory) throw GeneralSecurityException()
    }

    private fun atomicWrite(target: File, bytes: ByteArray) {
        val parent = target.parentFile ?: throw GeneralSecurityException()
        val temporary = File(parent, "${target.name}.${UUID.randomUUID()}.tmp")
        try {
            temporary.writeBytes(bytes)
            try {
                Files.move(
                    temporary.toPath(), target.toPath(),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING
                )
            } catch (_: Exception) {
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temporary.toPath())
            bytes.fill(0)
        }
    }

    private fun deleteTemporaryFiles(secretFile: File) {
        secretFile.parentFile?.listFiles { file ->
            file.name.startsWith("${secretFile.name}.") && file.name.endsWith(".tmp")
        }.orEmpty().forEach { Files.deleteIfExists(it.toPath()) }
    }

    private fun removeEmptyParent(directory: File?) {
        if (directory != null && directory.isDirectory && directory.list().isNullOrEmpty()) {
            Files.deleteIfExists(directory.toPath())
        }
    }

    private fun encodeEnvelope(iv: ByteArray, ciphertext: ByteArray): ByteArray {
        require(iv.size == IvLength && ciphertext.size >= TagBits / 8)
        return ByteArrayOutputStream(EnvelopeHeaderSize + iv.size + ciphertext.size).use { bytes ->
            DataOutputStream(bytes).use { output ->
                output.writeInt(Magic)
                output.writeInt(FormatVersion)
                output.writeInt(iv.size)
                output.writeInt(ciphertext.size)
                output.write(iv)
                output.write(ciphertext)
            }
            bytes.toByteArray()
        }
    }

    private fun decodeEnvelope(bytes: ByteArray): Envelope {
        if (bytes.size < EnvelopeHeaderSize + IvLength + TagBits / 8 || bytes.size > MaxEnvelopeBytes) {
            throw GeneralSecurityException()
        }
        val buffer = ByteBuffer.wrap(bytes)
        if (buffer.int != Magic || buffer.int != FormatVersion) throw GeneralSecurityException()
        val ivLength = buffer.int
        val ciphertextLength = buffer.int
        if (ivLength != IvLength || ciphertextLength < TagBits / 8 ||
            buffer.remaining() != ivLength + ciphertextLength
        ) {
            throw GeneralSecurityException()
        }
        val iv = ByteArray(ivLength).also(buffer::get)
        val ciphertext = ByteArray(ciphertextLength).also(buffer::get)
        return Envelope(iv, ciphertext)
    }

    private fun aad(sourceId: String, fieldId: CredentialFieldId): ByteArray {
        val sourceBytes = sourceId.toByteArray(StandardCharsets.UTF_8)
        val fieldBytes = fieldId.value.toByteArray(StandardCharsets.UTF_8)
        val prefix = AadDomain.toByteArray(StandardCharsets.UTF_8)
        return ByteBuffer.allocate(12 + prefix.size + sourceBytes.size + fieldBytes.size)
            .putInt(prefix.size).put(prefix)
            .putInt(sourceBytes.size).put(sourceBytes)
            .putInt(fieldBytes.size).put(fieldBytes)
            .array()
    }

    private data class Envelope(val iv: ByteArray, val ciphertext: ByteArray)
    private class SecretReadException(val reason: CredentialSecretFailure, cause: Throwable? = null) : Exception(cause)

    companion object {
        internal const val KeyAlias = "flowtone.credential.secret.v1"
        private const val Transformation = "AES/GCM/NoPadding"
        private const val TagBits = 128
        private const val IvLength = 12
        private const val Magic = 0x46544353 // FTCS
        private const val FormatVersion = 1
        private const val EnvelopeHeaderSize = 16
        private const val MaxEnvelopeBytes = 32 * 1024 * 1024
        private const val AadDomain = "flowtone.credential.secret.v1"
        private val SafeSourceId = Regex("cs_[a-zA-Z0-9-]{8,128}")

        fun from(context: Context): CredentialSecretStore = CredentialSecretStore(
            context.applicationContext.noBackupFilesDir.resolve("credential-secrets")
        )
    }
}

internal interface CredentialSecretKeyProvider {
    fun getExistingKey(): SecretKey?
    fun getOrCreateKey(): SecretKey
    fun recreateKey(): SecretKey
}

private class AndroidCredentialSecretKeyProvider : CredentialSecretKeyProvider {
    override fun getExistingKey(): SecretKey? = loadStore().getKey(CredentialSecretStore.KeyAlias, null) as? SecretKey

    override fun getOrCreateKey(): SecretKey = synchronized(KeyCreationLock) {
        getExistingKey() ?: generateKey()
    }

    override fun recreateKey(): SecretKey = synchronized(KeyCreationLock) {
        loadStore().deleteEntry(CredentialSecretStore.KeyAlias)
        generateKey()
    }

    private fun generateKey(): SecretKey {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, AndroidKeyStore)
        generator.init(
            KeyGenParameterSpec.Builder(
                CredentialSecretStore.KeyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KeySizeBits)
                .setUserAuthenticationRequired(false)
                .build()
        )
        return generator.generateKey()
    }

    private fun loadStore(): KeyStore = KeyStore.getInstance(AndroidKeyStore).apply { load(null) }

    companion object {
        private const val AndroidKeyStore = "AndroidKeyStore"
        private const val KeySizeBits = 256
        private val KeyCreationLock = Any()
    }
}
