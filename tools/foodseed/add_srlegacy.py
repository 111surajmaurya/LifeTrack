"""Fold SR Legacy into the same per-100g index, tagged so we know which source a number came from."""
import zipfile, json, io, os

HERE = os.path.dirname(os.path.abspath(__file__))

WANT = {
    1008: 'kcal', 1003: 'protein', 1079: 'fiber', 1106: 'vitA', 1162: 'vitC',
    1114: 'vitD', 1178: 'b12', 1089: 'iron', 1087: 'calcium', 1093: 'sodium',
    1004: 'fat', 1005: 'carbs',
}

z = zipfile.ZipFile(os.path.join(HERE, 'srlegacy.zip'))
name = z.namelist()[0]
data = json.loads(z.read(name).decode('utf-8'))
key = list(data.keys())[0]
foods = data[key]
print('sr legacy foods:', len(foods))

out = []
for f in foods:
    row = {'id': f['fdcId'], 'desc': f['description'], 'cat': (f.get('foodCategory') or {}).get('description', ''), 'src': 'SR'}
    for fn in f.get('foodNutrients', []):
        k = WANT.get(fn.get('nutrient', {}).get('id'))
        if k is not None and fn.get('amount') is not None:
            row[k] = fn['amount']
    row['portions'] = [
        {'g': p.get('gramWeight'), 'd': p.get('modifier') or p.get('portionDescription')}
        for p in f.get('foodPortions', [])
    ][:8]
    out.append(row)

# merge with the FNDDS index
idx_path = os.path.join(HERE, 'fndds_index.json')
fndds = json.loads(io.open(idx_path, encoding='utf-8').read())
for r in fndds:
    r['src'] = 'FNDDS'

merged = fndds + out
io.open(os.path.join(HERE, 'nutrient_index.json'), 'w', encoding='utf-8').write(json.dumps(merged))
print('merged foods:', len(merged))
print('size MB:', round(os.path.getsize(os.path.join(HERE, 'nutrient_index.json')) / 1e6, 1))
