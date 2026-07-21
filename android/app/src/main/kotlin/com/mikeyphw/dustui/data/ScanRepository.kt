package com.mikeyphw.dustui.data

import android.content.Context
import com.mikeyphw.dustui.root.RootServiceClient
import com.mikeyphw.dustui.scan.ScanForegroundService
import com.mikeyphw.dustui.scan.ScanRequest
import kotlinx.coroutines.flow.Flow

class ScanRepository(
    private val context: Context,
    private val dao: ScanDao,
    private val rootClient: RootServiceClient,
) {
    fun observeLatestSession(): Flow<ScanSessionEntity?> = dao.observeLatestSession()
    fun observeEntries(scanId: String): Flow<List<ScanEntryEntity>> = dao.observeEntries(scanId, 500)
    suspend fun rootAvailable(): Boolean = rootClient.rootAvailable()

    fun startScan(request: ScanRequest) {
        ScanForegroundService.start(context, request)
    }

    fun cancelScan(scanId: String) {
        ScanForegroundService.cancel(context, scanId)
    }
}
