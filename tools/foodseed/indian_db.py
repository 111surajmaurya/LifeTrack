"""
Turns Indian_Food_Nutrition_Database.xlsx into IndianFoodSeed.kt - more foods in the app's
existing format (kcal, protein, fibre, vitamin A, vitamin C, iron, calcium for one medium
serving, a Serving type and a category). Nothing about the app's format changes.

    python indian_db.py ../../Indian_Food_Nutrition_Database.xlsx \
        ../../app/src/main/java/com/lifetrack/app/data/IndianFoodSeed.kt indian_audit.txt

Standard library only: an .xlsx is a zip of XML.

Rules, in order:
  * A dish whose name or any "also known as" matches a food already in FoodSeed.kt is skipped,
    so the catalogue never shows the same thing twice and existing numbers stay put.
  * kcal / protein / fibre come from the workbook's Medium portion.
  * Vitamin A, vitamin C, iron and calcium come from the INDB recipe the workbook links, per
    100 g, scaled to the medium portion - only when that INDB recipe's energy is within 2x of the
    dish's, because the workbook itself flags INDB recipes that count all the frying oil or list
    a whole batch as a serving. Vitamin A is retinol + carotenoids / 12 (RAE). No link -> 0.
  * The medium description decides the Serving: "1 medium idli" is a piece, "2 tbsp" becomes per
    teaspoon, a katori is a bowl, ml drinks are cups/glasses, and so on.
  * The 32 meal combos & thalis come in as one serving each, from the workbook's TOTAL rows.
"""
import re
import sys
import zipfile
import xml.etree.ElementTree as ET

M = '{http://schemas.openxmlformats.org/spreadsheetml/2006/main}'

# Workbook category -> the app's category constant (FoodSeed / IndianFoodSeed).
CATEGORY = {
    'Hot Beverages': 'DAIRY', 'Cold Beverages & Sharbat': 'DAIRY', 'Fruit Juices': 'DAIRY',
    'Vegetable Juices': 'DAIRY', 'Lassi & Buttermilk': 'DAIRY', 'Milkshakes & Milk': 'DAIRY',
    'Breakfast - North & West': 'BREAKFAST', 'Breakfast - South': 'SOUTH',
    'Breads': 'ROTI', 'Rice, Pulao & Khichdi': 'RICE', 'Biryani': 'RICE',
    'Dal & Legumes': 'DAL', 'Veg Curries (Gravy)': 'DAL', 'Veg Dry Sabzi': 'SABJI',
    'Non-Veg Curries': 'NONVEG', 'Egg Dishes': 'NONVEG', 'Non-Veg Starters & Kebabs': 'NONVEG',
    'Veg Starters': 'SNACK', 'Snacks & Fritters': 'SNACK', 'Namkeen & Dry Snacks': 'SNACK',
    'Street Food & Chaat': 'CHAAT', 'Indo-Chinese': 'INDO_CHINESE', 'Soups': 'SOUP',
    'Raita, Salad & Curd': 'DAIRY', 'Chutneys, Pickles & Papad': 'OTHER',
    'Sweets (Mithai)': 'SWEET', 'Desserts (Kheer, Halwa, Ice Cream)': 'SWEET',
    'Basics & Add-ons': 'OTHER',
}


def read_book(path):
    z = zipfile.ZipFile(path)
    shared = [''.join(t.text or '' for t in si.iter(M + 't'))
              for si in ET.fromstring(z.read('xl/sharedStrings.xml')).findall(M + 'si')]
    book = ET.fromstring(z.read('xl/workbook.xml'))
    names = [s.get('name') for s in book.find(M + 'sheets')]

    def sheet(i):
        rows = []
        for r in ET.fromstring(z.read(f'xl/worksheets/sheet{i}.xml')).find(M + 'sheetData').findall(M + 'row'):
            row = {}
            for c in r.findall(M + 'c'):
                col = re.match(r'[A-Z]+', c.get('r')).group()
                v = c.find(M + 'v')
                if c.get('t') == 's' and v is not None:
                    row[col] = shared[int(v.text)]
                elif c.get('t') == 'inlineStr':
                    row[col] = ''.join(x.text or '' for x in c.iter(M + 't'))
                else:
                    row[col] = v.text if v is not None else None
            rows.append(row)
        return rows

    return {n: sheet(i) for i, n in enumerate(names, 1)}


def table(rows):
    """Header row -> list of dicts keyed by header text."""
    head = rows[0]
    return [{head[k]: v for k, v in r.items() if k in head} for r in rows[1:]]


def num(v):
    try:
        return float(v)
    except (TypeError, ValueError):
        return 0.0


def norm(s):
    s = re.sub(r'\(.*?\)', '', s.lower())
    s = s.replace('daal', 'dal').replace('sabji', 'sabzi').replace('channa', 'chana')
    return re.sub(r'[^a-z]', '', s)


def existing_names(seed_kt):
    return re.findall(r'^\s+\w+\("([^"]+)", \d+', open(seed_kt, encoding='utf-8').read(), re.M)


def serving_for(desc, unit, category):
    """(Serving, divisor) - divisor turns the medium figure into one unit of that serving."""
    d = desc.lower()
    m = re.match(r'(\d+) tbsp', d)
    if m:
        return 'Spoon', int(m.group(1)) * 3       # app's spoon unit is the teaspoon
    m = re.match(r'(\d+) tsp', d)
    if m:
        return 'Spoon', int(m.group(1))
    if re.match(r'1 (medium|leg|large|small)\b', d) or 'medium egg' in d:
        return 'Piece', 1
    if 'handful' in d:
        return 'Handful', 1
    if unit == 'ml':
        if category == 'Soups':
            return 'Bowl', 1
        return ('Cup', 1) if category == 'Hot Beverages' or 'cup' in d else ('Glass', 1)
    if 'plate' in d or 'pieces' in d or 'seekh' in d:
        return 'Plate', 1
    if 'katori' in d or 'bowl' in d or 'cup' in d:
        return 'Bowl', 1
    return 'Serve', 1


def kt_name(s):
    return s.replace('\\', '\\\\').replace('"', '\\"')


def main(xlsx, seed_kt, out_kt, audit_path):
    book = read_book(xlsx)
    dishes = table(book['Dish Database'])
    portions = table(book['Portion Nutrition'])
    indb = {r['INDB code']: r for r in table(book['INDB Full Nutrients']) if r.get('INDB code')}

    medium = {r['ID']: r for r in portions if r.get('Portion') == 'Medium'}
    taken = {norm(n) for n in existing_names(seed_kt)}

    def micros(dish, grams):
        """Vitamin A (ug RAE), vitamin C, iron, calcium (mg) for `grams` of `dish`, or zeros."""
        code = dish.get('INDB code')
        ref = indb.get(code) if code else None
        if not ref:
            return (0.0, 0.0, 0.0, 0.0), 'no INDB link'
        mine, theirs = num(dish.get('Energy (kcal) per 100')), num(ref.get('Energy (kcal)'))
        if mine <= 0 or theirs <= 0 or not (0.5 <= theirs / mine <= 2.0):
            return (0.0, 0.0, 0.0, 0.0), f'INDB {code} energy {theirs:.0f} vs {mine:.0f}/100 g - skipped'
        f = grams / 100.0
        vit_a = num(ref.get('Vitamin A (µg)')) + num(ref.get('Carotenoids (µg)')) / 12.0
        return (vit_a * f, num(ref.get('Vitamin C (mg)')) * f, num(ref.get('Iron (mg)')) * f,
                num(ref.get('Calcium (mg)')) * f), f'INDB {code}'

    items, audit, skipped = [], [], []
    by_id = {d['ID']: d for d in dishes}
    # "Dal makhani (restaurant)" and "Dal makhani (home, light)" are different numbers for the
    # same dish; when a dish has variants like that, keep them all rather than skip both.
    variants = {}
    for d in dishes:
        variants[norm(d['Dish'])] = variants.get(norm(d['Dish']), 0) + 1
    for d in dishes:
        name = d['Dish'].strip()
        if variants[norm(name)] > 1:
            keys = [re.sub(r'[^a-z]', '', name.lower())]
        else:
            keys = [norm(name)] + [norm(a) for a in (d.get('Also known as') or '').split(',') if a.strip()]
        if any(k and k in taken for k in keys):
            skipped.append(name)
            continue
        p = medium[d['ID']]
        serving, div = serving_for(p['Portion description'] or '', d['Unit'], d['Category'])
        grams = num(p['Quantity']) / div
        (a, c, fe, ca), note = micros(d, num(p['Quantity']))
        a, c, fe, ca = a / div, c / div, fe / div, ca / div
        kcal = round(num(p['Energy (kcal)']) / div)
        prot = num(p['Protein (g)']) / div
        fib = num(p['Fibre (g)']) / div
        items.append((name, kcal, prot, fib, a, c, fe, ca, serving, CATEGORY[d['Category']]))
        taken.add(norm(name) if variants[norm(name)] == 1 else re.sub(r'[^a-z]', '', name.lower()))
        audit.append(f"{d['ID']}  {name}  |  {serving} = {p['Portion description']} / {div}  "
                     f"({grams:.0f} {d['Unit']})  |  {kcal} kcal  |  {note}")

    # Meal combos: a title row, item rows with a Dish ID, then a TOTAL row.
    combos = book['Meal Combos & Thalis'][1:]
    title, parts = None, []
    for r in combos:
        if r.get('A') and not r.get('B'):
            title, parts = r['A'].strip(), []
        elif r.get('B') == 'TOTAL' and title:
            if norm(title) not in taken:
                a = c = fe = ca = 0.0
                for code, qty in parts:
                    dish = by_id.get(code)
                    if dish:
                        (pa, pc, pfe, pca), _ = micros(dish, qty)
                        a, c, fe, ca = a + pa, c + pc, fe + pfe, ca + pca
                items.append((title, round(num(r['E'])), num(r['F']), num(r['I']), a, c, fe, ca,
                              'Serve', 'THALI'))
                taken.add(norm(title))
                audit.append(f"COMBO  {title}  |  {r['D']} g total  |  {round(num(r['E']))} kcal  |  "
                             f"{len(parts)} parts")
            title = None
        elif r.get('C'):
            parts.append((r['C'], num(r.get('D'))))

    write_kotlin(out_kt, items)
    with open(audit_path, 'w', encoding='utf-8') as f:
        f.write(f"{len(items)} foods added, {len(skipped)} skipped as already in FoodSeed.kt\n\n")
        f.write('\n'.join(audit))
        f.write('\n\nSkipped (already in the catalogue):\n  ' + '\n  '.join(skipped) + '\n')
    print(f'{len(items)} foods written, {len(skipped)} skipped')


HEADER = '''package com.lifetrack.app.data

/**
 * More foods, in exactly the same shape as [FoodSeed]: one medium serving each, with calories,
 * protein, fibre, vitamin A, vitamin C, iron and calcium.
 *
 * **This file is generated** by `tools/foodseed/indian_db.py` from the Indian Food Nutrition
 * Database workbook (585 dishes + meal combos, built on IFCT 2017 / INDB 2024 / USDA FDC).
 * Dishes already in [FoodSeed] are left out. Vitamins and minerals come from the linked INDB
 * recipe when its energy agrees with the dish; otherwise they are 0 (unknown), as the audit
 * file `tools/foodseed/indian_audit.txt` records per food.
 *
 * Split into chunks because one list initialiser this long would exceed the JVM method limit.
 */
object IndianFoodSeed {

    /** Bumped whenever this list changes, so existing installs pick the change up. */
    const val VERSION = 1

    private fun f(n: String, k: Int, p: Float, fi: Float, a: Float, c: Float, fe: Float, ca: Float, s: Serving, cat: String) =
        FoodItem(
            name = n, kcal = k, protein = p, fiber = fi, vitA = a, vitC = c, iron = fe, calcium = ca,
            serving = s.name, category = cat
        )
'''


def write_kotlin(path, items):
    chunks = [items[i:i + 80] for i in range(0, len(items), 80)]
    out = [HEADER]
    out.append('    val items: List<FoodItem> by lazy { ' +
               ' + '.join(f'part{i}()' for i in range(len(chunks))) + ' }\n')
    for i, chunk in enumerate(chunks):
        out.append(f'\n    private fun part{i}() = listOf(')
        rows = []
        for n, k, p, fi, a, c, fe, ca, s, cat in chunk:
            const = cat if cat in ('BREAKFAST', 'INDO_CHINESE', 'SOUP', 'THALI') else f'FoodSeed.{cat}'
            rows.append(f'        f("{kt_name(n)}", {k}, {p:.1f}f, {fi:.1f}f, {a:.1f}f, {c:.1f}f, '
                        f'{fe:.1f}f, {ca:.1f}f, Serving.{s}, {const})')
        out.append(',\n'.join(rows))
        out.append('\n    )\n')
    out.append('''
    const val BREAKFAST = "Breakfast"
    const val INDO_CHINESE = "Indo-Chinese"
    const val SOUP = "Soups"
    const val THALI = "Meals & thalis"
}
''')
    with open(path, 'w', encoding='utf-8') as f:
        f.write(''.join(out))


if __name__ == '__main__':
    main(*sys.argv[1:5]) if len(sys.argv) == 5 else main(
        '../../Indian_Food_Nutrition_Database.xlsx',
        '../../app/src/main/java/com/lifetrack/app/data/FoodSeed.kt',
        '../../app/src/main/java/com/lifetrack/app/data/IndianFoodSeed.kt',
        'indian_audit.txt')
