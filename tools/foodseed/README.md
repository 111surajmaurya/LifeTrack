# Where the food numbers come from

`app/src/main/java/com/lifetrack/app/data/FoodSeed.kt` is **generated**. Nothing in it is
typed in by hand, and editing it directly will be overwritten the next time anyone runs the
generator.

## The sources

Both are [USDA FoodData Central](https://fdc.nal.usda.gov/), which publishes the underlying
laboratory analyses and is free to download in bulk.

| Dataset | Release | What it gives us |
| --- | --- | --- |
| **FNDDS** (Food and Nutrient Database for Dietary Studies) 2021–2023 | 2024-10-31 | Prepared, composite dishes — dosa, biryani, samosa, naan, pizza, cheeseburger. This is the one that matters: it has cooked food as eaten, not ingredients. |
| **SR Legacy** | 2018-04 | Reference foods FNDDS omits — chapati, raw fruit, flours, oils. |

**Why not IFCT?** The ICMR-NIN [Indian Food Composition Tables 2017](https://www.nin.res.in/ebooks/IFCT2017.pdf)
is the better source for Indian *ingredients* — 528 foods sampled across six regions. But it
covers raw foods only: there is no chapati in it, no dal, no curry. For a meal tracker the
composite dish is the unit, so FNDDS is the primary source and IFCT informs the serving sizes.

## Serving sizes

Nutrient data is published per 100 g; people eat katoris. Each food carries the gram weight of
one **medium** serving, and the app's portion chips scale it (small ×0.6, large ×1.5).

The standard ICMR-NIN reference katori is **150 ml / 150 g**, which is what dal, rice and gravy
dishes use here; dry sabji uses 100 g, a roti 40 g, a glass 250 ml. These match the app's
existing `Portion` factors, so a "large bowl" is ~225 g — a genuinely large helping.

## Dishes neither database has

Poha, lassi, bhel puri, kadhi, gulab jamun, halwa and about forty others appear in no
published composition table. Those are declared as a `mix` — a list of measured ingredients
and their grams — and the generator adds them up. That is the same method FNDDS itself uses,
and it beats inventing a number.

It is still an approximation of a *recipe*, not of your kitchen. Anyone who wants exact
figures should add a custom food.

## Regenerating

```bash
cd tools/foodseed
python fetch_datasets.py     # ~17 MB, writes nutrient_index.json
python check_queries.py      # every source resolves? run this after editing catalogue.py
python generate_seed.py ../../app/src/main/java/com/lifetrack/app/data/FoodSeed.kt mapping_audit.txt
```

Then bump `VERSION` in the generator's Kotlin template so existing installs refresh their
catalogue (`Repository.ensureSeeded` compares it against `Settings.seedVersion`), and run the
unit tests — `FoodSeedTest` will fail loudly if a figure has drifted somewhere impossible.

## Auditing

`mapping_audit.txt` lists every food, the serving weight used, the resulting numbers, and the
exact FDC entry (by id) each came from. It is regenerated alongside `FoodSeed.kt`, so it can
never drift out of step with the numbers actually shipping.

To look something up while curating:

```bash
python find.py paneer          # search descriptions
python resolve.py "Kheer=rice pudding"   # see what the scorer would pick, and why
```

## Files

| File | What it does |
| --- | --- |
| `catalogue.py` | **The input.** Every food, its category, serving, gram weight and FDC source. Edit this. |
| `generate_seed.py` | Resolves sources, does the arithmetic, writes `FoodSeed.kt` + the audit. |
| `fetch_datasets.py` | Downloads the USDA zips and builds the index. |
| `build_index.py` / `add_srlegacy.py` | Flatten each dataset into the shared per-100 g index. |
| `check_queries.py` | Fails fast on any source that no longer resolves. |
| `find.py` / `resolve.py` | Interactive helpers for curating `catalogue.py`. |
| `mapping_audit.txt` | Generated provenance for all 226 foods. |
