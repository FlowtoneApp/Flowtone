package ink.tenqui.flowtone.data.online.credential

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import org.json.JSONArray
import org.json.JSONObject

/** Host-owned authorization metadata. This format deliberately has no Secret field. */
class CredentialGrantStore(private val directory: File) {
    private val lock = lockFor(directory)

    fun grantsForExtension(extensionId: String): List<CredentialGrant> =
        synchronized(lock) { readRecords().filter { it.extensionId == extensionId } }

    fun find(extensionId: String, requestId: String): CredentialGrant? =
        synchronized(lock) {
            readRecords().firstOrNull {
                it.extensionId == extensionId && it.credentialRequestId == requestId
            }
        }

    fun putOrReplace(grant: CredentialGrant) {
        synchronized(lock) {
            validate(grant)
            val records = readRecords()
            val updated = records.filterNot {
                it.extensionId == grant.extensionId && it.credentialRequestId == grant.credentialRequestId
            } + grant
            writeRecords(updated)
        }
    }

    fun revoke(extensionId: String, requestId: String): Boolean = synchronized(lock) {
        mutate { records ->
            val updated = records.filterNot {
                it.extensionId == extensionId && it.credentialRequestId == requestId
            }
            Triple(updated, updated.size != records.size, updated.size != records.size)
        }
    }

    fun deleteForExtension(extensionId: String): Int = synchronized(lock) {
        mutate { records ->
            val updated = records.filterNot { it.extensionId == extensionId }
            Triple(updated, records.size - updated.size, updated.size != records.size)
        }
    }

    fun deleteForCredentialSource(sourceId: String): Int = synchronized(lock) {
        mutate { records ->
            val updated = records.filterNot { it.credentialSourceId == sourceId }
            Triple(updated, records.size - updated.size, updated.size != records.size)
        }
    }

    fun reconcileExtension(
        extensionId: String,
        extensionInstanceId: String,
        currentRequests: Collection<CredentialRequestDefinition>
    ): Int = synchronized(lock) {
        mutate { records ->
            val contracts = currentRequests.mapNotNull { request ->
                CredentialRequestContractSnapshot.from(request)?.let { request.id to it }
            }.toMap()
            val updated = records.filterNot { grant ->
                grant.extensionId == extensionId &&
                    (grant.extensionInstanceId != extensionInstanceId || contracts[grant.credentialRequestId] != grant.requestContract)
            }
            Triple(updated, records.size - updated.size, updated.size != records.size)
        }
    }

    private inline fun <T> mutate(transform: (List<CredentialGrant>) -> Triple<List<CredentialGrant>, T, Boolean>): T {
        val (updated, result, changed) = transform(readRecords())
        if (changed) writeRecords(updated)
        return result
    }

    private fun readRecords(): List<CredentialGrant> {
        val file = recordsFile
        if (!file.exists()) return emptyList()
        try {
            val root = JSONObject(file.readText(StandardCharsets.UTF_8))
            require(root.keys().asSequence().toSet() == setOf("formatVersion", "grants"))
            require(root.getInt("formatVersion") == FormatVersion)
            val array = root.getJSONArray("grants")
            val records = ArrayList<CredentialGrant>(array.length())
            val keys = mutableSetOf<Pair<String, String>>()
            repeat(array.length()) { index ->
                val grant = parseGrant(array.getJSONObject(index))
                validate(grant)
                require(keys.add(grant.extensionId to grant.credentialRequestId))
                records += grant
            }
            return records
        } catch (_: Exception) {
            throw CredentialGrantStoreException("Credential grants are unavailable")
        }
    }

    private fun parseGrant(json: JSONObject): CredentialGrant {
        require(json.keys().asSequence().toSet() == GrantKeys)
        val contractJson = json.getJSONObject("requestContract")
        require(contractJson.keys().asSequence().toSet() == ContractKeys)
        val fieldsJson = contractJson.getJSONArray("requestedFieldIds")
        val fields = buildSet {
            repeat(fieldsJson.length()) { index ->
                val field = CredentialFieldId.fromValue(fieldsJson.getString(index))
                    ?: throw IllegalArgumentException()
                require(add(field))
            }
        }
        val identitiesJson = contractJson.getJSONArray("identityConstraints")
        val identities = buildSet {
            repeat(identitiesJson.length()) { index ->
                val identity = CredentialIdentifierType.fromValue(identitiesJson.getString(index))
                    ?: throw IllegalArgumentException()
                require(add(identity))
            }
        }
        return CredentialGrant(
            extensionId = json.getString("extensionId"),
            extensionInstanceId = json.getString("extensionInstanceId"),
            credentialRequestId = json.getString("credentialRequestId"),
            credentialSourceId = json.getString("credentialSourceId"),
            requestContract = CredentialRequestContractSnapshot(
                credentialType = CredentialType.fromValue(contractJson.getString("credentialType"))
                    ?: throw IllegalArgumentException(),
                realm = if (contractJson.isNull("realm")) null else contractJson.getString("realm"),
                requestedFieldIds = fields,
                identityConstraints = identities
            ),
            createdAtEpochMillis = json.getLong("createdAtEpochMillis")
        )
    }

    private fun writeRecords(records: List<CredentialGrant>) {
        try {
            directory.mkdirs()
            val temporary = File(directory, "grants.json.tmp")
            FileOutputStream(temporary).use { output ->
                output.write(serialize(records).toByteArray(StandardCharsets.UTF_8))
                output.fd.sync()
            }
            try {
                Files.move(
                    temporary.toPath(), recordsFile.toPath(),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING
                )
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(temporary.toPath(), recordsFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (_: Exception) {
            throw CredentialGrantStoreException("Credential grants could not be saved")
        }
    }

    private fun serialize(records: List<CredentialGrant>): String {
        val grants = JSONArray()
        records.forEach { grant ->
            val fields = JSONArray().also { array ->
                grant.requestContract.requestedFieldIds.sortedBy { it.value }.forEach { array.put(it.value) }
            }
            val identities = JSONArray().also { array ->
                grant.requestContract.identityConstraints.sortedBy { it.value }.forEach { array.put(it.value) }
            }
            val contract = JSONObject()
                .put("credentialType", grant.requestContract.credentialType.value)
                .put("realm", grant.requestContract.realm ?: JSONObject.NULL)
                .put("requestedFieldIds", fields)
                .put("identityConstraints", identities)
            grants.put(
                JSONObject()
                    .put("extensionId", grant.extensionId)
                    .put("extensionInstanceId", grant.extensionInstanceId)
                    .put("credentialRequestId", grant.credentialRequestId)
                    .put("credentialSourceId", grant.credentialSourceId)
                    .put("requestContract", contract)
                    .put("createdAtEpochMillis", grant.createdAtEpochMillis)
            )
        }
        return JSONObject().put("formatVersion", FormatVersion).put("grants", grants).toString()
    }

    private fun validate(grant: CredentialGrant) {
        require(ExtensionId.matches(grant.extensionId))
        require(runCatching { java.util.UUID.fromString(grant.extensionInstanceId).toString() == grant.extensionInstanceId }
            .getOrDefault(false))
        require(RequestId.matches(grant.credentialRequestId))
        require(SourceId.matches(grant.credentialSourceId))
        require(grant.createdAtEpochMillis > 0)
        require(grant.requestContract.isWellFormed())
    }

    private val recordsFile: File get() = File(directory, "grants.json")

    companion object {
        private const val FormatVersion = 1
        private val ExtensionId = Regex("[a-zA-Z0-9._-]{1,128}")
        private val RequestId = Regex("[a-zA-Z][a-zA-Z0-9._-]{0,63}")
        private val SourceId = Regex("cs_[a-zA-Z0-9-]{8,128}")
        private val GrantKeys = setOf(
            "extensionId", "extensionInstanceId", "credentialRequestId", "credentialSourceId",
            "requestContract", "createdAtEpochMillis"
        )
        private val ContractKeys = setOf(
            "credentialType", "realm", "requestedFieldIds", "identityConstraints"
        )
        private val Locks = java.util.concurrent.ConcurrentHashMap<String, Any>()

        private fun lockFor(directory: File): Any = Locks.computeIfAbsent(
            directory.absoluteFile.normalize().path
        ) { Any() }

        fun from(context: Context): CredentialGrantStore =
            CredentialGrantStore(context.applicationContext.noBackupFilesDir.resolve("credential-grants"))
    }
}

class CredentialGrantStoreException(message: String) : Exception(message)
