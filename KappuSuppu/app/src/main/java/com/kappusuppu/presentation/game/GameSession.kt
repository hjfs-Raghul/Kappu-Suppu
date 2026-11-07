package com.kappusuppu.presentation.game

import com.kappusuppu.domain.actions.GameEvent
import com.kappusuppu.domain.engine.BookSimulator
import com.kappusuppu.domain.engine.EngineResult
import com.kappusuppu.domain.engine.GameEngine
import com.kappusuppu.domain.engine.Spread
import com.kappusuppu.domain.model.Expression
import com.kappusuppu.domain.model.GameState
import com.kappusuppu.domain.model.PendingShot
import com.kappusuppu.domain.model.Phase
import com.kappusuppu.domain.model.PlayerAssignment
import com.kappusuppu.domain.model.PlayerId
import com.kappusuppu.domain.model.Stage
import com.kappusuppu.domain.rules.CharacterMapping
import com.kappusuppu.domain.rules.PageError
import com.kappusuppu.domain.rules.PageParse
import com.kappusuppu.domain.rules.PageRules
import com.kappusuppu.drawing.InkBoard

enum class BookMode { PHYSICAL, SIMULATOR }

enum class NextStep { HANDOFF, TARGET, RESULT }

sealed interface TurnStep {
    /** The current player has to produce a page (type it, or open the simulated book). */
    data object AwaitingPage : TurnStep

    /** Simulator only: the spread is revealed and cannot be re-rolled. */
    data class SpreadRevealed(val spread: Spread) : TurnStep

    /** The page was resolved; the board is animating and shows [message]. */
    data class Feedback(val message: String, val next: NextStep) : TurnStep
}

sealed interface SessionEffect {
    data object PageFlip : SessionEffect
    data object TensionCue : SessionEffect
}

data class SessionState(
    val game: GameState = GameEngine.newGame(),
    val ink: InkBoard = InkBoard.newGame(),
    val assignment: PlayerAssignment = PlayerAssignment(PlayerId.KAPPU),
    val mode: BookMode = BookMode.PHYSICAL,
    val step: TurnStep = TurnStep.AwaitingPage,
    val pageError: PageError? = null,
    /** The shooter of the shot currently being aimed / resolved (kept after the engine clears it). */
    val shotContext: PendingShot? = null,
    val selectedTarget: Int? = null,
    val firing: Boolean = false,
    val fireOutcome: String? = null,
    /** Who performed the last page action (the turn may already have passed to the other player). */
    val actor: PlayerId = PlayerId.KAPPU,
) {
    val currentPlayer: PlayerId get() = game.currentPlayer
    val winner: PlayerId? get() = game.winner
}

data class Transition(val state: SessionState, val effects: List<SessionEffect> = emptyList())

/**
 * Pure (Android-free) description of how a game session moves between turn steps. The ViewModel only
 * forwards user input here, runs the timing delays and plays the requested audio effects.
 */
class GameSession(private val simulator: BookSimulator = BookSimulator()) {

    fun newGame(assignment: PlayerAssignment, mode: BookMode): SessionState =
        SessionState(assignment = assignment, mode = mode)

    // ---- page input -------------------------------------------------------------------------

    fun submitPhysicalText(s: SessionState, text: String): Transition {
        if (s.mode != BookMode.PHYSICAL || s.step != TurnStep.AwaitingPage || s.winner != null) return Transition(s)
        return when (val parsed = PageRules.validate(text)) {
            is PageParse.Invalid -> Transition(s.copy(pageError = parsed.error))
            is PageParse.Valid -> applyPage(s, parsed.page)
        }
    }

    fun clearPageError(s: SessionState): SessionState = s.copy(pageError = null)

    /** Rolls the simulated book exactly once per turn; further calls change nothing. */
    fun openBook(s: SessionState): Transition {
        if (s.mode != BookMode.SIMULATOR || s.step != TurnStep.AwaitingPage || s.winner != null) return Transition(s)
        val spread = simulator.open()
        return Transition(s.copy(step = TurnStep.SpreadRevealed(spread), pageError = null), listOf(SessionEffect.PageFlip))
    }

    fun confirmSpread(s: SessionState): Transition {
        val step = s.step as? TurnStep.SpreadRevealed ?: return Transition(s)
        return applyPage(s, step.spread.evenPage)
    }

    private fun applyPage(s: SessionState, page: Int): Transition {
        val before = s.game
        val result = GameEngine.submitPage(before, page, before.turnNumber)
        if (result !is EngineResult.Applied) return Transition(s)
        val after = result.state
        val next = if (after.phase == Phase.AWAITING_TARGET) NextStep.TARGET else NextStep.HANDOFF
        val effects = result.events.filterIsInstance<GameEvent.BulletLoaded>().map { SessionEffect.TensionCue }
        return Transition(
            s.copy(
                game = after,
                ink = s.ink.apply(result.events),
                step = TurnStep.Feedback(describePage(before.currentPlayer, page, result.events), next),
                pageError = null,
                shotContext = after.pendingShot,
                selectedTarget = null,
                fireOutcome = null,
                actor = before.currentPlayer,
            ),
            effects,
        )
    }

    /** "Done" on the board: go to target selection or hand the phone over. */
    fun continueFromFeedback(s: SessionState): Pair<SessionState, NextStep> {
        val step = s.step as? TurnStep.Feedback ?: return s to NextStep.HANDOFF
        val settled = s.copy(ink = s.ink.completeAll())
        return when (step.next) {
            NextStep.TARGET -> settled to NextStep.TARGET
            else -> settled.copy(
                game = GameEngine.settleExpressions(settled.game),
                step = TurnStep.AwaitingPage,
            ) to NextStep.HANDOFF
        }
    }

    // ---- target selection & shooting ---------------------------------------------------------

    fun selectTarget(s: SessionState, index: Int): SessionState {
        if (s.firing || s.fireOutcome != null || s.game.phase != Phase.AWAITING_TARGET) return s
        return if (index in GameEngine.validTargets(s.game)) s.copy(selectedTarget = index) else s
    }

    /** Cancel returns to the board; the bullet is untouched and the shot is still available. */
    fun cancelTarget(s: SessionState): SessionState =
        if (s.firing || s.fireOutcome != null) s else s.copy(game = GameEngine.cancelShot(s.game), selectedTarget = null)

    /** Step 1 of firing: the shooter shows the cunning face. Ignored when already firing. */
    fun beginFire(s: SessionState): Transition {
        val pending = s.game.pendingShot
        val target = s.selectedTarget
        if (s.firing || s.fireOutcome != null || pending == null || target == null) return Transition(s)
        if (target !in GameEngine.validTargets(s.game)) return Transition(s)
        return Transition(
            s.copy(
                game = GameEngine.setExpression(s.game, pending.shooter, pending.shooterIndex, Expression.CUNNING),
                firing = true,
            ),
        )
    }

    /** Step 2 of firing: the engine applies the shot exactly once. */
    fun completeFire(s: SessionState): Transition {
        val pending = s.game.pendingShot
        val target = s.selectedTarget
        if (!s.firing || s.fireOutcome != null || pending == null || target == null) return Transition(s)
        val result = GameEngine.fireShot(s.game, pending.shooter.opponent, target, s.game.turnNumber)
        if (result !is EngineResult.Applied) {
            return Transition(s.copy(firing = false, selectedTarget = null, fireOutcome = "That shot could not be fired."))
        }
        return Transition(
            s.copy(
                game = result.state,
                ink = s.ink.apply(result.events),
                fireOutcome = describeShot(pending, target, result.events),
            ),
        )
    }

    /** Step 3 of firing: the shocked / cunning faces relax back to normal. */
    fun finishFire(s: SessionState): Transition =
        Transition(s.copy(game = GameEngine.settleExpressions(s.game), firing = false))

    fun continueFromTarget(s: SessionState): Pair<SessionState, NextStep> {
        if (s.firing || s.fireOutcome == null) return s to NextStep.TARGET
        val settled = s.copy(ink = s.ink.completeAll(), game = GameEngine.settleExpressions(s.game))
        if (settled.game.winner != null) return settled to NextStep.RESULT
        return settled.copy(
            step = TurnStep.AwaitingPage,
            shotContext = null,
            selectedTarget = null,
            fireOutcome = null,
        ) to NextStep.HANDOFF
    }

    // ---- text ---------------------------------------------------------------------------------

    private fun describePage(player: PlayerId, page: Int, events: List<GameEvent>): String {
        val index = CharacterMapping.indexForPage(page)
        val head = "Page $page ends in ${CharacterMapping.lastDigit(page)}: ${player.displayName}'s Character ${index + 1}"
        val detail = events.firstNotNullOfOrNull { e ->
            when (e) {
                is GameEvent.Progressed -> when (e.to) {
                    Stage.FACE -> "draws eyes and a mouth."
                    Stage.BODY -> "draws a body, arms and legs."
                    Stage.GUN -> "draws a gun."
                    else -> null
                }
                is GameEvent.BulletLoaded -> "loads a red bullet!"
                is GameEvent.DeadCharacterSkipped -> "is dead. The turn is wasted."
                is GameEvent.ShootingOpportunity -> "has a loaded gun. Pick a target!"
                else -> null
            }
        }
        return "$head $detail"
    }

    private fun describeShot(shot: PendingShot, targetIndex: Int, events: List<GameEvent>): String {
        val target = shot.shooter.opponent
        val who = "${target.displayName}'s Character ${targetIndex + 1}"
        val parts = mutableListOf<String>()
        for (e in events) {
            when (e) {
                is GameEvent.GunDestroyed ->
                    parts += if (e.hadBullet) "$who loses its gun and bullet, back to a plain body!" else "$who loses its gun, back to a plain body!"
                is GameEvent.CharacterKilled -> parts += "$who has no gun and is dead."
                is GameEvent.PlayerEliminated -> parts += "${e.player.displayName} has no characters left."
                is GameEvent.GameWon -> parts += "${e.winner.displayName} wins!"
                else -> Unit
            }
        }
        return parts.joinToString(" ")
    }
}
