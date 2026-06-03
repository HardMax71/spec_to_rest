import glob
import os
import re
import sqlite3
import subprocess
import sys
import tempfile
from collections import defaultdict

heaps_dir = sys.argv[1]
root_dir = sys.argv[2]
out_path = sys.argv[3]
isa_home = os.environ.get("ISABELLE_HOME", "")
home = os.environ.get("HOME", "")

# Sessions to profile are read from the project's ROOT file(s), not hardcoded:
# any session declared under root_dir is picked up, so adding or renaming a
# session needs no change here. Sessions outside the project (HOL, AFP, other
# local experiments that happen to share the heaps dir) are skipped.
session_re = re.compile(r'^\s*session\s+"?([A-Za-z0-9_.]+)"?')
project_sessions = set()
for root in glob.glob(os.path.join(root_dir, "**", "ROOT"), recursive=True):
    try:
        with open(root, "r", encoding="utf-8", errors="replace") as fh:
            for ln in fh:
                m = session_re.match(ln)
                if m:
                    project_sessions.add(m.group(1))
    except OSError:
        pass


def blob_of(db):
    try:
        con = sqlite3.connect(f"file:{db}?mode=ro", uri=True)
    except sqlite3.Error:
        return None
    try:
        row = con.execute(
            "SELECT command_timings FROM isabelle_session_info LIMIT 1"
        ).fetchone()
    except sqlite3.Error:
        return None
    finally:
        con.close()
    return row[0] if row and row[0] is not None else None


def decompress(blob):
    with tempfile.NamedTemporaryFile(delete=False, suffix=".blob") as f:
        f.write(blob)
        p = f.name
    try:
        r = subprocess.run(
            ["zstd", "-dq", "-f", p, "-o", p + ".out"], capture_output=True
        )
        if r.returncode == 0:
            with open(p + ".out", "rb") as fh:
                return fh.read()
    except OSError:
        pass
    return blob


def parse(data):
    text = data.decode("utf-8", errors="replace")
    entries = []
    for chunk in text.split("\x05"):
        if not chunk:
            continue
        f = {}
        for kv in chunk.split("\x06"):
            if "=" in kv:
                k, v = kv.split("=", 1)
                f[k] = v
        if "elapsed" in f and "file" in f:
            try:
                entries.append(
                    (float(f["elapsed"]), f.get("name", "?"), f["file"],
                     int(f.get("offset", "0")))
                )
            except ValueError:
                pass
    return entries


_src = {}


def line_of(path, off):
    # Isabelle command offsets count symbols (`\<dots>` is one symbol, not its
    # byte length), so a byte slice under-reports the line by ~15. Walk the
    # source consuming `off` symbols, counting newlines.
    exp = path.replace("~~", isa_home).replace("~/", home + "/")
    if exp not in _src:
        try:
            with open(exp, "r", encoding="utf-8", errors="replace") as fh:
                _src[exp] = fh.read()
        except OSError:
            _src[exp] = None
    s = _src[exp]
    if s is None:
        return -1
    i = sym = 0
    line = 1
    n = len(s)
    while i < n and sym < off:
        if s[i] == "\\" and i + 1 < n and s[i + 1] == "<":
            j = s.find(">", i)
            i = (j + 1) if j != -1 else (i + 1)
        else:
            if s[i] == "\n":
                line += 1
            i += 1
        sym += 1
    return line


def where(path, off):
    ln = line_of(path, off)
    fn = path.split("/")[-1]
    return f"{fn}:{ln}" if ln > 0 else f"{fn}:?(off={off})"


sessions = []
for db in sorted(glob.glob(os.path.join(heaps_dir, "*", "log", "*.db"))):
    name = os.path.basename(db)[:-3]
    if project_sessions and name not in project_sessions:
        continue
    blob = blob_of(db)
    if blob is None:
        continue
    es = parse(decompress(blob))
    if es:
        sessions.append((name, es))

out = []
if not sessions:
    out.append("No session timing data found under " + heaps_dir)
else:
    alle = [(e[0], name, e[1], e[2], e[3]) for name, es in sessions for e in es]
    grand = sum(x[0] for x in alle)
    out.append("# Isabelle per-command timing trace")
    out.append("Sessions profiled: " + ", ".join(n for n, _ in sessions))
    out.append(
        f"Grand total (commands >= build_timing_threshold): {grand:.1f}s "
        f"across {len(alle)} commands"
    )
    out.append("")
    out.append("=== OVERALL top 40 across all sessions (by elapsed) ===")
    for elapsed, sess, nm, path, off in sorted(alle, key=lambda x: -x[0])[:40]:
        out.append(f"  {elapsed:8.2f}s  {sess:22s}  {nm:14s}  {where(path, off)}")
    out.append("")
    for name, es in sorted(sessions, key=lambda x: -sum(e[0] for e in x[1])):
        tot = sum(e[0] for e in es)
        out.append(f"=== {name}: total {tot:.1f}s across {len(es)} commands ===")
        out.append("  -- top 20 --")
        for elapsed, nm, path, off in sorted(es, key=lambda x: -x[0])[:20]:
            out.append(f"  {elapsed:8.2f}s  {nm:14s}  {where(path, off)}")
        byf = defaultdict(float)
        for e in es:
            byf[e[2].split("/")[-1]] += e[0]
        out.append("  -- cumulative by file --")
        for fn, t in sorted(byf.items(), key=lambda x: -x[1]):
            out.append(f"  {t:8.2f}s  {fn}")
        out.append("")

with open(out_path, "w") as fh:
    fh.write("\n".join(out) + "\n")
