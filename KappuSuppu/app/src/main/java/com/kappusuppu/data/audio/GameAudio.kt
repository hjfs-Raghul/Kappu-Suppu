package com.kappusuppu.data.audio

import kotlinx.coroutines.flow.StateFlow

/** Everything the game needs from audio. The ViewModel talks only to this interface. */
interface GameAudio {
    val soundEnabled: StateFlow<Boolean>

    /** Global toggle: when off, ALL music and effects are muted. */
    fun setSoundEnabled(enabled: Boolean)

    /** A game started: play the subtle suspense loop (if sound is on and the app is in the foreground). */
    fun startGameplayMusic()

    fun playPageFlip()

    /** Loops the pencil-scratching sound while strokes are being drawn. */
    fun setPencilScratching(on: Boolean)

    /** Brief high-tension cue; the suspense loop resumes automatically when it ends. */
    fun playTensionCue()

    /** Game ended or screen exited: stop everything and give up audio focus. */
    fun stopAll()

    fun onAppBackground()
    fun onAppForeground()
    fun release()
}
