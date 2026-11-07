package com.kappusuppu.presentation.game

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kappusuppu.presentation.components.PencilButton
import com.kappusuppu.presentation.components.notebookPage
import com.kappusuppu.presentation.theme.HandText

/** Shows ONLY whose turn it is. No board, no result of the last action, nothing about the next move. */
@Composable
fun HandoffScreen(state: SessionState, onContinue: () -> Unit) {
    BackHandler { /* the phone is being passed around: ignore accidental back presses */ }
    val player = state.currentPlayer
    Column(
        Modifier.fillMaxSize().notebookPage().systemBarsPadding().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        HandText("Pass the phone to", size = 30.sp, align = TextAlign.Center)
        HandText(state.assignment.humanLabel(player), size = 56.sp, bold = true, align = TextAlign.Center)
        HandText("who plays ${player.displayName.uppercase()}", size = 34.sp, align = TextAlign.Center)
        Column(Modifier.padding(vertical = 18.dp)) {
            HandText("Tap Continue only when you are holding the phone.", size = 20.sp, align = TextAlign.Center)
        }
        PencilButton("Continue", onClick = onContinue, modifier = Modifier.fillMaxWidth(0.8f), textSize = 30.sp)
    }
}
