// The step-by-step page. Everything the AI writes is shown with textContent, never as HTML.
import * as viewer from "./viewer.js";

const $ = (id) => document.getElementById(id);
const STEPS = ["Request", "Questions", "Building", "Preview", "Saved"];
const STEP_OF = { questions: 1, brief: 1, building: 2, preview: 3, saved: 4 };
const IMPORT_STEPS = ["Import", "Check", "Adapt", "Saved"];
const WOODS = ["oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry", "pale_oak", "bamboo", "crimson", "warped"];
const DROPPED_WORDS = { block_data: "chests' contents, sign text and other block data", entities: "entities such as item frames and mobs", command_blocks: "command blocks kept as blocks, without their commands" };
let app = null, project = null, screen = "home", draftText = "", compare = new Set(), shown = null, viewsSent = false, poll = null, base = null, blockNames = null;

function el(tag, props = {}, ...children) {
  const node = document.createElement(tag);
  for (const [key, value] of Object.entries(props)) {
    if (key === "on") Object.entries(value).forEach(([name, fn]) => node.addEventListener(name, fn));
    else if (key === "list") node.setAttribute("list", value); // read-only as a property
    else if (key in node) node[key] = value;
    else node.setAttribute(key, value);
  }
  node.append(...children.flat().filter((c) => c !== null && c !== undefined));
  return node;
}
async function get(url) {
  const reply = await fetch(url);
  const body = await reply.json();
  if (!reply.ok) throw new Error(body.error || "Something went wrong");
  return body;
}
async function post(url, body = {}) {
  const reply = await fetch(url, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) });
  const data = await reply.json();
  if (!reply.ok) throw new Error(data.error || "Something went wrong");
  return data;
}
const money = (value) => (value < 0.01 && value > 0 ? "under $0.01" : "$" + value.toFixed(2));
function spent(p) {
  const tokens = (p.input_tokens + p.output_tokens).toLocaleString("en") + " tokens";
  return p.cost_known ? `Spent so far: ${money(p.cost)} (${tokens}, limit ${money(p.budget)})` : `Spent so far: ${tokens}. This AI's prices aren't known here.`;
}

function steps() {
  if (screen === "library" || screen === "settings") { $("steps").replaceChildren(); return; }
  if (screen === "home" || screen === "import" || project?.mode === "import") {
    let step = 0;
    if (screen === "project" && project) step = project.stage === "saved" ? 3 : project.versions.length > 1 ? 2 : 1;
    if (screen === "library" || screen === "settings") step = -1;
    if (step >= 0) {
      $("steps").replaceChildren(...IMPORT_STEPS.map((name, i) => el("span", { className: i === step ? "now" : i < step ? "done" : "", textContent: name })));
      return;
    }
  }
  const now = screen === "project" && project ? STEP_OF[project.stage] ?? 0 : screen === "request" ? 0 : -1;
  $("steps").replaceChildren(...STEPS.map((name, i) => el("span", { className: i === now ? "now" : i < now ? "done" : "", textContent: name })));
}
function page(...children) {
  $("page").classList.remove("wide");
  viewer.hide();
  shown = null;
  $("stage").classList.remove("on");
  $("page").style.display = "";
  $("page").replaceChildren(...children.flat());
}
function showFailure(problem, where) {
  (where || $("page")).prepend(el("div", { className: "notice bad", role: "alert", textContent: problem.message }));
}

// ---- home ----
function drawHome() {
  screen = "home";
  stopPolling();
  project = null;
  steps();
  page(
    el("h1", { textContent: "What do you want to do?" }),
    el("div", { className: "home" },
      el("button", { className: "first", on: { click: () => drawImport() } }, el("b", { textContent: "Import builds" }), "Load files, folders, mods, datapacks or a world box into your library. No AI or key needed."),
      el("button", { on: { click: () => drawRequest().catch((problem) => showFailure(problem)) } }, el("b", { textContent: "Make with AI" }), "Describe a tree or plant, and an AI builds it. Needs an API key.")),
  );
}

// ---- import ----
const BUILD_FILES = /\.(schem|litematic|nbt|jar|zip)$/i;
let importTab = "file", queue = null;

function detailFields() {
  const kind = el("select", {}, el("option", { value: "", textContent: "Guess from the build" }), CATEGORIES().map((c) => el("option", { value: c, textContent: c[0].toUpperCase() + c.slice(1) })));
  const creator = el("input", { type: "text", maxLength: 200, placeholder: "Leave empty for your own builds" });
  const permission = el("input", { type: "text", maxLength: 500, placeholder: "A link, or where they said yes" });
  const licence = el("input", { type: "text", maxLength: 200 });
  return {
    node: [el("h2", { textContent: "Details for everything here (all optional)" }),
      el("label", { className: "field" }, "Kind", kind), el("label", { className: "field" }, "Creator", creator),
      el("label", { className: "field" }, "Permission", permission), el("label", { className: "field" }, "Licence", licence)],
    read: () => ({ kind: kind.value, creator: creator.value, permission: permission.value, licence: licence.value }),
  };
}

async function readEntries(entry, out) {
  if (entry.isFile) {
    out.push(await new Promise((done, fail) => entry.file((file) => { Object.defineProperty(file, "picked", { value: entry.fullPath.replace(/^\//, "") }); done(file); }, fail)));
  } else if (entry.isDirectory) {
    const reader = entry.createReader();
    for (;;) {
      const batch = await new Promise((done, fail) => reader.readEntries(done, fail));
      if (!batch.length) break;
      for (const child of batch) await readEntries(child, out);
    }
  }
}

async function drawImport() {
  screen = "import";
  stopPolling();
  project = null;
  queue = null;
  steps();
  if (!app) app = await get("/api/state");
  const sources = [], failed = [];
  const ticked = new Set();
  const key = (source, item) => source.token + "|" + item.id;
  const status = el("div", { className: "dim", role: "status" });
  const listBox = el("div");
  const filter = el("input", { type: "text", placeholder: "Filter the list", "aria-label": "Filter", style: "flex:1" });
  const go = el("button", { className: "main", textContent: "Import", disabled: true });
  const details = detailFields();
  const refresh = () => {
    go.textContent = ticked.size ? `Import ${ticked.size}` : "Import";
    go.disabled = !ticked.size;
  };
  const shown = () => {
    const words = filter.value.toLowerCase();
    return sources.flatMap((source) => source.items.filter((i) => !words || (source.name + " " + i.label).toLowerCase().includes(words)).map((item) => ({ source, item })));
  };
  const drawList = () => {
    const rows = shown();
    let group = null;
    const nodes = [];
    for (const { source, item } of rows.slice(0, 1000)) {
      if (source.items.length > 1 && group !== source.name) { group = source.name; nodes.push(el("div", { className: "group", textContent: source.name })); }
      const k = key(source, item);
      nodes.push(el("label", {}, el("input", { type: "checkbox", checked: ticked.has(k), on: { change: (e) => { if (e.target.checked) ticked.add(k); else ticked.delete(k); refresh(); } } }), " " + item.label));
    }
    const total = sources.reduce((n, s) => n + s.items.length, 0);
    listBox.replaceChildren(!total ? "" : el("div", {},
      el("div", { className: "tool" }, filter,
        el("button", { textContent: "Tick all shown", on: { click: () => { shown().forEach(({ source, item }) => ticked.add(key(source, item))); drawList(); refresh(); } } }),
        el("button", { textContent: "Untick all shown", on: { click: () => { shown().forEach(({ source, item }) => ticked.delete(key(source, item))); drawList(); refresh(); } } })),
      el("div", { className: "ticks" }, nodes),
      rows.length > 1000 ? el("div", { className: "dim", textContent: `Showing 1,000 of ${rows.length.toLocaleString("en")}. Filter to see the rest. Tick all shown covers them all.` }) : null));
  };
  filter.addEventListener("input", drawList);
  const addFiles = async (files) => {
    const usable = files.filter((f) => BUILD_FILES.test(f.name));
    const ignored = files.length - usable.length;
    let done = 0;
    for (const file of usable) {
      done++;
      status.textContent = `Reading ${done} of ${usable.length}: ${file.name}`;
      const name = file.picked || file.webkitRelativePath || file.name;
      try {
        const reply = await fetch("/api/import/upload?name=" + encodeURIComponent(name), { method: "POST", headers: { "Content-Type": "application/octet-stream" }, body: file });
        const body = await reply.json();
        if (!reply.ok) throw new Error(body.error);
        const source = { token: body.token, name, items: body.items };
        sources.push(source);
        source.items.forEach((item) => ticked.add(key(source, item)));
      } catch (problem) { failed.push({ label: name, reason: problem.message }); }
    }
    const count = sources.reduce((n, s) => n + s.items.length, 0);
    status.textContent = `${count.toLocaleString("en")} builds found.` + (failed.length ? ` ${failed.length} couldn't be read.` : "") + (ignored ? ` ${ignored} other files ignored.` : "");
    drawList();
    refresh();
  };
  const picker = el("input", { type: "file", multiple: true, accept: ".schem,.litematic,.nbt,.jar,.zip", "aria-label": "Choose files", style: "display:none", on: { change: () => { addFiles([...picker.files]); picker.value = ""; } } });
  const folder = el("input", { type: "file", webkitdirectory: true, "aria-label": "Choose a folder", style: "display:none", on: { change: () => { addFiles([...folder.files]); folder.value = ""; } } });
  const drop = el("div", { className: "drop", on: {
    dragover: (e) => { e.preventDefault(); drop.classList.add("over"); },
    dragleave: () => drop.classList.remove("over"),
    drop: async (e) => {
      e.preventDefault();
      drop.classList.remove("over");
      const entries = [...e.dataTransfer.items].map((i) => i.webkitGetAsEntry()).filter(Boolean);
      const files = [];
      for (const entry of entries) await readEntries(entry, files);
      addFiles(files);
    } } },
    el("p", { textContent: "Drop files or folders here" }),
    el("div", { className: "tool", style: "justify-content:center" }, el("button", { textContent: "Choose files", on: { click: () => picker.click() } }), el("button", { textContent: "Choose a folder", on: { click: () => folder.click() } })),
    el("p", { className: "dim", textContent: ".schem, .litematic and structure .nbt files, mod .jar and datapack .zip files" }), picker, folder);
  go.onclick = async () => {
    go.disabled = true;
    const chosen = sources.map((source) => ({ token: source.token, items: source.items.filter((i) => ticked.has(key(source, i))).map((i) => i.id) })).filter((s) => s.items.length);
    try {
      const { id } = await post("/api/batch/create", { sources: chosen, failed, ...details.read() });
      drawBatch(id);
    } catch (problem) { showFailure(problem); refresh(); }
  };
  const fileTab = el("div", {}, drop, status, listBox);

  // A box from a saved world.
  const worldTitle = el("input", { type: "text", maxLength: 60, placeholder: "Optional" });
  const world = el("input", { type: "text", placeholder: "Or paste a world folder" });
  const saves = el("select", { "aria-label": "Saved world" });
  const dimension = el("select", { "aria-label": "Dimension" }, [["overworld", "Overworld"], ["the_nether", "Nether"], ["the_end", "End"]].map(([v, t]) => el("option", { value: v, textContent: t })));
  const corner = () => ["X", "Y", "Z"].map((axis) => el("input", { type: "number", "aria-label": axis, placeholder: axis }));
  const first = corner(), second = corner();
  const worldGo = el("button", { className: "main", textContent: "Import this box", disabled: true });
  const worldPath = () => world.value.trim() || saves.value;
  const worldRefresh = () => { worldGo.disabled = !worldPath() || ![...first, ...second].every((i) => i.value !== ""); };
  [world, ...first, ...second].forEach((i) => i.addEventListener("input", worldRefresh));
  saves.addEventListener("change", worldRefresh);
  worldGo.onclick = async () => {
    worldGo.disabled = true;
    const shared = details.read();
    try {
      const { id } = await post("/api/import/create", { title: worldTitle.value, creator: shared.creator, permission: shared.permission, licence: shared.licence, category: shared.kind, world: worldPath(), dimension: dimension.value, first: first.map((i) => Number(i.value)), second: second.map((i) => Number(i.value)) });
      await openProject(id);
    } catch (problem) { showFailure(problem); worldRefresh(); }
  };
  const worldTab = el("div", {},
    el("label", { className: "field" }, "Saved world", saves),
    el("label", { className: "field" }, "Folder", world),
    el("label", { className: "field" }, "Dimension", dimension),
    el("label", { className: "field" }, "Name", worldTitle),
    el("p", { className: "dim", textContent: "Two opposite corners of the box to copy. In game, F3 shows your position. Your world is only read, never changed." }),
    el("div", { className: "corners" }, el("span", { textContent: "From" }), first, el("span", { textContent: "To" }), second));

  const holder = el("div"), tabs = el("div", { className: "tabs" }), actions = el("div", { className: "row" });
  const showTab = () => {
    tabs.replaceChildren(...[["file", "From files"], ["world", "From a saved world"]].map(([id, text]) => el("button", { className: importTab === id ? "on" : "", textContent: text, on: { click: () => { importTab = id; showTab(); } } })));
    holder.replaceChildren(importTab === "file" ? fileTab : worldTab);
    actions.replaceChildren(el("span", { className: "grow" }), importTab === "file" ? go : worldGo);
  };
  showTab();
  page(el("h1", { textContent: "Import builds" }), tabs, holder, details.node, actions);
  try {
    const found = await get("/api/import/saves");
    saves.replaceChildren(el("option", { value: "", textContent: found.length ? "Choose a world" : "No worlds found" }), ...found.map((w) => el("option", { value: w.path, textContent: w.name })));
  } catch (problem) { showFailure(problem); }
}
const CATEGORIES = () => app?.categories || ["tree"];

async function drawBatch(id) {
  screen = "import";
  stopPolling();
  const batch = await get("/api/batch?id=" + id);
  const percent = batch.total ? Math.round((batch.done / batch.total) * 100) : 100;
  const review = () => openQueue(batch.imported, 0);
  const lists = [];
  if (batch.duplicates.length) lists.push(el("details", {}, el("summary", { textContent: `${batch.duplicates.length} already in the Library, so skipped` }), el("ul", { className: "dim" }, batch.duplicates.slice(0, 300).map((d) => el("li", { textContent: d })))));
  if (batch.failed.length) lists.push(el("details", { open: true }, el("summary", { textContent: `${batch.failed.length} couldn't be imported` }), el("ul", { className: "dim" }, batch.failed.slice(0, 300).map((f) => el("li", { textContent: `${f.label}: ${f.reason}` })))));
  page(
    el("h1", { textContent: batch.running ? "Importing…" : "Import finished" }),
    el("div", { className: "bar" }, el("div", { style: `width:${percent}%` })),
    el("p", { textContent: `${batch.done.toLocaleString("en")} of ${batch.total.toLocaleString("en")} read · ${batch.imported.length.toLocaleString("en")} added` }),
    batch.running ? el("p", { className: "dim", textContent: batch.current }) : null,
    lists,
    batch.running ? null : el("div", { className: "row" },
      el("button", { textContent: "Open in Library", on: { click: () => drawLibrary({ batch: id }) } }),
      el("button", { className: "main", textContent: "Review one by one", disabled: !batch.imported.length, on: { click: review } })),
  );
  if (batch.running) poll = setTimeout(() => drawBatch(id).catch(() => {}), 700);
}

async function openQueue(ids, index) {
  queue = { ids, index };
  await openProject(ids[index]);
}
function queueBar() {
  if (!queue || queue.ids[queue.index] !== project.id) return null;
  const go = (step) => openQueue(queue.ids, queue.index + step);
  return el("div", { className: "queue" },
    el("button", { textContent: "Previous", disabled: queue.index === 0, on: { click: () => go(-1) } }),
    el("b", { textContent: `${queue.index + 1} of ${queue.ids.length}` }),
    el("button", { className: "main", textContent: "Next", disabled: queue.index >= queue.ids.length - 1, on: { click: () => go(1) } }),
    el("button", { textContent: project.status === "skipped" ? "Skipped" : "Skip", title: "Mark as skipped and go on", on: { click: async () => {
      await post("/api/status", { id: project.id, status: project.status === "skipped" ? "review" : "skipped" });
      if (project.status !== "skipped" && queue.index < queue.ids.length - 1) go(1); else refreshProject();
    } } }));
}

// ---- adapt ----
async function drawAdapt() {
  const numbers = project.versions.map((v) => v.number);
  if (!numbers.includes(base)) base = project.saved_version ?? numbers[numbers.length - 1];
  const version = project.versions.find((v) => v.number === base);
  const list = [...compare].filter((n) => numbers.includes(n));
  const show = list.length > 1 ? list.sort((a, b) => a - b) : [base];
  const thisSaved = project.saved.some((entry) => entry.version === base);
  const proposed = await get(`/api/propose?id=${project.id}&v=${base}`);
  const detailTitle = el("input", { type: "text", value: project.title, maxLength: 60 });
  const detailKind = el("select", {}, CATEGORIES().map((c) => el("option", { value: c, textContent: c })));
  detailKind.value = project.category;
  const detailCreator = el("input", { type: "text", value: project.credit?.creator || "" });
  const detailPermission = el("input", { type: "text", value: project.credit?.permission || "" });
  const detailLicence = el("input", { type: "text", value: project.credit?.licence || "" });
  const detailTags = el("input", { type: "text", value: (project.tags || []).join(", ") });
  const s = version.check.stats;
  const used = Object.keys(s.block_counts);
  if (!blockNames) blockNames = await get("/api/blocks");
  const edit = (change) => act(async () => {
    const reply = await post("/api/edit", { id: project.id, base, edit: change });
    base = reply.version;
  });
  const usedSelect = () => el("select", { "aria-label": "Block to change" }, used.map((b) => el("option", { value: b, textContent: b.replaceAll("_", " ") })));

  const source = usedSelect();
  const target = el("input", { type: "text", list: "allBlocks", placeholder: "New block", "aria-label": "New block" });
  const share = el("input", { type: "number", min: 1, max: 100, value: 100, "aria-label": "Percent to change" });
  const fromWood = el("select", { "aria-label": "From wood" }, WOODS.map((w) => el("option", { value: w, textContent: w.replaceAll("_", " ") })));
  const toWood = el("select", { "aria-label": "To wood" }, WOODS.map((w) => el("option", { value: w, textContent: w.replaceAll("_", " ") })));
  toWood.value = "spruce";
  const box = (label) => el("input", { type: "number", placeholder: label, "aria-label": label });
  const low = ["x", "y", "z"].map((a) => box("min " + a)), high = ["x", "y", "z"].map((a) => box("max " + a));
  const ground = el("input", { type: "number", value: 0, "aria-label": "Height of the ground" });
  const thin = el("input", { type: "number", min: 1, max: 90, value: 20, "aria-label": "Percent of leaves to remove" });
  const read = (inputs) => inputs.map((i) => (i.value === "" ? null : Number(i.value)));
  const result = el("div", { className: "dim", role: "status" });
  const blocked = version.check.errors.length > 0;

  const side = [
    el("datalist", { id: "allBlocks" }, blockNames.map((b) => el("option", { value: b }))),
    queueBar(),
    el("h1", { textContent: project.title }),
    el("div", { className: "dim", textContent: `${project.credit?.creator ? "By " + project.credit.creator : "No credit"} · ${project.source?.name || ""}` }),
    el("div", { className: "dim", textContent: `Version ${version.number}: ` + (version.edits.length ? version.edits.join(", ") : "the original") }),
    el("h2", { textContent: "Checks" }),
    version.check.errors.length ? null : el("div", { className: "good-t", textContent: "✓ Vanilla blocks only" }),
    version.check.errors.map((e) => el("div", { className: "warn-t", textContent: "! " + e + ". They show pink. Swap or remove them to use this in the mod." })),
    version.check.warnings.map((w) => el("div", { className: "warn-t", textContent: "! " + w })),
    Object.entries(version.dropped).map(([k, n]) => el("div", { className: "dim", textContent: `Not copied: ${n} ${DROPPED_WORDS[k] || k}` })),
    version.notes.map((n) => el("div", { className: "dim", textContent: n })),
    el("h2", { textContent: "Size" }),
    el("table", {}, [["Height", s.height], ["Width", `${s.width_x} × ${s.width_z}`], ["Blocks", s.blocks.toLocaleString("en")]].map(([a, b]) => el("tr", {}, el("td", { textContent: a }), el("td", { textContent: b })))),
    el("h2", { textContent: "Swap or remove a block" }),
    el("div", { className: "tool" }, source), el("div", { className: "tool" }, target, share, "%"),
    el("div", { className: "tool" },
      el("button", { textContent: "Swap", on: { click: () => edit({ op: "swap", source: source.value, target: target.value.trim(), share: Number(share.value) / 100, seed: numbers.length }) } }),
      el("button", { textContent: "Remove", on: { click: () => edit({ op: "swap", source: source.value, target: "", share: Number(share.value) / 100, seed: numbers.length }) } })),
    el("h2", { textContent: "Swap a whole wood type" }),
    el("div", { className: "tool" }, fromWood, "to", toWood, el("button", { textContent: "Swap", on: { click: () => edit({ op: "family", source: fromWood.value, target: toWood.value }) } })),
    el("h2", { textContent: "Turn or mirror" }),
    el("div", { className: "tool" }, [1, 2, 3].map((t) => el("button", { textContent: `${t * 90}°`, on: { click: () => edit({ op: "rotate", turns: t }) } })),
      el("button", { textContent: "Mirror X", on: { click: () => edit({ op: "mirror", axis: "x" }) } }), el("button", { textContent: "Mirror Z", on: { click: () => edit({ op: "mirror", axis: "z" }) } })),
    el("h2", { textContent: "Trim to a box" }),
    el("div", { className: "tool" }, low), el("div", { className: "tool" }, high),
    el("div", { className: "tool" }, el("button", { textContent: "Trim", on: { click: () => edit({ op: "trim", low: read(low), high: read(high) }) } })),
    el("h2", { textContent: "Ground level" }),
    el("div", { className: "tool" }, ground, el("button", { textContent: "Set", on: { click: () => edit({ op: "ground", height: Number(ground.value) }) } }), el("span", { className: "dim", textContent: "Lower blocks go underground." })),
    el("h2", { textContent: "Clean up" }),
    el("div", { className: "tool" }, el("button", { textContent: "Fix leaves", title: "Leaves near real logs fall when cut. Others stay permanent.", on: { click: () => edit({ op: "fix_leaves" }) } }),
      el("button", { textContent: "Remove floating bits", on: { click: () => edit({ op: "remove_floating" }) } })),
    el("div", { className: "tool" }, thin, "%", el("button", { textContent: "Thin leaves", on: { click: () => edit({ op: "thin_leaves", share: Number(thin.value) / 100, seed: numbers.length }) } })),
    el("h2", { textContent: "Versions" }),
    el("p", { className: "dim", textContent: "Pick the version to work from. Tick two or more to compare." }),
    project.versions.slice().reverse().map((v) => el("div", { className: "tool" },
      el("input", { type: "checkbox", checked: compare.has(v.number), "aria-label": `Compare version ${v.number}`, on: { change: (e) => { if (e.target.checked) compare.add(v.number); else compare.delete(v.number); shown = null; render(); } } }),
      el("button", { textContent: `Version ${v.number}`, className: v.number === base ? "main" : "", title: v.edits.join(", ") || "The original", on: { click: () => { base = v.number; compare.clear(); shown = null; render(); } } }),
      el("span", { className: "dim", textContent: v.edits[v.edits.length - 1] || "Original" }))),
    el("details", {}, el("summary", { textContent: "Details and credit (optional)" }),
      el("label", { className: "field" }, "Name", detailTitle), el("label", { className: "field" }, "Kind", detailKind),
      el("label", { className: "field" }, "Creator", detailCreator), el("label", { className: "field" }, "Permission", detailPermission),
      el("label", { className: "field" }, "Licence", detailLicence), el("label", { className: "field" }, "Tags, with commas", detailTags),
      el("div", { className: "tool" }, el("button", { textContent: "Save details", on: { click: () => act(() => post("/api/details", { id: project.id, title: detailTitle.value, kind: detailKind.value, creator: detailCreator.value, permission: detailPermission.value, licence: detailLicence.value, tags: detailTags.value })) } }))),
    el("h2", { textContent: thisSaved ? "Saved name" : "Save this version" }),
    el("input", { type: "text", className: "name-box", value: proposed.name, "aria-label": "File name", id: "saveName" }),
    el("div", { className: "tool" }, el("button", { className: "good", textContent: thisSaved ? "Rename" : `Save version ${base}`, on: { click: () => act(() => post("/api/save", { id: project.id, version: base, name: $("saveName").value })) } })),
    el("p", { className: "dim", textContent: "kind_subject_materials_detail_size_source_variant. Save edited versions as more variants." }),
    project.saved.length ? el("h2", { textContent: `Saved variants (${project.saved.length})` }) : null,
    project.saved.map((entry) => el("div", { style: "margin-top:8px" },
      el("div", { className: "name", style: "word-break:break-all;font-size:12px", textContent: `v${entry.version}: ${entry.name}` }),
      el("div", { className: "tool" },
        el("a", { href: `/api/export?id=${project.id}&v=${entry.version}&format=nbt`, download: "" }, el("button", { textContent: ".nbt" })),
        el("a", { href: `/api/export?id=${project.id}&v=${entry.version}&format=schem`, download: "" }, el("button", { textContent: ".schem" })),
        el("button", { textContent: "Add to mod", disabled: blocked, title: blocked ? "The mod only uses vanilla blocks" : "", on: { click: async () => {
          try {
            const done = await post("/api/export/mod", { id: project.id, version: entry.version });
            result.textContent = `Added. In game: /place template ${done.name}`;
          } catch (problem) { result.textContent = problem.message; }
        } } })))),
    result,
  ];
  $("page").style.display = "none";
  $("stage").classList.add("on");
  $("side").replaceChildren(...side.flat().filter(Boolean));
  const key = project.id + ":" + show.join(",");
  if (shown !== key) {
    const sameBuild = shown?.startsWith(project.id + ":");
    shown = key;
    const items = await Promise.all(show.map((n) => get(`/api/version?id=${project.id}&v=${n}`)));
    await viewer.show(items, sameBuild && show.length === 1);
  }
  window.__previewReady = true;
}

// ---- 1. request ----
async function drawRequest() {
  screen = "request";
  stopPolling();
  project = null;
  app = await get("/api/state");
  steps();
  const box = el("textarea", { id: "request", value: draftText, placeholder: "For example: a tree 12 to 15 blocks high and at most 5 wide, with mixed leaves and moss as leaves, and walls and barriers for the trunk", "aria-label": "What do you want to make?" });
  const ready = app.providers.filter((p) => p.ready);
  const choice = el("select", { "aria-label": "Which AI" }, app.providers.map((p) => el("option", { value: p.id, disabled: !p.ready, textContent: p.label + (p.ready ? "" : " (not set up)") })));
  choice.value = app.provider;
  const guess = el("span", { className: "dim" });
  const go = el("button", { className: "main", textContent: "Continue" });
  const refresh = () => {
    const p = app.providers.find((x) => x.id === choice.value);
    guess.textContent = !p?.ready ? "" : p.guess === null ? "Cost not known for this AI." : `Rough guess: ${money(p.guess)} an asset · limit ${money(app.budget)}`;
    go.disabled = !box.value.trim() || !p?.ready;
    draftText = box.value;
  };
  box.addEventListener("input", refresh);
  choice.addEventListener("change", refresh);
  go.onclick = async () => {
    go.disabled = true;
    try {
      const { id } = await post("/api/start", { request: box.value, provider: choice.value });
      draftText = "";
      await openProject(id);
    } catch (problem) { showFailure(problem); go.disabled = false; }
  };
  page(
    el("h1", { textContent: "What do you want to make?" }),
    el("p", { className: "dim", textContent: "Describe it in your own words. The AI will ask about anything it still needs before building." }),
    box,
    el("h2", { textContent: "What the world needs" }),
    el("div", { className: "cards" }, app.suggestions.map((s) => el("button", { textContent: s.label, title: s.text, on: { click: () => { box.value = s.text; refresh(); box.focus(); } } }))),
    !ready.length ? el("div", { className: "notice" }, "To start, add your API key. ", el("button", { textContent: "Open Settings", on: { click: drawSettings } })) : null,
    el("div", { className: "row" }, choice, el("span", { className: "grow" }, guess), go),
  );
  refresh();
}

// ---- 2. questions and brief ----
function drawQuestions() {
  const read = new Map();
  const go = el("button", { className: "main", textContent: "Continue" });
  const update = () => { go.disabled = project.questions.some((q) => q.required && !answered(read.get(q.id)())); };
  const forms = project.questions.map((q) => {
    let body;
    if (q.kind === "select") {
      const select = el("select", { "aria-label": q.text, on: { change: update } }, el("option", { value: "", textContent: q.required ? "Choose…" : "AI decides" }), (q.options || []).map((o) => el("option", { value: o, textContent: o })));
      read.set(q.id, () => select.value);
      body = select;
    } else if (q.kind === "multi") {
      const boxes = (q.options || []).map((o) => ({ o, box: el("input", { type: "checkbox", on: { change: update } }) }));
      read.set(q.id, () => boxes.filter((b) => b.box.checked).map((b) => b.o));
      body = boxes.map(({ o, box }) => el("label", { className: "opt" }, box, " " + o));
    } else if (q.kind === "range") {
      const low = el("input", { type: "number", value: q.min ?? "", "aria-label": q.text + " from", on: { input: update } });
      const high = el("input", { type: "number", value: q.max ?? "", "aria-label": q.text + " to", on: { input: update } });
      read.set(q.id, () => (low.value === "" && high.value === "" ? "" : [Number(low.value), Number(high.value)]));
      body = el("div", {}, low, " to ", high);
    } else {
      const text = el("textarea", { "aria-label": q.text, on: { input: update }, placeholder: q.required ? "" : "Optional" });
      read.set(q.id, () => text.value.trim());
      body = text;
    }
    return el("div", { className: "q" },
      el("div", {}, el("b", { textContent: q.text }), el("span", { className: "tag" + (q.required ? " req" : ""), textContent: q.required ? "Required" : "Optional" })),
      q.help ? el("div", { className: "dim", textContent: q.help }) : null, body);
  });
  go.onclick = () => act(async () => {
    const answers = {};
    for (const q of project.questions) {
      const value = read.get(q.id)();
      if (answered(value)) answers[q.text] = value;
    }
    await post("/api/answers", { id: project.id, answers });
  });
  page(
    el("h1", { textContent: project.title }),
    project.followup_reason ? el("div", { className: "notice", textContent: project.followup_reason }) : null,
    el("p", { className: "dim", textContent: project.request }),
    forms,
    el("div", { className: "row" }, el("span", { className: "grow dim", textContent: spent(project) }), go),
  );
  update();
}
const answered = (value) => (Array.isArray(value) ? value.length > 0 && value.every((v) => !Number.isNaN(v)) : !!value);

function drawBrief() {
  const brief = project.brief;
  const change = el("textarea", { placeholder: "Anything wrong? Write it here.", "aria-label": "Changes to the brief" });
  const redo = el("button", { textContent: "Change the brief", disabled: true, on: { click: () => act(() => post("/api/brief/change", { id: project.id, text: change.value })) } });
  change.addEventListener("input", () => { redo.disabled = !change.value.trim(); });
  page(
    el("h1", { textContent: "Is this right?" }),
    el("p", { textContent: brief.summary }),
    el("ul", {}, brief.points.map((p) => el("li", { textContent: p }))),
    el("p", { className: "dim", textContent: "Nothing is built until you confirm." }),
    change,
    el("div", { className: "row" }, el("span", { className: "grow dim", textContent: spent(project) }), redo,
      el("button", { className: "main", textContent: "Confirm and build", on: { click: () => act(() => post("/api/brief/confirm", { id: project.id })) } })),
  );
}

// ---- 3. working ----
function drawWorking() {
  const lines = project.progress.length ? project.progress : ["Starting"];
  page(
    el("h1", { textContent: project.title }),
    el("p", {}, el("span", { className: "spin", "aria-hidden": "true" }), lines[lines.length - 1]),
    el("ul", { className: "dim" }, lines.slice(0, -1).map((l) => el("li", { textContent: l }))),
    el("p", { className: "dim", textContent: spent(project) }),
  );
}
function drawError() {
  const e = project.error;
  const buttons = [];
  if (e.kind === "budget") {
    buttons.push(el("button", { className: "main", textContent: "Add $1 and continue", on: { click: () => act(() => post("/api/resume", { id: project.id, extra_budget: 1 })) } }));
  } else if (e.retry !== false && project.resume) {
    buttons.push(el("button", { className: "main", textContent: "Try again", on: { click: () => act(() => post("/api/resume", { id: project.id })) } }));
  } else if (project.resume) {
    buttons.push(el("button", { textContent: "Open Settings", on: { click: drawSettings } }), el("button", { className: "main", textContent: "Try again", on: { click: () => act(() => post("/api/resume", { id: project.id })) } }));
  }
  page(
    el("h1", { textContent: project.title }),
    el("div", { className: "notice bad", role: "alert", textContent: e.message }),
    el("p", { className: "dim", textContent: spent(project) }),
    el("div", { className: "row" }, buttons),
  );
}

// ---- 4 and 5. preview and saved ----
async function drawPreview() {
  const saved = project.stage === "saved";
  const numbers = project.versions.map((v) => v.number);
  const current = saved ? project.saved_version : numbers[numbers.length - 1];
  const list = [...compare].filter((n) => numbers.includes(n));
  const show = list.length > 1 ? list.sort((a, b) => a - b) : [current];
  const version = project.versions.find((v) => v.number === (show.length === 1 ? show[0] : current));

  const change = el("textarea", { placeholder: "What should change?", "aria-label": "What should change?" });
  const send = el("button", { textContent: "Make a new version", disabled: true, on: { click: () => act(() => post("/api/change", { id: project.id, text: change.value })) } });
  change.addEventListener("input", () => { send.disabled = !change.value.trim(); });
  const s = version.check.stats;
  const result = el("div", { className: "dim", role: "status" });
  const side = [
    el("h1", { textContent: project.title }),
    el("div", { className: "dim", textContent: `Version ${version.number}` + (version.change ? `: ${version.change}` : "") }),
    el("h2", { textContent: "AI review" }),
    el("p", { textContent: version.verdict }),
    version.had_images ? null : el("p", { className: "warn-t", textContent: "The AI judged this from measurements only, without seeing it." }),
    el("h2", { textContent: "Checks" }),
    version.check.errors.length ? null : el("div", { className: "good-t", textContent: "✓ Vanilla blocks, nothing floats, matches the brief's limits" }),
    version.check.warnings.map((w) => el("div", { className: "warn-t", textContent: "! " + w })),
    el("h2", { textContent: "Size" }),
    el("table", {}, [["Height", s.height], ["Width", `${s.width_x} × ${s.width_z}`], ["Blocks", s.blocks.toLocaleString("en")]].map(([a, b]) => el("tr", {}, el("td", { textContent: a }), el("td", { textContent: b })))),
    el("h2", { textContent: "Blocks used" }),
    el("table", {}, Object.entries(s.block_counts).map(([a, b]) => el("tr", {}, el("td", { textContent: a.replaceAll("_", " ") }), el("td", { textContent: b })))),
    numbers.length > 1 ? el("h2", { textContent: "Compare versions" }) : null,
    numbers.length > 1 ? numbers.map((n) => el("label", { className: "opt", style: "display:block" }, el("input", { type: "checkbox", checked: compare.has(n), on: { change: (e) => { if (e.target.checked) compare.add(n); else compare.delete(n); shown = null; render(); } } }), ` Version ${n}`)) : null,
  ];
  if (saved) {
    side.push(
      el("h2", { textContent: "Export" }),
      el("div", { className: "row" },
        el("a", { href: `/api/export?id=${project.id}&format=nbt`, download: "" }, el("button", { textContent: "Download .nbt" })),
        el("a", { href: `/api/export?id=${project.id}&format=schem`, download: "" }, el("button", { textContent: "Download .schem" }))),
      el("div", { className: "row" }, el("button", { className: "main", textContent: "Add to mod", on: { click: async () => {
        try {
          const done = await post("/api/export/mod", { id: project.id });
          result.textContent = `Added. In game: /place template ${done.name} (roots go ${done.ground_y} blocks below where the tree stands).`;
        } catch (problem) { result.textContent = problem.message; }
      } } })),
      result,
      el("h2", { textContent: "Change it" }), change, el("div", { className: "row" }, send));
  } else {
    side.push(
      el("h2", { textContent: "Your decision" }),
      el("div", { className: "row" }, el("button", { className: "good", textContent: `Agree and save version ${current}`, on: { click: () => act(() => post("/api/agree", { id: project.id, version: current })) } })),
      el("h2", { textContent: "Or change it" }), change, el("div", { className: "row" }, send));
  }
  side.push(el("p", { className: "dim", textContent: spent(project) }));

  $("page").style.display = "none";
  $("stage").classList.add("on");
  $("side").replaceChildren(...side.flat().filter(Boolean));
  const key = project.id + ":" + show.join(",");
  if (shown !== key) {
    shown = key;
    const items = await Promise.all(show.map((n) => get(`/api/version?id=${project.id}&v=${n}`)));
    await viewer.show(items);
  }
  window.__previewReady = true;
}

// ---- moving between steps ----
function stopPolling() {
  clearTimeout(poll);
  poll = null;
}
async function act(work) {
  try {
    await work();
    shown = null;
    await refreshProject();
  } catch (problem) {
    showFailure(problem, $("stage").classList.contains("on") ? $("side") : $("page"));
  }
}
async function openProject(id) {
  screen = "project";
  shown = null;
  base = null;
  compare = new Set();
  project = await get("/api/project?id=" + id);
  await refreshProject();
}
async function refreshProject() {
  stopPolling();
  if (screen !== "project" || !project) return;
  const id = project.id;
  const fresh = await get("/api/project?id=" + id);
  if (screen !== "project" || project?.id !== id) return;
  project = fresh;
  if (project.views_wanted && !viewsSent) {
    viewsSent = true;
    try {
      const images = await viewer.photograph(await get(`/api/version?id=${id}&v=0`));
      await post("/api/views", { id, images });
    } catch (problem) {
      console.error(problem);
      await post("/api/views", { id, images: [] });
    }
  }
  if (!project.views_wanted) viewsSent = false;
  render();
  if (project.busy) poll = setTimeout(() => refreshProject().catch(() => { poll = setTimeout(refreshProject, 3000); }), 900);
}
function render() {
  steps();
  if (project.mode === "import") return drawAdapt();
  if (project.error && !project.busy) return drawError();
  if (project.busy) return drawWorking();
  if (project.stage === "questions") return drawQuestions();
  if (project.stage === "brief") return drawBrief();
  if (project.stage === "preview" || project.stage === "saved") return drawPreview();
  return drawWorking();
}

// ---- library ----
const STATUS_WORDS = { review: "To review", saved: "Saved", skipped: "Skipped" };
let libraryQuery = { q: "", kind: "", status: "", batch: "", no_credit: false, not_vanilla: false, page: 0 };
async function drawLibrary(start) {
  screen = "library";
  stopPolling();
  project = null;
  queue = null;
  if (start) libraryQuery = { ...libraryQuery, q: "", kind: "", status: "", no_credit: false, not_vanilla: false, ...start, page: 0 };
  if (!app) app = await get("/api/state");
  steps();
  const q = libraryQuery;
  const params = new URLSearchParams({ q: q.q, kind: q.kind, status: q.status, batch: q.batch, no_credit: q.no_credit ? 1 : 0, not_vanilla: q.not_vanilla ? 1 : 0, page: q.page });
  const data = await get("/api/library?" + params);
  const ticked = new Set();
  const set = (change) => { libraryQuery = { ...libraryQuery, ...change, page: change.page ?? 0 }; drawLibrary(); };
  const search = el("input", { type: "text", value: q.q, placeholder: "Search names, tags, creators", "aria-label": "Search", style: "flex:1;min-width:200px" });
  let waiting = null;
  search.addEventListener("input", () => { clearTimeout(waiting); waiting = setTimeout(() => set({ q: search.value }), 350); });
  const pick = (value, label, options, change) => {
    const select = el("select", { "aria-label": label, on: { change: (e) => set(change(e.target.value)) } }, el("option", { value: "", textContent: label }), options.map(([v, t]) => el("option", { value: v, textContent: t })));
    select.value = value;
    return select;
  };
  const chosen = () => (ticked.size ? [...ticked] : data.ids);
  const bulkKind = el("select", { "aria-label": "Set kind" }, el("option", { value: "", textContent: "Keep kind" }), CATEGORIES().map((c) => el("option", { value: c, textContent: c })));
  const bulkCreator = el("input", { type: "text", placeholder: "Creator", "aria-label": "Set creator" });
  const zipKind = el("select", { "aria-label": "Zip format" }, el("option", { value: "nbt", textContent: ".nbt" }), el("option", { value: "schem", textContent: ".schem" }));
  const scope = el("b");
  const showScope = () => { scope.textContent = ticked.size ? `${ticked.size} ticked` : `All ${data.total.toLocaleString("en")} shown`; };
  const bulkDo = (body) => post("/api/library/bulk", { ids: chosen(), ...body }).then(() => drawLibrary()).catch((problem) => showFailure(problem));
  const rows = data.rows.map((r) => el("tr", {},
    el("td", {}, el("input", { type: "checkbox", "aria-label": "Tick " + r.title, on: { change: (e) => { if (e.target.checked) ticked.add(r.id); else ticked.delete(r.id); showScope(); } } })),
    el("td", { className: "name" }, el("a", { href: "#", style: "color:inherit", textContent: r.saved.length ? r.saved[r.saved.length - 1].name : r.title, on: { click: (e) => { e.preventDefault(); openQueue(data.ids, data.ids.indexOf(r.id)).catch((problem) => showFailure(problem)); } } }),
      r.saved.length > 1 ? el("span", { className: "dim", textContent: ` +${r.saved.length - 1} variants` }) : null),
    el("td", { textContent: r.kind }), el("td", { textContent: r.size }), el("td", { textContent: r.blocks.toLocaleString("en") }),
    el("td", { textContent: r.creator || (r.mode === "import" ? "No credit" : "AI") }),
    el("td", { textContent: (STATUS_WORDS[r.status] || r.status) + (r.not_vanilla ? " · not vanilla" : "") })));
  const last = Math.max(Math.ceil(data.total / data.page_size) - 1, 0);
  page(
    el("h1", { textContent: `Library (${data.total.toLocaleString("en")})` }),
    el("div", { className: "tool" }, search,
      pick(q.kind, "Any kind", data.kinds.map((k) => [k, k]), (v) => ({ kind: v })),
      pick(q.status, "Any status", Object.entries(STATUS_WORDS), (v) => ({ status: v })),
      pick(q.batch, "Any batch", data.batches.map((b) => [b, b.slice(1, 9) + " " + b.slice(10, 12) + ":" + b.slice(12, 14)]), (v) => ({ batch: v })),
      el("label", {}, el("input", { type: "checkbox", checked: q.no_credit, on: { change: (e) => set({ no_credit: e.target.checked }) } }), " No credit"),
      el("label", {}, el("input", { type: "checkbox", checked: q.not_vanilla, on: { change: (e) => set({ not_vanilla: e.target.checked }) } }), " Not vanilla")),
    el("div", { className: "queue" }, scope,
      el("button", { className: "main", textContent: "Review one by one", disabled: !data.total, on: { click: () => openQueue(chosen(), 0).catch((problem) => showFailure(problem)) } }),
      el("button", { textContent: "Save with proposed names", on: { click: () => bulkDo({ action: "save" }) } }),
      bulkKind, bulkCreator,
      el("button", { textContent: "Apply", on: { click: () => bulkDo({ action: "details", kind: bulkKind.value, creator: bulkCreator.value.trim() }) } }),
      zipKind,
      el("button", { textContent: "Download as zip", on: { click: async () => {
        const reply = await fetch("/api/library/zip", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ ids: chosen(), format: zipKind.value }) });
        if (!reply.ok) return showFailure(new Error("The zip couldn't be made."));
        const link = el("a", { href: URL.createObjectURL(await reply.blob()), download: "asset-library.zip" });
        link.click();
        window.__zipDone = true;
      } } }),
      el("button", { className: "danger", textContent: "Delete", on: { click: () => { if (confirm(`Delete ${chosen().length} assets? This can't be undone.`)) bulkDo({ action: "delete" }); } } })),
    data.total ? el("table", { className: "lib" }, el("tr", {}, ["", "Name", "Kind", "Size", "Blocks", "Creator", "Status"].map((h) => el("th", { textContent: h }))), rows)
      : el("p", { className: "dim", textContent: "Nothing here yet. Press New to import builds." }),
    last ? el("div", { className: "row" }, el("button", { textContent: "Previous page", disabled: q.page === 0, on: { click: () => set({ page: q.page - 1 }) } }), el("span", { className: "dim", textContent: `Page ${q.page + 1} of ${last + 1}` }), el("button", { textContent: "Next page", disabled: q.page >= last, on: { click: () => set({ page: q.page + 1 }) } })) : null,
  );
  $("page").classList.add("wide");
  showScope();
}

// ---- settings ----
async function drawSettings() {
  screen = "settings";
  stopPolling();
  app = await get("/api/state");
  steps();
  const claude = app.providers.find((p) => p.id === "claude");
  const gemini = app.providers.find((p) => p.id === "gemini");
  const note = (text, good) => el("div", { className: good ? "good-t" : "bad-t", role: "status", textContent: text });
  const claudeKey = el("input", { type: "password", autocomplete: "off", size: 40, placeholder: claude.has_key ? "Saved. Paste a new one to replace it." : "Paste your key", "aria-label": "Claude API key" });
  const claudeModel = el("select", { "aria-label": "Claude model" }, claude.models.map((m) => el("option", { value: m.id, textContent: m.label })));
  claudeModel.value = claude.model;
  const geminiKey = el("input", { type: "password", autocomplete: "off", size: 40, placeholder: gemini.has_key ? "Saved. Paste a new one to replace it." : "Paste your key", "aria-label": "Gemini API key" });
  const geminiModel = el("select", { "aria-label": "Gemini model" }, el("option", { value: "", textContent: gemini.model || "Press Load models" }));
  geminiModel.value = gemini.model;
  const budget = el("input", { type: "number", min: 0.1, step: 0.5, value: app.budget, "aria-label": "Limit per asset in dollars" });
  const images = el("input", { type: "checkbox", checked: app.send_images });
  const fallback = el("input", { type: "checkbox", checked: app.safety_fallback });
  const claudeStatus = el("div"), geminiStatus = el("div"), saveStatus = el("div");
  const save = async () => {
    const keys = {};
    if (claudeKey.value.trim()) keys.claude = claudeKey.value.trim();
    if (geminiKey.value.trim()) keys.gemini = geminiKey.value.trim();
    await post("/api/settings", { keys, settings: { budget: Math.max(Number(budget.value) || 1, 0.1), send_images: images.checked, safety_fallback: fallback.checked, models: { claude: claudeModel.value, gemini: geminiModel.value } } });
    claudeKey.value = geminiKey.value = "";
  };
  const test = (name, status) => async () => {
    status.replaceChildren(el("span", { className: "dim", textContent: "Testing…" }));
    try {
      await save();
      await post("/api/settings/test", { provider: name });
      status.replaceChildren(note("It works.", true));
    } catch (problem) { status.replaceChildren(note(problem.message, false)); }
  };
  page(
    el("h1", { textContent: "Settings" }),
    el("div", { className: "q" }, el("b", { textContent: "Claude" }),
      el("p", { className: "dim", textContent: "Get a key at platform.claude.com. It's billed by use, separately from a chat subscription." }),
      el("div", { className: "row" }, claudeKey), el("div", { className: "row" }, claudeModel, el("button", { textContent: "Test", on: { click: test("claude", claudeStatus) } })), claudeStatus),
    el("div", { className: "q" }, el("b", { textContent: "Gemini" }),
      el("p", { className: "dim", textContent: "Get a key at aistudio.google.com. Its model list comes from Google." }),
      el("div", { className: "row" }, geminiKey),
      el("div", { className: "row" }, geminiModel,
        el("button", { textContent: "Load models", on: { click: async () => {
          try {
            await save();
            const { models } = await post("/api/settings/gemini-models");
            geminiModel.replaceChildren(...models.map((m) => el("option", { value: m, textContent: m })));
            geminiStatus.replaceChildren(note(`${models.length} models found. Pick one and press Test.`, true));
          } catch (problem) { geminiStatus.replaceChildren(note(problem.message, false)); }
        } } }),
        el("button", { textContent: "Test", on: { click: test("gemini", geminiStatus) } })), geminiStatus),
    el("div", { className: "q" }, el("b", { textContent: "Spending and privacy" }),
      el("div", { className: "row" }, el("label", {}, "Limit per asset, in dollars: ", budget)),
      el("p", { className: "dim", textContent: "The work stops at this limit and asks before spending more. For Gemini, whose prices aren't known here, it counts tokens instead: 300,000 per dollar." }),
      el("label", { className: "opt" }, images, " Send pictures of the asset to the AI for review. They contain game textures. Without them, it judges from measurements only."),
      el("label", { className: "opt" }, fallback, " If Claude's safety filter wrongly declines a request, let another Claude model try it.")),
    el("div", { className: "row" }, saveStatus, el("span", { className: "grow" }),
      el("button", { className: "main", textContent: "Save", on: { click: async () => {
        try { await save(); saveStatus.replaceChildren(note("Saved.", true)); } catch (problem) { saveStatus.replaceChildren(note(problem.message, false)); }
      } } })),
  );
}

$("newBtn").onclick = drawHome;
$("libraryBtn").onclick = () => drawLibrary({ batch: "" }).catch((problem) => showFailure(problem));
$("settingsBtn").onclick = drawSettings;
get("/api/state").then((state) => { app = state; }).catch(() => {});
drawHome();
