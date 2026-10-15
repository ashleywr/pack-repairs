"""Fingerprint verified optional-integration tables; never copy upstream fixtures into source.

Run with --mods pointing at the exact affected JAR directory. Registry presence is
checked at resource-read time, so adding optional content restores its original loot.
"""
import argparse
import hashlib
import json
from pathlib import Path
from zipfile import ZipFile

from generate_catalog import walk, rename


COLORS = {"amber", "aqua", "beige", "coral", "forest", "ginger", "indigo", "maroon",
          "mint", "navy", "olive", "rose", "slate", "tan", "teal", "verdant"}
IDAS_TABLES = {
    "chests/archmages_tower/archmages_tower", "chests/archmages_tower/archmages_tower_library",
    "chests/archmages_tower/archmages_tower_treasure",
    "chests/enchantingtower/enchantingtower_basic_ars", "chests/enchantingtower/enchantingtower_top_ars",
}


def dyed_block(path):
    name = path.removeprefix("blocks/").removeprefix("solid_")
    return path.startswith("blocks/") and name.split("_", 1)[0] in COLORS


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--mods", type=Path, required=True)
    args = parser.parse_args()
    rules = []
    specifications = [
        ("Design-n-Decor-1.21.1-2.2b.jar", "dndecor", "dndecor", dyed_block, 256),
        ("create_connected-1.3.3-mc1.21.1.jar", "create_connected", "create_connected",
         lambda p: p.startswith("blocks/dye_depot_"), 16),
        ("createbuddycards-1.21.1-2.0.4.jar", "createbuddycards", "createbuddycards",
         lambda p: p == "blocks/playmat_create", 1),
        ("delightfulbuddycards-1.21.1-2.1.0.jar", "delightfulbuddycards", "delightfulbuddycards",
         lambda p: p == "blocks/playmat_delightful", 1),
        ("ExtendedAE-1.21-2.2.41-neoforge.jar", "extendedae", "extendedae",
         lambda p: p == "blocks/ex_emc_interface", 1),
        ("MineColonies_Compatibility-1.21.1-3.57.jar", "minecolonies_compatibility", "minecolonies_compatibility",
         lambda p: p == "blocks/citizen_grid", 1),
        ("creaturefeature-1.2.3.3.jar", "creaturefeature", "oreganized", lambda p: p.startswith("entities/extra_"), 3),
        ("creaturefeature-1.2.3.3.jar", "creaturefeature", "caverns_and_chasms", lambda p: p == "entities/extra_sinister", 1),
        ("idas-1.13.7+1.21.1-neoforge.jar", "idas", "idas", lambda p: p in IDAS_TABLES, 5),
    ]
    for filename, owner, namespace, selected, expected in specifications:
        count = 0
        with ZipFile(args.mods / filename) as source:
            prefix = f"data/{namespace}/loot_table/"
            for entry in sorted(source.namelist()):
                if not entry.startswith(prefix) or not entry.endswith(".json"):
                    continue
                path = entry[len(prefix):-5]
                if not selected(path):
                    continue
                raw = source.read(entry)
                data = json.loads(raw)
                operations = [{"op": "empty_if_missing_item", "path": location}
                              for location, value in walk(data)
                              if isinstance(value, dict) and value.get("type") == "minecraft:item"]
                assert operations, f"No known item-entry signature in {entry}"
                assert all(isinstance(value.get("name"), str) for _, value in walk(data)
                           if isinstance(value, dict) and value.get("type") == "minecraft:item")
                if data["type"] == "minecraft:block":
                    assert len(operations) == 1
                    assert data["pools"][0]["entries"][0]["name"] == namespace + ":" + path.removeprefix("blocks/")
                rules.append({"id": owner + ".optional_loot_items", "mods": [owner],
                              "resource": namespace + ":loot_table/" + path + ".json",
                              "sha256": hashlib.sha256(raw).hexdigest(), "operations": operations})
                count += 1
        assert count == expected, (filename, count, expected)

    # GenerateJewelryLootFunction uses TagKey.codec, not TagKey.hashedCodec.
    # Its map keys are bare tag IDs; material holder values keep their original form.
    with ZipFile(args.mods / "irons_jewelry-1.21.1-2.0.2.jar") as source:
        entry = "data/irons_jewelry/loot_table/generate_jewelry_test_materials.json"
        raw = source.read(entry)
        data = json.loads(raw)
        path = "/pools/0/entries/0/functions/0/materials"
        materials = data["pools"][0]["entries"][0]["functions"][0]["materials"]
        assert materials == {"#irons_jewelry:metal": "irons_jewelry:gold",
                             "#irons_jewelry:gem": ["irons_jewelry:ruby", "irons_jewelry:diamond"]}
        rules.append({"id": "irons_jewelry.material_filter_tags", "mods": ["irons_jewelry"],
                      "resource": "irons_jewelry:loot_table/generate_jewelry_test_materials.json",
                      "sha256": hashlib.sha256(raw).hexdigest(),
                      "operations": [rename(path + "/" + key, key[1:]) for key in materials]})
    target = Path(__file__).resolve().parents[1] / "src/main/resources/packrepairs/loot-catalog.json"
    target.write_text(json.dumps(rules, indent=2) + "\n", encoding="utf8")
    print(f"Recorded {len(rules)} verified loot tables")


if __name__ == "__main__":
    main()
