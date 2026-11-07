package com.kappusuppu.presentation.theme

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/** Flat notebook palette: paper, graphite pencil, a faint ruling grey. Red is reserved for the bullet. */
object Notebook {
    val Paper = Color(0xFFFBFAF4)
    val Graphite = Color(0xFF2B2B2B)
    val Rule = Color(0xFFDAD7CC)
    val Bullet = Color(0xFFD32F2F)
}

/** The platform's built-in handwriting-style face, so no font file has to be bundled. */
val HandFont: FontFamily = FontFamily.Cursive

@Composable
fun HandText(
    text: String,
    modifier: Modifier = Modifier,
    size: TextUnit = 20.sp,
    color: Color = Notebook.Graphite,
    align: TextAlign = TextAlign.Start,
    bold: Boolean = false,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = TextStyle(
            color = color,
            fontSize = size,
            fontFamily = HandFont,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            textAlign = align,
            lineHeight = size * 1.2f,
        ),
    )
}
