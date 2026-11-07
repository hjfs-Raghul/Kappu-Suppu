package com.kappusuppu.presentation.game

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kappusuppu.domain.model.CHARACTERS_PER_PLAYER
import com.kappusuppu.domain.model.PlayerId
import com.kappusuppu.drawing.CharKey
import com.kappusuppu.drawing.Pt
import com.kappusuppu.presentation.components.CharacterCanvas
import com.kappusuppu.presentation.components.PencilShapes
import com.kappusuppu.presentation.components.drawPencil
import com.kappusuppu.presentation.theme.HandText
import kotlin.random.Random

private val HEADER_HEIGHT = 56.dp

/**
 * THE board: one notebook page with a hand-drawn table of exactly 6 rows x 2 columns.
 * Row 1 = KAPPU | SUPPU, rows 2-6 = Character 1..5 of each player. Both players are always visible.
 */
@Composable
fun NotebookBoard(
    state: SessionState,
    marker: PlayerId?,
    onStrokeDrawn: (CharKey, Long) -> Unit,
    onStrokesErased: (CharKey, Set<Long>) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.drawWithCache {
            val rnd = Random(2024)
            val w = size.width
            val h = size.height
            val inset = 3f
            val headerH = HEADER_HEIGHT.toPx()
            val rowH = (h - headerH) / CHARACTERS_PER_PLAYER
            val lines = ArrayList<List<Pt>>()
            lines += PencilShapes.rect(inset, inset, w - inset, h - inset, rnd)
            lines += PencilShapes.line(w / 2f, inset, w / 2f, h - inset, rnd)
            lines += PencilShapes.line(inset, headerH, w - inset, headerH, rnd)
            lines += PencilShapes.line(inset, headerH + 4f, w - inset, headerH + 4f, rnd) // heavier header rule
            for (i in 1 until CHARACTERS_PER_PLAYER) {
                val y = headerH + rowH * i
                lines += PencilShapes.line(inset, y, w - inset, y, rnd)
            }
            val stroke = 2.4.dp.toPx()
            onDrawBehind { lines.forEach { drawPencil(it, widthPx = stroke) } }
        },
    ) {
        Row(Modifier.fillMaxWidth().height(HEADER_HEIGHT)) {
            for (id in PlayerId.entries) {
                HeaderCell(
                    id = id,
                    human = state.assignment.humanLabel(id),
                    active = marker == id,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        }
        for (index in 0 until CHARACTERS_PER_PLAYER) {
            Row(Modifier.fillMaxWidth().weight(1f)) {
                for (id in PlayerId.entries) {
                    val key = CharKey(id, index)
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        CharacterCanvas(
                            key = key,
                            strokes = state.ink.of(key),
                            expression = state.game.character(id, index).expression,
                            modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 6.dp),
                            onStrokeDrawn = onStrokeDrawn,
                            onStrokesErased = onStrokesErased,
                        )
                        HandText(
                            "Character ${index + 1}",
                            size = 13.sp,
                            modifier = Modifier.align(Alignment.TopStart).padding(start = 8.dp, top = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderCell(id: PlayerId, human: String, active: Boolean, modifier: Modifier) {
    Box(
        modifier = modifier.drawWithCache {
            val rnd = Random(id.ordinal + 5L)
            // the active player's name is circled in pencil, with a pencil underline
            val circle = if (active) PencilShapes.loop(size.width / 2f, size.height / 2f, size.width * 0.42f, size.height * 0.46f, rnd) else null
            val underline = if (active) PencilShapes.line(size.width * 0.18f, size.height * 0.9f, size.width * 0.82f, size.height * 0.9f, rnd) else null
            val w = 2.dp.toPx()
            onDrawBehind {
                circle?.let { drawPencil(it, widthPx = w) }
                underline?.let { drawPencil(it, widthPx = w * 0.8f) }
            }
        },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            HandText(id.displayName.uppercase(), size = 26.sp, bold = true, align = TextAlign.Center)
            HandText(human, size = 12.sp, align = TextAlign.Center)
        }
    }
}
