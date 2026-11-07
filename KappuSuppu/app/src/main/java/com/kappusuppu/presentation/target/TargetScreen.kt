package com.kappusuppu.presentation.target

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kappusuppu.domain.model.CHARACTERS_PER_PLAYER
import com.kappusuppu.drawing.CharKey
import com.kappusuppu.presentation.components.CharacterCanvas
import com.kappusuppu.presentation.components.PencilButton
import com.kappusuppu.presentation.components.PencilShapes
import com.kappusuppu.presentation.components.drawPencil
import com.kappusuppu.presentation.components.notebookPage
import com.kappusuppu.presentation.components.pencilBorder
import com.kappusuppu.presentation.game.SessionState
import com.kappusuppu.presentation.theme.HandText
import kotlin.random.Random

/**
 * The shooter picks any LIVING opponent character, whatever its stage, then confirms. Own characters
 * are not offered at all. The result of the defence rules is shown here once the shot is fired.
 */
@Composable
fun TargetScreen(
    state: SessionState,
    onSelect: (Int) -> Unit,
    onCancel: () -> Unit,
    onFire: () -> Unit,
    onContinue: () -> Unit,
    onStrokeDrawn: (CharKey, Long) -> Unit,
    onStrokesErased: (CharKey, Set<Long>) -> Unit,
) {
    val shot = state.shotContext
    BackHandler(enabled = !state.firing && state.fireOutcome == null, onBack = onCancel)
    if (shot == null) return // nothing to aim: navigation moves away on its own

    val target = shot.shooter.opponent
    val locked = state.firing || state.fireOutcome != null

    Column(
        Modifier.fillMaxSize().notebookPage().systemBarsPadding().padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        HandText("Pick a target", size = 34.sp, bold = true)
        HandText(
            "${shot.shooter.displayName}'s Character ${shot.shooterIndex + 1} has a loaded gun. " +
                "Choose one of ${target.displayName}'s characters.",
            size = 18.sp,
            align = TextAlign.Center,
        )
        HandText(target.displayName.uppercase(), size = 26.sp, bold = true)

        Column(Modifier.weight(1f).fillMaxWidth().pencilBorder(5, 2.dp)) {
            for (index in 0 until CHARACTERS_PER_PLAYER) {
                val character = state.game.character(target, index)
                val key = CharKey(target, index)
                val selected = state.selectedTarget == index
                val selectable = character.alive && !locked
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .selectionRing(selected, index.toLong())
                        .clickable(enabled = selectable) { onSelect(index) },
                ) {
                    CharacterCanvas(
                        key = key,
                        strokes = state.ink.of(key),
                        expression = character.expression,
                        modifier = Modifier.fillMaxSize().padding(vertical = 3.dp),
                        onStrokeDrawn = onStrokeDrawn,
                        onStrokesErased = onStrokesErased,
                    )
                    HandText(
                        text = "Character ${index + 1}" + if (!character.alive) "  (dead)" else "",
                        size = 16.sp,
                        modifier = Modifier.align(Alignment.CenterStart).padding(start = 12.dp),
                    )
                }
            }
        }

        val outcome = state.fireOutcome
        if (outcome != null) {
            HandText(outcome, size = 21.sp, align = TextAlign.Center)
            PencilButton("Continue", onClick = onContinue, modifier = Modifier.fillMaxWidth(), textSize = 26.sp)
        } else {
            val chosen = state.selectedTarget
            HandText(
                if (chosen == null) "Tap a living character." else "Target: ${target.displayName}'s Character ${chosen + 1}",
                size = 20.sp,
                align = TextAlign.Center,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PencilButton("Cancel", onClick = onCancel, enabled = !locked, modifier = Modifier.weight(1f))
                PencilButton("Fire!", onClick = onFire, enabled = chosen != null && !locked, modifier = Modifier.weight(1f))
            }
        }
    }
}

/** A pencil circle scribbled around the selected row. */
private fun Modifier.selectionRing(selected: Boolean, seed: Long): Modifier = drawWithCache {
    val ring = if (selected) {
        PencilShapes.loop(size.width / 2f, size.height / 2f, size.width * 0.47f, size.height * 0.46f, Random(seed + 40))
    } else null
    onDrawBehind { ring?.let { drawPencil(it, widthPx = 3.dp.toPx()) } }
}
