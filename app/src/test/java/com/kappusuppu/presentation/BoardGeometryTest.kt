package com.kappusuppu.presentation

import com.kappusuppu.domain.model.PlayerId
import com.kappusuppu.drawing.Pt
import com.kappusuppu.drawing.StrokeFactory
import com.kappusuppu.presentation.game.BoardLayout
import com.kappusuppu.presentation.game.ShotPoints
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private val K = PlayerId.KAPPU
private val S = PlayerId.SUPPU
private val layout = BoardLayout(width = 400f, height = 656f, headerHeight = 56f, padH = 4f, padV = 6f)

class BoardGeometryTest {
    @Test fun sixRowsFiveCharacterRowsPlusHeaderTileTheBoard() {
        assertEquals(120f, layout.rowHeight, 1e-3f)
        assertEquals(layout.height, layout.headerHeight + layout.rowHeight * 5, 1e-3f)
        assertEquals(200f, layout.columnWidth, 1e-3f)
        assertEquals(0f, layout.cellLeft(K), 0f)
        assertEquals(200f, layout.cellLeft(S), 0f)
    }

    @Test fun everyCharacterStaysInsideItsOwnCell() {
        for (player in PlayerId.entries) for (i in 0..4) {
            for (corner in listOf(Pt(0f, 0f), Pt(1f, 0f), Pt(0f, 1f), Pt(1f, 1f))) {
                val p = layout.point(player, i, corner)
                assertTrue(p.x >= layout.cellLeft(player) && p.x <= layout.cellLeft(player) + layout.columnWidth)
                assertTrue(p.y >= layout.cellTop(i) && p.y <= layout.cellTop(i) + layout.rowHeight)
            }
        }
    }

    @Test fun kappuIsOnTheLeftSuppuOnTheRight() {
        assertTrue(layout.point(K, 2, ShotPoints.CHEST).x < layout.width / 2)
        assertTrue(layout.point(S, 2, ShotPoints.CHEST).x > layout.width / 2)
    }

    @Test fun kappusGunIsOnTheRightAndSuppusGunOnTheLeft() {
        val head = Pt(StrokeFactory.HEAD_X, StrokeFactory.HEAD_Y)
        assertTrue(layout.point(K, 0, ShotPoints.MUZZLE).x > layout.point(K, 0, head).x)
        assertTrue(layout.point(S, 0, ShotPoints.MUZZLE).x < layout.point(S, 0, head).x)
    }

    @Test fun bulletsTravelTowardsTheOpponent() {
        // Kappu (left) shooting Suppu (right): the bullet moves right
        val kFrom = layout.point(K, 1, ShotPoints.MUZZLE)
        val kTo = layout.point(S, 3, ShotPoints.CHEST)
        assertTrue(kTo.x > kFrom.x)
        // Suppu (right) shooting Kappu (left): the bullet moves left
        val sFrom = layout.point(S, 1, ShotPoints.MUZZLE)
        val sTo = layout.point(K, 3, ShotPoints.GUN_HIT)
        assertTrue(sTo.x < sFrom.x)
    }

    @Test fun mirroringIsExactlyHorizontal() {
        val a = layout.point(K, 2, Pt(0.3f, 0.7f))
        val b = layout.point(S, 2, Pt(0.7f, 0.7f))
        assertEquals(a.y, b.y, 1e-3f)
        // Suppu's drawing is flipped: unit x 0.7 lands where Kappu's unit x 0.3 lands inside its own column
        assertEquals(a.x, b.x - layout.columnWidth, 1e-3f)
    }
}
