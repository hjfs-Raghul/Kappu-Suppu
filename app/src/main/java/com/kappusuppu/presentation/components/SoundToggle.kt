package com.kappusuppu.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kappusuppu.drawing.Pt
import com.kappusuppu.drawing.Wobble
import com.kappusuppu.presentation.theme.HandText
import kotlin.random.Random

/** Hand-drawn speaker: with sound waves when on, with a cross when off. */
@Composable
fun SoundToggle(enabled: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .heightIn(min = 52.dp)
            .clickable(onClick = onToggle)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val seed = remember { 99L }
        Canvas(Modifier.size(40.dp)) {
            val rnd = Random(seed)
            val s = size.width
            fun p(x: Float, y: Float) = Pt(x * s, y * s)
            val w = 2.4.dp.toPx()
            val speaker = Wobble.polyline(
                listOf(p(0.12f, 0.38f), p(0.30f, 0.38f), p(0.50f, 0.2f), p(0.50f, 0.8f), p(0.30f, 0.62f), p(0.12f, 0.62f)),
                rnd, closed = true, amp = 1.2f, step = 8f,
            )
            drawPencil(speaker, widthPx = w)
            if (enabled) {
                drawPencil(Wobble.curve(p(0.62f, 0.38f), p(0.72f, 0.5f), p(0.62f, 0.62f), rnd, 1.2f), widthPx = w)
                drawPencil(Wobble.curve(p(0.70f, 0.26f), p(0.90f, 0.5f), p(0.70f, 0.74f), rnd, 1.2f), widthPx = w)
            } else {
                drawPencil(Wobble.line(p(0.62f, 0.36f), p(0.92f, 0.64f), rnd, 1.2f, 8f), widthPx = w)
                drawPencil(Wobble.line(p(0.92f, 0.36f), p(0.62f, 0.64f), rnd, 1.2f, 8f), widthPx = w)
            }
        }
        HandText(if (enabled) "Sound: on" else "Sound: off", size = 22.sp)
    }
}
