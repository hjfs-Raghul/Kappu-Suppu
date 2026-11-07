package com.kappusuppu.presentation.book

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import com.kappusuppu.presentation.components.PencilButton
import com.kappusuppu.presentation.components.notebookPage
import com.kappusuppu.presentation.components.pencilBorder
import com.kappusuppu.presentation.game.BookMode
import com.kappusuppu.presentation.theme.HandText

@Composable
fun BookModeScreen(onPick: (BookMode) -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    Column(
        Modifier
            .fillMaxSize()
            .notebookPage()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        HandText("Pick a book", size = 40.sp, bold = true)

        Column(
            Modifier.fillMaxWidth().pencilBorder(11).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            HandText("The even-page rule", size = 26.sp, bold = true)
            HandText("Open a book and look at the EVEN page (the left one). Only its last digit counts:", size = 20.sp)
            HandText("0 = Character 1     2 = Character 2     4 = Character 3", size = 20.sp)
            HandText("6 = Character 4     8 = Character 5", size = 20.sp)
            HandText("Example: page 126 ends in 6, so Character 4 acts.", size = 20.sp)
        }

        PencilButton("Physical Book", onClick = { onPick(BookMode.PHYSICAL) }, modifier = Modifier.fillMaxWidth(), textSize = 28.sp)
        HandText(
            "Open a real book yourself and type the even page number. No camera is used.",
            size = 19.sp,
            align = TextAlign.Center,
        )
        PencilButton("Book Simulator", onClick = { onPick(BookMode.SIMULATOR) }, modifier = Modifier.fillMaxWidth(), textSize = 28.sp)
        HandText(
            "The phone opens a pretend 200-page book. Even pages 2 to 200, picked at random.",
            size = 19.sp,
            align = TextAlign.Center,
        )
        PencilButton("Back", onClick = onBack, modifier = Modifier.fillMaxWidth())
    }
}
