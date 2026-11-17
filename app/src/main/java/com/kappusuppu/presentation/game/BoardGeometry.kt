package com.kappusuppu.presentation.game

import com.kappusuppu.domain.model.CHARACTERS_PER_PLAYER
import com.kappusuppu.domain.model.PlayerId
import com.kappusuppu.drawing.Pt
import com.kappusuppu.drawing.StrokeFactory
import kotlin.math.min

/**
 * Where things are on the 6 x 2 board, in pixels. Pure arithmetic (no Compose types) so it can be
 * unit-tested and shared between the table drawing and the shot overlay.
 *
 * Kappu owns the left column and faces right; Suppu owns the right column and faces left. A
 * character is drawn in a square of side [side] centred in its cell; for Suppu the drawing is
 * mirrored horizontally, so his gun is on the left, pointing at Kappu.
 */
data class BoardLayout(
    val width: Float,
    val height: Float,
    val headerHeight: Float,
    val padH: Float,
    val padV: Float,
) {
    val columnWidth: Float get() = width / 2f
    val rowHeight: Float get() = (height - headerHeight) / CHARACTERS_PER_PLAYER

    val side: Float get() = min(columnWidth - 2f * padH, rowHeight - 2f * padV)

    fun cellLeft(player: PlayerId): Float = if (player == PlayerId.KAPPU) 0f else columnWidth
    fun cellTop(index: Int): Float = headerHeight + rowHeight * index

    /** Maps a point of the 0..1 character box to board pixels (mirrored for Suppu). */
    fun point(player: PlayerId, index: Int, unit: Pt): Pt {
        val ux = if (player == PlayerId.SUPPU) 1f - unit.x else unit.x
        val left = cellLeft(player) + padH + (columnWidth - 2f * padH - side) / 2f
        val top = cellTop(index) + padV + (rowHeight - 2f * padV - side) / 2f
        return Pt(left + ux * side, top + unit.y * side)
    }
}

/** Key points of a character drawing, in its 0..1 box (before mirroring). */
object ShotPoints {
    /** End of the gun barrel, where the bullet leaves. */
    val MUZZLE = Pt(0.92f, 0.535f)

    /** Middle of the gun body: where a bullet hits an armed target. */
    val GUN_HIT = Pt(0.75f, 0.535f)

    /** Chest of the stick body: where a bullet hits an unarmed target. */
    val CHEST = Pt(StrokeFactory.HEAD_X, 0.56f)
}
