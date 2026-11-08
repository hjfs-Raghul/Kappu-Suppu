package com.kappusuppu.presentation.game

import com.kappusuppu.domain.actions.GameEvent
import com.kappusuppu.domain.engine.BookSimulator
import com.kappusuppu.domain.engine.EngineResult
import com.kappusuppu.domain.engine.GameEngine
import com.kappusuppu.domain.engine.Spread
import com.kappusuppu.domain.model.Expression
import com.kappusuppu.domain.model.GameState
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

/** Where the current turn is. The whole game happens on the one board screen. */
sealed interface TurnStep {
    /** The current player's book button is enabled; the other player's is not. */
    data object AwaitingPage : TurnStep

    /** Simulator only: the spread is shown. It cannot be re-rolled and is applied automatically. */
    data class SpreadRevealed(val spread: Spread) : TurnStep

    /** The page was applied; the pencil / eraser animations are playing. */
    data object Resolving : TurnStep

    /** The shooter must tap one of the highlighted living enemy characters. */
    data object AwaitingTarget : TurnStep

    /** A shot is in progress (aim, bullet flight, impact). */
    data object Firing : TurnStep

    data object GameOver : TurnStep
}

enum class ShotStage {
    /** The shooter shows the cunning face. */
    AIMING,

    /** Gun fires, bullet flies to the target. */
    FLYING,

    /** The bullet has hit: the gun breaks, or the character is shot dead. */
    IMPACT,
}

/**
 * A shot in progress. The engine result is computed when the target is tapped but only committed
 * to the game state at impact, so the board never shows the damage before the bullet arrives.
 */
data class ShotAnimation(
    val shooter: PlayerId,
    val shooterIndex: Int,
    val target: PlayerId,
    val targetIndex: Int,
    val targetHadGun: Boolean,
    val stage: ShotStage,
    val pendingState: GameState,
    val pendingEvents: List<GameEvent>,
    val message: String,
)

sealed interface SessionEffect {
    data object PageFlip : SessionEffect
    data object TensionCue : SessionEffect
    data object Gunshot : SessionEffect
}

/** Animation timings shared by the view model (sequencing) and the board overlay (drawing). */
object GameTiming {
    const val SPREAD_MS = 1800L
    const val MIN_RESOLVE_MS = 700L
    const val CUNNING_MS = 900L
    const val FLIGHT_MS = 650L
    const val IMPACT_MS = 900L
    const val ANIMATION_WAIT_MS = 6000L
}

data class SessionState(
    val game: GameState = GameEngine.newGame(),
    val ink: InkBoard = InkBoard.newGame(),
    val assignment: PlayerAssignment = PlayerAssignment(PlayerId.KAPPU),
    val mode: BookMode = BookMode.PHYSICAL,
    val step: TurnStep = TurnStep.AwaitingPage,
    val pageError: PageError? = null,
    val shot: ShotAnimation? = null,
    val lastMessage: String = "",
    /** The player whose turn is being played out (the engine may already have moved on to the next). */
    val actor: PlayerId = PlayerId.KAPPU,
) {
    val currentPlayer: PlayerId get() = game.currentPlayer
    val winner: PlayerId? get() = game.winner

    /** Only the player whose turn it is may open a page, and only when nothing is animating. */
    fun canOpenBook(player: PlayerId): Boolean =
        step == TurnStep.AwaitingPage && game.winner == null && game.currentPlayer == player
}

data class Transition(val state: SessionState, val effects: List<SessionEffect> = emptyList())

/**
 * Pure (Android-free) description of how a game session moves between turn steps. The ViewModel only
 * forwards input here, waits the animation delays and plays the requested audio effects.
 */
class GameSession(private val simulator: BookSimulator = BookSimulator()) {

    fun newGame(assignment: PlayerAssignment, mode: BookMode): SessionState =
        SessionState(assignment = assignment, mode = mode)

    // ---- page input -------------------------------------------------------------------------

    fun submitPhysicalText(s: SessionState, player: PlayerId, text: String): Transition {
        if (s.mode != BookMode.PHYSICAL || !s.canOpenBook(player)) return Transition(s)
        return when (val parsed = PageRules.validate(text)) {
            is PageParse.Invalid -> Transition(s.copy(pageError = parsed.error))
            is PageParse.Valid -> applyPage(s, parsed.page)
        }
    }

    fun clearPageError(s: SessionState): SessionState = s.copy(pageError = null)

    /** Rolls the simulated book exactly once per turn; any further tap changes nothing. */
    fun openBook(s: SessionState, player: PlayerId): Transition {
        if (s.mode != BookMode.SIMULATOR || !s.canOpenBook(player)) return Transition(s)
        val spread = simulator.open()
        return Transition(
            s.copy(step = TurnStep.SpreadRevealed(spread), pageError = null, actor = player),
            listOf(SessionEffect.PageFlip),
        )
    }

    /** Called automatically a moment after the spread is shown. */
    fun confirmSpread(s: SessionState): Transition {
        val step = s.step as? TurnStep.SpreadRevealed ?: return Transition(s)
        return applyPage(s, step.spread.evenPage)
    }

    private fun applyPage(s: SessionState, page: Int): Transition {
        val before = s.game
        val result = GameEngine.submitPage(before, page, before.turnNumber)
        if (result !is EngineResult.Applied) return Transition(s)
        val effects = result.events.filterIsInstance<GameEvent.BulletLoaded>().map { SessionEffect.TensionCue }
        return Transition(
            s.copy(
                game = result.state,
                ink = s.ink.apply(result.events),
                step = TurnStep.Resolving,
                pageError = null,
                shot = null,
                lastMessage = describePage(before.currentPlayer, page, result.events),
                actor = before.currentPlayer,
            ),
            effects,
        )
    }

    /**
     * The animations are over: settle the faces, then hand the turn to the next player (their book
     * becomes available), open target selection, or end the game.
     */
    fun finishResolving(s: SessionState): SessionState {
        if (s.step != TurnStep.Resolving) return s
        val ink = s.ink.completeAll()
        val settled = GameEngine.settleExpressions(s.game)
        return when {
            settled.winner != null -> s.copy(game = settled, ink = ink, step = TurnStep.GameOver, shot = null)
            settled.phase == Phase.AWAITING_TARGET ->
                s.copy(game = settled, ink = ink, step = TurnStep.AwaitingTarget, shot = null)
            else -> s.copy(game = settled, ink = ink, step = TurnStep.AwaitingPage, shot = null, actor = settled.currentPlayer)
        }
    }

    // ---- shooting ---------------------------------------------------------------------------

    /** Living enemy characters the shooter may tap right now (empty unless a shot is pending). */
    fun targets(s: SessionState): List<Int> =
        if (s.step == TurnStep.AwaitingTarget) GameEngine.validTargets(s.game) else emptyList()

    /**
     * Tapping a highlighted enemy fires at once. Only the first tap counts: the step leaves
     * AwaitingTarget immediately, so rapid extra taps are ignored here and by the engine.
     */
    fun fireAt(s: SessionState, targetIndex: Int): Transition {
        val pending = s.game.pendingShot
        if (s.step != TurnStep.AwaitingTarget || pending == null) return Transition(s)
        if (targetIndex !in GameEngine.validTargets(s.game)) return Transition(s)
        val targetPlayer = pending.shooter.opponent
        val hadGun = s.game.character(targetPlayer, targetIndex).hasGun
        val result = GameEngine.fireShot(s.game, targetPlayer, targetIndex, s.game.turnNumber)
        if (result !is EngineResult.Applied) return Transition(s)
        val animation = ShotAnimation(
            shooter = pending.shooter,
            shooterIndex = pending.shooterIndex,
            target = targetPlayer,
            targetIndex = targetIndex,
            targetHadGun = hadGun,
            stage = ShotStage.AIMING,
            pendingState = result.state,
            pendingEvents = result.events,
            message = describeShot(targetPlayer, targetIndex, result.events),
        )
        return Transition(
            s.copy(
                game = GameEngine.setExpression(s.game, pending.shooter, pending.shooterIndex, Expression.CUNNING),
                step = TurnStep.Firing,
                shot = animation,
            ),
        )
    }

    /** Step 2: the gun fires. The bullet leaves the barrel (erased from the shooter's gun). */
    fun launch(s: SessionState): Transition {
        val shot = s.shot ?: return Transition(s)
        if (s.step != TurnStep.Firing || shot.stage != ShotStage.AIMING) return Transition(s)
        return Transition(
            s.copy(
                ink = s.ink.apply(shot.pendingEvents.filterIsInstance<GameEvent.ShotFired>()),
                shot = shot.copy(stage = ShotStage.FLYING),
            ),
            listOf(SessionEffect.Gunshot),
        )
    }

    /** Step 3: the bullet arrives. The engine result is committed and its drawing changes start. */
    fun impact(s: SessionState): Transition {
        val shot = s.shot ?: return Transition(s)
        if (s.step != TurnStep.Firing || shot.stage != ShotStage.FLYING) return Transition(s)
        return Transition(
            s.copy(
                game = shot.pendingState,
                ink = s.ink.apply(shot.pendingEvents.filterNot { it is GameEvent.ShotFired }),
                shot = shot.copy(stage = ShotStage.IMPACT),
                step = TurnStep.Resolving,
                lastMessage = shot.message,
            ),
        )
    }

    // ---- text ---------------------------------------------------------------------------------

    private fun describePage(player: PlayerId, page: Int, events: List<GameEvent>): String {
        val head = "${player.displayName} opened page $page (digit ${CharacterMapping.lastDigit(page)}): "
        val detail = events.firstNotNullOfOrNull { e ->
            when (e) {
                is GameEvent.Progressed -> when (e.to) {
                    Stage.CIRCLE -> "a circle is drawn."
                    Stage.FACE -> "eyes and a mouth are drawn."
                    Stage.BODY -> "a body, arms and legs are drawn."
                    Stage.GUN -> "a gun is drawn."
                    else -> null
                }
                is GameEvent.BulletLoaded -> "the gun is loaded with a red bullet!"
                is GameEvent.DeadCharacterSkipped -> "that character is dead, so the turn is lost."
                is GameEvent.ShootingOpportunity -> "loaded gun! Tap an enemy to shoot."
                else -> null
            }
        }
        return head + detail
    }

    private fun describeShot(target: PlayerId, targetIndex: Int, events: List<GameEvent>): String {
        val who = "${target.displayName}'s ${CharacterMapping.digitForIndex(targetIndex)}"
        val parts = mutableListOf<String>()
        for (e in events) {
            when (e) {
                is GameEvent.GunDestroyed ->
                    parts += if (e.hadBullet) "$who loses its gun and bullet!" else "$who loses its gun!"
                is GameEvent.CharacterKilled -> parts += "$who has no gun and is shot dead."
                is GameEvent.PlayerEliminated -> parts += "${e.player.displayName} has no characters left."
                is GameEvent.GameWon -> parts += "${e.winner.displayName} wins!"
                else -> Unit
            }
        }
        return parts.joinToString(" ")
    }
}
