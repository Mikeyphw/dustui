package com.mikeyphw.dustui.data

import android.content.Context
import androidx.core.content.FileProvider
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

class CleanupRuleStore(private val context: Context) {
    private val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun rules(): List<CleanupRule> {
        val raw = preferences.getString(KEY_RULES, null)
        return if (raw == null) CleanupRulePresets.defaults() else CleanupRuleCodec.decode(raw)
    }

    fun save(rule: CleanupRule) {
        val now = System.currentTimeMillis()
        val reviewed = if (rule.imported) rule.copy(reviewedAt = rule.reviewedAt ?: now, enabled = false) else rule
        val updated = reviewed.copy(updatedAt = now)
        persist(rules().filterNot { it.id == updated.id } + updated)
    }

    fun markReviewed(ruleId: String): CleanupRule? {
        val existing = rules().firstOrNull { it.id == ruleId } ?: return null
        val reviewed = existing.copy(reviewedAt = System.currentTimeMillis(), enabled = false, updatedAt = System.currentTimeMillis())
        persist(rules().map { if (it.id == ruleId) reviewed else it })
        return reviewed
    }

    fun delete(ruleId: String) = persist(rules().filterNot { it.id == ruleId })

    fun duplicate(ruleId: String): CleanupRule? {
        val source = rules().firstOrNull { it.id == ruleId } ?: return null
        val now = System.currentTimeMillis()
        val duplicate = source.copy(
            id = UUID.randomUUID().toString(),
            name = source.name + " copy",
            enabled = false,
            imported = false,
            reviewedAt = now,
            createdAt = now,
            updatedAt = now,
        )
        persist(rules() + duplicate)
        return duplicate
    }

    fun setEnabled(ruleId: String, enabled: Boolean): Result<CleanupRule> {
        val current = rules()
        val rule = current.firstOrNull { it.id == ruleId }
            ?: return Result.failure(IllegalArgumentException("Cleanup rule was not found"))
        if (enabled && !rule.reviewed) {
            return Result.failure(IllegalStateException("Open and save this imported rule before enabling it"))
        }
        val updated = rule.copy(enabled = enabled, updatedAt = System.currentTimeMillis())
        val validation = CleanupRuleEngine.validate(updated)
        if (!validation.valid) return Result.failure(IllegalArgumentException(validation.errors.joinToString("; ")))
        persist(current.map { if (it.id == ruleId) updated else it })
        return Result.success(updated)
    }

    fun exportJson(): String = CleanupRuleCodec.encode(rules()).toString(2)

    fun importJson(raw: String): Result<List<CleanupRule>> = runCatching {
        val decoded = CleanupRuleCodec.decode(raw)
        require(decoded.isNotEmpty()) { "No cleanup rules were found in the import" }
        val now = System.currentTimeMillis()
        val imported = decoded.map { rule ->
            rule.copy(
                id = UUID.randomUUID().toString(),
                enabled = false,
                imported = true,
                reviewedAt = null,
                schemaVersion = CleanupRule.SCHEMA_VERSION,
                createdAt = now,
                updatedAt = now,
            )
        }
        imported.forEach { rule ->
            val validation = CleanupRuleEngine.validate(rule)
            require(validation.valid) { validation.errors.joinToString("; ") }
        }
        persist(rules() + imported)
        imported
    }

    fun writeExportFile(context: Context): android.net.Uri {
        val directory = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(directory, "dustui-cleanup-rules.json")
        file.writeText(exportJson())
        return FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    }

    private fun persist(value: List<CleanupRule>) = preferences.edit { putString(KEY_RULES, CleanupRuleCodec.encode(value).toString()) }

    private companion object {
        const val PREFS = "cleanup-rules-v1"
        const val KEY_RULES = "rules"
    }
}

/** Versioned codec is public to unit tests so import/export compatibility is executable, not aspirational. */
object CleanupRuleCodec {
    fun encode(rules: List<CleanupRule>): JSONObject = JSONObject()
        .put("schemaVersion", CleanupRule.SCHEMA_VERSION)
        .put("rules", JSONArray(rules.map(::toJson)))

    fun decode(raw: String): List<CleanupRule> {
        val root = JSONObject(raw)
        val version = root.optInt("schemaVersion", 1)
        require(version in 1..CleanupRule.SCHEMA_VERSION) { "Unsupported cleanup-rule schema $version" }
        val array = root.optJSONArray("rules") ?: JSONArray()
        return buildList {
            for (index in 0 until array.length()) add(fromJson(array.getJSONObject(index), version))
        }
    }

    private fun toJson(rule: CleanupRule): JSONObject = JSONObject()
        .put("id", rule.id)
        .put("name", rule.name)
        .put("enabled", rule.enabled)
        .put("imported", rule.imported)
        .apply { rule.reviewedAt?.let { put("reviewedAt", it) } }
        .put("schemaVersion", rule.schemaVersion)
        .put("createdAt", rule.createdAt)
        .put("updatedAt", rule.updatedAt)
        .put("conditions", JSONObject().apply {
            val conditions = rule.conditions
            put("includePaths", JSONArray(conditions.includePaths))
            put("excludePaths", JSONArray(conditions.excludePaths))
            put("extensions", JSONArray(conditions.extensions))
            put("minimumBytes", conditions.minimumBytes)
            conditions.maximumBytes?.let { put("maximumBytes", it) }
            conditions.olderThanDays?.let { put("olderThanDays", it) }
            conditions.category?.let { put("category", it) }
            conditions.packageName?.let { put("packageName", it) }
            conditions.regularExpression?.let { put("regularExpression", it) }
            conditions.maximumDepth?.let { put("maximumDepth", it) }
            put("entryType", conditions.entryType.name)
            put("duplicateOnly", conditions.duplicateOnly)
            put("residueConfidence", conditions.residueConfidence.name)
        })

    private fun fromJson(json: JSONObject, containerVersion: Int): CleanupRule {
        val conditions = json.optJSONObject("conditions") ?: JSONObject()
        val imported = json.optBoolean("imported")
        val reviewedAt = when {
            json.has("reviewedAt") && !json.isNull("reviewedAt") -> json.optLong("reviewedAt")
            containerVersion < 2 && !imported -> json.optLong("updatedAt", System.currentTimeMillis())
            else -> null
        }
        return CleanupRule(
            id = json.optString("id").takeIf(String::isNotBlank) ?: UUID.randomUUID().toString(),
            name = json.optString("name", "Imported cleanup rule"),
            enabled = json.optBoolean("enabled") && (!imported || reviewedAt != null),
            imported = imported,
            reviewedAt = reviewedAt,
            schemaVersion = CleanupRule.SCHEMA_VERSION,
            createdAt = json.optLong("createdAt", System.currentTimeMillis()),
            updatedAt = json.optLong("updatedAt", System.currentTimeMillis()),
            conditions = CleanupRuleConditions(
                includePaths = conditions.stringList("includePaths"),
                excludePaths = conditions.stringList("excludePaths"),
                extensions = conditions.stringList("extensions"),
                minimumBytes = conditions.optLong("minimumBytes"),
                maximumBytes = conditions.optLongOrNull("maximumBytes"),
                olderThanDays = conditions.optIntOrNull("olderThanDays"),
                category = conditions.optString("category").takeIf(String::isNotBlank),
                packageName = conditions.optString("packageName").takeIf(String::isNotBlank),
                regularExpression = conditions.optString("regularExpression").takeIf(String::isNotBlank),
                maximumDepth = conditions.optIntOrNull("maximumDepth"),
                entryType = enumValueOrDefault(conditions.optString("entryType"), CleanupRuleEntryType.FILES),
                duplicateOnly = conditions.optBoolean("duplicateOnly"),
                residueConfidence = enumValueOrDefault(conditions.optString("residueConfidence"), CleanupRuleResidueConfidence.ANY),
            ),
        )
    }

    private fun JSONObject.stringList(key: String): List<String> = optJSONArray(key)?.let { array ->
        buildList { for (index in 0 until array.length()) add(array.optString(index)) }
    }.orEmpty()

    private fun JSONObject.optLongOrNull(key: String): Long? = if (has(key) && !isNull(key)) optLong(key) else null
    private fun JSONObject.optIntOrNull(key: String): Int? = if (has(key) && !isNull(key)) optInt(key) else null
    private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == value } ?: fallback
}
