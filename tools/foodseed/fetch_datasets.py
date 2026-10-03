# -*- coding: utf-8 -*-
"""
Download the two USDA FoodData Central datasets the catalogue is built from and flatten them
into nutrient_index.json.

    python fetch_datasets.py

Downloads ~17 MB and writes ~5 MB. Both the zips and the index are derived data and are
gitignored; this script is the thing that reproduces them.

FNDDS (survey foods) carries prepared, composite dishes - dosa, biryani, samosa, pizza -
which is what a meal tracker actually needs. SR Legacy carries the reference foods FNDDS
leaves out, chapati and most raw fruit among them.
"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))

DATASETS = [
    ("fndds.zip",
     "https://fdc.nal.usda.gov/fdc-datasets/FoodData_Central_survey_food_json_2024-10-31.zip"),
    ("srlegacy.zip",
     "https://fdc.nal.usda.gov/fdc-datasets/FoodData_Central_sr_legacy_food_json_2018-04.zip"),
]

try:
    from urllib.request import urlopen
except ImportError:  # python 2
    from urllib2 import urlopen


def fetch(name, url):
    path = os.path.join(HERE, name)
    if os.path.exists(path):
        print("%s already downloaded (%.1f MB)" % (name, os.path.getsize(path) / 1e6))
        return
    print("downloading %s ..." % name)
    data = urlopen(url).read()
    with open(path, 'wb') as f:
        f.write(data)
    print("  %.1f MB" % (len(data) / 1e6))


def main():
    for name, url in DATASETS:
        fetch(name, url)
    print("\nflattening ...")
    sys.path.insert(0, HERE)
    os.system('"%s" "%s"' % (sys.executable, os.path.join(HERE, 'build_index.py')))
    os.system('"%s" "%s"' % (sys.executable, os.path.join(HERE, 'add_srlegacy.py')))
    print("\nnow run:  python generate_seed.py "
          "../../app/src/main/java/com/lifetrack/app/data/FoodSeed.kt mapping_audit.txt")


if __name__ == '__main__':
    main()
