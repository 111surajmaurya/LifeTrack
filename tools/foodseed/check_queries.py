# -*- coding: utf-8 -*-
"""Report every catalogue query that finds no FDC match, all in one pass."""
import json, io, os, sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from catalogue import FOODS  # noqa: E402
from generate_seed import score, IDX, BY_ID  # noqa: E402


def queries_of(src):
    if src[0] == 'mix':
        for sub, _g in src[1]:
            for q in queries_of(sub):
                yield q
    else:
        yield src


bad, ok = [], 0
seen = set()
for name, cat, serving, grams, src in FOODS:
    for kind, val in queries_of(src):
        key = (kind, val)
        if key in seen:
            continue
        seen.add(key)
        if kind == 'id':
            if val not in BY_ID:
                bad.append((name, 'id', val))
            else:
                ok += 1
            continue
        terms = val.lower().split()
        best = max((score(r, terms) for r in IDX), default=0)
        if best <= 0:
            bad.append((name, 'q', val))
        else:
            ok += 1

print("resolved:", ok, " unresolved:", len(bad))
for name, kind, val in bad:
    print("  %-26s %s %r" % (name, kind, val))
