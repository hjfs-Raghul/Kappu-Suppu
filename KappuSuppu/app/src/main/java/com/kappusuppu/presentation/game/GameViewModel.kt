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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Holds the one in-memory game. Nothing is persisted: when the process ends the game is gone.
 * All rules live in [GameSession] / the domain engine; this class only forwards input, runs the
 * short animation delays of a shot and triggers audio.
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

    private var fireJob: Job? = null

    private val boardVisible = MutableStateFlow(false)

    init {
        // pencil scratching plays exactly while strokes are being drawn on a visible board
        viewModelScope.launch {
            combine(_state.map { it.ink.isDrawing }.distinctUntilChanged(), boardVisible) { drawing, visible -> drawing && visible }
                .distinctUntilChanged()
                .collect { audio.setPencilScratching(it) }
        }
    }

    /** Called by the navigation layer while a screen that shows characters is on screen. */
    fun setBoardVisible(visible: Boolean) {
        boardVisible.value = visible
    }

    // ---- settings / setup --------------------------------------------------------------------

    fun toggleSound() = audio.setSoundEnabled(!audio.soundEnabled.value)

    fun chooseRole(player1: PlayerId) {
        _roleChoice.value = player1
    }

    fun startNewGame(mode: BookMode) {
        fireJob?.cancel()
        val assignment = PlayerAssignment(_roleChoice.value ?: PlayerId.KAPPU)
        _state.value = session.newGame(assignment, mode)
        audio.startGameplayMusic()
    }

    fun playAgain() = startNewGame(_state.value.mode)

    fun exitToHome() {
        fireJob?.cancel()
        audio.stopAll()
        _state.value = SessionState()
    }

    /** The game is over: stop every sound. */
    fun onResultShown() {
        audio.stopAll()
    }

    // ---- turn input --------------------------------------------------------------------------

    fun submitPhysicalPage(text: String) = commit(session.submitPhysicalText(_state.value, text))

    fun clearPageError() {
        _state.update { session.clearPageError(it) }
    }

    fun openBook() = commit(session.openBook(_state.value))

    fun confirmSpread() = commit(session.confirmSpread(_state.value))

    /** "Done" on the board. Returns where to go next. */
    fun continueFromFeedback(): NextStep {
        val (next, step) = session.continueFromFeedback(_state.value)
        _state.value = next
        return step
    }

    // ---- target selection & shooting ---------------------------------------------------------

    fun selectTarget(index: Int) {
        _state.update { session.selectTarget(it, index) }
    }

    fun cancelTarget() {
        _state.update { session.cancelTarget(it) }
    }

    fun confirmFire() {
        if (fireJob?.isActive == true) return // rapid taps while a shot is in flight
        val begin = session.beginFire(_state.value)
        if (!begin.state.firing) return
        commit(begin)
        fireJob = viewModelScope.launch {
            delay(CUNNING_MS) // the shooter's cunning face is visible before the shot
            commit(session.completeFire(_state.value))
            delay(REACTION_MS) // shocked reaction / gun destruction plays
            commit(session.finishFire(_state.value))
        }
    }

    fun continueFromTarget(): NextStep {
        val (next, step) = session.continueFromTarget(_state.value)
        _state.value = next
        return step
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
            }
        }
    }

    private companion object {
        const val CUNNING_MS = 900L
        const val REACTION_MS = 1100L
    }
}
