# -*- coding: utf-8 -*-
"""
The LifeTrack food catalogue, defined against USDA FoodData Central rather than by hand.

Each entry is (name, category, serving, grams, source) where

  grams   the weight of ONE MEDIUM serving - a 150 g katori, a 40 g roti, a 250 ml glass.
          Portion chips scale this (small x0.6, large x1.5), exactly as the app does.
  source  ('q', 'search terms')       -> best-scoring FDC match, audited in mapping_audit.txt
          ('id', 123456)              -> a specific FDC entry, pinned because the search picked wrong
          ('mix', [(src, g), ...])    -> a dish neither database carries, built from its measured
                                         ingredients; the grams listed ARE the serving.

Everything downstream (calories, protein, fibre, vitamin A, vitamin C, iron, calcium) is
computed from those, so no nutrient number in this project is typed in by hand.
"""

ROTI = "Roti & bread"
RICE = "Rice & grains"
DAL = "Dal & curry"
SABJI = "Sabji"
NONVEG = "Egg & meat"
SOUTH = "South Indian"
CHAAT = "Chaat & street"
SNACK = "Snacks"
ITALIAN = "Italian"
FAST = "Fast food"
DAIRY = "Dairy & drinks"
FRUIT = "Fruit & nuts"
SWEET = "Sweets"
OTHER = "Other"

PIECE, BOWL, PLATE, GLASS, CUP, SPOON, HANDFUL, SERVE = (
    "Piece", "Bowl", "Plate", "Glass", "Cup", "Spoon", "Handful", "Serve")

# Ingredients reused by the 'mix' recipes below.
YOGURT = ('id', 2705418)        # Yogurt, whole milk, plain
MILK = ('id', 2705385)          # Milk, whole
SUGAR = ('id', 2710258)         # Sugar, white, granulated
OIL = ('id', 2710180)           # Vegetable oil, NFS
BESAN = ('id', 174288)          # Chickpea flour (besan)
SEMOLINA = ('id', 169715)       # Semolina, enriched
POTATO = ('id', 2709385)        # Potato, boiled
ONION = ('id', 2709795)         # Onions, raw
TOMATO = ('id', 2709719)        # Tomatoes, raw
CUCUMBER = ('q', 'cucumber raw')
RICE_COOKED = ('id', 2708403)   # Rice, white, cooked
PEANUTS = ('id', 2707515)       # Peanuts, roasted, salted
COCONUT = ('q', 'coconut meat raw')
CORIANDER = ('q', 'coriander leaves raw')
TAMARIND = ('q', 'tamarind raw')
CHICKPEA_C = ('id', 2707418)    # Chickpeas, canned, no added fat
WHEAT_FLOUR = ('q', 'wheat flour whole grain')
GHEE = ('q', 'butter without salt')
PUFFED_RICE = ('q', 'cereals ready-to-eat rice puffed')
MILLET_FLOUR = ('id', 172023)   # Millet flour
JOWAR_FLOUR = ('id', 168943)    # Sorghum flour, whole-grain
CREAM = ('id', 2705597)         # Cream, heavy
PARMESAN = ('id', 2705730)      # Cheese, Parmesan, hard
ALFREDO = ('id', 2705809)       # Alfredo sauce
CHICKPEA_D = ('id', 2707416)    # Chickpeas, from dried, no added fat
SOYBEANS = ('id', 2707388)      # Soybeans, cooked
FISH_PLAIN = ('id', 2710783)    # Fish, cooked, as ingredient
WHITE_BREAD = ('q', 'bread white')
EGG_BOILED = ('q', 'egg whole boiled')
CHICKEN_BREAST = ('q', 'chicken breast roasted skin not eaten')
LAMB = ('q', 'lamb ground cooked')
PASTA_PLAIN = ('q', 'pasta cooked')

FOODS = [
    # ------------------------------------------------------------------ Roti & bread
    ("Roti", ROTI, PIECE, 40, ('id', 171844)),
    ("Chapati", ROTI, PIECE, 40, ('id', 171844)),
    ("Phulka", ROTI, PIECE, 30, ('id', 171844)),
    ("Tandoori Roti", ROTI, PIECE, 50, ('id', 171844)),
    ("Whole Wheat Roti", ROTI, PIECE, 45, ('id', 174075)),
    ("Missi Roti", ROTI, PIECE, 50, ('mix', [(WHEAT_FLOUR, 25), (BESAN, 12), (OIL, 4), (ONION, 8)])),
    ("Bajra Roti", ROTI, PIECE, 45, ('mix', [(MILLET_FLOUR, 30), (OIL, 2)])),
    ("Jowar Roti", ROTI, PIECE, 45, ('mix', [(JOWAR_FLOUR, 30), (OIL, 2)])),
    ("Makki Roti", ROTI, PIECE, 50, ('id', 168070)),
    ("Naan", ROTI, PIECE, 90, ('id', 2707613)),
    ("Butter Naan", ROTI, PIECE, 95, ('mix', [(('id', 2707613), 90), (GHEE, 6)])),
    ("Kulcha", ROTI, PIECE, 80, ('id', 171845)),
    ("Paratha", ROTI, PIECE, 70, ('id', 2707715)),
    ("Aloo Paratha", ROTI, PIECE, 100, ('mix', [(('id', 2707715), 70), (POTATO, 28), (OIL, 4)])),
    ("Paneer Paratha", ROTI, PIECE, 100, ('mix', [(('id', 2707715), 70), (('q', 'cheese paneer'), 26), (OIL, 4)])),
    ("Gobi Paratha", ROTI, PIECE, 100, ('mix', [(('id', 2707715), 70), (('q', 'cauliflower cooked'), 26), (OIL, 4)])),
    ("Puri", ROTI, PIECE, 25, ('id', 2707714)),
    ("Bhatura", ROTI, PIECE, 70, ('id', 2707714)),
    ("Pav", ROTI, PIECE, 40, ('q', 'roll white soft')),
    ("Bread Slice", ROTI, PIECE, 25, ('q', 'bread white')),
    ("Brown Bread Slice", ROTI, PIECE, 25, ('q', 'bread whole wheat')),
    ("Toast with Butter", ROTI, PIECE, 30, ('mix', [(WHITE_BREAD, 25), (GHEE, 5)])),

    # ------------------------------------------------------------------ Rice & grains
    ("Rice", RICE, BOWL, 150, ('id', 2708403)),
    ("Brown Rice", RICE, BOWL, 150, ('id', 2708409)),
    ("Jeera Rice", RICE, BOWL, 150, ('id', 2708404)),
    ("Veg Pulao", RICE, BOWL, 150, ('q', 'rice with vegetables')),
    ("Veg Biryani", RICE, BOWL, 200, ('id', 2708985)),
    ("Chicken Biryani", RICE, BOWL, 200, ('id', 2706538)),
    ("Mutton Biryani", RICE, BOWL, 200, ('id', 2706490)),
    ("Curd Rice", RICE, BOWL, 150, ('mix', [(RICE_COOKED, 100), (YOGURT, 50)])),
    ("Lemon Rice", RICE, BOWL, 150, ('mix', [(RICE_COOKED, 135), (OIL, 6), (PEANUTS, 8)])),
    ("Fried Rice", RICE, BOWL, 180, ('q', 'fried rice NFS')),
    ("Khichdi", RICE, BOWL, 150, ('mix', [(RICE_COOKED, 90), (('id', 2707427), 55), (GHEE, 5)])),
    ("Daliya", RICE, BOWL, 150, ('q', 'bulgur cooked')),
    ("Poha", RICE, BOWL, 150, ('mix', [(('q', 'rice white cooked no added fat'), 120), (OIL, 5), (POTATO, 20), (PEANUTS, 5)])),
    ("Upma", RICE, BOWL, 150, ('id', 2709128)),
    ("Oats", RICE, BOWL, 150, ('id', 2708380)),
    ("Muesli with Milk", RICE, BOWL, 200, ('mix', [(('q', 'granola homemade'), 45), (MILK, 155)])),
    ("Cornflakes with Milk", RICE, BOWL, 200, ('mix', [(('q', 'cereal corn flakes'), 30), (MILK, 170)])),
    ("Sabudana Khichdi", RICE, BOWL, 150, ('mix', [(('id', 169717), 45), (POTATO, 40), (PEANUTS, 15), (OIL, 8)])),
    ("Quinoa", RICE, BOWL, 150, ('q', 'quinoa cooked')),

    # ------------------------------------------------------------------ Dal & curry
    ("Daal", DAL, BOWL, 150, ('id', 2707427)),
    ("Daal Tadka", DAL, BOWL, 150, ('mix', [(('id', 2707427), 145), (GHEE, 5)])),
    ("Daal Fry", DAL, BOWL, 150, ('mix', [(('id', 2707427), 142), (OIL, 8)])),
    ("Daal Makhani", DAL, BOWL, 150, ('mix', [(('q', 'black beans cooked'), 110), (CREAM, 22), (GHEE, 8), (TOMATO, 10)])),
    ("Moong Daal", DAL, BOWL, 150, ('q', 'mung beans cooked')),
    ("Chana Daal", DAL, BOWL, 150, ('q', 'split peas cooked')),
    ("Arhar Daal", DAL, BOWL, 150, ('id', 2707427)),
    ("Masoor Daal", DAL, BOWL, 150, ('id', 172421)),
    ("Rajma", DAL, BOWL, 150, ('id', 2707380)),
    ("Chole", DAL, BOWL, 150, ('id', 2707415)),
    ("Kadhi", DAL, BOWL, 150, ('mix', [(YOGURT, 90), (BESAN, 15), (OIL, 6), (ONION, 15)])),
    ("Sambar", DAL, BOWL, 150, ('id', 2707430)),
    ("Rasam", DAL, BOWL, 150, ('mix', [(TOMATO, 60), (TAMARIND, 8), (('id', 2707427), 20), (OIL, 3)])),
    ("Chana Masala", DAL, BOWL, 150, ('mix', [(CHICKPEA_C, 120), (ONION, 15), (TOMATO, 15), (OIL, 6)])),
    ("Soya Chunk Curry", DAL, BOWL, 150, ('mix', [(SOYBEANS, 60), (ONION, 22), (TOMATO, 22), (OIL, 7)])),

    # ------------------------------------------------------------------ Sabji
    ("Sabji", SABJI, BOWL, 100, ('q', 'vegetable curry')),
    ("Mix Veg Sabji", SABJI, BOWL, 100, ('id', 2710018)),
    ("Aloo Sabji", SABJI, BOWL, 100, ('mix', [(POTATO, 85), (OIL, 6), (ONION, 10)])),
    ("Aloo Gobi", SABJI, BOWL, 100, ('mix', [(POTATO, 50), (('q', 'cauliflower cooked'), 40), (OIL, 6)])),
    ("Aloo Matar", SABJI, BOWL, 100, ('mix', [(POTATO, 55), (('q', 'peas green cooked'), 35), (OIL, 6)])),
    ("Bhindi Masala", SABJI, BOWL, 100, ('mix', [(('q', 'okra cooked'), 85), (OIL, 7), (ONION, 10)])),
    ("Baingan Bharta", SABJI, BOWL, 100, ('mix', [(('q', 'eggplant cooked'), 80), (OIL, 7), (TOMATO, 12)])),
    ("Lauki Sabji", SABJI, BOWL, 100, ('mix', [(('q', 'gourd cooked'), 88), (OIL, 5), (ONION, 8)])),
    ("Cabbage Sabji", SABJI, BOWL, 100, ('mix', [(('q', 'cabbage cooked'), 88), (OIL, 5), (ONION, 8)])),
    ("Kaddu Sabji", SABJI, BOWL, 100, ('mix', [(('q', 'pumpkin cooked'), 88), (OIL, 5), (ONION, 8)])),
    ("Palak Sabji", SABJI, BOWL, 100, ('id', 2709618)),
    ("Palak Paneer", SABJI, BOWL, 150, ('id', 2709631)),
    ("Paneer Butter Masala", SABJI, BOWL, 150, ('mix', [(('q', 'cheese paneer'), 55), (TOMATO, 40), (CREAM, 20), (GHEE, 10)])),
    ("Shahi Paneer", SABJI, BOWL, 150, ('mix', [(('q', 'cheese paneer'), 55), (CREAM, 25), (ONION, 25), (GHEE, 8)])),
    ("Matar Paneer", SABJI, BOWL, 150, ('mix', [(('q', 'cheese paneer'), 45), (('q', 'peas green cooked'), 45), (TOMATO, 30), (OIL, 8)])),
    ("Kadhai Paneer", SABJI, BOWL, 150, ('mix', [(('q', 'cheese paneer'), 55), (('q', 'peppers sweet cooked'), 35), (TOMATO, 30), (OIL, 9)])),
    ("Channa Saag", SABJI, BOWL, 150, ('q', 'channa saag')),
    ("Mushroom Masala", SABJI, BOWL, 100, ('mix', [(('q', 'mushrooms cooked'), 80), (ONION, 12), (OIL, 7)])),
    ("Salad", SABJI, BOWL, 100, ('mix', [(CUCUMBER, 40), (TOMATO, 35), (ONION, 25)])),
    ("Raita", SABJI, BOWL, 100, ('mix', [(YOGURT, 80), (CUCUMBER, 20)])),
    ("Papad", SABJI, PIECE, 13, ('q', 'papad grilled')),

    # ------------------------------------------------------------------ Egg & meat
    ("Egg (boiled)", NONVEG, PIECE, 50, ('q', 'egg whole boiled')),
    ("Egg Omelette", NONVEG, PIECE, 61, ('id', 2707198)),
    ("Egg Poached", NONVEG, PIECE, 50, ('q', 'egg poached')),
    ("Egg Bhurji", NONVEG, BOWL, 120, ('id', 2707200)),
    ("Egg Curry", NONVEG, BOWL, 150, ('mix', [(EGG_BOILED, 100), (TOMATO, 25), (ONION, 15), (OIL, 8)])),
    ("Chicken Curry", NONVEG, BOWL, 150, ('q', 'chicken curry')),
    ("Butter Chicken", NONVEG, BOWL, 150, ('mix', [(('q', 'chicken breast roasted'), 70), (TOMATO, 35), (('q', 'cream heavy'), 25), (GHEE, 10)])),
    ("Chicken Tikka Masala", NONVEG, BOWL, 150, ('mix', [(CHICKEN_BREAST, 70), (TOMATO, 35), (CREAM, 22), (OIL, 10)])),
    ("Mutton Curry", NONVEG, BOWL, 150, ('mix', [(LAMB, 70), (ONION, 25), (TOMATO, 25), (OIL, 10)])),
    ("Fish Curry", NONVEG, BOWL, 150, ('q', 'fish curry')),
    ("Tandoori Chicken (piece)", NONVEG, PIECE, 90, ('q', 'chicken breast roasted skin not eaten')),
    ("Chicken Tikka (piece)", NONVEG, PIECE, 45, ('q', 'chicken breast roasted skin not eaten')),
    ("Fish Fry (piece)", NONVEG, PIECE, 85, ('mix', [(FISH_PLAIN, 70), (BESAN, 10), (OIL, 8)])),
    ("Chicken Breast (100 g)", NONVEG, PIECE, 100, ('q', 'chicken breast roasted skin not eaten')),
    ("Prawns (100 g)", NONVEG, PIECE, 100, ('q', 'shrimp cooked')),
    ("Keema", NONVEG, BOWL, 150, ('q', 'lamb ground cooked')),

    # ------------------------------------------------------------------ South Indian
    ("Dosa", SOUTH, PIECE, 80, ('id', 2708347)),
    ("Masala Dosa", SOUTH, PIECE, 130, ('id', 2709129)),
    ("Rava Dosa", SOUTH, PIECE, 100, ('mix', [(SEMOLINA, 32), (OIL, 8), (ONION, 10)])),
    ("Idli", SOUTH, PIECE, 40, ('q', 'idli')),
    ("Medu Vada", SOUTH, PIECE, 45, ('q', 'vada')),
    ("Uttapam", SOUTH, PIECE, 110, ('mix', [(('id', 2708347), 90), (ONION, 15), (TOMATO, 10)])),
    ("Upma (South)", SOUTH, BOWL, 150, ('id', 2709128)),
    ("Coconut Chutney", SOUTH, SPOON, 20, ('mix', [(COCONUT, 14), (CHICKPEA_D, 3), (OIL, 2)])),
    ("Pongal", SOUTH, BOWL, 150, ('mix', [(RICE_COOKED, 95), (('q', 'mung beans cooked'), 40), (GHEE, 8)])),
    ("Appam", SOUTH, PIECE, 70, ('mix', [(RICE_COOKED, 50), (COCONUT, 10)])),
    ("Lemon Sevai", SOUTH, BOWL, 150, ('mix', [(RICE_COOKED, 135), (OIL, 6), (PEANUTS, 6)])),
    ("Curd Rice (South)", SOUTH, BOWL, 150, ('mix', [(RICE_COOKED, 100), (YOGURT, 50)])),

    # ------------------------------------------------------------------ Chaat & street
    ("Samosa", CHAAT, PIECE, 60, ('id', 2708730)),
    ("Kachori", CHAAT, PIECE, 50, ('mix', [(('q', 'flour wheat white'), 22), (OIL, 10), (('q', 'mung beans cooked'), 15)])),
    # FNDDS 2710066 "Pakora" is 125 kcal/100 g, which cannot be right for a besan fritter deep
    # fried in oil - the same database has vada at 266 and samosa at 310, and dry besan alone
    # is 387. Built from ingredients instead; revert to ('q', 'pakora') to take USDA's figure.
    ("Pakora (piece)", CHAAT, PIECE, 25, ('mix', [(BESAN, 10), (ONION, 7), (POTATO, 4), (OIL, 4)])),
    ("Bhel Puri", CHAAT, BOWL, 100, ('mix', [(PUFFED_RICE, 35), (ONION, 20), (TOMATO, 18), (PEANUTS, 10), (TAMARIND, 8)])),
    ("Pani Puri (6 pc)", CHAAT, BOWL, 90, ('mix', [(('id', 2707714), 30), (POTATO, 35), (CHICKPEA_C, 18), (TAMARIND, 7)])),
    ("Sev Puri", CHAAT, BOWL, 100, ('mix', [(('id', 2707714), 30), (POTATO, 30), (BESAN, 15), (ONION, 15), (TAMARIND, 8)])),
    ("Dahi Puri", CHAAT, BOWL, 110, ('mix', [(('id', 2707714), 28), (POTATO, 30), (YOGURT, 40), (TAMARIND, 8)])),
    ("Aloo Tikki", CHAAT, PIECE, 60, ('mix', [(POTATO, 52), (OIL, 8)])),
    ("Chole Bhature", CHAAT, PLATE, 250, ('mix', [(('id', 2707415), 150), (('id', 2707714), 100)])),
    ("Vada Pav", CHAAT, PIECE, 110, ('mix', [(('q', 'roll white soft'), 40), (POTATO, 45), (BESAN, 12), (OIL, 12)])),
    ("Pav Bhaji", CHAAT, PLATE, 250, ('mix', [(('q', 'roll white soft'), 80), (POTATO, 75), (('id', 2710018), 60), (GHEE, 15), (TOMATO, 20)])),
    ("Dhokla (piece)", CHAAT, PIECE, 35, ('mix', [(BESAN, 14), (OIL, 2), (SUGAR, 2)])),
    ("Papdi Chaat", CHAAT, BOWL, 120, ('mix', [(('id', 2707714), 30), (POTATO, 35), (YOGURT, 40), (TAMARIND, 8), (CHICKPEA_C, 10)])),
    ("Momo (piece)", CHAAT, PIECE, 25, ('id', 2708344)),
    ("Spring Roll", CHAAT, PIECE, 40, ('q', 'egg roll vegetable')),
    ("Bread Pakora", CHAAT, PIECE, 70, ('mix', [(('q', 'bread white'), 25), (BESAN, 15), (POTATO, 18), (OIL, 12)])),

    # ------------------------------------------------------------------ Snacks
    ("Sandwich", SNACK, PIECE, 120, ('q', 'sandwich vegetable')),
    ("Grilled Sandwich", SNACK, PIECE, 140, ('q', 'grilled cheese sandwich')),
    ("Biscuit", SNACK, PIECE, 8, ('id', 2707899)),
    ("Rusk", SNACK, PIECE, 12, ('q', 'zwieback')),
    ("Maggi", SNACK, BOWL, 180, ('q', 'noodle soup ramen')),
    ("Popcorn", SNACK, BOWL, 25, ('q', 'popcorn air popped')),
    ("Namkeen", SNACK, HANDFUL, 30, ('mix', [(BESAN, 18), (OIL, 9), (PEANUTS, 3)])),
    ("Bhujia", SNACK, HANDFUL, 30, ('mix', [(BESAN, 19), (OIL, 10)])),
    ("Chips", SNACK, HANDFUL, 28, ('q', 'potato chips')),
    ("Peanuts", SNACK, HANDFUL, 30, ('id', 2707515)),
    ("Roasted Chana", SNACK, HANDFUL, 30, ('id', 2707416)),
    ("Makhana", SNACK, HANDFUL, 20, ('q', 'lotus seeds')),
    ("Protein Bar", SNACK, PIECE, 60, ('q', 'nutrition bar')),

    # ------------------------------------------------------------------ Italian
    ("Pizza Slice", ITALIAN, PIECE, 107, ('id', 2708616)),
    ("Margherita Pizza Slice", ITALIAN, PIECE, 107, ('id', 2708615)),
    ("Pepperoni Pizza Slice", ITALIAN, PIECE, 111, ('q', 'pizza pepperoni')),
    ("Veggie Pizza Slice", ITALIAN, PIECE, 115, ('id', 2708627)),
    ("Pasta (plain)", ITALIAN, BOWL, 180, ('q', 'pasta cooked')),
    ("Pasta in Red Sauce", ITALIAN, BOWL, 250, ('id', 2708831)),
    ("Pasta in White Sauce", ITALIAN, BOWL, 250, ('mix', [(PASTA_PLAIN, 160), (ALFREDO, 90)])),
    ("Spaghetti Bolognese", ITALIAN, BOWL, 250, ('q', 'spaghetti meat sauce')),
    ("Lasagna", ITALIAN, PLATE, 250, ('q', 'lasagna cheese')),
    ("Ravioli", ITALIAN, BOWL, 220, ('q', 'ravioli cheese sauce')),
    ("Gnocchi", ITALIAN, BOWL, 200, ('q', 'gnocchi cheese')),
    ("Macaroni Cheese", ITALIAN, BOWL, 220, ('id', 2708811)),
    ("Risotto", ITALIAN, BOWL, 200, ('mix', [(RICE_COOKED, 170), (PARMESAN, 15), (GHEE, 8)])),
    ("Garlic Bread", ITALIAN, PIECE, 45, ('q', 'garlic bread NFS')),
    ("Bruschetta", ITALIAN, PIECE, 45, ('q', 'bruschetta')),
    ("Minestrone Soup", ITALIAN, BOWL, 245, ('q', 'soup minestrone')),
    ("Tiramisu", ITALIAN, PIECE, 100, ('q', 'tiramisu')),
    ("Focaccia", ITALIAN, PIECE, 55, ('q', 'focaccia')),

    # ------------------------------------------------------------------ Fast food
    ("Burger", FAST, PIECE, 110, ('q', 'hamburger NFS')),
    ("Cheeseburger", FAST, PIECE, 120, ('q', 'cheeseburger NFS')),
    ("Chicken Burger", FAST, PIECE, 160, ('q', 'chicken sandwich fried')),
    ("Veg Burger", FAST, PIECE, 120, ('id', 2707481)),
    ("French Fries", FAST, BOWL, 117, ('q', 'potato french fries NFS')),
    ("Onion Rings", FAST, BOWL, 80, ('q', 'fried onion rings')),
    ("Chicken Nuggets (6 pc)", FAST, PIECE, 96, ('q', 'chicken nuggets NFS')),
    ("Fried Chicken (piece)", FAST, PIECE, 90, ('id', 170718)),
    ("Hot Dog", FAST, PIECE, 98, ('q', 'hot dog NFS')),
    ("Taco", FAST, PIECE, 100, ('q', 'taco beef')),
    ("Burrito", FAST, PIECE, 220, ('q', 'burrito bean')),
    ("Quesadilla", FAST, PIECE, 130, ('q', 'quesadilla NFS')),
    ("Sub Sandwich", FAST, PIECE, 220, ('q', 'submarine sandwich')),
    ("Wrap", FAST, PIECE, 180, ('q', 'sandwich wrap NFS')),
    ("Doughnut", FAST, PIECE, 60, ('q', 'doughnut NFS')),
    ("Milkshake", FAST, GLASS, 300, ('id', 2705509)),
    ("Nachos", FAST, BOWL, 110, ('q', 'nachos cheese')),

    # ------------------------------------------------------------------ Dairy & drinks
    ("Milk", DAIRY, GLASS, 250, ('id', 2705385)),
    ("Toned Milk", DAIRY, GLASS, 250, ('q', 'milk reduced fat')),
    ("Lassi (sweet)", DAIRY, GLASS, 250, ('mix', [(YOGURT, 170), (MILK, 55), (SUGAR, 18)])),
    ("Chaas", DAIRY, GLASS, 250, ('mix', [(YOGURT, 80), (('q', 'water'), 168)])),
    ("Nimbu Pani", DAIRY, GLASS, 250, ('mix', [(('q', 'lemon juice raw'), 20), (SUGAR, 16), (('q', 'water'), 214)])),
    ("Coconut Water", DAIRY, GLASS, 240, ('q', 'coconut water')),
    ("Fruit Juice", DAIRY, GLASS, 240, ('q', 'orange juice')),
    ("Cold Drink", DAIRY, GLASS, 250, ('q', 'soft drink cola')),
    ("Protein Shake", DAIRY, GLASS, 280, ('mix', [(('id', 2710745), 30), (MILK, 250)])),
    ("Water", DAIRY, GLASS, 250, ('q', 'water')),
    ("Tea", DAIRY, CUP, 150, ('mix', [(MILK, 60), (SUGAR, 8), (('q', 'water'), 82)])),
    ("Tea (no sugar)", DAIRY, CUP, 150, ('mix', [(MILK, 60), (('q', 'water'), 90)])),
    ("Green Tea", DAIRY, CUP, 150, ('q', 'tea green brewed')),
    ("Black Coffee", DAIRY, CUP, 150, ('q', 'coffee brewed')),
    ("Coffee with Milk", DAIRY, CUP, 150, ('mix', [(MILK, 60), (SUGAR, 6), (('q', 'coffee brewed'), 84)])),
    ("Curd", DAIRY, BOWL, 150, ('id', 2705418)),
    ("Dahi", DAIRY, BOWL, 150, ('id', 2705418)),
    ("Greek Yogurt", DAIRY, BOWL, 150, ('id', 2705422)),
    ("Paneer (50 g)", DAIRY, PIECE, 50, ('q', 'cheese paneer')),
    ("Cheese Slice", DAIRY, PIECE, 20, ('q', 'cheese american')),
    ("Butter", DAIRY, SPOON, 14, ('q', 'butter without salt')),
    ("Ghee", DAIRY, SPOON, 14, ('q', 'butter oil anhydrous')),
    ("Cooking Oil", DAIRY, SPOON, 14, ('id', 2710180)),
    ("Sugar", DAIRY, SPOON, 12, ('id', 2710258)),
    ("Honey", DAIRY, SPOON, 21, ('q', 'honey')),
    ("Jaggery", DAIRY, SPOON, 15, ('q', 'sugars brown')),

    # ------------------------------------------------------------------ Fruit & nuts
    ("Banana", FRUIT, PIECE, 118, ('q', 'bananas raw')),
    ("Apple", FRUIT, PIECE, 182, ('id', 171688)),
    ("Orange", FRUIT, PIECE, 131, ('q', 'oranges raw')),
    ("Mango", FRUIT, PIECE, 207, ('q', 'mangos raw')),
    ("Guava", FRUIT, PIECE, 55, ('q', 'guavas raw')),
    ("Chikoo", FRUIT, PIECE, 100, ('q', 'sapodilla raw')),
    ("Pear", FRUIT, PIECE, 178, ('q', 'pears raw')),
    ("Date", FRUIT, PIECE, 8, ('q', 'dates medjool')),
    ("Papaya", FRUIT, BOWL, 145, ('q', 'papayas raw')),
    ("Watermelon", FRUIT, BOWL, 152, ('q', 'watermelon raw')),
    ("Pomegranate", FRUIT, BOWL, 174, ('q', 'pomegranates raw')),
    ("Grapes", FRUIT, BOWL, 151, ('q', 'grapes raw')),
    ("Fruit Salad", FRUIT, BOWL, 150, ('q', 'fruit salad')),
    ("Almond", FRUIT, PIECE, 1.2, ('id', 2707485)),
    ("Cashew", FRUIT, PIECE, 1.6, ('q', 'cashew nuts raw')),
    ("Walnut", FRUIT, PIECE, 4, ('q', 'walnuts english')),
    ("Raisin (10 pc)", FRUIT, PIECE, 5, ('q', 'raisins seedless')),
    ("Pistachio", FRUIT, PIECE, 0.8, ('q', 'pistachio nuts raw')),

    # ------------------------------------------------------------------ Sweets
    ("Gulab Jamun", SWEET, PIECE, 45, ('mix', [(('q', 'milk dry whole'), 12), (SUGAR, 18), (OIL, 6), (('q', 'flour wheat white'), 5)])),
    ("Rasgulla", SWEET, PIECE, 50, ('mix', [(('q', 'cheese paneer'), 18), (SUGAR, 20)])),
    ("Jalebi", SWEET, PIECE, 35, ('mix', [(('q', 'flour wheat white'), 10), (SUGAR, 16), (OIL, 7)])),
    ("Laddu", SWEET, PIECE, 45, ('mix', [(BESAN, 16), (SUGAR, 14), (GHEE, 11)])),
    ("Barfi", SWEET, PIECE, 40, ('mix', [(('q', 'milk dry whole'), 14), (SUGAR, 15), (GHEE, 6)])),
    ("Gujiya", SWEET, PIECE, 45, ('mix', [(('q', 'flour wheat white'), 14), (SUGAR, 12), (GHEE, 8), (COCONUT, 6)])),
    ("Chocolate Bar", SWEET, PIECE, 45, ('q', 'chocolate milk candy')),
    ("Ice Cream Scoop", SWEET, PIECE, 66, ('q', 'ice cream vanilla')),
    ("Halwa", SWEET, BOWL, 100, ('mix', [(SEMOLINA, 30), (SUGAR, 25), (GHEE, 18)])),
    ("Kheer", SWEET, BOWL, 150, ('id', 2705685)),
    ("Shrikhand", SWEET, BOWL, 100, ('mix', [(YOGURT, 70), (SUGAR, 25)])),
    ("Gajar Halwa", SWEET, BOWL, 100, ('mix', [(('q', 'carrots cooked'), 55), (MILK, 20), (SUGAR, 15), (GHEE, 10)])),
    ("Rasmalai", SWEET, PIECE, 60, ('mix', [(('q', 'cheese paneer'), 20), (MILK, 25), (SUGAR, 14)])),
]
