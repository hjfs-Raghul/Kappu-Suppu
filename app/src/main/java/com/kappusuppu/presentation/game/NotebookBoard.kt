package com.kappusuppu.presentation.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
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
import com.kappusuppu.presentation.theme.Notebook
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

private val HEADER_HEIGHT = 56.dp
private val CELL_PAD_H = 4.dp
private val CELL_PAD_V = 6.dp

/**
 * THE board: one notebook page with a hand-drawn table of exactly 6 rows x 2 columns.
 * Row 1 = KAPPU | SUPPU, rows 2-6 = the five characters of each player. Both players are always visible.
 *
 * While a shooter must pick a target, the living enemy cells are highlighted and tapping one fires.
 * A transparent overlay draws the muzzle flash, the flying bullet and the impact.
 */
@Composable
fun NotebookBoard(
    state: SessionState,
    targets: Set<Int>,
    onTarget: (Int) -> Unit,
    onStrokeDrawn: (CharKey, Long) -> Unit,
    onStrokesErased: (CharKey, Set<Long>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val targetPlayer = if (targets.isNotEmpty()) state.actor.opponent else null
    val pulse = rememberInfiniteTransition(label = "targets").animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(650, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulse",
    )

    Box(modifier) {
        Column(
            modifier = Modifier.fillMaxSize().drawWithCache {
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
                        active = state.actor == id && state.winner == null,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
            }
            for (index in 0 until CHARACTERS_PER_PLAYER) {
                Row(Modifier.fillMaxWidth().weight(1f)) {
                    for (id in PlayerId.entries) {
                        val key = CharKey(id, index)
                        val isTarget = id == targetPlayer && index in targets
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .targetHighlight(isTarget, pulse, id.ordinal * 10L + index)
                                .then(if (isTarget) Modifier.clickable { onTarget(index) } else Modifier),
                        ) {
                            CharacterCanvas(
                                key = key,
                                strokes = state.ink.of(key),
                                expression = state.game.character(id, index).expression,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = CELL_PAD_H, vertical = CELL_PAD_V),
                                onStrokeDrawn = onStrokeDrawn,
                                onStrokesErased = onStrokesErased,
                            )
                            // a faint reminder of the page digit (0, 2, 4, 6, 8) that moves this character
                            HandText(
                                text = (index * 2).toString(),
                                size = 15.sp,
                                color = Notebook.Graphite.copy(alpha = 0.32f),
                                modifier = Modifier
                                    .align(if (id == PlayerId.KAPPU) Alignment.BottomStart else Alignment.BottomEnd)
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
            }
        }
        ShotOverlay(state.shot, Modifier.matchParentSizeCompat())
    }
}

private fun Modifier.matchParentSizeCompat(): Modifier = this.fillMaxSize()

/** A pulsing pencil ring around a cell the shooter can tap. */
private fun Modifier.targetHighlight(active: Boolean, pulse: State<Float>, seed: Long): Modifier = drawWithCache {
    val rnd = Random(seed + 500)
    val inset = 5.dp.toPx()
    val outer = if (active) PencilShapes.rect(inset, inset, size.width - inset, size.height - inset, rnd) else null
    val inner = if (active) PencilShapes.rect(inset * 1.9f, inset * 1.9f, size.width - inset * 1.9f, size.height - inset * 1.9f, rnd) else null
    onDrawBehind {
        val a = pulse.value
        outer?.let { drawPencil(it, widthPx = 3.dp.toPx(), alpha = a) }
        inner?.let { drawPencil(it, widthPx = 1.6.dp.toPx(), alpha = a) }
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

// ---- shot overlay: muzzle flash, flying bullet, impact ---------------------------------------------

@Composable
private fun ShotOverlay(shot: ShotAnimation?, modifier: Modifier) {
    val flight = remember { Animatable(0f) }
    val burst = remember { Animatable(0f) }
    val stage = shot?.stage
    LaunchedEffect(stage) {
        when (stage) {
            ShotStage.FLYING -> {
                flight.snapTo(0f)
                flight.animateTo(1f, tween(GameTiming.FLIGHT_MS.toInt(), easing = LinearEasing))
            }
            ShotStage.IMPACT -> {
                burst.snapTo(0f)
                burst.animateTo(1f, tween(GameTiming.IMPACT_MS.toInt() - 100, easing = LinearEasing))
            }
            else -> Unit
        }
    }

    Canvas(modifier) {
        if (shot == null || shot.stage == ShotStage.AIMING) return@Canvas
        val layout = BoardLayout(size.width, size.height, HEADER_HEIGHT.toPx(), CELL_PAD_H.toPx(), CELL_PAD_V.toPx())
        val side = layout.side
        val from = layout.point(shot.shooter, shot.shooterIndex, ShotPoints.MUZZLE)
        val hitUnit = if (shot.targetHadGun) ShotPoints.GUN_HIT else ShotPoints.CHEST
        val to = layout.point(shot.target, shot.targetIndex, hitUnit)

        when (shot.stage) {
            ShotStage.FLYING -> {
                val t = flight.value
                val pos = Pt(from.x + (to.x - from.x) * t, from.y + (to.y - from.y) * t)
                drawMuzzleFlash(from, to, side, t)
                drawBullet(from, to, pos, side)
            }
            ShotStage.IMPACT -> {
                val p = burst.value
                if (shot.targetHadGun) drawGunBreak(to, side, p) else drawSplash(to, side, p)
            }
            ShotStage.AIMING -> Unit
        }
    }
}

private fun DrawScope.drawBullet(from: Pt, to: Pt, pos: Pt, side: Float) {
    val dx = to.x - from.x
    val dy = to.y - from.y
    val len = hypot(dx, dy).coerceAtLeast(1f)
    val ux = dx / len
    val uy = dy / len
    val bulletLen = side * 0.14f
    val tail = Offset(pos.x - ux * bulletLen, pos.y - uy * bulletLen)
    // short speed lines behind the bullet, then the bullet itself: a thick red line
    for (k in -1..1 step 2) {
        val ox = -uy * side * 0.035f * k
        val oy = ux * side * 0.035f * k
        drawLine(
            Notebook.Graphite.copy(alpha = 0.45f),
            Offset(tail.x + ox - ux * side * 0.06f, tail.y + oy - uy * side * 0.06f),
            Offset(tail.x + ox - ux * side * 0.16f, tail.y + oy - uy * side * 0.16f),
            strokeWidth = 1.6.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
    drawLine(Notebook.Bullet, tail, Offset(pos.x, pos.y), strokeWidth = 4.5.dp.toPx(), cap = StrokeCap.Round)
}

private fun DrawScope.drawMuzzleFlash(from: Pt, to: Pt, side: Float, t: Float) {
    if (t > 0.3f) return
    val fade = 1f - t / 0.3f
    val base = kotlin.math.atan2(to.y - from.y, to.x - from.x)
    for (i in -3..3) {
        val a = base + i * 0.34f
        val r0 = side * 0.03f
        val r1 = side * (0.10f + 0.04f * (3 - kotlin.math.abs(i)) / 3f) * fade + r0
        drawLine(
            Notebook.Graphite.copy(alpha = fade),
            Offset(from.x + cos(a) * r0, from.y + sin(a) * r0),
            Offset(from.x + cos(a) * r1, from.y + sin(a) * r1),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

/** The bullet strikes the gun: pencil sparks and a crack fly out as the gun is rubbed away. */
private fun DrawScope.drawGunBreak(at: Pt, side: Float, p: Float) {
    val fade = 1f - p
    for (i in 0 until 9) {
        val a = i * (2 * Math.PI / 9).toFloat() + 0.3f
        val r0 = side * (0.03f + 0.05f * p)
        val r1 = r0 + side * (0.05f + 0.09f * p)
        drawLine(
            Notebook.Graphite.copy(alpha = fade),
            Offset(at.x + cos(a) * r0, at.y + sin(a) * r0),
            Offset(at.x + cos(a) * r1, at.y + sin(a) * r1),
            strokeWidth = 2.2.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
    // a little zig-zag crack through the gun
    val zig = listOf(Offset(-0.09f, -0.03f), Offset(-0.03f, 0.03f), Offset(0.03f, -0.03f), Offset(0.09f, 0.03f))
    for (i in 0 until zig.size - 1) {
        drawLine(
            Notebook.Graphite.copy(alpha = fade),
            Offset(at.x + zig[i].x * side, at.y + zig[i].y * side),
            Offset(at.x + zig[i + 1].x * side, at.y + zig[i + 1].y * side),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

/** The bullet hits the chest: a slight red splash that fades down to the small mark left on the drawing. */
private fun DrawScope.drawSplash(at: Pt, side: Float, p: Float) {
    val fade = 1f - 0.75f * p
    for (i in 0 until 10) {
        val a = i * (2 * Math.PI / 10).toFloat() + 0.2f
        val r0 = side * (0.02f + 0.04f * p)
        val r1 = r0 + side * (0.04f + 0.07f * p) * (if (i % 2 == 0) 1f else 0.7f)
        drawLine(
            Notebook.Bullet.copy(alpha = fade),
            Offset(at.x + cos(a) * r0, at.y + sin(a) * r0),
            Offset(at.x + cos(a) * r1, at.y + sin(a) * r1),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
    for (i in 0 until 6) {
        val a = i * (2 * Math.PI / 6).toFloat() + 0.7f
        val r = side * (0.08f + 0.10f * p)
        drawCircle(
            Notebook.Bullet.copy(alpha = fade),
            radius = side * (0.012f + 0.006f * (i % 3)),
            center = Offset(at.x + cos(a) * r, at.y + sin(a) * r),
        )
    }
}
