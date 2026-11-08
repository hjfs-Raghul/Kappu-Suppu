package com.kappusuppu.presentation

import com.kappusuppu.domain.engine.BookSimulator
import com.kappusuppu.domain.model.Expression
import com.kappusuppu.domain.model.PlayerAssignment
import com.kappusuppu.domain.model.PlayerId
import com.kappusuppu.domain.model.Stage
import com.kappusuppu.domain.rules.PageError
import com.kappusuppu.drawing.CharKey
import com.kappusuppu.drawing.InkBoard
import com.kappusuppu.drawing.StaticInk
import com.kappusuppu.drawing.StrokeGroup
import com.kappusuppu.drawing.StrokePhase
import com.kappusuppu.killAll
import com.kappusuppu.presentation.game.BookMode
import com.kappusuppu.presentation.game.GameSession
import com.kappusuppu.presentation.game.SessionEffect
import com.kappusuppu.presentation.game.SessionState
import com.kappusuppu.presentation.game.ShotStage
import com.kappusuppu.presentation.game.TurnStep
import com.kappusuppu.progress
import com.kappusuppu.with
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

private val K = PlayerId.KAPPU
private val S = PlayerId.SUPPU

private fun physical() = GameSession().newGame(PlayerAssignment(K), BookMode.PHYSICAL)

class TurnFlowTest {
    private val g = GameSession()

    @Test fun onlyTheCurrentPlayersBookIsEnabled() {
        val s = physical()
        assertTrue(s.canOpenBook(K))
        assertFalse(s.canOpenBook(S))
    }

    @Test fun theOppositionCannotPlayAPlayersTurn() {
        val s = physical()
        assertEquals(s, g.submitPhysicalText(s, S, "10").state)
        val sim = GameSession(BookSimulator(Random(1))).newGame(PlayerAssignment(K), BookMode.SIMULATOR)
        val t = GameSession(BookSimulator(Random(1))).openBook(sim, S)
        assertEquals(sim, t.state)
        assertTrue(t.effects.isEmpty())
    }

    @Test fun invalidInputsSetErrorsAndChangeNothingElse() {
        val s = physical()
        assertEquals(PageError.ODD, g.submitPhysicalText(s, K, "7").state.pageError)
        assertEquals(PageError.NOT_POSITIVE, g.submitPhysicalText(s, K, "0").state.pageError)
        assertEquals(PageError.NOT_POSITIVE, g.submitPhysicalText(s, K, "-6").state.pageError)
        assertEquals(PageError.NOT_A_NUMBER, g.submitPhysicalText(s, K, "abc").state.pageError)
        assertEquals(PageError.EMPTY, g.submitPhysicalText(s, K, "").state.pageError)
        val bad = g.submitPhysicalText(s, K, "abc").state
        assertEquals(s.game, bad.game)
        assertEquals(TurnStep.AwaitingPage, bad.step)
        assertNull(g.clearPageError(bad).pageError)
    }

    @Test fun aPageIsAppliedAutomaticallyWithNoConfirmationStep() {
        val t = g.submitPhysicalText(physical(), K, "126") // digit 6 -> the 4th character
        val s = t.state
        assertEquals(Stage.CIRCLE, s.game.character(K, 3).stage)
        assertEquals(TurnStep.Resolving, s.step)
        assertEquals(K, s.actor)
        assertTrue(s.lastMessage.contains("digit 6") && s.lastMessage.contains("circle"))
        // a single circle is queued for the pencil animation, in that cell only
        val drawing = s.ink.of(CharKey(K, 3)).filter { it.phase == StrokePhase.DRAWING }
        assertEquals(1, drawing.size)
        assertEquals(StrokeGroup.HEAD, drawing.single().group)
        assertTrue(s.ink.of(CharKey(K, 0)).isEmpty())
    }

    @Test fun nobodyCanOpenABookWhileTheTurnIsResolving() {
        val s = g.submitPhysicalText(physical(), K, "10").state
        assertFalse(s.canOpenBook(K))
        assertFalse(s.canOpenBook(S))
        assertEquals(s, g.submitPhysicalText(s, S, "12").state)
    }

    @Test fun afterResolvingTheNextPlayersBookBecomesAvailable() {
        val s = g.finishResolving(g.submitPhysicalText(physical(), K, "10").state)
        assertEquals(TurnStep.AwaitingPage, s.step)
        assertEquals(S, s.actor)
        assertTrue(s.canOpenBook(S))
        assertFalse(s.canOpenBook(K))
        assertFalse(s.ink.isAnimating)
    }

    @Test fun duplicateSubmitAppliesOnlyOnce() {
        val first = g.submitPhysicalText(physical(), K, "10").state
        val second = g.submitPhysicalText(first, K, "10").state
        assertEquals(first, second)
        assertEquals(Stage.CIRCLE, second.game.character(K, 0).stage)
        assertEquals(Stage.EMPTY, second.game.character(S, 0).stage)
    }

    @Test fun simulatorRevealsOnceAppliesAutomaticallyAndCannotBeRerolled() {
        val sim = GameSession(BookSimulator(Random(5)))
        val s = sim.newGame(PlayerAssignment(S), BookMode.SIMULATOR)
        val opened = sim.openBook(s, K)
        assertEquals(listOf<SessionEffect>(SessionEffect.PageFlip), opened.effects)
        val spread = (opened.state.step as TurnStep.SpreadRevealed).spread
        val again = sim.openBook(opened.state, K)
        assertEquals(opened.state, again.state)
        assertTrue(again.effects.isEmpty())
        // typing is not accepted in simulator mode, and a spread cannot be skipped by typing
        assertEquals(opened.state, sim.submitPhysicalText(opened.state, K, "10").state)
        val done = sim.confirmSpread(opened.state)
        assertEquals(TurnStep.Resolving, done.state.step)
        assertEquals(Stage.CIRCLE, done.state.game.character(K, spread.left % 10 / 2).stage)
        assertEquals(done.state, sim.confirmSpread(done.state).state)
    }

    @Test fun simulatorOnlyUsesTheEvenPage() {
        val sim = GameSession(BookSimulator(Random(11)))
        repeat(100) {
            val opened = sim.openBook(sim.newGame(PlayerAssignment(K), BookMode.SIMULATOR), K)
            val spread = (opened.state.step as TurnStep.SpreadRevealed).spread
            val resolved = sim.confirmSpread(opened.state)
            assertEquals(Stage.CIRCLE, resolved.state.game.character(K, spread.left % 10 / 2).stage)
        }
    }

    @Test fun bulletLoadingRequestsTheTensionCueOnly() {
        val s = physical().let { it.copy(game = it.game.with(K, 1, Stage.GUN), ink = it.ink.progress(K, 1, Stage.GUN)) }
        val t = g.submitPhysicalText(s, K, "12")
        assertEquals(listOf<SessionEffect>(SessionEffect.TensionCue), t.effects)
        assertEquals(Stage.LOADED, t.state.game.character(K, 1).stage)
        assertTrue(t.state.ink.of(CharKey(K, 1)).any { it.group == StrokeGroup.BULLET })
        assertTrue(g.submitPhysicalText(physical(), K, "10").effects.isEmpty())
    }
}

class ShootingFlowTest {
    private val g = GameSession()

    /** Kappu's first character is loaded and its digit comes up; returns the state awaiting a target. */
    private fun awaitingTarget(targetStage: Stage = Stage.GUN, tweak: (SessionState) -> SessionState = { it }): SessionState {
        val base = physical().let {
            it.copy(
                game = it.game.with(K, 0, Stage.LOADED).with(S, 2, targetStage),
                ink = it.ink.progress(K, 0, Stage.LOADED).progress(S, 2, targetStage),
            )
        }
        val t = g.submitPhysicalText(tweak(base), K, "10")
        assertEquals(TurnStep.Resolving, t.state.step)
        return g.finishResolving(t.state)
    }

    @Test fun shootingOpportunityOpensTargetSelectionWithoutPassingTheTurn() {
        val s = awaitingTarget()
        assertEquals(TurnStep.AwaitingTarget, s.step)
        assertEquals(K, s.currentPlayer)
        assertEquals(K, s.actor)
        assertTrue(s.game.character(K, 0).bulletLoaded)
        assertFalse(s.canOpenBook(K) || s.canOpenBook(S))
    }

    @Test fun allLivingEnemiesAreHighlightedAndDeadOnesAreNot() {
        assertEquals(listOf(0, 1, 2, 3, 4), g.targets(awaitingTarget()))
        val s = awaitingTarget { it.copy(game = it.game.killAll(S, except = setOf(0, 2, 4))) }
        assertEquals(listOf(0, 2, 4), g.targets(s))
        assertTrue(g.targets(physical()).isEmpty())
    }

    @Test fun invalidTargetTapsAreIgnored() {
        val s = awaitingTarget { it.copy(game = it.game.killAll(S, except = setOf(0, 2, 4))) }
        assertEquals(s, g.fireAt(s, 1).state)  // dead
        assertEquals(s, g.fireAt(s, 9).state)  // out of range
        assertEquals(s, g.fireAt(s, -1).state)
        assertEquals(physical(), g.fireAt(physical(), 0).state) // nothing to shoot with
    }

    @Test fun tappingATargetFiresAtOnceWithoutAConfirmation() {
        val s = awaitingTarget()
        val t = g.fireAt(s, 2)
        assertEquals(TurnStep.Firing, t.state.step)
        val shot = t.state.shot!!
        assertEquals(ShotStage.AIMING, shot.stage)
        assertEquals(2, shot.targetIndex)
        assertTrue(shot.targetHadGun)
        // the shooter shows the cunning face; nothing is damaged yet and the bullet is not spent yet
        assertEquals(Expression.CUNNING, t.state.game.character(K, 0).expression)
        assertTrue(t.state.game.character(K, 0).bulletLoaded)
        assertEquals(Stage.GUN, t.state.game.character(S, 2).stage)
    }

    @Test fun rapidExtraTapsAreIgnored() {
        val s = awaitingTarget()
        val first = g.fireAt(s, 2).state
        assertEquals(first, g.fireAt(first, 2).state)
        assertEquals(first, g.fireAt(first, 3).state)
        // also ignored in later stages
        val flying = g.launch(first).state
        assertEquals(flying, g.fireAt(flying, 1).state)
    }

    @Test fun theShotRunsInTwoStepsGunFiresThenBulletHits() {
        val s = awaitingTarget(Stage.LOADED)
        val aiming = g.fireAt(s, 2).state

        // step 1: gunshot, the bullet leaves the shooter's gun, the target is still untouched
        val launched = g.launch(aiming)
        assertEquals(listOf<SessionEffect>(SessionEffect.Gunshot), launched.effects)
        assertEquals(ShotStage.FLYING, launched.state.shot!!.stage)
        assertTrue(launched.state.ink.of(CharKey(K, 0)).any { it.group == StrokeGroup.BULLET && it.phase == StrokePhase.ERASING })
        assertEquals(Stage.LOADED, launched.state.game.character(S, 2).stage)
        assertEquals(launched.state, g.launch(launched.state).state) // gunshot only once
        assertEquals(aiming, g.impact(aiming).state)                 // cannot skip the flight

        // step 2: the bullet hits and breaks the gun
        val hit = g.impact(launched.state).state
        assertEquals(TurnStep.Resolving, hit.step)
        assertEquals(ShotStage.IMPACT, hit.shot!!.stage)
        assertEquals(Stage.GUN, hit.game.character(K, 0).stage)          // bullet consumed, gun stays
        assertEquals(Stage.BODY, hit.game.character(S, 2).stage)         // gun destroyed
        assertTrue(hit.game.character(S, 2).alive)
        assertEquals(Expression.SHOCKED, hit.game.character(S, 2).expression)
        assertTrue(hit.ink.of(CharKey(S, 2)).filter { it.group == StrokeGroup.GUN || it.group == StrokeGroup.BULLET }.all { it.phase == StrokePhase.ERASING })
        assertTrue(hit.lastMessage.contains("loses its gun and bullet"))
        assertEquals(hit, g.impact(hit).state)                           // exactly once

        // done: the next player's book is enabled and faces relax
        val next = g.finishResolving(hit)
        assertEquals(TurnStep.AwaitingPage, next.step)
        assertTrue(next.canOpenBook(S))
        assertNull(next.shot)
        assertEquals(Expression.NORMAL, next.game.character(S, 2).expression)
        assertFalse(next.ink.isAnimating)
    }

    @Test fun anUnarmedTargetIsShotDeadWithACrossAndRedSplash() {
        val s = awaitingTarget(Stage.FACE)
        val aiming = g.fireAt(s, 2).state
        assertFalse(aiming.shot!!.targetHadGun)
        val hit = g.impact(g.launch(aiming).state).state
        assertFalse(hit.game.character(S, 2).alive)
        val groups = hit.ink.of(CharKey(S, 2)).map { it.group }.toSet()
        assertTrue(StrokeGroup.CROSS in groups && StrokeGroup.SPLASH in groups)
        assertTrue(hit.lastMessage.contains("shot dead"))
        assertEquals(TurnStep.AwaitingPage, g.finishResolving(hit).step)
    }

    @Test fun killingTheLastCharacterEndsTheGame() {
        val s = awaitingTarget(Stage.BODY) { it.copy(game = it.game.killAll(S, except = setOf(2))) }
        val hit = g.impact(g.launch(g.fireAt(s, 2).state).state).state
        assertEquals(K, hit.winner)
        val over = g.finishResolving(hit)
        assertEquals(TurnStep.GameOver, over.step)
        assertFalse(over.canOpenBook(K) || over.canOpenBook(S))
        assertEquals(over, g.submitPhysicalText(over, K, "10").state)
        assertTrue(g.targets(over).isEmpty())
    }

    @Test fun aFullGameCanBePlayedThroughTheSession() {
        val rnd = Random(3)
        repeat(30) {
            val sim = GameSession(BookSimulator(Random(rnd.nextInt())))
            var s = sim.newGame(PlayerAssignment(K), BookMode.SIMULATOR)
            var guard = 0
            while (s.step != TurnStep.GameOver && guard++ < 5000) {
                s = when (s.step) {
                    TurnStep.AwaitingPage -> sim.confirmSpread(sim.openBook(s, s.currentPlayer).state).state
                    TurnStep.Resolving -> sim.finishResolving(s)
                    TurnStep.AwaitingTarget -> sim.fireAt(s, sim.targets(s).random(rnd)).state
                    TurnStep.Firing -> sim.impact(sim.launch(s).state).state
                    else -> error("unexpected ${s.step}")
                }
            }
            assertEquals(TurnStep.GameOver, s.step)
            assertTrue(s.game.player(s.winner!!.opponent).isEliminated)
        }
    }

    @Test fun staticInkForHowToPlayMatchesStages() {
        val groups = { st: Stage, dead: Boolean -> StaticInk.strokes(st, dead).map { it.group }.toSet() }
        assertTrue(groups(Stage.EMPTY, false).isEmpty())
        assertEquals(setOf(StrokeGroup.HEAD), groups(Stage.CIRCLE, false))
        assertEquals(setOf(StrokeGroup.HEAD, StrokeGroup.FACE), groups(Stage.FACE, false))
        assertTrue(StrokeGroup.GUN in groups(Stage.GUN, false) && StrokeGroup.BULLET !in groups(Stage.GUN, false))
        assertTrue(StrokeGroup.BULLET in groups(Stage.LOADED, false))
        assertTrue(StrokeGroup.CROSS in groups(Stage.BODY, true))
        assertTrue(StaticInk.strokes(Stage.LOADED).all { it.phase == StrokePhase.DRAWN })
        assertEquals(InkBoard.newGame().strokes.size, 0)
    }
}
