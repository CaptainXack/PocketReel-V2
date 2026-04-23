package com.cxacks.cxfireloader.data

data class PackageHistoryEntry(
    val title: String,
    val source: String,
    val mode: String,
    val packageName: String? = null,
    val versionName: String? = null,
    val apkUrl: String? = null,
    val code: String? = null,
    val savedAtEpochMs: Long = System.currentTimeMillis(),
)
