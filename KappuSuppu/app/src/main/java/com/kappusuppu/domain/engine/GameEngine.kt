package com.kappusuppu.domain.engine

import com.kappusuppu.domain.actions.GameEvent
import com.kappusuppu.domain.actions.TurnAction
import com.kappusuppu.domain.actions.TurnRules
import com.kappusuppu.domain.model.CHARACTERS_PER_PLAYER
import com.kappusuppu.domain.model.Expression
import com.kappusuppu.domain.model.GameState
import com.kappusuppu.domain.model.PendingShot
import com.kappusuppu.domain.model.Phase
import com.kappusuppu.domain.model.PlayerId
import com.kappusuppu.domain.rules.CharacterMapping
import com.kappusuppu.domain.rules.PageParse
import com.kappusuppu.domain.rules.PageRules

enum class RejectReason {
    GAME_OVER,
    NOT_AWAITING_PAGE,
    NOT_AWAITING_TARGET,
    STALE_TURN,
    INVALID_PAGE,
    INVALID_TARGET_INDEX,
    SELF_TARGET,
    DEAD_TARGET,
}

sealed interface EngineResult {
    data class Applied(val state: GameState, val events: List<GameEvent>) : EngineResult
    data class Rejected(val reason: RejectReason) : EngineResult
}

/**
 * Pure, immutable game engine. No Android, no UI. Every operation takes a state and returns a
 * new state (or a rejection) so that rules are fully testable.
 *
 * Duplicate-input protection: every state-changing call carries the `turnNumber` the caller was
 * looking at. A repeated tap re-sends the same turn number and is rejected, because the first call
 * already moved the game forward (new phase or new turn).
 */
object GameEngine {

    fun newGame(): GameState = GameState.newGame()

    /** Which action would the current player's character get for this page? Null if the page is invalid. */
    fun previewAction(state: GameState, page: Int): TurnAction? {
        if (state.phase != Phase.AWAITING_PAGE || !PageRules.isValid(page)) return null
        val character = state.character(state.currentPlayer, CharacterMapping.indexForPage(page))
        return TurnRules.actionFor(character)
    }

    /** Whether resolving this page needs the manual target-selection step. */
    fun needsTargetSelection(state: GameState, page: Int): Boolean =
        previewAction(state, page) == TurnAction.OfferShot

    fun submitPage(state: GameState, page: Int, expectedTurn: Int): EngineResult {
        when (state.phase) {
            Phase.GAME_OVER -> return EngineResult.Rejected(RejectReason.GAME_OVER)
            Phase.AWAITING_TARGET -> return EngineResult.Rejected(RejectReason.NOT_AWAITING_PAGE)
            Phase.AWAITING_PAGE -> Unit
        }
        if (expectedTurn != state.turnNumber) return EngineResult.Rejected(RejectReason.STALE_TURN)
        if (PageRules.validate(page) !is PageParse.Valid) return EngineResult.Rejected(RejectReason.INVALID_PAGE)

        val player = state.currentPlayer
        val index = CharacterMapping.indexForPage(page)
        val character = state.character(player, index)

        return when (val action = TurnRules.actionFor(character)) {
            TurnAction.DeadCharacter -> EngineResult.Applied(
                advanceTurn(state),
                listOf(GameEvent.DeadCharacterSkipped(player, index)),
            )

            is TurnAction.Progress -> EngineResult.Applied(
                advanceTurn(state.updateCharacter(player, index) { it.advance() }),
                listOf(GameEvent.Progressed(player, index, action.to)),
            )

            TurnAction.LoadBullet -> EngineResult.Applied(
                advanceTurn(state.updateCharacter(player, index) { it.advance() }),
                listOf(GameEvent.BulletLoaded(player, index)),
            )

            // Shooting opportunity: the turn is NOT finished until the shot is confirmed.
            TurnAction.OfferShot -> EngineResult.Applied(
                state.copy(pendingShot = PendingShot(player, index)),
                listOf(GameEvent.ShootingOpportunity(player, index)),
            )
        }
    }

    /** Living opponent characters that may be selected right now. Empty unless a shot is pending. */
    fun validTargets(state: GameState): List<Int> {
        val pending = state.pendingShot ?: return emptyList()
        return state.player(pending.shooter.opponent).characters.filter { it.alive }.map { it.index }
    }

    /** Cancelling target selection changes nothing: the bullet is not consumed and no shot is committed. */
    fun cancelShot(state: GameState): GameState = state

    fun fireShot(state: GameState, targetPlayer: PlayerId, targetIndex: Int, expectedTurn: Int): EngineResult {
        if (state.phase == Phase.GAME_OVER) return EngineResult.Rejected(RejectReason.GAME_OVER)
        val pending = state.pendingShot ?: return EngineResult.Rejected(RejectReason.NOT_AWAITING_TARGET)
        if (expectedTurn != state.turnNumber) return EngineResult.Rejected(RejectReason.STALE_TURN)
        if (targetPlayer == pending.shooter) return EngineResult.Rejected(RejectReason.SELF_TARGET)
        if (targetIndex !in 0 until CHARACTERS_PER_PLAYER) return EngineResult.Rejected(RejectReason.INVALID_TARGET_INDEX)

        val shooter = pending.shooter
        val target = state.character(targetPlayer, targetIndex)
        if (!target.alive) return EngineResult.Rejected(RejectReason.DEAD_TARGET)

        val events = mutableListOf<GameEvent>(
            GameEvent.ShotFired(shooter, pending.shooterIndex, targetPlayer, targetIndex),
        )

        // 1) the shooter's bullet is consumed exactly once; the gun stays
        var next = state.updateCharacter(shooter, pending.shooterIndex) { it.consumeBullet() }

        // 2) defence rules
        if (target.hasGun) {
            events += GameEvent.GunDestroyed(targetPlayer, targetIndex, hadBullet = target.bulletLoaded)
            next = next.updateCharacter(targetPlayer, targetIndex) { it.destroyGun() }
        } else {
            events += GameEvent.CharacterKilled(targetPlayer, targetIndex)
            next = next.updateCharacter(targetPlayer, targetIndex) { it.kill() }
        }

        // 3) elimination / winner
        if (next.player(targetPlayer).isEliminated) {
            events += GameEvent.PlayerEliminated(targetPlayer)
            events += GameEvent.GameWon(shooter)
            return EngineResult.Applied(next.copy(pendingShot = null, winner = shooter), events)
        }
        return EngineResult.Applied(advanceTurn(next), events)
    }

    /** Next player in rotation, skipping eliminated players. Falls back to the current player. */
    fun nextPlayer(state: GameState): PlayerId {
        val candidate = state.currentPlayer.opponent
        return if (state.player(candidate).isEliminated) state.currentPlayer else candidate
    }

    private fun advanceTurn(state: GameState): GameState =
        state.copy(
            currentPlayer = nextPlayer(state),
            turnNumber = state.turnNumber + 1,
            pendingShot = null,
        )

    // ---- temporary expressions (cosmetic, never change stage/alive) ----

    fun setExpression(state: GameState, player: PlayerId, index: Int, expression: Expression): GameState =
        state.updateCharacter(player, index) { it.withExpression(expression) }

    fun settleExpressions(state: GameState): GameState {
        fun settle(p: com.kappusuppu.domain.model.PlayerState) =
            p.copy(characters = p.characters.map { it.withExpression(Expression.NORMAL) })
        return state.copy(kappu = settle(state.kappu), suppu = settle(state.suppu))
    }
}
