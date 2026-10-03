"""Flatten the FNDDS survey dump into a compact per-100g index we can search offline."""
import zipfile, json, io, os

HERE = os.path.dirname(os.path.abspath(__file__))

# The nutrients LifeTrack cares about, by FDC nutrient id.
WANT = {
    1008: 'kcal',      # Energy, kcal
    1003: 'protein',   # g
    1079: 'fiber',     # Fiber, total dietary, g
    1106: 'vitA',      # Vitamin A, RAE, ug
    1162: 'vitC',      # Vitamin C, mg
    1114: 'vitD',      # Vitamin D (D2+D3), ug
    1178: 'b12',       # Vitamin B-12, ug
    1089: 'iron',      # mg
    1087: 'calcium',   # mg
    1093: 'sodium',    # mg
    1004: 'fat',       # g
    1005: 'carbs',     # g
}

z = zipfile.ZipFile(os.path.join(HERE, 'fndds.zip'))
data = json.loads(z.read('surveyDownload.json').decode('utf-8'))
foods = data['SurveyFoods']

out = []
for f in foods:
    row = {
        'id': f['fdcId'],
        'desc': f['description'],
        'cat': (f.get('wweiaFoodCategory') or {}).get('wweiaFoodCategoryDescription', ''),
    }
    for fn in f['foodNutrients']:
        key = WANT.get(fn['nutrient']['id'])
        if key is not None:
            row[key] = fn.get('amount', 0.0)
    # portions, so we can sanity-check gram weights against USDA's own
    row['portions'] = [
        {'g': p.get('gramWeight'), 'd': p.get('portionDescription') or p.get('modifier')}
        for p in f.get('foodPortions', [])
    ][:8]
    out.append(row)

path = os.path.join(HERE, 'fndds_index.json')
io.open(path, 'w', encoding='utf-8').write(json.dumps(out))
print('foods indexed:', len(out))
print('with kcal:', sum(1 for r in out if 'kcal' in r))
print('with fiber:', sum(1 for r in out if 'fiber' in r))
print('with vitA:', sum(1 for r in out if 'vitA' in r))
print('size MB:', round(os.path.getsize(path) / 1e6, 1))
