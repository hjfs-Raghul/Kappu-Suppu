package com.kappusuppu.presentation.roles

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import com.kappusuppu.domain.model.PlayerId
import com.kappusuppu.presentation.components.PencilButton
import com.kappusuppu.presentation.components.notebookPage
import com.kappusuppu.presentation.theme.HandText

@Composable
fun RoleScreen(
    selected: PlayerId?,
    onSelect: (PlayerId) -> Unit,
    onConfirm: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    Column(
        Modifier.fillMaxSize().notebookPage().systemBarsPadding().padding(horizontal = 28.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        HandText("Who is who?", size = 40.sp, bold = true)
        HandText(
            "Two players share one phone. Player 1, choose your name. Player 2 gets the other one.",
            size = 22.sp,
            align = TextAlign.Center,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            for (id in PlayerId.entries) {
                PencilButton(
                    text = "Player 1 is ${id.displayName}",
                    onClick = { onSelect(id) },
                    selected = selected == id,
                    modifier = Modifier.weight(1f),
                    textSize = 24.sp,
                )
            }
        }
        if (selected == null) {
            HandText("Tap one to choose.", size = 22.sp)
        } else {
            HandText("Player 1  =  ${selected.displayName}", size = 30.sp, bold = true)
            HandText("Player 2  =  ${selected.opponent.displayName}", size = 30.sp, bold = true)
            HandText("Kappu always takes the first turn.", size = 20.sp)
        }
        Column(Modifier.weight(1f)) {}
        PencilButton("Confirm roles", onClick = onConfirm, enabled = selected != null, modifier = Modifier.fillMaxWidth(), textSize = 28.sp)
        PencilButton("Back", onClick = onBack, modifier = Modifier.fillMaxWidth())
    }
}
