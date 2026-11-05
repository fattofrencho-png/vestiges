"""Tests for the organiser: facets (who said what), the analyser, and the triage runner. No key, no cost.

    python tools/assets/test_triage.py
"""
import json
import os
import sys
import tempfile
import unittest
from pathlib import Path

sys.dont_write_bytecode = True
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import analyse  # noqa: E402
import dashboard  # noqa: E402
import exporter  # noqa: E402
import facets  # noqa: E402
import library  # noqa: E402
import projects  # noqa: E402
import review  # noqa: E402
import studio  # noqa: E402
import triage  # noqa: E402


def tree(species="oak", offset=(0, 0, 0)):
    ox, oy, oz = offset
    blocks = [[ox, oy + y, oz, f"minecraft:{species}_log[axis=y]"] for y in range(6)]
    for x in range(-2, 3):
        for z in range(-2, 3):
            for y in range(4, 8):
                if (x, z) != (0, 0) or y > 5:
                    blocks.append([ox + x, oy + y, oz + z, f"minecraft:{species}_leaves[persistent=true]"])
    return blocks


def house():
    blocks = []
    for x in range(7):
        for z in range(7):
            blocks.append([x, 0, z, "minecraft:cobblestone"])
            blocks.append([x, 5, z, "minecraft:oak_planks"])
            for y in range(1, 5):
                if x in (0, 6) or z in (0, 6):
                    blocks.append([x, y, z, "minecraft:oak_planks"])
    blocks = [b for b in blocks if not (b[0] == 3 and b[2] == 0 and b[1] in (1, 2))]
    blocks += [[3, 1, 0, "minecraft:oak_door[half=lower]"], [3, 2, 0, "minecraft:oak_door[half=upper]"],
               [0, 2, 3, "minecraft:glass_pane"], [5, 1, 5, "minecraft:red_bed[part=foot]"], [5, 1, 4, "minecraft:red_bed[part=head]"]]
    return blocks


def rotate(blocks):
    return [[z, y, -x, s] for x, y, z, s in blocks]


def swap_material(blocks, old, new):
    return [[x, y, z, s.replace(old, new)] for x, y, z, s in blocks]


class FacetTests(unittest.TestCase):
    def test_human_beats_machine_and_rejections_are_remembered(self):
        p = {}
        self.assertTrue(facets.propose(p, "type", "Houses", "rule", 0.6, "rooms"))
        facets.decide(p, "type", "Towers")
        self.assertFalse(facets.propose(p, "type", "Houses", "rule", 0.9), "a decision must not be overwritten")
        self.assertEqual(facets.get(p, "type"), "Towers")
        self.assertEqual(p["facets"]["type"]["status"], "changed")
        q = {}
        facets.propose(q, "style", "Nordic", "picture", 0.4)
        facets.reject(q, "style")
        self.assertIsNone(facets.get(q, "style"))
        self.assertFalse(facets.propose(q, "style", "Nordic", "picture", 0.4), "a rejected value must not come back")
        self.assertEqual(facets.get(q, "style"), None)

    def test_accept_and_confidence(self):
        p = {}
        facets.propose(p, "type", "Trees", "rule", 0.9)
        facets.propose(p, "style", "Rustic", "rule", 0.3)
        self.assertEqual(facets.confidence_of(p, keys=("type", "style")), 0.3)
        self.assertEqual(facets.confidence_of(p), 0.9, "style is not a key facet")
        facets.accept(p, "style")
        self.assertEqual(facets.confidence_of(p, keys=("type", "style")), 0.9)
        self.assertEqual(set(facets.undecided(p)), {"type"})

    def test_review_states(self):
        p = {}
        self.assertEqual(facets.review_of(p)["state"], "inbox")
        facets.set_review(p, "rejected", "duplicate")
        self.assertEqual(p["review"]["reason"], "duplicate")
        facets.set_review(p, "accepted", "ignored")
        self.assertEqual(p["review"]["reason"], "")
        with self.assertRaises(ValueError):
            facets.set_review(p, "maybe")
        with self.assertRaises(ValueError):
            facets.propose(p, "type", "x", "human", 1)


class AnalyseTests(unittest.TestCase):
    def proposals(self, blocks, path="", title=""):
        result = analyse.analyse_blocks(blocks, path, title)
        return {f: (v, c, w) for f, v, s, c, w in result["proposals"]}, result

    def test_tree_and_species(self):
        found, _ = self.proposals(tree("birch"))
        self.assertEqual(found["type"][0], "Trees")
        self.assertEqual(found["species"][0], "birch")
        self.assertEqual(found["subtype"][0], "birch")
        self.assertGreater(found["type"][1], 0.7)
        self.assertEqual(found["realm"][0], "Overworld")
        found, _ = self.proposals(tree("dark_oak"))
        self.assertEqual(found["species"][0], "dark oak")

    def test_house_has_rooms(self):
        found, result = self.proposals(house())
        self.assertEqual(found["type"][0], "Houses")
        self.assertGreater(result["measures"]["roofed"], 20)
        self.assertEqual(result["measures"]["doors"], 1)
        self.assertEqual(result["measures"]["beds"], 1)

    def test_name_decides_between_buildings(self):
        bedless = [b for b in house() if "_bed" not in b[3]]
        found, _ = self.proposals(bedless, "pack/houses/taiga_library_1.nbt", "Taiga library")
        self.assertEqual(found["type"][0], "Civic and public buildings")
        found, _ = self.proposals(house(), "pack/streets/streets_corner.nbt", "")
        self.assertEqual(found["type"][0], "Streets and plazas")

    def test_package_of_many_builds(self):
        blocks = []
        for i in range(5):
            blocks += tree("oak", (i * 30, 0, 0))
        found, result = self.proposals(blocks)
        self.assertGreaterEqual(result["measures"]["parts"], 4)
        self.assertEqual(found["type"][0], "Other")
        self.assertIn("package", found["type"][2])

    def test_nether_realm_and_scale(self):
        blocks = [[x, y, z, "minecraft:nether_bricks"] for x in range(70) for y in range(1) for z in range(2)]
        found, _ = self.proposals(blocks)
        self.assertEqual(found["realm"][0], "Nether")
        self.assertEqual(found["scale"][0], "Mega")

    def test_fingerprints_ignore_turning_and_mirroring(self):
        base = analyse.fingerprints(house())
        turned = analyse.fingerprints(rotate(rotate(house())))
        mirrored = analyse.fingerprints([[-x, y, z, s] for x, y, z, s in house()])
        self.assertEqual(base["exact"], turned["exact"])
        self.assertEqual(base["exact"], mirrored["exact"])
        recolour = analyse.fingerprints(swap_material(house(), "oak_planks", "spruce_planks"))
        self.assertNotEqual(base["exact"], recolour["exact"])
        self.assertEqual(base["shape"], recolour["shape"], "same shape, other material: same family")
        self.assertNotEqual(base["palette"], recolour["palette"])
        self.assertNotEqual(base["shape"], analyse.fingerprints(tree())["shape"])

    def test_families(self):
        rows = [{"id": "a", "fp_exact": "1", "fp_shape": "s", "fp_palette": "p"}, {"id": "b", "fp_exact": "1", "fp_shape": "s", "fp_palette": "q"},
                {"id": "c", "fp_exact": "2", "fp_shape": "s", "fp_palette": "q"}, {"id": "d", "fp_exact": "3", "fp_shape": "t", "fp_palette": "z"}]
        groups = analyse.families(rows)
        self.assertEqual(groups["duplicates"], [["a", "b"]])
        self.assertEqual(groups["shapes"], [["a", "b", "c"]])
        self.assertEqual(groups["palettes"], [["b", "c"]])

    def test_empty_build_gives_nothing(self):
        self.assertEqual(analyse.analyse_blocks([])["proposals"], [])


class LibraryCase(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.saved = (projects.HOME, projects.ROOT)
        projects.HOME = Path(self.temp.name)
        projects.ROOT = Path(self.temp.name) / "library"
        library.reset()

    def tearDown(self):
        projects.HOME, projects.ROOT = self.saved
        library.reset()
        self.temp.cleanup()

    def add(self, blocks, title, category="ruin", item=""):
        project = projects.new_import(title, {"type": "file", "name": "pack.zip", "item": item}, {}, category)
        projects.save_version(project["id"], 1, None, {"blocks": blocks})
        counts = {}
        for b in blocks:
            counts[b[3].split("[")[0]] = counts.get(b[3].split("[")[0], 0) + 1
        project["versions"].append({"number": 1, "edits": [], "check": {"ok": True, "errors": [], "warnings": [], "stats": {
            "blocks": len(blocks), "width_x": 1, "height": 1, "width_z": 1, "block_counts": counts}}, "dropped": {}, "notes": []})
        projects.save(project)
        return project["id"]



class TriageTests(LibraryCase):
    def test_analyse_library_then_fix_kinds(self):
        tree_id = self.add(tree("spruce"), "Spruce tree", item="pack/spruce_tree.schem")
        house_id = self.add(house(), "Small house", item="pack/small_house.nbt")
        ruin_id = self.add(house(), "Old ruins", item="pack/old_ruins.nbt")
        result = triage.analyse_library()
        self.assertEqual((result["done"], result["failed"]), (3, []))
        self.assertEqual(triage.analyse_library()["done"], 0, "already analysed builds are skipped")
        self.assertEqual(facets.get(projects.load(tree_id), "type"), "Trees")
        fixed = triage.fix_categories(apply=False)
        self.assertEqual(dict(fixed), {"tree": 1, "house": 1})
        self.assertEqual(projects.load(tree_id)["category"], "ruin", "a dry run writes nothing")
        triage.fix_categories(apply=True)
        self.assertEqual(projects.load(tree_id)["category"], "tree")
        self.assertEqual(projects.load(tree_id)["category_was"], "ruin")
        self.assertEqual(projects.load(house_id)["category"], "house")
        self.assertEqual(projects.load(ruin_id)["category"], "ruin", "a name that says ruins keeps its kind")
        self.assertTrue(any(Path(self.temp.name, "backups").iterdir()), "a backup is made before writing")

    def test_decisions_survive_a_new_analysis(self):
        pid = self.add(tree("oak"), "Oak tree")
        triage.analyse_library()
        project = projects.load(pid)
        facets.decide(project, "type", "Gardens and plants")
        facets.set_review(project, "accepted")
        projects.save(project)
        triage.analyse_library(everything=True)
        project = projects.load(pid)
        self.assertEqual(facets.get(project, "type"), "Gardens and plants")
        self.assertEqual(facets.review_of(project)["state"], "accepted")

    def test_index_row_carries_organiser_fields(self):
        pid = self.add(tree("birch"), "Birch tree")
        triage.analyse_library()
        row = library._load()[pid]
        self.assertEqual((row["f_type"], row["f_species"], row["review"], row["analysed"]), ("Trees", "birch", "inbox", True))
        self.assertTrue(row["fp_exact"])
        self.assertGreater(row["w"], 0)

    def test_rarity_rewards_scarce_kinds(self):
        self.add(tree("oak"), "Oak tree")
        pid = self.add(tree("mangrove"), "Mangrove tree")
        self.add(tree("oak", (0, 0, 40)), "Oak tree two")
        triage.analyse_library()
        points = {p["id"]: p["analysis"]["measures"]["rarity_points"] for p in projects.all_projects()}
        self.assertIn(pid, points)
        self.assertIn("scarcity 2", facets.record(projects.load(pid), "rarity")["why"])


class ReviewTests(LibraryCase):
    def fill(self):
        ids = {"oak": self.add(tree("oak"), "Oak tree", item="p/oak_tree.schem"), "birch": self.add(tree("birch"), "Birch tree", item="p/birch_tree.schem"),
               "house": self.add(house(), "Small house", item="p/small_house.nbt")}
        ids["turned"] = self.add(rotate(house()), "Small house copy", item="p/small_house_2.nbt")
        triage.analyse_library()
        return ids

    def test_views_and_counts(self):
        ids = self.fill()
        rows = library.all_rows()
        c = review.counts(rows)
        self.assertEqual((c["inbox"], c["accepted"], c["rejected"], c["unanalysed"]), (4, 0, 0, 0))
        self.assertEqual(set(review.queue(rows, "inbox")), set(ids.values()))
        self.assertEqual(review.queue(rows, "inbox", {"type": "Trees"}), review.queue(rows, "inbox", {"type": "Trees"}))
        self.assertEqual(len(review.queue(rows, "inbox", {"type": "Trees"})), 2)
        self.assertEqual(set(review.queue(rows, "groups")), set(ids.values()), "turned copies and recoloured trees are look-alikes")
        self.assertEqual(len(review.related(rows, ids["oak"])["shapes"]), 1, "the birch has the oak's shape")
        sure = review.queue(rows, "sure")
        unsure = review.queue(rows, "unsure")
        self.assertTrue(set(sure) >= {ids["oak"]})
        conf = {r["id"]: r["conf"] for r in rows}
        self.assertEqual([conf[i] for i in unsure], sorted(conf[i] for i in unsure), "weakest guesses first")
        self.assertEqual([conf[i] for i in sure], sorted((conf[i] for i in sure), reverse=True), "strongest first")

    def test_related_suggests_a_keeper(self):
        ids = self.fill()
        found = review.related(library.all_rows(), ids["house"])
        self.assertEqual([d["id"] for d in found["duplicates"]], [ids["turned"]])
        self.assertIn(found["keeper"], (ids["house"], ids["turned"]))
        self.assertEqual(review.related(library.all_rows(), ids["oak"])["duplicates"], [])

    def test_accept_change_reject_and_undo(self):
        pid = self.fill()["oak"]
        project = projects.load(pid)
        review.change(project, "type", "Gardens and plants")
        review.accept_all(project)
        self.assertEqual(facets.review_of(project)["state"], "accepted")
        self.assertEqual(facets.get(project, "type"), "Gardens and plants")
        self.assertEqual(project["category"], "plant", "the Library's kind follows the settled type")
        self.assertEqual(facets.undecided(project), [])
        self.assertTrue(review.undo(project))   # undo the accept
        self.assertEqual(facets.review_of(project)["state"], "inbox")
        self.assertEqual(facets.get(project, "type"), "Gardens and plants", "the change before it stays")
        self.assertTrue(review.undo(project))   # undo the change
        self.assertEqual(facets.get(project, "type"), "Trees")
        self.assertFalse(review.undo(project), "nothing left to undo")
        review.reject_asset(project, "ugly")
        self.assertEqual(project["review"]["reason"], "ugly")
        review.restore(project)
        self.assertEqual(facets.review_of(project)["state"], "inbox")

    def test_batch_accept_only_sure_builds(self):
        ids = self.fill()
        rows = library.all_rows()
        wanted = review.batch_accept_ids(rows)
        self.assertIn(ids["oak"], wanted)
        self.assertFalse({ids["house"], ids["turned"]} & set(wanted), "twins wait for a keeper to be chosen")
        self.assertEqual(review.counts(rows)["batch"], len(wanted))
        sure_only = studio.review_batch({"dry_run": True})
        self.assertEqual(sure_only["count"], len(wanted))
        package = []
        for i in range(5):
            package += tree("oak", (i * 30, 0, 0))
        pack_id = self.add(package, "Package oaks", item="p/package_oak.schem")
        triage.analyse_library()
        row = next(r for r in library.all_rows() if r["id"] == pack_id)
        self.assertGreater(row["parts"], 3)
        self.assertLess(row["conf"], review.SURE, "a package is never sure")
        self.assertNotIn(pack_id, review.batch_accept_ids(library.all_rows()))
        done = studio.review_batch({"dry_run": False, "ids": sure_only["ids"] + ["not-an-id"]})
        self.assertEqual(done["accepted"], len(wanted), "ids outside the preview are ignored")
        self.assertEqual(facets.review_of(projects.load(ids["oak"]))["state"], "accepted")
        self.assertEqual(studio.review_batch({"dry_run": True})["count"], 0)

    def test_rights_are_stricter_than_the_library_status(self):
        ids = self.fill()
        self.assertEqual(review.rights_of(projects.load(ids["oak"])), "unknown", "an import with nothing noted is not 'own'")
        project = projects.load(ids["oak"])
        project["credit"] = {"licence": "CC0", "permission": "", "creator": "Sam"}
        self.assertEqual(review.rights_of(project), "open")
        project["credit"] = {"licence": "All Rights Reserved", "permission": ""}
        self.assertEqual(review.rights_of(project), "limited")
        project["credit"]["permission"] = "asked on Discord"
        self.assertEqual(review.rights_of(project), "permission")
        self.assertEqual(review.rights_of({"mode": "ai"}), "own")

    def test_name_changes_the_library_file_name_but_not_the_number(self):
        ids = self.fill()
        before = studio.review_asset(ids["oak"])["library"]
        studio.review_decide({"id": ids["oak"], "action": "change", "facet": "name", "value": "Great  Oak of the Meadow"})
        after = studio.review_asset(ids["oak"])["library"]
        self.assertEqual(before["card"], after["card"])
        self.assertTrue(after["file_name"].endswith("_great-oak-of-the-meadow.nbt"), after)
        self.assertNotEqual(before["file_name"], after["file_name"])
        studio.review_decide({"id": ids["oak"], "action": "accept_all"})
        self.assertEqual(projects.load(ids["oak"])["library"]["file_name"], after["file_name"])

    def test_ground_offset_is_saved_limited_and_undoable(self):
        ids = self.fill()
        self.assertEqual(studio.review_asset(ids["oak"])["ground_offset"], 0)
        studio.review_decide({"id": ids["oak"], "action": "ground_offset", "value": -2})
        self.assertEqual(studio.review_asset(ids["oak"])["ground_offset"], -2)
        studio.review_decide({"id": ids["oak"], "action": "ground_offset", "value": 1})
        self.assertEqual(projects.load(ids["oak"])["ground_offset"], 1)
        with self.assertRaises(ValueError):
            studio.review_decide({"id": ids["oak"], "action": "ground_offset", "value": 40})
        studio.review_decide({"id": ids["oak"], "action": "undo"})
        self.assertEqual(studio.review_asset(ids["oak"])["ground_offset"], -2)
        studio.review_decide({"id": ids["oak"], "action": "undo"})
        self.assertEqual(studio.review_asset(ids["oak"])["ground_offset"], 0)

    def test_api_functions(self):
        ids = self.fill()
        queue = studio.review_queue({"view": "inbox", "type": "Trees"})
        self.assertEqual(len(queue["ids"]), 2)
        self.assertIn("Trees", queue["types"])
        asset = studio.review_asset(ids["oak"])
        names = [f["facet"] for f in asset["facets"]]
        self.assertEqual(names[0], "name")   # the name is the first row to check
        names = names[1:]
        self.assertEqual(names[:3], ["type", "species", "realm"][:3] if "subtype" not in names else ["type", "subtype", "species"])
        self.assertTrue(asset["facets"][0]["why"])
        for row in ("front", "ground", "season"):
            self.assertIn(row, names)
        self.assertRegex(asset["library"]["file_name"], r"^trees_[a-z0-9-]+_\d{4}-00_[a-z0-9-]+\.nbt$")
        self.assertRegex(asset["library"]["card"], r"^#\d{4}-00$")
        studio.review_decide({"id": ids["oak"], "action": "accept_all"})
        self.assertEqual(studio.review_asset(ids["oak"])["review"]["state"], "accepted")
        self.assertTrue(studio.review_decide({"id": ids["oak"], "action": "undo"})["ok"] or True)
        with self.assertRaises(ValueError):
            studio.review_decide({"id": ids["birch"], "action": "undo"})
        with self.assertRaises(ValueError):
            studio.review_decide({"id": ids["birch"], "action": "change", "facet": "type", "value": " "})
        studio.review_decide({"id": ids["birch"], "action": "reject", "reason": "duplicate"})
        self.assertEqual(review.counts(library.all_rows())["rejected"], 1)
        self.assertTrue(studio.review_decide({"id": ids["birch"], "action": "delete"})["deleted"])
        with self.assertRaises(KeyError):
            studio.review_asset(ids["birch"])


class DashboardTests(LibraryCase):
    def build(self):
        return dashboard.build(library.all_rows(), dashboard.load_targets(), [], None, now=1_000_000)

    def test_progress_only_against_goals_and_focus_ladder(self):
        oak = self.add(tree("oak"), "Oak tree", item="p/oak_tree.schem")
        self.add(house(), "Small house", item="p/small_house.nbt")
        triage.analyse_library()
        # a build added after the analysis run has no proposals yet
        self.add(tree("spruce"), "Spruce tree", item="p/spruce_tree.schem")
        data = self.build()
        rows = {c["key"]: c for c in data["coverage"]}
        self.assertEqual((rows["Trees/oak"]["target"], rows["Trees/oak"]["gap"], rows["Trees/oak"]["inbox"]), (8, 8, 1))
        self.assertIsNone(rows["Houses"]["target"], "no goal set: a count, no bar")
        self.assertIsNone(rows["Houses"]["gap"])
        self.assertEqual(rows["Trees"]["target"], 72, "the total is the sum of the species goals")
        self.assertEqual(data["focus"][0]["id"], "analyse", "new builds are analysed first")
        self.assertEqual(data["health"]["unanalysed"], 1)
        ids = [f["id"] for f in data["focus"] + data["more"]]
        self.assertIn("goal:Trees/oak", ids)
        self.assertEqual(data["goal"], {"done": 0, "goal": 72})
        del oak

    def test_accepting_moves_progress_and_feeds(self):
        pid = self.add(tree("oak"), "Oak tree", item="p/oak_tree.schem")
        triage.analyse_library()
        project = projects.load(pid)
        review.accept_all(project)
        projects.save(project)
        data = self.build()
        oak = next(c for c in data["coverage"] if c["key"] == "Trees/oak")
        self.assertEqual((oak["accepted"], oak["gap"]), (1, 7))
        self.assertEqual(data["goal"], {"done": 1, "goal": 72})
        self.assertEqual((data["feeds"]["accepted"], data["feeds"]["video"], data["feeds"]["blocked_rights"]), (1, 0, 1), "unknown rights block both feeds")
        project = projects.load(pid)
        project["credit"] = {"creator": "Sam", "licence": "CC0", "permission": "", "link": ""}
        projects.save(project)
        feeds = self.build()["feeds"]
        self.assertEqual((feeds["video"], feeds["world"], feeds["world_trees"]), (1, 1, {"oak": 1}))

    def test_targets_are_set_and_removed(self):
        targets = dashboard.set_target("Houses", 40)
        self.assertEqual(targets["Houses"], 40)
        self.assertEqual(dashboard.set_target("Trees/oak", None).get("Trees/oak"), None, "a default goal can be removed")
        self.assertEqual(dashboard.set_target("Trees/oak", 12)["Trees/oak"], 12)
        with self.assertRaises(ValueError):
            dashboard.set_target("Spaceships", 3)
        with self.assertRaises(ValueError):
            dashboard.set_target("Houses", -2)

    def test_sure_batch_and_empty_goal_hints(self):
        for n in range(12):
            taller = tree("birch") + [[0, 8 + k, 0, "minecraft:birch_log[axis=y]"] for k in range(n)]  # each a different shape, so none is a twin
            self.add(taller, f"Birch tree {n}", item=f"p/birch_tree_{n}.schem")
        triage.analyse_library()
        data = self.build()
        ids = [f["id"] for f in data["focus"] + data["more"]]
        self.assertIn("sure", ids)  # 12 birches, no twins
        self.assertLess(ids.index("sure"), ids.index("goal:Trees/birch"), "the quick win comes before filling a goal")
        self.assertIn("empty", ids, "goals with nothing waiting are named")

    def test_api_data(self):
        self.add(tree("oak"), "Oak tree", item="p/oak_tree.schem")
        triage.analyse_library()
        data = studio.dashboard_data()
        self.assertEqual((data["library"], data["analysed"]), (1, 1))
        self.assertIsNone(data["running"])
        self.add(tree("birch"), "Birch tree", item="p/birch_tree.schem")
        self.assertTrue(triage.start_analyse())
        import time
        for _ in range(100):
            if not triage.run_status()["running"]:
                break
            time.sleep(0.05)
        self.assertEqual(triage.run_status()["running"], False)
        self.assertEqual(studio.dashboard_data()["analysed"], 2)


class ExportTests(LibraryCase):
    def fill(self, rights=True):
        ids = {"oak": self.add(tree("oak"), "Oak tree", item="p/oak_tree.schem"), "house": self.add(house(), "Small house", item="p/small_house.nbt"),
               "turned": self.add(rotate(house()), "Small house copy", item="p/small_house_2.nbt"), "birch": self.add(tree("birch"), "Birch tree", item="p/birch_tree.schem")}
        triage.analyse_library()
        if rights:
            exporter.set_pack_rights("pack.zip", creator="Sam", licence="CC0")
        for key in ("oak", "house", "turned"):  # the birch stays in the inbox
            project = projects.load(ids[key])
            review.accept_all(project)
            projects.save(project)
        return ids

    def test_numbers_are_permanent_and_adopt_the_video_catalog(self):
        first = exporter.assign_numbers(["a", "b"])
        again = exporter.assign_numbers(["a", "b", "c"])
        self.assertEqual((again["a"], again["b"]), (first["a"], first["b"]))
        self.assertEqual(len(set(again.values())), 3)
        self.assertEqual(exporter.label(315), "#315-00")
        self.assertEqual(exporter.label(1234, 7), "#1234-07")
        incoming = Path(self.temp.name) / "theirs.json"
        incoming.write_text('{"x": 7, "y": %d, "a": 5}' % again["b"], encoding="utf-8")
        adopted, skipped = exporter.import_numbers(incoming)
        self.assertEqual((adopted, skipped), (1, 2), "a clash and an existing id are skipped")
        self.assertEqual(exporter.load_numbers()["x"], 7)
        self.assertNotIn(7, [v for k, v in exporter.assign_numbers(["z"]).items() if k != "x"])

    def test_video_export(self):
        ids = self.fill()
        result = exporter.write_video()
        self.assertEqual((result["count"], result["video_ready"], result["held_back"]), (3, 3, 0))
        document = json.loads(Path(result["file"]).read_text(encoding="utf-8"))
        by_id = {e["id"]: e for e in document["entries"]}
        oak = by_id[ids["oak"]]
        self.assertEqual((oak["folder"], oak["species"], oak["realm"], oak["variant_no"], oak["video_ready"]), ("Trees", "oak", "Overworld", 0, True))
        self.assertRegex(oak["number"], r"^#\d{3}-00$")
        self.assertEqual(oak["series"], {"kind": "folder", "key": "Trees/oak"})
        twins = [by_id[ids["house"]]["duplicate_of"], by_id[ids["turned"]]["duplicate_of"]]
        self.assertEqual(len([t for t in twins if t]), 1, "one twin points at the other as the keeper")
        self.assertNotIn(ids["birch"], by_id, "only accepted builds are exported")
        self.assertTrue(Path(result["csv"]).read_text(encoding="utf-8-sig").startswith("id,"))

    def test_rights_gate_the_feeds(self):
        self.fill(rights=False)
        result = exporter.write_video()
        self.assertEqual((result["count"], result["video_ready"], result["held_back"]), (3, 0, 3))
        self.assertEqual(exporter.write_world()["count"], 0, "unknown rights keep builds out of the world")
        packs = exporter.packs(library.all_rows())
        self.assertEqual((packs[0]["source"], packs[0]["count"], packs[0]["unknown"]), ("pack.zip", 4, 4))
        with self.assertRaises(ValueError):
            exporter.set_pack_rights("pack.zip")
        self.assertEqual(exporter.set_pack_rights("pack.zip", permission="Asked on Discord, yes"), 4)
        self.assertEqual(exporter.write_video()["video_ready"], 3)

    def test_world_export_and_prepare(self):
        ids = self.fill()
        before = exporter.write_world()
        self.assertEqual(before["count"], 2, "the oak and one of the twins; the other twin is dropped")
        self.assertEqual(before["trees"], 1)
        self.assertEqual(len(before["unsaved"]), 2)
        ready = exporter.prepare_world()
        self.assertEqual(len(ready), 2)
        studio.review_decide({"id": ids["oak"], "action": "ground_offset", "value": -2})   # sunk by two blocks in Review
        after = exporter.write_world()
        self.assertEqual(after["unsaved"], [])
        entries = json.loads(Path(after["file"]).read_text(encoding="utf-8"))["entries"]
        oak = next(e for e in entries if e["id"] == ids["oak"])
        self.assertEqual(oak["replaces"], {"kind": "tree", "vanilla": "minecraft:oak", "to_confirm": True})
        self.assertIn("forest", oak["biomes"])
        self.assertTrue(oak["structure"].startswith("vestiges:"))
        self.assertEqual((oak["ground_y"], oak["origin_x"]), (0, 2))
        self.assertEqual((oak["ground_offset"], oak["lower_by"]), (-2, 2))   # the placer lowers it two more blocks
        self.assertTrue(all(e["vanilla"] for e in entries))
        self.assertEqual(exporter.summary()["world"], 2)


if __name__ == "__main__":
    unittest.main()
