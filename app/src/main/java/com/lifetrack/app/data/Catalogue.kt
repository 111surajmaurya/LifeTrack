package com.lifetrack.app.data

/**
 * The whole food catalogue: the USDA-derived [FoodSeed] plus the Indian dishes in
 * [IndianFoodSeed]. Both are generated; this is the hand-written seam that joins them, so
 * seeding and the UI never need to know there are two sources.
 */
object Catalogue {

    /**
     * Goes up whenever either list changes, which is what makes an existing install refresh
     * (`Repository.ensureSeeded` compares it with `Settings.seedVersion`).
     */
    const val VERSION = FoodSeed.VERSION + IndianFoodSeed.VERSION

    val items: List<FoodItem> by lazy { FoodSeed.items + IndianFoodSeed.items }

    val byName: Map<String, FoodItem> by lazy { items.associateBy { it.name } }

    fun emojiFor(category: String): String = when (category) {
        IndianFoodSeed.BREAKFAST -> "🍳"     // cooking
        IndianFoodSeed.INDO_CHINESE -> "🍜"  // noodles
        IndianFoodSeed.SOUP -> "🥣"          // bowl with spoon
        IndianFoodSeed.THALI -> "🍱"         // bento / thali
        else -> FoodSeed.emojiFor(category)
    }
}
