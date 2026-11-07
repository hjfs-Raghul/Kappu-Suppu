package com.kappusuppu

import com.kappusuppu.domain.engine.EngineResult
import com.kappusuppu.domain.model.GameState
import com.kappusuppu.domain.model.PlayerId
import com.kappusuppu.domain.model.Stage
import org.junit.Assert.assertTrue

/** Page whose last digit selects character [index] (0..4): 10, 12, 14, 16, 18. */
fun pageFor(index: Int): Int = 10 + index * 2

fun GameState.with(player: PlayerId, index: Int, stage: Stage, alive: Boolean = true): GameState =
    updateCharacter(player, index) { it.copy(stage = stage, alive = alive) }

fun GameState.kill(player: PlayerId, index: Int, stage: Stage = Stage.CIRCLE): GameState = with(player, index, stage, alive = false)

fun GameState.killAll(player: PlayerId, except: Set<Int> = emptySet()): GameState =
    (0 until 5).filterNot { it in except }.fold(this) { s, i -> s.kill(player, i) }

fun EngineResult.applied(): EngineResult.Applied {
    assertTrue("Expected Applied but was $this", this is EngineResult.Applied)
    return this as EngineResult.Applied
}
