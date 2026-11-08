package com.kappusuppu.domain.actions

import com.kappusuppu.domain.model.GameCharacter
import com.kappusuppu.domain.model.PlayerId
import com.kappusuppu.domain.model.Stage

/** What happens to a character when its digit appears on its owner's turn. */
sealed interface TurnAction {
    data class Progress(val from: Stage, val to: Stage) : TurnAction
    data object LoadBullet : TurnAction
    data object OfferShot : TurnAction
    data object DeadCharacter : TurnAction
}

object TurnRules {
    fun actionFor(character: GameCharacter): TurnAction = when {
        !character.alive -> TurnAction.DeadCharacter
        character.stage == Stage.LOADED -> TurnAction.OfferShot
        character.stage == Stage.GUN -> TurnAction.LoadBullet
        else -> TurnAction.Progress(character.stage, checkNotNull(character.stage.next()))
    }
}

sealed interface GameEvent {
    data class Progressed(val player: PlayerId, val index: Int, val to: Stage) : GameEvent
    data class BulletLoaded(val player: PlayerId, val index: Int) : GameEvent
    data class DeadCharacterSkipped(val player: PlayerId, val index: Int) : GameEvent
    data class ShootingOpportunity(val player: PlayerId, val index: Int) : GameEvent
    data class ShotFired(
        val shooter: PlayerId,
        val shooterIndex: Int,
        val target: PlayerId,
        val targetIndex: Int,
    ) : GameEvent

    data class CharacterKilled(val player: PlayerId, val index: Int) : GameEvent
    data class GunDestroyed(val player: PlayerId, val index: Int, val hadBullet: Boolean) : GameEvent
    data class PlayerEliminated(val player: PlayerId) : GameEvent
    data class GameWon(val winner: PlayerId) : GameEvent
}
