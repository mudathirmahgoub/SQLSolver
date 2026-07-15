#!/usr/bin/env python3
"""Build the linear benchmark corpus from the pipeline's dump output.

A dump qualifies iff its WHOLE formula is linear integer arithmetic plus
stars with well-formed bodies:
  - no product of two variable terms and no division, anywhere (inside or
    outside star bodies);
  - no uninterpreted-function application anywhere;
  - no int.star-contains nested inside another star's lambda body.
Star-free pure-LIA dumps qualify trivially.

Qualifying dumps are copied into the flat corpus directory cvc5/linear/ as
<suite>-<basename>.smt2 and the suite directories' dump files are pruned:
cvc5/linear/ is the single benchmark corpus.

Usage:
  comparison/filter_linear.py                build corpus from suite dumps
                                             (prunes suite dump files)
  comparison/filter_linear.py --no-prune     same, keep suite dumps
  comparison/filter_linear.py --refilter     re-apply the (possibly stricter)
                                             criterion to the files already in
                                             cvc5/linear/, deleting failures
"""
import os
import re
import shutil
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from starfree_check import tokenize, parse  # noqa

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(HERE)
OUTDIR = os.path.join(REPO, "cvc5", "linear")
SUITES = ["calcite", "spark", "tpc-c", "tpc-h"]
OPS = {"and", "or", "not", "=>", "=", "<", "<=", ">", ">=", "+", "-", "*", "/",
       "ite", "int.star-contains", "lambda", "true", "false", "distinct", "let"}
NUM = re.compile(r"^-?\d+$")


def has_var(n):
    if isinstance(n, str):
        return not NUM.match(n) and n not in OPS
    return any(has_var(x) for x in n)


def node_ok(n, in_star_body):
    """Whole-formula check: linear arithmetic only, no UF application anywhere,
    stars allowed except nested inside a star body."""
    if isinstance(n, str):
        return True
    head = n[0] if n and isinstance(n[0], str) else None
    if head == "int.star-contains":
        if in_star_body:
            return False  # nested star
        lam = n[1]
        return node_ok(lam[2], True) and all(node_ok(x, False) for x in n[2:])
    if head == "*" and sum(1 for a in n[1:] if has_var(a)) >= 2:
        return False  # non-linear product
    if head == "/":
        return False
    if (head is not None and head not in OPS and not NUM.match(head)
            and len(n) > 1):
        return False  # uninterpreted function application
    return all(node_ok(x, in_star_body) for x in n)


def file_ok(path):
    forms = parse(tokenize(open(path).read()))
    return all(node_ok(form[1], False) for form in forms
               if isinstance(form, list) and form and form[0] == "assert")


def refilter():
    kept = dropped = 0
    for fn in sorted(os.listdir(OUTDIR)):
        if not fn.endswith(".smt2"):
            continue
        path = os.path.join(OUTDIR, fn)
        if file_ok(path):
            kept += 1
        else:
            dropped += 1
            os.remove(path)
            txt = path[:-5] + ".txt"
            if os.path.exists(txt):
                os.remove(txt)
    print(f"linear corpus refiltered: {kept} kept, {dropped} removed")


def build(prune):
    shutil.rmtree(OUTDIR, ignore_errors=True)
    os.makedirs(OUTDIR)
    kept = dropped = 0
    for suite in SUITES:
        d = os.path.join(REPO, "cvc5", suite)
        if not os.path.isdir(d):
            continue
        for fn in sorted(os.listdir(d)):
            if not (fn.endswith(".smt2") and fn.startswith("query")):
                continue
            path = os.path.join(d, fn)
            if file_ok(path):
                shutil.copy(path, os.path.join(OUTDIR, f"{suite}-{fn}"))
                kept += 1
            else:
                dropped += 1
            if prune:
                os.remove(path)
                txt = path[:-5] + ".txt"
                if os.path.exists(txt):
                    os.remove(txt)
    print(f"linear corpus: {kept} benchmarks -> {OUTDIR}"
          f" ({dropped} out-of-fragment dumps excluded"
          f"{', suite dirs pruned' if prune else ''})")


if __name__ == "__main__":
    if "--refilter" in sys.argv:
        refilter()
    else:
        build(prune="--no-prune" not in sys.argv)
