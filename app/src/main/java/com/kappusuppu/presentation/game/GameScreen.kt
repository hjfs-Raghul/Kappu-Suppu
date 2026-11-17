package com.kappusuppu.presentation.game

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kappusuppu.domain.engine.GameEngine
import com.kappusuppu.domain.model.PlayerId
import com.kappusuppu.domain.rules.PageError
import com.kappusuppu.drawing.CharKey
import com.kappusuppu.presentation.book.BookSpread
import com.kappusuppu.presentation.components.PencilButton
import com.kappusuppu.presentation.components.notebookPage
import com.kappusuppu.presentation.components.pencilBorder
import com.kappusuppu.presentation.theme.HandFont
import com.kappusuppu.presentation.theme.HandText
import com.kappusuppu.presentation.theme.Notebook

/**
 * The whole game lives on this one screen: the notebook board, a line saying whose turn it is, and
 * one book button per player. There are no hand-over, confirmation or target screens.
 */
@Composable
fun GameScreen(
    state: SessionState,
    onSubmitPage: (PlayerId, String) -> Unit,
    onClearError: () -> Unit,
    onOpenBook: (PlayerId) -> Unit,
    onTarget: (Int) -> Unit,
    onStrokeDrawn: (CharKey, Long) -> Unit,
    onStrokesErased: (CharKey, Set<Long>) -> Unit,
    onQuit: () -> Unit,
) {
    var confirmQuit by remember { mutableStateOf(false) }
    var entryFor by remember { mutableStateOf<PlayerId?>(null) } // physical book: whose page box is open
    BackHandler { confirmQuit = true }

    // the page box closes by itself as soon as the page was accepted (the step moves on)
    LaunchedEffect(state.step) { if (state.step != TurnStep.AwaitingPage) entryFor = null }

    val targets: Set<Int> =
        if (state.step == TurnStep.AwaitingTarget) GameEngine.validTargets(state.game).toSet() else emptySet()

    Box(Modifier.fillMaxSize().notebookPage().systemBarsPadding().imePadding()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 8.dp)) {
            NotebookBoard(
                state = state,
                targets = targets,
                onTarget = onTarget,
                onStrokeDrawn = onStrokeDrawn,
                onStrokesErased = onStrokesErased,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
            StatusLines(state)
            TurnDock(
                state = state,
                onOpen = { player ->
                    if (state.mode == BookMode.PHYSICAL) entryFor = player else onOpenBook(player)
                },
            )
        }

        (state.step as? TurnStep.SpreadRevealed)?.let { step ->
            Column(
                Modifier
                    .align(Alignment.Center)
                    .padding(20.dp)
                    .background(Notebook.Paper)
                    .pencilBorder(77, 2.dp, double = true)
                    .padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HandText("${state.actor.displayName} opens the book", size = 24.sp, bold = true)
                BookSpread(step.spread)
                HandText("The even page counts: ${step.spread.evenPage}", size = 20.sp, align = TextAlign.Center)
            }
        }

        val entry = entryFor
        if (entry != null && state.canOpenBook(entry)) {
            PageEntry(
                player = entry,
                error = state.pageError,
                onSubmit = { text -> onSubmitPage(entry, text) },
                onClearError = onClearError,
                onCancel = { entryFor = null; onClearError() },
                modifier = Modifier.align(Alignment.Center),
            )
        }

        if (confirmQuit) {
            Box(Modifier.fillMaxSize().notebookPage().padding(32.dp), contentAlignment = Alignment.Center) {
                Column(
                    Modifier.fillMaxWidth().pencilBorder(31, double = true).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    HandText("Leave this game?", size = 30.sp, bold = true)
                    HandText("Nothing is saved. The game will be lost.", size = 20.sp, align = TextAlign.Center)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PencilButton("Stay", onClick = { confirmQuit = false })
                        PencilButton("Leave", onClick = { confirmQuit = false; onQuit() })
                    }
                }
            }
        }
    }
}

/** What just happened, and what is expected next. The active player's name is also circled on the board. */
@Composable
private fun StatusLines(state: SessionState) {
    val actor = state.actor.displayName
    val prompt = when (state.step) {
        TurnStep.AwaitingPage -> "${state.currentPlayer.displayName}'s turn: tap your book"
        is TurnStep.SpreadRevealed -> "$actor is turning the pages..."
        TurnStep.Resolving -> ""
        TurnStep.AwaitingTarget -> "$actor: tap an enemy to shoot!"
        TurnStep.Firing -> "$actor takes aim..."
        TurnStep.GameOver -> ""
    }
    Column(
        Modifier.fillMaxWidth().heightIn(min = 78.dp).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (state.lastMessage.isNotBlank()) HandText(state.lastMessage, size = 16.sp, align = TextAlign.Center)
        if (prompt.isNotBlank()) HandText(prompt, size = 24.sp, bold = true, align = TextAlign.Center)
    }
}

/** Physical book: type the even page, press Draw. The page is applied at once; no second confirmation. */
@Composable
private fun PageEntry(
    player: PlayerId,
    error: PageError?,
    onSubmit: (String) -> Unit,
    onClearError: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by remember { mutableStateOf("") }
    Column(
        modifier
            .padding(20.dp)
            .background(Notebook.Paper)
            .pencilBorder(41, 2.dp, double = true)
            .padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        HandText("${player.displayName}, open your book", size = 24.sp, bold = true, align = TextAlign.Center)
        HandText("Type the EVEN page number (the left page).", size = 18.sp, align = TextAlign.Center)
        Box(Modifier.fillMaxWidth().pencilBorder(17, 2.dp).padding(horizontal = 14.dp, vertical = 12.dp)) {
            BasicTextField(
                value = text,
                onValueChange = {
                    text = it.take(12)
                    if (error != null) onClearError()
                },
                singleLine = true,
                textStyle = TextStyle(
                    color = Notebook.Graphite,
                    fontSize = 28.sp,
                    fontFamily = HandFont,
                    fontWeight = FontWeight.Bold,
                ),
                cursorBrush = SolidColor(Notebook.Graphite),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSubmit(text) }),
                decorationBox = { inner ->
                    Box {
                        if (text.isEmpty()) HandText("page number", size = 24.sp, color = Notebook.Rule)
                        inner()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (error != null) HandText(errorText(error), size = 19.sp, align = TextAlign.Center)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PencilButton("Cancel", onClick = onCancel)
            PencilButton("Draw", onClick = { onSubmit(text) })
        }
    }
}

private fun errorText(error: PageError): String = when (error) {
    PageError.EMPTY -> "Type the even page number first."
    PageError.NOT_A_NUMBER -> "That is not a whole number. Digits only, please."
    PageError.NOT_POSITIVE -> "The page must be more than zero."
    PageError.ODD -> "That page is odd. Use the EVEN page (the left one)."
}
