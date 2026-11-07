package com.kappusuppu.presentation.howtoplay

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
import com.kappusuppu.domain.model.Expression
import com.kappusuppu.domain.model.Stage
import com.kappusuppu.presentation.components.PencilButton
import com.kappusuppu.presentation.components.StaticCharacter
import com.kappusuppu.presentation.components.notebookPage
import com.kappusuppu.presentation.components.pencilBorder
import com.kappusuppu.presentation.theme.HandText

@Composable
fun HowToPlayScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    Column(
        Modifier
            .fillMaxSize()
            .notebookPage()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        HandText("How to Play", size = 42.sp, bold = true, modifier = Modifier.align(Alignment.CenterHorizontally))

        Section("1. Choose a page") {
            Body("Two players, one phone: Kappu and Suppu, five characters each. On your turn open a book (a real one, or the built-in simulator) and look at the EVEN page, the left one.")
            Body("Example: you open pages 126 | 127. Use 126.")
        }

        Section("2. Which character?") {
            Body("Only the last digit of the even page matters:")
            Body("0 = Character 1,  2 = Character 2,  4 = Character 3,  6 = Character 4,  8 = Character 5.")
            Body("126 ends in 6, so Character 4 acts. If that character is dead, the turn is simply lost.")
        }

        Section("3. Characters grow") {
            Body("Every matching digit adds to the drawing, on top of what is already there:")
            StageRow(
                listOf(
                    Stage.CIRCLE to "1: circle",
                    Stage.FACE to "2: face",
                    Stage.BODY to "3: body",
                    Stage.GUN to "4: gun",
                    Stage.LOADED to "5: loaded",
                ),
            )
            Body("The red bullet means the gun is loaded.")
        }

        Section("4. Shooting") {
            Body("When a character with a LOADED gun (stage 5) gets its digit, you may shoot. Choose any living enemy character and confirm. Your bullet is used up, but your character and its gun stay.")
            StageRow(listOf(Stage.LOADED to "loaded", Stage.GUN to "after the shot"))
        }

        Section("5. Gun defence") {
            Body("A target WITHOUT a gun (stages 1, 2 or 3) is out for good and crossed off:")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Example(Stage.BODY, dead = true, caption = "no gun: dead")
            }
            Body("A target WITH a gun (loaded or not) only loses the gun and its bullet. It survives, goes back to stage 3 and looks shocked:")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Example(Stage.LOADED, caption = "before")
                Example(Stage.BODY, caption = "after", expression = Expression.SHOCKED)
            }
        }

        Section("6. Reloading") {
            Body("A character with an empty gun (stage 4) that gets its digit loads a bullet (stage 5). Loading uses the turn: no extra turn and no second try. The next matching turn can shoot. This can repeat forever.")
            Body("A character that lost its gun has to rebuild: first the gun (stage 4), then the bullet (stage 5). It cannot skip a step.")
        }

        Section("7. Winning") {
            Body("Players take turns, Kappu then Suppu. When a player has no living characters left, the other player wins.")
        }

        PencilButton("Back", onClick = onBack, modifier = Modifier.fillMaxWidth(), textSize = 26.sp)
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().pencilBorder(title.hashCode().toLong()).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HandText(title, size = 26.sp, bold = true)
        content()
    }
}

@Composable
private fun Body(text: String) = HandText(text, size = 19.sp)

@Composable
private fun StageRow(items: List<Pair<Stage, String>>) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for ((i, item) in items.withIndex()) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                StaticCharacter(item.first, Modifier.fillMaxWidth().aspectRatio(0.85f), seed = 10L + i)
                HandText(item.second, size = 14.sp, align = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun Example(stage: Stage, caption: String, dead: Boolean = false, expression: Expression = Expression.NORMAL) {
    Column(Modifier.padding(horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        StaticCharacter(stage, Modifier.padding(4.dp).aspectRatio(1f).fillMaxWidth(0.4f), dead = dead, expression = expression, seed = 40L)
        HandText(caption, size = 16.sp)
    }
}
