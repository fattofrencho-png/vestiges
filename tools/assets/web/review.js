// The Review screen: one asset at a time, keyboard first. The machine proposes; you accept, change or reject.
// Everything from the server is shown with textContent, never as HTML.

const $ = (id) => document.getElementById(id);
const REASONS = [["duplicate", "Duplicate"], ["ugly", "Ugly"], ["wrong", "Wrong / broken"], ["not vanilla", "Not vanilla"], ["too small", "Too small"], ["package", "Package (split first)"], ["other", "Other"]];
const VIEW_HELP = { inbox: "Everything not reviewed yet, similar builds together.", sure: "The machine is sure: quick to accept.", unsure: "The machine's weakest guesses first: your answer matters most here.",
  groups: "Builds that look alike (turned or mirrored copies, same shape): pick the one to keep.", needs: "Marked 'needs work': fix them in the workbench.",
  accepted: "Already in the catalog.", rejected: "Archived. Restore any of them.", all: "Everything that has been analysed." };
const RIGHTS_WORDS = { own: "Your own work", open: "Open licence", permission: "Permission noted", limited: "Restricted licence: don't publish", unknown: "Rights unknown: made by someone else, nothing noted (to confirm)" };
const KEYS = [["A / Enter", "accept everything shown"], ["1-9", "change that row"], ["X", "archive (then 1-6 for the reason)"], ["N", "needs work"], ["S / →", "later"], ["←", "previous"],
  ["U / Ctrl+Z", "undo"], ["E", "open in the workbench"], ["[ / ]", "sink or raise the build by one block (3D view)"], ["?", "show or hide these keys"]];

const state = { active: false, view: "inbox", filter: { type: "", species: "", pack: "", q: "" }, ids: [], index: 0, counts: {}, meta: { types: [], packs: [], species: [] },
  undo: [], seen: [], cache: new Map(), data: null, busy: false, reason: false, help: false, overlay: null, message: "",
  mode3d: (() => { try { return localStorage.getItem("reviewPicture") !== "flat"; } catch (problem) { return true; } })() };
let host = null;

function el(tag, props = {}, ...children) {
  const node = document.createElement(tag);
  for (const [key, value] of Object.entries(props)) {
    if (key === "on") Object.entries(value).forEach(([name, fn]) => node.addEventListener(name, fn));
    else if (key in node) node[key] = value;
    else node.setAttribute(key, value);
  }
  node.append(...children.flat().filter((c) => c !== null && c !== undefined && c !== false));
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
function remember() {
  try { localStorage.setItem("reviewState", JSON.stringify({ view: state.view, filter: state.filter })); } catch (problem) { /* not saved */ }
}
function recall() {
  try {
    const saved = JSON.parse(localStorage.getItem("reviewState") || "null");
    if (saved && saved.view) { state.view = saved.view; state.filter = { ...state.filter, ...saved.filter }; }
  } catch (problem) { /* start fresh */ }
}

export function initReview(hooks) {
  host = hooks;
  recall();
  document.addEventListener("keydown", onKey);
}
export function leaveReview() { state.active = false; }

const current = () => state.ids[state.index] || null;
const params = () => new URLSearchParams({ view: state.view, type: state.filter.type, species: state.filter.species, source: state.filter.pack, q: state.filter.q });

async function loadQueue(keepId) {
  const data = await get("/api/review/queue?" + params());
  state.ids = data.ids;
  state.counts = data.counts;
  state.meta = data;
  state.index = keepId && data.ids.includes(keepId) ? data.ids.indexOf(keepId) : 0;
}
async function refreshCounts() {
  try { state.counts = (await get("/api/review/queue?" + params())).counts; paintTop(); } catch (problem) { /* the numbers catch up next time */ }
}
async function asset(id) {
  if (!state.cache.has(id)) state.cache.set(id, get("/api/review/asset?id=" + encodeURIComponent(id)));
  try { return await state.cache.get(id); } catch (problem) { state.cache.delete(id); throw problem; }
}
function prefetch() {
  for (const id of state.ids.slice(state.index + 1, state.index + 3)) {
    asset(id).then((a) => { new Image().src = `/api/thumb?id=${id}&v=${a.versions}`; }).catch(() => {});
  }
}

export async function drawReview(start = {}) {
  host.enter();
  state.active = true;
  if (start.view) {  // opened from somewhere else with a view: start from clean filters so an old filter can't hide things
    state.view = start.view;
    state.filter = { type: "", species: "", pack: "", q: "", ...(start.filter || {}) };
  } else if (start.filter) {
    state.filter = { ...state.filter, ...start.filter };
  }
  state.message = "";
  await loadQueue(start.id);
  await show();
}

// ---- drawing ----
async function show() {
  const id = current();
  state.data = null;
  if (id) {
    try { state.data = await asset(id); } catch (problem) {
      // The asset is gone (deleted elsewhere): drop it and go on.
      state.ids.splice(state.index, 1);
      return show();
    }
  }
  if (!state.active) return;
  remember();
  paint();
  if (id) prefetch();
}

function setMode(on) {
  state.mode3d = on;
  try { localStorage.setItem("reviewPicture", on ? "3d" : "flat"); } catch (problem) { /* not saved */ }
  paint();
}

function dots(confidence) {
  const n = Math.round(confidence * 5);
  return el("span", { className: confidence >= 0.8 ? "good-t" : confidence >= 0.5 ? "warn-t" : "bad-t", title: `${Math.round(confidence * 100)}% sure`, textContent: "●".repeat(n) + "○".repeat(5 - n) });
}
function liftBar(a) {
  const n = a.ground_offset || 0;
  const words = n === 0 ? "level: lowest layer on the ground (as saved)" : n < 0 ? `sunk ${-n} block${n === -1 ? "" : "s"} into the ground` : `raised ${n} block${n === 1 ? "" : "s"} above the ground`;
  return el("div", { className: "row rv-lift" },
    el("button", { textContent: "▼ Lower", title: "Sink the build one block into the ground ( [ )", on: { click: () => lift(-1) } }),
    el("button", { textContent: "▲ Raise", title: "Lift the build one block off the ground ( ] )", on: { click: () => lift(1) } }),
    n ? el("button", { textContent: "Level", title: "Back to as saved", on: { click: () => lift(-n) } }) : null,
    el("span", { className: n ? "warn-t" : "dim", textContent: `Ground: ${words}` }));
}
async function lift(step) {
  const a = state.data;
  if (!a) return;
  const value = (a.ground_offset || 0) + step;
  if (Math.abs(value) > 16) return;
  a.ground_offset = value;          // shown at once; the save follows
  host.lift3d(value);
  document.querySelector(".rv-lift")?.replaceWith(liftBar(a));
  try {
    await post("/api/review/decide", { id: a.id, action: "ground_offset", value });
    state.cache.delete(a.id);
    state.undo.push(a.id);
  } catch (problem) { host.showFailure(problem); }
}
function nameBox(f) {
  return el("input", { type: "text", className: "rv-text", value: f.value || "", maxLength: 60, "aria-label": "Name", placeholder: "Name this build",
    on: { change: (e) => decide("change", { facet: "name", value: e.target.value }, false) } });
}
function select(options, value, label, on) {
  const list = options.includes(value) || !value ? options : [value, ...options];
  const node = el("select", { "aria-label": label },
    value ? null : el("option", { value: "", textContent: "(nothing)" }),
    list.map((o) => el("option", { value: o, textContent: o })),
    el("option", { value: "\u0000other", textContent: "Other…" }));
  node.value = value || "";
  node.addEventListener("change", () => {
    let chosen = node.value;
    if (chosen === "\u0000other") { chosen = (prompt(`${label}: type a value`) || "").trim(); if (!chosen) { node.value = value || ""; return; } }
    on(chosen);
  });
  return node;
}
function filterSelect(label, value, options, on) {
  const node = el("select", { "aria-label": label }, el("option", { value: "", textContent: label }), options.map((o) => el("option", { value: o, textContent: o })));
  node.value = value;
  node.addEventListener("change", () => on(node.value));
  return node;
}

let topNode = null;
function paintTop() {
  if (!topNode) return;
  const c = state.counts;
  const left = state.ids.length;
  topNode.replaceChildren(
    el("b", { textContent: left ? `${state.index + 1} of ${left}` : "0 left" }),
    el("span", { className: "dim", textContent: ` · ${c.inbox ?? 0} not reviewed · ${c.accepted ?? 0} accepted · ${c.rejected ?? 0} archived · ${c.today ?? 0} decided today` }),
    ...(c.unanalysed ? [el("span", { className: "warn-t", textContent: ` · ${c.unanalysed} not analysed yet` })] : []));
}

function paint() {
  const c = state.counts;
  const switchView = (view) => { state.view = view; state.message = ""; loadQueue().then(show).catch(host.showFailure); };
  const setFilter = (change) => { state.filter = { ...state.filter, ...change }; loadQueue().then(show).catch(host.showFailure); };
  const search = el("input", { type: "text", value: state.filter.q, placeholder: "Search", "aria-label": "Search", size: 14 });
  let waiting = null;
  search.addEventListener("input", () => { clearTimeout(waiting); waiting = setTimeout(() => setFilter({ q: search.value }), 350); });
  topNode = el("div", { className: "rv-count" });
  const views = el("select", { "aria-label": "Queue" }, state.meta.views ? state.meta.views.map(([v, t]) => el("option", { value: v, textContent: `${t} (${c[v] ?? 0})` })) : null);
  views.value = state.view;
  views.addEventListener("change", () => switchView(views.value));
  const bar = el("div", { className: "rv-bar" }, views,
    filterSelect("Any type", state.filter.type, state.meta.types, (v) => setFilter({ type: v })),
    filterSelect("Any species", state.filter.species, state.meta.species, (v) => setFilter({ species: v })),
    filterSelect("Any pack", state.filter.pack, state.meta.packs, (v) => setFilter({ pack: v })), search,
    state.view === "sure" && state.ids.length ? el("button", { className: "good", textContent: "Accept the sure ones…", on: { click: () => batchPreview().catch(host.showFailure) } }) : null,
    el("button", { textContent: "?", title: "Keys", on: { click: () => { state.help = !state.help; paint(); } } }));
  const body = state.data ? assetView(state.data) : empty();
  host.page(
    el("div", { className: "rv" }, bar, topNode,
      el("p", { className: "dim", textContent: VIEW_HELP[state.view] || "" }),
      state.message ? el("div", { className: "notice", textContent: state.message }) : null,
      state.help ? helpBox() : null,
      state.reason ? reasonBar() : null,
      body,
      state.overlay));
  $("page").classList.add("wide");
  paintTop();
  const slot = document.querySelector(".rv-3d");
  if (slot && state.data) host.attach3d(slot, state.data.id, state.data.latest, state.data.ground_offset || 0).catch((problem) => { slot.textContent = "The 3D view could not open: " + problem.message; });
}

function helpBox() {
  return el("div", { className: "q" }, el("b", { textContent: "Keys" }),
    el("table", {}, KEYS.map(([k, t]) => el("tr", {}, el("td", {}, el("kbd", { textContent: k })), el("td", { textContent: t })))));
}
function reasonBar() {
  return el("div", { className: "notice bad" }, el("b", { textContent: "Archive. Why? " }),
    REASONS.map(([v, t], i) => el("button", { textContent: `${i + 1} ${t}`, on: { click: () => decide("reject", { reason: v }) } })),
    el("button", { className: "danger", textContent: "D Delete now", title: "Removes it from the library. Can't be undone.", on: { click: () => deleteNow() } }),
    el("button", { textContent: "Esc Cancel", on: { click: () => { state.reason = false; paint(); } } }));
}
function empty() {
  const c = state.counts;
  const others = (state.meta.views || []).filter(([v]) => v !== state.view && (c[v] ?? 0) > 0 && v !== "all");
  return el("div", { className: "q" }, el("b", { textContent: state.view === "inbox" ? "Nothing left to review here." : "Nothing in this view." }),
    el("p", { className: "dim", textContent: c.unanalysed ? `${c.unanalysed} builds have not been analysed yet (run triage.py analyse).` : "Try another queue or clear the filters." }),
    el("div", { className: "row" }, others.map(([v, t]) => el("button", { textContent: `${t} (${c[v]})`, on: { click: () => { state.view = v; loadQueue().then(show).catch(host.showFailure); } } }))));
}

function assetView(a) {
  const state_ = a.review.state;
  const stateWord = { inbox: "Not reviewed", accepted: "Accepted", rejected: `Archived (${a.review.reason || "no reason"})`, needs_work: "Needs work" }[state_] || state_;
  const rows = a.facets.map((f, i) => {
    const key = i + 1;
    return el("div", { className: "rv-row" },
      el("kbd", { textContent: key <= 9 ? String(key) : "" }),
      el("div", { className: "rv-name", textContent: f.facet }),
      el("div", {}, f.facet === "name" ? nameBox(f) : select(f.options, f.value, f.facet, (value) => decide("change", { facet: f.facet, value }, false)), " ", dots(f.confidence),
        el("span", { className: f.status === "proposed" ? "dim" : "good-t", textContent: ` ${f.status}${f.source === "human" ? "" : " · " + f.source}` }),
        f.status === "proposed" ? el("button", { className: "rv-mini", textContent: "✓", title: "Agree with this row", on: { click: () => decide("accept", { facet: f.facet }, false) } }) : null,
        f.status !== "rejected" && f.source !== "human" ? el("button", { className: "rv-mini", textContent: "✕", title: "This row is wrong: don't offer it again", on: { click: () => decide("reject_facet", { facet: f.facet }, false) } }) : null,
        el("div", { className: "dim", textContent: f.why })));
  });
  const lib = a.library || {};
  rows.push(el("div", { className: "rv-row" }, el("kbd", { textContent: "" }), el("div", { className: "rv-name", textContent: "library file" }),
    el("div", {}, el("code", { className: "rv-file", textContent: lib.file_name || "" }), el("div", { className: "dim", textContent: `card ${lib.card || ""} · built from type, species, the permanent card number and the name above` }))));
  const m = a.measures;
  const facts = [`${m.w ?? "?"}×${m.h ?? "?"}×${m.d ?? "?"}`, `${(m.blocks ?? a.blocks).toLocaleString("en")} blocks`, `${m.distinct ?? "?"} different blocks`,
    m.doors ? `${m.doors} doors` : null, m.beds ? `${m.beds} beds` : null, m.roofed ? `${m.roofed} cells of rooms` : null, m.symmetry !== undefined ? `${Math.round(m.symmetry * 100)}% symmetric` : null,
    m.parts > 1 ? `${m.parts} separate builds` : null].filter(Boolean).join(" · ");
  const related = a.related;
  const card = (r, note) => el("a", { href: "#", className: "rv-rel", title: r.title, on: { click: (e) => { e.preventDefault(); jump(r.id); } } },
    el("img", { src: `/api/thumb?id=${r.id}`, alt: "", loading: "lazy", width: 64, height: 64 }), el("div", { className: "dim", textContent: note || (r.review !== "inbox" ? r.review : "") }),
    el("div", { textContent: r.title.slice(0, 22) }));
  const hasRel = related.duplicates.length || related.shapes.length || related.palettes.length;
  const licenceBad = a.rights === "limited" || a.rights === "unknown";
  return el("div", { className: "rv-main" },
    el("div", { className: "rv-left" },
      el("div", { className: "row" },
        el("button", { className: state.mode3d ? "on" : "", textContent: "3D view", on: { click: () => setMode(true) } }),
        el("button", { className: state.mode3d ? "" : "on", textContent: "Picture", on: { click: () => setMode(false) } }),
        el("span", { className: "dim", textContent: state.mode3d ? "drag to spin · wheel to zoom · right-drag to move" : "" })),
      state.mode3d ? el("div", { className: "rv-3d" }) : null,
      state.mode3d ? liftBar(a) : null,
      state.mode3d ? null : el("img", { className: "rv-pic", src: `/api/thumb?id=${a.id}&v=${a.versions}`, alt: a.title }),
      el("h2", { textContent: a.title, style: "color:var(--text);font-size:17px" }),
      el("div", { className: "dim", textContent: `${a.pack || "own"} · ${a.path}`.slice(0, 140) }),
      el("div", { textContent: facts }),
      el("div", { className: licenceBad ? "bad-t" : "dim", textContent: `${RIGHTS_WORDS[a.rights] || a.rights}${a.creator ? " · " + a.creator : ""}` }),
      a.not_vanilla ? el("div", { className: "bad-t", textContent: "Not vanilla: " + (a.problems[0] || "").slice(0, 120) }) : null,
      hasRel ? el("div", {}, el("h2", { textContent: "Looks like" }),
        related.duplicates.length ? el("div", {}, el("div", { className: "dim", textContent: `Same build, turned or mirrored${related.keeper ? (related.keeper === a.id ? " · this is the one to keep" : " · keep the other one") : ""}` }),
          el("div", { className: "rv-rels" }, related.duplicates.map((r) => card(r)))) : null,
        related.shapes.length ? el("div", {}, el("div", { className: "dim", textContent: "Same shape, other materials" }), el("div", { className: "rv-rels" }, related.shapes.map((r) => card(r)))) : null,
        related.palettes.length ? el("div", {}, el("div", { className: "dim", textContent: "Same materials" }), el("div", { className: "rv-rels" }, related.palettes.slice(0, 6).map((r) => card(r)))) : null) : null),
    el("div", { className: "rv-right" },
      el("div", { className: "rv-state" }, el("b", { textContent: stateWord }), el("span", { className: "dim", textContent: ` · overall ${Math.round(a.confidence * 100)}% sure` })),
      rows,
      el("div", { className: "row" },
        el("button", { className: "good", textContent: "A  Accept all", on: { click: () => decide("accept_all") } }),
        el("button", { textContent: "X  Archive…", on: { click: () => { state.reason = true; paint(); } } }),
        el("button", { textContent: "N  Needs work", on: { click: () => decide("needs_work") } }),
        el("button", { textContent: "E  Workbench", on: { click: () => host.openWorkbench(a.id) } })),
      el("div", { className: "row" },
        el("button", { textContent: "←  Previous", disabled: !state.seen.length, on: { click: () => back() } }),
        el("button", { textContent: "S  Later", on: { click: () => later() } }),
        el("button", { textContent: "U  Undo", disabled: !state.undo.length, on: { click: () => undo() } }),
        state_ === "accepted" || state_ === "rejected" || state_ === "needs_work" ? el("button", { textContent: "Back to the inbox", on: { click: () => decide("restore") } }) : null)));
}

// ---- moving ----
function go(delta) {
  const id = current();
  if (id) state.seen.push(id);
  state.index = Math.min(Math.max(state.index + delta, 0), Math.max(state.ids.length - 1, 0));
  return show();
}
function later() {
  const id = current();
  if (!id || state.ids.length < 2) return;
  state.ids.push(state.ids.splice(state.index, 1)[0]);
  state.seen.push(id);
  show();
}
function back() {
  const id = state.seen.pop();
  if (!id) return;
  if (!state.ids.includes(id)) state.ids.splice(state.index, 0, id);
  state.index = state.ids.indexOf(id);
  show();
}
function jump(id) {
  const now = current();
  if (now) state.seen.push(now);
  if (!state.ids.includes(id)) state.ids.splice(state.index + 1, 0, id);
  state.index = state.ids.indexOf(id);
  show();
}

// ---- deciding ----
// Does this decision take the asset out of the view it is shown in?
const LEAVES = {
  accept_all: ["inbox", "sure", "unsure", "groups", "needs", "rejected"], reject: ["inbox", "sure", "unsure", "groups", "needs", "accepted"],
  needs_work: ["inbox", "sure", "unsure", "groups", "accepted", "rejected"], restore: ["needs", "rejected", "accepted"],
};
async function decide(action, extra = {}, advance = true) {
  const id = current();
  if (!id || state.busy) return;
  state.busy = true;
  try {
    await post("/api/review/decide", { id, action, ...extra });
    state.cache.delete(id);
    state.reason = false;
    state.message = "";
    if (["change", "accept", "reject_facet", "ground_offset"].includes(action)) state.undo.push(id);   // a single row can be undone too
    if (["accept_all", "reject", "needs_work", "restore"].includes(action)) {
      state.undo.push(id);
      if ((LEAVES[action] || []).includes(state.view)) {
        state.ids.splice(state.index, 1);
        state.index = Math.min(state.index, Math.max(state.ids.length - 1, 0));
      } else if (advance) {
        state.index = Math.min(state.index + 1, state.ids.length - 1);
      }
      refreshCounts();
    }
    await show();
  } catch (problem) { host.showFailure(problem); } finally { state.busy = false; }
}
async function undo() {
  const id = state.undo.pop();
  if (!id || state.busy) return;
  state.busy = true;
  try {
    await post("/api/review/decide", { id, action: "undo" });
    state.cache.delete(id);
    if (!state.ids.includes(id)) state.ids.splice(state.index, 0, id);
    state.index = state.ids.indexOf(id);
    state.message = "Undone.";
    refreshCounts();
    await show();
  } catch (problem) { host.showFailure(problem); } finally { state.busy = false; }
}
async function deleteNow() {
  const a = state.data;
  if (!a || !confirm(`Delete "${a.title}" from the library now? This can't be undone.`)) return;
  state.busy = true;
  try {
    await post("/api/review/decide", { id: a.id, action: "delete" });
    state.cache.delete(a.id);
    state.reason = false;
    state.ids.splice(state.index, 1);
    state.index = Math.min(state.index, Math.max(state.ids.length - 1, 0));
    state.message = `Deleted "${a.title}".`;
    refreshCounts();
    await show();
  } catch (problem) { host.showFailure(problem); } finally { state.busy = false; }
}

// ---- batch accept ----
async function batchPreview() {
  const f = state.filter;
  const body = { type: f.type, species: f.species, q: f.q, source: f.pack };
  const plan = await post("/api/review/batch", { ...body, dry_run: true });
  if (!plan.count) { state.message = "Nothing sure enough to accept in one go."; return paint(); }
  const close = () => { state.overlay = null; paint(); };
  state.overlay = el("div", { className: "rv-overlay" }, el("div", { className: "rv-dialog" },
    el("h1", { textContent: `Accept ${plan.count} sure builds?` }),
    el("p", { className: "dim", textContent: "The machine is at least 80% sure of what each one is. Builds that have a turned or mirrored twin are left out: pick their keeper in the Look-alikes queue. Each one can be sent back to the inbox later from the Accepted queue. A sample:" }),
    el("div", { className: "rv-rels" }, plan.sample.map((id) => el("img", { src: `/api/thumb?id=${id}`, alt: "", width: 64, height: 64, loading: "lazy" }))),
    el("div", { className: "row" },
      el("button", { className: "good", textContent: `Accept ${plan.count}`, on: { click: async () => {
        try { const done = await post("/api/review/batch", { ...body, dry_run: false, ids: plan.ids }); state.message = `Accepted ${done.accepted} builds.`; state.overlay = null; state.cache.clear(); await loadQueue(); await show(); } catch (problem) { host.showFailure(problem); }
      } } }),
      el("button", { textContent: "Cancel", on: { click: close } }))));
  paint();
}

// ---- keyboard ----
function onKey(e) {
  if (!state.active || state.overlay) { if (state.overlay && e.key === "Escape") { state.overlay = null; paint(); } return; }
  const tag = (e.target && e.target.tagName) || "";
  if (tag === "INPUT" || tag === "TEXTAREA" || tag === "SELECT") { if (e.key === "Escape") e.target.blur(); return; }
  const key = e.key.toLowerCase();
  if (e.ctrlKey && key === "z") { e.preventDefault(); return undo(); }
  if (e.ctrlKey || e.altKey || e.metaKey) return;
  if (state.reason) {
    if (key === "escape") { state.reason = false; return paint(); }
    if (key === "d") return deleteNow();
    const n = Number(key);
    if (n >= 1 && n <= REASONS.length) return decide("reject", { reason: REASONS[n - 1][0] });
    return;
  }
  if (key === "a" || key === "enter") { e.preventDefault(); return decide("accept_all"); }
  if (key === "x") { if (current()) { state.reason = true; paint(); } return; }
  if (key === "n") return decide("needs_work");
  if (key === "s" || key === "arrowright" || key === " ") { e.preventDefault(); return later(); }
  if (key === "arrowleft") return back();
  if (key === "u" || key === "backspace") { e.preventDefault(); return undo(); }
  if (key === "e") { if (current()) host.openWorkbench(current()); return; }
  if (key === "?") { state.help = !state.help; return paint(); }
  if (key === "[" || key === "]") { if (state.mode3d) lift(key === "[" ? -1 : 1); return; }
  const n = Number(key);
  if (n >= 1 && n <= 9) {
    const box = document.querySelectorAll(".rv-row select, .rv-row .rv-text")[n - 1];
    if (box) { e.preventDefault(); box.focus(); try { box.showPicker(); } catch (problem) { /* arrow keys still work */ } }
  }
}
