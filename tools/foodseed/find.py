"""Search the flattened FNDDS index from the command line."""
import json, sys, io, os

HERE = os.path.dirname(os.path.abspath(__file__))
IDX = json.loads(io.open(os.path.join(HERE, 'nutrient_index.json'), encoding='utf-8').read())

def show(r):
    return "{id:<8} {kcal:>5.0f} {protein:>5.1f} {fiber:>5.1f} {vitA:>6.0f} {vitC:>6.1f} {iron:>5.1f} {ca:>6.0f}  {desc}".format(
        id=r['id'], kcal=r.get('kcal', 0), protein=r.get('protein', 0), fiber=r.get('fiber', 0),
        vitA=r.get('vitA', 0), vitC=r.get('vitC', 0), iron=r.get('iron', 0), ca=r.get('calcium', 0),
        desc=r['desc'][:78])

terms = [t.lower() for t in sys.argv[1:]]
limit = 14
print("{:<8} {:>5} {:>5} {:>5} {:>6} {:>6} {:>5} {:>6}  {}".format(
    'fdcId', 'kcal', 'prot', 'fib', 'vitA', 'vitC', 'iron', 'Ca', 'description (per 100 g)'))
hits = [r for r in IDX if all(t in r['desc'].lower() for t in terms)]
hits.sort(key=lambda r: len(r['desc']))
for r in hits[:limit]:
    print(show(r))
print("({} matches)".format(len(hits)))
