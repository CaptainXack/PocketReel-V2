package com.captainxack.pocketreel

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
import android.view.Gravity
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.captainxack.pocketreel.data.PlaybackStateStore
import com.captainxack.pocketreel.data.SettingsStore

class PlayerActivity : ComponentActivity() {
    private var player: ExoPlayer? = null
    private var playerView: PlayerView? = null
    private var trailerWebView: WebView? = null
    private var documentUri: String? = null
    private var playbackStateStore: PlaybackStateStore? = null
    private var countdownTimer: CountDownTimer? = null
    private var autoplayDialog: AlertDialog? = null
    private var currentSeriesUris: ArrayList<String> = arrayListOf()
    private var currentSeriesTitles: ArrayList<String> = arrayListOf()
    private var currentSeriesIndex: Int = -1
    private var autoplayStreak: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val uriString = intent.getStringExtra(EXTRA_URI)
        if (uriString.isNullOrBlank()) {
            Toast.makeText(this, "Missing media file", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        documentUri = uriString
        currentSeriesUris = intent.getStringArrayListExtra(EXTRA_SERIES_URIS) ?: arrayListOf()
        currentSeriesTitles = intent.getStringArrayListExtra(EXTRA_SERIES_TITLES) ?: arrayListOf()
        currentSeriesIndex = intent.getIntExtra(EXTRA_SERIES_INDEX, -1)
        autoplayStreak = intent.getIntExtra(EXTRA_AUTOPLAY_STREAK, 0)
        title = intent.getStringExtra(EXTRA_TITLE) ?: "Now Playing"

        if (isWebTrailerUrl(uriString)) {
            setContentView(createTrailerWebView(uriString))
            return
        }

        val settingsStore = SettingsStore(this)
        playbackStateStore = PlaybackStateStore(this)
        val savedPlaybackState = playbackStateStore?.getState(uriString)
        val subtitleLanguage = settingsStore.getPreferredSubtitleLanguage()
        val audioLanguage = settingsStore.getPreferredAudioLanguage()
        val mediaUri = Uri.parse(uriString)

        val root = FrameLayout(this)
        playerView = PlayerView(this).apply {
            useController = true
            controllerAutoShow = true
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            )
            setShowSubtitleButton(true)
        }
        root.addView(playerView)
        root.addView(createOverlayControls())
        setContentView(root)

        player = ExoPlayer.Builder(this).build().also { exoPlayer ->
            val trackParameters = TrackSelectionParameters.Builder()
                .setPreferredAudioLanguage(audioLanguage)
                .setPreferredTextLanguage(subtitleLanguage)
                .setSelectUndeterminedTextLanguage(true)
                .build()

            exoPlayer.trackSelectionParameters = trackParameters
            exoPlayer.addListener(
                object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_ENDED) {
                            documentUri?.let { playbackStateStore?.markWatched(it, exoPlayer.duration.coerceAtLeast(0L)) }
                            handleEpisodeEnded()
                        }
                    }
                },
            )
            playerView?.player = exoPlayer
            exoPlayer.setMediaItem(MediaItem.fromUri(mediaUri))
            exoPlayer.prepare()
            if (savedPlaybackState != null && !savedPlaybackState.watched && savedPlaybackState.resumePositionMs > 0L) {
                exoPlayer.seekTo(savedPlaybackState.resumePositionMs)
            }
            exoPlayer.playWhenReady = true
        }
    }

    override fun onStart() {
        super.onStart()
        player?.playWhenReady = true
        trailerWebView?.onResume()
    }

    override fun onStop() {
        persistPlaybackState()
        player?.playWhenReady = false
        trailerWebView?.onPause()
        countdownTimer?.cancel()
        autoplayDialog?.dismiss()
        super.onStop()
    }

    override fun onDestroy() {
        persistPlaybackState()
        countdownTimer?.cancel()
        autoplayDialog?.dismiss()
        playerView?.player = null
        player?.release()
        player = null
        playerView = null
        trailerWebView?.apply {
            stopLoading()
            loadUrl("about:blank")
            clearHistory()
            removeAllViews()
            destroy()
        }
        trailerWebView = null
        super.onDestroy()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createTrailerWebView(uriString: String): FrameLayout {
        val root = FrameLayout(this)
        trailerWebView = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            webChromeClient = WebChromeClient()
            webViewClient = WebViewClient()
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.loadsImagesAutomatically = true
            loadUrl(buildTrailerEmbedUrl(uriString))
        }
        root.addView(trailerWebView)
        root.addView(
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.END
                setPadding(24, 40, 24, 24)
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP,
                )
                addView(makeOverlayButton("Done") { finish() })
            },
        )
        return root
    }

    private fun createOverlayControls(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(24, 40, 24, 24)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP,
            )

            if (isEpisodePlayback()) {
                addView(makeOverlayButton("Skip Intro") {
                    player?.let { exoPlayer ->
                        val duration = exoPlayer.duration.takeIf { it > 0L }
                        val target = exoPlayer.currentPosition + INTRO_SKIP_MS
                        exoPlayer.seekTo(duration?.let { target.coerceAtMost(it) } ?: target)
                    }
                })
                addView(makeOverlayButton("Skip Credits") {
                    player?.let { exoPlayer ->
                        val duration = exoPlayer.duration.takeIf { it > 0L } ?: return@let
                        exoPlayer.seekTo((duration - CREDITS_SKIP_FROM_END_MS).coerceAtLeast(exoPlayer.currentPosition))
                    }
                })
            }

            if (hasNextEpisode()) {
                addView(makeOverlayButton("Next Episode") {
                    launchNextEpisode(autoStarted = false)
                })
            }
        }
    }

    private fun makeOverlayButton(text: String, action: () -> Unit): Button {
        return Button(this).apply {
            this.text = text
            setOnClickListener { action() }
        }
    }

    private fun isEpisodePlayback(): Boolean = currentSeriesIndex >= 0 && currentSeriesUris.isNotEmpty()

    private fun hasNextEpisode(): Boolean = currentSeriesIndex >= 0 && currentSeriesIndex < currentSeriesUris.lastIndex

    private fun handleEpisodeEnded() {
        if (!hasNextEpisode()) return
        if (autoplayStreak >= BINGE_WARNING_THRESHOLD) {
            showStillWatchingPrompt()
        } else {
            showUpNextCountdown()
        }
    }

    private fun showUpNextCountdown() {
        countdownTimer?.cancel()
        autoplayDialog?.dismiss()

        val dialog = AlertDialog.Builder(this)
            .setTitle("Up next")
            .setMessage("Next episode starts in 8 seconds")
            .setPositiveButton("Play now") { _, _ -> launchNextEpisode(autoStarted = true) }
            .setNegativeButton("Cancel", null)
            .create()
        autoplayDialog = dialog
        dialog.show()

        countdownTimer = object : CountDownTimer(8_000L, 1_000L) {
            override fun onTick(millisUntilFinished: Long) {
                dialog.setMessage("Next episode starts in ${millisUntilFinished / 1000L} seconds")
            }

            override fun onFinish() {
                if (dialog.isShowing) {
                    dialog.dismiss()
                }
                launchNextEpisode(autoStarted = true)
            }
        }.start()
    }

    private fun showStillWatchingPrompt() {
        countdownTimer?.cancel()
        autoplayDialog?.dismiss()
        autoplayDialog = AlertDialog.Builder(this)
            .setTitle("Still watching?")
            .setMessage("PocketReel can start the next episode when you are ready.")
            .setPositiveButton("Continue") { _, _ -> launchNextEpisode(autoStarted = true) }
            .setNegativeButton("Stop", null)
            .create()
        autoplayDialog?.show()
    }

    private fun launchNextEpisode(autoStarted: Boolean) {
        if (!hasNextEpisode()) return
        persistPlaybackState()

        val nextIndex = currentSeriesIndex + 1
        val nextUri = currentSeriesUris[nextIndex]
        val nextTitle = currentSeriesTitles.getOrNull(nextIndex) ?: "Next Episode"

        startActivity(Intent(this, PlayerActivity::class.java).apply {
            putExtra(EXTRA_URI, nextUri)
            putExtra(EXTRA_TITLE, nextTitle)
            putStringArrayListExtra(EXTRA_SERIES_URIS, ArrayList(currentSeriesUris))
            putStringArrayListExtra(EXTRA_SERIES_TITLES, ArrayList(currentSeriesTitles))
            putExtra(EXTRA_SERIES_INDEX, nextIndex)
            putExtra(EXTRA_AUTOPLAY_STREAK, if (autoStarted) autoplayStreak + 1 else 0)
        })
        finish()
    }

    private fun persistPlaybackState() {
        val uri = documentUri ?: return
        if (isWebTrailerUrl(uri)) return
        val exoPlayer = player ?: return
        val duration = exoPlayer.duration.takeIf { it > 0L } ?: 0L
        val position = exoPlayer.currentPosition.takeIf { it > 0L } ?: 0L
        playbackStateStore?.saveProgress(uri, position, duration)
    }

    private fun isWebTrailerUrl(value: String): Boolean {
        val lower = value.lowercase()
        return lower.startsWith("http://") || lower.startsWith("https://")
    }

    private fun buildTrailerEmbedUrl(value: String): String {
        val uri = Uri.parse(value)
        val host = uri.host.orEmpty().lowercase()
        return when {
            host.contains("youtube.com") || host.contains("youtu.be") -> {
                val id = uri.getQueryParameter("v") ?: uri.lastPathSegment.orEmpty().substringAfterLast('/')
                "https://www.youtube.com/embed/$id?autoplay=1&playsinline=1&rel=0"
            }
            host.contains("vimeo.com") -> {
                val id = uri.lastPathSegment.orEmpty().substringAfterLast('/')
                "https://player.vimeo.com/video/$id?autoplay=1"
            }
            else -> value
        }
    }

    companion object {
        const val EXTRA_URI = "extra_uri"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_SERIES_URIS = "extra_series_uris"
        const val EXTRA_SERIES_TITLES = "extra_series_titles"
        const val EXTRA_SERIES_INDEX = "extra_series_index"
        const val EXTRA_AUTOPLAY_STREAK = "extra_autoplay_streak"

        private const val INTRO_SKIP_MS = 85_000L
        private const val CREDITS_SKIP_FROM_END_MS = 45_000L
        private const val BINGE_WARNING_THRESHOLD = 2
    }
}
