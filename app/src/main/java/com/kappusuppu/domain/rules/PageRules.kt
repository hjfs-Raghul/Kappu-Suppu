package com.kappusuppu.domain.rules

import com.kappusuppu.domain.model.CHARACTERS_PER_PLAYER

enum class PageError { EMPTY, NOT_A_NUMBER, NOT_POSITIVE, ODD }

sealed interface PageParse {
    data class Valid(val page: Int) : PageParse
    data class Invalid(val error: PageError) : PageParse
}

object PageRules {
    private val integerPattern = Regex("^-?\\d+$")

    /** Validates text typed by the player for the physical-book mode. */
    fun validate(text: String): PageParse {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return PageParse.Invalid(PageError.EMPTY)
        if (!integerPattern.matches(trimmed)) return PageParse.Invalid(PageError.NOT_A_NUMBER)
        val value = trimmed.toIntOrNull() ?: return PageParse.Invalid(PageError.NOT_A_NUMBER)
        return validate(value)
    }

    fun validate(page: Int): PageParse = when {
        page <= 0 -> PageParse.Invalid(PageError.NOT_POSITIVE)
        page % 2 != 0 -> PageParse.Invalid(PageError.ODD)
        else -> PageParse.Valid(page)
    }

    fun isValid(page: Int): Boolean = validate(page) is PageParse.Valid
}

object CharacterMapping {
    /** Last digit 0,2,4,6,8 -> character index 0..4 (Character 1..5). */
    fun lastDigit(evenPage: Int): Int = evenPage % 10

    fun indexForPage(evenPage: Int): Int {
        require(PageRules.isValid(evenPage)) { "Page must be a positive even number" }
        return lastDigit(evenPage) / 2
    }

    fun indexForDigit(digit: Int): Int {
        require(digit in 0..8 && digit % 2 == 0) { "Digit must be 0, 2, 4, 6 or 8" }
        return digit / 2
    }

    fun digitForIndex(index: Int): Int {
        require(index in 0 until CHARACTERS_PER_PLAYER)
        return index * 2
    }
}
