// The step-by-step page. Everything the AI writes is shown with textContent, never as HTML.
import * as viewer from "./viewer.js";
import { initReview, drawReview, leaveReview } from "./review.js";
import { initDashboard, drawDashboard } from "./dashboard.js";

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
  if (screen === "dashboard" || screen === "review" || screen === "library" || screen === "settings" || screen === "find" || screen === "explore" || screen === "presets" || screen === "addfiles" || screen === "send" || project?.mode === "file") { $("steps").replaceChildren(); return; }
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
let viewHeldFor = null;   // the build the 3D view holds while it sits inside the Review screen
function parkView() {
  const stage = $("stage"), view = $("view");
  if (view && view.parentNode !== stage) stage.prepend(view);
}
function page(...children) {
  parkView();   // the 3D view may be sitting in the Review page that is about to be replaced
  if (screen !== "review") { leaveReview(); viewHeldFor = null; }
  $("page").classList.remove("wide");
  viewer.hide();
  shown = null;
  $("stage").classList.remove("on");
  $("page").style.display = "";
  $("page").replaceChildren(...children.flat().filter((c) => c !== null && c !== undefined && c !== false));
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
      el("button", { on: { click: () => drawPresets().catch((problem) => showFailure(problem)) } }, el("b", { textContent: "Make a tree" }), "Pick a preset (oak, spruce, willow, cherry...) and move the sliders. No AI, no cost, and the same seed always gives the same tree."),
      el("button", { on: { click: () => drawRequest().catch((problem) => showFailure(problem)) } }, el("b", { textContent: "Make with AI" }), "Describe a tree or plant, and an AI builds it. Needs an API key.")),
  );
}

// ---- import ----
const BUILD_FILES = /\.(schem|litematic|nbt|jar|zip)$/i;
let importTab = "file", queue = null;

function detailFields(defaults = {}) {
  const kind = el("select", {}, el("option", { value: "", textContent: "Guess from the build" }), CATEGORIES().map((c) => el("option", { value: c, textContent: c[0].toUpperCase() + c.slice(1) })));
  const creator = el("input", { type: "text", maxLength: 200, placeholder: "Leave empty for your own builds" });
  const permission = el("input", { type: "text", maxLength: 500, placeholder: "A link, or where they said yes" });
  const licence = el("input", { type: "text", maxLength: 200 });
  const link = el("input", { type: "text", maxLength: 500, placeholder: "Where it was found" });
  link.value = defaults.link || "";
  creator.value = defaults.creator || "";
  permission.value = defaults.permission || "";
  licence.value = defaults.licence || "";
  return {
    node: [el("h2", { textContent: "Details for everything here (all optional)" }),
      el("label", { className: "field" }, "Kind", kind), el("label", { className: "field" }, "Creator", creator),
      el("label", { className: "field" }, "Permission", permission), el("label", { className: "field" }, "Licence", licence), el("label", { className: "field" }, "Link", link)],
    read: () => ({ kind: kind.value, creator: creator.value, permission: permission.value, licence: licence.value, link: link.value }),
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

async function drawImport(prefill = null) {
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
  const details = detailFields(prefill?.details);
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
  const fileTab = el("div", {}, prefill?.notice ? el("p", { className: "bad-t", role: "status", textContent: prefill.notice }) : null, drop, status, listBox);
  if (prefill) {
    importTab = "file";
    sources.push(prefill.source);
    prefill.source.items.forEach((item) => ticked.add(key(prefill.source, item)));
    status.textContent = `${prefill.source.items.length.toLocaleString("en")} builds found in ${prefill.source.name}.`;
    drawList();
    refresh();
  }

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
// The side panel is a stack of sections. Each heading becomes a drop-down, so a section can be closed to reach the ones below faster.
// Which are open is remembered while the app runs.
const sectionOpen = new Map();
function sectionize(nodes) {
  const out = [];
  let current = null;
  const all = (open) => () => { document.querySelectorAll("#side details.sec").forEach((d) => { d.open = open; }); };
  for (const node of nodes) {
    if (node.tagName === "H2") {
      const title = node.textContent;
      const name = title.replace(/\s*\(.*\)$/, "");
      const section = el("details", { className: "sec" }, el("summary", { textContent: title }));
      section.open = sectionOpen.get(name) !== false;
      section.addEventListener("toggle", () => sectionOpen.set(name, section.open));
      out.push(section);
      current = section;
    } else if (current && !(node.dataset && node.dataset.top)) {
      current.append(node);
    } else {
      out.push(node);
    }
  }
  const first = out.findIndex((n) => n.className === "sec");
  if (first >= 0) out.splice(first, 0, el("div", { className: "tool" }, el("button", { textContent: "Close all", on: { click: all(false) } }), el("button", { textContent: "Open all", on: { click: all(true) } })));
  return out;
}

let backToReview = null;  // set when the Review screen opened the workbench, so one click returns
function queueBar() {
  if (backToReview && backToReview === project.id) {
    return el("div", { className: "queue" }, el("button", { className: "main", textContent: "Back to Review", on: { click: () => { const id = backToReview; backToReview = null; drawReview({ id }).catch((problem) => showFailure(problem)); } } }),
      el("span", { className: "dim", textContent: "Changes you make here are kept. The analysis stays as it was until you re-analyse." }));
  }
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
// A build with a limited or unknown licence is only added to the mod after the author confirms.
async function addToMod(project, body) {
  const check = project.licence_check;
  const needsCheck = check && (check.status === "limited" || check.status === "unknown");
  if (needsCheck && !confirm(`${check.words}. Do you have the right to use this build in the mod?`)) throw new Error("Not added.");
  return post("/api/export/mod", { ...body, confirm_licence: Boolean(needsCheck) });
}

let nameStyle = -1;

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
  const detailLink = el("input", { type: "text", value: project.credit?.link || "" });
  const licenceCheck = project.licence_check || { status: "own", words: "", can_modify: true };
  const needsCheck = licenceCheck.status === "limited" || licenceCheck.status === "unknown";
  const detailTags = el("input", { type: "text", value: (project.tags || []).join(", ") });
  const detailCollections = el("input", { type: "text", value: (project.collections || []).join(", "), placeholder: "For example: Forest set, Village", list: "collectionList" });
  const detailFavorite = el("input", { type: "checkbox", checked: Boolean(project.favorite) });
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
    licenceCheck.words ? el("p", { className: needsCheck ? "bad-t" : "dim", role: "status", textContent: `Licence: ${licenceCheck.words}${licenceCheck.can_modify === false ? ". You may not change it." : licenceCheck.can_modify === null ? ". Whether you may change it: to confirm." : ""}` }) : null,
    el("h2", { textContent: "Details and credit (optional)" }),
      el("label", { className: "field" }, "Name", detailTitle), el("label", { className: "field" }, "Kind", detailKind),
      el("label", { className: "field" }, "Creator", detailCreator), el("label", { className: "field" }, "Permission", detailPermission),
      el("label", { className: "field" }, "Licence", detailLicence), el("label", { className: "field" }, "Link", detailLink), el("label", { className: "field" }, "Tags, with commas", detailTags),
      el("label", { className: "field" }, "Collections, with commas", detailCollections), el("label", { className: "opt" }, detailFavorite, " ★ Favorite"),
      el("div", { className: "tool" }, el("button", { textContent: "Save details", on: { click: () => act(() => post("/api/details", { id: project.id, title: detailTitle.value, kind: detailKind.value, creator: detailCreator.value, permission: detailPermission.value, licence: detailLicence.value, link: detailLink.value, tags: detailTags.value, collections: detailCollections.value, favorite: detailFavorite.checked })) } })),
    el("h2", { textContent: thisSaved ? "Saved name" : "Save this version" }),
    el("input", { type: "text", className: "name-box", value: proposed.name, "aria-label": "File name", id: "saveName" }),
    el("div", { className: "tool" }, el("button", { textContent: "New proposed name", title: "Another name with the size, block count and version in it. Press again for the next style.", on: { click: async () => {
      nameStyle = (nameStyle + 1) % 3;
      try {
        const next = await get(`/api/propose?id=${project.id}&v=${base}&style=${nameStyle}`);
        $("saveName").value = next.name;
        $("nameStyle").textContent = next.label;
      } catch (problem) { $("nameStyle").textContent = problem.message; }
    } } }), el("span", { className: "dim", id: "nameStyle", role: "status", textContent: "" })),
    el("div", { className: "tool" }, el("button", { className: "good", textContent: thisSaved ? "Rename" : `Save version ${base}`, on: { click: () => act(() => post("/api/save", { id: project.id, version: base, name: $("saveName").value })) } })),
    el("p", { className: "dim", textContent: "Default: kind_subject_materials_detail_size_source_variant. w = width, d = depth, h = height. Save edited versions as more variants." }),
    project.saved.length ? el("h2", { textContent: `Saved variants (${project.saved.length})` }) : null,
    project.saved.map((entry) => el("div", { style: "margin-top:8px" },
      el("div", { className: "name", style: "word-break:break-all;font-size:12px", textContent: `v${entry.version}: ${entry.name}` }),
      el("div", { className: "tool" },
        el("a", { href: `/api/export?id=${project.id}&v=${entry.version}&format=nbt`, download: "" }, el("button", { textContent: ".nbt" })),
        el("a", { href: `/api/export?id=${project.id}&v=${entry.version}&format=schem`, download: "" }, el("button", { textContent: ".schem" })),
        el("button", { textContent: "Add to mod", disabled: blocked, title: blocked ? "The mod only uses vanilla blocks" : "", on: { click: async () => {
          try {
            const done = await addToMod(project, { id: project.id, version: entry.version });
            result.textContent = `Added. In game: /place template ${done.name}`;
          } catch (problem) { result.textContent = problem.message; }
        } } })))),
    result,
  ];
  result.dataset.top = "1";
  $("page").style.display = "none";
  $("stage").classList.add("on");
  $("side").replaceChildren(...sectionize(side.flat().filter(Boolean)));
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
          const done = await addToMod(project, { id: project.id });
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
  $("side").replaceChildren(...sectionize(side.flat().filter(Boolean)));
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
  if (project.mode === "file") return drawFile();
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
let libraryQuery = { q: "", kind: "", status: "", batch: "", licence: "", tag: "", collection: "", favorite: false, no_credit: false, not_vanilla: false, page: 0 };
let libraryView = "list";
try { libraryView = localStorage.getItem("libraryView") === "grid" ? "grid" : "list"; } catch (problem) { /* no saved choice */ }
const LICENCE_WORDS = { own: "Your own", open: "Open", permission: "Permission noted", limited: "Limited", unknown: "Unknown" };
async function drawLibrary(start) {
  screen = "library";
  stopPolling();
  project = null;
  queue = null;
  if (start) libraryQuery = { ...libraryQuery, q: "", kind: "", status: "", licence: "", tag: "", collection: "", favorite: false, no_credit: false, not_vanilla: false, ...start, page: 0 };
  if (!app) app = await get("/api/state");
  steps();
  const q = libraryQuery;
  const params = new URLSearchParams({ q: q.q, kind: q.kind, status: q.status, batch: q.batch, licence: q.licence, tag: q.tag, collection: q.collection, favorite: q.favorite ? 1 : 0, no_credit: q.no_credit ? 1 : 0, not_vanilla: q.not_vanilla ? 1 : 0, page: q.page });
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
  const tagBox = el("input", { type: "text", placeholder: "Tag", "aria-label": "Tag", size: 12, list: "tagList" });
  const collectionBox = el("input", { type: "text", placeholder: "Collection", "aria-label": "Collection", size: 16, list: "collectionList" });
  const bulkCreator = el("input", { type: "text", placeholder: "Creator", "aria-label": "Set creator" });
  const zipKind = el("select", { "aria-label": "Zip format" }, el("option", { value: "nbt", textContent: ".nbt" }), el("option", { value: "schem", textContent: ".schem" }));
  const scope = el("b");
  const showScope = () => { scope.textContent = ticked.size ? `${ticked.size} ticked` : `All ${data.total.toLocaleString("en")} shown`; };
  const bulkDo = (body) => post("/api/library/bulk", { ids: chosen(), ...body }).then(() => drawLibrary()).catch((problem) => showFailure(problem));
  const rows = data.rows.map((r) => el("tr", {},
    el("td", {}, el("button", { className: "star", textContent: r.favorite ? "★" : "☆", title: r.favorite ? "Favorite (click to remove)" : "Make a favorite", "aria-pressed": String(r.favorite), "aria-label": "Favorite " + r.title,
      style: "border:0;background:none;cursor:pointer;font-size:16px;padding:0 4px;" + (r.favorite ? "color:gold" : ""), on: { click: () => post("/api/library/bulk", { ids: [r.id], action: "favorite", value: !r.favorite }).then(() => drawLibrary()).catch((problem) => showFailure(problem)) } }),
      el("input", { type: "checkbox", "aria-label": "Tick " + r.title, on: { change: (e) => { if (e.target.checked) ticked.add(r.id); else ticked.delete(r.id); showScope(); } } })),
    el("td", { className: "name" }, el("a", { href: "#", style: "color:inherit", textContent: r.saved.length ? r.saved[r.saved.length - 1].name : r.title, on: { click: (e) => { e.preventDefault(); openQueue(data.ids, data.ids.indexOf(r.id)).catch((problem) => showFailure(problem)); } } }),
      r.saved.length > 1 ? el("span", { className: "dim", textContent: ` +${r.saved.length - 1} variants` }) : null,
      r.collections.length ? el("span", { className: "dim", textContent: ` · ${r.collections.join(", ")}` }) : null),
    el("td", { textContent: r.kind }), el("td", { textContent: r.size }), el("td", { textContent: r.mode === "file" ? "-" : r.blocks.toLocaleString("en") }),
    el("td", { textContent: r.creator || (r.mode === "import" || r.mode === "file" ? "No credit" : "AI") }),
    el("td", { className: r.licence_status === "limited" || r.licence_status === "unknown" ? "bad-t" : "", textContent: LICENCE_WORDS[r.licence_status] || "", title: (r.licence || "No licence given") + (r.can_modify === false ? ". You may not change it." : r.can_modify === null ? ". Changes: to confirm." : "") }),
    el("td", { textContent: (STATUS_WORDS[r.status] || r.status) + (r.not_vanilla ? " · not vanilla" : "") })));
  const cards = data.rows.map((r) => {
    const name = r.saved.length ? r.saved[r.saved.length - 1].name : r.title;
    const open = (e) => { e.preventDefault(); openQueue(data.ids, data.ids.indexOf(r.id)).catch((problem) => showFailure(problem)); };
    return el("div", { className: "card" },
      el("a", { href: "#", className: "thumb", "aria-label": "Open " + name, on: { click: open } },
        el("img", { src: `/api/thumb?id=${r.id}&v=${r.versions}`, alt: "", loading: "lazy", width: 160, height: 160 })),
      el("div", { className: "cardbar" },
        el("input", { type: "checkbox", "aria-label": "Tick " + r.title, checked: ticked.has(r.id), on: { change: (e) => { if (e.target.checked) ticked.add(r.id); else ticked.delete(r.id); showScope(); } } }),
        el("button", { className: "star", textContent: r.favorite ? "★" : "☆", title: r.favorite ? "Favorite (click to remove)" : "Make a favorite", "aria-pressed": String(r.favorite), "aria-label": "Favorite " + r.title,
          style: "border:0;background:none;cursor:pointer;font-size:16px;padding:0 4px;" + (r.favorite ? "color:gold" : ""), on: { click: () => post("/api/library/bulk", { ids: [r.id], action: "favorite", value: !r.favorite }).then(() => drawLibrary()).catch((problem) => showFailure(problem)) } }),
        el("span", { className: r.licence_status === "limited" || r.licence_status === "unknown" ? "bad-t" : "dim", textContent: LICENCE_WORDS[r.licence_status] || "" })),
      el("a", { href: "#", className: "cardname", textContent: name, title: name, on: { click: open } }),
      el("div", { className: "dim", textContent: r.mode === "file" ? `${r.kind}${r.ftype && r.ftype !== r.kind ? " (" + r.ftype + ")" : ""} · ${r.size}` : `${r.kind} · ${r.size} · ${r.blocks.toLocaleString("en")} blocks` }));
  });
  const last = Math.max(Math.ceil(data.total / data.page_size) - 1, 0);
  page(
    el("h1", { textContent: `Library (${data.total.toLocaleString("en")})` }),
    el("datalist", { id: "tagList" }, data.tags.map((t) => el("option", { value: t.name }))),
    el("datalist", { id: "collectionList" }, data.collections.map((c) => el("option", { value: c.name }))),
    el("div", { className: "tool" }, search,
      pick(q.kind, "Any kind", data.kinds.map((k) => [k, k]), (v) => ({ kind: v })),
      pick(q.status, "Any status", Object.entries(STATUS_WORDS), (v) => ({ status: v })),
      el("label", {}, el("input", { type: "checkbox", checked: q.favorite, on: { change: (e) => set({ favorite: e.target.checked }) } }), " ★ Favorites"),
      pick(q.collection, "Any collection", data.collections.map((c) => [c.name, `${c.name} (${c.count})`]), (v) => ({ collection: v })),
      pick(q.tag, "Any tag", data.tags.map((t) => [t.name, `${t.name} (${t.count})`]), (v) => ({ tag: v })),
      pick(q.licence, "Any licence", [["check", "Needs checking"], ...Object.entries(LICENCE_WORDS)], (v) => ({ licence: v })),
      pick(q.batch, "Any batch", data.batches.map((b) => [b, b.slice(1, 9) + " " + b.slice(10, 12) + ":" + b.slice(12, 14)]), (v) => ({ batch: v })),
      el("label", {}, el("input", { type: "checkbox", checked: q.no_credit, on: { change: (e) => set({ no_credit: e.target.checked }) } }), " No credit"),
      el("label", {}, el("input", { type: "checkbox", checked: q.not_vanilla, on: { change: (e) => set({ not_vanilla: e.target.checked }) } }), " Not vanilla")),
    el("div", { className: "queue" }, el("span", { className: "dim", textContent: "For the assets above:" }),
      tagBox, el("button", { textContent: "Add tag", on: { click: () => tagBox.value.trim() && bulkDo({ action: "tag_add", tag: tagBox.value }) } }), el("button", { textContent: "Remove tag", on: { click: () => tagBox.value.trim() && bulkDo({ action: "tag_remove", tag: tagBox.value }) } }),
      collectionBox, el("button", { textContent: "Add to collection", on: { click: () => collectionBox.value.trim() && bulkDo({ action: "collection_add", name: collectionBox.value }) } }), el("button", { textContent: "Remove from collection", on: { click: () => collectionBox.value.trim() && bulkDo({ action: "collection_remove", name: collectionBox.value }) } }),
      el("button", { textContent: "★ Make favorite", on: { click: () => bulkDo({ action: "favorite", value: true }) } }), el("button", { textContent: "Remove favorite", on: { click: () => bulkDo({ action: "favorite", value: false }) } })),
    data.collections.length ? el("details", {}, el("summary", { textContent: `Manage collections (${data.collections.length})` }),
      data.collections.map((c) => el("div", { className: "tool" }, el("span", { textContent: `${c.name} · ${c.count}` }),
        el("button", { textContent: "Rename", on: { click: () => { const to = prompt(`New name for "${c.name}"`, c.name); if (to && to.trim()) post("/api/library/bulk", { action: "collection_rename", name: c.name, to }).then(() => drawLibrary()).catch((problem) => showFailure(problem)); } } }),
        el("button", { className: "danger", textContent: "Delete collection", title: "The assets stay in the Library", on: { click: () => { if (confirm(`Delete the collection "${c.name}"? Its ${c.count} assets stay in the Library.`)) post("/api/library/bulk", { action: "collection_delete", name: c.name }).then(() => drawLibrary()).catch((problem) => showFailure(problem)); } } })))) : null,
    el("div", { className: "queue" }, scope,
      el("button", { className: "main", textContent: "Review one by one", disabled: !data.total, on: { click: () => openQueue(chosen(), 0).catch((problem) => showFailure(problem)) } }),
      el("button", { textContent: "Save with proposed names", on: { click: () => bulkDo({ action: "save" }) } }),
      bulkKind, bulkCreator,
      el("button", { textContent: "Apply", on: { click: () => bulkDo({ action: "details", kind: bulkKind.value, creator: bulkCreator.value.trim() }) } }),
      zipKind,
      el("button", { textContent: "Send to mod…", title: "See where each asset would go in the mod, then confirm", on: { click: () => drawSend(chosen(), () => drawLibrary()).catch((problem) => showFailure(problem)) } }),
      el("button", { textContent: "Download as zip", on: { click: async () => {
        const reply = await fetch("/api/library/zip", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ ids: chosen(), format: zipKind.value }) });
        if (!reply.ok) return showFailure(new Error("The zip couldn't be made."));
        const link = el("a", { href: URL.createObjectURL(await reply.blob()), download: "asset-library.zip" });
        link.click();
        window.__zipDone = true;
      } } }),
      el("button", { className: "danger", textContent: "Delete", on: { click: () => { if (confirm(`Delete ${chosen().length} assets? This can't be undone.`)) bulkDo({ action: "delete" }); } } })),
    el("div", { className: "tool" }, el("button", { textContent: "Add my files", title: "Add your own textures, sounds and JSON files", on: { click: () => drawAddFiles().catch((problem) => showFailure(problem)) } }), el("span", { className: "dim", textContent: "View:" }),
      ...[["list", "List"], ["grid", "Icons"]].map(([v, t]) => el("button", { textContent: t, className: libraryView === v ? "main" : "", "aria-pressed": String(libraryView === v),
        on: { click: () => { libraryView = v; try { localStorage.setItem("libraryView", v); } catch (problem) { /* not saved */ } drawLibrary(); } } }))),
    data.total && libraryView === "grid" ? el("div", { className: "grid" }, cards) : null,
    data.total && libraryView === "list" ? el("table", { className: "lib" }, el("tr", {}, ["★ ☐", "Name", "Kind", "Size", "Blocks", "Creator", "Licence", "Status"].map((h) => el("th", { textContent: h }))), rows)
      : null,
    data.total ? null : el("p", { className: "dim", textContent: "Nothing here yet. Press New to import builds." }),
    last ? el("div", { className: "row" }, el("button", { textContent: "Previous page", disabled: q.page === 0, on: { click: () => set({ page: q.page - 1 }) } }), el("span", { className: "dim", textContent: `Page ${q.page + 1} of ${last + 1}` }), el("button", { textContent: "Next page", disabled: q.page >= last, on: { click: () => set({ page: q.page + 1 }) } })) : null,
  );
  $("page").classList.add("wide");
  showScope();
}

// ---- send assets to the mod ----
const SEND_WORDS = { new: "New", same: "Already there", overwrite: "Replaces a different file", blocked: "Refused" };

async function drawSend(ids, back) {
  screen = "send";
  stopPolling();
  project = null;
  steps();
  const edits = {};
  const status = el("div", { role: "status" });
  const body = el("div");
  const confirmBox = el("input", { type: "checkbox" });
  const confirmLine = el("label", { className: "opt" }, confirmBox, " I have the right to use the assets marked in red in the mod");
  const overwrite = new Set();
  const send = el("button", { className: "main", textContent: "Send", disabled: true });
  let rows = [];
  const count = (n) => n.toLocaleString("en");

  const refresh = async () => {
    status.replaceChildren(el("span", { className: "spin" }), "Checking where each one would go…");
    try {
      const planned = await post("/api/send/plan", { ids, edits });
      rows = planned.rows;
      draw(planned.folder);
      status.textContent = "";
    } catch (problem) { status.replaceChildren(el("span", { className: "bad-t", textContent: problem.message })); }
  };
  const sendable = () => rows.filter((r) => r.action === "new" || (r.action === "overwrite" && overwrite.has(r.id)));
  const draw = (folder) => {
    const need = rows.some((r) => r.needs_confirm && r.action !== "blocked" && r.action !== "same");
    confirmLine.style.display = need ? "" : "none";
    const table = el("table", { className: "lib" },
      el("tr", {}, ["Asset", "Goes to (inside " + folder + ")", "What happens", "Replace?"].map((h) => el("th", { textContent: h }))),
      ...rows.map((r) => {
        const input = el("input", { type: "text", value: r.to, "aria-label": "Destination of " + r.title, style: "width:100%;min-width:260px", on: { change: (e) => { edits[r.id] = e.target.value.trim(); refresh(); } } });
        return el("tr", {},
          el("td", {}, el("div", { textContent: r.title }), el("div", { className: r.needs_confirm ? "bad-t" : "dim", textContent: `${r.kind} · ${r.licence_words}` })),
          el("td", {}, input),
          el("td", { className: r.action === "blocked" ? "bad-t" : r.action === "overwrite" ? "warn-t" : "dim", textContent: `${SEND_WORDS[r.action]}: ${r.note}` }),
          el("td", {}, r.action === "overwrite" ? el("input", { type: "checkbox", checked: overwrite.has(r.id), "aria-label": "Replace " + r.to, on: { change: (e) => { if (e.target.checked) overwrite.add(r.id); else overwrite.delete(r.id); update(); } } }) : null));
      }));
    body.replaceChildren(rows.length ? table : el("p", { className: "dim", textContent: "Nothing to send." }));
    update();
  };
  const update = () => {
    const n = sendable().length;
    send.textContent = n ? `Send ${count(n)} to the mod` : "Nothing to send";
    send.disabled = !n;
  };
  send.onclick = async () => {
    send.disabled = true;
    status.replaceChildren(el("span", { className: "spin" }), "Writing…");
    try {
      const done = await post("/api/send/apply", { items: rows.filter((r) => r.action !== "blocked").map((r) => ({ id: r.id, to: edits[r.id] || r.to, overwrite: overwrite.has(r.id) })), confirm_licence: confirmBox.checked });
      const tally = {};
      done.results.forEach((r) => { tally[r.result] = (tally[r.result] || 0) + 1; });
      const words = { created: "created", replaced: "replaced", unchanged: "unchanged", kept: "kept as they were", refused: "refused" };
      body.replaceChildren(el("h2", { textContent: "Result" }), el("p", { className: tally.created || tally.replaced ? "good-t" : "dim", textContent: Object.entries(tally).map(([k, v]) => `${count(v)} ${words[k] || k}`).join(" · ") + "." }),
        el("table", { className: "lib" }, done.results.map((r) => el("tr", {}, el("td", { className: r.result === "created" || r.result === "replaced" ? "good-t" : r.result === "refused" ? "bad-t" : "dim", textContent: r.result }), el("td", { textContent: r.to }), el("td", { className: "dim", textContent: `${r.title}: ${r.note}` })))),
        el("p", { className: "dim", textContent: "Every send is listed in docs/assets/send-log.md, and credits.md is updated for assets made by others. Nothing is committed: do that from the project." }));
      send.style.display = "none";
      confirmLine.style.display = "none";
      status.textContent = "";
    } catch (problem) { status.replaceChildren(el("span", { className: "bad-t", textContent: problem.message })); update(); }
  };
  page(el("h1", { textContent: "Send to mod" }),
    el("p", { className: "dim", textContent: "Nothing is written until you press Send. Files go inside the mod's own resources (assets/vestiges and data/vestiges), in the same folders as their originals. Loot tables and the Codex are generated by the mod's tools, so those are refused. Nothing is ever deleted." }),
    body, confirmLine, status,
    el("div", { className: "row" }, el("button", { textContent: "Back", on: { click: () => (back ? back() : drawLibrary()) } }), el("span", { className: "grow" }), send));
  $("page").classList.add("wide");
  confirmLine.style.display = "none";
  refresh();
}

// ---- add my own files ----
const OWN_FILES = /\.(png|ogg|json|mcmeta|mcfunction|txt|lang)$/i;

async function drawAddFiles() {
  screen = "addfiles";
  stopPolling();
  project = null;
  steps();
  if (!app) app = await get("/api/state");
  const kind = el("select", { "aria-label": "Kind" }, [["", "Guess from the file"], ["texture", "Texture"], ["sound", "Sound"], ["model", "Model"], ["data", "Data (loot, recipes, tags...)"]].map(([v, t]) => el("option", { value: v, textContent: t })));
  const details = detailFields();
  const status = el("div", { role: "status" });
  const picker = el("input", { type: "file", multiple: true, accept: ".png,.ogg,.json,.mcmeta,.mcfunction,.txt,.lang,.zip,.jar", "aria-label": "Choose files", style: "display:none" });
  const folder = el("input", { type: "file", webkitdirectory: true, "aria-label": "Choose a folder", style: "display:none" });
  const stamp = () => { const d = new Date(), p = (n) => String(n).padStart(2, "0"); return `b${d.getFullYear()}${p(d.getMonth() + 1)}${p(d.getDate())}_${p(d.getHours())}${p(d.getMinutes())}${p(d.getSeconds())}`; };

  const add = async (all) => {
    const archives = all.filter((f) => /\.(zip|jar)$/i.test(f.name));
    const usable = all.filter((f) => OWN_FILES.test(f.name));
    const ignored = all.length - usable.length - archives.length;
    if (archives.length && !usable.length) {
      status.replaceChildren(el("span", { className: "spin" }), `Reading ${archives[0].name}…`);
      try {
        const reply = await fetch("/api/explore/upload?name=" + encodeURIComponent(archives[0].name), { method: "POST", headers: { "Content-Type": "application/octet-stream" }, body: archives[0] });
        const body = await reply.json();
        if (!reply.ok) throw new Error(body.error);
        return drawExplore(body, {});
      } catch (problem) { status.replaceChildren(el("span", { className: "bad-t", textContent: problem.message })); return; }
    }
    const batch = usable.length > 1 ? stamp() : "";
    const shared = details.read();
    let added = 0, duplicates = 0;
    const failed = [];
    for (const [index, file] of usable.entries()) {
      status.replaceChildren(el("span", { className: "spin" }), `Adding ${index + 1} of ${usable.length}: ${file.name}`);
      const query = new URLSearchParams({ name: file.name, path: file.picked || file.webkitRelativePath || "", kind: kind.value, batch, creator: shared.creator, permission: shared.permission, licence: shared.licence, link: shared.link });
      try {
        const reply = await fetch("/api/files/upload?" + query, { method: "POST", headers: { "Content-Type": "application/octet-stream" }, body: file });
        const body = await reply.json();
        if (!reply.ok) throw new Error(body.error);
        if (body.duplicate) duplicates++; else added++;
      } catch (problem) { failed.push(`${file.name}: ${problem.message}`); }
    }
    const parts = [`${added} added`];
    if (duplicates) parts.push(`${duplicates} already in the Library`);
    if (failed.length) parts.push(`${failed.length} couldn't be added`);
    if (ignored) parts.push(`${ignored} other files ignored`);
    if (archives.length) parts.push(`${archives.length} zip or jar left out (open those with Look inside)`);
    status.replaceChildren(...[el("div", { className: failed.length && !added ? "bad-t" : "good-t", textContent: parts.join(" · ") + "." }),
      failed.length ? el("ul", { className: "dim" }, failed.slice(0, 20).map((f) => el("li", { textContent: f }))) : null,
      added ? el("div", { className: "row" }, el("button", { className: "main", textContent: "Open in Library", on: { click: () => drawLibrary(batch ? { batch } : {}) } })) : null].filter(Boolean));
  };
  picker.addEventListener("change", () => { add([...picker.files]); picker.value = ""; });
  folder.addEventListener("change", () => { add([...folder.files]); folder.value = ""; });
  const drop = el("div", { className: "drop", on: {
    dragover: (e) => { e.preventDefault(); drop.classList.add("over"); },
    dragleave: () => drop.classList.remove("over"),
    drop: async (e) => {
      e.preventDefault();
      drop.classList.remove("over");
      const list = [];
      for (const item of [...e.dataTransfer.items].map((i) => i.webkitGetAsEntry()).filter(Boolean)) await readEntries(item, list);
      add(list);
    } } },
    el("p", { textContent: "Drop your own textures, sounds, JSON files or folders here" }),
    el("div", { className: "tool", style: "justify-content:center" }, el("button", { textContent: "Choose files", on: { click: () => picker.click() } }), el("button", { textContent: "Choose a folder", on: { click: () => folder.click() } })),
    el("p", { className: "dim", textContent: ".png, .ogg, .json, .mcmeta, .mcfunction, .txt. A zip or jar opens in Look inside, where you pick what to keep." }), picker, folder);
  page(el("h1", { textContent: "Add my files" }),
    el("p", { className: "dim", textContent: "Files you made yourself go into the Library as your own work. Fill in the details below only for files made by someone else." }),
    drop, el("label", { className: "field" }, "Kind", kind), details.node, status);
}

// ---- a saved file (texture, sound, JSON data, model) ----
const FILE_KIND_WORDS = { texture: "Texture", sound: "Sound", model: "Model", data: "Data" };

// Texture viewer: zoom, pixel grid, background, 3x3 tiling (to check seams), and playback for animation strips.
function textureViewer(src, info) {
  const frames = info.frames || 1, frameSize = info.frame_size || info.width;
  const canvas = el("canvas", { "aria-label": "Texture", style: "max-width:100%;border:1px solid var(--line);image-rendering:pixelated" });
  const zoom = el("input", { type: "range", min: 1, max: 32, value: Math.max(1, Math.min(32, Math.floor(256 / Math.max(info.width, frameSize)))), "aria-label": "Zoom", style: "width:140px" });
  const zoomText = el("span", { className: "dim" });
  const grid = el("input", { type: "checkbox" });
  const tile = el("input", { type: "checkbox" });
  const background = el("select", { "aria-label": "Background" }, [["checker", "Checkerboard"], ["black", "Black"], ["white", "White"], ["grass", "Grass green"], ["stone", "Grey"]].map(([v, t]) => el("option", { value: v, textContent: t })));
  const frame = el("input", { type: "range", min: 0, max: Math.max(frames - 1, 0), value: 0, "aria-label": "Frame", style: "width:140px" });
  const play = el("button", { textContent: "Play" });
  const image = new Image();
  let playing = null;
  const colour = { black: "#000", white: "#fff", grass: "#5a8f3a", stone: "#8a8a8a" };
  const draw = () => {
    const scale = Number(zoom.value), side = frameSize * scale, n = tile.checked ? 3 : 1;
    zoomText.textContent = `${scale}x`;
    canvas.width = canvas.height = side * n;
    const g = canvas.getContext("2d");
    g.imageSmoothingEnabled = false;
    if (background.value === "checker") {
      const cell = Math.max(8, scale * 2);
      for (let y = 0; y < canvas.height; y += cell) for (let x = 0; x < canvas.width; x += cell) { g.fillStyle = ((x + y) / cell) % 2 ? "#6a6a6a" : "#8a8a8a"; g.fillRect(x, y, cell, cell); }
    } else { g.fillStyle = colour[background.value]; g.fillRect(0, 0, canvas.width, canvas.height); }
    for (let i = 0; i < n; i++) for (let j = 0; j < n; j++) g.drawImage(image, 0, Number(frame.value) * frameSize, frameSize, frameSize, i * side, j * side, side, side);
    if (grid.checked && scale >= 4) {
      g.strokeStyle = "rgba(0,0,0,.35)";
      g.lineWidth = 1;
      for (let k = 0; k <= frameSize * n; k++) {
        g.beginPath(); g.moveTo(k * scale + 0.5, 0); g.lineTo(k * scale + 0.5, canvas.height); g.stroke();
        g.beginPath(); g.moveTo(0, k * scale + 0.5); g.lineTo(canvas.width, k * scale + 0.5); g.stroke();
      }
    }
  };
  [zoom, grid, tile, background, frame].forEach((c) => c.addEventListener("input", draw));
  play.onclick = () => {
    if (playing) { clearInterval(playing); playing = null; play.textContent = "Play"; return; }
    play.textContent = "Stop";
    playing = setInterval(() => { frame.value = (Number(frame.value) + 1) % frames; draw(); }, 100);
  };
  image.onload = draw;
  image.src = src;
  return el("div", {}, canvas,
    el("div", { className: "tool" }, "Zoom", zoom, zoomText, el("label", { className: "opt" }, grid, " Pixel grid"), el("label", { className: "opt" }, tile, " Tile 3x3 (check seams)"), background),
    frames > 1 ? el("div", { className: "tool" }, `Animation: ${frames} frames`, frame, play) : null);
}

async function drawFile() {
  const file = project.file || {};
  const link = (download) => `/api/file?id=${project.id}${download ? "&download=1" : ""}`;
  const licenceCheck = project.licence_check || { status: "own", words: "" };
  const needsCheck = licenceCheck.status === "limited" || licenceCheck.status === "unknown";
  const size = (bytes) => (bytes > 1048576 ? `${(bytes / 1048576).toFixed(1)} MB` : bytes > 1024 ? `${Math.round(bytes / 1024)} KB` : `${bytes} B`);
  const view = el("div");
  let preview3d = null;
  // What the file is, and checks: from the server, by kind.
  let info = null;
  try { info = await get(`/api/file-info?id=${project.id}`); } catch (problem) { info = { kind: "error", error: problem.message }; }
  const facts = (...lines) => el("div", { className: "dim" }, lines.filter(Boolean).map((l) => el("div", { textContent: l })));
  const notes = (list) => (list || []).map((n) => el("div", { className: "warn-t", textContent: "! " + n }));
  if (file.ext === ".png") {
    if (info.error) view.append(el("p", { className: "bad-t", textContent: info.error }));
    else view.append(textureViewer(link(false), info), facts(`${info.width}x${info.height} px · ${info.colours} colours · ${info.clear_percent}% see-through${info.frames > 1 ? " · " + info.frames + " animation frames" : ""}`), ...notes(info.notes));
  } else if (file.ext === ".ogg") {
    const loop = el("input", { type: "checkbox" });
    const audio = el("audio", { src: link(false), controls: true, style: "width:100%" });
    loop.addEventListener("change", () => { audio.loop = loop.checked; });
    view.append(audio, el("label", { className: "opt" }, loop, " Loop"),
      facts(info.seconds ? `${info.seconds} seconds` : "Length unknown", info.rate ? `${info.rate} Hz · ${info.channels === 1 ? "mono" : info.channels === 2 ? "stereo" : info.channels + " channels"}${info.kbps ? " · about " + info.kbps + " kbps" : ""}` : "", info.channels === 2 ? "Minecraft plays stereo sounds without distance, so sounds that should come from a place are normally mono." : ""));
  } else if (info.kind === "json") {
    if (!info.ok) {
      view.append(el("div", { className: "bad-t", role: "alert", textContent: info.error }));
      try { view.append(el("pre", { style: "max-height:360px;overflow:auto;font-size:12px;white-space:pre-wrap;word-break:break-all", textContent: await (await fetch(link(false))).text() })); } catch (problem) { /* no preview */ }
    } else {
      view.append(el("div", { className: "good-t", textContent: "✓ Valid JSON" }), ...notes(info.notes));
      if (info.pretty) view.append(el("pre", { style: "max-height:460px;overflow:auto;font-size:12px;margin:6px 0", textContent: info.pretty }));
    }
  } else if ([".mcfunction", ".txt", ".lang"].includes(file.ext) && (file.bytes || 0) <= 400 * 1024) {
    try { view.append(el("pre", { style: "max-height:420px;overflow:auto;font-size:12px;white-space:pre-wrap;word-break:break-all", textContent: await (await fetch(link(false))).text() })); } catch (problem) { view.append(el("p", { className: "bad-t", textContent: problem.message })); }
  } else {
    view.append(el("p", { className: "dim", textContent: "No preview for this file." }));
  }
  if (project.category === "model") {
    try {
      preview3d = await get(`/api/model-preview?id=${project.id}`);
      if (preview3d.note) view.prepend(el("div", { className: "warn-t", textContent: "! " + preview3d.note }));
    } catch (problem) { view.prepend(el("div", { className: "warn-t", textContent: "! No 3D view: " + problem.message })); }
  }

  const title = el("input", { type: "text", value: project.title, maxLength: 60 });
  const creator = el("input", { type: "text", value: project.credit?.creator || "" });
  const permission = el("input", { type: "text", value: project.credit?.permission || "" });
  const licence = el("input", { type: "text", value: project.credit?.licence || "" });
  const where = el("input", { type: "text", value: project.credit?.link || "" });
  const tags = el("input", { type: "text", value: (project.tags || []).join(", ") });
  const collections = el("input", { type: "text", value: (project.collections || []).join(", "), placeholder: "For example: Forest set, Village" });
  const favorite = el("input", { type: "checkbox", checked: Boolean(project.favorite) });
  const note = el("div", { className: "dim", role: "status" });
  let nameStyle = -1, renameFile = false;
  const styleNote = el("span", { className: "dim", role: "status" });
  const proposeName = el("button", { textContent: "New proposed name", title: "A descriptive name with the size. Press again for the next style.", on: { click: async () => {
    nameStyle = (nameStyle + 1) % 3;
    try {
      const next = await get(`/api/propose-file?id=${project.id}&style=${nameStyle}`);
      title.value = next.name;
      renameFile = true;
      styleNote.textContent = next.label + ". Save details to keep it; the file is renamed too.";
    } catch (problem) { styleNote.textContent = problem.message; }
  } } });
  const field = (label, input) => el("label", { className: "field" }, label, input);
  const content = [
    queueBar(),
    el("h1", { textContent: project.title }),
    el("p", { className: "dim", textContent: `${FILE_KIND_WORDS[project.category] || project.category}${file.type && file.type !== project.category ? " · " + file.type : ""} · ${file.width ? `${file.width}x${file.height} px · ` : ""}${size(file.bytes || 0)}` }),
    view,
    el("p", { className: "dim", style: "word-break:break-all", textContent: `From ${project.source?.name || "?"}: ${file.path || file.name}` }),
    licenceCheck.words ? el("p", { className: needsCheck ? "bad-t" : "dim", role: "status", textContent: `Licence: ${licenceCheck.words}${licenceCheck.can_modify === false ? ". You may not change it." : licenceCheck.can_modify === null ? ". Whether you may change it: to confirm." : ""}` }) : null,
    el("div", { className: "tool" },
      el("a", { href: link(true), download: file.name }, el("button", { className: "main", textContent: "Download" })),
      el("button", { textContent: "Send to mod…", on: { click: () => drawSend([project.id], () => openProject(project.id)).catch((problem) => showFailure(problem)) } }),
      el("button", { className: "danger", textContent: "Delete", on: { click: async () => {
        if (!confirm(`Delete "${project.title}" from the Library? This can't be undone (a backup may still hold it).`)) return;
        try { await post("/api/delete", { id: project.id }); drawLibrary(); } catch (problem) { showFailure(problem); }
      } } })),
    el("h2", { textContent: "Details and credit" }),
    field("Name", title), el("div", { className: "tool" }, proposeName, styleNote), field("Creator", creator), field("Permission", permission), field("Licence", licence), field("Link", where),
    field("Tags, with commas", tags), field("Collections, with commas", collections), el("label", { className: "opt" }, favorite, " ★ Favorite"),
    el("div", { className: "tool" }, el("button", { textContent: "Save details", on: { click: () => act(() => post("/api/details", { id: project.id, title: title.value, rename_file: renameFile ? title.value : "", creator: creator.value, permission: permission.value, licence: licence.value, link: where.value, tags: tags.value, collections: collections.value, favorite: favorite.checked })) } })),
    note,
  ].filter((n) => n !== null);
  if (preview3d) {
    // A model gets the 3D view beside its details, like a build.
    $("page").style.display = "none";
    $("stage").classList.add("on");
    $("side").replaceChildren(...content);
    shown = "file:" + project.id;
    await viewer.show([preview3d.data]);
  } else {
    page(...content);
  }
}

// ---- make a tree from a preset ----
let presetState = { preset: "oak", changes: {} };

// The sliders: [path in the spec, label, low, high, step]
const PRESET_SLIDERS = [
  [["height"], "Height", 4, 60, 1],
  [["trunk", "base_radius"], "Trunk thickness", 0.4, 4, 0.1],
  [["trunk", "lean"], "Trunk lean", 0, 0.8, 0.05],
  [["branches", "count"], "Branches", 0, 12, 1],
  [["branches", "length"], "Branch length", 1, 14, 1],
  [["canopy", "radius"], "Leaf width", 1.5, 12, 0.5],
  [["canopy", "thickness"], "Leaf thickness", 1, 10, 1],
  [["canopy", "density"], "Leaf density", 0.2, 1, 0.05],
  [["canopy", "droop_length"], "Hanging leaves (willow)", 1, 12, 1],
  [["roots", "count"], "Roots", 0, 8, 1],
];

async function drawPresets() {
  screen = "presets";
  stopPolling();
  project = null;
  steps();
  const info = await get("/api/presets");
  const status = el("div", { className: "dim", role: "status" });
  const picture = el("img", { alt: "Preview of the tree", style: "width:100%;max-width:360px;aspect-ratio:1;object-fit:contain;background:rgba(127,127,127,.12);border-radius:8px" });
  const numbers = el("div", { className: "dim" });
  const dig = (spec, path) => path.reduce((o, k) => o?.[k], spec);
  const setIn = (target, path, value) => { path.slice(0, -1).reduce((o, k) => (o[k] ??= {}), target)[path[path.length - 1]] = value; };
  let current = null, timer = null, waiting = 0;
  const controls = {};

  const refresh = async () => {
    const ticket = ++waiting;
    status.replaceChildren(el("span", { className: "spin" }), "Drawing…");
    try {
      const shown = await post("/api/presets/preview", { preset: presetState.preset, changes: presetState.changes });
      if (ticket !== waiting) return;
      current = shown.spec;
      picture.src = "data:image/png;base64," + shown.image;
      numbers.textContent = `${shown.blocks.toLocaleString("en")} blocks · ${shown.size}` + (shown.errors.length ? " · " + shown.errors[0] : "");
      for (const [path, , , , , input, out] of controls.list) {
        const value = dig(current, path);
        input.value = value;
        out.textContent = String(value);
      }
      controls.seed.value = current.seed;
      controls.shape.value = current.canopy.shape;
      status.textContent = "";
    } catch (problem) { status.replaceChildren(el("span", { className: "bad-t", textContent: problem.message })); }
  };
  const later = () => { clearTimeout(timer); timer = setTimeout(refresh, 200); };
  const change = (path, value) => { setIn(presetState.changes, path, value); later(); };

  const picker = el("select", { "aria-label": "Preset", on: { change: (e) => { presetState = { preset: e.target.value, changes: {} }; refresh(); } } },
    info.presets.map((p) => el("option", { value: p.id, textContent: p.name })));
  picker.value = presetState.preset;
  controls.list = PRESET_SLIDERS.map(([path, label, low, high, step]) => {
    const out = el("span", { className: "dim", style: "min-width:36px;text-align:right" });
    const input = el("input", { type: "range", min: low, max: high, step, "aria-label": label, style: "flex:1", on: { input: (e) => { out.textContent = e.target.value; change(path, Number(e.target.value)); } } });
    return [path, label, low, high, step, input, out];
  });
  controls.shape = el("select", { "aria-label": "Leaf shape", on: { change: (e) => change(["canopy", "shape"], e.target.value) } },
    [["round", "Round"], ["cone", "Cone (spruce)"], ["flat", "Flat (acacia)"], ["droop", "Hanging (willow)"]].map(([v, t]) => el("option", { value: v, textContent: t })));
  controls.seed = el("input", { type: "number", min: 0, max: 9999999, "aria-label": "Seed", style: "width:110px", on: { change: (e) => change(["seed"], Number(e.target.value)) } });
  const count = el("input", { type: "number", min: 1, max: 24, value: 1, "aria-label": "How many variations", style: "width:60px" });
  const make = el("button", { className: "main", textContent: "Add to Library", on: { click: async () => {
    make.disabled = true;
    status.replaceChildren(el("span", { className: "spin" }), "Saving…");
    try {
      const made = await post("/api/presets/create", { preset: presetState.preset, changes: presetState.changes, count: Number(count.value) || 1 });
      if (!made.ids.length) { status.textContent = "Already in the Library: the same tree was made before."; make.disabled = false; return; }
      if (made.ids.length === 1) await openProject(made.ids[0]);
      else await drawLibrary({ batch: made.batch });
    } catch (problem) { status.replaceChildren(el("span", { className: "bad-t", textContent: problem.message })); make.disabled = false; }
  } } });

  page(el("h1", { textContent: "Make a tree" }),
    el("p", { className: "dim", textContent: "Pick a preset and move the sliders. No AI and no cost. The same settings and seed always make the same tree; a new seed makes a sibling." }),
    el("div", { className: "preset-grid" },
      el("div", {}, picture, numbers, status),
      el("div", {},
        el("label", { className: "field" }, "Preset", picker),
        controls.list.map(([, label, , , , input, out]) => el("div", { className: "tool" }, el("span", { style: "min-width:150px", textContent: label }), input, out)),
        el("div", { className: "tool" }, el("span", { style: "min-width:150px", textContent: "Leaf shape" }), controls.shape),
        el("div", { className: "tool" }, el("span", { style: "min-width:150px", textContent: "Seed" }), controls.seed,
          el("button", { textContent: "New seed", on: { click: () => { const seed = Math.floor(Math.random() * 99999); controls.seed.value = seed; change(["seed"], seed); } } })),
        el("div", { className: "tool" }, el("span", { textContent: "Make" }), count, el("span", { className: "dim", textContent: "variations (seed counts up)" })),
        el("div", { className: "row" }, make, el("button", { textContent: "Reset", on: { click: () => { presetState.changes = {}; refresh(); } } })))));
  $("page").classList.add("wide");
  refresh();
}

// ---- look inside an archive ----
const EXPLORE_SHOWN = 400;
let exploreState = { type: "", text: "" };

async function drawExplore(listing = null, meta = {}) {
  screen = "explore";
  stopPolling();
  project = null;
  steps();
  const status = el("div", { className: "dim", role: "status" });
  const ticked = new Set();
  const dropzone = () => {
    const picker = el("input", { type: "file", accept: ".zip,.jar", "aria-label": "Choose an archive", style: "display:none", on: { change: () => open(picker.files[0]) } });
    const open = async (file) => {
      if (!file) return;
      status.replaceChildren(el("span", { className: "spin" }), `Reading ${file.name}…`);
      try {
        const reply = await fetch("/api/explore/upload?name=" + encodeURIComponent(file.name), { method: "POST", headers: { "Content-Type": "application/octet-stream" }, body: file });
        const body = await reply.json();
        if (!reply.ok) throw new Error(body.error);
        exploreState = { type: "", text: "" };
        drawExplore(body, {});
      } catch (problem) { status.replaceChildren(el("span", { className: "bad-t", textContent: problem.message })); }
    };
    const drop = el("div", { className: "drop", on: {
      dragover: (e) => { e.preventDefault(); drop.classList.add("over"); },
      dragleave: () => drop.classList.remove("over"),
      drop: (e) => { e.preventDefault(); drop.classList.remove("over"); open(e.dataTransfer.files[0]); } } },
      el("p", { textContent: "Drop a datapack, resource pack, mod or any .zip / .jar here" }),
      el("div", { className: "tool", style: "justify-content:center" }, el("button", { textContent: "Choose a file", on: { click: () => picker.click() } })),
      el("p", { className: "dim", textContent: "You will see everything inside, by type, and can pick files to keep. Nothing is unpacked or run." }), picker);
    return drop;
  };
  if (!listing) {
    page(el("h1", { textContent: "Look inside" }), dropzone(), status);
    return;
  }
  const fill = (node, ...kids) => node.replaceChildren(...kids.flat().filter((k) => k !== null && k !== undefined));
  const count = (n) => n.toLocaleString("en");
  const size = (bytes) => (bytes > 1048576 ? `${(bytes / 1048576).toFixed(1)} MB` : bytes > 1024 ? `${Math.round(bytes / 1024)} KB` : `${bytes} B`);
  const list = el("div", { className: "ticks" });
  const chips = el("div", { className: "tool" });
  const preview = el("div");
  const actions = el("div", { className: "tool" });
  const filter = el("input", { type: "text", placeholder: "Filter by name", "aria-label": "Filter", value: exploreState.text, style: "flex:1" });
  const keep = el("input", { type: "checkbox", checked: true });
  const shown = () => {
    const words = filter.value.toLowerCase().split(/\s+/).filter(Boolean);
    return listing.entries.filter((e) => (!exploreState.type || e.type === exploreState.type) && words.every((w) => e.path.toLowerCase().includes(w)));
  };
  const drawChips = () => chips.replaceChildren(
    el("button", { textContent: `All (${count(listing.entries.length)})`, className: exploreState.type === "" ? "main" : "", on: { click: () => { exploreState.type = ""; draw(); } } }),
    ...listing.types.map((t) => el("button", { textContent: `${t.label} (${count(t.count)})`, className: exploreState.type === t.type ? "main" : "", on: { click: () => { exploreState.type = t.type; draw(); } } })));
  const showPreview = async (entry) => {
    preview.replaceChildren(el("div", { className: "dim", textContent: `Opening ${entry.path}…` }));
    const link = `/api/explore/file?token=${listing.token}&path=${encodeURIComponent(entry.path)}`;
    try {
      const info = await get(`/api/explore/preview?token=${listing.token}&path=${encodeURIComponent(entry.path)}`);
      const head = el("div", { className: "name", style: "word-break:break-all", textContent: `${entry.path} · ${size(entry.size)}` });
      let body;
      if (info.kind === "image") body = el("img", { src: link, alt: entry.path, style: "image-rendering:pixelated;max-width:100%;max-height:300px;min-width:64px;background:rgba(127,127,127,.2)" });
      else if (info.kind === "sound") body = el("audio", { src: link, controls: true });
      else if (info.kind === "text") body = el("pre", { style: "max-height:300px;overflow:auto;font-size:12px;margin:6px 0;white-space:pre-wrap;word-break:break-all", textContent: info.text + (info.cut ? "\n… (cut)" : "") });
      else body = el("p", { className: "dim", textContent: "No preview for this kind of file. You can still tick it and download it." });
      preview.replaceChildren(el("div", { className: "q" }, head, body));
    } catch (problem) { preview.replaceChildren(el("div", { className: "bad-t", textContent: problem.message })); }
  };
  const draw = () => {
    exploreState.text = filter.value;
    drawChips();
    const rows = shown();
    fill(list, rows.slice(0, EXPLORE_SHOWN).map((e) => el("label", {},
      el("input", { type: "checkbox", checked: ticked.has(e.path), on: { change: (x) => { if (x.target.checked) ticked.add(e.path); else ticked.delete(e.path); drawActions(); } } }), " ",
      el("a", { href: "#", style: "color:inherit", textContent: e.path, on: { click: (x) => { x.preventDefault(); showPreview(e); } } }), el("span", { className: "dim", textContent: ` · ${size(e.size)}` }))),
      rows.length > EXPLORE_SHOWN ? el("div", { className: "dim", textContent: `Showing ${count(EXPLORE_SHOWN)} of ${count(rows.length)}. Filter to see the rest. Tick all shown covers all of them.` }) : null,
      rows.length ? null : el("div", { className: "dim", textContent: "Nothing matches." }));
    drawActions();
  };
  const drawActions = () => {
    const structures = listing.types.find((t) => t.type === "structure");
    fill(actions,
      el("button", { textContent: "Tick all shown", on: { click: () => { shown().forEach((e) => ticked.add(e.path)); draw(); } } }),
      el("button", { textContent: "Untick all", on: { click: () => { ticked.clear(); draw(); } } }),
      el("label", { className: "opt" }, keep, " Keep folders"),
      el("button", { className: "main", textContent: ticked.size ? `Download ${count(ticked.size)} as zip` : "Download as zip", disabled: !ticked.size, on: { click: async () => {
        const reply = await fetch("/api/explore/export", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ token: listing.token, paths: [...ticked], keep_folders: keep.checked }) });
        if (!reply.ok) return showFailure(new Error((await reply.json()).error));
        const link = el("a", { href: URL.createObjectURL(await reply.blob()), download: listing.name.replace(/\.[^.]+$/, "") + "-picked.zip" });
        link.click();
      } } }),
      el("button", { textContent: ticked.size ? `Save ${count(ticked.size)} to Library` : "Save to Library", disabled: !ticked.size, title: "Keeps textures, sounds, JSON data and models in the Library. Builds go through Import.", on: { click: async () => {
        status.replaceChildren(el("span", { className: "spin" }), "Saving…");
        try {
          const done = await post("/api/explore/save", { token: listing.token, paths: [...ticked], ...(meta.details || {}) });
          const parts = [`${count(done.ids.length)} saved`];
          if (done.duplicates) parts.push(`${count(done.duplicates)} already in the Library`);
          if (done.skipped.length) parts.push(`${count(done.skipped.length)} not kept (${done.skipped[0].reason})`);
          status.replaceChildren(el("span", { className: done.ids.length ? "good-t" : "dim", textContent: parts.join(" · ") + ". " }),
            done.ids.length ? el("a", { href: "#", textContent: "Open in Library", style: "color:inherit", on: { click: (e) => { e.preventDefault(); done.batch ? drawLibrary({ batch: done.batch }) : openProject(done.ids[0]); } } }) : null);
        } catch (problem) { status.replaceChildren(el("span", { className: "bad-t", textContent: problem.message })); }
      } } }),
      structures ? el("button", { textContent: `Import the ${count(structures.count)} structures`, title: "Opens the normal Import screen with this archive's builds", on: { click: async () => {
        try {
          const got = await post("/api/explore/to-import", { token: listing.token });
          drawImport({ source: { token: got.token, name: got.name, items: got.items }, details: meta.details || {}, notice: meta.notice || "" });
        } catch (problem) { showFailure(problem); }
      } } }) : null);
  };
  filter.addEventListener("input", draw);
  page(el("h1", { textContent: listing.name }),
    el("p", { className: "dim", textContent: `${count(listing.total)} files${listing.namespaces.length ? " · " + listing.namespaces.slice(0, 6).join(", ") + (listing.namespaces.length > 6 ? "…" : "") : ""}${listing.truncated ? ". Only the first " + count(listing.entries.length) + " are listed." : ""}` }),
    chips, el("div", { className: "tool" }, filter), actions, preview, list,
    el("div", { className: "row" }, el("button", { textContent: "Open another file", on: { click: () => { exploreState = { type: "", text: "" }; drawExplore(); } } })), status);
  $("page").classList.add("wide");
  draw();
}

// ---- find online ----
let findState = { source: "modrinth", q: "", type: "datapack", builds: true, page: 0 };

async function drawFind() {
  screen = "find";
  stopPolling();
  project = null;
  steps();
  const info = await get("/api/find/sources");
  const sourceOf = (id) => info.sources.find((s) => s.id === id) || info.sources[0];
  const query = el("input", { type: "search", size: 40, placeholder: "What are you looking for? For example: oak tree, castle, village house", "aria-label": "Search", value: findState.q });
  const source = el("select", { "aria-label": "Where to search" }, info.sources.map((s) => el("option", { value: s.id, textContent: s.label })));
  source.value = findState.source;
  const type = el("select", { "aria-label": "Type" });
  const builds = el("input", { type: "checkbox", checked: findState.builds });
  const buildsLabel = el("label", { className: "opt" }, builds, " Only likely builds. Untick to search everything.");
  const note = el("p", { className: "dim" });
  const status = el("div", { className: "dim", role: "status" });
  const list = el("div");
  const count = (n) => n.toLocaleString("en");
  const fillTypes = () => {
    const current = sourceOf(source.value);
    type.replaceChildren(...current.types.map(([v, t]) => el("option", { value: v, textContent: t })));
    type.value = current.types.some(([v]) => v === findState.type) ? findState.type : current.types[0][0];
    type.style.display = current.types.length > 1 ? "" : "none";
    buildsLabel.style.display = current.id === "curseforge" || type.value === "resourcepack" ? "none" : "";
    note.textContent = current.note + (current.needs_key && !current.has_key ? " No key is saved yet: add it in Settings." : "");
    note.className = current.needs_key && !current.has_key ? "bad-t" : "dim";
  };
  type.addEventListener("change", () => { findState.type = type.value; fillTypes(); });
  source.addEventListener("change", () => { findState.source = source.value; fillTypes(); list.replaceChildren(); status.textContent = ""; });
  fillTypes();

  const search = async (page = 0) => {
    findState = { source: source.value, q: query.value.trim(), type: type.value, builds: builds.checked, page };
    status.replaceChildren(el("span", { className: "spin" }), `Searching ${sourceOf(source.value).label}…`);
    list.replaceChildren();
    try {
      const found = await get(`/api/find/search?source=${findState.source}&q=${encodeURIComponent(findState.q)}&type=${findState.type}&builds=${findState.builds ? 1 : 0}&page=${page}`);
      status.textContent = found.total ? `${count(found.total)} found. Showing ${count(page * found.per_page + 1)} to ${count(page * found.per_page + found.hits.length)}.` : "Nothing found. Try other words, or untick the builds filter.";
      list.replaceChildren(...found.hits.map(card), el("div", { className: "row" },
        el("button", { textContent: "Previous", disabled: page === 0, on: { click: () => search(page - 1) } }),
        el("button", { textContent: "Next", disabled: (page + 1) * found.per_page >= found.total, on: { click: () => search(page + 1) } })));
    } catch (problem) { status.replaceChildren(el("span", { className: "bad-t", textContent: problem.message })); }
  };
  const card = (hit) => {
    const result = el("div", { className: "dim", role: "status" });
    const meta = { details: { creator: hit.author, link: hit.url, licence: hit.licence }, notice: hit.warning ? `${hit.title}: ${hit.warning} Check the terms before using it in the mod.` : "" };
    const isPack = hit.kind === "resourcepack";  // a resource pack has textures, not builds
    const button = el("button", { className: "main", textContent: "Import", style: isPack ? "display:none" : "", on: { click: async () => {
      button.disabled = true;
      result.replaceChildren(el("span", { className: "spin" }), "Downloading and reading it…");
      try {
        const got = await post("/api/find/fetch", { project: hit.id, source: hit.source });
        drawImport({ source: { token: got.token, name: got.name, items: got.items }, ...meta });
      } catch (problem) { result.replaceChildren(el("span", { className: "bad-t", textContent: problem.message })); button.disabled = false; }
    } } });
    const label = sourceOf(hit.source).label;
    return el("div", { className: "q" },
      el("b", { textContent: hit.title }), el("span", { className: "dim", textContent: ` by ${hit.author} · ${count(hit.downloads)} ${hit.source === "github" ? "stars" : "downloads"}${hit.categories.length ? " · " + hit.categories.join(", ") : ""}` }),
      el("p", { textContent: hit.description }),
      el("p", { className: hit.warning ? "bad-t" : "dim", textContent: `Licence: ${hit.licence || "not given"}${hit.warning ? ". " + hit.warning : ""}` }),
      el("div", { className: "row" }, button,
        el("button", { textContent: "Look inside", className: isPack ? "main" : "", title: isPack ? "Download it, filter the textures you want, and save them to the Library" : "Download it and see everything in it, not only the builds", on: { click: async () => {
          result.replaceChildren(el("span", { className: "spin" }), "Downloading…");
          try {
            const got = await post("/api/find/fetch", { project: hit.id, source: hit.source, look_inside: true });
            drawExplore(got, meta);
          } catch (problem) { result.replaceChildren(el("span", { className: "bad-t", textContent: problem.message })); }
        } } }),
        el("button", { textContent: `Open on ${label}`, on: { click: () => post("/api/find/open", { url: hit.url }).catch((problem) => { result.textContent = problem.message; }) } })),
      result);
  };
  query.addEventListener("keydown", (e) => { if (e.key === "Enter") search(0); });
  page(el("h1", { textContent: "Find online" }),
    el("p", { className: "dim", textContent: "Nothing is downloaded until you press Import or Look inside on a result. Files are only read, never run." }),
    el("div", { className: "row" }, query, source, type, el("button", { className: "main", textContent: "Search", on: { click: () => search(0) } })),
    buildsLabel, note,
    el("div", { className: "tool" }, el("span", { className: "dim", textContent: "Sites with no search interface for apps (opens your browser; download there, then drop the file into Look inside):" }),
      ...info.sites.map((site) => el("button", { textContent: site, on: { click: () => post("/api/find/browse", { site, q: query.value }).catch((problem) => { status.textContent = problem.message; }) } }))),
    status, list);
  if (findState.q) search(findState.page);
}

// ---- backups ----
function backupSection() {
  const list = el("div"), status = el("div");
  const note = (text, good) => el("div", { className: good ? "good-t" : "bad-t", role: "status", textContent: text });
  const size = (bytes) => (bytes > 1048576 ? `${(bytes / 1048576).toFixed(1)} MB` : `${Math.max(1, Math.round(bytes / 1024))} KB`);
  const label = { auto: "automatic", manual: "manual", "before-restore": "before a restore" };
  const draw = (backups) => {
    list.replaceChildren(...(backups.length ? backups.map((b) => el("div", { className: "row" },
      el("span", { textContent: `${new Date(b.time * 1000).toLocaleString()} · ${b.assets} assets · ${size(b.bytes)} · ${label[b.reason]}` }),
      el("span", { className: "grow" }),
      el("button", { textContent: "Restore", on: { click: async () => {
        if (!confirm(`Replace the whole library with this backup from ${new Date(b.time * 1000).toLocaleString()}? The current library is backed up first.`)) return;
        try {
          const done = await post("/api/backups/restore", { name: b.name });
          draw(done.backups);
          status.replaceChildren(note(`Restored: ${done.assets} assets.`, true));
        } catch (problem) { status.replaceChildren(note(problem.message, false)); }
      } } }))) : [el("p", { className: "dim", textContent: "No backups yet." })]));
  };
  get("/api/backups").then((r) => draw(r.backups)).catch((problem) => status.replaceChildren(note(problem.message, false)));
  return el("div", { className: "q" }, el("b", { textContent: "Library backup" }),
    el("p", { className: "dim", textContent: "A backup is made when the app starts if the library changed since the last one. The newest 10 are kept, in .vestiges-assets/backups. API keys are never included." }),
    list,
    el("div", { className: "row" }, el("button", { textContent: "Back up now", on: { click: async () => {
      status.replaceChildren(el("span", { className: "dim", textContent: "Backing up…" }));
      try {
        const done = await post("/api/backups/create");
        draw(done.backups);
        status.replaceChildren(note(done.backup ? "Backup made." : "Nothing to back up yet.", true));
      } catch (problem) { status.replaceChildren(note(problem.message, false)); }
    } } }), status));
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
  const curseKey = el("input", { type: "password", autocomplete: "off", size: 40, placeholder: app.curseforge_key ? "Saved. Paste a new one to replace it." : "Paste your key", "aria-label": "CurseForge API key" });
  const claudeStatus = el("div"), geminiStatus = el("div"), saveStatus = el("div");
  const save = async () => {
    const keys = {};
    if (claudeKey.value.trim()) keys.claude = claudeKey.value.trim();
    if (geminiKey.value.trim()) keys.gemini = geminiKey.value.trim();
    if (curseKey.value.trim()) keys.curseforge = curseKey.value.trim();
    await post("/api/settings", { keys, settings: { budget: Math.max(Number(budget.value) || 1, 0.1), send_images: images.checked, safety_fallback: fallback.checked, models: { claude: claudeModel.value, gemini: geminiModel.value } } });
    claudeKey.value = geminiKey.value = curseKey.value = "";
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
    el("div", { className: "q" }, el("b", { textContent: "CurseForge (for Find online)" }),
      el("p", { className: "dim", textContent: "A free key from console.curseforge.com lets Find online search CurseForge. It is kept only on this computer." }),
      el("div", { className: "row" }, curseKey)),
    el("div", { className: "q" }, el("b", { textContent: "Spending and privacy" }),
      el("div", { className: "row" }, el("label", {}, "Limit per asset, in dollars: ", budget)),
      el("p", { className: "dim", textContent: "The work stops at this limit and asks before spending more. For Gemini, whose prices aren't known here, it counts tokens instead: 300,000 per dollar." }),
      el("label", { className: "opt" }, images, " Send pictures of the asset to the AI for review. They contain game textures. Without them, it judges from measurements only."),
      el("label", { className: "opt" }, fallback, " If Claude's safety filter wrongly declines a request, let another Claude model try it.")),
    backupSection(),
    el("div", { className: "row" }, saveStatus, el("span", { className: "grow" }),
      el("button", { className: "main", textContent: "Save", on: { click: async () => {
        try { await save(); saveStatus.replaceChildren(note("Saved.", true)); } catch (problem) { saveStatus.replaceChildren(note(problem.message, false)); }
      } } })),
  );
}

initReview({
  page: (...nodes) => page(...nodes),
  showFailure: (problem) => showFailure(problem),
  enter: () => { screen = "review"; stopPolling(); project = null; queue = null; backToReview = null; steps(); },
  attach3d: async (slot, id, number, raise = 0) => {
    slot.append($("view"));
    if (viewHeldFor === id + ":" + number) { viewer.resume(); viewer.setLift(raise); return; }
    viewHeldFor = id + ":" + number;
    try { await viewer.show([await get(`/api/version?id=${encodeURIComponent(id)}&v=${number}`)], false, raise); } catch (problem) { viewHeldFor = null; throw problem; }
  },
  lift3d: (blocks) => viewer.setLift(blocks),
  openWorkbench: (id) => { leaveReview(); backToReview = id; openProject(id).catch((problem) => showFailure(problem)); },
});

initDashboard({
  page: (...nodes) => page(...nodes),
  showFailure: (problem, where) => showFailure(problem, where),
  enter: () => { screen = "dashboard"; stopPolling(); project = null; queue = null; backToReview = null; steps(); },
  isActive: () => screen === "dashboard",
  openReview: (options) => drawReview(options),
  openLibrary: (start) => drawLibrary(start).catch((problem) => showFailure(problem)),
  openImport: () => drawImport(),
  openSend: (ids) => drawSend(ids, () => drawDashboard()).catch((problem) => showFailure(problem)),
});

$("homeBtn").onclick = () => drawDashboard();
$("reviewBtn").onclick = () => drawReview().catch((problem) => showFailure(problem));
$("newBtn").onclick = drawHome;
$("findBtn").onclick = drawFind;
$("exploreBtn").onclick = () => drawExplore();
$("libraryBtn").onclick = () => drawLibrary({ batch: "" }).catch((problem) => showFailure(problem));
$("settingsBtn").onclick = drawSettings;
get("/api/state").then((state) => { app = state; }).catch(() => {});
drawDashboard().catch(() => drawHome());
