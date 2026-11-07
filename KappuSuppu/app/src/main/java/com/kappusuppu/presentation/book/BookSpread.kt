package com.kappusuppu.presentation.book

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kappusuppu.domain.engine.Spread
import com.kappusuppu.presentation.components.PencilShapes
import com.kappusuppu.presentation.components.drawPencil
import com.kappusuppu.presentation.theme.HandText
import com.kappusuppu.presentation.theme.Notebook
import kotlin.random.Random

/**
 * An illustrated open book. The even page is on the left (the only one the game uses), the following
 * odd page on the right. The right page swings open when the spread appears.
 */
@Composable
fun BookSpread(spread: Spread, modifier: Modifier = Modifier) {
    val open = remember(spread) { Animatable(0f) }
    LaunchedEffect(spread) { open.animateTo(1f, tween(750)) }
    val camera = 14f * LocalDensity.current.density

    Row(modifier.fillMaxWidth().height(150.dp)) {
        Page(
            number = spread.left,
            highlight = true,
            modifier = Modifier.weight(1f).fillMaxHeight().alpha(open.value),
        )
        Page(
            number = spread.right,
            highlight = false,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .graphicsLayer {
                    transformOrigin = TransformOrigin(0f, 0.5f) // hinge on the spine
                    rotationY = -90f * (1f - open.value)
                    cameraDistance = camera
                },
        )
    }
}

@Composable
private fun Page(number: Int, highlight: Boolean, modifier: Modifier) {
    Box(
        modifier = modifier.drawWithCache {
            val rnd = Random(number.toLong())
            val outline = PencilShapes.rect(6f, 6f, size.width - 6f, size.height - 6f, rnd)
            val rules = (1..5).map { i ->
                val y = size.height * (0.14f + i * 0.11f)
                PencilShapes.line(size.width * 0.14f, y, size.width * 0.86f, y, rnd)
            }
            val ring = if (highlight) {
                PencilShapes.loop(size.width / 2f, size.height * 0.84f, size.width * 0.26f, size.height * 0.13f, rnd)
            } else null
            val w = 2.dp.toPx()
            onDrawBehind {
                drawRect(Notebook.Paper)
                drawPencil(outline, widthPx = w)
                rules.forEach { drawPencil(it, Notebook.Rule, w * 0.6f) }
                ring?.let { drawPencil(it, widthPx = w) }
            }
        },
        contentAlignment = Alignment.BottomCenter,
    ) {
        HandText(
            number.toString(),
            size = 26.sp,
            bold = highlight,
            modifier = Modifier.padding(bottom = 18.dp),
        )
    }
}
