package com.mikeyphw.dustui.data

import android.content.Context
import androidx.core.content.FileProvider
import java.io.File

object ExportWriter {
    fun writeCsv(context: Context, session: ScanSessionEntity, entries: List<ScanEntryEntity>): android.net.Uri {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "dustui-${session.id}.csv")
        file.bufferedWriter().use { out ->
            out.appendLine("path,name,size_bytes,allocated_bytes,is_directory,modified_at,file_count")
            entries.forEach { e -> out.appendLine(listOf(e.path, e.name, e.sizeBytes, e.allocatedBytes, e.isDirectory, e.modifiedAt, e.fileCount).joinToString(",") { csv(it.toString()) }) }
        }
        return FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    }

    suspend fun writeCsvPaged(
        context: Context,
        session: ScanSessionEntity,
        pageSize: Int = 500,
        page: suspend (offset: Int, limit: Int) -> ScanBrowsePage,
    ): android.net.Uri {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "dustui-${session.id}.csv")
        file.bufferedWriter().use { out ->
            out.appendLine("path,name,size_bytes,allocated_bytes,is_directory,modified_at,file_count")
            var offset = 0
            while (true) {
                val result = page(offset, pageSize.coerceIn(1, 500))
                result.entries.forEach { e ->
                    out.appendLine(listOf(e.path, e.name, e.sizeBytes, e.allocatedBytes, e.isDirectory, e.modifiedAt, e.fileCount).joinToString(",") { csv(it.toString()) })
                }
                if (!result.hasMore || result.entries.isEmpty()) break
                offset += result.entries.size
            }
        }
        return FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    }
    private fun csv(value: String) = "\"${value.replace("\"", "\"\"")}\""
}
