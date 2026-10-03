# Indian food database — how to wire it into LifeTrack

File: `indian_foods.json` (239 foods, 12 categories). Put it at `app/src/main/assets/indian_foods.json`.

## Schema
```
foods[]:
  id         stable string key, e.g. "roti"
  name       display name
  aliases[]  extra search terms (hindi/regional spellings) — search must match name OR any alias, prefix + contains
  category   one of db.categories
  unit       piece | bowl | glass | cup | tbsp | plate | pack | handful | scoop | serving | thali
  servings[] {label, grams, kcal}  — 1 entry for "piece" foods; 2–3 sizes for everything else
```
Total for a logged item = servings[chosen].kcal × count.

## Room changes
- New table `foods` mirroring the schema (store servings as JSON string or a child table). Seed from the asset on first launch; `isCustom = false`.
- `meals` gets: `foodId` (nullable, for custom typed entries), `servingLabel`, `count`, `kcalPerUnit`. Keep `name` and total `kcal` so old rows still work.
- Custom foods the user creates → insert into `foods` with `isCustom = true`, `unit = "piece"` unless they pick sizes. They must appear in search like built-ins.
- Track `lastUsedAt` per food to power "Recent" quick-add chips.

## UI behaviour
- Tab is called **Calorie Tracker**.
- Search field at top; results update on every keystroke ("da" → Dal tadka, Daliya, Dahi, Dates…). Rank: name prefix > alias prefix > contains. Show category as a small label.
- Tapping a result opens a bottom sheet:
  - if unit == piece: name, kcal per piece, a − / + counter (default 1), live total, Add button
  - else: size chips (Small / Medium / Large etc. from servings), then the same counter and total
- Below search when it's empty: "Recent" chips (last 8 foods) that add with one tap (default size Medium / count 1), and a "+ Create food" row.
- Meal list rows show `2 × Roti · 200 kcal` or `Medium bowl Dal tadka · 150 kcal`; tap to edit count/size, swipe or × to delete.
- Optional: group by Breakfast / Lunch / Snacks / Dinner using time of day.

## Tests Claude Code should write first
- search("da") returns dal_tadka, daliya, curd (alias "dahi") and dates; search("anda") returns eggs.
- 3 × roti = 300; large bowl rice = 280; 2 × small bowl dal = 196 (2 × 98).
- creating custom food "Mom's kheer 180" then searching "mom" finds it.
- seeding runs once (second launch doesn't duplicate rows).
