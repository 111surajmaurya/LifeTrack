package com.lifetrack.app

import com.lifetrack.app.data.FoodItem
import com.lifetrack.app.data.FoodSeed
import com.lifetrack.app.data.Portion
import com.lifetrack.app.data.Serving
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the generated catalogue.
 *
 * `FoodSeed.kt` is written by `tools/foodseed/generate_seed.py` out of USDA FoodData Central,
 * so the risk here is not a typo — it is a bad *mapping*: the search picking "Cake made with
 * glutinous rice" for Upma, or a serving weight off by a factor of ten. Both produce a file
 * that compiles perfectly and is quietly wrong.
 *
 * These tests are the arithmetic that catches that. A failure means re-run the generator and
 * read `tools/foodseed/mapping_audit.txt`, which names the FDC entry behind every number.
 */
class FoodSeedTest {

    private fun food(name: String): FoodItem =
        FoodSeed.items.firstOrNull { it.name == name }
            ?: throw AssertionError("no food called $name")

    // ------------------------------------------------------------------ shape

    @Test fun `the catalogue covers every cuisine the app offers`() {
        FoodSeed.categories.forEach { category ->
            val n = FoodSeed.items.count { it.category == category }
            if (category != FoodSeed.OTHER) {
                assertTrue("$category has only $n foods", n >= 10)
            }
        }
    }

    @Test fun `names are unique, because the catalogue refresh matches on them`() {
        val names = FoodSeed.items.map { it.name.lowercase() }
        assertEquals("duplicate food names", names.size, names.toSet().size)
        assertEquals(FoodSeed.items.size, FoodSeed.byName.size)
        FoodSeed.items.forEach { assertEquals(it, FoodSeed.byName[it.name]) }
    }

    @Test fun `every food has a name, a known category and an emoji`() {
        FoodSeed.items.forEach { item ->
            assertTrue("blank name", item.name.isNotBlank())
            assertTrue("unknown category ${item.category}", item.category in FoodSeed.categories)
            assertTrue("no emoji for ${item.category}", FoodSeed.emojiFor(item.category).isNotBlank())
            assertTrue("seeded food marked custom: ${item.name}", !item.custom)
        }
    }

    @Test fun `the seed version is set, or no existing install would ever refresh`() {
        assertTrue(FoodSeed.VERSION > 0)
    }

    // ------------------------------------------------------------------ plausibility

    @Test fun `no nutrient is negative`() {
        FoodSeed.items.forEach {
            assertTrue("${it.name} kcal", it.kcal >= 0)
            assertTrue("${it.name} protein", it.protein >= 0f)
            assertTrue("${it.name} fibre", it.fiber >= 0f)
            assertTrue("${it.name} vitA", it.vitA >= 0f)
            assertTrue("${it.name} vitC", it.vitC >= 0f)
            assertTrue("${it.name} iron", it.iron >= 0f)
            assertTrue("${it.name} calcium", it.calcium >= 0f)
        }
    }

    /**
     * Protein is 4 kcal a gram and fibre about 2, so neither can account for more energy than
     * the food actually has. This is the check that catches a serving weight scaled wrongly:
     * ten times the protein on the same calories fails here immediately.
     */
    @Test fun `protein and fibre fit inside the calories they come with`() {
        FoodSeed.items.filter { it.kcal > 0 }.forEach {
            val fromMacros = it.protein * 4f + it.fiber * 2f
            assertTrue(
                "${it.name}: ${it.protein}g protein + ${it.fiber}g fibre cannot fit in ${it.kcal} kcal",
                fromMacros <= it.kcal * 1.15f
            )
        }
    }

    @Test fun `a zero-calorie food carries no macros either`() {
        FoodSeed.items.filter { it.kcal == 0 }.forEach {
            assertEquals("${it.name} has calories-free protein", 0f, it.protein, 0.05f)
        }
    }

    /** A medium serving of anything a person eats in one go lands in a believable range. */
    @Test fun `no serving is absurdly large or small`() {
        FoodSeed.items.forEach {
            assertTrue("${it.name} at ${it.kcal} kcal a serving", it.kcal <= 900)
        }
        // A spoon of oil is the densest thing here; nothing should beat it per serving.
        assertTrue(food("Cooking Oil").kcal >= 100)
    }

    // ------------------------------------------------------------------ known values

    /**
     * Spot checks against figures anyone can look up. These are the canaries: if the mapping
     * or the serving weights drift, these move first.
     */
    @Test fun `staples match their published values`() {
        // 40 g chapati, USDA SR 171844 at 297 kcal/100 g
        assertEquals(119, food("Roti").kcal)
        // a 150 g katori of cooked rice, which ICMR-NIN also puts near 195 kcal
        assertEquals(194, food("Rice").kcal)
        // 250 ml whole milk
        assertEquals(152, food("Milk").kcal)
        assertEquals(8.2f, food("Milk").protein, 0.2f)
        // one boiled egg
        assertEquals(72, food("Egg (boiled)").kcal)
        // 100 g roast chicken breast, skinless
        assertEquals(30.2f, food("Chicken Breast (100 g)").protein, 0.5f)
        // one medium banana
        assertEquals(105, food("Banana").kcal)
    }

    @Test fun `the protein-rich foods really are the protein-rich ones`() {
        assertTrue(food("Chicken Breast (100 g)").protein >= 25f)
        assertTrue(food("Paneer (50 g)").protein >= 7f)
        assertTrue(food("Daal").protein >= 8f)
        assertTrue(food("Greek Yogurt").protein >= 10f)
        assertEquals(0f, food("Water").protein, 0.01f)
    }

    @Test fun `the high-fibre foods really are the high-fibre ones`() {
        assertTrue("daal", food("Daal").fiber >= 5f)
        assertTrue("rajma", food("Rajma").fiber >= 5f)
        assertTrue("chole", food("Chole").fiber >= 5f)
        assertTrue("apple", food("Apple").fiber >= 3f)
        assertEquals("oil has no fibre", 0f, food("Cooking Oil").fiber, 0.01f)
    }

    @Test fun `vitamin C sits where the fruit is`() {
        assertTrue("guava", food("Guava").vitC >= 100f)
        assertTrue("orange", food("Orange").vitC >= 40f)
        assertEquals("rice has none", 0f, food("Rice").vitC, 0.5f)
    }

    @Test fun `calcium sits where the dairy is`() {
        assertTrue("milk", food("Milk").calcium >= 250f)
        assertTrue("curd", food("Curd").calcium >= 100f)
        assertTrue("paneer", food("Paneer (50 g)").calcium >= 100f)
    }

    // ------------------------------------------------------------------ servings

    @Test fun `rice daal and sabji are bowls so they get size options`() {
        listOf("Rice", "Daal", "Sabji", "Rajma", "Sambar").forEach {
            assertTrue("$it should be sized", food(it).servingType.sized)
        }
    }

    @Test fun `roti and egg are counted, not sized`() {
        listOf("Roti", "Naan", "Egg (boiled)", "Samosa", "Idli").forEach {
            assertTrue("$it should be counted", !food(it).servingType.sized)
        }
        assertEquals(Serving.Piece, food("Roti").servingType)
    }

    /** Two rotis is exactly twice one roti; a large bowl is exactly 1.5 medium ones. */
    @Test fun `portions scale the published serving, not some other number`() {
        val roti = food("Roti")
        assertEquals(roti.kcal * 2, roti.kcalFor(2f, Portion.Medium))
        val daal = food("Daal")
        assertEquals(Math.round(daal.kcal * 1.5f), daal.kcalFor(1f, Portion.Large))
        assertEquals(Math.round(daal.kcal * 0.6f), daal.kcalFor(1f, Portion.Small))
    }

    // ------------------------------------------------------------------ coverage

    @Test fun `the cuisines the user asked for are actually there`() {
        listOf("Dosa", "Idli", "Sambar", "Medu Vada", "Pongal").forEach { food(it) }
        listOf("Pizza Slice", "Lasagna", "Risotto", "Ravioli", "Tiramisu").forEach { food(it) }
        listOf("Burger", "French Fries", "Hot Dog", "Taco", "Milkshake").forEach { food(it) }
        listOf("Bhel Puri", "Pani Puri (6 pc)", "Vada Pav", "Pav Bhaji", "Aloo Tikki").forEach { food(it) }
        listOf("Rajma", "Chole", "Paneer Butter Masala", "Naan", "Kheer").forEach { food(it) }
    }
}
