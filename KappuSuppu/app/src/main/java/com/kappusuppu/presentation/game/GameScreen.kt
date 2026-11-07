package com.kappusuppu.presentation.game

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.kappusuppu.domain.rules.PageError
import com.kappusuppu.drawing.CharKey
import com.kappusuppu.presentation.book.BookSpread
import com.kappusuppu.presentation.components.PencilButton
import com.kappusuppu.presentation.components.notebookPage
import com.kappusuppu.presentation.components.pencilBorder
import com.kappusuppu.presentation.theme.HandFont
import com.kappusuppu.presentation.theme.HandText
import com.kappusuppu.presentation.theme.Notebook

@Composable
fun GameScreen(
    state: SessionState,
    onSubmitPage: (String) -> Unit,
    onClearError: () -> Unit,
    onOpenBook: () -> Unit,
    onConfirmSpread: () -> Unit,
    onContinue: () -> Unit,
    onStrokeDrawn: (CharKey, Long) -> Unit,
    onStrokesErased: (CharKey, Set<Long>) -> Unit,
    onQuit: () -> Unit,
) {
    var confirmQuit by rememberSaveable { mutableStateOf(false) }
    BackHandler { confirmQuit = true }

    // while the result of an action is shown, the marker stays on the player who just acted
    val marker = if (state.step is TurnStep.Feedback) state.actor else state.currentPlayer

    Box(Modifier.fillMaxSize().notebookPage().systemBarsPadding().imePadding()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 8.dp)) {
            NotebookBoard(
                state = state,
                marker = marker,
                onStrokeDrawn = onStrokeDrawn,
                onStrokesErased = onStrokesErased,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
            Column(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                when (val step = state.step) {
                    TurnStep.AwaitingPage -> if (state.mode == BookMode.PHYSICAL) {
                        PhysicalInput(state, onSubmitPage, onClearError)
                    } else {
                        HandText(turnLabel(state), size = 22.sp, bold = true, align = TextAlign.Center)
                        HandText("Open the book to find the even page.", size = 18.sp, align = TextAlign.Center)
                        PencilButton("Open the book", onClick = onOpenBook, modifier = Modifier.fillMaxWidth())
                    }

                    is TurnStep.SpreadRevealed -> {
                        BookSpread(step.spread)
                        HandText(
                            "Only the even page counts: ${step.spread.evenPage}",
                            size = 20.sp,
                            align = TextAlign.Center,
                        )
                        PencilButton("Use page ${step.spread.evenPage}", onClick = onConfirmSpread, modifier = Modifier.fillMaxWidth())
                    }

                    is TurnStep.Feedback -> {
                        HandText(step.message, size = 21.sp, align = TextAlign.Center)
                        PencilButton(
                            text = if (step.next == NextStep.TARGET) "Pick a target" else "Done",
                            onClick = onContinue,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
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

private fun turnLabel(state: SessionState): String =
    "${state.assignment.humanLabel(state.currentPlayer)} (${state.currentPlayer.displayName}), your turn"

@Composable
private fun PhysicalInput(state: SessionState, onSubmit: (String) -> Unit, onClearError: () -> Unit) {
    // a new text box per turn, so the previous page number never lingers
    var text by rememberSaveable(state.game.turnNumber) { mutableStateOf("") }
    val submit = { onSubmit(text) }

    HandText(turnLabel(state), size = 22.sp, bold = true, align = TextAlign.Center)
    HandText("Open a book and type the EVEN page number.", size = 18.sp, align = TextAlign.Center)
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.weight(1f).pencilBorder(17, 2.dp).padding(horizontal = 14.dp, vertical = 12.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicTextField(
                value = text,
                onValueChange = {
                    text = it.take(12)
                    if (state.pageError != null) onClearError()
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
                keyboardActions = KeyboardActions(onDone = { submit() }),
                decorationBox = { inner ->
                    Box {
                        if (text.isEmpty()) HandText("page number", size = 24.sp, color = Notebook.Rule)
                        inner()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        PencilButton("Draw", onClick = { submit() })
    }
    state.pageError?.let { HandText(errorText(it), size = 19.sp, align = TextAlign.Center) }
}

private fun errorText(error: PageError): String = when (error) {
    PageError.EMPTY -> "Type the even page number first."
    PageError.NOT_A_NUMBER -> "That is not a whole number. Digits only, please."
    PageError.NOT_POSITIVE -> "The page must be more than zero."
    PageError.ODD -> "That page is odd. Use the EVEN page (the left one)."
}
