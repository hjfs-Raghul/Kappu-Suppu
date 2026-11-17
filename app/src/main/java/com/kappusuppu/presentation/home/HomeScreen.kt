package com.kappusuppu.presentation.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kappusuppu.domain.model.Stage
import com.kappusuppu.drawing.Pt
import com.kappusuppu.drawing.Wobble
import com.kappusuppu.presentation.components.PencilButton
import com.kappusuppu.presentation.components.SoundToggle
import com.kappusuppu.presentation.components.StaticCharacter
import com.kappusuppu.presentation.components.drawPencil
import com.kappusuppu.presentation.components.notebookPage
import com.kappusuppu.presentation.theme.HandText
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun HomeScreen(
    soundEnabled: Boolean,
    onToggleSound: () -> Unit,
    onNewGame: () -> Unit,
    onHowToPlay: () -> Unit,
) {
    Box(Modifier.fillMaxSize().notebookPage().systemBarsPadding()) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            HandText("KAPPU", size = 64.sp, bold = true, align = TextAlign.Center)
            Underscribble(Modifier.fillMaxWidth(0.7f).height(14.dp))
            HandText("SUPPU", size = 64.sp, bold = true, align = TextAlign.Center)
            Underscribble(Modifier.fillMaxWidth(0.7f).height(14.dp), seed = 9)

            // small pencil doodles: a circle, a stick person, an armed person with a red bullet, and stars
            Row(
                Modifier.fillMaxWidth().padding(vertical = 28.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StaticCharacter(Stage.CIRCLE, Modifier.size(70.dp), seed = 3)
                StaticCharacter(Stage.BODY, Modifier.size(82.dp), seed = 4)
                StaticCharacter(Stage.LOADED, Modifier.size(94.dp), seed = 5)
                Star(Modifier.size(28.dp))
            }

            PencilButton("New Game", onClick = onNewGame, modifier = Modifier.fillMaxWidth(0.8f), textSize = 30.sp)
            Box(Modifier.height(18.dp))
            PencilButton("How to Play", onClick = onHowToPlay, modifier = Modifier.fillMaxWidth(0.8f), textSize = 30.sp)
            Box(Modifier.height(24.dp))
            SoundToggle(soundEnabled, onToggleSound)
        }
    }
}

@Composable
private fun Underscribble(modifier: Modifier, seed: Long = 1) {
    Canvas(modifier) {
        val rnd = Random(seed)
        drawPencil(Wobble.line(Pt(0f, size.height * 0.4f), Pt(size.width, size.height * 0.3f), rnd, 2.2f, 18f), widthPx = 3.dp.toPx())
        drawPencil(Wobble.line(Pt(size.width * 0.1f, size.height * 0.85f), Pt(size.width * 0.9f, size.height * 0.75f), rnd, 2.2f, 18f), widthPx = 2.dp.toPx())
    }
}

@Composable
private fun Star(modifier: Modifier) {
    Canvas(modifier) {
        val rnd = Random(77)
        val c = Pt(size.width / 2f, size.height / 2f)
        val r = size.width * 0.46f
        val pts = (0 until 5).map { i ->
            val a = -PI / 2 + i * 4 * PI / 5
            Pt(c.x + (cos(a) * r).toFloat(), c.y + (sin(a) * r).toFloat())
        }
        drawPencil(Wobble.polyline(pts, rnd, closed = true, amp = 1f, step = 6f), widthPx = 2.dp.toPx())
    }
}
