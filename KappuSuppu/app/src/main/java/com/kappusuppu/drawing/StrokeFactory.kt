package com.kappusuppu.drawing

import com.kappusuppu.domain.model.Expression
import kotlin.random.Random

enum class InkColor { GRAPHITE, RED }

/** Which part of the character a stroke belongs to. */
enum class StrokeGroup { HEAD, FACE, BODY, GUN, BULLET, CROSS }

/** Geometry of one stroke in the 0..1 character box (y grows downwards). */
data class StrokeSpec(
    val group: StrokeGroup,
    val color: InkColor,
    val widthFactor: Float,
    val points: List<Pt>,
)

/**
 * Builds every stroke of the notebook characters. A new factory (new seed) is used each time
 * something is drawn, so two guns drawn at different times are never pixel-identical.
 */
class StrokeFactory(seed: Long) {
    private val rnd = Random(seed)

    private fun spec(group: StrokeGroup, points: List<Pt>, color: InkColor = InkColor.GRAPHITE, width: Float = 1f) =
        StrokeSpec(group, color, width, points)

    private fun seg(group: StrokeGroup, ax: Float, ay: Float, bx: Float, by: Float) =
        spec(group, Wobble.line(Pt(ax, ay), Pt(bx, by), rnd))

    /** Stage 1: plain empty circle. Nothing else. */
    fun head(): List<StrokeSpec> =
        listOf(spec(StrokeGroup.HEAD, Wobble.ellipse(HEAD_X, HEAD_Y, HEAD_R, HEAD_R, rnd)))

    /** Stage 2: two eyes and a mouth (neutral). */
    fun face(): List<StrokeSpec> = listOf(
        spec(StrokeGroup.FACE, Wobble.ellipse(HEAD_X - 0.065f, HEAD_Y - 0.02f, 0.017f, 0.02f, rnd, 0.2)),
        spec(StrokeGroup.FACE, Wobble.ellipse(HEAD_X + 0.065f, HEAD_Y - 0.02f, 0.017f, 0.02f, rnd, 0.2)),
        spec(StrokeGroup.FACE, Wobble.line(Pt(HEAD_X - 0.06f, HEAD_Y + 0.07f), Pt(HEAD_X + 0.06f, HEAD_Y + 0.07f), rnd, 0.004f, 0.02f)),
    )

    /** Stage 3: stick body, two arms, two legs. */
    fun body(): List<StrokeSpec> = listOf(
        seg(StrokeGroup.BODY, HEAD_X, HEAD_Y + HEAD_R, HEAD_X, 0.68f),
        seg(StrokeGroup.BODY, HEAD_X, 0.50f, 0.20f, 0.60f),
        seg(StrokeGroup.BODY, HEAD_X, 0.50f, 0.54f, 0.54f),
        seg(StrokeGroup.BODY, HEAD_X, 0.68f, 0.24f, 0.90f),
        seg(StrokeGroup.BODY, HEAD_X, 0.68f, 0.48f, 0.90f),
    )

    /** Stage 4: gun beside the character, held by the right hand. One closed pencil outline. */
    fun gun(): List<StrokeSpec> {
        val outline = listOf(
            Pt(0.54f, 0.47f), Pt(0.92f, 0.47f), Pt(0.92f, 0.60f), Pt(0.72f, 0.60f),
            Pt(0.68f, 0.74f), Pt(0.58f, 0.74f), Pt(0.61f, 0.60f), Pt(0.54f, 0.60f),
        )
        return listOf(spec(StrokeGroup.GUN, Wobble.polyline(outline, rnd, closed = true, amp = 0.005f, step = 0.03f)))
    }

    /** Stage 5: red bullet inside the gun barrel. The only coloured strokes in the game. */
    fun bullet(): List<StrokeSpec> = listOf(
        spec(StrokeGroup.BULLET, Wobble.line(Pt(0.60f, 0.535f), Pt(0.78f, 0.535f), rnd, 0.002f, 0.03f), InkColor.RED, 2.4f),
        spec(
            StrokeGroup.BULLET,
            Wobble.polyline(listOf(Pt(0.78f, 0.505f), Pt(0.84f, 0.535f), Pt(0.78f, 0.565f)), rnd, closed = false, amp = 0.002f, step = 0.03f),
            InkColor.RED,
            1.6f,
        ),
    )

    /** Dead marker: a big pencil cross over the whole cell. Drawn on top, the character stays visible. */
    fun cross(): List<StrokeSpec> = listOf(
        spec(StrokeGroup.CROSS, Wobble.line(Pt(0.06f, 0.05f), Pt(0.94f, 0.95f), rnd, 0.008f, 0.03f), width = 1.4f),
        spec(StrokeGroup.CROSS, Wobble.line(Pt(0.94f, 0.05f), Pt(0.06f, 0.95f), rnd, 0.008f, 0.03f), width = 1.4f),
    )

    /**
     * Temporary expression overlay for the face. These are NOT stored in the character's stroke data;
     * the renderer shows them instead of the neutral eyes/mouth and then goes back to the stored face.
     */
    fun expressionFace(expression: Expression): List<StrokeSpec> = when (expression) {
        Expression.NORMAL -> emptyList()
        Expression.CUNNING -> listOf(
            // narrowed, slanted eyes + brows
            spec(StrokeGroup.FACE, Wobble.line(Pt(HEAD_X - 0.105f, HEAD_Y - 0.055f), Pt(HEAD_X - 0.03f, HEAD_Y - 0.015f), rnd, 0.002f, 0.02f)),
            spec(StrokeGroup.FACE, Wobble.line(Pt(HEAD_X + 0.105f, HEAD_Y - 0.055f), Pt(HEAD_X + 0.03f, HEAD_Y - 0.015f), rnd, 0.002f, 0.02f)),
            spec(StrokeGroup.FACE, Wobble.ellipse(HEAD_X - 0.065f, HEAD_Y - 0.005f, 0.018f, 0.008f, rnd, 0.2)),
            spec(StrokeGroup.FACE, Wobble.ellipse(HEAD_X + 0.065f, HEAD_Y - 0.005f, 0.018f, 0.008f, rnd, 0.2)),
            // lop-sided mischievous smile
            spec(
                StrokeGroup.FACE,
                Wobble.curve(Pt(HEAD_X - 0.075f, HEAD_Y + 0.065f), Pt(HEAD_X, HEAD_Y + 0.11f), Pt(HEAD_X + 0.085f, HEAD_Y + 0.045f), rnd),
            ),
        )

        Expression.SHOCKED -> listOf(
            // wide round eyes with pupils + surprised "O" mouth
            spec(StrokeGroup.FACE, Wobble.ellipse(HEAD_X - 0.065f, HEAD_Y - 0.03f, 0.036f, 0.04f, rnd, 0.15)),
            spec(StrokeGroup.FACE, Wobble.ellipse(HEAD_X + 0.065f, HEAD_Y - 0.03f, 0.036f, 0.04f, rnd, 0.15)),
            spec(StrokeGroup.FACE, Wobble.ellipse(HEAD_X - 0.065f, HEAD_Y - 0.03f, 0.007f, 0.007f, rnd, 0.3)),
            spec(StrokeGroup.FACE, Wobble.ellipse(HEAD_X + 0.065f, HEAD_Y - 0.03f, 0.007f, 0.007f, rnd, 0.3)),
            spec(StrokeGroup.FACE, Wobble.ellipse(HEAD_X, HEAD_Y + 0.085f, 0.026f, 0.036f, rnd, 0.15)),
        )
    }

    companion object {
        const val HEAD_X = 0.36f
        const val HEAD_Y = 0.25f
        const val HEAD_R = 0.16f
    }
}
