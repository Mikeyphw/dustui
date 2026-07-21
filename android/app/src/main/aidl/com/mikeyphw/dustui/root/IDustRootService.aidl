package com.mikeyphw.dustui.root;

import android.os.ParcelFileDescriptor;

/** Fixed privileged interface. Arbitrary shell execution is deliberately absent. */
interface IDustRootService {
    String serviceInfo();
    String startScan(String requestJson, in ParcelFileDescriptor output);
    String scanState(String scanId);
    String cancelScan(String scanId);
    String reviewCleanup(String planJson);
    String executeCleanup(String planJson);
    String restoreCleanup(String operationId, String rootPath);
    String verifyDuplicates(String requestJson);
}
