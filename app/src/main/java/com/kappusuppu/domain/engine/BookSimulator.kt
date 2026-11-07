package com.kappusuppu.domain.engine

import kotlin.random.Random

const val SIMULATED_BOOK_PAGES = 200

/** An open double page: even page on the left, following odd page on the right. */
data class Spread(val left: Int, val right: Int) {
    init {
        require(left in 2..SIMULATED_BOOK_PAGES && left % 2 == 0) { "Left page must be an even page 2..200" }
        require(right == left + 1) { "Right page must follow the left page" }
    }

    /** Only the even page is used by the game. */
    val evenPage: Int get() = left
}

class BookSimulator(private val random: Random = Random.Default) {
    /** Uniformly picks an even page from 2 through 200 inclusive (100 possibilities). */
    fun open(): Spread {
        val pairIndex = random.nextInt(1, SIMULATED_BOOK_PAGES / 2 + 1) // 1..100 inclusive
        val even = pairIndex * 2
        return Spread(even, even + 1)
    }
}
