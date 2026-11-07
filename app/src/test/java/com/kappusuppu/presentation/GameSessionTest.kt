package com.kappusuppu.presentation

import com.kappusuppu.domain.engine.BookSimulator
import com.kappusuppu.domain.model.Expression
import com.kappusuppu.domain.model.Phase
import com.kappusuppu.domain.model.PlayerAssignment
import com.kappusuppu.domain.model.PlayerId
import com.kappusuppu.domain.model.Stage
import com.kappusuppu.domain.rules.PageError
import com.kappusuppu.drawing.CharKey
import com.kappusuppu.drawing.StaticInk
import com.kappusuppu.drawing.StrokeGroup
import com.kappusuppu.drawing.StrokePhase
import com.kappusuppu.kill
import com.kappusuppu.killAll
import com.kappusuppu.presentation.game.BookMode
import com.kappusuppu.presentation.game.GameSession
import com.kappusuppu.presentation.game.NextStep
import com.kappusuppu.presentation.game.SessionEffect
import com.kappusuppu.presentation.game.SessionState
import com.kappusuppu.presentation.game.TurnStep
import com.kappusuppu.with
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

private val K = PlayerId.KAPPU
private val S = PlayerId.SUPPU

class GameSessionTest {
    private fun physical() = GameSession().newGame(PlayerAssignment(K), BookMode.PHYSICAL)
    private fun session(seed: Int = 1) = GameSession(BookSimulator(Random(seed)))

    @Test fun physicalInvalidInputsSetErrorsAndChangeNothingElse() {
        val g = GameSession()
        val s = physical()
        assertEquals(PageError.ODD, g.submitPhysicalText(s, "7").state.pageError)
        assertEquals(PageError.NOT_POSITIVE, g.submitPhysicalText(s, "0").state.pageError)
        assertEquals(PageError.NOT_POSITIVE, g.submitPhysicalText(s, "-6").state.pageError)
        assertEquals(PageError.NOT_A_NUMBER, g.submitPhysicalText(s, "abc").state.pageError)
        assertEquals(s.game, g.submitPhysicalText(s, "abc").state.game)
        assertTrue(g.submitPhysicalText(s, "abc").state.step is TurnStep.AwaitingPage)
    }

    @Test fun physicalPageResolvesTheMatchingCharacter() {
        val g = GameSession()
        val t = g.submitPhysicalText(physical(), "126") // last digit 6 -> Character 4
        assertEquals(Stage.FACE, t.state.game.character(K, 3).stage)
        assertEquals(S, t.state.currentPlayer)
        val step = t.state.step as TurnStep.Feedback
        assertEquals(NextStep.HANDOFF, step.next)
        assertTrue(step.message.contains("Character 4"))
        // eyes and mouth are queued for the pencil animation, only on that character
        val drawing = t.state.ink.of(CharKey(K, 3)).filter { it.phase == StrokePhase.DRAWING && it.group == StrokeGroup.FACE }
        assertEquals(3, drawing.size)
        assertTrue(t.state.ink.of(CharKey(K, 0)).none { it.group == StrokeGroup.FACE })
    }

    @Test fun duplicatePhysicalSubmitAppliesOnlyOnce() {
        val g = GameSession()
        val first = g.submitPhysicalText(physical(), "10")
        val second = g.submitPhysicalText(first.state, "10")
        assertEquals(first.state, second.state)
        assertEquals(Stage.FACE, second.state.game.character(K, 0).stage)
        assertEquals(Stage.CIRCLE, second.state.game.character(S, 0).stage)
    }

    @Test fun simulatorRevealsOnceAndCannotBeRerolled() {
        val g = session(5)
        val s = g.newGame(PlayerAssignment(S), BookMode.SIMULATOR)
        val opened = g.openBook(s)
        assertEquals(listOf<SessionEffect>(SessionEffect.PageFlip), opened.effects)
        val spread = (opened.state.step as TurnStep.SpreadRevealed).spread
        val again = g.openBook(opened.state)
        assertEquals(opened.state, again.state)
        assertTrue(again.effects.isEmpty())
        assertEquals(spread, (again.state.step as TurnStep.SpreadRevealed).spread)
        // physical text input is not accepted in simulator mode
        assertEquals(opened.state, g.submitPhysicalText(opened.state, "10").state)
        val done = g.confirmSpread(opened.state)
        assertTrue(done.state.step is TurnStep.Feedback)
        assertEquals(done.state, g.confirmSpread(done.state).state) // double tap on OK is ignored
    }

    @Test fun simulatorOnlyUsesTheEvenPage() {
        val g = session(11)
        repeat(100) {
            val s = g.newGame(PlayerAssignment(K), BookMode.SIMULATOR)
            val opened = g.openBook(s)
            val spread = (opened.state.step as TurnStep.SpreadRevealed).spread
            val resolved = g.confirmSpread(opened.state)
            val idx = spread.left % 10 / 2
            assertEquals(Stage.FACE, resolved.state.game.character(K, idx).stage)
        }
    }

    @Test fun bulletLoadingRequestsTheTensionCue() {
        val g = GameSession()
        val s = physical().let { it.copy(game = it.game.with(K, 1, Stage.GUN)) }
        val t = g.submitPhysicalText(s, "12")
        assertEquals(listOf<SessionEffect>(SessionEffect.TensionCue), t.effects)
        assertEquals(Stage.LOADED, t.state.game.character(K, 1).stage)
        assertTrue(t.state.ink.of(CharKey(K, 1)).any { it.group == StrokeGroup.BULLET })
        // plain progress has no tension cue
        assertTrue(g.submitPhysicalText(physical(), "10").effects.isEmpty())
    }

    @Test fun continueFromFeedbackHandsOverToNextPlayer() {
        val g = GameSession()
        val t = g.submitPhysicalText(physical(), "10")
        val (next, step) = g.continueFromFeedback(t.state)
        assertEquals(NextStep.HANDOFF, step)
        assertTrue(next.step is TurnStep.AwaitingPage)
        assertFalse(next.ink.isAnimating)
        assertEquals(S, next.currentPlayer)
    }

    // ---- full shooting flow -----------------------------------------------------------------

    private fun loadedAndPending(g: GameSession, targetStage: Stage = Stage.GUN): SessionState {
        val s = physical().let {
            it.copy(game = it.game.with(K, 0, Stage.LOADED).with(S, 2, targetStage), ink = it.ink.completeAll())
        }
        return g.submitPhysicalText(s, "10").state
    }

    @Test fun shootingOpportunityRoutesToTargetSelectionWithoutPassingTheTurn() {
        val g = GameSession()
        val s = loadedAndPending(g)
        assertEquals(NextStep.TARGET, (s.step as TurnStep.Feedback).next)
        assertEquals(Phase.AWAITING_TARGET, s.game.phase)
        assertEquals(K, s.currentPlayer)
        val (_, next) = g.continueFromFeedback(s)
        assertEquals(NextStep.TARGET, next)
    }

    @Test fun selectingOnlyLivingOpponentCharactersWorks() {
        val g = GameSession()
        var s = loadedAndPending(g).let { it.copy(game = it.game.kill(S, 1)) }
        assertNull(g.selectTarget(s, 1).selectedTarget)       // dead
        assertNull(g.selectTarget(s, 9).selectedTarget)       // out of range
        s = g.selectTarget(s, 3)
        assertEquals(3, s.selectedTarget)
    }

    @Test fun cancelKeepsTheBulletAndShotAvailable() {
        val g = GameSession()
        val s = g.selectTarget(loadedAndPending(g), 2)
        val cancelled = g.cancelTarget(s)
        assertNull(cancelled.selectedTarget)
        assertTrue(cancelled.game.character(K, 0).bulletLoaded)
        assertEquals(Phase.AWAITING_TARGET, cancelled.game.phase)
        assertEquals(NextStep.TARGET, (cancelled.step as TurnStep.Feedback).next)
    }

    @Test fun firingNeedsATargetAndRunsExactlyOnceEvenWithRapidTaps() {
        val g = GameSession()
        val pending = loadedAndPending(g)
        // no target chosen -> nothing happens
        assertFalse(g.beginFire(pending).state.firing)

        val aimed = g.selectTarget(pending, 2)
        val begun = g.beginFire(aimed).state
        assertTrue(begun.firing)
        assertEquals(Expression.CUNNING, begun.game.character(K, 0).expression)
        assertTrue(begun.game.character(K, 0).bulletLoaded) // bullet not consumed by the cunning face
        // rapid second tap while firing: ignored
        assertEquals(begun, g.beginFire(begun).state)

        val fired = g.completeFire(begun).state
        assertEquals(Stage.GUN, fired.game.character(K, 0).stage)       // bullet consumed
        assertEquals(Stage.BODY, fired.game.character(S, 2).stage)      // gun destroyed
        assertEquals(Expression.SHOCKED, fired.game.character(S, 2).expression)
        assertTrue(fired.fireOutcome!!.contains("loses its gun"))
        // a duplicate completion changes nothing
        assertEquals(fired, g.completeFire(fired).state)
        // targets cannot be re-picked mid-flow
        assertEquals(fired, g.selectTarget(fired, 0))

        val finished = g.finishFire(fired).state
        assertEquals(Expression.NORMAL, finished.game.character(S, 2).expression)
        assertFalse(finished.firing)
        assertEquals(S, finished.currentPlayer)

        val (next, step) = g.continueFromTarget(finished)
        assertEquals(NextStep.HANDOFF, step)
        assertTrue(next.step is TurnStep.AwaitingPage)
        assertNull(next.fireOutcome)
    }

    @Test fun killingAnUnarmedTargetMarksItDeadWithACross() {
        val g = GameSession()
        val s = g.selectTarget(loadedAndPending(g, Stage.FACE), 2)
        val fired = g.completeFire(g.beginFire(s).state).state
        assertFalse(fired.game.character(S, 2).alive)
        assertTrue(fired.fireOutcome!!.contains("dead"))
        assertTrue(fired.ink.of(CharKey(S, 2)).any { it.group == StrokeGroup.CROSS })
    }

    @Test fun lastCharacterKilledGoesToResultScreen() {
        val g = GameSession()
        val base = physical().let {
            it.copy(game = it.game.with(K, 0, Stage.LOADED).killAll(S, except = setOf(4)), ink = it.ink.completeAll())
        }
        val pending = g.submitPhysicalText(base, "10").state
        val fired = g.completeFire(g.beginFire(g.selectTarget(pending, 4)).state).state
        assertEquals(K, fired.winner)
        assertTrue(fired.fireOutcome!!.contains("wins"))
        val (end, step) = g.continueFromTarget(g.finishFire(fired).state)
        assertEquals(NextStep.RESULT, step)
        assertEquals(K, end.winner)
        // no further input is accepted
        assertEquals(end, g.submitPhysicalText(end, "10").state)
    }

    @Test fun staticInkForHowToPlayMatchesStages() {
        val groups = { st: Stage, dead: Boolean -> StaticInk.strokes(st, dead).map { it.group }.toSet() }
        assertEquals(setOf(StrokeGroup.HEAD), groups(Stage.CIRCLE, false))
        assertEquals(setOf(StrokeGroup.HEAD, StrokeGroup.FACE), groups(Stage.FACE, false))
        assertTrue(StrokeGroup.GUN in groups(Stage.GUN, false) && StrokeGroup.BULLET !in groups(Stage.GUN, false))
        assertTrue(StrokeGroup.BULLET in groups(Stage.LOADED, false))
        assertTrue(StrokeGroup.CROSS in groups(Stage.BODY, true))
        assertTrue(StaticInk.strokes(Stage.LOADED).all { it.phase == StrokePhase.DRAWN })
    }
}
