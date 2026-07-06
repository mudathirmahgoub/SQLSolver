#!/usr/bin/env python3
"""Join SQLSolver's per-file verdict (each dir's sqlsolver_results.csv) with the
cvc5 output files (per-query .txt) for cvc5/calcite, cvc5/spark, cvc5/tpc-h.
Writes one CSV, files prefixed by the directory name."""
import csv, os, glob
from collections import Counter

BASE = "/Users/mahgoubyahia/SQLSolver"
DIRS = ["calcite", "spark", "tpc-h"]
OUT = os.path.join(BASE, "sqlsolver_vs_cvc5.csv")
SOLVED = {"sat", "unsat"}


def classify_cvc5(text):
    """Normalize a cvc5 output .txt into a single verdict."""
    low = text.lower()
    first = next((ln.strip().lower() for ln in text.splitlines() if ln.strip()), "")
    if first in ("sat", "unsat", "unknown"):
        return first
    if "interrupted by timeout" in low:
        return "timeout"
    if ("fatal failure" in low or "internal error" in low
            or "segfault" in low or "terminated by the c++ runtime" in low):
        return "crash"
    if "parse error" in low or first.startswith("(error"):
        return "parse_error"
    if not text.strip():
        return "missing"
    return "other"


rows = []
for d in DIRS:
    dpath = os.path.join(BASE, "cvc5", d)
    # SQLSolver verdicts, keyed by base name (strip .smt2)
    sql = {}
    with open(os.path.join(dpath, "sqlsolver_results.csv"), newline="") as f:
        for r in csv.DictReader(f):
            fn = (r.get("sqlsolver file") or "").strip()
            if not fn:
                continue
            base = fn[:-5] if fn.endswith(".smt2") else fn
            sql[base] = ((r.get("sqlsolver result") or "").strip(),
                         (r.get("sql duration") or "").strip())
    # cvc5 outputs, keyed by base name (strip .txt), skipping aggregate logs
    cvc = {}
    for txt in glob.glob(os.path.join(dpath, "*.txt")):
        b = os.path.basename(txt)
        if b in ("output.txt", "error.txt"):
            continue
        cvc[b[:-4]] = classify_cvc5(open(txt, errors="replace").read())

    for base in sorted(set(sql) | set(cvc)):
        sres, sdur = sql.get(base, ("missing", ""))
        cres = cvc.get(base, "missing")
        sl, cl = sres.strip().lower(), cres.strip().lower()
        if sl in SOLVED and cl in SOLVED:
            agree = "yes" if sl == cl else "NO"
        else:
            agree = "n/a"
        rows.append([f"{d}/{base}.smt2", sres, sdur, cres, agree])

with open(OUT, "w", newline="") as f:
    w = csv.writer(f)
    w.writerow(["file", "sqlsolver result", "sqlsolver duration", "cvc5 result", "agree"])
    w.writerows(rows)

print(f"wrote {OUT}  ({len(rows)} rows)")
# summary
for d in DIRS:
    sub = [r for r in rows if r[0].startswith(d + "/")]
    sq = Counter(r[1].strip().lower() for r in sub)
    cv = Counter(r[3].strip().lower() for r in sub)
    ag = Counter(r[4] for r in sub)
    print(f"\n[{d}]  n={len(sub)}")
    print(f"  sqlsolver: {dict(sq)}")
    print(f"  cvc5:      {dict(cv)}")
    print(f"  agree:     {dict(ag)}")
    dis = [r[0] for r in sub if r[4] == "NO"]
    if dis:
        print(f"  DISAGREE ({len(dis)}): {', '.join(x.split('/')[1] for x in dis)}")
