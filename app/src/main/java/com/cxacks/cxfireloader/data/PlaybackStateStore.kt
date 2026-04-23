package com.captainxack.pocketreel.data

import android.content.Context
import java.security.MessageDigest

data class PlaybackState(
    val watched: Boolean = false,
    val resumePositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val updatedAt: Long = 0L,
)

class PlaybackStateStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getState(documentUri: String): PlaybackState {
        val key = stableKey(documentUri)
        return PlaybackState(
            watched = prefs.getBoolean(watchedKey(key), false),
            resumePositionMs = prefs.getLong(positionKey(key), 0L),
            durationMs = prefs.getLong(durationKey(key), 0L),
            updatedAt = prefs.getLong(updatedKey(key), 0L),
        )
    }

    fun saveProgress(documentUri: String, positionMs: Long, durationMs: Long) {
        val safePosition = positionMs.coerceAtLeast(0L)
        val safeDuration = durationMs.coerceAtLeast(0L)
        val key = stableKey(documentUri)

        when {
            safeDuration > 0L && safePosition >= (safeDuration * WATCHED_THRESHOLD).toLong() -> {
                markWatched(documentUri, safeDuration)
            }

            safePosition < MIN_RESUME_MS -> {
                prefs.edit()
                    .putBoolean(watchedKey(key), false)
                    .putLong(positionKey(key), 0L)
                    .putLong(durationKey(key), safeDuration)
                    .putLong(updatedKey(key), System.currentTimeMillis())
                    .apply()
            }

            else -> {
                prefs.edit()
                    .putBoolean(watchedKey(key), false)
                    .putLong(positionKey(key), safePosition)
                    .putLong(durationKey(key), safeDuration)
                    .putLong(updatedKey(key), System.currentTimeMillis())
                    .apply()
            }
        }
    }

    fun markWatched(documentUri: String, durationMs: Long = 0L) {
        val key = stableKey(documentUri)
        val storedDuration = prefs.getLong(durationKey(key), 0L)
        prefs.edit()
            .putBoolean(watchedKey(key), true)
            .putLong(positionKey(key), 0L)
            .putLong(durationKey(key), maxOf(durationMs, storedDuration))
            .putLong(updatedKey(key), System.currentTimeMillis())
            .apply()
    }

    fun markUnwatched(documentUri: String) {
        val key = stableKey(documentUri)
        prefs.edit()
            .putBoolean(watchedKey(key), false)
            .putLong(positionKey(key), 0L)
            .putLong(updatedKey(key), System.currentTimeMillis())
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "pocket_reel_playback"
        const val MIN_RESUME_MS = 30_000L
        private const val WATCHED_THRESHOLD = 0.9

        private fun stableKey(documentUri: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            return digest.digest(documentUri.toByteArray()).joinToString("") { "%02x".format(it) }
        }

        private fun watchedKey(key: String) = "watched_$key"
        private fun positionKey(key: String) = "position_$key"
        private fun durationKey(key: String) = "duration_$key"
        private fun updatedKey(key: String) = "updated_$key"
    }
}
