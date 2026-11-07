package com.kappusuppu.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.kappusuppu.domain.model.Expression
import com.kappusuppu.domain.model.PlayerId
import com.kappusuppu.domain.model.Stage
import com.kappusuppu.drawing.CharKey
import com.kappusuppu.drawing.StaticInk

/** A fully drawn character at a given stage (How to Play, home doodles, result screen). */
@Composable
fun StaticCharacter(
    stage: Stage,
    modifier: Modifier = Modifier,
    dead: Boolean = false,
    expression: Expression = Expression.NORMAL,
    seed: Long = 1L,
) {
    val strokes = remember(stage, dead, seed) { StaticInk.strokes(stage, dead, seed) }
    CharacterCanvas(CharKey(PlayerId.KAPPU, 0), strokes, expression, modifier)
}
