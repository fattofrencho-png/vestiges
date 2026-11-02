"""The AI connection. Claude and Gemini sit behind one call: complete(kind, system, text, schema, images) -> (dict, Usage).

Settings and keys live in ~/.vestiges-assets/, outside the repo. Keys are never sent to the page.
"""
import base64
import json
import os
import stat
from dataclasses import dataclass
from pathlib import Path

HOME = Path(os.environ.get("VESTIGES_ASSETS_HOME") or Path.home() / ".vestiges-assets")
SETTINGS = HOME / "settings.json"
KEYS = HOME / "keys.json"

# Dollars per million tokens: input, output, cache read. Rates as of 2026-10-06.
CLAUDE_MODELS = {
    "claude-opus-5-5": {"label": "Claude Opus 5.5 (best)", "in": 4.0, "out": 20.0, "cache": 0.20, "fallback": True},
    "claude-sonnet-5-5": {"label": "Claude Sonnet 5.5 (cheaper)", "in": 2.0, "out": 10.0, "cache": 0.20, "fallback": True},
    "claude-haiku-5-5": {"label": "Claude Haiku 5.5 (cheapest)", "in": 0.10, "out": 0.50, "cache": 0.01, "fallback": False},
}
# A rough size for one asset, used only for the guess shown before starting.
TYPICAL_INPUT, TYPICAL_OUTPUT = 20000, 12000
# Gemini prices aren't known here, so its limit counts tokens instead.
TOKEN_LIMIT_PER_DOLLAR = 300000
DEFAULTS = {"provider": "claude", "models": {"claude": "claude-opus-5-5", "gemini": ""}, "send_images": True,
            "budget": 1.0, "safety_fallback": True}


class ProviderError(Exception):
    """A failure with words the author can act on. retry says whether trying again could help."""

    def __init__(self, message, retry=True):
        super().__init__(message)
        self.retry = retry


@dataclass
class Usage:
    input_tokens: int = 0
    output_tokens: int = 0
    cost: float | None = 0.0


def _read(path, default):
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, ValueError):
        return default


def _write_private(path, value):
    HOME.mkdir(parents=True, exist_ok=True)
    temp = path.with_suffix(".tmp")
    temp.write_text(json.dumps(value, indent=1), encoding="utf-8")
    os.chmod(temp, stat.S_IRUSR | stat.S_IWUSR)
    temp.replace(path)


def load_settings():
    saved = _read(SETTINGS, {})
    merged = {**DEFAULTS, **saved}
    merged["models"] = {**DEFAULTS["models"], **saved.get("models", {})}
    return merged


def save_settings(changes):
    settings = load_settings()
    for key in ("provider", "send_images", "budget", "safety_fallback"):
        if key in changes:
            settings[key] = changes[key]
    settings["models"].update(changes.get("models", {}))
    _write_private(SETTINGS, settings)


def set_key(provider, key):
    keys = _read(KEYS, {})
    if key:
        keys[provider] = key.strip()
    else:
        keys.pop(provider, None)
    _write_private(KEYS, keys)


def has_key(provider):
    return bool(_read(KEYS, {}).get(provider))


def _key(provider):
    key = _read(KEYS, {}).get(provider)
    if not key:
        raise ProviderError(f"Add your {provider.title()} API key in Settings first.", retry=False)
    return key


def fake_enabled():
    return os.environ.get("VESTIGES_FAKE_AI") == "1"


def guess_cost(provider, model):
    if provider == "claude" and model in CLAUDE_MODELS:
        rate = CLAUDE_MODELS[model]
        return (TYPICAL_INPUT * rate["in"] + TYPICAL_OUTPUT * rate["out"]) / 1_000_000
    return 0.0 if provider == "test" else None


def _parse(body):
    try:
        value = json.loads(body)
    except ValueError as problem:
        raise ProviderError("The AI's answer wasn't valid. Try again.") from problem
    if not isinstance(value, dict):
        raise ProviderError("The AI's answer wasn't valid. Try again.")
    return value


OK_SCHEMA = {"type": "object", "properties": {"ok": {"type": "boolean"}}, "required": ["ok"], "additionalProperties": False}


class Claude:
    name = "claude"

    def __init__(self, model):
        self.model = model

    def complete(self, kind, system, text, schema, images=()):
        import anthropic
        content = [{"type": "image", "source": {"type": "base64", "media_type": "image/png", "data": data}} for data in images]
        content.append({"type": "text", "text": text})
        effort = "high" if kind in ("plan", "review") else "medium"
        rate = CLAUDE_MODELS.get(self.model, {})
        options = {}
        if rate.get("fallback") and load_settings()["safety_fallback"]:
            options = {"betas": ["server-side-fallback-2026-07-01"], "extra_body": {"fallbacks": "default"}}
        client = anthropic.Anthropic(api_key=_key("claude"), base_url=os.environ.get("VESTIGES_CLAUDE_URL") or None)
        try:
            with client.beta.messages.stream(
                model=self.model,
                max_tokens=32000,
                system=[{"type": "text", "text": system, "cache_control": {"type": "ephemeral"}}],
                messages=[{"role": "user", "content": content}],
                output_config={"effort": effort, "format": {"type": "json_schema", "schema": schema}},
                **options,
            ) as stream:
                message = stream.get_final_message()
        except anthropic.AuthenticationError as problem:
            raise ProviderError("Claude rejected the API key. Check it in Settings.", retry=False) from problem
        except anthropic.PermissionDeniedError as problem:
            raise ProviderError("This API key may not use that model. Pick another in Settings.", retry=False) from problem
        except anthropic.NotFoundError as problem:
            raise ProviderError(f"Claude doesn't know the model {self.model}. Pick another in Settings.", retry=False) from problem
        except anthropic.RateLimitError as problem:
            raise ProviderError("Claude says too many requests or no credit left. Wait a little, or check your account.") from problem
        except anthropic.BadRequestError as problem:
            raise ProviderError(f"Claude refused the request: {problem.message}", retry=False) from problem
        except anthropic.APIStatusError as problem:
            raise ProviderError(f"Claude had a problem ({problem.status_code}). Try again.") from problem
        except anthropic.APIConnectionError as problem:
            raise ProviderError("Couldn't reach Claude. Check your internet connection.") from problem
        usage = message.usage
        created = getattr(usage, "cache_creation_input_tokens", 0) or 0
        read = getattr(usage, "cache_read_input_tokens", 0) or 0
        cost = None
        if rate:
            cost = (usage.input_tokens * rate["in"] + created * rate["in"] * 1.25 + read * rate["cache"]
                    + usage.output_tokens * rate["out"]) / 1_000_000
        spent = Usage(usage.input_tokens + created + read, usage.output_tokens, cost)
        if message.stop_reason == "refusal":
            raise ProviderError("Claude declined this request. Try rewording it.", retry=False)
        if message.stop_reason == "max_tokens":
            raise ProviderError("Claude's answer was too long and got cut off. Try a smaller asset.", retry=False)
        body = "".join(block.text for block in message.content if block.type == "text")
        return _parse(body), spent

    def test(self):
        return self.complete("test", "Answer with JSON.", 'Reply with {"ok": true}.', OK_SCHEMA)


class Gemini:
    name = "gemini"

    def __init__(self, model):
        self.model = model

    def complete(self, kind, system, text, schema, images=()):
        from google import genai
        from google.genai import errors, types
        if not self.model:
            raise ProviderError("Pick a Gemini model in Settings first.", retry=False)
        client = genai.Client(api_key=_key("gemini"))
        contents = [types.Part.from_bytes(data=base64.b64decode(d), mime_type="image/png") for d in images] + [text]
        try:
            response = client.models.generate_content(
                model=self.model, contents=contents,
                config=types.GenerateContentConfig(system_instruction=system, response_mime_type="application/json",
                                                   response_json_schema=schema))
        except errors.ClientError as problem:
            if problem.code in (401, 403) or "API key" in str(problem):
                raise ProviderError("Gemini rejected the API key. Check it in Settings.", retry=False) from problem
            if problem.code == 429:
                raise ProviderError("Gemini says too many requests or no credit left. Wait a little.") from problem
            raise ProviderError(f"Gemini refused the request: {problem}", retry=False) from problem
        except errors.APIError as problem:
            raise ProviderError("Gemini had a problem. Try again.") from problem
        except OSError as problem:
            raise ProviderError("Couldn't reach Gemini. Check your internet connection.") from problem
        meta = response.usage_metadata
        spent = Usage((meta.prompt_token_count or 0) if meta else 0, (meta.candidates_token_count or 0) if meta else 0, None)
        if not response.text:
            raise ProviderError("Gemini returned nothing. Try rewording the request.", retry=False)
        return _parse(response.text), spent

    def test(self):
        return self.complete("test", "Answer with JSON.", 'Reply with {"ok": true}.', OK_SCHEMA)


def gemini_models():
    from google import genai
    client = genai.Client(api_key=_key("gemini"))
    names = [model.name.replace("models/", "") for model in client.models.list()
             if "generateContent" in (model.supported_actions or []) and "gemini" in model.name]
    return sorted(names, reverse=True)


class TestAI:
    """A scripted AI for tests and practice. It costs nothing and never leaves this computer."""
    name = "test"
    PLAN = {"seed": 7, "ops": [
        {"tool": "column", "args_json": json.dumps({"x": 0, "z": 0, "from_y": 0, "to_y": 10, "blocks": "cobblestone_wall"})},
        {"tool": "column", "args_json": json.dumps({"x": 1, "z": 0, "from_y": 0, "to_y": 3, "blocks": "barrier"})},
        {"tool": "blob", "args_json": json.dumps({"center": [0, 11, 0], "radii": [2, 2, 2], "blocks": [["oak_leaves", 0.5], ["azalea_leaves", 0.25], ["moss_block", 0.25]]})},
    ]}
    TOO_TALL = {"seed": 7, "ops": [{"tool": "column", "args_json": json.dumps({"x": 0, "z": 0, "from_y": 0, "to_y": 30, "blocks": "oak_log"})}]}

    def __init__(self, model=""):
        self.model = model

    def complete(self, kind, system, text, schema, images=()):
        usage = Usage(1000, 500, 0.0)
        script = text.split("AUTHOR'S REQUEST:", 1)[-1].split("\n", 1)[0]
        if kind == "questions":
            return {"title": "Test tree", "questions": [
                {"id": "leaves", "text": "Which leaves?", "kind": "multi", "required": True, "options": ["Oak", "Azalea", "Moss"], "min": None, "max": None, "help": "Pick at least one."},
                {"id": "height", "text": "How tall?", "kind": "range", "required": True, "options": [], "min": 12, "max": 15, "help": "In blocks."},
                {"id": "mood", "text": "What mood?", "kind": "select", "required": False, "options": ["Calm", "Wild"], "min": None, "max": None, "help": ""},
                {"id": "extra", "text": "Anything else?", "kind": "text", "required": False, "options": [], "min": None, "max": None, "help": ""},
            ]}, usage
        if kind == "brief":
            return {"summary": "A tree 12 to 15 blocks high and at most 5 wide, with mixed leaves, moss, and a trunk of walls and barriers.",
                    "points": ["Height 12 to 15", "At most 5 wide", "Leaves: oak, azalea, moss"],
                    "constraints": {"min_height": 12, "max_height": 15, "min_width": None, "max_width": 5,
                                    "required_blocks": ["cobblestone_wall", "barrier", "moss_block"], "forbidden_blocks": []}}, usage
        if kind == "plan":
            if "failplan" in script and "ATTEMPT 1" in text:
                return {"notes": "First try is too tall.", "plan": self.TOO_TALL}, usage
            return {"notes": "A wall trunk with a mossy crown.", "plan": self.PLAN}, usage
        if kind == "review":
            if "askuser" in script and "ANSWERS TO FOLLOW-UP" not in text:
                return {"passed": False, "verdict": "I can't tell which way it should lean.", "problems": ["The lean isn't clear."],
                        "needs_author": True, "questions": [{"id": "lean", "text": "Which way should it lean?", "kind": "select", "required": True, "options": ["Left", "Right"], "min": None, "max": None, "help": ""}]}, usage
            if "alwaysfail" in script:
                return {"passed": False, "verdict": "It still looks wrong.", "problems": ["The crown looks flat."], "needs_author": False, "questions": []}, usage
            return {"passed": True, "verdict": f"Matches the brief. Looked at {len(images)} views.", "problems": [], "needs_author": False, "questions": []}, usage
        raise ProviderError("Unknown step")

    def test(self):
        return {"ok": True}, Usage()


def get(provider, model):
    if provider == "test" and fake_enabled():
        return TestAI(model)
    if provider == "claude":
        return Claude(model or DEFAULTS["models"]["claude"])
    if provider == "gemini":
        return Gemini(model)
    raise ProviderError("Pick an AI in Settings first.", retry=False)
