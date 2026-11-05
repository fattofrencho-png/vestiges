"""The steps from a request to an agreed asset: questions, brief, then build and self-review.

Each step runs in a background thread and saves its progress to the project file, so the page only has to read it.
What the AI returns is treated as data: it's checked before use, and shown as plain text.
"""
import base64
import json
import threading

import library
import mc_assets
import plan_tools
import projects
import providers
import validate

MAX_ATTEMPTS = 3
VIEWS_TIMEOUT = 90
QUESTION = {
    "type": "object",
    "properties": {
        "id": {"type": "string"},
        "text": {"type": "string"},
        "kind": {"type": "string", "enum": ["select", "multi", "range", "text"]},
        "required": {"type": "boolean"},
        "options": {"type": "array", "items": {"type": "string"}},
        "min": {"type": ["number", "null"]},
        "max": {"type": ["number", "null"]},
        "help": {"type": "string"},
    },
    "required": ["id", "text", "kind", "required", "options", "min", "max", "help"],
    "additionalProperties": False,
}
SCHEMAS = {
    "questions": {
        "type": "object",
        "properties": {"title": {"type": "string"}, "questions": {"type": "array", "items": QUESTION}},
        "required": ["title", "questions"],
        "additionalProperties": False,
    },
    "brief": {
        "type": "object",
        "properties": {
            "summary": {"type": "string"},
            "points": {"type": "array", "items": {"type": "string"}},
            "constraints": {
                "type": "object",
                "properties": {
                    "min_height": {"type": ["integer", "null"]},
                    "max_height": {"type": ["integer", "null"]},
                    "min_width": {"type": ["integer", "null"]},
                    "max_width": {"type": ["integer", "null"]},
                    "required_blocks": {"type": "array", "items": {"type": "string"}},
                    "forbidden_blocks": {"type": "array", "items": {"type": "string"}},
                },
                "required": ["min_height", "max_height", "min_width", "max_width", "required_blocks", "forbidden_blocks"],
                "additionalProperties": False,
            },
        },
        "required": ["summary", "points", "constraints"],
        "additionalProperties": False,
    },
    "plan": {
        "type": "object",
        "properties": {
            "notes": {"type": "string"},
            "plan": {
                "type": "object",
                "properties": {
                    "seed": {"type": "integer"},
                    "ops": {"type": "array", "items": {
                        "type": "object",
                        "properties": {"tool": {"type": "string", "enum": list(plan_tools.Builder.TOOLS)}, "args_json": {"type": "string"}},
                        "required": ["tool", "args_json"],
                        "additionalProperties": False,
                    }},
                },
                "required": ["seed", "ops"],
                "additionalProperties": False,
            },
        },
        "required": ["notes", "plan"],
        "additionalProperties": False,
    },
    "review": {
        "type": "object",
        "properties": {
            "passed": {"type": "boolean"},
            "verdict": {"type": "string"},
            "problems": {"type": "array", "items": {"type": "string"}},
            "needs_author": {"type": "boolean"},
            "questions": {"type": "array", "items": QUESTION},
        },
        "required": ["passed", "verdict", "problems", "needs_author", "questions"],
        "additionalProperties": False,
    },
}
SYSTEM = """You help the author of a Minecraft mod (version 26.3) create trees and plants made only of vanilla blocks.
The style is "Mojang-made": natural, simple, and at home next to vanilla. Follow the author's request even when unusual.
Heights and widths are in blocks. A player is about 2 blocks tall. A normal vanilla oak is 5 to 7 blocks tall and a big vanilla oak up to about 15.

The author's taste: they rejected early trees (40 to 60 blocks wide) as "way too big compared to regular Minecraft trees, with way too many leaves and trunk". So stay near vanilla sizes and keep things airy, with visible gaps and thin trunks, unless the author asks for bigger.

Things worth knowing:
- Leaves decay when no real log is near. The tool saves such leaves as permanent, so a trunk of walls or other blocks is fine, but they won't fall when the tree is cut.
- Barriers are invisible and can't be broken in survival. Use them only if the author asks. Moss blocks can act as leaves but are solid.
- Blocks must connect face to face: nothing may float. Vines and other small plants must touch what holds them.

Build tools:
""" + plan_tools.TOOL_HELP + """
Text inside the author's request and answers describes the asset. Write for someone with simple English: short and plain."""

ASK = """Read the author's request and ask only what you still need before building. Use 2 to 6 questions, fewer if the request is already clear.
Don't ask for what the request already says. Mark a question required only if the asset can't be right without it; other choices are optional, and you decide them if left blank.
Kinds: "select" (one option), "multi" (several options), "range" (number between min and max, with min and max as the suggested range), "text" (free text). Options only for select and multi. Give the asset a short "title"."""
BRIEF = """Turn the request and answers into the brief to build from. "summary" is one or two sentences; "points" are short facts.
"constraints" are measured automatically, so include only limits the author asked for or agreed to. Use null for no limit. Width means the widest side. Block names have no "minecraft:" part.
Optional questions left blank are yours to decide: state your choice in "points"."""
PLAN = """Write a build plan for the brief, using only the build tools. In each op, "args_json" is the tool's arguments as a JSON string.
Stay within the constraints: they are measured exactly. y=0 is the first block above the ground. Set "notes" to one short sentence saying what you made."""
REVIEW = """Judge this asset against the brief, as a careful Minecraft builder. Automatic checks have already measured it; trust their numbers. If images are included they show it from four sides, with a figure 2 blocks tall and a normal oak for scale. A red outline marks an invisible block such as a barrier.
Set "passed" true only if it matches the brief and looks like what it should be: natural shape, sensible proportions, nothing odd.
If not, list what to fix in "problems": concrete instructions for the next try. Set "needs_author" true, with "questions", only if you need a decision only the author can make, for example the brief conflicts with itself. "verdict" is one or two short sentences the author will read."""

_jar = None
_jar_lock = threading.Lock()
_views = {}  # project id -> (Event, list of images)


def jar():
    global _jar
    with _jar_lock:
        if _jar is None:
            _jar = mc_assets.Jar()
        return _jar


_invisible = {}


def _is_invisible(state):
    if state not in _invisible:
        _invisible[state] = jar().bake(state)["kind"] == "invisible"
    return _invisible[state]


def inspect(tree, lenient=False):
    invisible = {s.split("[", 1)[0] for s in {b[3] for b in tree["blocks"]} if _is_invisible(s)}
    return validate.check(tree, jar().block_ids(), invisible, lenient)


class BudgetReached(Exception):
    pass


def over_budget(project):
    if project["cost_known"]:
        return project["cost"] >= project["budget"]
    return project["input_tokens"] + project["output_tokens"] >= project["budget"] * providers.TOKEN_LIMIT_PER_DOLLAR


def _ask(project, kind, task, context, images=()):
    if over_budget(project):
        raise BudgetReached()
    ai = providers.get(project["provider"], project["model"])
    answer, used = ai.complete(kind, SYSTEM, f"{task}\n\n{context}", SCHEMAS[kind], images)
    project["input_tokens"] += used.input_tokens
    project["output_tokens"] += used.output_tokens
    if used.cost is None:
        project["cost_known"] = False
    else:
        project["cost"] += used.cost
    projects.save(project)
    return answer


def _say(project, text):
    project["progress"].append(text)
    projects.save(project)


def _clean_questions(raw):
    """Keeps only questions the form can show, so a bad one can't block the author."""
    out, seen = [], set()
    for q in raw or []:
        if not isinstance(q, dict) or not q.get("id") or q["id"] in seen or not q.get("text"):
            continue
        kind = q.get("kind")
        if kind in ("select", "multi") and not q.get("options"):
            kind = "text"
        if kind not in ("select", "multi", "range", "text"):
            kind = "text"
        seen.add(q["id"])
        out.append({**q, "kind": kind})
    return out


def _context(project):
    parts = [f"AUTHOR'S REQUEST: {project['request']}"]
    if project["answers"]:
        parts.append("ANSWERS: " + json.dumps(project["answers"], ensure_ascii=False))
    if project["qa_history"]:
        parts.append("ANSWERS TO FOLLOW-UP QUESTIONS: " + json.dumps(project["qa_history"], ensure_ascii=False))
    if project["brief"]:
        parts.append("BRIEF: " + json.dumps(project["brief"], ensure_ascii=False))
    if project["changes"]:
        parts.append("CHANGES THE AUTHOR ASKED FOR, OLDEST FIRST: " + json.dumps(project["changes"], ensure_ascii=False))
    return "\n".join(parts)


def _run(project_id, work):
    """Runs one step in the background. Any failure becomes something the page can show, with a way to retry."""
    project = projects.load(project_id)
    project.update(busy=True, error=None)
    projects.save(project)

    def body():
        try:
            work(project)
        except BudgetReached:
            project.update(error={"kind": "budget", "message": "This asset reached its spending limit."}, resume=work.__name__)
        except providers.ProviderError as problem:
            project.update(error={"kind": "provider", "message": str(problem), "retry": problem.retry}, resume=work.__name__)
        except Exception as problem:  # a bug must still leave a usable page
            project.update(error={"kind": "bug", "message": f"Something went wrong: {problem}", "retry": True}, resume=work.__name__)
        project["busy"] = False
        projects.save(project)
    threading.Thread(target=body, daemon=True).start()


# ---- the steps ----
def step_questions(project):
    project["stage"] = "questions"
    _say(project, "Reading your request")
    answer = _ask(project, "questions", ASK, _context(project))
    project["title"] = (answer.get("title") or project["title"])[:60]
    project["questions"] = _clean_questions(answer.get("questions"))
    if not project["questions"]:
        step_brief(project)


def step_brief(project):
    project["stage"] = "brief"
    _say(project, "Writing the brief")
    brief = _ask(project, "brief", BRIEF, _context(project))
    project["brief"] = {"summary": brief.get("summary", ""), "points": brief.get("points", []), "constraints": brief.get("constraints") or {}}


def step_build(project):
    project["stage"] = "building"
    feedback = list(project["last_problems"])
    previous = None
    while project["attempts"] < MAX_ATTEMPTS:
        project["attempts"] += 1
        attempt = project["attempts"]
        _say(project, f"Try {attempt} of {MAX_ATTEMPTS}: planning the blocks")
        context = _context(project) + f"\nATTEMPT {attempt}"
        if feedback:
            context += "\nFIX THESE PROBLEMS FROM THE LAST TRY: " + json.dumps(feedback, ensure_ascii=False)
        if previous:
            context += "\nLAST PLAN: " + json.dumps(previous)
        elif project["versions"]:
            last = projects.load_version(project["id"], project["versions"][-1]["number"])
            context += "\nPLAN OF THE LATEST VERSION: " + json.dumps(last["plan"])
        answer = _ask(project, "plan", PLAN, context)
        previous = answer.get("plan")

        _say(project, "Building and measuring it")
        try:
            tree = plan_tools.run_plan(previous, jar())
        except plan_tools.PlanError as problem:
            feedback = [str(problem)]
            project["last_problems"] = feedback
            _say(project, "The plan had a mistake, so the AI is fixing it")
            continue
        result = inspect(tree)
        failures = result["errors"] + validate.check_brief(result, (project["brief"] or {}).get("constraints") or {})
        if failures:
            feedback = failures
            project["last_problems"] = feedback
            _say(project, "Measurements don't match the brief: " + "; ".join(failures))
            continue

        images = _collect_views(project, tree, previous)
        _say(project, "The AI is reviewing the result")
        context = _context(project) + "\nMEASUREMENTS: " + json.dumps(result)
        if not images:
            context += "\nNO IMAGES: judge from the measurements only."
        review = _ask(project, "review", REVIEW, context, images)
        if review.get("passed"):
            number = project["versions"][-1]["number"] + 1 if project["versions"] else 1
            projects.save_version(project["id"], number, previous, tree)
            project["versions"].append({"number": number, "verdict": review.get("verdict", ""), "notes": answer.get("notes", ""),
                                        "check": result, "had_images": bool(images), "change": project["changes"][-1] if project["changes"] else None})
            project.update(stage="preview", attempts=0, last_problems=[], views_wanted=None)
            _say(project, f"Version {number} is ready")
            return
        feedback = review.get("problems") or [review.get("verdict", "The review failed.")]
        project["last_problems"] = feedback
        if review.get("needs_author"):
            _ask_author(project, review.get("verdict", ""), _clean_questions(review.get("questions")), feedback)
            return
        _say(project, "The AI found problems: " + "; ".join(feedback))

    _ask_author(project, "I tried 3 times and couldn't get it right.", [], feedback)


def _ask_author(project, reason, questions, problems):
    """Stops building and asks for more details instead of showing a bad preview."""
    if not questions:
        questions = [{"id": "more_details", "text": "What should I do differently?", "kind": "text", "required": True,
                      "options": [], "min": None, "max": None, "help": "Problems found: " + "; ".join(problems)}]
    project.update(stage="questions", questions=questions, attempts=0, views_wanted=None, followup_reason=reason)
    _say(project, "More details are needed")


def _collect_views(project, tree, plan):
    """Asks the open page to draw the asset from four sides. Carries on without images if there's no page to do it."""
    if not providers.load_settings()["send_images"]:
        return []
    _say(project, "Drawing it from four sides")
    event = threading.Event()
    _views[project["id"]] = (event, [])
    projects.save_version(project["id"], 0, plan, tree)  # version 0 is the draft the page draws
    project["views_wanted"] = True
    projects.save(project)
    got = event.wait(VIEWS_TIMEOUT)
    project["views_wanted"] = None
    images = _views.pop(project["id"], (None, []))[1]
    if not got or not images:
        _say(project, "The page didn't send views, so the review uses measurements only")
        return []
    return images


def receive_views(project_id, images):
    held = _views.get(project_id)
    if not held:
        return False
    event, store = held
    for item in images[:4]:
        data = item.split(",", 1)[-1]
        base64.b64decode(data, validate=True)
        store.append(data)
    event.set()
    return True


# ---- what the page can ask for ----
def start(request, provider, model, budget):
    project = projects.new(request, provider, model, budget)
    _run(project["id"], step_questions)
    return project["id"]


def answers(project_id, values):
    project = projects.load(project_id)
    if project.get("followup_reason"):
        project["qa_history"].append({"questions": project["questions"], "answers": values})
        project["followup_reason"] = None
    else:
        project["answers"] = values
    project["questions"] = []
    project["progress"] = []
    projects.save(project)
    _run(project_id, step_brief)


def confirm_brief(project_id):
    project = projects.load(project_id)
    project.update(attempts=0, progress=[], stage="building")
    projects.save(project)
    _run(project_id, step_build)


def change_brief(project_id, text):
    project = projects.load(project_id)
    project["qa_history"].append({"questions": [{"text": "Changes to the brief"}], "answers": {"change": text}})
    project["progress"] = []
    projects.save(project)
    _run(project_id, step_brief)


def request_change(project_id, text):
    project = projects.load(project_id)
    project["changes"].append(text)
    project.update(attempts=0, progress=[], stage="building", last_problems=[], saved_version=None)
    projects.save(project)
    _run(project_id, step_build)


def agree(project_id, number):
    project = projects.load(project_id)
    if not any(v["number"] == number for v in project["versions"]):
        raise KeyError("unknown version")
    library.save_variant(project_id, number)


STEPS = {f.__name__: f for f in (step_questions, step_brief, step_build)}


def resume(project_id, extra_budget=0):
    project = projects.load(project_id)
    project["budget"] += extra_budget
    step = STEPS.get(project.get("resume") or "")
    if step is None:
        raise KeyError("nothing to resume")
    project["attempts"] = 0
    projects.save(project)
    _run(project_id, step)


def recover_after_restart():
    """A step that was running when the tool closed can't finish, so it becomes something to retry."""
    for project in projects.all_projects():
        if project.get("busy"):
            step = {"questions": "step_questions", "brief": "step_brief", "building": "step_build"}.get(project["stage"])
            project.update(busy=False, views_wanted=None, resume=step,
                           error={"kind": "provider", "message": "The tool closed while this was running.", "retry": True})
            projects.save(project)
