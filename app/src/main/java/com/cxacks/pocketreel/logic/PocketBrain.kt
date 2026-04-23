package com.cxacks.pocketreel.logic

import android.content.Context
import android.os.StatFs
import java.io.File

class PocketBrain(val context: Context) {
    // AUTOMATION: Detects the biggest storage and checks for FAT32 4GB wall
    fun getBestStorage(): File {
        val dirs = context.getExternalFilesDirs(null)
        return dirs.maxByOrNull { StatFs(it.path).availableBytes } ?: context.filesDir
    }

    fun isPathRestricted(path: String, fileSize: Long): Boolean {
        val isFat32 = path.contains("vfat") || path.contains("sdcard") || path.contains("usb")
        return isFat32 && fileSize >= 4294967295L // 4GB Limit
    }

    // TANDEM: RSS + Alldebrid Resolver
    fun resolveLink(apiKey: String, magnet: String): String {
        return "https://api.alldebrid.com/v4/magnet/upload?apikey=$apiKey&magnets[]=$magnet"
    }
}
