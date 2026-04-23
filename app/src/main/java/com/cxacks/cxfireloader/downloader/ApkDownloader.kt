package com.cxacks.cxfireloader.downloader

import android.content.Context
import android.os.Environment
import com.cxacks.cxfireloader.data.PackageDescriptor
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit

class ApkDownloader(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .build(),
) {
    @Throws(IOException::class)
    fun download(
        context: Context,
        descriptor: PackageDescriptor,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit,
    ): File {
        require(descriptor.apkUrl.startsWith("https://")) {
            "Only https downloads are allowed in this starter build"
        }

        val request = Request.Builder().url(descriptor.apkUrl).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Download failed with HTTP ${response.code}")
            }

            val body = response.body ?: throw IOException("Empty response body")
            val total = body.contentLength()
            val targetDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: File(context.filesDir, "downloads").apply { mkdirs() }

            if (!targetDir.exists()) targetDir.mkdirs()

            val fileName = buildFileName(descriptor)
            val outFile = File(targetDir, fileName)

            body.byteStream().use { input ->
                outFile.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var downloaded = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        onProgress(downloaded, total)
                    }
                    output.flush()
                }
            }

            return outFile
        }
    }

    private fun buildFileName(descriptor: PackageDescriptor): String {
        val seed = if (descriptor.title.isNotBlank()) descriptor.title else "package"
        val cleanTitle = seed
            .lowercase(Locale.US)
            .replace(Regex("[^a-z0-9._-]+"), "_")
            .trim('_')
            .ifBlank { "package" }

        val versionPart = descriptor.versionName?.takeIf { it.isNotBlank() }
            ?.replace(Regex("[^a-zA-Z0-9._-]+"), "_")
            ?.let { "_$it" }
            .orEmpty()

        return "${cleanTitle}${versionPart}.apk"
    }
}
