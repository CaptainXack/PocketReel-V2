package com.cxacks.cxfireloader.data

import android.net.Uri
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class ResolveRepository(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build(),
) {
    @Throws(IOException::class)
    fun resolveCode(baseEndpoint: String, code: String): PackageDescriptor {
        val cleanBase = baseEndpoint.trim().trimEnd('/')
        require(cleanBase.startsWith("https://")) { "Endpoint must start with https://" }
        require(code.isNotBlank()) { "Code cannot be empty" }

        val url = "$cleanBase/${Uri.encode(code.trim())}"
        val request = Request.Builder().url(url).get().build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Server returned ${response.code}")
            }

            val body = response.body?.string().orEmpty()
            val json = JSONObject(body)

            val apkUrl = json.optString("apkUrl").ifBlank {
                throw IOException("Response did not contain apkUrl")
            }

            return PackageDescriptor(
                title = json.optString("title").ifBlank { "Resolved Package" },
                apkUrl = apkUrl,
                sha256 = json.optString("sha256").ifBlank { null },
                packageName = json.optString("packageName").ifBlank { null },
                versionName = json.optString("versionName").ifBlank { null },
                notes = json.optString("notes").ifBlank { null },
            )
        }
    }
}
