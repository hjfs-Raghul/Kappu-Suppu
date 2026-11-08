package com.kappusuppu.drawing

import com.kappusuppu.domain.actions.GameEvent
import com.kappusuppu.domain.model.CHARACTERS_PER_PLAYER
import com.kappusuppu.domain.model.PlayerId
import com.kappusuppu.domain.model.Stage

data class CharKey(val player: PlayerId, val index: Int)

enum class StrokePhase {
    /** Waiting for / currently being drawn by the pencil animation. */
    DRAWING,

    /** Fully drawn, rendered statically. Never animates again. */
    DRAWN,

    /** Being rubbed out (gun destroyed / bullet spent). Removed when the animation ends. */
    ERASING,
}

data class InkStroke(
    val id: Long,
    val group: StrokeGroup,
    val color: InkColor,
    val widthFactor: Float,
    val points: List<Pt>,
    val phase: StrokePhase = StrokePhase.DRAWING,
)

/**
 * Persistent stroke data for all ten characters. It is only ever *added to* (new strokes) or has
 * strokes marked/removed (gun & bullet). Existing strokes are never regenerated, so a character
 * evolves on top of its earlier drawing and only newly added strokes animate.
 */
data class InkBoard(
    val strokes: Map<CharKey, List<InkStroke>>,
    val nextId: Long = 1L,
) {
    val isAnimating: Boolean
        get() = strokes.values.any { list -> list.any { it.phase != StrokePhase.DRAWN } }

    /** True while at least one stroke is waiting to be / being drawn by the pencil (not erasing). */
    val isDrawing: Boolean
        get() = strokes.values.any { list -> list.any { it.phase == StrokePhase.DRAWING } }

    fun of(key: CharKey): List<InkStroke> = strokes[key].orEmpty()

    private fun add(key: CharKey, specs: List<StrokeSpec>): InkBoard {
        var id = nextId
        val added = specs.map { s ->
            InkStroke(id++, s.group, s.color, s.widthFactor, s.points, StrokePhase.DRAWING)
        }
        return copy(strokes = strokes + (key to (of(key) + added)), nextId = id)
    }

    private fun factory(key: CharKey): StrokeFactory =
        StrokeFactory(nextId * 7919L + key.player.ordinal * 104729L + key.index * 1299721L)

    private fun eraseGroups(key: CharKey, vararg groups: StrokeGroup): InkBoard {
        val updated = of(key).map { if (it.group in groups && it.phase != StrokePhase.ERASING) it.copy(phase = StrokePhase.ERASING) else it }
        return copy(strokes = strokes + (key to updated))
    }

    /** Translates engine events into ink changes. */
    fun apply(events: List<GameEvent>): InkBoard = events.fold(this) { board, e -> board.apply(e) }

    fun apply(event: GameEvent): InkBoard = when (event) {
        is GameEvent.Progressed -> {
            val key = CharKey(event.player, event.index)
            when (event.to) {
                Stage.CIRCLE -> add(key, factory(key).head())
                Stage.FACE -> add(key, factory(key).face())
                Stage.BODY -> add(key, factory(key).body())
                Stage.GUN -> add(key, factory(key).gun())
                else -> this
            }
        }

        is GameEvent.BulletLoaded -> {
            val key = CharKey(event.player, event.index)
            add(key, factory(key).bullet())
        }

        is GameEvent.ShotFired -> eraseGroups(CharKey(event.shooter, event.shooterIndex), StrokeGroup.BULLET)

        is GameEvent.GunDestroyed -> eraseGroups(CharKey(event.player, event.index), StrokeGroup.GUN, StrokeGroup.BULLET)

        is GameEvent.CharacterKilled -> {
            val key = CharKey(event.player, event.index)
            val f = factory(key)
            add(key, f.cross() + f.splash())
        }

        is GameEvent.DeadCharacterSkipped,
        is GameEvent.ShootingOpportunity,
        is GameEvent.PlayerEliminated,
        is GameEvent.GameWon,
        -> this
    }

    fun markDrawn(key: CharKey, id: Long): InkBoard =
        copy(strokes = strokes + (key to of(key).map { if (it.id == id && it.phase == StrokePhase.DRAWING) it.copy(phase = StrokePhase.DRAWN) else it }))

    fun removeErased(key: CharKey, ids: Set<Long>): InkBoard =
        copy(strokes = strokes + (key to of(key).filterNot { it.id in ids && it.phase == StrokePhase.ERASING }))

    /** Finishes every pending animation instantly (used when the UI is skipped and in tests). */
    fun completeAll(): InkBoard = copy(
        strokes = strokes.mapValues { (_, list) ->
            list.filter { it.phase != StrokePhase.ERASING }.map { it.copy(phase = StrokePhase.DRAWN) }
        },
    )

    companion object {
        /** A new game: every cell is empty. The circle appears when the first matching digit comes up. */
        fun newGame(): InkBoard = InkBoard(emptyMap())
    }
}
