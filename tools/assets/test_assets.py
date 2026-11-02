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
import batches
import export
import import_formats
import library
import mc_assets
import naming
import pipeline
import plan_tools
import projects
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


if __name__ == "__main__":
    unittest.main(verbosity=0)
