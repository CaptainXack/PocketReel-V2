package com.captainxack.pocketreel.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class WantedRequest(
    val key: String,
    val mediaType: String,
    val tmdbId: Int,
    val title: String,
    val createdAt: Long,
)

class RequestStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getRequestedMediaKeys(): Set<String> {
        return getWantedRequests().map { it.key }.toSet()
    }

    fun getWantedRequests(): List<WantedRequest> {
        val raw = prefs.getString(KEY_WANTED_REQUESTS, null).orEmpty()
        if (raw.isBlank()) return emptyList()
        val array = JSONArray(raw)
        val items = mutableListOf<WantedRequest>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val key = obj.optString("key").ifBlank { null } ?: continue
            val mediaType = obj.optString("mediaType").ifBlank { null } ?: continue
            val tmdbId = obj.optInt("tmdbId")
            val title = obj.optString("title").ifBlank { null } ?: continue
            val createdAt = obj.optLong("createdAt")
            items.add(
                WantedRequest(
                    key = key,
                    mediaType = mediaType,
                    tmdbId = tmdbId,
                    title = title,
                    createdAt = createdAt,
                ),
            )
        }
        return items.sortedByDescending { it.createdAt }
    }

    fun addWantedRequest(mediaType: String, tmdbId: Int, title: String) {
        val key = buildRequestKey(mediaType, tmdbId)
        if (getRequestedMediaKeys().contains(key)) return
        val updated = getWantedRequests().toMutableList().apply {
            add(
                0,
                WantedRequest(
                    key = key,
                    mediaType = mediaType,
                    tmdbId = tmdbId,
                    title = title,
                    createdAt = System.currentTimeMillis(),
                ),
            )
        }
        persistWantedRequests(updated)
    }

    private fun persistWantedRequests(items: List<WantedRequest>) {
        val array = JSONArray()
        items.forEach { request ->
            array.put(
                JSONObject()
                    .put("key", request.key)
                    .put("mediaType", request.mediaType)
                    .put("tmdbId", request.tmdbId)
                    .put("title", request.title)
                    .put("createdAt", request.createdAt),
            )
        }
        prefs.edit().putString(KEY_WANTED_REQUESTS, array.toString()).apply()
    }

    private fun buildRequestKey(mediaType: String, tmdbId: Int): String = "$mediaType:$tmdbId"

    companion object {
        private const val PREFS_NAME = "pocket_reel_requests"
        private const val KEY_WANTED_REQUESTS = "wanted_requests"
    }
}
