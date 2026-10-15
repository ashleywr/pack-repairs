"""Record fingerprints and surgical JSON edits from verified affected mod files.

Usage: python tools/generate_catalog.py --mods <mods-dir> --original-aeroforge <jar>
Upstream binaries and data are not redistributed in this project.
"""
import argparse
import hashlib
import json
import re
from pathlib import Path
from zipfile import ZipFile


def walk(value, path=""):
    yield path, value
    if isinstance(value, dict):
        for key, child in value.items():
            yield from walk(child, path + "/" + key.replace("~", "~0").replace("/", "~1"))
    elif isinstance(value, list):
        for index, child in enumerate(value):
            yield from walk(child, path + "/" + str(index))


def rename(path, to):
    return {"op": "rename", "path": path, "to": to}


def replace(path, value):
    return {"op": "replace", "path": path, "value": value}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--mods", type=Path, required=True)
    parser.add_argument("--original-aeroforge", type=Path, required=True)
    args = parser.parse_args()
    rules = []

    def record(jar, entry, repair_id, dependencies, operations, resource_id=None):
        with ZipFile(jar) as source:
            original = source.read(entry)
        if not operations:
            raise ValueError(f"No recognized defect in {entry}")
        _, namespace, resource = entry.split("/", 2)
        rules.append({"id": repair_id, "mods": dependencies, "resource": resource_id or namespace + ":" + resource,
                      "sha256": hashlib.sha256(original).hexdigest(), "operations": operations})

    def jar(pattern):
        matches = list(args.mods.glob(pattern))
        if len(matches) != 1:
            raise ValueError(f"Expected exactly one {pattern}: {matches}")
        return matches[0]

    with ZipFile(args.original_aeroforge) as source:
        for entry in source.namelist():
            if not entry.startswith("data/aeroforge/recipe/") or not entry.endswith(".json"):
                continue
            data = json.loads(source.read(entry))
            if data.get("type") != "minecraft:crafting_shaped":
                continue
            operations = [replace("/key/" + key, {"item": value}) for key, value in data["key"].items() if isinstance(value, str)]
            if operations:
                record(args.original_aeroforge, entry, "aeroforge.component_ingredients", ["aeroforge"], operations)

    p = jar("hexerei-0.5.0.3.jar")
    n = "data/hexerei/loot_table/blocks/willow_vines.json"
    with ZipFile(p) as source:
        data = json.loads(source.read(n))
    assert data["pools"][0]["conditions"][0] == {"action": "shears_dig", "condition": "forge:can_tool_perform_action"}
    record(p, n, "hexerei.willow_vines_loot", ["hexerei"], [
        replace("/pools/0/conditions/0/condition", "neoforge:can_item_perform_ability"),
        rename("/pools/0/conditions/0/action", "ability")])

    p = jar("ScorchedGuns-1.5.jar")
    n = "data/scguns/recipe/create/mechanical_crafting/jr_wristbreaker.json"
    with ZipFile(p) as source:
        data = json.loads(source.read(n))
    assert "C" in data["key"] and all("C" not in row for row in data["pattern"])
    record(p, n, "scguns.wristbreaker_unused_key", ["scguns", "create"], [{"op": "remove", "path": "/key/C"}])
    n = "data/scguns/loot_table/entities/blunderer.json"
    with ZipFile(p) as source:
        data = json.loads(source.read(n))
    operations = []
    for path, value in walk(data):
        if isinstance(value, dict) and value.get("function") == "minecraft:looting_enchant":
            operations.extend([replace(path + "/function", "minecraft:enchanted_count_increase"),
                               replace(path + "/enchantment", "minecraft:looting")])
    record(p, n, "scguns.blunderer_looting", ["scguns"], operations)

    p = jar("beautify-neoforge-1.21.1-2.0.2.jar")
    with ZipFile(p) as source:
        for n in source.namelist():
            if not n.startswith("data/beautify/loot_table/") or not n.endswith(".json"):
                continue
            data = json.loads(source.read(n))
            operations = [rename(path + "/name", "value") for path, value in walk(data)
                          if isinstance(value, dict) and value.get("type") == "minecraft:loot_table" and "name" in value and "value" not in value]
            if operations:
                record(p, n, "beautify.nested_loot_reference", ["beautify"], operations)

    p = jar("createdeco-2.1.3.jar")
    n = "data/createdeco/recipe/placard.json"
    with ZipFile(p) as source:
        data = json.loads(source.read(n))
    assert data["ingredients"][1] == {"id": "minecraft:white_dye"}
    record(p, n, "createdeco.placard_ingredient", ["createdeco", "create"], [rename("/ingredients/1/id", "item")])

    p = jar("endersdelight-1.3.1.jar")
    n = "data/endersdelight/recipe/chorus_pie_slice.json"
    with ZipFile(p) as source:
        data = json.loads(source.read(n))
    assert data["result"][0] == {"id": "endersdelight:chorus_pie_slice", "count": 4}
    assert data["tool"]["tag"] == "forge:tools/knives"
    record(p, n, "endersdelight.chorus_pie_cutting", ["endersdelight", "farmersdelight"], [
        replace("/result/0", {"item": data["result"][0]}), replace("/tool/tag", "c:tools/knife")])

    p = jar("neoforge-dungeonsdelight-1.21.1-1.5.1.jar")
    with ZipFile(p) as source:
        for n in source.namelist():
            if not n.startswith("data/dungeonsdelight/loot_modifiers/") or not n.endswith(".json"):
                continue
            data = json.loads(source.read(n))
            operations = []
            for path, value in walk(data):
                if isinstance(value, dict) and value.get("condition") == "minecraft:random_chance_with_looting":
                    assert set(value) == {"condition", "chance", "looting_multiplier"}
                    chance, bonus = value["chance"], value["looting_multiplier"]
                    operations.append(replace(path, {
                        "condition": "minecraft:random_chance_with_enchanted_bonus",
                        "enchantment": "minecraft:looting", "unenchanted_chance": chance,
                        "enchanted_chance": {"type": "minecraft:linear", "base": round(chance + bonus, 8), "per_level_above_first": bonus}}))
            if operations:
                record(p, n, "dungeonsdelight.looting_conditions", ["dungeonsdelight", "farmersdelight"], operations)

    p = jar("minersdelight-1.21.1-1.4.5.jar")
    n = "data/minersdelight/loot_modifiers/breaking_infested_blocks.json"
    with ZipFile(p) as source:
        data = json.loads(source.read(n))
    assert data["conditions"][2] == {"condition": "neoforge:can_tool_perform_action", "action": "pickaxe_dig"}
    record(p, n, "minersdelight.pickaxe_ability", ["minersdelight", "farmersdelight"], [
        replace("/conditions/2/condition", "neoforge:can_item_perform_ability"), rename("/conditions/2/action", "ability")])
    n = "data/minersdelight/loot_modifiers/scavenging_cooked_bat_wing_knifeless.json"
    with ZipFile(p) as source:
        data = json.loads(source.read(n))
        cooked = json.loads(source.read("data/minersdelight/loot_modifiers/scavenging_cooked_bat_wing.json"))
        assert data["item"] == "minersdelight:baked_bat_wing" and cooked["item"] == "minersdelight:smoked_bat_wing"
        assert "data/minersdelight/recipe/smoked_bat_wing.json" in source.namelist()
    record(p, n, "minersdelight.cooked_bat_wing", ["minersdelight", "farmersdelight"], [replace("/item", cooked["item"])])
    for pattern, namespace, names in [
        ("minersdelight-1.21.1-1.4.5.jar", "minersdelight", ["abandoned_mineshaft"]),
        ("MyNethersDelight-1.21.1-1.10.4.1.jar", "mynethersdelight", ["bastion_treasure", "bastion_hoglin_stable"])]:
        p = jar(pattern)
        for name in names:
            n = f"data/{namespace}/loot_modifiers/add_loot_{name}.json"
            with ZipFile(p) as source:
                data = json.loads(source.read(n))
            assert data["type"] == "farmersdelight:add_loot_table" and "lootTable" in data and "table" not in data
            record(p, n, namespace + ".chest_loot_table", [namespace, "farmersdelight"], [rename("/lootTable", "table")])

    # Move only the offending spawn entry; retain weights, group sizes and other entries.
    def spawn(p, entry, entity, old, new, repair_id, mod, resource_id=None):
        with ZipFile(p) as source:
            data = json.loads(re.sub(r"//[^\r\n]*", "", source.read(entry).decode()))
        original = data["spawners"][old]
        moved = [v for v in original if v["type"] == entity]
        assert len(moved) == 1 and not any(v["type"] == entity for v in data["spawners"].get(new, []))
        record(p, entry, repair_id, [mod], [
            replace("/spawners/" + old, [v for v in original if v["type"] != entity]),
            replace("/spawners/" + new, data["spawners"].get(new, []) + moved)], resource_id)

    p = jar("regions-unexplored-0.6.2-neoforge-21.1.jar")
    for name in ["eucalyptus_forest", "rainforest", "sparse_rainforest", "tropics"]:
        spawn(p, f"data/regions_unexplored/worldgen/biome/{name}.json", "minecraft:ocelot", "monster", "creature", "regions_unexplored.ocelot_category", "regions_unexplored")
    spawn(jar("YungsCaveBiomes-1.21.1-NeoForge-3.1.1.jar"), "data/yungscavebiomes/worldgen/biome/lost_caves.json",
          "yungscavebiomes:sand_snapper", "ambient", "monster", "yungscavebiomes.sand_snapper_category", "yungscavebiomes")
    p = jar("Rats Unofficial Port(Fixed)-1.21.1-8.2.3B01.jar")
    with ZipFile(p) as source:
        enum = next(v for v in json.loads(source.read("META-INF/enumextensions.json"))["entries"] if v["name"] == "RATS_RATS")
    assert enum["parameters"][0] == "rats:rats"
    spawn(p, "data/minecraft/datapacks/ratlantis/data/rats/worldgen/biome/ratlantis.json",
          "rats:rat", "creature", enum["parameters"][0], "rats.ratlantis_category", "rats", "rats:worldgen/biome/ratlantis.json")

    p = jar("letsdo-bloomingnature-neoforge-1.1.11.jar")
    n = "data/bloomingnature/worldgen/biome/highland_woods.json"
    with ZipFile(p) as source:
        data = json.loads(source.read(n))
    a, b = data["features"][9][2:4]
    assert (a, b) == ("bloomingnature:grass/mossgrass_patch_placed", "bloomingnature:grass/dense_wild_grass_patch_placed")
    record(p, n, "bloomingnature.grass_feature_order", ["bloomingnature"], [replace("/features/9/2", b), replace("/features/9/3", a)])

    guards = []
    with ZipFile(p) as source:
        for entry in source.namelist():
            if not entry.endswith(".json"):
                continue
            relevant = entry.startswith("data/bloomingnature/worldgen/biome/") or entry == "data/bloomingnature/tags/worldgen/biome/has_ground_litter.json"
            if entry.startswith("data/bloomingnature/neoforge/biome_modifier/"):
                relevant = json.loads(source.read(entry)).get("type") == "neoforge:add_features"
            if not relevant:
                continue
            raw = source.read(entry)
            _, namespace, resource = entry.split("/", 2)
            guard = {"resource": namespace + ":" + resource, "sha256": hashlib.sha256(raw).hexdigest()}
            if entry == n:
                repaired = json.loads(raw)
                repaired["features"][9][2:4] = [b, a]
                # Gson's compact serialization retains insertion order and uses no spaces.
                guard["repair_sha256"] = hashlib.sha256(json.dumps(repaired, separators=(",", ":")).encode()).hexdigest()
            guards.append(guard)
    guard_target = Path(__file__).resolve().parents[1] / "src/main/resources/packrepairs/bloomingnature-order-guards.json"
    guard_target.write_text(json.dumps(guards, indent=2) + "\n", encoding="utf-8")

    target = Path(__file__).resolve().parents[1] / "src/main/resources/packrepairs/catalog.json"
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(rules, indent=2) + "\n", encoding="utf-8")
    print(f"Recorded {len(rules)} resources across {len(set(r['id'] for r in rules))} repairs")


if __name__ == "__main__":
    main()
