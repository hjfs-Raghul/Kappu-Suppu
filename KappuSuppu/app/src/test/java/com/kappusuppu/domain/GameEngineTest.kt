package com.kappusuppu.domain

import com.kappusuppu.applied
import com.kappusuppu.domain.actions.GameEvent
import com.kappusuppu.domain.actions.TurnAction
import com.kappusuppu.domain.engine.EngineResult
import com.kappusuppu.domain.engine.GameEngine
import com.kappusuppu.domain.engine.RejectReason
import com.kappusuppu.domain.model.Expression
import com.kappusuppu.domain.model.GameState
import com.kappusuppu.domain.model.Phase
import com.kappusuppu.domain.model.PlayerId
import com.kappusuppu.domain.model.Stage
import com.kappusuppu.kill
import com.kappusuppu.killAll
import com.kappusuppu.pageFor
import com.kappusuppu.with
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

private val K = PlayerId.KAPPU
private val S = PlayerId.SUPPU

/** Plays Kappu's turn with character [index] and returns the new state. */
private fun GameState.play(index: Int): EngineResult.Applied =
    GameEngine.submitPage(this, pageFor(index), turnNumber).applied()

/** State where it's Kappu's turn and Kappu's character 0 is loaded, ready to get a shooting opportunity. */
private fun loadedShooter(): GameState = GameEngine.newGame().with(K, 0, Stage.LOADED)

private fun GameState.shootingOpportunity(index: Int = 0): GameState = play(index).state

private fun GameState.fire(targetIndex: Int, target: PlayerId = S): EngineResult.Applied =
    GameEngine.fireShot(this, target, targetIndex, turnNumber).applied()

class CharacterProgressionTest {
    @Test fun stageOneToTwo() = progress(Stage.CIRCLE, Stage.FACE)
    @Test fun stageTwoToThree() = progress(Stage.FACE, Stage.BODY)
    @Test fun stageThreeToFour() = progress(Stage.BODY, Stage.GUN)

    private fun progress(from: Stage, to: Stage) {
        val s = GameEngine.newGame().with(K, 2, from)
        val r = s.play(2)
        assertEquals(to, r.state.character(K, 2).stage)
        assertEquals(listOf<GameEvent>(GameEvent.Progressed(K, 2, to)), r.events)
        assertTrue(r.state.character(K, 2).alive)
    }

    @Test fun stageFourToFiveLoadsBullet() {
        val r = GameEngine.newGame().with(K, 3, Stage.GUN).play(3)
        assertEquals(Stage.LOADED, r.state.character(K, 3).stage)
        assertTrue(r.state.character(K, 3).bulletLoaded)
        assertEquals(listOf<GameEvent>(GameEvent.BulletLoaded(K, 3)), r.events)
    }

    @Test fun deadCharactersDoNotProgress() {
        val s = GameEngine.newGame().kill(K, 1, Stage.FACE)
        val r = s.play(1)
        assertEquals(s.character(K, 1), r.state.character(K, 1))
        assertEquals(listOf<GameEvent>(GameEvent.DeadCharacterSkipped(K, 1)), r.events)
    }

    @Test fun onlyTheMatchingCharacterChanges() {
        val s = GameEngine.newGame()
        val after = s.play(4).state
        for (i in 0..3) assertEquals(Stage.CIRCLE, after.character(K, i).stage)
        assertEquals(Stage.FACE, after.character(K, 4).stage)
        after.suppu.characters.forEach { assertEquals(Stage.CIRCLE, it.stage) }
    }

    @Test fun fullProgressionOverFiveMatchingTurns() {
        var s = GameEngine.newGame()
        val expected = listOf(Stage.FACE, Stage.BODY, Stage.GUN, Stage.LOADED)
        for (e in expected) {
            s = s.play(0).state                        // Kappu's turn
            assertEquals(e, s.character(K, 0).stage)
            s = s.play(1).state                        // Suppu's turn uses a different character
        }
    }
}

class ShootingTest {
    @Test fun stageFiveGetsShootingOpportunity() {
        val s = loadedShooter()
        assertEquals(TurnAction.OfferShot, GameEngine.previewAction(s, pageFor(0)))
        assertTrue(GameEngine.needsTargetSelection(s, pageFor(0)))
        val r = s.play(0)
        assertEquals(Phase.AWAITING_TARGET, r.state.phase)
        assertEquals(listOf<GameEvent>(GameEvent.ShootingOpportunity(K, 0)), r.events)
        // the turn has not been passed and the bullet is still there
        assertEquals(K, r.state.currentPlayer)
        assertEquals(s.turnNumber, r.state.turnNumber)
        assertTrue(r.state.character(K, 0).bulletLoaded)
    }

    @Test fun nonShootingStagesDoNotNeedTargets() {
        val s = GameEngine.newGame().with(K, 0, Stage.GUN)
        assertFalse(GameEngine.needsTargetSelection(s, pageFor(0)))
        assertEquals(TurnAction.LoadBullet, GameEngine.previewAction(s, pageFor(0)))
    }

    @Test fun manualTargetSelectionOffersOnlyLivingOpponents() {
        var s = loadedShooter().kill(S, 1).kill(S, 3, Stage.BODY)
        s = s.shootingOpportunity()
        assertEquals(listOf(0, 2, 4), GameEngine.validTargets(s))
    }

    @Test fun anyLivingTargetStageCanBeSelected() {
        val s = loadedShooter()
            .with(S, 0, Stage.CIRCLE).with(S, 1, Stage.FACE).with(S, 2, Stage.BODY)
            .with(S, 3, Stage.GUN).with(S, 4, Stage.LOADED)
            .shootingOpportunity()
        assertEquals(listOf(0, 1, 2, 3, 4), GameEngine.validTargets(s))
        for (t in 0..4) assertTrue(GameEngine.fireShot(s, S, t, s.turnNumber) is EngineResult.Applied)
    }

    @Test fun noTargetsWhenNoShotIsPending() {
        assertTrue(GameEngine.validTargets(GameEngine.newGame()).isEmpty())
    }

    @Test fun selfTargetingIsProhibited() {
        val s = loadedShooter().with(K, 1, Stage.BODY).shootingOpportunity()
        val r = GameEngine.fireShot(s, K, 1, s.turnNumber)
        assertEquals(EngineResult.Rejected(RejectReason.SELF_TARGET), r)
        assertEquals(EngineResult.Rejected(RejectReason.SELF_TARGET), GameEngine.fireShot(s, K, 0, s.turnNumber))
    }

    @Test fun deadTargetIsProhibited() {
        val s = loadedShooter().kill(S, 2).shootingOpportunity()
        assertEquals(EngineResult.Rejected(RejectReason.DEAD_TARGET), GameEngine.fireShot(s, S, 2, s.turnNumber))
        assertEquals(EngineResult.Rejected(RejectReason.INVALID_TARGET_INDEX), GameEngine.fireShot(s, S, 5, s.turnNumber))
        assertEquals(EngineResult.Rejected(RejectReason.INVALID_TARGET_INDEX), GameEngine.fireShot(s, S, -1, s.turnNumber))
    }

    @Test fun bulletIsConsumedOnceAndShooterRemainsAliveWithUnloadedGun() {
        val s = loadedShooter().shootingOpportunity()
        val r = s.fire(0)
        val shooter = r.state.character(K, 0)
        assertTrue(shooter.alive)
        assertTrue(shooter.hasGun)
        assertFalse(shooter.bulletLoaded)
        assertEquals(Stage.GUN, shooter.stage)
        assertEquals(1, r.events.count { it is GameEvent.ShotFired })
        assertEquals(S, r.state.currentPlayer)
        assertEquals(Phase.AWAITING_PAGE, r.state.phase)
    }

    @Test fun cannotShootWithoutAPendingOpportunity() {
        val s = loadedShooter() // loaded but the digit has not appeared
        assertEquals(EngineResult.Rejected(RejectReason.NOT_AWAITING_TARGET), GameEngine.fireShot(s, S, 0, s.turnNumber))
    }

    @Test fun pageCannotBeSubmittedWhileTargetIsPending() {
        val s = loadedShooter().shootingOpportunity()
        assertEquals(EngineResult.Rejected(RejectReason.NOT_AWAITING_PAGE), GameEngine.submitPage(s, pageFor(1), s.turnNumber))
    }

    @Test fun invalidPagesAreRejectedByEngine() {
        val s = GameEngine.newGame()
        listOf(0, -2, 7, 127).forEach {
            assertEquals(EngineResult.Rejected(RejectReason.INVALID_PAGE), GameEngine.submitPage(s, it, s.turnNumber))
        }
    }
}

class DefenceTest {
    private fun shootAt(stage: Stage): EngineResult.Applied {
        val s = loadedShooter().with(S, 0, Stage.CIRCLE).with(S, 1, stage).shootingOpportunity()
        return s.fire(1)
    }

    @Test fun stageOneTargetDies() = dies(Stage.CIRCLE)
    @Test fun stageTwoTargetDies() = dies(Stage.FACE)
    @Test fun stageThreeTargetDies() = dies(Stage.BODY)

    private fun dies(stage: Stage) {
        val r = shootAt(stage)
        val t = r.state.character(S, 1)
        assertFalse(t.alive)
        assertEquals(stage, t.stage) // drawing is preserved, character is only marked dead
        assertTrue(r.events.contains(GameEvent.CharacterKilled(S, 1)))
        assertTrue(r.events.none { it is GameEvent.GunDestroyed })
        // dead characters never recover
        assertFalse(r.state.character(S, 1).hasGun)
    }

    @Test fun stageFourTargetLosesGunAndReturnsToStageThree() {
        val r = shootAt(Stage.GUN)
        val t = r.state.character(S, 1)
        assertTrue(t.alive)
        assertEquals(Stage.BODY, t.stage)
        assertFalse(t.hasGun)
        assertEquals(Expression.SHOCKED, t.expression)
        assertTrue(r.events.contains(GameEvent.GunDestroyed(S, 1, hadBullet = false)))
    }

    @Test fun stageFiveTargetLosesGunAndBulletAndReturnsToStageThree() {
        val r = shootAt(Stage.LOADED)
        val t = r.state.character(S, 1)
        assertTrue(t.alive)
        assertEquals(Stage.BODY, t.stage)
        assertFalse(t.hasGun)
        assertFalse(t.bulletLoaded)
        assertEquals(Expression.SHOCKED, t.expression)
        assertTrue(r.events.contains(GameEvent.GunDestroyed(S, 1, hadBullet = true)))
    }

    @Test fun otherCharactersAreUntouched() {
        val before = loadedShooter().with(S, 1, Stage.LOADED).shootingOpportunity()
        val after = before.fire(1).state
        for (i in listOf(0, 2, 3, 4)) assertEquals(before.character(S, i), after.character(S, i))
    }

    @Test fun shockedExpressionSettlesBackToNormalWithoutChangingDrawingState() {
        val after = shootAt(Stage.LOADED).state
        val settled = GameEngine.settleExpressions(after)
        assertEquals(Expression.NORMAL, settled.character(S, 1).expression)
        assertEquals(Stage.BODY, settled.character(S, 1).stage)
        assertTrue(settled.character(S, 1).alive)
    }

    @Test fun cunningExpressionIsCosmeticOnly() {
        val s = loadedShooter()
        val c = GameEngine.setExpression(s, K, 0, Expression.CUNNING)
        assertEquals(Expression.CUNNING, c.character(K, 0).expression)
        assertEquals(s.character(K, 0).stage, c.character(K, 0).stage)
        assertEquals(s, GameEngine.settleExpressions(c))
        // dead characters take no expression
        val dead = s.kill(S, 0)
        assertEquals(Expression.NORMAL, GameEngine.setExpression(dead, S, 0, Expression.SHOCKED).character(S, 0).expression)
    }
}

class ReloadTest {
    @Test fun stageFourMatchingDigitReloadsWithoutShootingOrExtraTurn() {
        val s = GameEngine.newGame().with(K, 0, Stage.GUN)
        val r = s.play(0)
        assertEquals(Stage.LOADED, r.state.character(K, 0).stage)
        assertEquals(S, r.state.currentPlayer)          // turn passed normally
        assertEquals(s.turnNumber + 1, r.state.turnNumber)
        assertNull(r.state.pendingShot)                // no shot, no second attempt
        assertEquals(Phase.AWAITING_PAGE, r.state.phase)
    }

    @Test fun stageFourIsNeverConfusedWithShooting() {
        val s = GameEngine.newGame().with(K, 0, Stage.GUN)
        assertEquals(TurnAction.LoadBullet, GameEngine.previewAction(s, pageFor(0)))
        assertEquals(TurnAction.OfferShot, GameEngine.previewAction(s.with(K, 0, Stage.LOADED), pageFor(0)))
    }

    @Test fun unlimitedReloadCycles() {
        var s = GameEngine.newGame()
        s = s.with(K, 0, Stage.GUN).with(S, 0, Stage.BODY).with(S, 1, Stage.CIRCLE)
        repeat(50) {
            // Kappu: reload (stage 4 -> 5)
            val loadedState = s.play(0).state
            assertEquals(Stage.LOADED, loadedState.character(K, 0).stage)
            // Suppu burns a turn on character 1 (cycles through stages but is never shot)
            s = loadedState.play(4).state.let { it.with(S, 4, Stage.CIRCLE) }
            // Kappu: shooting opportunity, shoots Suppu's character 2? keep targets alive by shooting stage-4 guns
            val withGun = s.with(S, 2, Stage.GUN)
            val shot = withGun.shootingOpportunity()
            val fired = shot.fire(2).state
            assertEquals(Stage.GUN, fired.character(K, 0).stage) // gun present, empty -> can reload again
            assertTrue(fired.character(K, 0).alive)
            assertEquals(Stage.BODY, fired.character(S, 2).stage)
            s = fired.play(4).state.with(S, 4, Stage.CIRCLE)     // Suppu's turn
            s = s.with(S, 2, Stage.GUN)
            assertEquals(K, s.currentPlayer)
        }
    }

    @Test fun reloadNeedsAFutureTurn_noSecondAttemptInTheSameTurn() {
        val s = loadedShooter().with(S, 1, Stage.BODY).shootingOpportunity()
        val after = s.fire(1).state
        assertEquals(S, after.currentPlayer)
        assertEquals(Stage.GUN, after.character(K, 0).stage)
        assertEquals(Phase.AWAITING_PAGE, after.phase)
    }
}

class RecoveryTest {
    @Test fun destroyedGunSurvivorRebuildsStepByStepAndNeverSkipsToStageFive() {
        var s = loadedShooter().with(S, 1, Stage.LOADED).shootingOpportunity().fire(1).state
        s = GameEngine.settleExpressions(s)
        assertEquals(Stage.BODY, s.character(S, 1).stage)
        assertTrue(s.character(S, 1).alive)
        assertEquals(S, s.currentPlayer)

        // first future matching turn: stage 3 -> 4 (gun drawn, unloaded)
        val rebuild = GameEngine.submitPage(s, pageFor(1), s.turnNumber).applied()
        assertEquals(Stage.GUN, rebuild.state.character(S, 1).stage)
        assertTrue(rebuild.state.character(S, 1).hasGun)
        assertFalse(rebuild.state.character(S, 1).bulletLoaded)
        assertEquals(listOf<GameEvent>(GameEvent.Progressed(S, 1, Stage.GUN)), rebuild.events)

        // second future matching turn: stage 4 -> 5 (bullet loaded)
        val afterKappu = rebuild.state.with(K, 3, Stage.CIRCLE).let { GameEngine.submitPage(it, pageFor(3), it.turnNumber).applied().state }
        val reload = GameEngine.submitPage(afterKappu, pageFor(1), afterKappu.turnNumber).applied()
        assertEquals(Stage.LOADED, reload.state.character(S, 1).stage)
        assertEquals(listOf<GameEvent>(GameEvent.BulletLoaded(S, 1)), reload.events)
    }

    @Test fun survivorKeepsFaceAndBodyStage() {
        val s = loadedShooter().with(S, 0, Stage.GUN).shootingOpportunity().fire(0).state
        val c = s.character(S, 0)
        assertEquals(Stage.BODY, c.stage) // face + body retained
        assertTrue(c.alive)
    }
}

class TurnManagementTest {
    @Test fun kappuAndSuppuAlternate() {
        var s = GameEngine.newGame()
        val order = mutableListOf<PlayerId>()
        repeat(6) { order += s.currentPlayer; s = s.play(0).state.let { n -> n.with(n.currentPlayer.opponent, 0, Stage.CIRCLE) } }
        assertEquals(listOf(K, S, K, S, K, S), order)
    }

    @Test fun turnNumberAdvancesByExactlyOnePerTurn() {
        val s = GameEngine.newGame()
        assertEquals(1, s.turnNumber)
        assertEquals(2, s.play(0).state.turnNumber)
    }

    @Test fun deadCharacterConsumesTheTurnWithoutAction() {
        val s = GameEngine.newGame().kill(K, 2)
        val r = s.play(2)
        assertEquals(S, r.state.currentPlayer)
        assertEquals(s.kappu, r.state.kappu)
        assertEquals(s.suppu, r.state.suppu)
    }

    @Test fun noExtraTurnsAreEverGranted() {
        // every kind of action advances the turn by exactly one
        val actions = listOf(Stage.CIRCLE, Stage.FACE, Stage.BODY, Stage.GUN)
        for (stage in actions) {
            val s = GameEngine.newGame().with(K, 0, stage)
            val r = s.play(0)
            assertEquals(S, r.state.currentPlayer)
            assertEquals(2, r.state.turnNumber)
        }
        val shot = loadedShooter().shootingOpportunity().fire(0)
        assertEquals(S, shot.state.currentPlayer)
        assertEquals(2, shot.state.turnNumber)
    }

    @Test fun eliminatedPlayerIsSkipped() {
        val s = GameEngine.newGame().killAll(S)
        assertEquals(K, GameEngine.nextPlayer(s))
        val alive = GameEngine.newGame()
        assertEquals(S, GameEngine.nextPlayer(alive))
    }

    @Test fun lastCharacterKilledEndsTheGameWithCorrectWinner() {
        val s = loadedShooter().killAll(S, except = setOf(3)).shootingOpportunity()
        val r = s.fire(3)
        assertEquals(K, r.state.winner)
        assertEquals(Phase.GAME_OVER, r.state.phase)
        assertTrue(r.state.suppu.isEliminated)
        assertFalse(r.state.kappu.isEliminated)
        assertTrue(r.events.contains(GameEvent.PlayerEliminated(S)))
        assertTrue(r.events.contains(GameEvent.GameWon(K)))
    }

    @Test fun suppuCanWinToo() {
        val base = GameEngine.newGame().killAll(K, except = setOf(0)).with(S, 2, Stage.LOADED)
        val inK = base.play(1).state // Kappu burns turn on a dead character
        val shot = GameEngine.submitPage(inK, pageFor(2), inK.turnNumber).applied().state
        val r = shot.fire(0, K)
        assertEquals(S, r.state.winner)
    }

    @Test fun lastSurvivorWithGunDoesNotLoseWhenShot() {
        // Hitting a gun only destroys the gun, so the last character survives.
        val s = loadedShooter().killAll(S, except = setOf(1)).with(S, 1, Stage.GUN).shootingOpportunity()
        val r = s.fire(1)
        assertNull(r.state.winner)
        assertEquals(Phase.AWAITING_PAGE, r.state.phase)
        assertTrue(r.state.character(S, 1).alive)
    }

    @Test fun noMovesAfterGameOver() {
        val over = loadedShooter().killAll(S, except = setOf(0)).shootingOpportunity().fire(0).state
        assertEquals(EngineResult.Rejected(RejectReason.GAME_OVER), GameEngine.submitPage(over, 10, over.turnNumber))
        assertEquals(EngineResult.Rejected(RejectReason.GAME_OVER), GameEngine.fireShot(over, S, 0, over.turnNumber))
        assertTrue(GameEngine.validTargets(over).isEmpty())
    }

    @Test fun randomGamesNeverBreakInvariants() {
        repeat(300) { seed ->
            val rnd = Random(seed)
            var s = GameEngine.newGame()
            var guard = 0
            while (s.phase != Phase.GAME_OVER && guard++ < 5000) {
                s = when (s.phase) {
                    Phase.AWAITING_PAGE -> GameEngine.submitPage(s, (rnd.nextInt(1, 101)) * 2, s.turnNumber).applied().state
                    Phase.AWAITING_TARGET -> {
                        val targets = GameEngine.validTargets(s)
                        assertTrue(targets.isNotEmpty())
                        GameEngine.fireShot(s, s.pendingShot!!.shooter.opponent, targets.random(rnd), s.turnNumber).applied().state
                    }
                    Phase.GAME_OVER -> s
                }
                for (p in PlayerId.entries) for (c in s.player(p).characters) {
                    if (!c.alive) assertFalse(c.stage.hasGun)
                    if (c.stage.number <= 3) assertFalse(c.hasGun)
                    if (c.stage == Stage.GUN) assertFalse(c.bulletLoaded)
                    if (c.stage == Stage.LOADED) assertTrue(c.hasGun && c.bulletLoaded)
                }
            }
            assertEquals("game $seed should end", Phase.GAME_OVER, s.phase)
            val w = s.winner!!
            assertTrue(s.player(w.opponent).isEliminated)
            assertFalse(s.player(w).isEliminated)
        }
    }
}

class InputProtectionTest {
    @Test fun duplicateRapidPageSubmitIsRejectedAndAppliesOnce() {
        val s = GameEngine.newGame()
        val turn = s.turnNumber
        val first = GameEngine.submitPage(s, pageFor(0), turn).applied()
        // the second tap re-sends the same turn number against the NEW state
        val second = GameEngine.submitPage(first.state, pageFor(0), turn)
        assertEquals(EngineResult.Rejected(RejectReason.STALE_TURN), second)
        assertEquals(Stage.FACE, first.state.character(K, 0).stage)
        assertEquals(Stage.CIRCLE, first.state.character(S, 0).stage)
    }

    @Test fun duplicateRapidShotIsRejectedAndConsumesBulletOnce() {
        val pending = loadedShooter().with(S, 1, Stage.GUN).with(S, 2, Stage.BODY).shootingOpportunity()
        val turn = pending.turnNumber
        val first = GameEngine.fireShot(pending, S, 1, turn).applied()
        val second = GameEngine.fireShot(first.state, S, 2, turn)
        assertTrue(second is EngineResult.Rejected)
        assertEquals(Stage.BODY, first.state.character(S, 1).stage)
        assertEquals(Stage.BODY, first.state.character(S, 2).stage)
        assertTrue(first.state.character(S, 2).alive) // never hit a second time
        assertEquals(1, first.events.count { it is GameEvent.ShotFired })
    }

    @Test fun staleTurnTokenIsRejected() {
        val s = loadedShooter().shootingOpportunity()
        assertEquals(EngineResult.Rejected(RejectReason.STALE_TURN), GameEngine.fireShot(s, S, 0, s.turnNumber - 1))
    }

    @Test fun cancelledTargetSelectionDoesNotConsumeBullet() {
        val pending = loadedShooter().shootingOpportunity()
        val cancelled = GameEngine.cancelShot(pending)
        assertEquals(pending, cancelled)
        assertTrue(cancelled.character(K, 0).bulletLoaded)
        assertEquals(Phase.AWAITING_TARGET, cancelled.phase)
        assertEquals(K, cancelled.currentPlayer)
        cancelled.suppu.characters.forEach { assertEquals(Stage.CIRCLE, it.stage); assertTrue(it.alive) }
        // and the shot can still be taken afterwards
        assertTrue(GameEngine.fireShot(cancelled, S, 0, cancelled.turnNumber) is EngineResult.Applied)
    }

    @Test fun confirmedSelectionCommitsOnlyOnce() {
        val pending = loadedShooter().with(S, 0, Stage.LOADED).shootingOpportunity()
        val once = GameEngine.fireShot(pending, S, 0, pending.turnNumber).applied()
        val twice = GameEngine.fireShot(once.state, S, 0, pending.turnNumber)
        assertTrue(twice is EngineResult.Rejected)
        assertEquals(Stage.BODY, once.state.character(S, 0).stage)
    }
}
