#!/usr/bin/env python3
"""Builds the Vestiges wiki from docs/lore.

    python build_wiki.py

Makes two single-file pages in this folder:
  vestiges-wiki.html         ships with the mod, locks on
  vestiges-wiki-author.html  the author's copy: everything open, plus notes and gaps

Sources: content/*.md (written by hand) and ../fragments.md, ../codex-flavour.md,
../mob-origins.md (read directly, so their text can't drift from what was approved).

Content format: see content/README.md. The build stops with an error on a broken link,
a misquoted bible line, a wrong count, or a secret left readable in the shipped file.
"""
import base64
import html
import json
import re
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
LORE = HERE.parent
CONTENT = HERE / "content"
BIBLE = LORE.parents[3] / "Minecraft DataPacks" / "RPG_Lore_Mod" / "bible" / "lore_source.md"

KEY = b"vestiges-wiki"
# Never readable in the shipped file. A progress file or "show everything" reveals them.
SECRETS = ["Kesmar", "Seeding", "Cyclebreaker", "Unmake", "Tulva", "Belaz", "Morst", "Dhars", "Ilvig",
           "Durhos", "Mortre", "Graciel", "Nitox", "Dunther", "Megia", "Hergun", "Scande", "Dreen", "Nemad",
           "Basylas", "Rethen", "Selince", "Kadre", "Voltre", "Mutine", "Larvi", "Tenrys", "Iginor",
           "Iselda", "Coreal", "Fennick", "Ohce", "Embersky", "Trevol", "King Hollan", "King Aldren"]
GROUPS = ["History", "World", "Reference", "Game guide"]
WARNINGS = []


def fail(msg):
    sys.exit("build_wiki: " + msg)


def slug(s):
    return re.sub(r"[^a-z0-9]+", "-", s.lower()).strip("-")


def inline(text):
    t = html.escape(text, quote=False)
    t = re.sub(r"\*\*(.+?)\*\*", r"<strong>\1</strong>", t)
    t = re.sub(r"(?<!\*)\*(?!\s)(.+?)\*", r"<em>\1</em>", t)

    def link(m):
        target, _, label = m.group(1).partition("|")
        LINKS.add(target)
        return '<a href="#%s">%s</a>' % (target, label or target)

    return re.sub(r"\[\[(.+?)\]\]", link, t)


LINKS = set()
IDS = set()
BIBLE_QUOTES = []


# --- locks -----------------------------------------------------------------

def parse_lock(s):
    """ "dragon 6 or found diary-x-1" -> ["d6", "fdiary-x-1"] """
    terms = []
    for part in re.split(r"\s+or\s+", s.strip()):
        m = re.fullmatch(r"dragon (\d+)", part)
        f = re.fullmatch(r"found ([\w-]+)", part)
        if m:
            terms.append("d" + m.group(1))
        elif f:
            terms.append("f" + f.group(1))
        else:
            fail("bad lock: " + s)
    return terms


def scramble(text):
    raw = text.encode("utf-8")
    return base64.b64encode(bytes(b ^ KEY[i % len(KEY)] for i, b in enumerate(raw))).decode("ascii")


def locked(inner, lock, anchor=None, kind="block"):
    terms = lock if isinstance(lock, list) else parse_lock(lock)
    a = ' id="%s"' % anchor if anchor else ""
    return '<div class="lk lk-%s"%s data-l="%s" data-e="%s"></div>' % (kind, a, " ".join(terms), scramble(inner))


# --- hand-written content --------------------------------------------------

class Page:
    def __init__(self, pid, title, group, flag=""):
        self.id, self.title, self.group, self.flag = pid, title, group, flag
        self.blocks = []


GAPS = []


def render_lines(lines, author):
    """Plain block markup: paragraphs, lists, tables, bible quotes, gaps."""
    out, i = [], 0
    while i < len(lines):
        ln = lines[i].rstrip()
        if not ln.strip():
            i += 1
        elif ln.startswith("## "):
            out.append("<h2>%s</h2>" % inline(ln[3:]))
            i += 1
        elif ln.startswith("@bible "):
            q = ln[7:]
            BIBLE_QUOTES.append(q)
            out.append('<blockquote class="bible">%s</blockquote>' % inline(q))
            i += 1
        elif ln.startswith("@gap "):
            out.append('<p class="gap">Not written yet%s</p>' % (": " + inline(ln[5:]) if author else "."))
            GAPS.append(ln[5:])
            i += 1
        elif ln.startswith("@gaplist"):
            out.append("@@GAPLIST@@")
            i += 1
        elif ln.startswith("|"):
            rows = []
            while i < len(lines) and lines[i].startswith("|"):
                rows.append(split_row(lines[i]))
                i += 1
            head, body = rows[0], rows[2:]
            t = "".join("<th>%s</th>" % inline(c) for c in head)
            b = "".join("<tr>%s</tr>" % "".join("<td>%s</td>" % inline(c) for c in r) for r in body)
            out.append('<div class="tw"><table><thead><tr>%s</tr></thead><tbody>%s</tbody></table></div>' % (t, b))
        elif ln.startswith("- "):
            items = []
            while i < len(lines) and lines[i].startswith("- "):
                items.append("<li>%s</li>" % inline(lines[i][2:].rstrip()))
                i += 1
            out.append("<ul>%s</ul>" % "".join(items))
        elif re.match(r"\d+\. ", ln):
            items = []
            while i < len(lines) and re.match(r"\d+\. ", lines[i]):
                items.append("<li>%s</li>" % inline(re.sub(r"^\d+\. ", "", lines[i].rstrip())))
                i += 1
            out.append("<ol>%s</ol>" % "".join(items))
        else:
            out.append("<p>%s</p>" % inline(ln))
            i += 1
    return "".join(out)


def split_row(line):
    # A link's "|" must not split a cell.
    cells, cur, depth = [], "", 0
    s = line.strip().strip("|")
    k = 0
    while k < len(s):
        if s.startswith("[[", k):
            depth += 1
        elif s.startswith("]]", k):
            depth -= 1
        if s[k] == "|" and depth == 0:
            cells.append(cur.strip())
            cur = ""
        else:
            cur += s[k]
        k += 1
    cells.append(cur.strip())
    return cells


def render_blocks(lines, author):
    """Handles @entry / @lock / @author around plain markup."""
    out, buf = [], []
    entry = None  # (id, title, lock, lines)

    def flush_buf():
        if buf:
            out.append(render_lines(buf, author))
            buf.clear()

    def close_entry():
        nonlocal entry
        if entry:
            eid, title, lock, body = entry
            IDS.add(eid)
            inner = "<h3>%s</h3>%s" % (inline(title), render_blocks(body, author))
            if lock:
                out.append(locked('<section class="entry" data-t="%s">%s</section>' % (html.escape(title, quote=True), inner), lock, eid, "entry"))
            else:
                out.append('<section class="entry" id="%s" data-t="%s">%s</section>' % (eid, html.escape(title, quote=True), inner))
            entry = None

    i = 0
    while i < len(lines):
        ln = lines[i]
        if ln.startswith("@entry "):
            flush_buf()
            close_entry()
            parts = [p.strip() for p in ln[7:].split("|")]
            eid, title = parts[0], parts[1]
            entry = (eid, title, parts[2] if len(parts) > 2 else None, [])
            i += 1
            continue
        if ln.startswith("## ") and entry:
            close_entry()
            buf.append(ln)
            i += 1
            continue
        target = entry[3] if entry else None
        if ln.startswith("@lock "):
            j = i + 1
            body = []
            while j < len(lines):
                if lines[j].startswith("@lock "):
                    fail("nested @lock: " + lines[j])
                if lines[j].startswith("@endlock"):
                    break
                body.append(lines[j])
                j += 1
            else:
                fail("@lock without @endlock: " + ln)
            if entry:
                target.extend(lines[i:j + 1])
            else:
                flush_buf()
                out.append(locked(render_blocks(body, author), ln[6:]))
            i = j + 1
            continue
        if ln.startswith("@author"):
            j = i + 1
            body = []
            while not lines[j].startswith("@endauthor"):
                body.append(lines[j])
                j += 1
            if author:
                note = '<div class="anote"><span class="alabel">Author note</span>%s</div>' % render_lines(body, author)
                (target.append("@raw " + note) if entry else (flush_buf(), out.append(note)))
            i = j + 1
            continue
        if ln.startswith("@raw "):
            flush_buf()
            out.append(ln[5:])
            i += 1
            continue
        (target if entry else buf).append(ln)
        i += 1
    flush_buf()
    close_entry()
    return "".join(out)


def load_pages(author):
    pages = []
    names = []
    for f in sorted(CONTENT.glob("*.md")):
        if f.name.lower() == "readme.md":
            continue
        for raw in f.read_text(encoding="utf-8").splitlines():
            if raw.startswith("@page "):
                parts = [p.strip() for p in raw[6:].split("|")]
                pages.append(Page(parts[0], parts[1], parts[2], parts[3] if len(parts) > 3 else ""))
            elif raw.startswith("@name "):
                parts = [p.strip() for p in raw[6:].split("|")]
                names.append(parts)
            elif pages:
                pages[-1].blocks.append(raw)
    return pages, names


# --- pages built from the approved sources ---------------------------------

FRAG_LABELS = {
    "Primacy": "primacy", "Bright Path": "bright-path", "The Promise": "promise", "Vitera": "vitera", "Unknown": "unknown",
}
DIARY = {
    "Overgrown": ("overgrown", "The Brewers", "Wenna, apprentice brewer"),
    "Frozen": ("frozen", "The Frostwardens", "Ilse, a young Frostwarden"),
    "Sunken": ("sunken", "The Tidefolk", "Corin, guardian-keeper"),
    "Deep Dark": ("deep", "The Deep Ones", "Sael, a gatewright"),
    "Nether": ("nether", "The Fortress-builders", "Varo, fortress mason"),
    "End": ("end", "The Ascended", "Ione, sky-builder"),
    "Buried": ("buried", "The Kilnfolk", "Tobias, a kiln-master's son"),
    "Trial": ("trial", "The First Heroes", "Aric, one of the seven"),
    "Illager Keep": ("keep", "The Hollow Kings", "Marek, royal scribe"),
}
FRAG_IDS = []


def frag_line(line):
    m = re.match(r"\d+\.\s+(?:\*\*(.+?):\*\*\s+)?(.*)$", line)
    title, rest = m.group(1), m.group(2).strip()
    q = re.match(r'^"(.*)"(?:\s*\((.*)\))?$', rest)
    if not q:
        fail("fragment not parsed: " + line)
    text, note = q.group(1), q.group(2)
    h = ""
    if title:
        h += '<span class="ftitle">%s</span>' % html.escape(title, quote=False)
    h += '<span class="ftext">"%s"</span>' % html.escape(text, quote=False)
    if note:
        h += '<span class="fnote">%s</span>' % html.escape(note, quote=False)
    return '<div class="frag">%s</div>' % h


def build_fragments():
    src = (LORE / "fragments.md").read_text(encoding="utf-8").splitlines()
    shrines, diaries, section, fid, n, count = [], [], None, None, 0, 0
    current = None
    for ln in src:
        if ln.startswith("### "):
            name = ln[4:]
            n = 0
            key = next((k for k in DIARY if name.startswith(k + " ")), None)
            if key:
                did, people, writer = DIARY[key]
                section, fid = "diary", "diary-" + did
                all_pages = " or ".join("found %s-%d" % (fid, p) for p in range(1, 9))
                diaries.append('<h2 id="diary-%s">%s</h2>' % (did, key))
                diaries.append(locked('<p class="fwriter">%s · %s</p>' % (people, writer), all_pages))
            else:
                key = next((k for k in FRAG_LABELS if name.startswith(k)), None)
                if not key:
                    fail("unknown fragment group: " + name)
                section, fid = "shrine", "shrine-" + FRAG_LABELS[key]
                title = "The Promise" if key == "The Promise" else ("The " + key if key in ("Primacy", "Bright Path", "Vitera") else "Unknown")
                shrines.append("<h2>%s</h2>" % title)
                if key == "Unknown":
                    shrines.append('<p class="fwriter">Written in enchanting glyphs.</p>')
        elif re.match(r"\d+\.\s", ln) and section:
            n += 1
            count += 1
            fragment_id = "%s-%d" % (fid, n)
            FRAG_IDS.append(fragment_id)
            (shrines if section == "shrine" else diaries).append(locked(frag_line(ln), "found " + fragment_id, kind="frag"))
    if count != 112:
        fail("expected 112 fragments, found %d" % count)
    return "".join(shrines), "".join(diaries)


# What approved lore says about each structure, shown when its chapter opens.
STRUCTURE_LORE = {
    "Stronghold": ("dragon 1", "Left by the [[pe1|Vault-builders]]."),
    "Desert Pyramid": ("dragon 2", "A tomb of the [[pe2|Kilnfolk]]."),
    "Igloo": ("dragon 8", "Linked to the [[pe8|Frostwardens]]."),
    "Mineshaft": ("dragon 7", "The [[pe7|Deepdelvers]] dug the first ones."),
    "Woodland Mansion": ("dragon 12", "The [[pe12|Hollow Kings]]' halls had rooms like these."),
    "Ocean Monument": ("dragon 16", "Built by the [[pe16|Tidefolk]]."),
    "Nether Fortress": ("dragon 17", "Built by the [[pe17|Fortress-builders]]."),
    "Ancient City": ("dragon 18", "Built by the [[pe18|Deep Ones]], around their own gate. Ancient cities belong to a previous cycle."),
    "Trial Chambers": ("dragon 18", "The makers' tests that Heroes through the ages had to complete, just like you."),
    "The Vestige": ("dragon 1", "See [[vestige|The Vestige]]."),
    "Dungeon Portal (the Gate)": (None, "Leads to [[archive|the Archive]]."),
}


def codex_table(heading):
    out, on = [], False
    for ln in (LORE / "codex-flavour.md").read_text(encoding="utf-8").splitlines():
        if ln.startswith("## "):
            on = ln.startswith("## " + heading)
            continue
        if on and ln.startswith("| ") and not ln.startswith("| Entry") and not ln.startswith("|---"):
            c = [x.strip() for x in ln.strip().strip("|").split("|")]
            out.append((c[0], c[1], c[2]))
    return out


def build_structures():
    rows = codex_table("Structures")
    if len(rows) != 32:
        fail("expected 32 structures, found %d" % len(rows))
    h = []
    for name, l1, l2 in rows:
        sid = "st-" + slug(name)
        IDS.add(sid)
        inner = "<h3>%s</h3><p>%s</p><p>%s</p>" % (html.escape(name), html.escape(l1, quote=False), html.escape(l2, quote=False))
        if name in STRUCTURE_LORE:
            lock, text = STRUCTURE_LORE[name]
            inner += locked("<p>%s</p>" % inline(text), lock) if lock else "<p>%s</p>" % inline(text)
        h.append('<section class="entry" id="%s" data-t="%s">%s</section>' % (sid, html.escape(name, quote=True), inner))
    return "".join(h)


def clean_seen(s):
    s = re.sub(r"\s*\((?:check in game|New in 26\.3[^)]*|new in 26\.3)\)\.?", "", s).strip()
    s = s.replace("; once per kill in the Vestiges design", "")
    return s[:1].upper() + s[1:]


def mob_decisions():
    """The author's group decisions and open points from mob-origins.md. Never shipped."""
    text = (LORE / "mob-origins.md").read_text(encoding="utf-8")
    return render_lines(text[text.index("## Group decisions"):].splitlines(), True)


def build_mobs(author):
    """Returns [(page id, title, html)] from mob-origins.md, with the Codex lines beside them."""
    codex = {r[0]: (r[1], r[2]) for r in codex_table("Mobs")}
    pages, cur, mob = [], None, None
    count = 0

    def add(name, seen, levels, ties):
        nonlocal count
        count += 1
        mid = "mob-" + slug(name)
        IDS.add(mid)
        c1, c2 = codex.get(name, ("", ""))
        h = ["<h3>%s</h3>" % html.escape(name)]
        if seen:
            h.append('<p class="seen">%s</p>' % html.escape(clean_seen(seen), quote=False))
        if c1:
            h.append('<div class="lv lv-c"><span class="lvl">In the Codex</span><p>%s</p>%s</div>' % (
                html.escape(c1, quote=False), "<p>%s</p>" % html.escape(c2, quote=False) if c2 else ""))
        labels = {"L1": "What people say", "L2": "A hint", "L3": "The truth"}
        for k in ("L1", "L2"):
            if levels.get(k):
                h.append('<div class="lv lv-%s"><span class="lvl">%s</span><p>%s</p></div>' % (k, labels[k], levels[k]))
        if levels.get("L3"):
            h.append(locked('<div class="lv lv-L3"><span class="lvl">%s</span><p>%s</p></div>' % (labels["L3"], levels["L3"]), "dragon 24", kind="truth"))
        if ties and author:
            h.append('<div class="anote"><span class="alabel">Ties</span><p>%s</p></div>' % html.escape(ties, quote=False))
        cur[2].append('<section class="entry" id="%s" data-t="%s">%s</section>' % (mid, html.escape(name, quote=True), "".join(h)))

    def flush():
        nonlocal mob
        if mob:
            add(mob["name"], mob.get("seen", ""), mob["levels"], mob.get("ties", ""))
            mob = None

    def level_text(v):
        v = v.strip()
        q = re.match(r'^"(.*?)"(?:\s*\(.*\))?$', v)
        if q:
            return html.escape(q.group(1), quote=False)
        return None if v.startswith("(") else html.escape(v, quote=False)

    for ln in (LORE / "mob-origins.md").read_text(encoding="utf-8").splitlines():
        if ln.startswith("## "):
            flush()
            title = ln[3:].strip()
            if title.startswith(("Group decisions", "Pending", "The three levels", "Cards")):
                cur = None if not title.startswith("Cards") else cur
                if title.startswith("Cards"):
                    cur = ["mobs-kin", "The cursed dead", []]
                    pages.append(cur)
                continue
            title = re.sub(r"\s*\(mostly.*$", "", title)
            if title.startswith("Zombie kin"):
                continue  # same page as the two cards above it
            cur = ["mobs-" + slug(title.split(":")[0].split(",")[0]), title.split(":")[0], []]
            pages.append(cur)
        elif cur is None:
            continue
        elif ln.startswith("### "):
            flush()
            mob = {"name": ln[4:].strip(), "levels": {}}
        elif mob is not None:
            m = re.match(r"- \*\*(.+?):\*\*\s*(.*)$", ln)
            if m:
                k, v = m.group(1), m.group(2)
                if k in ("Seen", "Where / when"):
                    mob["seen"] = v
                elif k.startswith("L1"):
                    mob["levels"]["L1"] = level_text(v)
                elif k.startswith("L2"):
                    mob["levels"]["L2"] = level_text(v)
                elif k.startswith("L3"):
                    mob["levels"]["L3"] = level_text(v)
                elif k == "Ties":
                    mob["ties"] = v
        elif ln.startswith("| ") and not ln.startswith("| Mob") and not ln.startswith("|---"):
            c = [x.strip() for x in ln.strip().strip("|").split("|")]
            name, lv = c[0], {}
            if c[1].startswith("("):
                add(name, "", {"L1": 'Follows the <a href="#mob-frog">Frog</a>.'}, "")
                continue
            for k, v in zip(("L1", "L2", "L3"), c[1:4]):
                if v:
                    lv[k] = html.escape(v, quote=False)
            add(name, "", lv, "")
    flush()
    if count != 88:
        fail("expected 88 mobs (87 with texts, plus Tadpole), found %d" % count)
    return [(p[0], p[1], "".join(p[2])) for p in pages]


# --- names index -----------------------------------------------------------

def build_names(names):
    rows = []
    for parts in sorted(names, key=lambda p: re.sub(r"^The ", "", p[0]).lower()):
        name, kind, line, target = parts[0], parts[1], parts[2], parts[3]
        lock = parts[4] if len(parts) > 4 else None
        LINKS.add(target)
        row = '<div class="nm"><a href="#%s">%s</a><span class="nk">%s</span><span class="nl">%s</span></div>' % (
            target, html.escape(name, quote=False), html.escape(kind, quote=False), html.escape(line, quote=False))
        rows.append(locked(row, lock, kind="name") if lock else row)
    return '<div class="names">%s</div>' % "".join(rows)


# --- output ----------------------------------------------------------------

def assemble(author):
    GAPS.clear()
    FRAG_IDS.clear()
    pages, names = load_pages(author)
    rendered = []
    home = ('<section class="page" id="home" data-title="Home">%s</section>' % home_html(author))
    IDS.add("home")

    shrines, diaries = build_fragments()
    extra = {
        "structures": ("Structures", "World", '<p class="lead">Every place you can find in the world, with what the Codex says about it and what is known of who built it.</p>' + build_structures()),
        "frag-shrines": ("Shrine fragments", "Reference", '<p class="lead">The 40 texts from ruined shrines. Each one appears here once you have found it.</p>' + shrines),
        "frag-diaries": ("Dungeon diaries", "Reference", '<p class="lead">Nine diaries of 8 pages, each replaying one past cycle. Pages appear here once you have found them.</p>' + diaries),
    }
    if author:
        extra["mob-decisions"] = ("Mob decisions", "Reference", mob_decisions())
    mob_pages = build_mobs(author)
    order = []
    for p in pages:
        if p.flag == "author" and not author:
            continue
        order.append(p)

    nav = {g: [] for g in GROUPS}
    bodies = []
    for p in order:
        IDS.add(p.id)
        body = render_blocks(p.blocks, author) if p.flag != "names" else render_blocks(p.blocks, author) + build_names(names)
        bodies.append((p, body))
    for p, body in bodies:
        if p.flag == "author" and "@@GAPLIST@@" in body:
            items = "".join("<li>%s</li>" % inline(g) for g in GAPS)
            body = body.replace("@@GAPLIST@@", "<ol>%s</ol>" % items)
        nav[p.group].append((p.id, p.title))
        rendered.append('<section class="page" id="%s" data-title="%s" hidden><h1>%s</h1>%s</section>' % (
            p.id, html.escape(p.title, quote=True), html.escape(p.title), body))
    for pid, (title, group, body) in extra.items():
        IDS.add(pid)
        nav[group].append((pid, title))
        rendered.append('<section class="page" id="%s" data-title="%s" hidden><h1>%s</h1>%s</section>' % (
            pid, html.escape(title, quote=True), html.escape(title), body))
    for pid, title, body in mob_pages:
        IDS.add(pid)
        nav["World"].append((pid, "Mobs: " + title))
        rendered.append('<section class="page" id="%s" data-title="%s" hidden><h1>%s</h1>%s</section>' % (
            pid, html.escape(title, quote=True), html.escape(title), body))

    # Locked entries also carry ids that links may target.
    missing = sorted(t for t in LINKS if t not in IDS)
    if missing:
        fail("broken links: " + ", ".join(missing))

    sidebar = []
    for g in GROUPS:
        if not nav[g]:
            continue
        sidebar.append('<div class="navg"><div class="navh">%s</div>%s</div>' % (
            g, "".join('<a href="#%s" data-nav="%s">%s</a>' % (i, i, html.escape(t, quote=False)) for i, t in nav[g])))

    tpl = (HERE / "template.html").read_text(encoding="utf-8")
    return (tpl.replace("/*AUTHOR*/false", "true" if author else "false")
            .replace("/*KEY*/", KEY.decode())
            .replace("<!--NAV-->", "".join(sidebar))
            .replace("<!--PAGES-->", home + "".join(rendered)))


def home_html(author):
    intro = ('<p class="anote"><span class="alabel">Your copy</span>Everything is open here. A small tag shows when each locked part opens for a player. '
             'See <a href="#notes-author">Author notes</a> and <a href="#gaps">Gaps to fill</a>.</p>') if author else ""
    return ('<h1>Vestiges wiki</h1>%s'
            '<p class="lead">Everything known about this world, in one place. It fills up as you play: '
            'parts of the history open only when you have earned them.</p>'
            '<div class="doors">'
            '<a href="#names"><strong>Names index</strong><span>Who is this? Every name in one line.</span></a>'
            '<a href="#timeline"><strong>Timeline</strong><span>The 24 cycles, told backwards.</span></a>'
            '<a href="#heroes"><strong>Heroes</strong><span>Famous people of this age.</span></a>'
            '<a href="#places"><strong>Places</strong><span>Trenec, Dilecta and what lies beyond.</span></a>'
            '<a href="#faiths"><strong>Faiths</strong><span>Three faiths, and the people they hunt.</span></a>'
            '<a href="#bosses"><strong>Bosses and mobs</strong><span>What people say, and what they hide.</span></a>'
            '<a href="#structures"><strong>Structures</strong><span>Who built what you find.</span></a>'
            '<a href="#guide"><strong>Game guide</strong><span>How the game works.</span></a>'
            '</div>') % intro


def main():
    bible = BIBLE.read_text(encoding="utf-8") if BIBLE.exists() else None
    player = assemble(False)
    author = assemble(True)

    if bible is None:
        print("note: bible not found, quotes not checked")
    else:
        bad = [q for q in set(BIBLE_QUOTES) if q not in bible]
        if bad:
            fail("not an exact bible quote:\n  " + "\n  ".join(bad))

    # Secrets must not be readable in the shipped file.
    leaks = [s for s in SECRETS if s in player]
    if leaks:
        fail("readable in the shipped file: " + ", ".join(leaks))
    if "Author note" in player or "notes-author" in player:
        fail("author notes leaked into the shipped file")

    (HERE / "vestiges-wiki.html").write_text(player, encoding="utf-8")
    (HERE / "vestiges-wiki-author.html").write_text(author, encoding="utf-8")
    (HERE / "fragment-ids.json").write_text(json.dumps(FRAG_IDS, indent=0), encoding="utf-8")
    print("built: %d KB shipped, %d KB author, %d links, %d gaps" % (len(player) // 1024, len(author) // 1024, len(LINKS), len(GAPS)))


if __name__ == "__main__":
    main()
