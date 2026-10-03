package com.lifetrack.app

import androidx.compose.ui.graphics.Color
import com.lifetrack.app.ui.components.Slice
import com.lifetrack.app.ui.components.ordinal
import com.lifetrack.app.ui.components.percentOf
import com.lifetrack.app.ui.components.sliceDetail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The arithmetic behind the sheet a chart opens when you tap it.
 *
 * Only the parts that do not need a composition: the ranking, the percentages and the slice
 * builder. `dayDetail` reads the theme for its colours, so it belongs to an instrumented test.
 */
class ChartDetailTest {

    private fun slice(label: String, value: Double) = Slice(label, value, Color.Red)

    private val meals = listOf(
        slice("Breakfast", 400.0),
        slice("Lunch", 800.0),
        slice("Snacks", 200.0),
        slice("Dinner", 600.0)
    )

    // ------------------------------------------------------------------ ordinals

    @Test fun `ordinals read the way people say them`() {
        assertEquals("1st", ordinal(1))
        assertEquals("2nd", ordinal(2))
        assertEquals("3rd", ordinal(3))
        assertEquals("4th", ordinal(4))
        assertEquals("21st", ordinal(21))
        assertEquals("22nd", ordinal(22))
        assertEquals("33rd", ordinal(33))
    }

    /** The exceptions everyone forgets: 11, 12 and 13 are all "th", not "st"/"nd"/"rd". */
    @Test fun `the teens are all th`() {
        assertEquals("11th", ordinal(11))
        assertEquals("12th", ordinal(12))
        assertEquals("13th", ordinal(13))
        assertEquals("111th", ordinal(111))
        assertEquals("112th", ordinal(112))
    }

    // ------------------------------------------------------------------ percentages

    @Test fun `percentages round to whole numbers`() {
        assertEquals(50, percentOf(1.0, 2.0))
        assertEquals(33, percentOf(1.0, 3.0))
        assertEquals(100, percentOf(2.0, 2.0))
    }

    /** A window with nothing logged must not divide by zero on the way to a tooltip. */
    @Test fun `an empty total is zero percent rather than a crash`() {
        assertEquals(0, percentOf(5.0, 0.0))
        assertEquals(0, percentOf(0.0, 0.0))
    }

    // ------------------------------------------------------------------ slice detail

    @Test fun `a slice knows its share and its rank`() {
        val detail = sliceDetail(meals, meals[1], format = { "%,.0f kcal".format(it) })
        assertEquals("Lunch", detail.title)
        assertEquals("800 kcal", detail.value)

        val share = detail.facts.first { it.label == "Share of the total" }
        assertEquals("40%", share.value)                       // 800 of 2000

        val rank = detail.facts.first { it.label == "Rank" }
        assertEquals("1st of 4", rank.value)

        val total = detail.facts.first { it.label == "Total across all" }
        assertEquals("2,000 kcal", total.value)
    }

    @Test fun `the smallest slice ranks last and still reports honestly`() {
        val detail = sliceDetail(meals, meals[2], format = { "%,.0f".format(it) })
        assertEquals("4th of 4", detail.facts.first { it.label == "Rank" }.value)
        assertEquals("10%", detail.facts.first { it.label == "Share of the total" }.value)
    }

    /** Looking at the biggest slice, "Biggest: itself" would be a silly line to print. */
    @Test fun `the biggest slice is not told what the biggest slice is`() {
        val biggest = sliceDetail(meals, meals[1], format = { "$it" })
        assertTrue(biggest.facts.none { it.label == "Biggest" })

        val smaller = sliceDetail(meals, meals[2], format = { "%,.0f".format(it) })
        assertEquals("Lunch (800)", smaller.facts.first { it.label == "Biggest" }.value)
    }

    @Test fun `a lone slice has nothing to rank against`() {
        val single = listOf(slice("Only", 100.0))
        val detail = sliceDetail(single, single[0], format = { "%,.0f".format(it) })
        assertTrue(detail.facts.none { it.label == "Rank" })
        assertEquals("100%", detail.facts.first { it.label == "Share of the total" }.value)
    }

    @Test fun `a slice carries the colour it was drawn in`() {
        val green = Slice("Lunch", 10.0, Color.Green)
        assertEquals(Color.Green, sliceDetail(listOf(green), green, format = { "$it" }).accent)
    }

    @Test fun `extra facts are appended, not lost`() {
        val detail = sliceDetail(
            meals, meals[0], format = { "%,.0f".format(it) },
            subtitle = "Across this week",
            extra = listOf(com.lifetrack.app.ui.components.ChartFact("Average a day", "57 kcal"))
        )
        assertEquals("Across this week", detail.subtitle)
        assertEquals("57 kcal", detail.facts.last().value)
    }
}
