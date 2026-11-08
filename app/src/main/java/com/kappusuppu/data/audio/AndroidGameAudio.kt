package com.kappusuppu.data.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.SoundPool
import com.kappusuppu.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Plays the bundled audio (res/raw, no network). One central rule, [applyState], decides what may
 * sound right now, so toggling sound, losing audio focus, backgrounding the app or finishing a cue
 * can never leave overlapping or orphaned playback:
 *
 *   audible = sound enabled && gameplay active && app in foreground && audio focus held
 *
 * - suspense loop: exactly one MediaPlayer instance, created lazily and reused (no overlapping copies)
 * - tension cue: its own MediaPlayer; while it plays the suspense loop is paused and restarts after it
 * - page flip + pencil scratch: SoundPool
 */
class AndroidGameAudio(context: Context) : GameAudio {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("kappu_suppu_settings", Context.MODE_PRIVATE)
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    private val enabledFlow = MutableStateFlow(prefs.getBoolean(KEY_SOUND, true))
    override val soundEnabled: StateFlow<Boolean> = enabledFlow.asStateFlow()

    private var gameplayActive = false
    private var foreground = true
    private var hasFocus = false
    private var ducked = false
    private var pencilWanted = false
    private var cuePlaying = false
    private var released = false

    private var music: MediaPlayer? = null
    private var cue: MediaPlayer? = null

    private val soundPool: SoundPool = SoundPool.Builder()
        .setMaxStreams(3)
        .setAudioAttributes(attributes)
        .build()
    private val flipSound = soundPool.load(appContext, R.raw.page_flip, 1)
    private val pencilSound = soundPool.load(appContext, R.raw.pencil_scratch, 1)
    private val gunSound = soundPool.load(appContext, R.raw.gunshot, 1)
    private val loaded = HashSet<Int>()
    private var pencilStream = 0

    private val focusRequest: AudioFocusRequest =
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener { change ->
                when (change) {
                    AudioManager.AUDIOFOCUS_GAIN -> { hasFocus = true; ducked = false }
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> ducked = true
                    else -> hasFocus = false // LOSS, LOSS_TRANSIENT: pause everything
                }
                applyState()
            }
            .build()

    init {
        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) {
                loaded += sampleId
                applyState()
            }
        }
    }

    // ---- GameAudio ---------------------------------------------------------------------------

    override fun setSoundEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SOUND, enabled).apply()
        enabledFlow.value = enabled
        syncFocus()
        applyState()
    }

    override fun startGameplayMusic() {
        gameplayActive = true
        syncFocus()
        applyState()
    }

    override fun playPageFlip() {
        if (!effectsAllowed() || flipSound !in loaded) return
        soundPool.play(flipSound, 1f, 1f, 1, 0, 1f)
    }

    override fun playGunshot() {
        if (!effectsAllowed() || gunSound !in loaded) return
        soundPool.play(gunSound, 0.9f, 0.9f, 2, 0, 1f)
    }

    override fun setPencilScratching(on: Boolean) {
        pencilWanted = on
        applyState()
    }

    override fun playTensionCue() {
        if (!effectsAllowed()) return
        cue?.let { runCatching { it.release() } }
        cue = null
        val player = createPlayer(R.raw.tension_cue, looping = false, volume = CUE_VOLUME) ?: return
        player.setOnCompletionListener {
            runCatching { it.release() }
            if (cue === it) cue = null
            cuePlaying = false
            applyState() // automatically back to the suspense loop
        }
        cue = player
        cuePlaying = true
        applyState()
    }

    override fun stopAll() {
        gameplayActive = false
        pencilWanted = false
        cuePlaying = false
        cue?.let { runCatching { it.release() } }
        cue = null
        music?.let { runCatching { it.release() } }
        music = null
        stopPencil()
        abandonFocus()
    }

    override fun onAppBackground() {
        foreground = false
        abandonFocus()
        applyState()
    }

    override fun onAppForeground() {
        foreground = true
        syncFocus()
        applyState()
    }

    override fun release() {
        if (released) return
        released = true
        stopAll()
        soundPool.release()
    }

    // ---- internals ---------------------------------------------------------------------------

    private fun effectsAllowed() = !released && enabledFlow.value && gameplayActive && foreground

    private fun audible() = effectsAllowed() && hasFocus

    private fun syncFocus() {
        if (released) return
        if (enabledFlow.value && gameplayActive && foreground) {
            if (!hasFocus) {
                hasFocus = audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            }
        } else {
            abandonFocus()
        }
    }

    private fun abandonFocus() {
        if (hasFocus || !released) {
            runCatching { audioManager.abandonAudioFocusRequest(focusRequest) }
        }
        hasFocus = false
        ducked = false
    }

    private fun createPlayer(rawId: Int, looping: Boolean, volume: Float): MediaPlayer? = runCatching {
        val player = MediaPlayer()
        player.setAudioAttributes(attributes)
        appContext.resources.openRawResourceFd(rawId).use { fd ->
            player.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
        }
        player.isLooping = looping
        player.setVolume(volume, volume)
        player.prepare()
        player
    }.getOrNull()

    private fun ensureMusic(): MediaPlayer? {
        val existing = music
        if (existing != null) return existing
        val player = createPlayer(R.raw.suspense_loop, looping = true, volume = MUSIC_VOLUME)
        music = player
        return player
    }

    private fun applyState() {
        if (released) return
        if (!audible()) {
            runCatching { music?.takeIf { it.isPlaying }?.pause() }
            runCatching { cue?.takeIf { it.isPlaying }?.pause() }
            stopPencil()
            return
        }
        val gain = if (ducked) DUCK_FACTOR else 1f
        if (cuePlaying && cue != null) {
            // high-tension cue replaces the suspense loop until it ends
            runCatching { music?.takeIf { it.isPlaying }?.pause() }
            cue?.let { c ->
                runCatching {
                    c.setVolume(CUE_VOLUME * gain, CUE_VOLUME * gain)
                    if (!c.isPlaying) c.start()
                }
            }
        } else {
            ensureMusic()?.let { m ->
                runCatching {
                    m.setVolume(MUSIC_VOLUME * gain, MUSIC_VOLUME * gain)
                    if (!m.isPlaying) m.start()
                }
            }
        }
        if (pencilWanted && pencilSound in loaded) {
            if (pencilStream == 0) pencilStream = soundPool.play(pencilSound, 0.8f * gain, 0.8f * gain, 1, -1, 1f)
        } else {
            stopPencil()
        }
    }

    private fun stopPencil() {
        if (pencilStream != 0) {
            soundPool.stop(pencilStream)
            pencilStream = 0
        }
    }

    private companion object {
        const val KEY_SOUND = "sound_enabled"
        const val MUSIC_VOLUME = 0.55f
        const val CUE_VOLUME = 0.8f
        const val DUCK_FACTOR = 0.3f
    }
}
