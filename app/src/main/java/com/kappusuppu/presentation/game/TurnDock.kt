package com.kappusuppu.presentation.game

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kappusuppu.domain.model.PlayerId
import com.kappusuppu.presentation.components.BookIcon
import com.kappusuppu.presentation.components.PencilShapes
import com.kappusuppu.presentation.components.drawPencil
import com.kappusuppu.presentation.theme.HandText
import kotlin.random.Random

/**
 * One book button per player, under that player's column, like a dice in a board game. Only the
 * player whose turn it is can use theirs; the other one is greyed out, so nobody can play the
 * opposition's turn by mistake.
 */
@Composable
fun TurnDock(state: SessionState, onOpen: (PlayerId) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        for (id in PlayerId.entries) {
            DockCell(id, enabled = state.canOpenBook(id), onClick = { onOpen(id) }, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun DockCell(id: PlayerId, enabled: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Column(
        modifier.alpha(if (enabled) 1f else 0.28f),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(76.dp)
                .drawWithCache {
                    // an obvious pencil circle marks the book that may be used right now
                    val ring = if (enabled) PencilShapes.loop(size.width / 2f, size.height / 2f, size.width * 0.47f, size.height * 0.47f, Random(id.ordinal + 3L)) else null
                    onDrawBehind { ring?.let { drawPencil(it, widthPx = 2.6.dp.toPx()) } }
                }
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            BookIcon(Modifier.size(52.dp), seed = id.ordinal + 1L)
        }
        HandText(
            text = if (enabled) "${id.displayName}: open page" else id.displayName,
            size = 16.sp,
            bold = enabled,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}
