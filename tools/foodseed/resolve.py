"""Score FDC candidates for a batch of app foods so the mapping can be curated by eye."""
import json, io, os, sys

HERE = os.path.dirname(os.path.abspath(__file__))
IDX = json.loads(io.open(os.path.join(HERE, 'nutrient_index.json'), encoding='utf-8').read())
BY_ID = {r['id']: r for r in IDX}


def score(row, terms):
    d = row['desc'].lower()
    if not all(t in d for t in terms):
        return -1
    s = 100.0
    s -= len(d) * 0.25                      # shorter descriptions are the generic ones
    if row.get('src') == 'FNDDS':
        s += 12                             # composite dishes, and a 2024 release
    if ', nfs' in d:
        s += 8                              # "not further specified" = the generic entry
    if 'baby food' in d or 'infant' in d:
        s -= 60
    if 'restaurant' in d or 'fast food' in d:
        s -= 6
    return s


def best(terms, n=5):
    terms = [t.lower() for t in terms]
    ranked = sorted(((score(r, terms), r) for r in IDX), key=lambda x: -x[0])
    return [(s, r) for s, r in ranked[:n] if s > 0]


def line(r, per=100.0):
    f = per / 100.0
    return "{:<8} {:>5.0f}kcal {:>4.1f}p {:>4.1f}f {:>5.0f}A {:>5.1f}C {:>4.1f}Fe {:>5.0f}Ca  {} [{}]".format(
        r['id'], r.get('kcal', 0) * f, r.get('protein', 0) * f, r.get('fiber', 0) * f,
        r.get('vitA', 0) * f, r.get('vitC', 0) * f, r.get('iron', 0) * f, r.get('calcium', 0) * f,
        r['desc'][:62], r.get('src', '?'))


if __name__ == '__main__':
    # each arg is "AppName=term term term"
    for spec in sys.argv[1:]:
        app, _, q = spec.partition('=')
        print("### " + app)
        hits = best(q.split())
        if not hits:
            print("    NO MATCH")
        for s, r in hits:
            print("   " + line(r))
        print()
