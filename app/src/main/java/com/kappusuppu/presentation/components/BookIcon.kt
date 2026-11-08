package com.kappusuppu.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kappusuppu.drawing.Pt
import com.kappusuppu.drawing.Wobble
import kotlin.random.Random

/** A hand-drawn open book: the "open page" button, like a dice in a board game. */
@Composable
fun BookIcon(modifier: Modifier = Modifier, seed: Long = 1L) {
    Canvas(modifier) {
        val rnd = Random(seed)
        val s = size.minDimension
        fun p(x: Float, y: Float) = Pt(x * s, y * s)
        val w = 2.4.dp.toPx()
        val left = Wobble.polyline(listOf(p(0.04f, 0.26f), p(0.48f, 0.34f), p(0.48f, 0.84f), p(0.04f, 0.76f)), rnd, closed = true, amp = 1.3f, step = 9f)
        val right = Wobble.polyline(listOf(p(0.52f, 0.34f), p(0.96f, 0.26f), p(0.96f, 0.76f), p(0.52f, 0.84f)), rnd, closed = true, amp = 1.3f, step = 9f)
        drawPencil(left, widthPx = w)
        drawPencil(right, widthPx = w)
        drawPencil(Wobble.line(p(0.5f, 0.32f), p(0.5f, 0.86f), rnd, 1f, 9f), widthPx = w * 0.8f)
        // text lines on both pages
        for (i in 0..2) {
            val y = 0.46f + i * 0.1f
            drawPencil(Wobble.line(p(0.12f, y - 0.01f * i), p(0.40f, y + 0.02f), rnd, 0.8f, 9f), widthPx = w * 0.5f)
            drawPencil(Wobble.line(p(0.60f, y + 0.02f), p(0.88f, y - 0.01f * i), rnd, 0.8f, 9f), widthPx = w * 0.5f)
        }
    }
}
