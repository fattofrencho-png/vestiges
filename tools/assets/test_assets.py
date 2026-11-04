"""Run with: python tools/assets/test_assets.py. Nothing here spends money or needs a key."""
import gzip
import io
import json
import os
import shutil
import struct
import sys
import tempfile
import time
import unittest
import zipfile
import zlib
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
TEMP = tempfile.mkdtemp(prefix="assets_test_")
os.environ["VESTIGES_ASSETS_HOME"] = TEMP
os.environ["VESTIGES_FAKE_AI"] = "1"

import edits
import backup
import batches
import explore
import export
import files
import import_formats
import library
import licences
import mc_assets
import naming
import online
import organise
import thumbs
import pipeline
import plan_tools
import presets
import projects
import send
import providers
import validate

projects.ROOT = Path(TEMP) / "projects"
library.reset()
pipeline.VIEWS_TIMEOUT = 0.2
LOG = "minecraft:oak_log[axis=y]"
JAR = mc_assets.Jar()


def tearDownModule():
    shutil.rmtree(TEMP, ignore_errors=True)


def leaf(distance):
    return f"minecraft:oak_leaves[distance={distance},persistent=false,waterlogged=false]"


def op(tool, **args):
    return {"tool": tool, "args_json": json.dumps(args)}


def build(*ops):
    return plan_tools.run_plan({"seed": 3, "ops": list(ops)}, JAR)


def states(tree):
    return {(x, y, z): s for x, y, z, s in tree["blocks"]}


class PlanTools(unittest.TestCase):
    def test_same_plan_gives_same_blocks(self):
        plan = op("blob", center=[0, 5, 0], radii=[3, 3, 3], blocks=[["oak_leaves", 1], ["moss_block", 1]], density=0.6)
        self.assertEqual(build(plan), build(plan))

    def test_organic_tree_small_and_large_pass_checks(self):
        for height, radius, branches in ((6, 0.5, 2), (14, 1, 5), (26, 3.5, 13)):
            tree = build(op("organic_tree", trunk_height=height, trunk_radius=radius, branches=branches, branch_length=height / 3, seed=height))
            self.assertEqual(validate.check(tree, JAR.block_ids())["errors"], [], f"height {height}")

    def test_line_sets_log_direction(self):
        tree = states(build(op("line", **{"from": [0, 0, 0], "to": [5, 0, 0], "blocks": "oak_log"})))
        self.assertEqual(tree[(3, 0, 0)], "minecraft:oak_log[axis=x]")

    def test_unknown_block_is_refused(self):
        with self.assertRaisesRegex(plan_tools.PlanError, "not a block"):
            build(op("set_block", pos=[0, 0, 0], block="diamond_tree"))

    def test_bad_arguments_name_the_op(self):
        with self.assertRaisesRegex(plan_tools.PlanError, "Op 1"):
            build(op("column", x="left", z=0, from_y=0, to_y=1, blocks="stone"))
        with self.assertRaisesRegex(plan_tools.PlanError, "not valid JSON"):
            plan_tools.run_plan({"ops": [{"tool": "column", "args_json": "{oops"}]}, JAR)
        with self.assertRaisesRegex(plan_tools.PlanError, "not a tool"):
            plan_tools.run_plan({"ops": [{"tool": "exec", "args_json": "{}"}]}, JAR)

    def test_too_big_is_refused(self):
        with self.assertRaisesRegex(plan_tools.PlanError, "outside"):
            build(op("set_block", pos=[500, 0, 0], block="stone"))

    def test_carve_removes(self):
        tree = states(build(op("blob", center=[0, 3, 0], radii=[3, 3, 3], blocks="stone"), op("carve", center=[0, 3, 0], radii=[1, 1, 1])))
        self.assertNotIn((0, 3, 0), tree)

    def test_leaves_without_logs_become_permanent(self):
        tree = build(op("column", x=0, z=0, from_y=0, to_y=4, blocks="cobblestone_wall"),
                     op("blob", center=[0, 6, 0], radii=[1, 1, 1], blocks="oak_leaves"))
        leaves = [s for s in states(tree).values() if "leaves" in s]
        self.assertTrue(leaves and all("persistent=true" in s for s in leaves))
        result = validate.check(tree, JAR.block_ids())
        self.assertEqual(result["errors"], [])
        self.assertTrue(any("permanent" in w for w in result["warnings"]))

    def test_leaves_near_logs_decay_normally(self):
        tree = states(build(op("column", x=0, z=0, from_y=0, to_y=4, blocks="oak_log"), op("set_block", pos=[1, 4, 0], block="oak_leaves"), op("set_block", pos=[2, 4, 0], block="oak_leaves")))
        self.assertEqual(tree[(2, 4, 0)], leaf(2))

    def test_walls_join_and_draw(self):
        tree = states(build(op("line", **{"from": [0, 0, 0], "to": [2, 0, 0], "blocks": "cobblestone_wall"})))
        middle = tree[(1, 0, 0)]
        self.assertIn("east=low", middle)
        self.assertIn("west=low", middle)
        self.assertIn("up=false", middle)
        self.assertIn("up=true", tree[(0, 0, 0)])
        self.assertTrue(JAR.bake(middle)["quads"])

    def test_wall_column_keeps_posts(self):
        tree = states(build(op("column", x=0, z=0, from_y=0, to_y=2, blocks="cobblestone_wall")))
        self.assertIn("up=true", tree[(0, 1, 0)])
        self.assertTrue(JAR.bake(tree[(0, 1, 0)])["quads"])

    def test_fence_joins(self):
        tree = states(build(op("line", **{"from": [0, 0, 0], "to": [1, 0, 0], "blocks": "oak_fence"})))
        self.assertIn("east=true", tree[(0, 0, 0)])
        self.assertTrue(JAR.bake(tree[(0, 0, 0)])["quads"])

    def test_every_default_state_can_be_drawn(self):
        tree = build(op("set_block", pos=[0, 0, 0], block="oak_slab"), op("set_block", pos=[1, 0, 0], block="oak_stairs"),
                     op("set_block", pos=[2, 0, 0], block="moss_block"), op("set_block", pos=[3, 0, 0], block="fern"))
        for state in states(tree).values():
            JAR.bake(state)

    def test_hanging_vine_touches_its_block(self):
        tree = states(build(op("set_block", pos=[0, 5, 0], block="moss_block"),
                            op("hanging", **{"from": ["moss_block"], "blocks": "vine", "chance": 1, "min_length": 2, "max_length": 2})))
        vines = [(p, s) for p, s in tree.items() if "vine" in s]
        self.assertEqual(len(vines), 2)
        self.assertTrue(JAR.bake(vines[0][1])["quads"])


class Checks(unittest.TestCase):
    def test_good_tree(self):
        self.assertEqual(validate.check({"blocks": [[0, 0, 0, LOG], [0, 1, 0, LOG], [1, 1, 0, leaf(1)], [2, 1, 0, leaf(2)]]})["errors"], [])

    def test_catches_floating(self):
        self.assertTrue(any("floating" in e for e in validate.check({"blocks": [[0, 0, 0, LOG], [0, 5, 0, LOG]]})["errors"]))

    def test_catches_far_leaves(self):
        blocks = [[0, 0, 0, LOG]] + [[i, 0, 0, leaf(min(i, 7))] for i in range(1, 9)]
        self.assertTrue(any("decay" in e for e in validate.check({"blocks": blocks})["errors"]))

    def test_catches_wrong_distance(self):
        self.assertTrue(any("distance" in e for e in validate.check({"blocks": [[0, 0, 0, LOG], [1, 0, 0, leaf(4)]]})["errors"]))

    def test_catches_unknown_block(self):
        self.assertTrue(any("Not vanilla" in e for e in validate.check({"blocks": [[0, 0, 0, "mymod:thing"]]}, {"minecraft:oak_log"})["errors"]))

    def test_warns_about_barriers_and_width(self):
        blocks = [[x, 0, 0, LOG] for x in range(40)] + [[0, 1, 0, "minecraft:barrier"]]
        words = " ".join(validate.check({"blocks": blocks})["warnings"])
        self.assertIn("wide", words)
        self.assertIn("barriers", words)

    def test_brief_limits(self):
        result = validate.check({"blocks": [[0, y, 0, LOG] for y in range(10)]})
        self.assertEqual(validate.check_brief(result, {"min_height": 8, "max_height": 10, "max_width": 1}), [])
        failures = validate.check_brief(result, {"max_height": 5, "min_width": 3, "required_blocks": ["moss_block"], "forbidden_blocks": ["oak_log"]})
        self.assertEqual(len(failures), 4)

    def test_roots_dont_count_towards_height(self):
        result = validate.check({"blocks": [[0, -3, 0, LOG], [0, 0, 0, LOG], [0, 1, 0, LOG]]})
        self.assertEqual(result["stats"]["height"], 2)
        self.assertEqual(result["stats"]["roots_depth"], 3)


class Files(unittest.TestCase):
    def test_structure_reads_back(self):
        tree = {"blocks": [[-2, -1, 3, LOG], [-2, 0, 3, LOG], [-1, 0, 3, leaf(1)]]}
        structure, placement = export.to_structure(tree, 5023)
        back = export.read_nbt(export.write_nbt(structure))
        self.assertEqual(back, structure)
        self.assertEqual(back["size"], [2, 2, 1])
        self.assertEqual(placement["ground_y"], 1)
        self.assertEqual(back["palette"][1]["properties"]["distance"], "1")

    def test_palette_uses_the_same_keys_as_vanilla_files(self):
        """The game silently reads unknown keys as air, so this guards against the format changing."""
        common = Path.home() / ".gradle/caches/fabric-loom/26.3/minecraft-common.jar"
        raw = gzip.decompress(zipfile.ZipFile(common).read("data/minecraft/structure/village/plains/houses/plains_small_house_1.nbt"))
        ours = gzip.decompress(export.write_nbt(export.to_structure({"blocks": [[0, 0, 0, LOG]]}, 5023)[0]))
        for key in (bytes([0, 2]) + b"id", bytes([0, 10]) + b"properties", bytes([0, 7]) + b"palette", bytes([0, 6]) + b"blocks"):
            self.assertIn(key, raw)
            self.assertIn(key, ours)

    def test_schem_reads_back(self):
        tree = {"blocks": [[-1, -1, 2, LOG], [0, 0, 2, leaf(1)], [0, 0, 3, LOG]]}
        back = export.read_nbt(export.write_nbt(export.to_schem(tree, 5023)))["Schematic"]
        self.assertEqual(back["Version"], 3)
        self.assertEqual((back["Width"], back["Height"], back["Length"]), (2, 2, 2))
        self.assertEqual(back["Offset"], (-1, -1, 2))
        palette = back["Blocks"]["Palette"]
        self.assertEqual(palette["minecraft:air"], 0)
        data = list(back["Blocks"]["Data"])
        self.assertEqual(len(data), 8)
        self.assertEqual(data[0], palette[LOG])
        self.assertEqual(data[1 + 0 * 2 + 1 * 4], palette[leaf(1)])  # x + z*width + y*width*length

    def test_varint_above_127(self):
        self.assertEqual(export._varint(300), bytes([0xAC, 0x02]))


class Look(unittest.TestCase):
    def test_sideways_log_has_end_facing_east(self):
        end = next(q for q in JAR.bake("minecraft:oak_log[axis=x]")["quads"] if q["n"] == [1, 0, 0])
        self.assertEqual(end["tex"], "block/oak_log_top")

    def test_leaves_tinted(self):
        baked = JAR.bake(leaf(1))
        self.assertEqual((baked["tint"], baked["kind"]), ("foliage", "leaves"))

    def test_barrier_is_invisible_and_moss_is_solid(self):
        self.assertEqual(JAR.bake("minecraft:barrier")["kind"], "invisible")
        self.assertEqual(JAR.bake("minecraft:moss_block")["kind"], "solid")
        self.assertNotEqual(JAR.bake("minecraft:glass")["kind"], "solid")


def wait(project_id):
    for _ in range(600):
        project = projects.load(project_id)
        if not project["busy"]:
            return project
        time.sleep(0.02)
    raise AssertionError("step never finished")


class Spender:
    """A test AI whose every call costs a set amount."""
    def __init__(self, cost):
        self.cost = cost
        self.calls = 0

    def complete(self, *args):
        self.calls += 1
        answer, _ = providers.TestAI().complete(*args)
        return answer, providers.Usage(10, 10, self.cost)


class Flow(unittest.TestCase):
    def setUp(self):
        providers.save_settings({"send_images": False})

    def run_to_brief(self, request="a small tree"):
        project_id = pipeline.start(request, "test", "", 1.0)
        project = wait(project_id)
        self.assertEqual(project["stage"], "questions")
        self.assertEqual(len(project["questions"]), 4)
        pipeline.answers(project_id, {"leaves": ["Oak"], "height": [12, 15]})
        project = wait(project_id)
        self.assertEqual(project["stage"], "brief")
        return project_id

    def test_whole_way_with_change_and_save(self):
        project_id = self.run_to_brief()
        pipeline.confirm_brief(project_id)
        project = wait(project_id)
        self.assertEqual(project["stage"], "preview", project["error"])
        self.assertEqual(project["versions"][0]["number"], 1)
        pipeline.request_change(project_id, "more moss")
        project = wait(project_id)
        self.assertEqual([v["number"] for v in project["versions"]], [1, 2])
        self.assertEqual(project["versions"][1]["change"], "more moss")
        pipeline.agree(project_id, 2)
        project = projects.load(project_id)
        self.assertEqual((project["stage"], project["saved_version"]), ("saved", 2))
        self.assertTrue(projects.load_version(project_id, 2)["blocks"])

    def test_failed_measurement_tries_again(self):
        project_id = self.run_to_brief("failplan")
        pipeline.confirm_brief(project_id)
        project = wait(project_id)
        self.assertEqual(project["stage"], "preview", project["error"])
        self.assertTrue(any("don't match" in line for line in project["progress"]))

    def test_gives_up_after_three_and_asks(self):
        project_id = self.run_to_brief("alwaysfail")
        pipeline.confirm_brief(project_id)
        project = wait(project_id)
        self.assertEqual(project["stage"], "questions")
        self.assertEqual(project["versions"], [])
        self.assertEqual(project["questions"][0]["id"], "more_details")
        self.assertIn("flat", project["questions"][0]["help"])

    def test_review_can_ask_the_author(self):
        project_id = self.run_to_brief("askuser")
        pipeline.confirm_brief(project_id)
        project = wait(project_id)
        self.assertEqual(project["questions"][0]["id"], "lean")
        pipeline.answers(project_id, {"lean": "Left"})
        project = wait(project_id)
        self.assertEqual(project["stage"], "brief")
        self.assertEqual(project["answers"]["leaves"], ["Oak"])  # the first answers stay
        pipeline.confirm_brief(project_id)
        self.assertEqual(wait(project_id)["stage"], "preview")

    def test_spending_limit_stops_and_can_continue(self):
        spender = Spender(0.6)
        real = providers.get
        providers.get = lambda *a: spender
        try:
            project_id = pipeline.start("a small tree", "test", "", 1.0)
            wait(project_id)
            pipeline.answers(project_id, {})
            wait(project_id)
            calls = spender.calls
            pipeline.confirm_brief(project_id)
            project = wait(project_id)
            self.assertEqual(project["error"]["kind"], "budget")
            self.assertEqual(spender.calls, calls)  # nothing was spent past the limit
            pipeline.resume(project_id, 5)
            self.assertEqual(wait(project_id)["stage"], "preview")
        finally:
            providers.get = real

    def test_provider_failure_can_be_retried(self):
        class Broken:
            def complete(self, *args):
                raise providers.ProviderError("Couldn't reach Claude.")
        real = providers.get
        providers.get = lambda *a: Broken()
        try:
            project_id = pipeline.start("a tree", "test", "", 1.0)
            self.assertIn("reach", wait(project_id)["error"]["message"])
        finally:
            providers.get = real
        pipeline.resume(project_id)
        project = wait(project_id)
        self.assertIsNone(project["error"])
        self.assertEqual(len(project["questions"]), 4)

    def test_bad_question_from_ai_is_cleaned(self):
        cleaned = pipeline._clean_questions([{"id": "a", "text": "A?", "kind": "select", "options": []}, {"id": "a", "text": "again"}, {"text": "no id"}, "junk"])
        self.assertEqual(len(cleaned), 1)
        self.assertEqual(cleaned[0]["kind"], "text")

    def test_bad_ids_cant_leave_the_folder(self):
        with self.assertRaises(KeyError):
            projects.load("../../secrets")


class ClaudeCall(unittest.TestCase):
    """Runs the real SDK against a local stand-in for Claude's server, so the request and reply handling are tested for free."""

    def test_request_and_reply(self):
        from http.server import BaseHTTPRequestHandler, HTTPServer
        import threading
        seen = {}
        events = [
            ("message_start", {"type": "message_start", "message": {"id": "m", "type": "message", "role": "assistant", "model": "x", "content": [], "stop_reason": None, "stop_sequence": None,
                                                                    "usage": {"input_tokens": 1000, "output_tokens": 0, "cache_read_input_tokens": 0, "cache_creation_input_tokens": 0}}}),
            ("content_block_start", {"type": "content_block_start", "index": 0, "content_block": {"type": "text", "text": ""}}),
            ("content_block_delta", {"type": "content_block_delta", "index": 0, "delta": {"type": "text_delta", "text": "{\"ok\": true}"}}),
            ("content_block_stop", {"type": "content_block_stop", "index": 0}),
            ("message_delta", {"type": "message_delta", "delta": {"stop_reason": "end_turn", "stop_sequence": None}, "usage": {"output_tokens": 1000}}),
            ("message_stop", {"type": "message_stop"}),
        ]

        class Stub(BaseHTTPRequestHandler):
            def log_message(self, *args):
                pass

            def do_POST(self):
                seen["body"] = json.loads(self.rfile.read(int(self.headers["Content-Length"])))
                seen["key"] = self.headers.get("authorization")
                seen["beta"] = self.headers.get("anthropic-beta")
                body = "".join(f"event: {n}\ndata: {json.dumps(d)}\n\n" for n, d in events).encode()
                self.send_response(200)
                self.send_header("Content-Type", "text/event-stream")
                self.send_header("Content-Length", str(len(body)))
                self.end_headers()
                self.wfile.write(body)

        server = HTTPServer(("127.0.0.1", 0), Stub)
        threading.Thread(target=server.serve_forever, daemon=True).start()
        os.environ["VESTIGES_CLAUDE_URL"] = f"http://127.0.0.1:{server.server_port}"
        providers.set_key("claude", "test-key")
        try:
            answer, used = providers.Claude("claude-opus-5-5").complete("plan", "sys", "hello", providers.OK_SCHEMA, ["QUJD"])
        finally:
            del os.environ["VESTIGES_CLAUDE_URL"]
            providers.set_key("claude", "")
            server.shutdown()
        self.assertEqual(answer, {"ok": True})
        self.assertAlmostEqual(used.cost, (1000 * 4 + 1000 * 20) / 1_000_000)
        self.assertEqual(seen["key"], "Bearer test-key")  # how SDK 2.x sends every API key
        self.assertEqual(seen["body"]["model"], "claude-opus-5-5")
        self.assertEqual(seen["body"]["fallbacks"], "default")
        self.assertIn("server-side-fallback-2026-07-01", seen["beta"])
        self.assertEqual(seen["body"]["output_config"]["effort"], "high")
        self.assertEqual(seen["body"]["output_config"]["format"]["type"], "json_schema")
        self.assertEqual(seen["body"]["messages"][0]["content"][0]["type"], "image")


def litematic(palette, size, values, position=(0, 0, 0)):
    return export.write_nbt({"MinecraftDataVersion": 5023, "Regions": {"a": {
        "BlockStatePalette": palette, "BlockStates": export.LongArray(values),
        "Size": {"x": size[0], "y": size[1], "z": size[2]}, "Position": {"x": position[0], "y": position[1], "z": position[2]},
        "TileEntities": [{"id": "chest"}], "Entities": []}}})


class Importing(unittest.TestCase):
    def cells(self, result):
        return {(x, y, z): s for x, y, z, s in result["blocks"]}

    def test_schem_v3_round_trip(self):
        tree = {"blocks": [[0, 0, 0, LOG], [0, 1, 0, LOG], [2, 1, 1, leaf(1)]]}
        result = import_formats.read_build(export.write_nbt(export.to_schem(tree, 5023)))
        self.assertEqual(len(result["blocks"]), 3)
        self.assertEqual(min(b[1] for b in result["blocks"]), 0)
        self.assertIn(LOG, {b[3] for b in result["blocks"]})

    def test_schem_v2_layout(self):
        raw = export.write_nbt({"Version": 2, "DataVersion": 5023, "Width": export.Short(1), "Height": export.Short(2), "Length": export.Short(1),
                                "Palette": {"minecraft:air": 0, "minecraft:stone": 1}, "BlockData": bytes([1, 0]), "BlockEntities": []})
        self.assertEqual([b[3] for b in import_formats.read_build(raw)["blocks"]], ["minecraft:stone"])

    def test_old_schematic_is_refused_with_advice(self):
        raw = export.write_nbt({"Width": export.Short(1), "Height": export.Short(1), "Length": export.Short(1), "Materials": "Alpha", "Blocks": bytes([1]), "Data": bytes([0])})
        with self.assertRaisesRegex(import_formats.ImportProblem, "WorldEdit"):
            import_formats.read_build(raw)

    def test_structure_old_and_new_keys(self):
        for name, props in (("id", "properties"), ("Name", "Properties")):
            raw = export.write_nbt({"size": [1, 1, 1], "entities": [], "blocks": [{"pos": [0, 0, 0], "state": 0}], "palette": [{name: "minecraft:oak_log", props: {"axis": "x"}}]})
            self.assertEqual(import_formats.read_build(raw)["blocks"][0][3], "minecraft:oak_log[axis=x]")

    def test_real_vanilla_house_from_a_jar(self):
        common = (Path.home() / ".gradle/caches/fabric-loom/26.3/minecraft-common.jar").read_bytes()
        items = import_formats.scan("game.jar", common)
        house = next(i for i in items if "plains_small_house_1" in i["id"])
        result = import_formats.read_item(common, house["id"])
        self.assertEqual(len(result["blocks"]), 153)
        check = validate.check(result, JAR.block_ids(), lenient=True)
        self.assertEqual(check["errors"], [])

    def test_litematic_packing_crosses_values(self):
        # 3 bits per block, so block 21 crosses from the first 64-bit value into the second.
        palette = [{"Name": "minecraft:air"}, {"Name": "minecraft:stone"}, {"Name": "minecraft:dirt"}, {"Name": "minecraft:sand"}, {"Name": "minecraft:gravel"}]
        numbers = [(i % 4) + 1 for i in range(27)]
        packed = 0
        for i, n in enumerate(numbers):
            packed |= n << (i * 3)
        first = packed & 0xFFFFFFFFFFFFFFFF
        second = packed >> 64
        signed = lambda v: v - (1 << 64) if v >= 1 << 63 else v
        result = import_formats.read_build(litematic(palette, (3, 3, 3), [signed(first), signed(second)]))
        names = {1: "stone", 2: "dirt", 3: "sand", 4: "gravel"}
        cells = self.cells(result)
        low_x = min(p[0] for p in cells)
        low_z = min(p[2] for p in cells)
        for i, n in enumerate(numbers):
            x, z, y = i % 3, (i // 3) % 3, i // 9
            self.assertEqual(cells[(low_x + x, y, low_z + z)], "minecraft:" + names[n])
        self.assertEqual(result["dropped"]["block_data"], 1)

    def test_litematic_negative_size(self):
        palette = [{"Name": "minecraft:air"}, {"Name": "minecraft:stone"}]
        result = import_formats.read_build(litematic(palette, (-2, 1, 1), [0b0101], (10, 0, 0)))
        self.assertEqual(len(result["blocks"]), 2)

    def test_junk_and_empty_are_refused(self):
        with self.assertRaises(import_formats.ImportProblem):
            import_formats.read_build(b"this is not minecraft")
        raw = export.write_nbt({"size": [1, 1, 1], "blocks": [{"pos": [0, 0, 0], "state": 0}], "palette": [{"id": "minecraft:air"}]})
        with self.assertRaisesRegex(import_formats.ImportProblem, "only air"):
            import_formats.read_build(raw)

    def test_archive_lists_only_structures(self):
        store = io.BytesIO()
        with zipfile.ZipFile(store, "w") as archive:
            archive.writestr("data/pack/structure/a/b.nbt", export.write_nbt({"size": [1, 1, 1], "blocks": [{"pos": [0, 0, 0], "state": 0}], "palette": [{"id": "minecraft:stone"}]}))
            archive.writestr("data/pack/loot_table/x.json", "{}")
            archive.writestr("readme.txt", "hi")
        items = import_formats.scan("pack.zip", store.getvalue())
        self.assertEqual([i["label"] for i in items], ["pack: a/b"])
        self.assertEqual(import_formats.read_item(store.getvalue(), items[0]["id"])["blocks"][0][3], "minecraft:stone")

    def test_world_box_from_region_file(self):
        folder = Path(TEMP) / "world"
        region = folder / "dimensions/minecraft/overworld/region"
        region.mkdir(parents=True, exist_ok=True)
        (folder / "level.dat").write_bytes(b"x")
        section = {"Y": 5, "block_states": {"palette": [{"": "minecraft:air"}, {"": "minecraft:oak_log[axis=y]"}, {"Name": "minecraft:stone"}], "data": export.LongArray([0x21] + [0] * 255)}}
        chunk = zlib.compress(gzip.decompress(export.write_nbt({"DataVersion": 5023, "sections": [section], "block_entities": []})))
        body = struct.pack(">IB", len(chunk) + 1, 2) + chunk
        (region / "r.0.0.mca").write_bytes(bytes([0, 0, 2, 1]) + bytes(8188) + body + bytes(4096 - len(body) % 4096))
        result = import_formats.read_world(folder, "overworld", [0, 80, 0], [15, 95, 15])
        # 4 bits a block: value 0x21 is 1 at index 0, then 2 at index 1.
        self.assertEqual(sorted(b[3] for b in result["blocks"]), ["minecraft:oak_log[axis=y]", "minecraft:stone"])
        with self.assertRaisesRegex(import_formats.ImportProblem, "too big"):
            import_formats.read_world(folder, "overworld", [0, 0, 0], [999, 300, 999])
        with self.assertRaisesRegex(import_formats.ImportProblem, "no level.dat"):
            import_formats.read_world(TEMP, "overworld", [0, 0, 0], [1, 1, 1])


class Editing(unittest.TestCase):
    def test_rotate_turns_position_and_state(self):
        stair = "minecraft:oak_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]"
        out = edits.rotate([[0, 0, 0, stair], [2, 0, 0, "minecraft:oak_log[axis=x]"]], 1)
        states = {s for *_, s in out}
        self.assertIn("minecraft:oak_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]", states)
        self.assertIn("minecraft:oak_log[axis=z]", states)
        self.assertEqual(len({b[2] for b in out}), 2)  # the line along x now runs along z

    def test_rotate_four_times_changes_nothing(self):
        states = ["minecraft:cobblestone_wall[east=low,north=none,south=tall,up=true,waterlogged=false,west=none]",
                  "minecraft:oak_sign[rotation=3,waterlogged=false]", "minecraft:rail[shape=north_east,waterlogged=false]", "minecraft:crafter[crafting=false,orientation=north_up,triggered=false]"]
        for state in states:
            self.assertEqual(edits.turn_state(state, 4), state)
            once = edits.turn_state(edits.turn_state(edits.turn_state(edits.turn_state(state, 1), 1), 1), 1)
            self.assertEqual(once, state)

    def test_wall_sides_turn(self):
        self.assertIn("south=low", edits.turn_state("minecraft:cobblestone_wall[east=low,north=none,south=none,up=true,waterlogged=false,west=none]", 1))

    def test_mirror_flips_left_and_right(self):
        out = edits.mirror_state("minecraft:oak_stairs[facing=east,half=bottom,shape=inner_left,waterlogged=false]", "x")
        self.assertEqual(out, "minecraft:oak_stairs[facing=west,half=bottom,shape=inner_right,waterlogged=false]")
        self.assertIn("hinge=right", edits.mirror_state("minecraft:oak_door[facing=north,half=lower,hinge=left,open=false,powered=false]", "z"))
        self.assertIn("facing=south", edits.mirror_state("minecraft:oak_door[facing=north,half=lower,hinge=left,open=false,powered=false]", "z"))

    def test_mirror_twice_changes_nothing(self):
        state = "minecraft:oak_stairs[facing=east,half=bottom,shape=outer_right,waterlogged=false]"
        self.assertEqual(edits.mirror_state(edits.mirror_state(state, "z"), "z"), state)

    def test_every_vanilla_state_still_draws_after_turning(self):
        for state in ("minecraft:oak_stairs[facing=north,half=bottom,shape=inner_left,waterlogged=false]", "minecraft:rail[shape=ascending_north,waterlogged=false]",
                      "minecraft:oak_fence[east=true,north=false,south=false,waterlogged=false,west=false]"):
            self.assertTrue(JAR.bake(edits.turn_state(state, 1))["quads"])

    def test_swap_keeps_shared_properties(self):
        stair = "minecraft:oak_stairs[facing=east,half=top,shape=straight,waterlogged=false]"
        out = edits.swap([[0, 0, 0, stair]], JAR, "oak_stairs", "stone_brick_stairs")
        self.assertEqual(out[0][3], stair.replace("oak_stairs", "stone_brick_stairs"))

    def test_swap_to_a_different_shape_uses_defaults(self):
        out = edits.swap([[0, 0, 0, "minecraft:oak_stairs[facing=east,half=top,shape=straight,waterlogged=false]"]], JAR, "oak_stairs", "stone")
        self.assertEqual(out[0][3], "minecraft:stone")
        JAR.bake(out[0][3])

    def test_swap_clears_non_vanilla_blocks(self):
        blocks = [[0, 0, 0, "somemod:fancy_log[axis=y]"], [0, 1, 0, LOG]]
        self.assertTrue(validate.check({"blocks": blocks}, JAR.block_ids(), lenient=True)["errors"])
        fixed = edits.swap(blocks, JAR, "somemod:fancy_log", "oak_log")
        self.assertEqual(validate.check({"blocks": fixed}, JAR.block_ids(), lenient=True)["errors"], [])
        self.assertEqual(fixed[0][3], LOG)

    def test_swap_share_and_seed_repeat(self):
        blocks = [[i, 0, 0, "minecraft:stone"] for i in range(100)]
        one = edits.swap(blocks, JAR, "stone", "dirt", 0.5, 3)
        self.assertEqual(one, edits.swap(blocks, JAR, "stone", "dirt", 0.5, 3))
        self.assertTrue(10 < sum("dirt" in b[3] for b in one) < 90)

    def test_refuses_non_vanilla_target_and_removing_everything(self):
        with self.assertRaises(edits.EditProblem):
            edits.swap([[0, 0, 0, "minecraft:stone"]], JAR, "stone", "mymod:rock")
        with self.assertRaises(edits.EditProblem):
            edits.swap([[0, 0, 0, "minecraft:stone"]], JAR, "stone", "")

    def test_family_swap_leaves_dark_oak_alone(self):
        blocks = [[0, 0, 0, "minecraft:oak_stairs[facing=east,half=top,shape=straight,waterlogged=false]"], [1, 0, 0, "minecraft:stripped_oak_log[axis=x]"],
                  [2, 0, 0, "minecraft:dark_oak_planks"], [3, 0, 0, "minecraft:oak_leaves[distance=1,persistent=true,waterlogged=false]"]]
        names = [b[3] for b in edits.swap_family(blocks, JAR, "oak", "spruce")]
        self.assertIn("minecraft:spruce_stairs[facing=east,half=top,shape=straight,waterlogged=false]", names)
        self.assertIn("minecraft:stripped_spruce_log[axis=x]", names)
        self.assertIn("minecraft:dark_oak_planks", names)
        self.assertIn("minecraft:spruce_leaves[distance=1,persistent=true,waterlogged=false]", names)

    def test_trim_and_ground(self):
        blocks = [[0, y, 0, "minecraft:stone"] for y in range(5)]
        self.assertEqual(len(edits.trim(blocks, [None, 1, None], [None, 3, None])), 3)
        self.assertEqual(min(b[1] for b in edits.set_ground(blocks, 2)), -2)

    def test_fix_leaves_makes_natural_leaves_fall(self):
        out = {(x, y, z): s for x, y, z, s in edits.fix_leaves([[0, 0, 0, LOG], [1, 0, 0, "minecraft:oak_leaves[distance=7,persistent=true,waterlogged=false]"],
                                                                [20, 0, 0, "minecraft:oak_leaves[distance=7,persistent=true,waterlogged=false]"]])}
        self.assertIn("persistent=false", out[(1, 0, 0)])
        self.assertIn("persistent=true", out[(20, 0, 0)])

    def test_remove_floating(self):
        out = edits.remove_floating([[0, 0, 0, LOG], [0, 1, 0, LOG], [0, 5, 0, LOG]])
        self.assertEqual(len(out), 2)

    def test_thin_leaves(self):
        blocks = [[0, 0, 0, LOG]] + [[i, 0, 0, leaf(1)] for i in range(1, 60)]
        out = edits.thin_leaves(blocks, 0.5, 2)
        self.assertLess(len(out), 55)
        self.assertIn(LOG, [b[3] for b in out])

    def test_air_only_inside_a_build(self):
        walls = [[x, y, z, "minecraft:stone"] for x in range(5) for z in range(5) for y in range(4) if x in (0, 4) or z in (0, 4) or y == 3]
        air = export.inside_air(walls)
        self.assertEqual(len(air), 3 * 3 * 3)
        self.assertEqual(export.inside_air([[0, 0, 0, LOG], [0, 1, 0, LOG]]), [])
        structure, _ = export.to_structure({"blocks": walls}, 5023, with_air=True)
        self.assertIn({"id": "minecraft:air"}, structure["palette"])


class ImportedProjects(unittest.TestCase):
    def setUp(self):
        import studio
        self.studio = studio
        real = {"mod": studio.MOD_STRUCTURES, "index": studio.EXPORT_INDEX, "credits": studio.CREDITS}
        self.addCleanup(lambda: (setattr(studio, "MOD_STRUCTURES", real["mod"]), setattr(studio, "EXPORT_INDEX", real["index"]), setattr(studio, "CREDITS", real["credits"])))
        studio.MOD_STRUCTURES = Path(TEMP) / "mod"
        studio.EXPORT_INDEX = Path(TEMP) / "exports.json"
        studio.CREDITS = Path(TEMP) / "credits.md"

    count = 0

    def make(self, blocks=None, **credit):
        ImportedProjects.count += 1  # every build differs, because the library refuses duplicates
        result = {"blocks": blocks or [[0, y, 0, LOG] for y in range(ImportedProjects.count + 500)], "dropped": {}, "notes": []}
        return self.studio.save_import(result, "A hut", {"type": "file", "name": "hut.schem"}, {"creator": "Sam", "permission": "Said yes on Discord", "licence": "", **credit}, "house")

    def test_edit_makes_a_new_version_and_keeps_the_original(self):
        project_id = self.make()
        self.assertEqual(self.studio.apply_edit(project_id, 1, {"op": "rotate", "turns": 1}), 2)
        project = projects.load(project_id)
        self.assertEqual(project["versions"][1]["edits"], ["Turned 90°"])
        self.assertEqual(projects.load_version(project_id, 1)["blocks"][0][3], LOG)

    def test_export_needs_only_a_save(self):
        project_id = self.make(creator="")
        with self.assertRaisesRegex(KeyError, "Save"):
            self.studio.saved_version(project_id)
        pipeline.agree(project_id, 1)
        self.assertTrue(self.studio.saved_version(project_id)[1]["blocks"])

    def test_modded_blocks_can_download_but_not_enter_the_mod(self):
        project_id = self.make([[0, 0, 0, "somemod:rock"]], creator="")
        pipeline.agree(project_id, 1)
        self.assertTrue(self.studio.saved_version(project_id)[1]["blocks"])
        with self.assertRaisesRegex(ValueError, "vanilla"):
            self.studio.add_to_mod(project_id)

    def test_add_to_mod_writes_file_and_credit(self):
        project_id = self.make()
        pipeline.agree(project_id, 1)
        done = self.studio.add_to_mod(project_id)
        self.assertTrue(done["name"].startswith("vestiges:house/house_hut_oak_"), done["name"])
        self.assertTrue((self.studio.MOD_STRUCTURES / "house" / (done["name"].split("/")[1] + ".nbt")).exists())
        self.assertTrue(done["name"].endswith("_sam_var01"))
        self.assertIn("by Sam. Permission: Said yes on Discord", self.studio.CREDITS.read_text(encoding="utf-8"))

    def test_saving_after_an_edit_returns_to_adapt(self):
        project_id = self.make()
        pipeline.agree(project_id, 1)
        self.studio.apply_edit(project_id, 1, {"op": "mirror", "axis": "x"})
        project = projects.load(project_id)
        self.assertEqual((project["stage"], project["saved_version"]), ("adapt", None))

    def test_several_variants_of_one_asset(self):
        project_id = self.make(creator="")
        first = library.save_variant(project_id, 1)
        self.assertTrue(first.endswith("_var01"))
        self.studio.apply_edit(project_id, 1, {"op": "rotate", "turns": 2})
        second = library.save_variant(project_id, 2)
        self.assertTrue(second.endswith("_var02"), second)
        names = [s["name"] for s in projects.load(project_id)["saved"]]
        self.assertEqual(names, [first, second])
        self.assertEqual(self.studio.saved_version(project_id, 1)[2], first)

    def test_rename_and_taken_names(self):
        one = self.make(creator="")
        two = self.make(creator="")
        self.assertEqual(library.save_variant(one, 1, "My Tree!!_var01"), "my-tree_var01")
        self.assertEqual(library.save_variant(two, 1, "my-tree_var01"), "my-tree_var02")
        self.assertEqual(library.save_variant(one, 1, "other_var01"), "other_var01")
        self.assertEqual(len(projects.load(one)["saved"]), 1)

    def test_zip_holds_each_variant(self):
        project_id = self.make([[9, 0, 0, LOG], [9, 1, 0, LOG], [9, 2, 0, LOG], [9, 3, 0, LOG]], creator="")
        name = library.save_variant(project_id, 1)
        archive = zipfile.ZipFile(io.BytesIO(self.studio.zip_of([project_id], "schem")))
        self.assertEqual(archive.namelist(), [name + ".schem"])


class Naming(unittest.TestCase):
    STATS = {"width_x": 7, "height": 7, "width_z": 7, "block_counts": {"oak_stairs": 49, "cobblestone": 43, "oak_planks": 33, "stripped_oak_log": 16, "torch": 2}}

    def test_vanilla_house_example(self):
        source = {"type": "file", "name": "x.jar", "item": "data/minecraft/structure/village/plains/houses/plains_small_house_1.nbt"}
        subject = naming.subject_from(source)
        self.assertEqual(subject, "plains-small-house")
        self.assertEqual(naming.next_free(naming.base_name("house", subject, self.STATS, source, {}), set()), "house_plains-small-house_oak-cobblestone_7x7x7_minecraft_var01")

    def test_own_build_with_detail(self):
        stats = {"width_x": 9, "height": 14, "width_z": 9, "block_counts": {"oak_log": 40, "azalea_leaves": 30, "moss_block": 9, "vine": 1}}
        name = naming.base_name("tree", "old-forest", stats, {"type": "file", "name": "t.schem", "item": ""}, {})
        self.assertEqual(name, "tree_old-forest_oak-azalea_mossy_9x14x9_own")

    def test_creator_is_the_source(self):
        self.assertEqual(naming.source_word({"item": "data/foo/structure/x.nbt"}, {"creator": "Sam & Co"}), "sam-co")

    def test_filler_case_and_odd_characters(self):
        self.assertEqual(naming.subject_from({"name": "schematics/New_MegaZero Trading-Hall (final).litematic"}), "mega-zero-trading-hall")
        self.assertEqual(naming.clean("  Ma Tour Élevée/../x "), "ma-tour-lev-e-x")
        self.assertEqual(naming.subject_from({"name": "1.nbt"}), "unnamed")

    def test_next_free_variant(self):
        self.assertEqual(naming.next_free("a", {"a_var01", "a_var02"}), "a_var03")
        self.assertEqual(naming.unique("a_var01", {"a_var01"}), "a_var02")

    def test_guess_kind(self):
        self.assertEqual(naming.guess_kind("village/plains/houses/x.nbt", {"stone": 9}), "house")
        self.assertEqual(naming.guess_kind("thing.schem", {"oak_log": 10, "oak_leaves": 40}), "tree")
        self.assertEqual(naming.guess_kind("thing.schem", {"stone": 400}), "structure")

    def test_long_names_are_limited(self):
        self.assertLessEqual(len(naming.subject_from({"name": "word_" * 60 + ".nbt"})), 48)


class Batches(unittest.TestCase):
    def wait(self, batch_id):
        for _ in range(500):
            batch = batches.load(batch_id)
            if not batch["running"]:
                return batch
            time.sleep(0.02)
        raise AssertionError("batch never finished")

    def upload(self, name, data):
        path = Path(TEMP) / f"up_{time.time_ns()}"
        path.write_bytes(data)
        return {"name": name, "path": str(path), "items": [""]}

    def structure(self, block):
        return export.write_nbt({"size": [1, 1, 1], "entities": [], "blocks": [{"pos": [0, 0, 0], "state": 0}], "palette": [{"id": block}]})

    def test_bad_file_doesnt_stop_duplicates_are_skipped(self):
        batch = self.wait(batches.start([self.upload("good.nbt", self.structure("minecraft:emerald_block")), self.upload("bad.nbt", b"junk"),
                                         self.upload("copy.nbt", self.structure("minecraft:emerald_block")), self.upload("other.nbt", self.structure("minecraft:gold_block"))], {}))
        self.assertEqual((len(batch["imported"]), len(batch["failed"]), len(batch["duplicates"]), batch["done"]), (2, 1, 1, 4))
        self.assertEqual(batch["failed"][0]["label"], "bad.nbt")
        self.assertEqual(library.search(batch=batch["id"])["total"], 2)

    def test_details_apply_and_temp_files_go(self):
        source = self.upload("a.nbt", self.structure("minecraft:diamond_block"))
        batch = self.wait(batches.start([source], {"kind": "ruin", "creator": "Sam"}))
        project = projects.load(batch["imported"][0])
        self.assertEqual((project["category"], project["credit"]["creator"]), ("ruin", "Sam"))
        self.assertFalse(Path(source["path"]).exists())

    def test_search_paging_and_filters(self):
        ids = [batches.add_build({"blocks": [[0, y, 0, LOG] for y in range(n + 200)], "dropped": {}, "notes": []}, {"type": "file", "name": f"pagetest_{n}.nbt"}, {"kind": "other"}, "bpage") for n in range(105)]
        self.assertTrue(all(ids))
        first = library.search(batch="bpage")
        self.assertEqual((first["total"], len(first["rows"])), (105, 100))
        self.assertEqual(len(library.search(batch="bpage", page=1)["rows"]), 5)
        self.assertEqual(library.search(words="pagetest 7", batch="bpage", no_credit=True)["total"], 19)
        self.assertEqual(library.search(batch="bpage", status="saved")["total"], 0)

    def test_index_survives_restart_and_deleted_folder(self):
        made = batches.add_build({"blocks": [[0, 0, 0, "minecraft:lapis_block"]], "dropped": {}, "notes": []}, {"type": "file", "name": "idx.nbt"}, {})
        library.flush()
        library.reset()
        self.assertTrue(any(r["id"] == made for r in library.search(words="idx")["rows"]))
        shutil.rmtree(Path(projects.ROOT) / made)
        library.reset()
        self.assertEqual(library.search(words="idx")["total"], 0)

    def test_old_assets_convert_and_move(self):
        old = Path(TEMP) / "old_projects" / "20200101_000000"
        (old / "versions").mkdir(parents=True)
        (old / "project.json").write_text(json.dumps({"id": "20200101_000000", "title": "Old Tree", "stage": "saved", "saved_version": 1, "versions": [], "created": 1}), encoding="utf-8")
        library.reset()
        self.assertEqual(library.move_old_folder(old.parent), 1)
        project = projects.load("20200101_000000")
        self.assertEqual(project["saved"], [{"version": 1, "name": "old-tree_var01"}])
        self.assertEqual(project["status"], "saved")
        self.assertFalse(old.parent.exists())


class Keys(unittest.TestCase):
    def test_key_is_saved_outside_the_repo_and_never_in_state(self):
        providers.set_key("gemini", "SECRET-VALUE")
        self.assertTrue(providers.has_key("gemini"))
        import studio
        self.assertNotIn("SECRET-VALUE", json.dumps(studio.state()))
        providers.set_key("gemini", "")
        self.assertFalse(providers.has_key("gemini"))

    def test_no_key_gives_clear_words(self):
        providers.set_key("claude", "")
        with self.assertRaisesRegex(providers.ProviderError, "API key"):
            providers.Claude("claude-opus-5-5").complete("brief", "s", "t", {})


class Backups(unittest.TestCase):
    def setUp(self):
        self.saved_root = projects.ROOT
        projects.ROOT = Path(TEMP) / "backup_library"
        shutil.rmtree(projects.ROOT, ignore_errors=True)
        shutil.rmtree(backup.folder(), ignore_errors=True)
        library.reset()

    def tearDown(self):
        projects.ROOT = self.saved_root
        library.reset()

    def make(self, name, text="x"):
        folder = projects.ROOT / name
        folder.mkdir(parents=True, exist_ok=True)
        (folder / "project.json").write_text(json.dumps({"id": name, "title": text}), encoding="utf-8")

    def test_nothing_to_back_up_when_empty(self):
        self.assertIsNone(backup.create("manual"))
        self.assertEqual(backup.list_backups(), [])

    def test_backup_then_restore_brings_back_deleted_assets(self):
        self.make("a1"), self.make("a2")
        row = backup.create("manual")
        self.assertEqual(row["assets"], 2)
        shutil.rmtree(projects.ROOT / "a1")
        (projects.ROOT / "a2" / "project.json").write_text("{}", encoding="utf-8")
        done = backup.restore(row["name"])
        self.assertEqual(done["assets"], 2)
        self.assertEqual(json.loads((projects.ROOT / "a2" / "project.json").read_text(encoding="utf-8"))["title"], "x")
        self.assertTrue((projects.ROOT / "a1" / "project.json").exists())
        self.assertFalse(Path(str(projects.ROOT) + ".replaced").exists())
        self.assertIn("before-restore", [r["reason"] for r in backup.list_backups()])

    def test_start_up_backup_only_when_changed_and_old_enough(self):
        self.make("a1")
        self.assertIsNotNone(backup.auto_if_due())
        self.assertIsNone(backup.auto_if_due())  # too soon
        row = backup.list_backups()[0]
        old = time.time() - 30 * 3600
        os.utime(backup.folder() / row["name"], (old, old))
        for path in projects.ROOT.rglob("*"):
            os.utime(path, (old - 3600, old - 3600))
        self.assertIsNone(backup.auto_if_due())  # old backup but nothing changed
        self.make("a2")
        self.assertIsNotNone(backup.auto_if_due())

    def test_only_the_newest_are_kept(self):
        self.make("a1")
        backup.folder().mkdir(parents=True, exist_ok=True)
        for day in range(1, 15):
            (backup.folder() / f"library_202601{day:02d}_000000_auto.zip").write_bytes(b"")
        backup._prune()
        names = [r["name"] for r in backup.list_backups()]
        self.assertEqual(len(names), backup.MAX_KEPT)
        self.assertEqual(names[0], "library_20260114_000000_auto.zip")

    def test_damaged_or_unsafe_backup_changes_nothing(self):
        self.make("a1")
        backup.folder().mkdir(parents=True, exist_ok=True)
        (backup.folder() / "library_20260101_000000_manual.zip").write_bytes(b"not a zip")
        with self.assertRaises(backup.BackupProblem):
            backup.restore("library_20260101_000000_manual.zip")
        evil = backup.folder() / "library_20260102_000000_manual.zip"
        with zipfile.ZipFile(evil, "w") as archive:
            archive.writestr("../escaped.txt", "x")
        with self.assertRaises(backup.BackupProblem):
            backup.restore(evil.name)
        self.assertFalse((projects.ROOT.parent / "escaped.txt").exists())
        self.assertTrue((projects.ROOT / "a1" / "project.json").exists())
        for bad in ("../x.zip", "library_1.zip", ""):
            with self.assertRaises(KeyError):
                backup.restore(bad)


class FakeReply(io.BytesIO):
    def __init__(self, data, url="https://cdn.modrinth.com/data/x/versions/y/pack.zip", length=None):
        super().__init__(data)
        self.headers = {"Content-Length": str(len(data) if length is None else length)}
        self.final = url

    def geturl(self):
        return self.final


class Online(unittest.TestCase):
    def setUp(self):
        self.real_open = online._open

    def tearDown(self):
        online._open = self.real_open

    def pack(self):
        structure = export.to_structure({"blocks": [[0, 0, 0, LOG], [0, 1, 0, LOG]]}, 5023)[0]
        raw = io.BytesIO()
        with zipfile.ZipFile(raw, "w") as archive:
            archive.writestr("data/demo/structure/stump.nbt", export.write_nbt(structure))
        return raw.getvalue()

    def file_for(self, data, **extra):
        import hashlib
        return {"url": "https://cdn.modrinth.com/data/x/versions/y/pack.zip", "filename": "pack.zip", "version": "1.0",
                "hashes": {"sha512": hashlib.sha512(data).hexdigest()}, **extra}

    def test_search_asks_for_builds_in_the_chosen_type(self):
        url = online.search_url("oak tree", "datapack", True, 2)
        self.assertIn("project_type%3Adatapack", url)
        self.assertIn("categories%3Aworldgen", url)
        self.assertIn("offset=24", url)
        self.assertNotIn("categories", online.search_url("x", "mod", False))
        with self.assertRaises(online.OnlineProblem):
            online.search_url("x", "plugin")

    def test_results_are_tidied_and_licences_warned_about(self):
        hit = online.clean_hit({"project_id": "AbCd1234", "slug": "castle", "title": "Castle", "author": "me", "license": "LicenseRef-All-Rights-Reserved", "downloads": 5, "project_type": "datapack"})
        self.assertEqual(hit["url"], "https://modrinth.com/datapack/castle")
        self.assertIn("All rights reserved", hit["warning"])
        self.assertEqual(online.licence_warning("MIT"), "")
        self.assertIn("Non-commercial", online.licence_warning("CC-BY-NC-SA-4.0"))
        self.assertIn("derivatives", online.licence_warning("CC-BY-NC-ND-4.0"))
        self.assertTrue(online.licence_warning(""))

    def test_picks_the_main_zip_or_jar_of_the_newest_version(self):
        versions = [{"version_number": "2", "files": [{"filename": "notes.txt"}]},
                    {"version_number": "1", "files": [{"filename": "a.zip"}, {"filename": "b.jar", "primary": True}]}]
        self.assertEqual(online.pick_file(versions)["filename"], "b.jar")
        self.assertIsNone(online.pick_file([{"files": [{"filename": "x.txt"}]}]))

    def test_only_modrinth_hosts_are_trusted(self):
        for bad in ("http://cdn.modrinth.com/a.zip", "https://evil.example/a.zip", "https://cdn.modrinth.com.evil.example/a.zip", "file:///c:/x"):
            with self.assertRaises(online.OnlineProblem):
                online.check_url(bad)
        online.check_url("https://cdn.modrinth.com/a.zip")
        online.check_url("https://modrinth.com/mod/x", online.PAGE_HOSTS)

    def test_download_checks_hash_size_and_redirects(self):
        folder = Path(TEMP) / "dl"
        folder.mkdir(exist_ok=True)
        data = b"hello" * 100
        online._open = lambda url, timeout=30, **kw: FakeReply(data)
        target = folder / "good"
        self.assertEqual(online.download(self.file_for(data), target), len(data))
        self.assertEqual(target.read_bytes(), data)
        bad = folder / "bad"
        with self.assertRaisesRegex(online.OnlineProblem, "checksum"):
            online.download(self.file_for(b"other"), bad)
        self.assertFalse(bad.exists())
        big = folder / "big"
        with self.assertRaisesRegex(online.OnlineProblem, "limit"):
            online.download(self.file_for(data), big, max_bytes=50)
        self.assertFalse(big.exists())
        online._open = lambda url, timeout=30, **kw: FakeReply(data, url="https://evil.example/a.zip")
        moved = folder / "moved"
        with self.assertRaisesRegex(online.OnlineProblem, "not on the source"):
            online.download(self.file_for(data), moved)
        self.assertFalse(moved.exists())

    def test_a_found_datapack_goes_through_the_normal_import_reader(self):
        import studio
        data = self.pack()
        real_find = online.find_file
        online.find_file = lambda project_id, source="modrinth": self.file_for(data)
        online._open = lambda url, timeout=30, **kw: FakeReply(data)
        try:
            got = studio.fetch_online("AbCd1234")
        finally:
            online.find_file = real_find
        self.assertEqual(len(got["items"]), 1)
        self.assertIn(got["token"], studio._uploads)
        self.assertTrue(studio._uploads[got["token"]][1].exists())

    def test_a_bad_project_id_is_refused_before_any_request(self):
        online._open = lambda *a, **k: self.fail("should not call the network")
        with self.assertRaises(online.OnlineProblem):
            online.find_file("../etc")


class Licences(unittest.TestCase):
    def test_each_kind_of_licence_gets_the_right_answer(self):
        cases = [
            ({}, False, "own", True),
            ({}, True, "own", True),
            ({"creator": "Ann", "licence": "MIT"}, True, "open", True),
            ({"creator": "Ann", "licence": "CC-BY-4.0"}, True, "open", True),
            ({"creator": "Ann", "licence": "CC-BY-SA-4.0"}, True, "open", True),
            ({"creator": "Ann", "licence": "GPL-3.0-only"}, True, "open", True),
            ({"creator": "Ann", "licence": "CC-BY-NC-4.0"}, True, "limited", False),
            ({"creator": "Ann", "licence": "CC-BY-ND-4.0"}, True, "limited", False),
            ({"creator": "Ann", "licence": "LicenseRef-All-Rights-Reserved"}, True, "limited", False),
            ({"creator": "Ann", "licence": "All Rights Reserved", "permission": "DM on Discord, 2026-10-30"}, True, "permission", True),
            ({"creator": "Ann"}, True, "unknown", None),
            ({"creator": "Ann", "licence": "Custom terms"}, True, "unknown", None),
            ({"link": "https://modrinth.com/datapack/x"}, True, "unknown", None),
        ]
        for credit, imported, status, modify in cases:
            got = licences.assess(credit, imported)
            self.assertEqual((got["status"], got["can_modify"]), (status, modify), credit)

    def test_library_rows_filter_and_search_by_licence(self):
        def add(name, credit, _count=[0]):
            _count[0] += 1
            return batches.add_build({"blocks": [[0, 0, 0, "minecraft:lapis_block"], [_count[0], 0, 0, "minecraft:lapis_block"]], "dropped": {}, "notes": []}, {"type": "file", "name": name}, credit, "blic")
        mine = add("lic_mine.nbt", {})
        free = add("lic_free.nbt", {"creator": "Ann", "licence": "MIT"})
        tight = add("lic_tight.nbt", {"creator": "Bo", "licence": "LicenseRef-All-Rights-Reserved"})
        vague = add("lic_vague.nbt", {"creator": "Cy"})
        status = {r["id"]: r["licence_status"] for r in library.search(batch="blic")["rows"]}
        self.assertEqual(status, {mine: "own", free: "open", tight: "limited", vague: "unknown"})
        check = {r["id"] for r in library.search(batch="blic", licence="check")["rows"]}
        self.assertEqual(check, {tight, vague})
        self.assertEqual([r["id"] for r in library.search(batch="blic", licence="open")["rows"]], [free])
        self.assertEqual([r["id"] for r in library.search(words="mit", batch="blic")["rows"]], [free])

    def test_credits_file_says_what_to_check(self):
        import studio
        real = studio.CREDITS
        studio.CREDITS = Path(TEMP) / "credits.md"
        try:
            studio.write_credits({
                "a": {"title": "Oak", "category": "tree", "credit": {"creator": "Ann", "licence": "MIT", "link": "https://modrinth.com/datapack/oak"}},
                "b": {"title": "Keep", "category": "ruin", "credit": {"creator": "Bo", "licence": "All Rights Reserved"}},
                "c": {"title": "Mine", "category": "tree", "credit": {}},
            })
            text = studio.CREDITS.read_text(encoding="utf-8")
        finally:
            studio.CREDITS = real
        self.assertIn("Source: https://modrinth.com/datapack/oak", text)
        self.assertIn("Status: Open licence", text)
        self.assertIn("get permission before releasing", text)
        self.assertNotIn("Mine", text)

    def test_adding_to_the_mod_needs_a_yes_for_limited_builds(self):
        import studio
        made = batches.add_build({"blocks": [[0, 0, 0, LOG]], "dropped": {}, "notes": []}, {"type": "file", "name": "lic_gate.nbt"}, {"creator": "Bo", "licence": "All Rights Reserved"}, "bgate")
        library.save_variant(made, 1)
        with self.assertRaises(licences.LicenceProblem):
            studio.add_to_mod(made)


class Organise(unittest.TestCase):
    def add(self, n):
        return batches.add_build({"blocks": [[0, 0, 0, "minecraft:lapis_block"], [n + 500, 0, 0, "minecraft:lapis_block"]], "dropped": {}, "notes": []}, {"type": "file", "name": f"org{n}.nbt"}, {}, "borg")

    def test_tags_and_collections_ignore_capitals_and_repeats(self):
        project = {"tags": ["oak"]}
        organise.add_tag(project, "Big Oak"), organise.add_tag(project, "big-oak"), organise.add_tag(project, "oak")
        self.assertEqual(project["tags"], ["oak", "big-oak"])
        organise.remove_tag(project, "OAK")
        self.assertEqual(project["tags"], ["big-oak"])
        organise.add_to_collection(project, "  Forest   Set "), organise.add_to_collection(project, "forest set")
        self.assertEqual(project["collections"], ["Forest Set"])
        organise.rename_collection(project, "FOREST SET", "Woods")
        self.assertEqual(project["collections"], ["Woods"])
        organise.remove_from_collection(project, "woods")
        self.assertEqual(project["collections"], [])
        with self.assertRaises(ValueError):
            organise.apply(project, "nope", {})

    def test_bulk_actions_reach_the_library_and_its_filters(self):
        import studio
        a, b, c = self.add(1), self.add(2), self.add(3)
        studio.bulk({"ids": [a, b], "action": "collection_add", "name": "Forest set"})
        studio.bulk({"ids": [a], "action": "favorite", "value": True})
        studio.bulk({"ids": [a, c], "action": "tag_add", "tag": "mossy"})
        found = lambda **kw: {r["id"] for r in library.search(batch="borg", **kw)["rows"]} & {a, b, c}
        self.assertEqual(found(collection="forest SET"), {a, b})
        self.assertEqual(found(favorite=True), {a})
        self.assertEqual(found(tag="mossy"), {a, c})
        self.assertEqual(found(words="forest"), {a, b})
        facets = library.facets()
        self.assertIn({"name": "Forest set", "count": 2}, facets["collections"])
        self.assertIn("mossy", [t["name"] for t in facets["tags"]])
        # rename and delete reach every member, even when nothing is ticked
        studio.bulk({"ids": [], "action": "collection_rename", "name": "forest set", "to": "Woods"})
        self.assertEqual(found(collection="woods"), {a, b})
        self.assertEqual(found(collection="forest set"), set())
        studio.bulk({"ids": [], "action": "collection_delete", "name": "Woods"})
        self.assertEqual(found(collection="woods"), set())
        self.assertTrue(projects.load(a)["favorite"])  # deleting a collection keeps the assets
        studio.bulk({"ids": [a], "action": "favorite", "value": False})
        self.assertEqual(found(favorite=True), set())

    def test_asset_screen_saves_favorite_and_collections(self):
        import studio
        a = self.add(4)
        studio.set_details(a, {"favorite": True, "collections": "Village, village, Ruins", "tags": "Stone Well, old"})
        project = projects.load(a)
        self.assertEqual((project["favorite"], project["collections"], project["tags"]), (True, ["Village", "Ruins"], ["stone-well", "old"]))


class Names(unittest.TestCase):
    stats = {"width_x": 21, "height": 34, "width_z": 19, "blocks": 1832, "block_counts": {"spruce_log": 300, "spruce_leaves": 1400, "moss_block": 132}}

    def test_each_style_carries_size_blocks_and_version(self):
        full, compact, sorted_ = (naming.styled_name(i, "tree", "spruce-tree", self.stats, None, {}, 3) for i in range(3))
        self.assertEqual(full, "large_spruce-tree_moss_mossy_w21_d19_h34_1832blocks_v3")
        self.assertEqual(compact, "spruce-tree_21x34x19_1832b_v3")
        self.assertEqual(sorted_, "h034_w021_spruce-tree_1832blocks_v3")
        self.assertEqual(naming.styled_name(3, "tree", "spruce-tree", self.stats, None, {}, 3), full)

    def test_the_kind_is_added_when_the_subject_does_not_say_it(self):
        self.assertEqual(naming.thing_word("tree", "oak"), "oak-tree")
        self.assertEqual(naming.thing_word("tree", "oak-tree"), "oak-tree")
        self.assertEqual(naming.thing_word("other", "unnamed"), "asset")
        self.assertEqual(naming.size_class({"width_x": 5, "height": 8, "width_z": 5}), "small")
        self.assertEqual(naming.size_class({"width_x": 5, "height": 45, "width_z": 5}), "huge")

    def test_creator_is_named_and_a_taken_name_gets_a_variant_number(self):
        name = naming.styled_name(0, "tree", "oak", self.stats, None, {"creator": "Ann"}, 1)
        self.assertIn("_by-ann_v1", name)
        self.assertEqual(naming.unique(name, {name}), naming.next_free(name, {name}))

    def test_a_version_can_be_given_a_new_proposed_name(self):
        made = batches.add_build({"blocks": [[0, y, 0, LOG] for y in range(7)] + [[1, 6, 0, leaf(1)]], "dropped": {}, "notes": []}, {"type": "file", "name": "plains_spruce_test.nbt"}, {}, "bname")
        project = projects.load(made)
        names = [library.propose_styled(project, 1, i) for i in range(3)]
        self.assertEqual(len(set(names)), 3)
        for name in names:
            self.assertRegex(name, r"v1")
            self.assertEqual(naming.clean(name), name)


class FakeJar:
    """Two blocks: a green top-and-side cube and nothing for air."""
    def __init__(self):
        from PIL import Image
        out = io.BytesIO()
        Image.new("RGBA", (16, 16), (200, 40, 40, 255)).save(out, "PNG")
        self.png = out.getvalue()

    def texture(self, path):
        return self.png

    def bake(self, state):
        if "air" in state:
            return {"quads": [], "kind": "invisible", "tint": None}
        corners = lambda *c: [v for point in c for v in point]
        top = {"v": corners((0, 1, 0), (1, 1, 0), (1, 1, 1), (0, 1, 1)), "n": [0, 1, 0], "tex": "block/x", "tinted": False}
        side = {"v": corners((0, 1, 1), (1, 1, 1), (1, 0, 1), (0, 0, 1)), "n": [0, 0, 1], "tex": "block/x", "tinted": False}
        return {"quads": [top, side], "kind": "solid", "tint": None}


class Thumbs(unittest.TestCase):
    def test_a_picture_is_drawn_from_the_blocks(self):
        from PIL import Image
        png = thumbs.draw([[0, 0, 0, "minecraft:stone"], [1, 0, 0, "minecraft:stone"], [0, 1, 0, "minecraft:stone"], [5, 5, 5, "minecraft:air"]], FakeJar())
        image = Image.open(io.BytesIO(png))
        self.assertEqual(image.size, (thumbs.SIZE, thumbs.SIZE))
        opaque = [p for p in image.convert("RGBA").getdata() if p[3] > 200]
        self.assertGreater(len(opaque), 500)
        self.assertTrue(all(p[0] > p[1] for p in opaque))  # reddish, from the texture
        self.assertEqual(Image.open(io.BytesIO(thumbs.draw([[0, 0, 0, "minecraft:air"]], FakeJar()))).getbbox(), None)

    def test_pictures_are_kept_and_redrawn_for_a_new_version(self):
        thumbs._jar_source = FakeJar
        try:
            made = batches.add_build({"blocks": [[0, 0, 0, "minecraft:stone"], [0, 1, 0, "minecraft:stone"], [3, 0, 0, "minecraft:stone"]], "dropped": {}, "notes": []}, {"type": "file", "name": "thumb_one.nbt"}, {}, "bthumb")
            first = thumbs.get(made)
            files = list(thumbs.folder().glob(f"{made}_v*.png"))
            self.assertEqual(len(files), 1)
            self.assertEqual(thumbs.get(made), first)
            projects.save_version(made, 2, None, {"blocks": [[0, 0, 0, "minecraft:stone"]], "dropped": {}, "notes": []})
            project = projects.load(made)
            project["versions"].append({**project["versions"][-1], "number": 2})
            projects.save(project)
            self.assertNotEqual(thumbs.get(made), first)
            self.assertEqual(len(list(thumbs.folder().glob(f"{made}_v*.png"))), 1)
            projects.delete(made)
            self.assertEqual(list(thumbs.folder().glob(f"{made}_v*.png")), [])
        finally:
            thumbs._jar_source = None


class Explore(unittest.TestCase):
    def archive(self):
        structure = export.to_structure({"blocks": [[0, 0, 0, LOG], [0, 1, 0, LOG]]}, 5023)[0]
        raw = io.BytesIO()
        with zipfile.ZipFile(raw, "w") as z:
            z.writestr("pack.mcmeta", '{"pack": {"pack_format": 99}}')
            z.writestr("data/demo/structure/stump.nbt", export.write_nbt(structure))
            z.writestr("data/demo/loot_table/chests/box.json", '{"pools": []}')
            z.writestr("data/demo/recipe/thing.json", "{}")
            z.writestr("data/demo/tags/block/x.json", "{}")
            z.writestr("data/demo/worldgen/configured_feature/t.json", "{}")
            z.writestr("assets/demo/textures/block/rock.png", b"PNG fake")
            z.writestr("assets/demo/models/block/rock.json", "{}")
            z.writestr("assets/demo/sounds/boom.ogg", b"OggS")
            z.writestr("assets/demo/lang/en_us.json", "{}")
            z.writestr("dev/Thing.class", b"class")
            z.writestr("notes/odd.bin", b"1")
            z.writestr("../evil.txt", b"x")
        return raw.getvalue()

    def test_every_file_lands_in_the_right_type(self):
        for path, kind in [("data/a/structures/x.nbt", "structure"), ("data/a/loot_tables/x.json", "loot"), ("data/a/recipes/x.json", "recipe"),
                           ("data/a/advancement/x.json", "advancement"), ("data/a/function/x.mcfunction", "function"), ("data/a/odd/x.json", "data"),
                           ("assets/a/textures/item/x.png", "texture"), ("assets/a/blockstates/x.json", "model"), ("assets/a/sounds.json", "sound"),
                           ("fabric.mod.json", "info"), ("com/x/Y.class", "code"), ("random/file.bin", "other")]:
            self.assertEqual(explore.classify(path), kind, path)

    def test_listing_counts_types_and_skips_unsafe_names(self):
        found = explore.listing(self.archive())
        counts = {t["type"]: t["count"] for t in found["types"]}
        self.assertEqual(counts["structure"], 1)
        self.assertEqual(counts["texture"], 1)
        self.assertEqual(counts["code"], 1)
        self.assertNotIn("../evil.txt", [e["path"] for e in found["entries"]])
        self.assertEqual(found["namespaces"], ["demo"])
        self.assertEqual(found["types"][0]["type"], "structure")  # fixed order: builds first
        with self.assertRaises(explore.ExploreProblem):
            explore.listing(b"not an archive")

    def test_previews_by_kind(self):
        data = self.archive()
        self.assertEqual(explore.preview(data, "assets/demo/textures/block/rock.png")["kind"], "image")
        self.assertEqual(explore.preview(data, "assets/demo/sounds/boom.ogg")["kind"], "sound")
        self.assertEqual(explore.preview(data, "pack.mcmeta")["kind"], "text")
        self.assertIn("pack_format", explore.preview(data, "pack.mcmeta")["text"])
        self.assertEqual(explore.preview(data, "data/demo/structure/stump.nbt")["kind"], "none")
        with self.assertRaises(explore.ExploreProblem):
            explore.read(data, "data/demo/missing.json")

    def test_export_keeps_paths_or_flattens_and_refuses_unsafe_ones(self):
        data = self.archive()
        picked = ["data/demo/loot_table/chests/box.json", "assets/demo/models/block/rock.json", "data/demo/recipe/thing.json"]
        names = zipfile.ZipFile(io.BytesIO(explore.export(data, picked))).namelist()
        self.assertEqual(sorted(names), sorted(picked))
        flat = zipfile.ZipFile(io.BytesIO(explore.export(data, picked + ["data/demo/tags/block/x.json", "assets/demo/lang/en_us.json"], keep_folders=False))).namelist()
        self.assertEqual(len(flat), len(set(flat)))
        self.assertIn("box.json", flat)
        for bad in (["../evil.txt"], ["/etc/passwd"], ["C:/x.txt"], ["data/demo/nothing.json"]):
            with self.assertRaises(explore.ExploreProblem):
                explore.export(data, bad)

    def test_the_screen_keeps_the_file_and_can_hand_its_builds_to_import(self):
        import studio
        path = Path(TEMP) / "explore_test.zip"
        path.write_bytes(self.archive())
        opened = studio.open_explored("tok1", "demo.zip", path)
        self.assertEqual(opened["name"], "demo.zip")
        self.assertEqual(studio.explored("tok1")[0], "demo.zip")
        with self.assertRaises(explore.ExploreProblem):
            studio.explored("nope")
        for number in range(5):  # only the last few files are kept
            extra = Path(TEMP) / f"explore_extra_{number}.zip"
            extra.write_bytes(self.archive())
            studio.open_explored(f"more{number}", "x.zip", extra)
        self.assertNotIn("tok1", studio._explored)
        self.assertFalse(path.exists())
        self.assertLessEqual(len(studio._explored), 4)


class Presets(unittest.TestCase):
    def test_every_preset_makes_a_valid_vanilla_tree(self):
        for key in presets.PRESETS:
            blocks = presets.generate(presets.make_spec(key))
            check = pipeline.inspect({"blocks": blocks}, lenient=True)
            self.assertEqual(check["errors"], [], key)
            self.assertTrue(100 < check["stats"]["blocks"] < 3000, key)
            self.assertTrue(any(b[3].split("[")[0].endswith(("_log", "stone", "cobblestone")) for b in blocks), key)

    def test_the_same_settings_and_seed_make_the_same_tree(self):
        one = presets.generate(presets.make_spec("cherry", {"seed": 11}))
        self.assertEqual(one, presets.generate(presets.make_spec("cherry", {"seed": 11})))
        self.assertNotEqual(one, presets.generate(presets.make_spec("cherry", {"seed": 12})))
        self.assertNotEqual(one, presets.generate(presets.make_spec("cherry", {"seed": 11, "height": 20})))

    def test_sliders_cannot_make_a_tree_too_big_or_broken(self):
        spec = presets.make_spec("oak", {"height": 9999, "canopy": {"radius": 500, "density": 5}, "branches": {"count": -3}})
        self.assertEqual((spec["height"], spec["canopy"]["radius"], spec["canopy"]["density"], spec["branches"]["count"]), (60, 12, 1, 0))
        with self.assertRaises(presets.PresetProblem):
            presets.make_spec("oak", {"height": "tall"})
        with self.assertRaises(presets.PresetProblem):
            presets.make_spec("oak", {"canopy": {"shape": "cube"}})
        with self.assertRaises(presets.PresetProblem):
            presets.make_spec("nope")
        self.assertLess(len(presets.generate(spec)), 60000)

    def test_leaves_get_a_real_distance_and_logs_an_axis(self):
        blocks = presets.generate(presets.make_spec("oak"))
        leaf_states = [b[3] for b in blocks if "_leaves" in b[3]]
        self.assertTrue(leaf_states)
        self.assertTrue(all("distance=" in s and "persistent=" in s for s in leaf_states))
        self.assertTrue(all("[axis=" in b[3] for b in blocks if b[3].endswith("_log[axis=x]") or "oak_log" in b[3]))

    def test_preview_and_create_work_through_the_tool(self):
        import studio
        shown = studio.preview_preset({"preset": "spruce", "changes": {"height": 20}})
        self.assertEqual(shown["spec"]["height"], 20)
        self.assertTrue(base64_png(shown["image"]))
        made = studio.create_presets({"preset": "birch", "changes": {"seed": 4000}, "count": 3})
        self.assertEqual(len(made["ids"]) + made["skipped"], 3)
        self.assertTrue(made["batch"])
        project = projects.load(made["ids"][0])
        self.assertIn("seed 4000", project["title"])
        self.assertIn("preset", project["tags"])
        self.assertEqual(project["category"], "tree")
        self.assertEqual(licences.assess(project.get("credit"), True)["status"], "own")
        again = studio.create_presets({"preset": "birch", "changes": {"seed": 4000}, "count": 1})
        self.assertEqual((again["ids"], again["skipped"]), ([], 1))
        self.assertLessEqual(len(studio.create_presets({"preset": "oak", "changes": {"seed": 6000}, "count": 999})["ids"]), 24)


def base64_png(text):
    import base64
    return base64.b64decode(text)[1:4] == b"PNG"


class MoreSources(unittest.TestCase):
    def setUp(self):
        self.real_open, self.real_json = online._open, online._get_json

    def tearDown(self):
        online._open, online._get_json = self.real_open, self.real_json

    def test_github_search_asks_for_builds_and_tidies_results(self):
        self.assertIn("schematic OR litematic", online.github_query("oak tree"))
        self.assertNotIn("litematic", online.github_query("oak tree", builds=False))
        self.assertIn("fork%3Afalse", online.github_url("tree", True, 2))
        self.assertIn("page=3", online.github_url("tree", True, 2))
        hit = online.clean_github({"full_name": "ann/trees", "name": "trees", "description": "Tree schematics", "owner": {"login": "ann"}, "license": {"spdx_id": "NOASSERTION"},
                                   "stargazers_count": 7, "size": 300, "topics": ["minecraft"], "html_url": "https://github.com/ann/trees"})
        self.assertEqual((hit["source"], hit["id"], hit["author"], hit["licence"]), ("github", "ann/trees", "ann", ""))
        self.assertIn("No licence", hit["warning"])
        online._get_json = lambda url, headers=None, name="": {"items": [{"full_name": "a/b", "name": "b", "owner": {"login": "a"}, "license": {"spdx_id": "MIT"}}], "total_count": 5000}
        found = online.search("x", source="github")
        self.assertEqual((found["total"], found["hits"][0]["warning"]), (1000, ""))  # GitHub only lists the first 1,000

    def test_github_downloads_are_checked_and_size_capped(self):
        online._get_json = lambda url, headers=None, name="": {"name": "trees", "size": 900, "default_branch": "main"}
        file = online.find_file("ann/trees", "github")
        self.assertEqual((file["filename"], file["version"]), ("trees.zip", "main"))
        self.assertTrue(file["url"].startswith("https://api.github.com/repos/ann/trees/zipball"))
        online._get_json = lambda url, headers=None, name="": {"name": "big", "size": 300000}
        with self.assertRaisesRegex(online.OnlineProblem, "100 MB"):
            online.find_file("ann/big", "github")
        for bad in ("../x", "a/b/c", "nope", ""):
            with self.assertRaises(online.OnlineProblem):
                online.find_file(bad, "github")
        data = b"zipdata" * 50
        folder = Path(TEMP) / "dl2"
        folder.mkdir(exist_ok=True)
        online._open = lambda url, timeout=30, headers=None, name="": FakeReply(data, url="https://codeload.github.com/ann/trees/legacy.zip/main")
        self.assertEqual(online.download({**file, "hashes": {}}, folder / "gh.zip"), len(data))
        online._open = lambda url, timeout=30, headers=None, name="": FakeReply(data, url="https://evil.example/x.zip")
        with self.assertRaises(online.OnlineProblem):
            online.download({**file, "hashes": {}}, folder / "gh2.zip")
        self.assertFalse((folder / "gh2.zip").exists())

    def test_curseforge_needs_a_key_and_respects_creators_who_block_downloads(self):
        import providers
        providers.set_key("curseforge", "")
        with self.assertRaisesRegex(online.OnlineProblem, "CurseForge API key"):
            online.search("tree", "datapack", source="curseforge")
        url = online.curseforge_url("oak tree", "world", 1)
        self.assertIn("classId=17", url)
        self.assertIn("index=12", url)
        self.assertIn("gameId=432", url)
        with self.assertRaises(online.OnlineProblem):
            online.curseforge_url("x", "plugin")
        hit = online.clean_curseforge({"id": 123, "name": "Big Castle", "summary": "A castle", "authors": [{"name": "Bo"}], "downloadCount": 9, "allowModDistribution": False,
                                       "links": {"websiteUrl": "https://www.curseforge.com/minecraft/worlds/big-castle"}, "categories": [{"name": "Adventure"}]}, "world")
        self.assertEqual((hit["source"], hit["id"], hit["author"]), ("curseforge", "123", "Bo"))
        self.assertIn("only allows downloads on CurseForge", hit["warning"])
        files = [{"fileName": "a.zip", "downloadUrl": None}, {"fileName": "notes.txt", "downloadUrl": "https://edge.forgecdn.net/x.txt"},
                 {"fileName": "b.zip", "downloadUrl": "https://edge.forgecdn.net/files/b.zip", "hashes": [{"value": "ABC", "algo": 1}], "displayName": "b v2", "fileLength": 10}]
        picked = online.pick_curseforge_file(files)
        self.assertEqual((picked["filename"], picked["hashes"]), ("b.zip", {"sha1": "abc"}))
        self.assertIsNone(online.pick_curseforge_file(files[:2]))
        providers.set_key("curseforge", "test-key")
        try:
            online._get_json = lambda url, headers=None, name="": {"data": files[:2]}
            with self.assertRaisesRegex(online.OnlineProblem, "only allows downloads on CurseForge"):
                online.find_file("123", "curseforge")
            online._get_json = lambda url, headers=None, name="": {"data": [files[2]], "pagination": {"totalCount": 1}}
            self.assertEqual(online.find_file("123", "curseforge")["hosts"], online.CURSEFORGE_HOSTS)
            with self.assertRaises(online.OnlineProblem):
                online.find_file("../1", "curseforge")
            self.assertTrue(online.sources()[2]["has_key"])
        finally:
            providers.set_key("curseforge", "")
        self.assertFalse(online.sources()[2]["has_key"])
        self.assertNotIn("test-key", json.dumps(online.sources()))

    def test_download_checks_a_sha1(self):
        import hashlib
        data = b"curse" * 40
        folder = Path(TEMP) / "dl3"
        folder.mkdir(exist_ok=True)
        online._open = lambda url, timeout=30, headers=None, name="": FakeReply(data, url="https://edge.forgecdn.net/files/b.zip")
        file = {"url": "https://edge.forgecdn.net/files/b.zip", "hosts": online.CURSEFORGE_HOSTS, "hashes": {"sha1": hashlib.sha1(data).hexdigest()}}
        self.assertEqual(online.download(file, folder / "ok.zip"), len(data))
        with self.assertRaisesRegex(online.OnlineProblem, "checksum"):
            online.download({**file, "hashes": {"sha1": "0" * 40}}, folder / "bad.zip")
        self.assertFalse((folder / "bad.zip").exists())
        with self.assertRaises(online.OnlineProblem):  # a Modrinth-only host list refuses a CurseForge file
            online.download({**file, "hosts": online.FILE_HOSTS}, folder / "x.zip")

    def test_sites_without_an_api_only_open_a_web_search(self):
        from urllib.parse import urlparse
        url = online.browse_url("Planet Minecraft", "oak tree")
        self.assertTrue(url.startswith("https://duckduckgo.com/?q="))
        self.assertIn("site%3Aplanetminecraft.com+oak+tree", url)
        self.assertEqual(urlparse(url).hostname, "duckduckgo.com")
        with self.assertRaises(online.OnlineProblem):
            online.browse_url("evil.example", "x")
        online.check_url("https://github.com/ann/trees", online.PAGE_HOSTS)
        with self.assertRaises(online.OnlineProblem):
            online.search("x", source="nowhere")

    def test_a_repository_download_is_read_through_its_wrapping_folder(self):
        self.assertEqual(explore.classify("trees-abc123/data/demo/structure/oak.nbt"), "structure")
        self.assertEqual(explore.classify("trees-abc123/assets/demo/textures/block/x.png"), "texture")
        self.assertEqual(explore.classify("trees-abc123/README.md"), "info")
        self.assertEqual(explore.classify("trees-abc123/random.bin"), "other")


class FileAssets(unittest.TestCase):
    def pack(self, tag=0):
        """tag makes the content unique, because the Library keeps identical files only once."""
        from PIL import Image
        png = io.BytesIO()
        Image.new("RGBA", (16, 16), (30, 160, 60 + tag, 255)).save(png, "PNG")
        structure = export.to_structure({"blocks": [[0, 0, 0, LOG]]}, 5023)[0]
        raw = io.BytesIO()
        with zipfile.ZipFile(raw, "w") as z:
            z.writestr("assets/demo/textures/block/mossy_rock.png", png.getvalue())
            z.writestr("assets/demo/sounds/boom.ogg", b"OggS-fake-sound%d" % tag)
            z.writestr("data/demo/loot_table/chests/box.json", '{"pools": [], "tag": %d}' % tag)
            z.writestr("assets/demo/models/block/mossy_rock.json", '{"parent": "block/cube_all", "tag": %d}' % tag)
            z.writestr("data/demo/structure/stump.nbt", export.write_nbt(structure))
            z.writestr("dev/Thing.class", b"class")
        return raw.getvalue()

    def test_picked_files_become_library_assets_with_credit_and_a_source(self):
        picks = ["assets/demo/textures/block/mossy_rock.png", "assets/demo/sounds/boom.ogg", "data/demo/loot_table/chests/box.json", "assets/demo/models/block/mossy_rock.json"]
        done = files.save_from_archive(self.pack(1), "rocks.zip", picks, {"creator": "Ann", "licence": "MIT", "link": "https://example.test/rocks"})
        self.assertEqual((len(done["ids"]), done["duplicates"], done["skipped"]), (4, 0, []))
        self.assertTrue(done["batch"])
        by_kind = {projects.load(i)["category"]: projects.load(i) for i in done["ids"]}
        self.assertEqual(set(by_kind), {"texture", "sound", "data", "model"})
        texture = by_kind["texture"]
        self.assertEqual((texture["mode"], texture["title"], texture["file"]["width"], texture["file"]["height"]), ("file", "mossy rock", 16, 16))
        self.assertEqual(texture["source"], {"type": "archive", "name": "rocks.zip", "item": "assets/demo/textures/block/mossy_rock.png"})
        self.assertIn("texture", texture["tags"])
        self.assertEqual(projects.file_path(texture["id"]).read_bytes()[:4], bytes([137, 80, 78, 71]))
        self.assertEqual(licences.of_project(texture)["status"], "open")
        row = [r for r in library.search(batch=done["batch"])["rows"] if r["id"] == texture["id"]][0]
        self.assertEqual((row["kind"], row["size"], row["mode"], row["ftype"]), ("texture", "16x16 px", "file", "texture"))

    def test_the_same_content_is_kept_once_and_unsupported_files_are_reported(self):
        picks = ["assets/demo/textures/block/mossy_rock.png", "data/demo/structure/stump.nbt", "dev/Thing.class"]
        first = files.save_from_archive(self.pack(2), "rocks2.zip", picks[:1], {})
        again = files.save_from_archive(self.pack(2), "rocks2.zip", picks, {})
        self.assertEqual((len(first["ids"]), len(again["ids"]), again["duplicates"]), (1, 0, 1))
        self.assertEqual(len(again["skipped"]), 2)
        self.assertIn("Import builds", again["skipped"][0]["reason"])

    def test_a_file_from_an_archive_with_no_licence_is_unknown_not_your_own(self):
        done = files.save_from_archive(self.pack(3), "mystery.zip", ["assets/demo/sounds/boom.ogg"], {})
        project = projects.load(done["ids"][0])
        self.assertEqual(licences.of_project(project)["status"], "unknown")
        self.assertIn(done["ids"][0], {r["id"] for r in library.search(licence="check")["rows"]})

    def test_thumbnails_show_a_texture_as_itself_and_other_files_as_a_card(self):
        from PIL import Image
        done = files.save_from_archive(self.pack(4), "thumbs.zip", ["assets/demo/textures/block/mossy_rock.png", "data/demo/loot_table/chests/box.json"], {})
        pictures = [Image.open(io.BytesIO(thumbs.get(i))).convert("RGBA") for i in done["ids"]] if done["ids"] else []
        self.assertTrue(pictures)
        for picture in pictures:
            self.assertEqual(picture.size, (thumbs.SIZE, thumbs.SIZE))
            self.assertIsNotNone(picture.getbbox())
        texture = [p for p in pictures if p.getpixel((thumbs.SIZE // 2, thumbs.SIZE // 2)) == (30, 160, 64, 255)]
        self.assertEqual(len(texture), 1 if len(pictures) else 0)

    def test_files_are_in_the_zip_download_and_deleting_removes_the_folder(self):
        import studio
        done = files.save_from_archive(self.pack(5), "zipme.zip", ["data/demo/loot_table/chests/box.json"], {"creator": "Bo", "licence": "MIT"})
        names = zipfile.ZipFile(io.BytesIO(studio.zip_of(done["ids"], "nbt"))).namelist()
        self.assertEqual(names, ["data/box.json"])
        folder = projects._path(done["ids"][0])
        projects.delete(done["ids"][0])
        self.assertFalse(folder.exists())
        self.assertEqual(thumbs.forget(done["ids"][0]), None)


class OwnFiles(unittest.TestCase):
    def png(self, shade):
        from PIL import Image
        out = io.BytesIO()
        Image.new("RGBA", (8, 16), (shade, 90, 40, 255)).save(out, "PNG")
        return out.getvalue()

    def test_the_type_is_guessed_from_path_extension_then_content(self):
        guess = files.guess_type
        self.assertEqual(guess("a.png", "a.png", b""), "texture")
        self.assertEqual(guess("a.ogg", "a.ogg", b""), "sound")
        self.assertEqual(guess("x.json", "assets/demo/models/block/x.json", b"{}"), "model")
        self.assertEqual(guess("x.json", "data/demo/loot_table/x.json", b"{}"), "loot")
        self.assertEqual(guess("x.json", "x.json", b'{"pools": []}'), "loot")
        self.assertEqual(guess("x.json", "x.json", b'{"parent": "block/cube_all"}'), "model")
        self.assertEqual(guess("x.json", "x.json", b'{"values": ["minecraft:stone"]}'), "tag")
        self.assertEqual(guess("x.json", "x.json", b"not json"), "data")
        self.assertEqual(guess("x.png", "x.png", b"", forced="sound"), "sound")
        with self.assertRaises(files.FileProblem):
            guess("tool.exe", "tool.exe", b"")
        with self.assertRaises(files.FileProblem):
            guess("pack.zip", "pack.zip", b"")

    def test_own_files_are_your_own_work_unless_credit_is_given(self):
        mine = files.create_own("my_stone.png", self.png(11), {})
        project = projects.load(mine)
        self.assertEqual((project["mode"], project["category"], project["source"]["type"]), ("file", "texture", "own"))
        self.assertEqual(licences.of_project(project)["status"], "own")
        self.assertEqual((project["file"]["width"], project["file"]["height"]), (8, 16))
        theirs = files.create_own("their_stone.png", self.png(12), {"creator": "Ann", "licence": "All Rights Reserved"})
        self.assertEqual(licences.of_project(projects.load(theirs))["status"], "limited")
        self.assertIsNone(files.create_own("copy_of_my_stone.png", self.png(11), {}))  # same content

    def test_files_in_a_folder_keep_their_path_for_the_type(self):
        made = files.create_own("deep.json", b"{}", {}, path="assets/demo/models/block/deep.json")
        project = projects.load(made)
        self.assertEqual((project["category"], project["file"]["type"], project["file"]["path"]), ("model", "model", "assets/demo/models/block/deep.json"))

    def test_sound_length_is_read_from_the_ogg_headers(self):
        import struct
        head = b"OggS" + bytes(22) + bytes([1]) + b"vorbis" + struct.pack("<I", 0) + bytes([2]) + struct.pack("<I", 44100)
        last = b"OggS" + bytes([0, 4]) + struct.pack("<q", 132300) + bytes(10)
        self.assertEqual(files.ogg_seconds(head + last), 3.0)
        self.assertIsNone(files.ogg_seconds(b"not an ogg"))
        made = files.create_own("boom.ogg", head + last, {})
        self.assertEqual(projects.load(made)["file"]["seconds"], 3.0)

    def test_proposed_names_carry_the_kind_name_and_size(self):
        made = files.create_own("Mossy Rock.png", self.png(13), {})
        project = projects.load(made)
        names = [files.styled_name(project, i) for i in range(3)]
        self.assertEqual(names[0], "texture_mossy_rock_8x16")
        self.assertEqual(names[1], "mossy_rock")
        self.assertEqual(names[2], "0008_mossy_rock_texture")
        self.assertEqual(files.styled_name(project, 3), names[0])
        for name in names:
            self.assertEqual(naming.clean(name), name)

    def test_renaming_keeps_the_extension_and_refuses_a_clash_or_an_empty_name(self):
        made = files.create_own("rename_me.png", self.png(14), {})
        self.assertEqual(files.rename_file(made, "Texture Moss 8x16"), "texture_moss_8x16.png")
        self.assertEqual(projects.file_path(made).name, "texture_moss_8x16.png")
        self.assertEqual(files.rename_file(made, "texture_moss_8x16"), "texture_moss_8x16.png")
        with self.assertRaises(files.FileProblem):
            files.rename_file(made, "  ")
        self.assertEqual(files.rename_file(made, "../../escape"), "escape.png")  # a path becomes a plain name
        self.assertEqual(projects.file_path(made).parent, projects._path(made) / "file")


class FileViewers(unittest.TestCase):
    JAR = mc_assets.Jar()

    def own(self, name, data, path=""):
        made = files.create_own(name, data, {}, path=path)
        self.assertIsNotNone(made)
        return projects.load(made)

    def png(self, width, height, shade):
        from PIL import Image
        out = io.BytesIO()
        Image.new("RGBA", (width, height), (shade, 60, 90, 255)).save(out, "PNG")
        return out.getvalue()

    def test_json_is_laid_out_and_checked_for_its_type(self):
        good = files.json_check('{"pools":[{"rolls":1,"entries":[]}]}', "loot", self.JAR.known_ids())
        self.assertTrue(good["ok"])
        self.assertIn('\n  "pools"', good["pretty"])
        self.assertEqual(good["notes"], [])
        broken = files.json_check('{"pools": [ }', "loot")
        self.assertFalse(broken["ok"])
        self.assertIn("line 1", broken["error"])
        self.assertIsNone(broken["pretty"])
        self.assertIn('"rolls"', " ".join(files.json_check('{"pools":[{"entries":[]}]}', "loot")["notes"]))
        self.assertIn('"type"', " ".join(files.json_check("{}", "recipe")["notes"]))
        typo = files.json_check('{"pools":[{"rolls":1,"entries":[{"type":"minecraft:item","name":"minecraft:applee"},{"type":"minecraft:item","name":"minecraft:apple"}]}]}', "loot", self.JAR.known_ids())
        self.assertIn("minecraft:applee", " ".join(typo["notes"]))
        self.assertNotIn("minecraft:apple,", " ".join(typo["notes"]) + ",")
        other = files.json_check('{"pools":[{"rolls":1,"entries":[{"type":"minecraft:loot_table","name":"minecraft:chests/x"}]}]}', "loot", self.JAR.known_ids())
        self.assertEqual(other["notes"], [])  # a loot table reference is not an item name

    def test_texture_facts_notes_and_animation_frames(self):
        flat = files.texture_info(self.png(16, 16, 20))
        self.assertEqual((flat["width"], flat["frames"], flat["colours"], flat["notes"]), (16, 1, 1, []))
        strip = files.texture_info(self.png(16, 64, 21))
        self.assertEqual((strip["frames"], strip["frame_size"], strip["notes"]), (4, 16, []))
        odd = files.texture_info(self.png(12, 12, 22))
        self.assertIn("power of two", " ".join(odd["notes"]))
        wide = files.texture_info(self.png(16, 24, 23))
        self.assertIn("Not square", " ".join(wide["notes"]))

    def test_sound_facts(self):
        import struct
        head = b"OggS" + bytes(22) + bytes([1]) + b"vorbis" + struct.pack("<I", 0) + bytes([2]) + struct.pack("<I", 44100)
        data = head + b"OggS" + bytes([0, 4]) + struct.pack("<q", 88200) + bytes(40000)
        found = files.ogg_info(data)
        self.assertEqual((found["seconds"], found["rate"], found["channels"]), (2.0, 44100, 2))
        self.assertGreater(found["kbps"], 100)
        project = self.own("viewer_boom.ogg", data)
        self.assertEqual(files.info(project, self.JAR)["kind"], "sound")

    def test_info_by_kind(self):
        project = self.own("viewer_tex.png", self.png(16, 16, 30))
        self.assertEqual((files.info(project)["kind"], files.info(project)["width"]), ("texture", 16))
        broken = self.own("viewer_bad.json", b'{"pools": [', path="data/demo/loot_table/viewer_bad.json")
        self.assertFalse(files.info(broken, self.JAR)["ok"])
        fine = self.own("viewer_ok.json", b'{"type": "minecraft:crafting_shapeless", "ingredients": [], "result": {"id": "minecraft:applee"}}', path="data/demo/recipe/viewer_ok.json")
        result = files.info(fine, self.JAR)
        self.assertTrue(result["ok"])
        self.assertIn("minecraft:applee", " ".join(result["notes"]))
        text = self.own("viewer_note.txt", b"hello")
        self.assertEqual(files.info(text)["kind"], "other")

    def test_a_model_file_is_baked_for_the_3d_view(self):
        model = {"parent": "block/cube_all", "textures": {"all": "minecraft:block/stone"}}
        project = self.own("viewer_cube.json", json.dumps(model).encode(), path="assets/demo/models/block/viewer_cube.json")
        shown = files.model_preview(project, self.JAR)
        self.assertEqual(shown["missing"], [])
        self.assertEqual(shown["note"], "")
        self.assertEqual(len(shown["data"]["states"]["preview"]["quads"]), 6)
        self.assertEqual(shown["data"]["blocks"], [[0, 0, 0, "preview"]])
        own_texture = {"parent": "block/cube_all", "textures": {"all": "demo:block/not_in_game"}}
        pink = files.model_preview(self.own("viewer_pink.json", json.dumps(own_texture).encode(), path="assets/demo/models/block/viewer_pink.json"), self.JAR)
        self.assertEqual(pink["missing"], ["demo:block/not_in_game"])
        self.assertIn("shown pink", pink["note"])
        self.assertTrue(all(q["tex"] == "block/magenta_concrete" for q in pink["data"]["states"]["preview"]["quads"]))
        shape = {"textures": {"t": "block/oak_log"}, "elements": [{"from": [0, 0, 0], "to": [8, 8, 8], "faces": {"up": {"texture": "#t"}, "north": {"texture": "#t"}}}]}
        custom = files.model_preview(self.own("viewer_shape.json", json.dumps(shape).encode(), path="assets/demo/models/block/viewer_shape.json"), self.JAR)
        self.assertEqual(len(custom["data"]["states"]["preview"]["quads"]), 2)
        stateful = {"variants": {"": {"model": "minecraft:block/stone"}}}
        self.assertEqual(len(files.model_preview(self.own("viewer_state.json", json.dumps(stateful).encode(), path="assets/demo/blockstates/viewer_state.json"), self.JAR)["data"]["states"]["preview"]["quads"]), 6)

    def test_a_model_with_an_unknown_parent_or_broken_json_says_so(self):
        orphan = self.own("viewer_orphan.json", b'{"parent": "demo:block/base"}', path="assets/demo/models/block/viewer_orphan.json")
        with self.assertRaisesRegex(files.FileProblem, "demo:block/base. isn't in the game"):
            files.model_preview(orphan, self.JAR)
        broken = self.own("viewer_broken.json", b'{"parent": ', path="assets/demo/models/block/viewer_broken.json")
        with self.assertRaisesRegex(files.FileProblem, "Not valid JSON"):
            files.model_preview(broken, self.JAR)


class ResourcePacks(unittest.TestCase):
    def test_modrinth_resource_packs_are_searched_without_the_builds_filter(self):
        url = online.search_url("faithful", "resourcepack", True, 0)
        self.assertIn("project_type%3Aresourcepack", url)
        self.assertNotIn("worldgen", url)
        self.assertIn("worldgen", online.search_url("faithful", "datapack", True, 0))
        self.assertEqual(online.SOURCES["modrinth"]["types"][2], ["resourcepack", "Resource packs"])

    def test_a_custom_licence_is_pointed_out(self):
        hit = online.clean_hit({"project_id": "AbCd1234", "slug": "faithful-32x", "title": "Faithful 32x", "author": "F", "license": "LicenseRef-Faithful-License",
                                "downloads": 5, "project_type": "resourcepack"})
        self.assertEqual(hit["kind"], "resourcepack")
        self.assertIn("custom licence", hit["warning"])
        self.assertEqual(online.licence_warning("MIT"), "")
        self.assertIn("All rights reserved", online.licence_warning("LicenseRef-All-Rights-Reserved"))
        self.assertIn("No licence", online.licence_warning("LicenseRef-Unknown"))

    def test_many_textures_are_saved_from_one_opened_archive(self):
        from PIL import Image
        raw = io.BytesIO()
        with zipfile.ZipFile(raw, "w") as z:
            for number in range(60):
                png = io.BytesIO()
                Image.new("RGBA", (16, 16), (number * 4 % 256, 70, 20 + number, 255)).save(png, "PNG")
                z.writestr(f"assets/minecraft/textures/block/pack_tile_{number:02d}.png", png.getvalue())
            z.writestr("pack.mcmeta", "{}")
        data = raw.getvalue()
        opened = []
        real = explore.open_archive
        explore.open_archive = lambda source: (opened.append(1) if not isinstance(source, zipfile.ZipFile) else None) or real(source)
        try:
            picks = [f"assets/minecraft/textures/block/pack_tile_{n:02d}.png" for n in range(60)]
            done = files.save_from_archive(data, "Fancy Pack 16x.zip", picks, {"creator": "Pack Author", "licence": "LicenseRef-Faithful-License", "link": "https://example.test/pack"})
        finally:
            explore.open_archive = real
        self.assertEqual((len(done["ids"]), done["duplicates"], done["skipped"]), (60, 0, []))
        self.assertEqual(len(opened), 1)  # not once per file
        sample = projects.load(done["ids"][0])
        self.assertEqual(sample["credit"]["creator"], "Pack Author")
        self.assertEqual(licences.of_project(sample)["status"], "unknown")  # a custom licence still needs a look
        self.assertEqual(sample["source"]["name"], "Fancy Pack 16x.zip")
        self.assertTrue(done["batch"])
        self.assertTrue(set(done["ids"]) <= {r["id"] for r in library.search(batch=done["batch"])["rows"]})

    def test_reading_from_an_already_open_archive_works(self):
        data = zipfile.ZipFile(io.BytesIO(self.simple()))
        self.assertEqual(explore.read(data, "assets/minecraft/textures/block/a.png"), b"PNG a")
        self.assertEqual(explore.listing(data)["types"][0]["type"], "texture")

    def simple(self):
        raw = io.BytesIO()
        with zipfile.ZipFile(raw, "w") as z:
            z.writestr("assets/minecraft/textures/block/a.png", b"PNG a")
        return raw.getvalue()


class SendToMod(unittest.TestCase):
    def setUp(self):
        self.mod = Path(TEMP) / f"mod_{self.id().split('.')[-1]}"
        shutil.rmtree(self.mod, ignore_errors=True)
        (self.mod / "src/main/resources").mkdir(parents=True)
        self.index = self.mod / "exports.json"
        self.credits_seen = []

    def own(self, name, data, path="", creator=""):
        made = files.create_own(name, data, {"creator": creator, "licence": "MIT"} if creator else {}, path=path)
        self.assertIsNotNone(made)
        return made

    def png(self, shade):
        from PIL import Image
        out = io.BytesIO()
        Image.new("RGBA", (4, 4), (shade, 5, 9, 255)).save(out, "PNG")
        return out.getvalue()

    def rows(self, ids, edits=None):
        return {r["id"]: r for r in send.plan(ids, self.mod, edits)}

    def test_destinations_follow_the_original_path_or_the_kind(self):
        a = self.own("send_a.png", self.png(101))
        b = self.own("Send B.png", self.png(102), "assets/minecraft/textures/item/Send B.png")
        c = self.own("send_c.ogg", b"OggS-c")
        d = self.own("send_d.json", b'{"parent": "block/cube_all"}', "pack-abc123/assets/minecraft/models/block/send_d.json")
        e = self.own("send_e.json", b'{"type": "minecraft:crafting_shapeless", "ingredients": [], "result": {"id": "minecraft:stick"}}')
        f = self.own("send_f.json", b'{"values": ["minecraft:stone"]}', "data/minecraft/tags/block/send_f.json")
        rows = self.rows([a, b, c, d, e, f])
        self.assertEqual(rows[a]["to"], "assets/vestiges/textures/block/send_a.png")
        self.assertEqual(rows[b]["to"], "assets/vestiges/textures/item/send_b.png")
        self.assertEqual(rows[c]["to"], "assets/vestiges/sounds/send_c.ogg")
        self.assertEqual(rows[d]["to"], "assets/vestiges/models/block/send_d.json")
        self.assertEqual(rows[e]["to"], "data/vestiges/recipe/send_e.json")  # no path: the folder comes from what the file is
        self.assertEqual(rows[f]["to"], "data/vestiges/tags/block/send_f.json")
        self.assertTrue(all(r["action"] == "new" for r in rows.values()), [r["note"] for r in rows.values()])

    def test_generated_folders_and_unsafe_paths_are_refused(self):
        loot = self.own("send_loot.json", b'{"pools": []}', "data/minecraft/loot_table/chests/send_loot.json")
        rows = self.rows([loot])
        self.assertEqual(rows[loot]["action"], "blocked")
        self.assertIn("generated", rows[loot]["note"])
        tex = self.own("send_t.png", self.png(103))
        for bad in ("../outside.png", "assets/vestiges/../../x.png", "assets/other/textures/x.png", "assets/vestiges/Textures/X.png", "/etc/x.png", "assets/vestiges/textures/noext",
                    "data/vestiges/loot_table/mine.json", "data/vestiges/vestiges/codex/x.json"):
            self.assertEqual(self.rows([tex], {tex: bad})[tex]["action"], "blocked", bad)
        good = self.rows([tex], {tex: "assets/vestiges/textures/gui/send_t.png"})[tex]
        self.assertEqual((good["action"], good["to"]), ("new", "assets/vestiges/textures/gui/send_t.png"))

    def test_nothing_is_replaced_without_a_yes_and_identical_files_are_left_alone(self):
        tex = self.own("send_o.png", self.png(104), creator="Ann")
        item = {"id": tex, "overwrite": False}
        first = send.apply([item], self.mod, False, self.index, self.credits_seen.append)
        self.assertEqual(first[0]["result"], "created")
        target = self.mod / "src/main/resources/assets/vestiges/textures/block/send_o.png"
        self.assertEqual(target.read_bytes(), self.png(104))
        self.assertEqual(send.apply([item], self.mod, False, self.index, None)[0]["result"], "unchanged")
        target.write_bytes(b"different")
        kept = send.apply([item], self.mod, False, self.index, None)
        self.assertEqual((kept[0]["result"], target.read_bytes()), ("kept", b"different"))
        replaced = send.apply([{"id": tex, "overwrite": True}], self.mod, False, self.index, None)
        self.assertEqual((replaced[0]["result"], target.read_bytes()), ("replaced", self.png(104)))

    def test_limited_or_unknown_licences_need_a_yes_and_the_credits_are_rewritten(self):
        mystery = files.save_from_archive(zipfile_with("assets/demo/textures/block/send_mystery.png", self.png(105)), "mystery.zip", ["assets/demo/textures/block/send_mystery.png"], {})["ids"][0]
        refused = send.apply([{"id": mystery}], self.mod, False, self.index, self.credits_seen.append)
        self.assertEqual(refused[0]["result"], "refused")
        self.assertFalse((self.mod / "src/main/resources/assets/vestiges/textures/block/send_mystery.png").exists())
        self.assertEqual(self.credits_seen, [])
        sent = send.apply([{"id": mystery}], self.mod, True, self.index, self.credits_seen.append)
        self.assertEqual(sent[0]["result"], "created")
        entry = json.loads(self.index.read_text(encoding="utf-8"))["file:assets/vestiges/textures/block/send_mystery.png"]
        self.assertEqual((entry["from"], entry["to"]), (mystery, "assets/vestiges/textures/block/send_mystery.png"))
        self.assertEqual(len(self.credits_seen), 1)

    def test_every_send_is_logged_and_two_assets_cannot_share_a_path(self):
        one = self.own("send_l1.png", self.png(106))
        two = self.own("send_l2.png", self.png(107))
        rows = self.rows([one, two], {one: "assets/vestiges/textures/block/same.png", two: "assets/vestiges/textures/block/same.png"})
        self.assertEqual((rows[one]["action"], rows[two]["action"]), ("blocked", "blocked"))
        self.assertIn("same path", rows[one]["note"])
        send.apply([{"id": one}, {"id": two}], self.mod, False, self.index, None)
        log = (self.mod / "docs/assets/send-log.md").read_text(encoding="utf-8")
        self.assertIn("created: `assets/vestiges/textures/block/send_l1.png`", log)
        self.assertIn("created: `assets/vestiges/textures/block/send_l2.png`", log)

    def test_builds_go_to_the_structure_folder_by_their_saved_name(self):
        made = batches.add_build({"blocks": [[0, y, 0, LOG] for y in range(5)] + [[1, 4, 0, "minecraft:oak_planks"]], "dropped": {}, "notes": []}, {"type": "file", "name": "send_build.nbt"}, {"kind": "tree"}, "bsend")
        unsaved = self.rows([made])[made]
        self.assertEqual(unsaved["action"], "blocked")
        self.assertIn("save a variant", unsaved["note"])
        library.save_variant(made, 1)
        row = self.rows([made])[made]
        name = projects.load(made)["saved"][-1]["name"]
        self.assertEqual((row["action"], row["to"]), ("new", f"data/vestiges/structure/tree/{name}.nbt"))
        done = send.apply([{"id": made}], self.mod, False, self.index, self.credits_seen.append)
        self.assertEqual(done[0]["result"], "created")
        written = (self.mod / "src/main/resources" / row["to"]).read_bytes()
        self.assertEqual(export.read_nbt(written)["size"], [2, 5, 1])
        self.assertIn(name, json.loads(self.index.read_text(encoding="utf-8")))

    def test_nothing_is_written_outside_the_mod_folder(self):
        tex = self.own("send_safe.png", self.png(108))
        done = send.apply([{"id": tex, "to": "../../escape.png"}], self.mod, False, self.index, None)
        self.assertEqual(done[0]["result"], "refused")
        self.assertFalse((self.mod.parent / "escape.png").exists())
        self.assertFalse(any(p.name == "escape.png" for p in self.mod.rglob("*")))


def zipfile_with(path, data):
    raw = io.BytesIO()
    with zipfile.ZipFile(raw, "w") as z:
        z.writestr(path, data)
    return raw.getvalue()


class PortSafety(unittest.TestCase):
    def test_a_port_that_is_taken_is_reported_taken(self):
        import studio
        first = studio.Server(("127.0.0.1", 0), studio.Handler)
        try:
            port = first.server_address[1]
            with self.assertRaises(OSError):
                studio.Server(("127.0.0.1", port), studio.Handler)
        finally:
            first.server_close()


if __name__ == "__main__":
    unittest.main(verbosity=0)
