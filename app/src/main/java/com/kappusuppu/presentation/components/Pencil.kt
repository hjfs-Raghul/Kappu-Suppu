package com.kappusuppu.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kappusuppu.drawing.Pt
import com.kappusuppu.drawing.Wobble
import com.kappusuppu.presentation.theme.HandText
import com.kappusuppu.presentation.theme.Notebook
import kotlin.random.Random

/** Pixel-space hand-drawn shapes (the character strokes live in a 0..1 box instead). */
object PencilShapes {
    private const val AMP = 1.7f
    private const val STEP = 14f

    fun line(ax: Float, ay: Float, bx: Float, by: Float, rnd: Random): List<Pt> =
        Wobble.line(Pt(ax, ay), Pt(bx, by), rnd, AMP, STEP)

    fun rect(l: Float, t: Float, r: Float, b: Float, rnd: Random): List<Pt> =
        Wobble.polyline(listOf(Pt(l, t), Pt(r, t), Pt(r, b), Pt(l, b)), rnd, closed = true, amp = AMP, step = STEP)

    fun loop(cx: Float, cy: Float, rx: Float, ry: Float, rnd: Random): List<Pt> =
        Wobble.ellipse(cx, cy, rx, ry, rnd, overlap = 0.14, step = 8f)
}

fun List<Pt>.toPath(): Path {
    val path = Path()
    if (isEmpty()) return path
    path.moveTo(first().x, first().y)
    for (i in 1 until size) path.lineTo(this[i].x, this[i].y)
    return path
}

/** A pencil line: one firm pass and one lighter, thinner pass slightly offset (graphite texture). */
fun DrawScope.drawPencil(points: List<Pt>, color: Color = Notebook.Graphite, widthPx: Float, alpha: Float = 1f) {
    if (points.size < 2) return
    val path = points.toPath()
    val style = Stroke(width = widthPx, cap = StrokeCap.Round, join = StrokeJoin.Round)
    drawPath(path, color.copy(alpha = 0.92f * alpha), style = style)
    val thin = Stroke(width = widthPx * 0.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val shifted = Path().apply { addPath(path, Offset(widthPx * 0.35f, widthPx * 0.3f)) }
    drawPath(shifted, color.copy(alpha = 0.35f * alpha), style = thin)
}

/** Imperfect pencil rectangle around any content. [double] draws a second pass for emphasis. */
fun Modifier.pencilBorder(seed: Long, width: Dp = 2.dp, double: Boolean = false): Modifier = drawWithCache {
    val rnd = Random(seed)
    val w = width.toPx()
    val first = PencilShapes.rect(w, w, size.width - w, size.height - w, rnd)
    val second = if (double) PencilShapes.rect(w * 2.6f, w * 2.6f, size.width - w * 2.6f, size.height - w * 2.6f, rnd) else null
    onDrawWithContent {
        drawContent()
        drawPencil(first, widthPx = w)
        second?.let { drawPencil(it, widthPx = w * 0.8f) }
    }
}

/** Notebook paper: off-white with faint, slightly wobbly ruling and a faint margin line. */
fun Modifier.notebookPage(): Modifier = drawWithCache {
    val rnd = Random(7)
    val gap = 30.dp.toPx()
    val lines = ArrayList<List<Pt>>()
    var y = gap * 1.2f
    while (y < size.height) {
        lines += Wobble.line(Pt(0f, y), Pt(size.width, y), rnd, 0.9f, 26f)
        y += gap
    }
    val margin = Wobble.line(Pt(size.width * 0.1f, 0f), Pt(size.width * 0.1f, size.height), rnd, 1.2f, 30f)
    onDrawBehind {
        drawRect(Notebook.Paper)
        val w = 1.dp.toPx()
        lines.forEach { drawPencil(it, Notebook.Rule, w) }
        drawPencil(margin, Notebook.Rule, w * 1.4f)
    }
}

@Composable
fun PencilButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
    textSize: TextUnit = 24.sp,
) {
    val seed = remember(text) { text.hashCode().toLong() }
    Box(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.4f)
            .pencilBorder(seed, if (selected) 3.dp else 2.dp, double = selected)
            .clickable(enabled = enabled, onClick = onClick)
            .heightIn(min = 52.dp)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        HandText(text, size = textSize, align = TextAlign.Center, bold = selected)
    }
}
