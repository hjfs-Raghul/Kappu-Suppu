package com.kappusuppu.drawing

import com.kappusuppu.applied
import com.kappusuppu.domain.engine.GameEngine
import com.kappusuppu.domain.model.Expression
import com.kappusuppu.domain.model.Phase
import com.kappusuppu.domain.model.PlayerId
import com.kappusuppu.domain.model.Stage
import com.kappusuppu.domain.actions.GameEvent
import com.kappusuppu.with
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

private fun InkBoard.groups(key: CharKey): Set<StrokeGroup> = of(key).map { it.group }.toSet()

class InkBoardTest {
    private val key = CharKey(PlayerId.KAPPU, 0)

    @Test fun stageOneIsOnlyAnEmptyCircleWithNoFaceOrBody() {
        val board = InkBoard.newGame()
        assertEquals(10, board.strokes.size)
        board.strokes.forEach { (_, list) ->
            assertEquals(1, list.size)
            assertEquals(StrokeGroup.HEAD, list.single().group)
        }
    }

    @Test fun drawingIsCumulativeAndExistingStrokesAreNeverRegenerated() {
        var board = InkBoard.newGame().completeAll()
        val circle = board.of(key)
        board = board.apply(GameEvent.Progressed(PlayerId.KAPPU, 0, Stage.FACE))
        assertEquals(circle, board.of(key).take(1).map { it }) // same circle, untouched
        assertEquals(setOf(StrokeGroup.HEAD, StrokeGroup.FACE), board.groups(key))
        board = board.completeAll()
        val withFace = board.of(key)
        board = board.apply(GameEvent.Progressed(PlayerId.KAPPU, 0, Stage.BODY))
        assertEquals(withFace, board.of(key).take(withFace.size))
        assertEquals(setOf(StrokeGroup.HEAD, StrokeGroup.FACE, StrokeGroup.BODY), board.groups(key))
        board = board.completeAll()
        val withBody = board.of(key)
        board = board.apply(GameEvent.Progressed(PlayerId.KAPPU, 0, Stage.GUN))
        assertEquals(withBody, board.of(key).take(withBody.size))
        assertTrue(board.groups(key).contains(StrokeGroup.GUN))
        board = board.completeAll()
        val withGun = board.of(key)
        board = board.apply(GameEvent.BulletLoaded(PlayerId.KAPPU, 0))
        assertEquals(withGun, board.of(key).take(withGun.size))
    }

    @Test fun onlyNewlyAddedStrokesAnimate() {
        var board = InkBoard.newGame().completeAll()
        assertFalse(board.isAnimating)
        board = board.apply(GameEvent.Progressed(PlayerId.KAPPU, 0, Stage.FACE))
        val drawing = board.of(key).filter { it.phase == StrokePhase.DRAWING }
        assertEquals(3, drawing.size) // two eyes + mouth
        assertTrue(drawing.all { it.group == StrokeGroup.FACE })
        assertTrue(board.of(key).filter { it.group == StrokeGroup.HEAD }.all { it.phase == StrokePhase.DRAWN })
        // other characters do not animate
        assertTrue(board.of(CharKey(PlayerId.SUPPU, 3)).all { it.phase == StrokePhase.DRAWN })
    }

    @Test fun strokeIdsAreUnique() {
        var board = InkBoard.newGame()
        board = board.apply(GameEvent.Progressed(PlayerId.KAPPU, 0, Stage.FACE)).apply(GameEvent.Progressed(PlayerId.KAPPU, 1, Stage.FACE))
        val ids = board.strokes.values.flatten().map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test fun onlyTheBulletIsRedEverythingElseIsBlackGraphite() {
        var board = InkBoard.newGame()
        listOf(Stage.FACE, Stage.BODY, Stage.GUN).forEach { board = board.apply(GameEvent.Progressed(PlayerId.KAPPU, 0, it)) }
        board = board.apply(GameEvent.BulletLoaded(PlayerId.KAPPU, 0))
        board = board.apply(GameEvent.CharacterKilled(PlayerId.SUPPU, 0))
        for (s in board.strokes.values.flatten()) {
            if (s.group == StrokeGroup.BULLET) assertEquals(InkColor.RED, s.color) else assertEquals(InkColor.GRAPHITE, s.color)
        }
        assertTrue(board.of(key).any { it.color == InkColor.RED })
    }

    @Test fun gunDestructionRemovesOnlyGunAndBulletAndKeepsFaceAndBody() {
        var board = InkBoard.newGame()
        listOf(Stage.FACE, Stage.BODY, Stage.GUN).forEach { board = board.apply(GameEvent.Progressed(PlayerId.KAPPU, 0, it)) }
        board = board.apply(GameEvent.BulletLoaded(PlayerId.KAPPU, 0)).completeAll()
        board = board.apply(GameEvent.GunDestroyed(PlayerId.KAPPU, 0, hadBullet = true))
        val erasing = board.of(key).filter { it.phase == StrokePhase.ERASING }
        assertTrue(erasing.isNotEmpty())
        assertTrue(erasing.all { it.group == StrokeGroup.GUN || it.group == StrokeGroup.BULLET })
        board = board.removeErased(key, erasing.map { it.id }.toSet())
        assertEquals(setOf(StrokeGroup.HEAD, StrokeGroup.FACE, StrokeGroup.BODY), board.groups(key))
    }

    @Test fun shootingErasesOnlyTheBulletAndKeepsTheGun() {
        var board = InkBoard.newGame()
        listOf(Stage.FACE, Stage.BODY, Stage.GUN).forEach { board = board.apply(GameEvent.Progressed(PlayerId.KAPPU, 0, it)) }
        board = board.apply(GameEvent.BulletLoaded(PlayerId.KAPPU, 0)).completeAll()
        board = board.apply(GameEvent.ShotFired(PlayerId.KAPPU, 0, PlayerId.SUPPU, 0))
        val erasing = board.of(key).filter { it.phase == StrokePhase.ERASING }
        assertTrue(erasing.all { it.group == StrokeGroup.BULLET })
        board = board.removeErased(key, erasing.map { it.id }.toSet())
        assertTrue(board.groups(key).contains(StrokeGroup.GUN))
        assertFalse(board.groups(key).contains(StrokeGroup.BULLET))
    }

    @Test fun rebuiltGunIsDrawnFreshAfterDestruction() {
        var board = InkBoard.newGame()
        listOf(Stage.FACE, Stage.BODY, Stage.GUN).forEach { board = board.apply(GameEvent.Progressed(PlayerId.KAPPU, 0, it)) }
        val firstGun = board.of(key).first { it.group == StrokeGroup.GUN }
        board = board.completeAll().apply(GameEvent.GunDestroyed(PlayerId.KAPPU, 0, false)).completeAll()
        assertFalse(board.groups(key).contains(StrokeGroup.GUN))
        board = board.apply(GameEvent.Progressed(PlayerId.KAPPU, 0, Stage.GUN))
        val newGun = board.of(key).first { it.group == StrokeGroup.GUN }
        assertNotEquals(firstGun.id, newGun.id)
        assertNotEquals(firstGun.points, newGun.points) // a different pencil stroke
    }

    @Test fun deadCharacterKeepsItsDrawingAndGetsACross() {
        var board = InkBoard.newGame().completeAll()
        board = board.apply(GameEvent.CharacterKilled(PlayerId.KAPPU, 0))
        assertEquals(setOf(StrokeGroup.HEAD, StrokeGroup.CROSS), board.groups(key))
        assertEquals(2, board.of(key).count { it.group == StrokeGroup.CROSS })
    }

    @Test fun expressionOverlaysAreTemporaryAndDoNotTouchStoredStrokes() {
        val board = InkBoard.newGame().apply(GameEvent.Progressed(PlayerId.KAPPU, 0, Stage.FACE)).completeAll()
        val before = board.of(key)
        val cunning = StrokeFactory(1).expressionFace(Expression.CUNNING)
        val shocked = StrokeFactory(2).expressionFace(Expression.SHOCKED)
        assertTrue(cunning.isNotEmpty() && shocked.isNotEmpty())
        assertTrue(StrokeFactory(3).expressionFace(Expression.NORMAL).isEmpty())
        assertEquals(before, board.of(key))
        assertTrue((cunning + shocked).all { it.color == InkColor.GRAPHITE })
    }

    @Test fun inkStaysInSyncWithEngineStateThroughRandomGames() {
        repeat(60) { seed ->
            val rnd = Random(seed)
            var state = GameEngine.newGame()
            var ink = InkBoard.newGame().completeAll()
            var guard = 0
            while (state.phase != Phase.GAME_OVER && guard++ < 4000) {
                val result = when (state.phase) {
                    Phase.AWAITING_PAGE -> GameEngine.submitPage(state, rnd.nextInt(1, 101) * 2, state.turnNumber).applied()
                    else -> {
                        val t = GameEngine.validTargets(state).random(rnd)
                        GameEngine.fireShot(state, state.pendingShot!!.shooter.opponent, t, state.turnNumber).applied()
                    }
                }
                state = result.state
                ink = ink.apply(result.events).completeAll()
                for (p in PlayerId.entries) for (c in state.player(p).characters) {
                    val g = ink.groups(CharKey(p, c.index))
                    assertTrue(StrokeGroup.HEAD in g)
                    assertEquals("face $seed", c.stage.number >= 2 || (!c.alive && StrokeGroup.FACE in g), StrokeGroup.FACE in g)
                    if (c.alive) {
                        assertFalse(StrokeGroup.CROSS in g)
                        assertEquals(c.stage.number >= 3, StrokeGroup.BODY in g)
                        assertEquals(c.stage.hasGun, StrokeGroup.GUN in g)
                        assertEquals(c.stage.bulletLoaded, StrokeGroup.BULLET in g)
                    } else {
                        assertTrue(StrokeGroup.CROSS in g)
                        assertFalse(StrokeGroup.GUN in g || StrokeGroup.BULLET in g)
                    }
                }
            }
        }
    }
}

class WobbleTest {
    @Test fun sameSeedGivesSameStrokeDifferentSeedGivesDifferent() {
        val a = StrokeFactory(5).head().single().points
        assertEquals(a, StrokeFactory(5).head().single().points)
        assertNotEquals(a, StrokeFactory(6).head().single().points)
    }

    @Test fun circleIsNotAPerfectPolishedCircle() {
        val pts = StrokeFactory(11).head().single().points
        val radii = pts.map { kotlin.math.hypot(it.x - StrokeFactory.HEAD_X, it.y - StrokeFactory.HEAD_Y) }
        assertTrue(radii.max() - radii.min() > 0.004f)
        assertTrue(pts.first() != pts.last()) // pencil circles do not close perfectly
    }

    @Test fun allStrokesStayInsideTheCharacterBox() {
        val f = StrokeFactory(21)
        val all = f.head() + f.face() + f.body() + f.gun() + f.bullet() + f.cross() +
            Expression.entries.flatMap { f.expressionFace(it) }
        all.flatMap { it.points }.forEach {
            assertTrue("x=${it.x}", it.x in -0.02f..1.02f)
            assertTrue("y=${it.y}", it.y in -0.02f..1.02f)
        }
    }

    @Test fun partialStrokeRespectsFraction() {
        val pts = listOf(Pt(0f, 0f), Pt(1f, 0f), Pt(1f, 1f))
        assertEquals(1, InkMath.partial(pts, 0f).size)
        assertEquals(pts, InkMath.partial(pts, 1f))
        val half = InkMath.partial(pts, 0.5f)
        assertEquals(Pt(1f, 0f), half.last())
        assertEquals(1f, InkMath.length(half), 1e-5f)
        val quarter = InkMath.partial(pts, 0.25f)
        assertEquals(Pt(0.5f, 0f), quarter.last())
    }

    @Test fun gunSitsBesideTheBodyAndBulletInsideTheGunBarrel() {
        val f = StrokeFactory(3)
        val gun = f.gun().single().points
        val bullet = f.bullet().flatMap { it.points }
        val gx = gun.map { it.x }
        val gy = gun.map { it.y }
        assertTrue(gx.min() > StrokeFactory.HEAD_X + 0.1f) // beside, not on the body
        assertTrue(bullet.all { it.x in (gx.min() - 0.02f)..(gx.max() + 0.02f) && it.y in (gy.min() - 0.02f)..(gy.max() + 0.02f) })
    }
}
