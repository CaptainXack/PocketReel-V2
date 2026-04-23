package com.cxacks.cxfireloader.data

data class PackageDescriptor(
    val title: String,
    val apkUrl: String,
    val sha256: String? = null,
    val packageName: String? = null,
    val versionName: String? = null,
    val notes: String? = null,
)
