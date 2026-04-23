package com.captainxack.pocketreel.data

import android.content.Context

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getMediaTreeUri(): String? = prefs.getString(KEY_MEDIA_TREE_URI, null)

    fun setMediaTreeUri(value: String?) {
        prefs.edit().putString(KEY_MEDIA_TREE_URI, value).apply()
    }

    fun getTmdbLanguage(): String = prefs.getString(KEY_TMDB_LANGUAGE, DEFAULT_TMDB_LANGUAGE) ?: DEFAULT_TMDB_LANGUAGE

    fun setTmdbLanguage(value: String) {
        prefs.edit().putString(KEY_TMDB_LANGUAGE, value.trim().ifBlank { DEFAULT_TMDB_LANGUAGE }).apply()
    }

    fun getPreferredSubtitleLanguage(): String = prefs.getString(KEY_SUBTITLE_LANGUAGE, DEFAULT_SUBTITLE_LANGUAGE) ?: DEFAULT_SUBTITLE_LANGUAGE

    fun setPreferredSubtitleLanguage(value: String) {
        prefs.edit().putString(KEY_SUBTITLE_LANGUAGE, value.trim().ifBlank { DEFAULT_SUBTITLE_LANGUAGE }).apply()
    }

    fun getPreferredAudioLanguage(): String = prefs.getString(KEY_AUDIO_LANGUAGE, DEFAULT_AUDIO_LANGUAGE) ?: DEFAULT_AUDIO_LANGUAGE

    fun setPreferredAudioLanguage(value: String) {
        prefs.edit().putString(KEY_AUDIO_LANGUAGE, value.trim().ifBlank { DEFAULT_AUDIO_LANGUAGE }).apply()
    }

    fun getUseExternalPlayer(): Boolean = prefs.getBoolean(KEY_USE_EXTERNAL_PLAYER, false)

    fun setUseExternalPlayer(value: Boolean) {
        prefs.edit().putBoolean(KEY_USE_EXTERNAL_PLAYER, value).apply()
    }

    fun getAutoFetchArtworkOnScan(): Boolean = prefs.getBoolean(KEY_AUTO_FETCH_ARTWORK_ON_SCAN, true)

    fun setAutoFetchArtworkOnScan(value: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_FETCH_ARTWORK_ON_SCAN, value).apply()
    }

    fun getEnableOnlinePlayback(): Boolean = prefs.getBoolean(KEY_ENABLE_ONLINE_PLAYBACK, false)

    fun setEnableOnlinePlayback(value: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLE_ONLINE_PLAYBACK, value).apply()
    }

    fun getAllDebridApiKey(): String = prefs.getString(KEY_ALLDEBRID_API_KEY, "") ?: ""

    fun setAllDebridApiKey(value: String) {
        prefs.edit().putString(KEY_ALLDEBRID_API_KEY, value.trim()).apply()
    }

    fun getPreferredOnlineAudioMode(): String = prefs.getString(KEY_ONLINE_AUDIO_MODE, DEFAULT_ONLINE_AUDIO_MODE) ?: DEFAULT_ONLINE_AUDIO_MODE

    fun setPreferredOnlineAudioMode(value: String) {
        prefs.edit().putString(KEY_ONLINE_AUDIO_MODE, value.trim().ifBlank { DEFAULT_ONLINE_AUDIO_MODE }).apply()
    }

    fun getPreferSeriesPacks(): Boolean = prefs.getBoolean(KEY_PREFER_SERIES_PACKS, true)

    fun setPreferSeriesPacks(value: Boolean) {
        prefs.edit().putBoolean(KEY_PREFER_SERIES_PACKS, value).apply()
    }

    fun getRssFeedsText(): String = prefs.getString(KEY_RSS_FEEDS_TEXT, "") ?: ""

    fun setRssFeedsText(value: String) {
        prefs.edit().putString(KEY_RSS_FEEDS_TEXT, value.trim()).apply()
    }

    companion object {
        private const val PREFS_NAME = "pocket_reel_prefs"
        private const val KEY_MEDIA_TREE_URI = "media_tree_uri"
        private const val KEY_TMDB_LANGUAGE = "tmdb_language"
        private const val KEY_SUBTITLE_LANGUAGE = "subtitle_language"
        private const val KEY_AUDIO_LANGUAGE = "audio_language"
        private const val KEY_USE_EXTERNAL_PLAYER = "use_external_player"
        private const val KEY_AUTO_FETCH_ARTWORK_ON_SCAN = "auto_fetch_artwork_on_scan"
        private const val KEY_ENABLE_ONLINE_PLAYBACK = "enable_online_playback"
        private const val KEY_ALLDEBRID_API_KEY = "alldebrid_api_key"
        private const val KEY_ONLINE_AUDIO_MODE = "online_audio_mode"
        private const val KEY_PREFER_SERIES_PACKS = "prefer_series_packs"
        private const val KEY_RSS_FEEDS_TEXT = "rss_feeds_text"
        const val DEFAULT_TMDB_LANGUAGE = "en-GB"
        const val DEFAULT_SUBTITLE_LANGUAGE = "en"
        const val DEFAULT_AUDIO_LANGUAGE = "en"
        const val DEFAULT_ONLINE_AUDIO_MODE = "english_or_multi"
    }
}
