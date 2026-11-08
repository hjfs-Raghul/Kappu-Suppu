package com.kappusuppu.drawing

import com.kappusuppu.domain.model.Stage

/** Fully drawn (non-animated) characters, used by the How to Play and result screens. */
object StaticInk {
    fun strokes(stage: Stage, dead: Boolean = false, seed: Long = 1L): List<InkStroke> {
        val f = StrokeFactory(seed)
        val specs = buildList {
            if (stage.number >= 1) addAll(f.head())
            if (stage.number >= 2) addAll(f.face())
            if (stage.number >= 3) addAll(f.body())
            if (stage.number >= 4) addAll(f.gun())
            if (stage.number >= 5) addAll(f.bullet())
            if (dead) addAll(f.cross())
        }
        return specs.mapIndexed { i, s ->
            InkStroke(i + 1L, s.group, s.color, s.widthFactor, s.points, StrokePhase.DRAWN)
        }
    }
}
