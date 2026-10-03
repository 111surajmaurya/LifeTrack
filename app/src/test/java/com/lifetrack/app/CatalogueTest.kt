package com.lifetrack.app

import com.lifetrack.app.data.Catalogue
import com.lifetrack.app.data.FoodSeed
import com.lifetrack.app.data.IndianFoodSeed
import com.lifetrack.app.data.Serving
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The joined catalogue: FoodSeed plus the Indian Food Nutrition Database dishes. */
class CatalogueTest {

    private fun food(name: String) = Catalogue.items.first { it.name == name }

    @Test fun `the Indian database adds hundreds of foods`() {
        assertTrue(IndianFoodSeed.items.size >= 500)
        assertEquals(FoodSeed.items.size + IndianFoodSeed.items.size, Catalogue.items.size)
    }

    /** The refresh matches on name; a duplicate would make one of them unreachable. */
    @Test fun `no name appears twice across both lists`() {
        val names = Catalogue.items.map { it.name.lowercase() }
        val dupes = names.groupingBy { it }.eachCount().filter { it.value > 1 }.keys
        assertTrue("duplicates: $dupes", dupes.isEmpty())
        assertEquals(Catalogue.items.size, Catalogue.byName.size)
    }

    @Test fun `the version moves when the Indian list does`() {
        assertTrue(Catalogue.VERSION > FoodSeed.VERSION)
    }

    @Test fun `every new food is plausible`() {
        IndianFoodSeed.items.forEach {
            assertTrue("blank name", it.name.isNotBlank())
            assertTrue("${it.name} kcal ${it.kcal}", it.kcal in 0..1_500)
            listOf(it.protein, it.fiber, it.vitA, it.vitC, it.iron, it.calcium).forEach { v ->
                assertTrue("${it.name} negative nutrient", v >= 0f)
            }
            assertTrue(
                "${it.name}: ${it.protein} g protein + ${it.fiber} g fibre can't fit in ${it.kcal} kcal",
                it.protein * 4f + it.fiber * 2f <= it.kcal * 1.15f + 1f
            )
            assertTrue("no emoji for ${it.category}", Catalogue.emojiFor(it.category).isNotBlank())
            assertTrue("seeded food marked custom", !it.custom)
        }
    }

    @Test fun `servings follow the portion description`() {
        assertEquals(Serving.Piece, food("Besan chilla").servingType)
        assertEquals(Serving.Bowl, food("Dal makhani (home, light)").servingType)
        assertEquals(Serving.Cup, food("Tea with milk, no sugar").servingType)
        // "2 tbsp" in the workbook becomes per teaspoon, the app's spoon unit.
        assertTrue(food("Green chutney (mint-coriander)").kcal < 15)
    }

    @Test fun `variants of one dish stay separate`() {
        assertTrue(food("Dal makhani (restaurant)").kcal > food("Dal makhani (home, light)").kcal)
    }

    @Test fun `meal combos arrive as one serving each`() {
        val combo = food("Idli sambar (2 idli)")
        assertEquals(IndianFoodSeed.THALI, combo.category)
        assertEquals(285, combo.kcal)
        assertTrue(Catalogue.items.count { it.category == IndianFoodSeed.THALI } >= 25)
    }

    @Test fun `foods the app already had keep their numbers`() {
        // Idli was in FoodSeed; the workbook's idli was skipped rather than duplicated.
        assertEquals(1, Catalogue.items.count { it.name.equals("Idli", ignoreCase = true) })
        assertEquals(FoodSeed.byName.getValue("Idli"), Catalogue.byName.getValue("Idli"))
    }
}
