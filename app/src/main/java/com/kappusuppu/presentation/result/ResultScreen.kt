package com.kappusuppu.presentation.result

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kappusuppu.domain.model.CHARACTERS_PER_PLAYER
import com.kappusuppu.domain.model.PlayerId
import com.kappusuppu.drawing.CharKey
import com.kappusuppu.presentation.components.CharacterCanvas
import com.kappusuppu.presentation.components.PencilButton
import com.kappusuppu.presentation.components.notebookPage
import com.kappusuppu.presentation.components.pencilBorder
import com.kappusuppu.presentation.game.SessionState
import com.kappusuppu.presentation.theme.HandText

@Composable
fun ResultScreen(state: SessionState, onPlayAgain: () -> Unit, onHome: () -> Unit) {
    BackHandler(onBack = onHome)
    val winner = state.winner ?: return
    Column(
        Modifier
            .fillMaxSize()
            .notebookPage()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HandText("${winner.displayName.uppercase()} WINS!", size = 46.sp, bold = true, align = TextAlign.Center)
        HandText(
            "${state.assignment.humanLabel(winner)} outlasted ${winner.opponent.displayName}.",
            size = 22.sp,
            align = TextAlign.Center,
        )
        for (id in PlayerId.entries) {
            val player = state.game.player(id)
            Column(
                Modifier.fillMaxWidth().pencilBorder(id.ordinal + 60L).padding(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                HandText(
                    "${id.displayName}: ${player.aliveCount} of $CHARACTERS_PER_PLAYER characters left",
                    size = 22.sp,
                    bold = true,
                )
                Row(Modifier.fillMaxWidth()) {
                    for (c in player.characters) {
                        val key = CharKey(id, c.index)
                        CharacterCanvas(
                            key = key,
                            strokes = state.ink.of(key),
                            expression = c.expression,
                            modifier = Modifier.weight(1f).aspectRatio(0.8f),
                        )
                    }
                }
            }
        }
        PencilButton("Play Again", onClick = onPlayAgain, modifier = Modifier.fillMaxWidth(), textSize = 28.sp)
        PencilButton("Home", onClick = onHome, modifier = Modifier.fillMaxWidth(), textSize = 28.sp)
    }
}
