"""Record only fingerprints and repair operations; upstream fixtures stay outside the repo."""
import argparse
import hashlib
import json
import zipfile
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument('--mods', type=Path, required=True)
args = parser.parse_args()
rules = []

def rule(jar, entry, repair, mods, operations):
    with zipfile.ZipFile(args.mods / jar) as archive:
        raw = archive.read(entry)
    namespace, path = entry.removeprefix('data/').split('/', 1)
    rules.append(dict(id=repair, mods=mods, resource=namespace + ':' + path,
                      sha256=hashlib.sha256(raw).hexdigest(), operations=operations))

def add(path, value):
    return dict(op='add', path=path, value=value)

rule('extradelight-2.6.6.jar', 'data/minecraft/recipe/dough.json',
     'extradelight.disabled_dough_condition', ['extradelight'],
     [dict(op='replace', path='/neoforge:conditions/0/type', value='neoforge:false')])

obsolete = {
    'warphopper_fur_paper': 'netherexp:warphopper_fur',
    'cooking/iron_nugget_from_iron_scrap': 'netherexp:iron_scrap',
    'cooking/iron_nugget_from_iron_scrap_blasting': 'netherexp:iron_scrap',
    'igneous_reeds_orange_dye': 'netherexp:igneous_reeds',
    'stonecutting/from_pale_soul_slate/indented': 'netherexp:indented_soul_slate_tiles',
}
for recipe, item in obsolete.items():
    rule('Jadens-Nether-Expansion-2.4.1.jar', f'data/netherexp/recipe/{recipe}.json',
         'netherexp.removed_item_recipes', ['netherexp'],
         [add('/neoforge:conditions', [dict(type='neoforge:item_exists', item=item)])])
for color in ('red', 'blue'):
    rule('jadensnetherexpansiondelight-1.21.1-1.0.4a-neoforge.jar',
         f'data/jadensnetherexpansiondelight/recipe/{color}_scale_fungus_roll.json',
         'jadensnetherexpansiondelight.removed_fungus_recipes', ['jadensnetherexpansiondelight'],
         [add('/neoforge:conditions', [dict(type='neoforge:item_exists', item=f'netherexp:{color}_scale_fungus')])])

jar = 'sawmill-1.21-1.8.1-neoforge.jar'
with zipfile.ZipFile(args.mods / jar) as archive:
    entries = [name for name in archive.namelist() if name.endswith('.json') and
               name.startswith(('data/sawmill/rs_pool_additions/', 'data/sawmill/rs_pieces_spawn_counts/'))]
    assert len(entries) == 28
    for entry in sorted(entries):
        original = json.loads(archive.read(entry))
        assert original['fabric:load_conditions'] == [dict(condition='sawmill:flag', flag='rs_compat')]
        rule(jar, entry, 'sawmill.repurposed_structures_condition', ['sawmill@1.21-1.8.1'],
             [dict(op='resolve_fabric_flag', path='/fabric:load_conditions', flag='sawmill.rs_compat',
                   inactiveKey='elements' if '/rs_pool_additions/' in entry else 'pieces_spawn_counts')])

rule('hexerei-0.5.0.3.jar', 'data/minecraft/advancement/recipes/root.json',
     'hexerei.recipe_advancement_root', ['hexerei'], [
         dict(op='remove', path='/parent'),
         dict(op='replace', path='/criteria', value=dict(impossible=dict(trigger='minecraft:impossible'))),
         dict(op='replace', path='/requirements', value=[['impossible']]),
         dict(op='remove', path='/rewards')])
rule('Jadens-Nether-Expansion-2.4.1.jar',
     'data/netherexp/advancement/nether/soul_sand_valley/big_brain_time.json',
     'netherexp.brain_food_advancement_parent', ['netherexp'],
     [dict(op='replace', path='/parent', value='netherexp:nether/brain_food')])
assert len(rules) == 38
destination = Path(__file__).resolve().parents[1] / 'src/main/resources/packrepairs/recipe-compat-catalog.json'
destination.write_text(json.dumps(rules, indent=2) + '\n', encoding='utf8')
print(f'Recorded {len(rules)} recipe/compat/advancement repairs')
