// The welcome dashboard: where the catalog stands and what to do next. Server text is shown with textContent only.

let host = null;
let timer = null;
let showAll = false;

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

export function initDashboard(hooks) { host = hooks; }

const n = (value) => Number(value || 0).toLocaleString("en");
let notice = null;  // the result of the last export, shown under the buttons

function act(item) {
  if (item.run === "analyse") return post("/api/triage/analyse").then(() => drawDashboard());
  if (item.open) return host.openReview(item.open);
  if (item.library) return host.openLibrary(item.library);
  if (item.go === "import") return host.openImport();
  return null;
}

function setGoal(row) {
  const answer = prompt(`Goal for ${row.label}: how many accepted cards do you want? Leave empty for no goal.`, row.target || "");
  if (answer === null) return;
  const value = answer.trim() === "" ? null : Number(answer);
  if (value !== null && (!Number.isInteger(value) || value < 1)) return host.showFailure(new Error("Type a whole number, or leave it empty."));
  post("/api/targets", { key: row.key, value }).then(() => drawDashboard()).catch(host.showFailure);
}

function bar(done, goal) {
  const share = Math.min(100, Math.round((100 * done) / goal));
  return el("div", { className: "bar", role: "progressbar", "aria-valuenow": String(done), "aria-valuemax": String(goal), "aria-label": `${done} of ${goal}` }, el("div", { style: `width:${share}%` }));
}

async function exportVideo() {
  try {
    const r = await post("/api/export/video");
    notice = el("div", { className: "notice", textContent: `Wrote ${n(r.count)} builds (${n(r.video_ready)} ready for video, ${n(r.held_back)} held back for rights) to ${r.file}` });
  } catch (problem) { return host.showFailure(problem); }
  drawDashboard();
}
async function exportWorld(count) {
  if (!confirm(`Save ${count} builds with their proposed names, write the world list, and open Send to mod? Nothing reaches the mod until you confirm each file there.`)) return;
  try {
    const r = await post("/api/export/world", { prepare: true });
    notice = null;
    host.openSend(r.ids);
  } catch (problem) { host.showFailure(problem); }
}

export async function drawRights() {
  host.enter();
  const data = await get("/api/rights/packs");
  const message = el("div");
  const rows = data.packs.map((p) => {
    const creator = el("input", { type: "text", placeholder: "Creator", value: p.creator, maxLength: 200 });
    const licence = el("input", { type: "text", placeholder: "Licence, e.g. CC0, MIT, CC-BY-4.0", value: p.licence, maxLength: 200 });
    const permission = el("input", { type: "text", placeholder: "Permission: a link or where they said yes", maxLength: 500 });
    const link = el("input", { type: "text", placeholder: "Where it was found", maxLength: 500 });
    return el("div", { className: "q" },
      el("b", { textContent: p.source || "(no pack)" }), el("span", { className: p.unknown ? "bad-t" : "good-t", textContent: `  ${n(p.count)} builds · ${n(p.unknown)} with rights unknown${p.limited ? ` · ${p.limited} restricted` : ""}` }),
      el("div", { className: "tool" }, creator, licence), el("div", { className: "tool" }, permission, link),
      el("div", { className: "row" }, el("button", { className: "main", textContent: `Note for all ${n(p.count)}`, on: { click: async () => {
        try {
          const r = await post("/api/rights", { source: p.source, creator: creator.value, licence: licence.value, permission: permission.value, link: link.value });
          message.replaceChildren(el("div", { className: "notice", textContent: `Noted for ${n(r.changed)} builds.` }));
          drawRights();
        } catch (problem) { host.showFailure(problem, message); }
      } } })));
  });
  host.page(el("h1", { textContent: "Rights by pack" }),
    el("p", { className: "dim", textContent: "A build can feed the videos or the mod only when its rights are noted: your own work, an open licence, or a permission. Type what you know; fields you leave empty change nothing. Nothing here decides the rights for you." }),
    el("div", { className: "row" }, el("button", { textContent: "Back to the dashboard", on: { click: () => drawDashboard() } })), message, rows.length ? rows : el("p", { className: "dim", textContent: "No builds yet." }));
}

export async function drawDashboard() {
  clearTimeout(timer);
  host.enter();
  let d;
  let ex = { files: {}, video: 0, world: 0, accepted: 0 };
  try { [d, ex] = await Promise.all([get("/api/dashboard"), get("/api/exports")]); } catch (problem) { return host.showFailure(problem); }
  if (!host.isActive()) return;
  const c = d.counts, f = d.feeds, h = d.health;
  const open = (view, extra = {}) => () => host.openReview({ view, ...extra }).catch(host.showFailure);
  const stat = (label, value, view) => el("button", { className: "stat", disabled: !value, on: { click: open(view) } }, el("b", { textContent: n(value) }), el("span", { className: "dim", textContent: label }));

  const run = d.running;
  const focusCard = (item, first) => el("button", { className: "focus" + (first ? " first" : ""), on: { click: () => Promise.resolve(act(item)).catch(host.showFailure) } },
    el("b", { textContent: item.title }), el("span", { className: "dim", textContent: item.detail }));
  const focus = d.library === 0
    ? [el("button", { className: "focus first", on: { click: () => host.openImport() } }, el("b", { textContent: "Import your first builds" }), el("span", { className: "dim", textContent: "Load files, folders, mods or datapacks. Each one is checked automatically as it arrives." }))]
    : d.focus.length ? d.focus.map((item, i) => focusCard(item, i === 0))
      : [el("div", { className: "notice", textContent: "Nothing needs you right now. Everything analysed has been reviewed, and no goal has candidates waiting." })];

  const rows = d.coverage.filter((r) => showAll || r.target || r.accepted || r.inbox);
  const coverage = rows.map((r) => el("div", { className: "cov" + (r.total ? " total" : "") },
    el("div", { className: "cov-head" },
      el("a", { href: "#", textContent: r.total ? `${r.label} (all)` : r.label, style: r.species ? "padding-left:16px" : "", title: "Open this folder in Review", on: { click: (e) => { e.preventDefault(); host.openReview({ view: "all", filter: { type: r.type, species: r.species } }).catch(host.showFailure); } } }),
      el("span", { className: "dim", textContent: r.target ? `${r.accepted} of ${r.target}` : `${r.accepted} accepted` }),
      r.inbox ? el("span", { className: "warn-t", textContent: `${r.inbox} waiting` }) : null,
      r.editable ? el("button", { className: "rv-mini", textContent: r.target ? "goal" : "set goal", title: "Set how many accepted cards you want here", on: { click: () => setGoal(r) } }) : null),
    r.target ? bar(r.accepted, r.target) : null));

  host.page(
    el("h1", { textContent: "Welcome" }),
    el("p", { className: "dim", textContent: `${n(d.library)} builds in the library · ${n(d.analysed)} analysed · ${n(c.accepted)} accepted · ${n(c.inbox)} waiting for you · ${n(c.today)} decided today` }),
    run && run.running ? el("div", { className: "notice" }, el("span", { className: "spin" }), `Analysing ${n(run.done)} of ${n(run.total)}…`, run.failed ? ` (${run.failed} could not be read)` : "") : null,
    el("h2", { textContent: "Focus next" }), el("div", { className: "focus-list" }, focus),
    d.more.length ? el("details", {}, el("summary", { textContent: `More (${d.more.length})` }), el("div", { className: "focus-list" }, d.more.map((item) => focusCard(item, false)))) : null,
    el("div", { className: "dash" },
      el("div", { className: "q" }, el("b", { textContent: "Inbox" }),
        el("div", { className: "stats" }, stat("not reviewed", c.inbox, "inbox"), stat("sure", c.sure, "sure"), stat("unsure", c.unsure, "unsure"), stat("look-alikes", c.groups, "groups"),
          stat("needs work", c.needs, "needs"), stat("archived", c.rejected, "rejected"))),
      el("div", { className: "q" }, el("b", { textContent: "Feeds" }),
        el("p", {}, el("b", { textContent: n(f.video) }), " builds ready for the videos"),
        el("p", {}, el("b", { textContent: n(f.world) }), " ready for world generation", f.world ? el("span", { className: "dim", textContent: Object.keys(f.world_trees).length ? ` (trees: ${Object.entries(f.world_trees).map(([k, v]) => `${k} ${v}`).join(", ")})` : "" }) : null),
        f.blocked_rights ? el("p", { className: "warn-t", textContent: `${f.blocked_rights} accepted builds are held back: rights not noted.` }) : null,
        f.blocked_packages ? el("p", { className: "warn-t", textContent: `${f.blocked_packages} accepted builds are packages of many builds: split or archive them.` }) : null,
        f.blocked_not_vanilla ? el("p", { className: "warn-t", textContent: `${f.blocked_not_vanilla} are held back from the world: not vanilla.` }) : null,
        el("div", { className: "row" },
          el("button", { textContent: "Export for the videos", disabled: !ex.accepted, title: "Writes video-catalog.json and .csv. Builds without noted rights are listed but not ready.", on: { click: () => exportVideo() } }),
          el("button", { className: "main", textContent: ex.world ? `Send ${ex.world} to the mod…` : "Send to the mod…", disabled: !ex.world, title: "Saves proposed names, writes worldgen.json, then opens Send to mod to confirm each file", on: { click: () => exportWorld(ex.world) } }),
          el("button", { textContent: "Rights by pack…", title: "Note the creator, licence or permission once for a whole pack", on: { click: () => drawRights().catch(host.showFailure) } })),
        notice,
        Object.keys(ex.files).length ? el("div", { className: "dim" }, "Last exports: ", Object.entries(ex.files).map(([name, f]) => `${name} (${new Date(f.time * 1000).toLocaleString()})`).join(" · "), el("div", { textContent: Object.values(ex.files)[0].path.replace(/[^\/]+$/, "") })) : null)),
    el("div", { className: "q" }, el("b", { textContent: "Catalog progress" }),
      d.goal ? el("div", {}, el("div", { textContent: `${d.goal.done} of ${d.goal.goal} goal slots have an accepted build` }), bar(d.goal.done, d.goal.goal),
        el("div", { className: "dim", textContent: "Counts accepted builds, not how good they are. Ranking the best of each folder into series comes later." })) : el("p", { className: "dim", textContent: "No goals set yet. Press 'set goal' on a folder." }),
      coverage.length ? coverage : el("p", { className: "dim", textContent: "Nothing analysed yet." }),
      el("div", { className: "row" }, el("button", { textContent: showAll ? "Show only folders in use" : "Show all folders", on: { click: () => { showAll = !showAll; drawDashboard(); } } }))),
    el("div", { className: "q" }, el("b", { textContent: "Health" }),
      el("div", { className: "stats" },
        health("no source noted", h.no_credit, () => host.openLibrary({ no_credit: true })), health("not vanilla", h.not_vanilla, () => host.openLibrary({ not_vanilla: true })),
        health("look-alike groups", h.duplicate_groups + h.shape_groups, open("groups")), health("analysis errors", h.analysis_errors, null),
        health("not analysed", h.unanalysed, () => post("/api/triage/analyse").then(() => drawDashboard()).catch(host.showFailure)),
        el("div", { className: "stat" }, el("b", { className: h.backup_days === null || h.backup_days > 7 ? "warn-t" : "", textContent: h.backup_days === null ? "never" : h.backup_days < 1 ? "today" : `${Math.round(h.backup_days)} days ago` }), el("span", { className: "dim", textContent: "last backup" })))));
  $page().classList.add("wide");
  if (run && run.running) timer = setTimeout(() => { if (host.isActive()) drawDashboard(); }, 1000);
}

const $page = () => document.getElementById("page");
function health(label, value, onClick) {
  return el("button", { className: "stat", disabled: !value || !onClick, on: onClick ? { click: onClick } : {} },
    el("b", { className: value ? "warn-t" : "good-t", textContent: Number(value || 0).toLocaleString("en") }), el("span", { className: "dim", textContent: label }));
}
