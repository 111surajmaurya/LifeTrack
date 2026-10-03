package com.lifetrack.app.data

/**
 * The food catalogue: 226 everyday foods with calories, protein, fibre, vitamin A,
 * vitamin C, iron and calcium for one *medium* serving.
 *
 * **This file is generated.** Every number is computed from USDA FoodData Central -
 * FNDDS 2021-2023 for prepared dishes (dosa, biryani, samosa, pizza) and SR Legacy for
 * reference foods (chapati, rice, fruit) - scaled to a real Indian serving: a 150 g katori,
 * a 40 g roti, a 250 ml glass. Dishes neither database carries (poha, lassi, bhel puri,
 * gulab jamun) are built from their measured ingredients instead of guessed at.
 *
 * The tooling that produced it, including the per-food provenance of every figure, lives in
 * `tools/foodseed/` - edit `catalogue.py` and re-run `generate_seed.py`, never this file.
 *
 * Bowl/plate/glass items scale by portion (small x0.6, large x1.5); pieces just multiply by
 * count. These are still averages of real foods, not of *your* food: a home kitchen's daal is
 * not a laboratory's. Add your own item with exact numbers and it joins the same list.
 */
object FoodSeed {

    /**
     * Bumped whenever the numbers here change. `Repository.ensureSeeded` compares it against
     * `Settings.seedVersion` and refreshes the catalogue when it is behind, which is the only
     * reason a phone that already has `food_items` rows ever sees a corrected figure.
     */
    const val VERSION = 2

    const val ROTI = "Roti & bread"
    const val RICE = "Rice & grains"
    const val DAL = "Dal & curry"
    const val SABJI = "Sabji"
    const val NONVEG = "Egg & meat"
    const val SOUTH = "South Indian"
    const val CHAAT = "Chaat & street"
    const val SNACK = "Snacks"
    const val ITALIAN = "Italian"
    const val FAST = "Fast food"
    const val DAIRY = "Dairy & drinks"
    const val FRUIT = "Fruit & nuts"
    const val SWEET = "Sweets"
    const val OTHER = "Other"

    /** Categories in the order they show up as browse chips. */
    val categories = listOf(
        ROTI, RICE, DAL, SABJI, NONVEG, SOUTH, CHAAT, SNACK, ITALIAN, FAST,
        DAIRY, FRUIT, SWEET, OTHER
    )

    fun emojiFor(category: String): String = when (category) {
        ROTI -> "\uD83E\uDED3"      // flatbread
        RICE -> "\uD83C\uDF5A"      // rice
        DAL -> "\uD83C\uDF72"       // stew
        SABJI -> "\uD83E\uDD57"     // salad
        NONVEG -> "\uD83E\uDD5A"    // egg
        SOUTH -> "\uD83E\uDD63"     // bowl with spoon
        CHAAT -> "\uD83C\uDF62"     // street snack
        SNACK -> "\uD83C\uDF5F"     // fries
        ITALIAN -> "\uD83C\uDF55"   // pizza
        FAST -> "\uD83C\uDF54"      // burger
        DAIRY -> "\uD83E\uDD5B"     // milk
        FRUIT -> "\uD83C\uDF4E"     // apple
        SWEET -> "\uD83C\uDF6E"     // custard
        else -> "\uD83C\uDF7D"      // plate
    }

    private fun item(
        name: String, kcal: Int, protein: Float, fiber: Float, vitA: Float, vitC: Float,
        iron: Float, calcium: Float, serving: Serving, cat: String
    ) = FoodItem(
        name = name, kcal = kcal, protein = protein, fiber = fiber, vitA = vitA,
        vitC = vitC, iron = iron, calcium = calcium, serving = serving.name, category = cat
    )

    private fun piece(n: String, k: Int, p: Float, f: Float, a: Float, c: Float, fe: Float, ca: Float, cat: String) =
        item(n, k, p, f, a, c, fe, ca, Serving.Piece, cat)

    private fun bowl(n: String, k: Int, p: Float, f: Float, a: Float, c: Float, fe: Float, ca: Float, cat: String) =
        item(n, k, p, f, a, c, fe, ca, Serving.Bowl, cat)

    private fun plate(n: String, k: Int, p: Float, f: Float, a: Float, c: Float, fe: Float, ca: Float, cat: String) =
        item(n, k, p, f, a, c, fe, ca, Serving.Plate, cat)

    private fun glass(n: String, k: Int, p: Float, f: Float, a: Float, c: Float, fe: Float, ca: Float, cat: String) =
        item(n, k, p, f, a, c, fe, ca, Serving.Glass, cat)

    private fun cup(n: String, k: Int, p: Float, f: Float, a: Float, c: Float, fe: Float, ca: Float, cat: String) =
        item(n, k, p, f, a, c, fe, ca, Serving.Cup, cat)

    private fun spoon(n: String, k: Int, p: Float, f: Float, a: Float, c: Float, fe: Float, ca: Float, cat: String) =
        item(n, k, p, f, a, c, fe, ca, Serving.Spoon, cat)

    private fun handful(n: String, k: Int, p: Float, f: Float, a: Float, c: Float, fe: Float, ca: Float, cat: String) =
        item(n, k, p, f, a, c, fe, ca, Serving.Handful, cat)

    private fun serve(n: String, k: Int, p: Float, f: Float, a: Float, c: Float, fe: Float, ca: Float, cat: String) =
        item(n, k, p, f, a, c, fe, ca, Serving.Serve, cat)

    val items: List<FoodItem> = listOf(
        // ---- Roti & bread ----
        piece("Roti", 119, 4.5f, 2.0f, 0.0f, 0.0f, 1.2f, 37.2f, ROTI),
        piece("Chapati", 119, 4.5f, 2.0f, 0.0f, 0.0f, 1.2f, 37.2f, ROTI),
        piece("Phulka", 89, 3.4f, 1.5f, 0.0f, 0.0f, 0.9f, 27.9f, ROTI),
        piece("Tandoori Roti", 148, 5.6f, 2.5f, 0.0f, 0.0f, 1.5f, 46.5f, ROTI),
        piece("Whole Wheat Roti", 135, 3.5f, 4.4f, 0.0f, 0.0f, 1.0f, 16.2f, ROTI),
        piece("Missi Roti", 168, 5.2f, 4.7f, 0.2f, 0.7f, 1.5f, 15.0f, ROTI),
        piece("Bajra Roti", 133, 3.2f, 1.1f, 0.0f, 0.0f, 1.2f, 4.2f, ROTI),
        piece("Jowar Roti", 126, 2.5f, 2.0f, 0.0f, 0.2f, 0.9f, 3.6f, ROTI),
        piece("Makki Roti", 110, 2.7f, 1.3f, 30.5f, 0.0f, 0.5f, 44.5f, ROTI),
        piece("Naan", 280, 10.0f, 4.7f, 1.8f, 0.0f, 1.7f, 57.6f, ROTI),
        piece("Butter Naan", 323, 10.0f, 4.7f, 42.8f, 0.0f, 1.7f, 59.0f, ROTI),
        piece("Kulcha", 233, 7.7f, 1.8f, 0.0f, 0.0f, 2.6f, 67.2f, ROTI),
        piece("Paratha", 228, 4.5f, 6.7f, 1.4f, 0.0f, 1.1f, 17.5f, ROTI),
        piece("Aloo Paratha", 299, 5.0f, 7.1f, 6.7f, 3.4f, 1.2f, 18.9f, ROTI),
        piece("Paneer Paratha", 342, 8.6f, 6.7f, 41.7f, 0.0f, 1.1f, 172.7f, ROTI),
        piece("Gobi Paratha", 277, 5.0f, 7.2f, 4.5f, 10.7f, 1.2f, 23.5f, ROTI),
        piece("Puri", 102, 1.7f, 0.9f, 0.0f, 0.0f, 0.6f, 4.0f, ROTI),
        piece("Bhatura", 286, 4.8f, 2.5f, 0.0f, 0.0f, 1.8f, 11.2f, ROTI),
        piece("Pav", 112, 3.9f, 0.7f, 0.0f, 0.5f, 1.4f, 57.6f, ROTI),
        piece("Bread Slice", 74, 2.7f, 0.2f, 0.5f, 0.0f, 0.5f, 20.8f, ROTI),
        piece("Brown Bread Slice", 64, 3.1f, 1.5f, 0.0f, 0.0f, 0.6f, 40.8f, ROTI),
        piece("Toast with Butter", 110, 2.8f, 0.2f, 34.7f, 0.0f, 0.5f, 21.9f, ROTI),

        // ---- Rice & grains ----
        bowl("Rice", 194, 4.0f, 0.6f, 0.0f, 0.0f, 1.8f, 15.0f, RICE),
        bowl("Brown Rice", 184, 3.7f, 1.5f, 0.0f, 0.0f, 0.6f, 7.5f, RICE),
        bowl("Jeera Rice", 226, 3.9f, 0.6f, 0.0f, 0.0f, 1.7f, 15.0f, RICE),
        bowl("Veg Pulao", 186, 3.5f, 1.2f, 4.5f, 11.7f, 1.5f, 21.0f, RICE),
        bowl("Veg Biryani", 218, 3.9f, 2.4f, 128.0f, 8.2f, 2.1f, 34.0f, RICE),
        bowl("Chicken Biryani", 208, 14.3f, 2.2f, 38.0f, 12.4f, 1.7f, 68.0f, RICE),
        bowl("Mutton Biryani", 290, 17.0f, 2.0f, 32.0f, 11.2f, 2.2f, 66.0f, RICE),
        bowl("Curd Rice", 168, 4.6f, 0.4f, 24.0f, 0.2f, 1.2f, 73.5f, RICE),
        bowl("Lemon Rice", 276, 5.8f, 1.3f, 0.0f, 0.1f, 1.7f, 18.4f, RICE),
        bowl("Fried Rice", 313, 6.9f, 2.0f, 39.6f, 6.8f, 0.8f, 19.8f, RICE),
        bowl("Khichdi", 232, 7.2f, 4.5f, 52.4f, 0.8f, 2.8f, 20.6f, RICE),
        bowl("Daliya", 124, 4.6f, 6.8f, 0.0f, 0.0f, 1.4f, 15.0f, RICE),
        bowl("Poha", 255, 5.0f, 1.2f, 3.8f, 2.5f, 1.6f, 16.1f, RICE),
        bowl("Upma", 130, 3.0f, 1.9f, 16.5f, 16.5f, 6.1f, 144.0f, RICE),
        bowl("Oats", 114, 3.3f, 2.5f, 24.0f, 0.0f, 1.0f, 18.0f, RICE),
        bowl("Muesli with Milk", 315, 11.2f, 4.0f, 50.1f, 0.5f, 1.8f, 224.9f, RICE),
        bowl("Cornflakes with Milk", 213, 7.5f, 1.1f, 57.4f, 0.0f, 8.4f, 210.3f, RICE),
        bowl("Sabudana Khichdi", 373, 5.0f, 2.4f, 7.6f, 5.0f, 1.1f, 20.1f, RICE),
        bowl("Quinoa", 180, 6.6f, 4.2f, 0.0f, 0.0f, 2.2f, 25.5f, RICE),

        // ---- Dal & curry ----
        bowl("Daal", 218, 12.9f, 11.2f, 49.5f, 2.1f, 4.8f, 28.5f, DAL),
        bowl("Daal Tadka", 246, 12.5f, 10.9f, 82.1f, 2.0f, 4.6f, 28.8f, DAL),
        bowl("Daal Fry", 278, 12.2f, 10.7f, 46.9f, 2.0f, 4.5f, 27.0f, DAL),
        bowl("Daal Makhani", 280, 10.3f, 9.7f, 129.9f, 1.8f, 2.3f, 46.0f, DAL),
        bowl("Moong Daal", 234, 9.8f, 10.7f, 1.5f, 1.4f, 2.0f, 37.5f, DAL),
        bowl("Chana Daal", 174, 12.5f, 12.4f, 0.0f, 0.6f, 1.9f, 21.0f, DAL),
        bowl("Arhar Daal", 218, 12.9f, 11.2f, 49.5f, 2.1f, 4.8f, 28.5f, DAL),
        bowl("Masoor Daal", 174, 13.5f, 11.8f, 0.0f, 2.2f, 5.0f, 28.5f, DAL),
        bowl("Rajma", 266, 12.1f, 10.3f, 0.0f, 1.6f, 4.1f, 39.0f, DAL),
        bowl("Chole", 316, 12.3f, 10.7f, 1.5f, 1.6f, 4.0f, 69.0f, DAL),
        bowl("Kadhi", 188, 6.9f, 1.9f, 43.5f, 1.7f, 0.8f, 123.6f, DAL),
        bowl("Sambar", 129, 6.5f, 6.0f, 10.5f, 4.8f, 2.5f, 28.5f, DAL),
        bowl("Rasam", 87, 2.4f, 2.6f, 26.0f, 10.3f, 1.0f, 15.7f, DAL),
        bowl("Chana Masala", 238, 10.1f, 9.2f, 7.2f, 3.9f, 2.5f, 73.6f, DAL),
        bowl("Soya Chunk Curry", 207, 10.5f, 4.0f, 7.0f, 6.3f, 3.0f, 62.9f, DAL),

        // ---- Sabji ----
        bowl("Sabji", 86, 1.6f, 1.8f, 66.0f, 12.3f, 0.7f, 21.0f, SABJI),
        bowl("Mix Veg Sabji", 89, 2.8f, 4.3f, 207.0f, 3.1f, 0.8f, 24.0f, SABJI),
        bowl("Aloo Sabji", 165, 1.7f, 1.4f, 16.1f, 11.1f, 0.3f, 6.0f, SABJI),
        bowl("Aloo Gobi", 137, 1.7f, 1.5f, 14.3f, 22.6f, 0.4f, 11.7f, SABJI),
        bowl("Aloo Matar", 158, 2.8f, 2.3f, 50.0f, 10.0f, 0.7f, 11.2f, SABJI),
        bowl("Bhindi Masala", 117, 1.8f, 3.0f, 40.8f, 18.2f, 0.6f, 74.8f, SABJI),
        bowl("Baingan Bharta", 106, 0.9f, 2.6f, 14.2f, 3.5f, 0.2f, 9.2f, SABJI),
        bowl("Lauki Sabji", 96, 0.6f, 2.7f, 0.0f, 5.7f, 0.3f, 9.3f, SABJI),
        bowl("Cabbage Sabji", 99, 1.2f, 2.0f, 57.2f, 41.3f, 0.0f, 29.5f, SABJI),
        bowl("Kaddu Sabji", 94, 1.0f, 0.6f, 381.0f, 7.7f, 0.8f, 20.7f, SABJI),
        bowl("Palak Sabji", 63, 3.3f, 1.8f, 331.0f, 24.2f, 1.2f, 76.0f, SABJI),
        bowl("Palak Paneer", 152, 8.1f, 1.4f, 184.5f, 13.8f, 0.8f, 90.0f, SABJI),
        bowl("Paneer Butter Masala", 313, 9.6f, 0.5f, 231.8f, 6.6f, 0.1f, 346.9f, SABJI),
        bowl("Shahi Paneer", 317, 9.5f, 0.4f, 221.7f, 2.2f, 0.1f, 349.8f, SABJI),
        bowl("Matar Paneer", 257, 9.6f, 2.3f, 130.2f, 9.2f, 0.7f, 282.4f, SABJI),
        bowl("Kadhai Paneer", 261, 9.3f, 0.8f, 146.3f, 64.7f, 0.2f, 334.5f, SABJI),
        bowl("Channa Saag", 134, 6.2f, 4.5f, 331.5f, 24.0f, 2.4f, 99.0f, SABJI),
        bowl("Mushroom Masala", 105, 1.6f, 2.0f, 8.8f, 1.0f, 0.7f, 10.8f, SABJI),
        bowl("Salad", 23, 0.7f, 1.0f, 13.2f, 8.9f, 0.1f, 14.2f, SABJI),
        bowl("Raita", 66, 3.2f, 0.1f, 39.4f, 1.0f, 0.0f, 104.8f, SABJI),
        piece("Papad", 48, 3.3f, 2.4f, 1.7f, 0.0f, 1.0f, 18.6f, SABJI),

        // ---- Egg & meat ----
        piece("Egg (boiled)", 72, 6.2f, 0.0f, 90.0f, 0.0f, 0.8f, 24.0f, NONVEG),
        piece("Egg Omelette", 113, 7.1f, 0.0f, 117.7f, 0.0f, 1.0f, 28.1f, NONVEG),
        piece("Egg Poached", 72, 6.2f, 0.0f, 90.0f, 0.0f, 0.8f, 24.0f, NONVEG),
        bowl("Egg Bhurji", 230, 13.9f, 0.0f, 201.6f, 0.0f, 1.9f, 54.0f, NONVEG),
        bowl("Egg Curry", 226, 12.7f, 0.6f, 188.0f, 5.3f, 1.8f, 53.0f, NONVEG),
        bowl("Chicken Curry", 160, 9.7f, 2.1f, 72.0f, 13.3f, 1.1f, 30.0f, NONVEG),
        bowl("Butter Chicken", 299, 19.2f, 0.4f, 173.9f, 5.9f, 0.5f, 26.0f, NONVEG),
        bowl("Chicken Tikka Masala", 285, 21.9f, 0.4f, 89.4f, 5.8f, 0.4f, 21.8f, NONVEG),
        bowl("Mutton Curry", 303, 17.8f, 0.7f, 8.0f, 6.1f, 1.4f, 22.1f, NONVEG),
        bowl("Fish Curry", 140, 7.4f, 2.1f, 82.5f, 13.3f, 0.9f, 37.5f, NONVEG),
        piece("Tandoori Chicken (piece)", 145, 27.2f, 0.0f, 8.1f, 0.0f, 0.4f, 6.3f, NONVEG),
        piece("Chicken Tikka (piece)", 72, 13.6f, 0.0f, 4.0f, 0.0f, 0.2f, 3.1f, NONVEG),
        piece("Fish Fry (piece)", 168, 15.8f, 1.1f, 10.7f, 0.0f, 0.7f, 15.0f, NONVEG),
        piece("Chicken Breast (100 g)", 161, 30.2f, 0.0f, 9.0f, 0.0f, 0.5f, 7.0f, NONVEG),
        piece("Prawns (100 g)", 99, 24.0f, 0.0f, 0.0f, 0.0f, 0.5f, 70.0f, NONVEG),
        bowl("Keema", 424, 37.2f, 0.0f, 0.0f, 0.0f, 2.7f, 33.0f, NONVEG),

        // ---- South Indian ----
        piece("Dosa", 168, 4.6f, 1.4f, 0.0f, 0.2f, 1.8f, 12.8f, SOUTH),
        piece("Masala Dosa", 239, 7.1f, 2.9f, 1.3f, 1.7f, 2.4f, 22.1f, SOUTH),
        piece("Rava Dosa", 191, 4.2f, 1.4f, 0.0f, 0.8f, 1.4f, 7.1f, SOUTH),
        piece("Idli", 51, 2.5f, 2.3f, 0.0f, 0.1f, 0.4f, 4.0f, SOUTH),
        piece("Medu Vada", 120, 5.8f, 2.6f, 2.2f, 4.3f, 1.6f, 10.8f, SOUTH),
        piece("Uttapam", 197, 5.3f, 2.0f, 3.2f, 3.1f, 2.0f, 17.9f, SOUTH),
        bowl("Upma (South)", 130, 3.0f, 1.9f, 16.5f, 16.5f, 6.1f, 144.0f, SOUTH),
        spoon("Coconut Chutney", 72, 0.7f, 1.5f, 0.0f, 0.5f, 0.4f, 3.4f, SOUTH),
        bowl("Pongal", 242, 5.2f, 3.2f, 55.1f, 0.4f, 1.7f, 21.4f, SOUTH),
        piece("Appam", 100, 1.7f, 1.1f, 0.0f, 0.3f, 0.8f, 6.4f, SOUTH),
        bowl("Lemon Sevai", 264, 5.3f, 1.1f, 0.0f, 0.0f, 1.7f, 17.2f, SOUTH),
        bowl("Curd Rice (South)", 168, 4.6f, 0.4f, 24.0f, 0.2f, 1.2f, 73.5f, SOUTH),

        // ---- Chaat & street ----
        piece("Samosa", 186, 3.1f, 1.1f, 32.4f, 0.2f, 1.3f, 19.8f, CHAAT),
        piece("Kachori", 193, 2.8f, 1.4f, 0.1f, 0.1f, 1.8f, 6.8f, CHAAT),
        piece("Pakora (piece)", 82, 2.4f, 1.3f, 1.0f, 1.1f, 0.5f, 5.9f, CHAAT),
        bowl("Bhel Puri", 231, 5.5f, 2.5f, 5.9f, 4.9f, 11.6f, 19.3f, CHAAT),
        bowl("Pani Puri (6 pc)", 210, 4.4f, 3.2f, 7.2f, 4.5f, 1.4f, 22.2f, CHAAT),
        bowl("Sev Puri", 243, 6.3f, 3.8f, 6.2f, 5.1f, 1.9f, 21.5f, CHAAT),
        bowl("Dahi Puri", 203, 4.2f, 1.8f, 25.1f, 4.1f, 1.0f, 62.7f, CHAAT),
        piece("Aloo Tikki", 138, 1.0f, 0.7f, 9.9f, 6.3f, 0.2f, 2.6f, CHAAT),
        plate("Chole Bhature", 726, 19.2f, 14.2f, 1.5f, 1.6f, 6.6f, 85.0f, CHAAT),
        piece("Vada Pav", 323, 7.4f, 2.6f, 8.8f, 6.0f, 2.1f, 65.2f, CHAAT),
        plate("Pav Bhaji", 483, 11.2f, 5.3f, 247.4f, 15.2f, 3.5f, 138.9f, CHAAT),
        piece("Dhokla (piece)", 80, 3.1f, 1.5f, 0.3f, 0.0f, 0.7f, 6.3f, CHAAT),
        bowl("Papdi Chaat", 232, 5.3f, 2.7f, 26.2f, 4.7f, 1.3f, 69.1f, CHAAT),
        piece("Momo (piece)", 31, 0.9f, 0.2f, 3.5f, 0.0f, 0.4f, 35.5f, CHAAT),
        piece("Spring Roll", 86, 2.4f, 1.0f, 19.6f, 2.7f, 0.8f, 20.0f, CHAAT),
        piece("Bread Pakora", 262, 6.4f, 2.1f, 4.2f, 2.2f, 1.3f, 28.4f, CHAAT),

        // ---- Snacks ----
        piece("Sandwich", 192, 6.5f, 2.2f, 70.8f, 19.9f, 2.0f, 198.0f, SNACK),
        piece("Grilled Sandwich", 480, 15.4f, 1.7f, 264.6f, 0.0f, 2.8f, 694.4f, SNACK),
        piece("Biscuit", 39, 0.4f, 0.2f, 0.0f, 0.0f, 0.4f, 1.7f, SNACK),
        piece("Rusk", 51, 1.2f, 0.3f, 1.9f, 0.6f, 0.1f, 2.4f, SNACK),
        bowl("Maggi", 119, 2.8f, 0.7f, 0.0f, 0.0f, 1.1f, 10.8f, SNACK),
        bowl("Popcorn", 96, 3.2f, 3.6f, 2.5f, 0.0f, 0.8f, 1.8f, SNACK),
        handful("Namkeen", 169, 4.9f, 2.2f, 0.4f, 0.0f, 0.9f, 9.9f, SNACK),
        handful("Bhujia", 164, 4.3f, 2.1f, 0.4f, 0.0f, 0.9f, 8.6f, SNACK),
        handful("Chips", 149, 1.8f, 0.9f, 0.0f, 6.0f, 0.4f, 5.9f, SNACK),
        handful("Peanuts", 180, 8.4f, 2.8f, 0.0f, 0.2f, 0.5f, 18.3f, SNACK),
        handful("Roasted Chana", 49, 2.6f, 2.3f, 0.3f, 0.4f, 0.9f, 14.7f, SNACK),
        handful("Makhana", 18, 0.8f, 0.0f, 0.2f, 0.0f, 0.2f, 8.8f, SNACK),
        piece("Protein Bar", 232, 13.4f, 2.8f, 315.6f, 62.4f, 3.1f, 170.4f, SNACK),

        // ---- Italian ----
        piece("Pizza Slice", 285, 12.2f, 2.5f, 73.8f, 1.5f, 2.7f, 201.2f, ITALIAN),
        piece("Margherita Pizza Slice", 285, 12.2f, 2.5f, 73.8f, 1.5f, 2.7f, 201.2f, ITALIAN),
        piece("Pepperoni Pizza Slice", 329, 14.3f, 1.7f, 117.7f, 3.1f, 2.2f, 240.9f, ITALIAN),
        piece("Veggie Pizza Slice", 278, 11.8f, 2.5f, 72.5f, 5.5f, 2.6f, 195.5f, ITALIAN),
        bowl("Pasta (plain)", 283, 10.4f, 3.2f, 0.0f, 0.0f, 2.3f, 12.6f, ITALIAN),
        bowl("Pasta in Red Sauce", 265, 9.2f, 4.5f, 37.5f, 2.5f, 2.6f, 42.5f, ITALIAN),
        bowl("Pasta in White Sauce", 386, 11.1f, 2.9f, 123.3f, 0.2f, 2.0f, 70.6f, ITALIAN),
        bowl("Spaghetti Bolognese", 225, 14.8f, 3.8f, 67.5f, 4.0f, 2.8f, 65.0f, ITALIAN),
        plate("Lasagna", 325, 16.4f, 4.2f, 115.0f, 42.8f, 3.2f, 277.5f, ITALIAN),
        bowl("Ravioli", 381, 16.3f, 1.5f, 165.0f, 0.0f, 3.7f, 217.8f, ITALIAN),
        bowl("Gnocchi", 356, 17.0f, 0.4f, 264.0f, 0.0f, 1.7f, 338.0f, ITALIAN),
        bowl("Macaroni Cheese", 491, 19.1f, 2.6f, 233.2f, 0.0f, 2.3f, 374.0f, ITALIAN),
        bowl("Risotto", 338, 9.1f, 0.7f, 89.2f, 0.0f, 2.1f, 156.5f, ITALIAN),
        piece("Garlic Bread", 157, 3.8f, 1.1f, 5.8f, 0.1f, 1.4f, 12.2f, ITALIAN),
        piece("Bruschetta", 77, 1.9f, 0.7f, 11.7f, 3.6f, 0.7f, 22.5f, ITALIAN),
        bowl("Minestrone Soup", 98, 4.2f, 3.7f, 76.0f, 14.5f, 1.2f, 31.9f, ITALIAN),
        piece("Tiramisu", 353, 5.7f, 0.6f, 185.0f, 1.1f, 1.1f, 65.0f, ITALIAN),
        piece("Focaccia", 137, 4.8f, 1.0f, 0.0f, 0.0f, 1.7f, 19.2f, ITALIAN),

        // ---- Fast food ----
        piece("Burger", 317, 19.2f, 0.8f, 0.0f, 0.6f, 3.1f, 72.6f, FAST),
        piece("Cheeseburger", 355, 21.5f, 0.7f, 40.8f, 0.6f, 3.0f, 205.2f, FAST),
        piece("Chicken Burger", 430, 19.8f, 2.7f, 6.4f, 1.3f, 2.9f, 126.4f, FAST),
        piece("Veg Burger", 269, 15.6f, 4.2f, 1.2f, 3.6f, 3.5f, 168.0f, FAST),
        bowl("French Fries", 263, 2.9f, 2.1f, 0.0f, 9.1f, 0.6f, 12.9f, FAST),
        bowl("Onion Rings", 282, 3.4f, 2.1f, 0.0f, 1.2f, 0.8f, 57.6f, FAST),
        piece("Chicken Nuggets (6 pc)", 295, 15.3f, 0.9f, 4.8f, 0.6f, 0.8f, 10.6f, FAST),
        piece("Fried Chicken (piece)", 276, 14.3f, 0.8f, 4.5f, 0.5f, 0.7f, 9.9f, FAST),
        piece("Hot Dog", 304, 11.5f, 0.0f, 2.9f, 0.0f, 1.1f, 14.7f, FAST),
        piece("Taco", 249, 15.5f, 2.1f, 12.0f, 0.0f, 1.6f, 69.0f, FAST),
        piece("Burrito", 284, 17.7f, 12.5f, 35.2f, 0.9f, 3.6f, 169.4f, FAST),
        piece("Quesadilla", 411, 17.4f, 2.6f, 110.5f, 0.0f, 2.7f, 452.4f, FAST),
        piece("Sub Sandwich", 482, 21.5f, 4.6f, 33.0f, 0.9f, 5.1f, 387.2f, FAST),
        piece("Wrap", 418, 23.0f, 2.7f, 73.8f, 0.0f, 3.3f, 374.4f, FAST),
        piece("Doughnut", 256, 3.3f, 1.1f, 2.4f, 0.4f, 1.4f, 41.4f, FAST),
        glass("Milkshake", 444, 10.1f, 2.7f, 273.0f, 0.0f, 1.4f, 345.0f, FAST),
        bowl("Nachos", 293, 4.9f, 2.2f, 14.3f, 0.0f, 1.0f, 91.3f, FAST),

        // ---- Dairy & drinks ----
        glass("Milk", 152, 8.2f, 0.0f, 80.0f, 0.0f, 0.0f, 307.5f, DAIRY),
        glass("Toned Milk", 125, 8.4f, 0.0f, 207.5f, 0.5f, 0.0f, 315.0f, DAIRY),
        glass("Lassi (sweet)", 238, 8.3f, 0.0f, 99.2f, 0.8f, 0.0f, 283.7f, DAIRY),
        glass("Chaas", 62, 3.1f, 0.0f, 38.4f, 0.4f, 0.0f, 111.7f, DAIRY),
        glass("Nimbu Pani", 69, 0.1f, 0.1f, 0.0f, 7.7f, 0.0f, 14.2f, DAIRY),
        glass("Coconut Water", 89, 0.5f, 0.0f, 0.0f, 22.6f, 0.1f, 16.8f, DAIRY),
        glass("Fruit Juice", 113, 1.8f, 0.7f, 4.8f, 68.9f, 0.1f, 134.4f, DAIRY),
        glass("Cold Drink", 105, 0.0f, 0.0f, 0.0f, 0.0f, 0.1f, 2.5f, DAIRY),
        glass("Protein Shake", 258, 31.6f, 0.9f, 80.0f, 0.0f, 0.3f, 448.2f, DAIRY),
        glass("Water", 0, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 15.0f, DAIRY),
        cup("Tea", 69, 2.0f, 0.0f, 19.2f, 0.0f, 0.0f, 78.8f, DAIRY),
        cup("Tea (no sugar)", 37, 2.0f, 0.0f, 19.2f, 0.0f, 0.0f, 79.2f, DAIRY),
        cup("Green Tea", 2, 0.3f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, DAIRY),
        cup("Black Coffee", 2, 0.2f, 0.0f, 0.0f, 0.0f, 0.0f, 3.0f, DAIRY),
        cup("Coffee with Milk", 62, 2.1f, 0.0f, 19.2f, 0.0f, 0.0f, 75.5f, DAIRY),
        bowl("Curd", 117, 5.7f, 0.0f, 72.0f, 0.8f, 0.0f, 190.5f, DAIRY),
        bowl("Dahi", 117, 5.7f, 0.0f, 72.0f, 0.8f, 0.0f, 190.5f, DAIRY),
        bowl("Greek Yogurt", 141, 13.2f, 0.0f, 57.0f, 0.0f, 0.0f, 166.5f, DAIRY),
        piece("Paneer (50 g)", 150, 8.0f, 0.0f, 77.5f, 0.0f, 0.0f, 298.5f, DAIRY),
        piece("Cheese Slice", 68, 3.4f, 0.0f, 52.4f, 0.0f, 0.1f, 212.0f, DAIRY),
        spoon("Butter", 100, 0.1f, 0.0f, 95.8f, 0.0f, 0.0f, 3.4f, DAIRY),
        spoon("Ghee", 123, 0.0f, 0.0f, 117.6f, 0.0f, 0.0f, 0.6f, DAIRY),
        spoon("Cooking Oil", 126, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, DAIRY),
        spoon("Sugar", 48, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.1f, DAIRY),
        spoon("Honey", 64, 0.1f, 0.0f, 0.0f, 0.1f, 0.1f, 1.3f, DAIRY),
        spoon("Jaggery", 57, 0.0f, 0.0f, 0.0f, 0.0f, 0.1f, 12.4f, DAIRY),

        // ---- Fruit & nuts ----
        piece("Banana", 105, 1.3f, 3.1f, 3.5f, 10.3f, 0.3f, 5.9f, FRUIT),
        piece("Apple", 95, 0.5f, 4.4f, 5.5f, 8.4f, 0.2f, 10.9f, FRUIT),
        piece("Orange", 60, 0.9f, 3.1f, 14.4f, 59.0f, 0.1f, 56.3f, FRUIT),
        piece("Mango", 124, 1.7f, 3.3f, 111.8f, 75.3f, 0.3f, 22.8f, FRUIT),
        piece("Guava", 37, 1.4f, 3.0f, 17.1f, 125.4f, 0.1f, 9.9f, FRUIT),
        piece("Chikoo", 83, 0.4f, 5.3f, 3.0f, 14.7f, 0.8f, 21.0f, FRUIT),
        piece("Pear", 101, 0.6f, 5.5f, 1.8f, 7.7f, 0.3f, 16.0f, FRUIT),
        piece("Date", 22, 0.1f, 0.5f, 0.6f, 0.0f, 0.1f, 5.1f, FRUIT),
        bowl("Papaya", 62, 0.7f, 2.5f, 68.2f, 88.3f, 0.4f, 29.0f, FRUIT),
        bowl("Watermelon", 46, 0.9f, 0.6f, 42.6f, 12.3f, 0.4f, 10.6f, FRUIT),
        bowl("Pomegranate", 144, 2.9f, 7.0f, 0.0f, 17.7f, 0.5f, 17.4f, FRUIT),
        bowl("Grapes", 125, 1.4f, 1.4f, 4.5f, 4.8f, 0.3f, 15.1f, FRUIT),
        bowl("Fruit Salad", 231, 2.1f, 0.9f, 100.5f, 40.0f, 0.5f, 33.0f, FRUIT),
        piece("Almond", 7, 0.3f, 0.1f, 0.0f, 0.0f, 0.0f, 3.2f, FRUIT),
        piece("Cashew", 9, 0.3f, 0.1f, 0.0f, 0.0f, 0.1f, 0.6f, FRUIT),
        piece("Walnut", 26, 0.6f, 0.3f, 0.0f, 0.1f, 0.1f, 3.9f, FRUIT),
        piece("Raisin (10 pc)", 15, 0.2f, 0.2f, 0.0f, 0.2f, 0.0f, 3.2f, FRUIT),
        piece("Pistachio", 4, 0.2f, 0.1f, 0.2f, 0.0f, 0.0f, 0.8f, FRUIT),

        // ---- Sweets ----
        piece("Gulab Jamun", 153, 0.9f, 0.1f, 4.7f, 0.2f, 0.4f, 17.9f, SWEET),
        piece("Rasgulla", 134, 2.9f, 0.0f, 27.9f, 0.0f, 0.0f, 107.7f, SWEET),
        piece("Jalebi", 163, 0.8f, 0.2f, 0.0f, 0.0f, 0.8f, 1.6f, SWEET),
        piece("Laddu", 197, 3.7f, 1.7f, 75.6f, 0.0f, 0.8f, 10.0f, SWEET),
        piece("Barfi", 114, 0.6f, 0.0f, 46.5f, 0.2f, 0.0f, 21.5f, SWEET),
        piece("Gujiya", 177, 1.4f, 0.8f, 54.7f, 0.2f, 1.2f, 4.8f, SWEET),
        piece("Chocolate Bar", 223, 4.0f, 1.6f, 6.3f, 0.0f, 0.3f, 16.2f, SWEET),
        piece("Ice Cream Scoop", 137, 2.3f, 0.5f, 77.9f, 0.4f, 0.1f, 84.5f, SWEET),
        bowl("Halwa", 337, 4.0f, 1.2f, 123.1f, 0.0f, 1.3f, 9.7f, SWEET),
        bowl("Kheer", 162, 4.8f, 0.5f, 28.5f, 1.9f, 0.2f, 142.5f, SWEET),
        bowl("Shrikhand", 155, 2.7f, 0.0f, 33.6f, 0.3f, 0.0f, 89.2f, SWEET),
        bowl("Gajar Halwa", 197, 1.2f, 1.6f, 490.6f, 2.5f, 0.1f, 46.4f, SWEET),
        piece("Rasmalai", 131, 4.0f, 0.0f, 39.0f, 0.0f, 0.0f, 150.3f, SWEET)
    )

    /**
     * Per-medium-serving nutrition keyed by name, used to top up a catalogue that predates a
     * release: seeding is skipped once `food_items` has rows, so without this an existing
     * install would keep the old numbers forever. See `Repository.ensureSeeded`.
     */
    val byName: Map<String, FoodItem> = items.associateBy { it.name }
}
