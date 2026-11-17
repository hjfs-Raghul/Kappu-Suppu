package com.kappusuppu.presentation.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.kappusuppu.domain.model.Expression
import com.kappusuppu.domain.model.PlayerId
import com.kappusuppu.drawing.CharKey
import com.kappusuppu.drawing.InkColor
import com.kappusuppu.drawing.InkMath
import com.kappusuppu.drawing.InkStroke
import com.kappusuppu.drawing.Pt
import com.kappusuppu.drawing.StrokeFactory
import com.kappusuppu.drawing.StrokeGroup
import com.kappusuppu.drawing.StrokePhase
import com.kappusuppu.presentation.theme.Notebook
import kotlin.math.max
import kotlin.math.min

/**
 * Draws one character from its persistent stroke list.
 *  - DRAWN strokes are painted statically.
 *  - DRAWING strokes (only the newly added ones) are revealed one after another, like a pencil.
 *  - ERASING strokes (gun / bullet) are rubbed out, then removed from the stroke data.
 *  - A temporary [expression] replaces the stored eyes/mouth while it lasts; the stored face is
 *    never changed, so the normal face comes back by itself.
 */
@Composable
fun CharacterCanvas(
    key: CharKey,
    strokes: List<InkStroke>,
    expression: Expression,
    modifier: Modifier = Modifier,
    onStrokeDrawn: (CharKey, Long) -> Unit = { _, _ -> },
    onStrokesErased: (CharKey, Set<Long>) -> Unit = { _, _ -> },
) {
    val progress = remember { mutableStateMapOf<Long, Float>() }
    val latest = rememberUpdatedState(strokes)

    // ---- pencil drawing of new strokes, one after another ----
    val drawingIds = strokes.filter { it.phase == StrokePhase.DRAWING }.map { it.id }
    LaunchedEffect(drawingIds) {
        for (id in drawingIds) {
            val stroke = latest.value.firstOrNull { it.id == id } ?: continue
            val anim = Animatable(progress[id] ?: 0f)
            val ms = (InkMath.length(stroke.points) * 1500f).toInt().coerceIn(170, 700)
            anim.animateTo(1f, tween(ms, easing = LinearEasing)) { progress[id] = value }
            progress[id] = 1f
            onStrokeDrawn(key, id)
        }
    }

    // ---- rubbing out the gun / bullet ----
    val erasingIds = strokes.filter { it.phase == StrokePhase.ERASING }.map { it.id }
    val eraseAlpha = remember { Animatable(1f) }
    LaunchedEffect(erasingIds) {
        eraseAlpha.snapTo(1f)
        if (erasingIds.isNotEmpty()) {
            eraseAlpha.animateTo(0f, tween(550))
            onStrokesErased(key, erasingIds.toSet())
        }
    }

    // ---- temporary expression overlay + shocked shake ----
    val overlay = remember(expression) {
        StrokeFactory(System.nanoTime()).expressionFace(expression)
    }
    val overlayProgress = remember { Animatable(1f) }
    val shake = remember { Animatable(0f) }
    LaunchedEffect(expression) {
        if (expression == Expression.NORMAL) {
            shake.snapTo(0f)
            return@LaunchedEffect
        }
        overlayProgress.snapTo(0f)
        overlayProgress.animateTo(1f, tween(if (expression == Expression.CUNNING) 380 else 200))
        if (expression == Expression.SHOCKED) {
            repeat(3) {
                shake.animateTo(7f, tween(60))
                shake.animateTo(-7f, tween(60))
            }
            shake.animateTo(0f, tween(60))
        }
    }

    // Kappu faces right and Suppu faces left: Suppu's whole drawing is mirrored, so his gun is on the left
    val mirror = key.player == PlayerId.SUPPU
    val hasFace = strokes.any { it.group == StrokeGroup.FACE }
    val showOverlay = expression != Expression.NORMAL && hasFace
    val dead = strokes.any { it.group == StrokeGroup.CROSS }

    Canvas(modifier.graphicsLayer { translationX = shake.value }) {
        val side = min(size.width, size.height)
        val ox = (size.width - side) / 2f
        val oy = (size.height - side) / 2f
        val base = max(2f, side * 0.022f)
        val bodyAlpha = if (dead) 0.6f else 1f

        fun paint(points: List<Pt>, fraction: Float, color: InkColor, widthFactor: Float, alpha: Float) {
            val part = InkMath.partial(points, fraction)
            if (part.size < 2) return
            val mapped = part.map { Pt(ox + (if (mirror) 1f - it.x else it.x) * side, oy + it.y * side) }
            val c = if (color == InkColor.RED) Notebook.Bullet else Notebook.Graphite
            drawPencil(mapped, c, base * widthFactor, alpha)
        }

        for (s in strokes) {
            if (showOverlay && s.group == StrokeGroup.FACE) continue
            val (fraction, alpha) = when (s.phase) {
                StrokePhase.DRAWN -> 1f to 1f
                StrokePhase.DRAWING -> (progress[s.id] ?: 0f) to 1f
                StrokePhase.ERASING -> 1f to eraseAlpha.value
            }
            val a = if (s.group == StrokeGroup.CROSS) alpha else alpha * bodyAlpha
            paint(s.points, fraction, s.color, s.widthFactor, a)
        }
        if (showOverlay) {
            for (s in overlay) paint(s.points, overlayProgress.value, s.color, s.widthFactor, 1f)
        }
    }
}
