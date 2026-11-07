package com.kappusuppu.domain

import com.kappusuppu.domain.engine.BookSimulator
import com.kappusuppu.domain.engine.GameEngine
import com.kappusuppu.domain.engine.SIMULATED_BOOK_PAGES
import com.kappusuppu.domain.engine.Spread
import com.kappusuppu.domain.model.GameCharacter
import com.kappusuppu.domain.model.PlayerAssignment
import com.kappusuppu.domain.model.PlayerId
import com.kappusuppu.domain.model.Stage
import com.kappusuppu.domain.rules.CharacterMapping
import com.kappusuppu.domain.rules.PageError
import com.kappusuppu.domain.rules.PageParse
import com.kappusuppu.domain.rules.PageRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.random.Random

class PlayerSetupTest {
    @Test fun exactlyTwoPlayers() {
        assertEquals(2, PlayerId.entries.size)
        assertEquals(setOf("Kappu", "Suppu"), PlayerId.entries.map { it.displayName }.toSet())
    }

    @Test fun newGameHasKappuAndSuppuWithFiveCharactersEach() {
        val s = GameEngine.newGame()
        assertEquals(PlayerId.KAPPU, s.kappu.id)
        assertEquals(PlayerId.SUPPU, s.suppu.id)
        assertEquals(5, s.kappu.characters.size)
        assertEquals(5, s.suppu.characters.size)
        assertEquals(PlayerId.KAPPU, s.currentPlayer)
    }

    @Test fun everyCharacterStartsAsAliveStageOneCircle() {
        val s = GameEngine.newGame()
        (s.kappu.characters + s.suppu.characters).forEach {
            assertEquals(Stage.CIRCLE, it.stage)
            assertTrue(it.alive)
            assertFalse(it.hasGun)
            assertFalse(it.bulletLoaded)
        }
    }

    @Test fun assignmentGivesExactlyOneHumanPerIdentity() {
        for (choice in PlayerId.entries) {
            val a = PlayerAssignment(choice)
            assertEquals(choice, a.player1)
            assertEquals(choice.opponent, a.player2)
            assertEquals(PlayerId.entries.toSet(), a.identities)
            assertEquals("Player 1", a.humanLabel(a.player1))
            assertEquals("Player 2", a.humanLabel(a.player2))
        }
    }

    @Test fun impossibleCharacterCombinationsAreRejected() {
        try { GameCharacter(0, Stage.LOADED, alive = false); fail() } catch (_: IllegalArgumentException) {}
        try { GameCharacter(0, Stage.GUN, alive = false); fail() } catch (_: IllegalArgumentException) {}
        try { GameCharacter(7); fail() } catch (_: IllegalArgumentException) {}
        assertFalse(GameCharacter(0, Stage.BODY).hasGun)
        assertTrue(GameCharacter(0, Stage.GUN).hasGun)
        assertFalse(GameCharacter(0, Stage.GUN).bulletLoaded)
        assertTrue(GameCharacter(0, Stage.LOADED).bulletLoaded)
    }
}

class PhysicalBookTest {
    private fun valid(text: String) = (PageRules.validate(text) as PageParse.Valid).page
    private fun error(text: String) = (PageRules.validate(text) as PageParse.Invalid).error

    @Test fun acceptsPositiveEvenNumbers() {
        assertEquals(2, valid("2"))
        assertEquals(126, valid("126"))
        assertEquals(200, valid(" 200 "))
        assertEquals(1000, valid("1000"))
    }

    @Test fun rejectsOddNumbers() {
        assertEquals(PageError.ODD, error("7"))
        assertEquals(PageError.ODD, error("127"))
    }

    @Test fun rejectsZero() = assertEquals(PageError.NOT_POSITIVE, error("0"))

    @Test fun rejectsNegativeValues() {
        assertEquals(PageError.NOT_POSITIVE, error("-4"))
        assertEquals(PageError.NOT_POSITIVE, error("-126"))
    }

    @Test fun rejectsInvalidText() {
        assertEquals(PageError.EMPTY, error(""))
        assertEquals(PageError.EMPTY, error("   "))
        listOf("abc", "12a", "4.0", "1e2", "--4", "+4", "4 6", "99999999999999").forEach {
            assertEquals("input '$it'", PageError.NOT_A_NUMBER, error(it))
        }
    }

    @Test fun extractsLastDigit() {
        assertEquals(6, CharacterMapping.lastDigit(126))
        assertEquals(0, CharacterMapping.lastDigit(200))
        assertEquals(2, CharacterMapping.lastDigit(2))
    }

    @Test fun mapsLastDigitToCharacter() {
        assertEquals(0, CharacterMapping.indexForPage(10))
        assertEquals(1, CharacterMapping.indexForPage(112))
        assertEquals(2, CharacterMapping.indexForPage(34))
        assertEquals(3, CharacterMapping.indexForPage(126)) // Character 4
        assertEquals(4, CharacterMapping.indexForPage(198))
        assertEquals(0, CharacterMapping.indexForPage(200))
        listOf(0, 2, 4, 6, 8).forEachIndexed { i, d -> assertEquals(i, CharacterMapping.indexForDigit(d)) }
    }

    @Test fun mappingRejectsInvalidPages() {
        try { CharacterMapping.indexForPage(7); fail() } catch (_: IllegalArgumentException) {}
        try { CharacterMapping.indexForPage(0); fail() } catch (_: IllegalArgumentException) {}
    }
}

class BookSimulatorTest {
    @Test fun onlyEvenPagesWithinRange() {
        val sim = BookSimulator(Random(12345))
        repeat(20_000) {
            val spread = sim.open()
            assertTrue(spread.left % 2 == 0)
            assertTrue(spread.left in 2..SIMULATED_BOOK_PAGES)
        }
    }

    @Test fun everyEvenPageFromTwoToTwoHundredCanAppear() {
        val sim = BookSimulator(Random(7))
        val seen = HashSet<Int>()
        repeat(50_000) { seen += sim.open().left }
        assertEquals((2..200 step 2).toSet(), seen)
        assertEquals(2, seen.min())
        assertEquals(200, seen.max())
    }

    @Test fun asksRandomForExactlyOneToHundredInclusive() {
        val calls = mutableListOf<Pair<Int, Int>>()
        val recording = object : Random() {
            override fun nextBits(bitCount: Int) = 0
            override fun nextInt(from: Int, until: Int): Int { calls += from to until; return from }
        }
        assertEquals(2, BookSimulator(recording).open().left)
        assertEquals(listOf(1 to 101), calls)

        val max = object : Random() {
            override fun nextBits(bitCount: Int) = 0
            override fun nextInt(from: Int, until: Int) = until - 1
        }
        assertEquals(200, BookSimulator(max).open().left)
    }

    @Test fun showsFollowingOddPageOnTheRight() {
        val sim = BookSimulator(Random(99))
        repeat(500) { val s = sim.open(); assertEquals(s.left + 1, s.right); assertTrue(s.right % 2 == 1) }
        val s = Spread(126, 127)
        assertEquals(126, s.evenPage)
        assertEquals(3, CharacterMapping.indexForPage(s.evenPage))
    }

    @Test fun spreadRejectsBadPages() {
        try { Spread(127, 128); fail() } catch (_: IllegalArgumentException) {}
        try { Spread(126, 128); fail() } catch (_: IllegalArgumentException) {}
        try { Spread(202, 203); fail() } catch (_: IllegalArgumentException) {}
        try { Spread(0, 1); fail() } catch (_: IllegalArgumentException) {}
    }

    @Test fun simulatedEvenPageAlwaysValidForEngine() {
        val sim = BookSimulator(Random(1))
        repeat(200) { assertTrue(PageRules.isValid(sim.open().evenPage)) }
        assertNull(GameEngine.previewAction(GameEngine.newGame(), 7))
    }
}
