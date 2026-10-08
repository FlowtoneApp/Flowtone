package ink.tenqui.flowtone.data.online.credential

import android.content.Context
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

/** App-private metadata store. Secret values deliberately have no serialization path. */
class CredentialSourceStore(
    private val root: File,
    private val nextId: () -> String = { "cs_${UUID.randomUUID()}" }
) {
    fun list(): List<CredentialSource> = readSources().sortedBy { it.label.lowercase() }

    fun get(id: String): CredentialSource? = readSources().firstOrNull { it.id == id }

    fun create(input: CredentialSourceInput): CredentialSource {
        val normalized = input.normalized()
        CredentialSourceValidator.requireValid(normalized)
        val existing = readSources()
        val id = generateUniqueId(existing.mapTo(hashSetOf()) { it.id })
        val source = CredentialSource(
            id = id,
            credentialType = normalized.credentialType,
            label = normalized.label,
            realm = normalized.realm,
            publicFields = normalized.publicFields
        )
        CredentialSourceValidator.requireValid(source)
        writeSources(existing + source)
        return source
    }

    fun update(id: String, input: CredentialSourceInput): CredentialSource {
        val existing = readSources()
        val current = existing.firstOrNull { it.id == id } ?: throw NoSuchElementException("凭据不存在")
        require(current.credentialType == input.credentialType) { "凭据类型不可修改" }
        val normalized = input.normalized()
        CredentialSourceValidator.requireValid(normalized)
        val updated = current.copy(
            label = normalized.label,
            realm = normalized.realm,
            publicFields = normalized.publicFields
        )
        CredentialSourceValidator.requireValid(updated)
        writeSources(existing.map { if (it.id == id) updated else it })
        return updated
    }

    internal fun delete(id: String): Boolean {
        val existing = readSources()
        val retained = existing.filterNot { it.id == id }
        if (retained.size == existing.size) return false
        writeSources(retained)
        return true
    }

    private fun readSources(): List<CredentialSource> {
        val file = sourceFile
        if (!file.isFile) return emptyList()
        return runCatching {
            val entries = JSONObject(file.readText(StandardCharsets.UTF_8)).optJSONArray("sources") ?: JSONArray()
            List(entries.length()) { index -> parse(entries.getJSONObject(index)) }
                .filterNotNull()
        }.getOrDefault(emptyList())
    }

    private fun parse(json: JSONObject): CredentialSource? = runCatching {
        val type = CredentialType.fromValue(json.getString("type")) ?: return@runCatching null
        val publicFields = json.optJSONObject("publicFields")?.let { fields ->
            buildMap {
                fields.keys().forEach { key ->
                    val field = CredentialFieldId.fromValue(key)
                        ?: throw IllegalArgumentException("未知凭据字段")
                    val value = fields.optString(key)
                    if (value.isNotBlank()) put(field, value)
                }
            }
        }.orEmpty()
        CredentialSource(
            id = json.getString("id"),
            credentialType = type,
            label = json.getString("label"),
            realm = json.optString("realm").takeIf(String::isNotBlank),
            publicFields = publicFields
        ).takeIf { CredentialSourceValidator.validate(it).isEmpty() }
    }.getOrNull()

    private fun writeSources(sources: List<CredentialSource>) {
        sources.forEach(CredentialSourceValidator::requireValid)
        sourceFile.parentFile?.mkdirs()
        val json = JSONObject().put("format", FormatVersion).put(
            "sources",
            JSONArray().apply {
                sources.forEach { source ->
                    put(JSONObject().apply {
                        put("id", source.id)
                        put("type", source.credentialType.value)
                        put("label", source.label)
                        source.realm?.let { put("realm", it) }
                        put("publicFields", JSONObject().apply {
                            source.publicFields.forEach { (field, value) -> put(field.value, value) }
                        })
                    })
                }
            }
        )
        val temporary = File(sourceFile.parentFile, "${sourceFile.name}.tmp")
        temporary.writeText(json.toString(), StandardCharsets.UTF_8)
        runCatching {
            Files.move(
                temporary.toPath(), sourceFile.toPath(),
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING
            )
        }.getOrElse {
            Files.move(temporary.toPath(), sourceFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun generateUniqueId(existing: Set<String>): String {
        repeat(8) {
            val id = nextId()
            if (id !in existing) return id
        }
        error("无法生成唯一凭据 ID")
    }

    private val sourceFile: File get() = root.resolve("sources.json")

    companion object {
        private const val FormatVersion = 1

        fun from(context: Context): CredentialSourceStore =
            CredentialSourceStore(context.applicationContext.filesDir.resolve("credential-sources"))
    }
}
