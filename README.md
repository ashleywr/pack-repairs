# Pack Repairs

A standalone Minecraft **1.21.1 / NeoForge** mod for verified bugs across optional mods.
Install the JAR in `mods/`. It needs NeoForge 21.1.228 or newer, Java 21, and no supported mod is mandatory. There is no dependency on KubeJS, Moonlight, Create, or a datapack loader.

Use the same build on client and server for the Riverhawk gameplay repair. Existing worlds and entities are supported; data repairs also run on resource reload.

## How it selects repairs

Resource repairs inspect the resource selected by Minecraft's normal pack priority. A repair applies only if all relevant mods are present and the bytes match a recorded SHA-256 of the broken upstream resource. No files or upstream JARs are rewritten. Higher-priority datapacks retain their priority; a custom or already-fixed replacement is returned unchanged.

EveryCompat's generated recipes cannot use static file hashes. That repair instead requires the exact verified EveryCompat/Botany Pots versions, the generated hedge recipe naming pattern, and the complete known obsolete JSON schema. It preserves the chosen hedge, leaves, growing time and drop chance.

The Riverhawk code repair requires hashes of both original affected classes. It is skipped if either mod is absent, upstream code changes, or Aeroforge already has the local water-power patch. It affects Riverhawk only and retains the submerged engine penalty.

The Embers/Create repair checks three affected class hashes and adds forwarding methods through Mixin's extension SPI. It preserves an already-declared getter and leaves changed upstream classes alone.

Biome-order repairs affect future chunk generation. Existing chunks are not regenerated; validate terrain changes in a separate new test world before applying them to a valued world.

Repairs are reported in the normal log as `Applied <repair> to <resource> from <pack>`; unmodified content keeps Minecraft's usual diagnostics.

## Repairs added in 0.1.8

`rechanted.inherited_ticker_helper` repairs a collision between Rechanted 1.0.0
and Quark 4.1-487. Rechanted merges a private static `createTickerHelper` into
Minecraft's enchanting table class. That hides the protected helper inherited
from `BaseEntityBlock`, so Quark's Matrix Enchanting Table throws
`IllegalAccessError` when its block-entity ticker is registered during chunk
loading.

After matching both affected upstream class fingerprints, the repair gives the
merged Rechanted helper a private, namespaced name and updates its local call
sites. Its bytecode body, access flags, ticker type checks, and table behavior
remain unchanged. The protected inherited helper is then available to Quark
and other subclasses again. The merged-method origin, modifiers and body are
also checked before changing anything. Missing mods, changed source classes,
unexpected transformed methods and the disable switch skip the repair.

Restart clients and server after installation. Existing tables and saved chunks
are supported; no world reset or world-file rewrite is required.

Validation includes 34 unit tests, an isolated reproduction of the original
access error, native ticker/type checks, placement and saved-chunk reload of a
Matrix table, and clean startup/save/shutdown with neither optional mod present.

## Repairs added in 0.1.7

`create.deferred_advancement_items` repairs Create 6.0.10's early advancement
initialization. Its trigger-registry callback can run before Create items and
fluids are bound. Advancement definitions eagerly resolved item stacks, a
package style and the chocolate bucket during class initialization. Three
verified class fingerprints enable deferred icon and item-criterion resolution
through Create's existing registry-aware generation path. Built-in trigger
registration, advancement IDs and criterion names remain intact. Both special
icon arguments are deferred before entering the builder as well. Changed
classes, missing Create and the disable switch skip the code repair.

The template catalog contains 371 exact upstream fingerprints and 5,376 edits.
`structures.explicit_air_stacks` normalizes invalid `minecraft:air` stacks:
indexed lists retain their empty positions, sparse inventory entries with Slot
metadata are omitted, and absent second villager trade costs use an absent buyB
field. `structures.optional_template_items` omits the four known moose-rib
references only when that item is unregistered. `structures.optional_template_entities`
omits the 58 known guard-villager references only while that entity type is
unregistered. Restoring an integration restores its original template content
on resource reload. No replacement items or mobs are invented.

NBT rewrites stream untouched subtrees, keeping large templates out of a second
full object tree. Changes require the owning mod, matching complete compressed
resource bytes and an enabled repair. Higher-priority custom replacements,
unrecognized resources and changed upstream templates are left alone. Existing
world files and player inventories are never edited. The fixes prevent rejected
empty stacks and unavailable optional content in future template loads; no
terrain or structure layout is changed.

Validation includes 31 unit tests; all 371 upstream fixtures under absent,
restored, disabled and customized conditions; an independent streaming check
of palettes, blocks, coordinates and retained entity positions; and a dedicated
server with no repaired optional mods. A deterministic early-load probe
reproduces the original Create failure and passes after repair. All 102
generated Create advancements are JSON-identical to a late-initialized original
reference. Native template/item readers and migrated villager trades pass too.

## Repairs added in 0.1.6

Crafting Tweaks 21.1.11 encodes the craft-stack slot as a signed byte: a valid
Sophisticated Storage result slot such as 175 becomes -81 on the server.
`craftingtweaks.full_range_stack_slots` interprets legacy packets as unsigned,
adds an optional high-byte extension for slots above 255, and rejects negative
indices before calling menu validation. Existing one-byte packets retain their
format. Install this build on both client and server to use slots above 255;
the server alone also accepts existing clients' slots from 128 through 255.
The repair requires the original CraftStackMessage class fingerprint and the
complete verified codec/handler shape. Other packet types and normal crafting
behavior are unchanged.

`astralsorcery.legacy_focal_point_processor` restores the legacy
`astralsorcery:focal_point_register_processor` processor-list name referenced by
saved 2.0.1.20 structure pieces. With the verified 2.0.2.39 resource and processor
class, it exposes the original focal-point registration processor under its old
list name, retaining its constellation tag. Current templates continue using
their new list and marker processor.

`iceandfire.legacy_dread_banner_patterns` maps the two legacy saved banner names
`minecraft:iaf_dread` and `minecraft:iceandfire.dread` to the verified Ice & Fire
2.1.4 dread pattern. Both registry repairs supply a required datapack at the
bottom of pack priority. Custom definitions of those names win. Missing mods,
changed source resources and disabled repairs produce no fallback resources.
`structures.blank_sign_json` fixes empty strings used as JSON sign components
in Underground Worlds 3.0.2's tundra house/archive entrance and Witchery
0.5.17's warped coven templates. It changes only blank lines in four-line
front/back sign-text fields, after matching the complete template fingerprint.
Text, colors, formatting, block positions and other NBT are preserved. Custom
templates and Hexerei's unrelated twelve-line message format are untouched.

No saved world files are rewritten. A restart is required; no new world is
required for these packet and legacy-name repairs.

Validation for 0.1.6 includes 27 passing unit tests, a dedicated server with no
supported optional mods, native CraftStackMessage roundtrips through integer
slot limits, actual bulk crafting in Sophisticated Storage menus at slots 157
and 182, native decoding of the legacy structure element and banner patterns,
and a higher-priority alias override. All 50 sign sides decode with Minecraft's
SignText codec. A structural comparison confirms only 200 blank sign lines
change across the three templates; every other NBT value is preserved.

## Repairs added in 0.1.5

Buddycards 5.2.1 ships its rulebook in the removed pre-1.20 Patchouli format.
`buddycards.patchouli_rulebook_resources` enables resource-pack loading for the
verified internal `buddycards:buddycards_rules` declaration and exposes its two
categories and nine entries at their modern client asset paths. The contents
come directly from the installed Buddycards JAR; Pack Repairs ships only hashes.
The virtual pack is required at the bottom of resource-pack priority, allowing
ordinary mod assets and custom resource packs to override it.

The repair requires all twelve original JSON fingerprints and Patchouli 93's
verified BookRegistry class. Missing optional mods, changed declarations or pages,
existing migrated assets and external books are left alone. Disable the repair
in `config/packrepairs.json` before startup. Install 0.1.5 on both client and server
and restart each; the client supplies book contents. No new world is required.

## Repairs added in 0.1.4

Vinery 1.5.5 renamed `RecipeTypesRegistry` to `RecipeTypeRegistry`, relocated
`util.GrapeType` to `wine.GrapeType`, and renamed `GrapeBush` to `GrapeBushBlock`.
The recipe suppliers, grape accessors and bush AGE property used by MineColonies
Compatibility 3.57 retain compatible signatures. The add-on still references
the old classes in registration, grape harvesting and both apple-press teach-menu
validators, preventing startup. `minecolonies_compatibility.vinery_api_relocations`
remaps those three aliases in five verified callers, keeping field types,
constructor signatures and call sites consistent. It requires the hashes of all
five callers and four relevant Vinery classes, and the absence of every old
class. Missing mods, changed classes and existing upstream bridges disable the
repair. No upstream JAR is modified and no dependency becomes mandatory.

## Repairs added in 0.1.3

Eight fingerprinted recipe repairs correct Extra Delight's `neoforge:never`
condition to `neoforge:false` and add native item-presence conditions to seven
stale Nether Expansion / Nether Expansion Delight recipes. Recipes requiring
removed fur, scrap, reeds, tiles or scale fungi are skipped while those items
are absent; their original ingredients and outputs remain intact if the items
are restored. Pack Repairs does not invent replacement ingredients.

The 28 Sawmill / Repurposed Structures village files use a Fabric condition
that Sawmill's NeoForge implementation does not register. The repair reads the
original `RS_COMPAT` supplier only for the verified CommonConfigs class hash,
checks that Repurposed Structures is installed, and resolves the Fabric condition
without linking either optional mod. Disabled integrations retain no pool elements
or spawn limits even without a Fabric condition loader. Active house weights,
templates and processors are preserved. Unknown class signatures are left alone.

Hexerei 0.5.0.3 incorrectly replaces `minecraft:recipes/root` with a recipe
advancement whose parent is itself. Its fingerprinted repair restores the
parentless impossible-trigger root so recipe descendants can attach normally.
Nether Expansion 2.4.1's Big Brain Time advancement is pointed at the actual
`netherexp:nether/brain_food` parent, retaining its icon, criteria and rewards.

Repurposed Structures 7.5.22 overwrites all previous spawn-count entries when
another resource targets the same village. A class-fingerprint-guarded optional
mixin merges contributor lists so Sawmill, Iron's Spellbooks and Waystones retain
their separate limits. Existing last-contributor behavior for the same piece is
preserved. Disable this code repair with `repurposed_structures.merge_piece_limits`.

These 38 data repairs live in `recipe-compat-catalog.json`. Each group can be disabled:
`extradelight.disabled_dough_condition`, `netherexp.removed_item_recipes`,
`jadensnetherexpansiondelight.removed_fungus_recipes`,
`sawmill.repurposed_structures_condition`, `hexerei.recipe_advancement_root`,
and `netherexp.brain_food_advancement_parent`.

## Repairs added in 0.1.2

Version 0.1.2 adds 286 fingerprinted loot-table repairs. Optional item entries are
checked against Minecraft's item registry when resources are read. A missing item
becomes a weighted empty entry, retaining the original weight, quality, conditions,
functions, pool rolls and every installed item's entry. Adding the integration
restores the original resource on the next reload. Custom or updated resources
remain unchanged, and no supported mod becomes a required dependency.

| Repair ID | Verified affected content | Change |
|---|---|---|
| `dndecor.optional_loot_items` | Design n' Decor 2.2b, 256 Dye Depot color tables | Keep absent optional colored items from invalidating their tables. |
| `create_connected.optional_loot_items` | Create: Connected 1.3.3, 16 Dye Depot catalyst tables | Keep absent optional catalyst items from invalidating their tables. |
| `createbuddycards.optional_loot_items` | Create Buddycards 2.0.4, Create playmat table | Retain the drop when its item exists; otherwise use an empty entry. |
| `delightfulbuddycards.optional_loot_items` | Delightful Buddycards 2.1.0, Delightful playmat table | Retain the drop when its item exists; otherwise use an empty entry. |
| `extendedae.optional_loot_items` | ExtendedAE 2.2.41, EMC interface table | Retain the ProjectE integration drop when its item exists. |
| `minecolonies_compatibility.optional_loot_items` | MineColonies Compatibility 3.57, citizen grid table | Retain the optional integration drop when its item exists. |
| `creaturefeature.optional_loot_items` | Creature Feature 1.2.3.3, four Oreganized/Caverns and Chasms tables | Guard absent optional metal drops, preserving their original loot functions. |
| `idas.optional_loot_items` | IDAS 1.13.7, five Archmage/Enchanting Tower chest tables | Preserve installed loot and its original probabilities when optional Ars Nouveau or Quark items are missing. Missing rewards become no-drop selections rather than increasing other rewards' odds. |
| `irons_jewelry.material_filter_tags` | Irons Jewelry 2.0.2, material-filter test table | Remove `#` from material-filter map keys to match its `TagKey.codec`; retain gold/ruby/diamond choices, patterns and counts. |

These changes do not regenerate terrain. Existing worlds are supported. Restart
once after installing the new JAR; subsequent item-presence decisions run on data
reload. The native validation checks all 286 tables, a custom higher-priority
override, preserved installed entries and actual generated jewelry, plus startup
with no supported optional mods. Unit tests cover absent and restored integrations,
partial item availability, unchanged selection weights, disabled repairs, signature
drift and idempotence.

### Existing repairs

| Repair ID | Verified affected content | Change |
|---|---|---|
| `aeroforge.component_ingredients` | Aeroforge 1.0.2, 11 recipes | Convert string ingredients to 1.21.1 item ingredient objects. |
| `aeroforge.riverhawk_water_power` | Aeroforge 1.0.2 + Immersive Aircraft 1.5.2 | Normal engine power while floating; original penalty while submerged. |
| `hexerei.willow_vines_loot` | Hexerei 0.5.0.3 | Replace the obsolete Forge tool-action condition with the equivalent NeoForge item-ability condition. Modded shears remain supported. |
| `scguns.wristbreaker_unused_key` | Scorched Guns 1.5 + Create | Remove the unused `C` key that prevents the mechanical recipe loading. |
| `scguns.blunderer_looting` | Scorched Guns 1.5 | Update the obsolete looting function, keeping the original drop ranges. |
| `beautify.nested_loot_reference` | Beautify 2.0.2, 11 tables | Use the 1.21.1 `value` field for nested loot references. |
| `createdeco.placard_ingredient` | Create Deco 2.1.3 + Create | Correct the white-dye ingredient's `id` field to `item`. |
| `endersdelight.chorus_pie_cutting` | Ender's Delight 1.3.1 + Farmer's Delight | Correct the cutting result field and migrate the old knife tag to `c:tools/knife`. |
| `everycomp.botanypots_hedges` | EveryCompat 1.21-2.11.52 + Botany Pots 21.1.44 | Convert generated hedge crop recipes to the current input/display/drop/soil schema. |

| `dungeonsdelight.looting_conditions` | Dungeons Delight 1.5.1, seven modifiers | Migrate obsolete Looting conditions, preserving probabilities at levels 0–3. |
| `minersdelight.pickaxe_ability` | Miner's Delight 1.4.5 | Use the current NeoForge pickaxe item-ability condition. |
| `minersdelight.cooked_bat_wing` | Miner's Delight 1.4.5 | Point the knifeless cooked drop at the existing smoked bat wing. |
| `minersdelight.chest_loot_table` | Miner's Delight 1.4.5 | Correct the chest modifier's table field. |
| `mynethersdelight.chest_loot_table` | My Nether's Delight 1.10.4.1, two modifiers | Correct chest modifiers' table fields. |
| `regions_unexplored.ocelot_category` | Regions Unexplored 0.6.2, four biomes | Use the entity's creature spawn category, retaining weights and group sizes. |
| `yungscavebiomes.sand_snapper_category` | YUNG's Cave Biomes 3.1.1 | Use the entity's monster spawn category, retaining weight and group size. |
| `rats.ratlantis_category` | Rats Unofficial Port (Fixed) 8.2.3B01 | Use the rat's registered `rats:rats` category. |
| `bloomingnature.grass_feature_order` | Blooming Nature 1.1.11 | Align Highland Woods' two grass features with Larch Forest. |
| `bloomingnature.added_feature_order` | Blooming Nature 1.1.11 | After biome modifiers run, consistently order Blooming Nature features within their existing stages. Preserve unrelated order, counts, carvers and spawn settings. Skip this group if any relevant Blooming Nature resource has customized or changed bytes. |
| `embers.create_press_inherited_methods` | Embers Reignited 1.5.8 + Create 6.0.10 | Supply declared forwarding methods before Embers validates its inherited-method shadows, allowing its native Dawnstone Anvil press integration to load. |

All data repairs apply only to their recorded defective resources, even when the installed mod has the listed version. This is a catalog of established repairs, not a universal fixer for arbitrary errors. IDAS spawners and the ambiguous Complete Immersive Aircraft rotary-cannon definition are outside this release.

The Hexerei repair uses NeoForge's [item-ability loot condition](https://docs.neoforged.net/docs/1.21.1/resources/server/loottables/lootconditions/) to retain the original shearing behavior rather than limiting it to a particular item ID.

## Disable a repair

`config/packrepairs.json` is created automatically:

```json
{
  "disabledRepairs": ["hexerei.willow_vines_loot"]
}
```

Use `"*"` to disable all repairs. Restart after changing this file; code repair selection happens before mods load. A malformed config disables all repairs and emits a warning.

## Build and extend

```powershell
.\gradlew.bat build
```

The JAR is in `build/libs/`. Builds do not deploy it to any pack.

Validation includes 15 unit tests for presence, disable switches, signature drift, custom replacements, partial integrations, probability preservation and idempotence; isolated dedicated servers with the affected pack and with no optional mods; the 46 existing static data resources, 286 additional loot tables and 100 generated crop recipes; original Aeroforge crafting grids and five powered-engine controls; and coexistence with Synesthesia 2's existing Hexerei/Scorched Guns overrides and locally patched Aeroforge. The native checks also decode all 12 repaired loot modifiers, verify six spawn entries, complete a Create/Embers tool-repair operation without duplicate output, and independently check the affected overworld feature graph for cycles. Test worlds, raw upstream data and reports stay outside the source repository.

`src/main/resources/packrepairs/catalog.json` stores only fingerprints and small edits, not copies of upstream data. `tools/generate_catalog.py` can rebuild this catalog from the exact affected JARs; it validates expected defects before recording edits. New repairs need a concrete defect signature, optional dependency checks, a preservation/skip test, and a native loader check. Do not infer repairs from log wording alone.

`tools/generate_loot_catalog.py --mods <mods-directory>` rebuilds the 0.1.2
`loot-catalog.json` from the exact affected JAR versions. It verifies resource
counts and the original self-drop and material-filter structures before recording
fingerprints and conditional edits.

Minecraft versions and loaders require their own builds and validation. This build is specific to NeoForge 1.21.1.

Open Parties and Claims provider selection is a pack configuration fix, not a global mod repair: Magitema uses `permissionSystem = "permission_api"` and `primaryPartySystem = "default"`, matching its installed providers and previous fallback behavior. Login or vehicle-mount stalls require a gameplay profile and are not claimed as fixed by this mod.

<!-- ashley-release-information -->

## My builds

This is my original project. Original project code is offered under the [MIT License](LICENSE). Third-party material retains its own license and attribution.

Provided **AS IS**, without warranty of any kind. Maintenance, updates and support are best effort; compatibility with every modpack is not guaranteed.

### Downloads and installation

Download a runtime `.jar` from [GitHub Releases](https://github.com/ashleywr/pack-repairs/releases). Use the Minecraft version and loader named in that release and install its required dependencies. Do not install `-sources`, `-dev`, `-slim`, `-common` or test JARs as the mod.

Close Minecraft, back up your world, and place the matching runtime JAR in the instance's `mods/` directory. Replace older versions of this same mod rather than loading duplicates. For content mods, install matching versions on the server and its clients unless the release explicitly says client-only or server-only.

### Building and reporting problems

Use Java 21 and the checked-in Gradle wrapper:

```powershell
.\gradlew.bat build
```

Build outputs are under `build/libs/` or each loader subproject's `build/libs/`. Some projects require the local development dependencies described above; those dependencies are not included in the download.

For a problem report, include the exact release filename, Minecraft and loader versions, relevant mod versions, `logs/latest.log`, and a crash report if one was written. Remove private information before sharing logs. Remove the JAR to uninstall; worlds containing this mod's blocks, entities or world generation may require restoring a backup.
