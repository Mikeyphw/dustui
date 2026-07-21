package com.mikeyphw.dustui.data

import android.content.Context
import android.os.Build
import java.io.File

enum class StorageCategory(val label: String) {
    APP_DATA("App data"), CACHE("Cache"), CODE_CACHE("Code cache"), DATABASE("Database"), PREFERENCES("Preferences"),
    APK("APK / app code"), OBB("OBB"), MEDIA("Media"), DOWNLOAD("Download"), LOG("Log"), ROOT_MODULE("Root module"),
    SYSTEM("System"), TEMPORARY("Temporary"), OTHER("Other")
}

data class EntryIntelligence(
    val category: StorageCategory,
    val packageName: String?,
    val appLabel: String?,
    val mountPoint: String?,
    val filesystem: String?,
    val readOnly: Boolean,
)

class EntryIntelligenceResolver(private val context: Context) {
    fun resolve(path: String): EntryIntelligence {
        val packageName = packageFromPath(path)
        val appLabel = packageName?.let(::labelForPackage)
        val mount = MountTable.bestMatch(path)
        return EntryIntelligence(
            category = classify(path), packageName = packageName, appLabel = appLabel,
            mountPoint = mount?.mountPoint, filesystem = mount?.filesystem, readOnly = mount?.options?.contains("ro") == true,
        )
    }

    private fun labelForPackage(packageName: String): String? = runCatching {
        val pm = context.packageManager
        val info = if (Build.VERSION.SDK_INT >= 33) pm.getApplicationInfo(packageName, android.content.pm.PackageManager.ApplicationInfoFlags.of(0))
        else @Suppress("DEPRECATION") pm.getApplicationInfo(packageName, 0)
        pm.getApplicationLabel(info).toString()
    }.getOrNull()

    companion object {
        fun packageFromPath(path: String): String? {
            val patterns = listOf(
                Regex("^/data/(?:user|user_de)/\\d+/([^/]+)(?:/|$)"),
                Regex("^/data/data/([^/]+)(?:/|$)"),
                Regex("^/storage/emulated/\\d+/Android/(?:data|media|obb)/([^/]+)(?:/|$)"),
                Regex("^/data/app/(?:~~[^/]+/)?([^/=]+)(?:-[^/]+)?(?:/|$)"),
            )
            return patterns.firstNotNullOfOrNull { it.find(path)?.groupValues?.getOrNull(1) }
        }

        fun classify(path: String): StorageCategory {
            val lower = path.lowercase()
            return when {
                lower.startsWith("/data/adb/modules/") -> StorageCategory.ROOT_MODULE
                "/cache/" in lower || lower.endsWith("/cache") -> StorageCategory.CACHE
                "/code_cache/" in lower || lower.endsWith("/code_cache") -> StorageCategory.CODE_CACHE
                "/databases/" in lower || lower.endsWith(".db") || lower.endsWith(".sqlite") -> StorageCategory.DATABASE
                "/shared_prefs/" in lower || lower.endsWith(".xml") && "/data/" in lower -> StorageCategory.PREFERENCES
                lower.endsWith(".apk") || lower.startsWith("/data/app/") -> StorageCategory.APK
                "/android/obb/" in lower -> StorageCategory.OBB
                lower.startsWith("/storage/emulated/") && ("/dcim/" in lower || "/pictures/" in lower || "/movies/" in lower || "/music/" in lower) -> StorageCategory.MEDIA
                "/download/" in lower || lower.endsWith("/download") -> StorageCategory.DOWNLOAD
                lower.endsWith(".log") || lower.startsWith("/data/tombstones") || lower.startsWith("/data/anr") -> StorageCategory.LOG
                lower.endsWith(".tmp") || lower.endsWith(".temp") -> StorageCategory.TEMPORARY
                packageFromPath(path) != null -> StorageCategory.APP_DATA
                lower.startsWith("/system") || lower.startsWith("/vendor") || lower.startsWith("/product") -> StorageCategory.SYSTEM
                else -> StorageCategory.OTHER
            }
        }
    }
}

data class MountRecord(val mountPoint: String, val filesystem: String, val options: Set<String>)

object MountTable {
    private val records: List<MountRecord> by lazy {
        runCatching {
            File("/proc/mounts").useLines { lines -> lines.mapNotNull { line ->
                val parts = line.split(' ')
                if (parts.size < 4) null else MountRecord(parts[1].replace("\\040", " "), parts[2], parts[3].split(',').toSet())
            }.toList() }
        }.getOrDefault(emptyList()).sortedByDescending { it.mountPoint.length }
    }
    fun bestMatch(path: String): MountRecord? = records.firstOrNull { path == it.mountPoint || path.startsWith(it.mountPoint.trimEnd('/') + "/") }
}
