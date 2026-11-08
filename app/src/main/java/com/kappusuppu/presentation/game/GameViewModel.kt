package com.kappusuppu.presentation.game

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kappusuppu.data.audio.AndroidGameAudio
import com.kappusuppu.data.audio.GameAudio
import com.kappusuppu.domain.model.PlayerAssignment
import com.kappusuppu.domain.model.PlayerId
import com.kappusuppu.drawing.CharKey
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Holds the one in-memory game. Nothing is persisted: when the process ends the game is gone.
 * All rules live in [GameSession] / the domain engine. This class forwards input, waits out the
 * short animation delays of a turn and triggers audio.
 */
class GameViewModel(application: Application) : AndroidViewModel(application) {

    private val audio: GameAudio = AndroidGameAudio(application)
    private val session = GameSession()

    private val _state = MutableStateFlow(SessionState())
    val state: StateFlow<SessionState> = _state.asStateFlow()

    private val _roleChoice = MutableStateFlow<PlayerId?>(null)

    /** Which identity Player 1 picked on the role screen (null until chosen). */
    val roleChoice: StateFlow<PlayerId?> = _roleChoice.asStateFlow()

    val soundEnabled: StateFlow<Boolean> get() = audio.soundEnabled

    /** The one running turn sequence (spread -> apply, or aim -> fire -> impact -> settle). */
    private var turnJob: Job? = null

    private val boardVisible = MutableStateFlow(false)

    init {
        // pencil scratching plays exactly while strokes are being drawn on a visible board
        viewModelScope.launch {
            combine(_state.map { it.ink.isDrawing }.distinctUntilChanged(), boardVisible) { drawing, visible -> drawing && visible }
                .distinctUntilChanged()
                .collect { audio.setPencilScratching(it) }
        }
    }

    /** Called by the navigation layer while the board is on screen. */
    fun setBoardVisible(visible: Boolean) {
        boardVisible.value = visible
    }

    // ---- settings / setup --------------------------------------------------------------------

    fun toggleSound() = audio.setSoundEnabled(!audio.soundEnabled.value)

    fun chooseRole(player1: PlayerId) {
        _roleChoice.value = player1
    }

    fun startNewGame(mode: BookMode) {
        turnJob?.cancel()
        val assignment = PlayerAssignment(_roleChoice.value ?: PlayerId.KAPPU)
        _state.value = session.newGame(assignment, mode)
        audio.startGameplayMusic()
    }

    fun playAgain() = startNewGame(_state.value.mode)

    fun exitToHome() {
        turnJob?.cancel()
        audio.stopAll()
        _state.value = SessionState()
    }

    /** The game is over: stop every sound. */
    fun onResultShown() {
        audio.stopAll()
    }

    // ---- turn input --------------------------------------------------------------------------

    /** Physical book: the player typed an even page number. A valid page is applied immediately. */
    fun submitPhysicalPage(player: PlayerId, text: String) {
        if (turnJob?.isActive == true) return
        val t = session.submitPhysicalText(_state.value, player, text)
        commit(t)
        if (t.state.step == TurnStep.Resolving) {
            turnJob = viewModelScope.launch { settle() }
        }
    }

    fun clearPageError() {
        _state.update { session.clearPageError(it) }
    }

    /** Book simulator: shows the spread, then applies its even page by itself. */
    fun openBook(player: PlayerId) {
        if (turnJob?.isActive == true) return
        val t = session.openBook(_state.value, player)
        if (t.state.step !is TurnStep.SpreadRevealed) return
        commit(t)
        turnJob = viewModelScope.launch {
            delay(GameTiming.SPREAD_MS)
            commit(session.confirmSpread(_state.value))
            settle()
        }
    }

    // ---- shooting ----------------------------------------------------------------------------

    /** A highlighted enemy was tapped: aim (cunning face), fire (gunshot), hit, then settle. */
    fun fireAt(targetIndex: Int) {
        if (turnJob?.isActive == true) return // rapid extra taps while a shot is in flight
        val t = session.fireAt(_state.value, targetIndex)
        if (t.state.step != TurnStep.Firing) return
        commit(t)
        turnJob = viewModelScope.launch {
            delay(GameTiming.CUNNING_MS)
            commit(session.launch(_state.value))
            delay(GameTiming.FLIGHT_MS)
            commit(session.impact(_state.value))
            delay(GameTiming.IMPACT_MS)
            settle()
        }
    }

    /** Waits until the pencil / eraser animations are done, then lets the next step begin. */
    private suspend fun settle() {
        delay(GameTiming.MIN_RESOLVE_MS)
        withTimeoutOrNull(GameTiming.ANIMATION_WAIT_MS) { _state.first { !it.ink.isAnimating } }
        _state.update { session.finishResolving(it) }
    }

    // ---- drawing callbacks -------------------------------------------------------------------

    fun onStrokeDrawn(key: CharKey, id: Long) {
        _state.update { it.copy(ink = it.ink.markDrawn(key, id)) }
    }

    fun onStrokesErased(key: CharKey, ids: Set<Long>) {
        _state.update { it.copy(ink = it.ink.removeErased(key, ids)) }
    }

    // ---- lifecycle ---------------------------------------------------------------------------

    fun onAppBackground() = audio.onAppBackground()

    fun onAppForeground() = audio.onAppForeground()

    override fun onCleared() {
        audio.release()
        super.onCleared()
    }

    private fun commit(transition: Transition) {
        _state.value = transition.state
        for (effect in transition.effects) {
            when (effect) {
                SessionEffect.PageFlip -> audio.playPageFlip()
                SessionEffect.TensionCue -> audio.playTensionCue()
                SessionEffect.Gunshot -> audio.playGunshot()
            }
        }
    }
}
