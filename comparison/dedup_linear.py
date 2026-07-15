#!/usr/bin/env python3
"""Remove duplicate benchmarks from cvc5/linear/, including files that are the
same benchmark up to variable renaming.

Each file is reduced to a canonical form:
  1. parse the assert bodies (declarations are ignored — they are derivable);
  2. normalize: arguments of commutative operators (and, or, +, *, =,
     distinct) are sorted by a name-erased skeleton of the subtree, so
     argument order cannot hide equality;
  3. rename every variable (declared constants and lambda binders alike) to
     v0, v1, ... in order of first occurrence in the normalized tree;
  4. serialize.
Two files with the same canonical form are alpha-equivalent modulo commutative
reordering, i.e. the same benchmark; only the lexicographically first file of
each group is kept. (The pipeline dumps one file per parameter-removal config
per solver call, so back-to-back call-K/call-K+1 files are typically the same
formula with different fresh-variable numbering.)

Files whose name-erased skeletons match but whose canonical forms differ
(sort tie-breaking is heuristic) get a second, exact stage: a backtracking
alpha-equivalence decider that searches for a variable bijection, matching
commutative arguments as multisets and star dimensions up to permutation
(a star's (binder, point) columns may be reordered without changing meaning).
Pairs proven equivalent are deduplicated as well.

Usage: comparison/dedup_linear.py [--dry-run]
"""
import os
import re
import sys
from collections import defaultdict

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from starfree_check import tokenize, parse  # noqa

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(HERE)
CORPUS = os.path.join(REPO, "cvc5", "linear")
RESERVED = {"and", "or", "not", "=>", "=", "<", "<=", ">", ">=", "+", "-",
            "*", "/", "ite", "int.star-contains", "lambda", "true", "false",
            "distinct", "let", "Int", "Bool", "assert"}
COMMUTATIVE = {"and", "or", "+", "*", "=", "distinct"}
NUM = re.compile(r"^-?\d+$")


def is_var(tok):
    return isinstance(tok, str) and tok not in RESERVED and not NUM.match(tok)


def skeleton(n):
    """Name-erased serialization; commutative children sorted."""
    if isinstance(n, str):
        return "V" if is_var(n) else n
    head = n[0] if isinstance(n[0], str) else None
    kids = [skeleton(x) for x in (n[1:] if head else n)]
    if head in COMMUTATIVE:
        kids = sorted(kids)
    return "(" + (head + " " if head else "") + " ".join(kids) + ")"


ASSOCIATIVE = {"and", "or", "+", "*"}


def normalize(n):
    """Flatten associative operators into n-ary nodes and sort commutative
    arguments by their skeleton (stable for ties). Flattening matters:
    generators emit the same conjunct set with different and-nesting."""
    if isinstance(n, str):
        return n
    head = n[0] if isinstance(n[0], str) else None
    if head:
        kids = [normalize(x) for x in n[1:]]
        if head in ASSOCIATIVE:
            flat = []
            for k in kids:
                if isinstance(k, list) and k and k[0] == head:
                    flat.extend(k[1:])
                else:
                    flat.append(k)
            kids = flat
        if head in COMMUTATIVE:
            kids = sorted(kids, key=skeleton)
        return [head] + kids
    return [normalize(x) for x in n]


def rename(n, mapping, out):
    if isinstance(n, str):
        if is_var(n):
            if n not in mapping:
                mapping[n] = f"v{len(mapping)}"
            return mapping[n]
        return n
    return [rename(x, mapping, out) for x in n]


def canonical(path):
    forms = parse(tokenize(open(path).read()))
    asserts = [normalize(f[1]) for f in forms
               if isinstance(f, list) and f and f[0] == "assert"]
    mapping = {}
    renamed = [rename(a, mapping, None) for a in asserts]

    def ser(x):
        return x if isinstance(x, str) else "(" + " ".join(ser(y) for y in x) + ")"
    return "\n".join(ser(a) for a in renamed), "\n".join(skeleton(a) for a in asserts)


# ---------- exact alpha-equivalence (second stage) ----------
# Matchers are functional: they take a variable-bijection dict and return the
# extended dict on success or None on failure, so backtracking is just
# "try with a copy".

def bind(vm, a, b):
    if a in vm:
        return vm if vm[a] == b else None
    if b in vm.values():
        return None
    vm2 = dict(vm)
    vm2[a] = b
    return vm2


def match(a, b, vm):
    if isinstance(a, str) or isinstance(b, str):
        if not (isinstance(a, str) and isinstance(b, str)):
            return None
        if is_var(a) != is_var(b):
            return None
        return bind(vm, a, b) if is_var(a) else (vm if a == b else None)
    if len(a) != len(b):
        return None
    ha = a[0] if isinstance(a[0], str) else None
    hb = b[0] if isinstance(b[0], str) else None
    if ha != hb:
        return None
    if ha == "int.star-contains":
        return match_star(a, b, vm)
    xs = a[1:] if ha else a
    ys = b[1:] if hb else b
    if ha in COMMUTATIVE:
        return match_multiset(xs, ys, vm)
    return match_seq(xs, ys, vm)


def match_seq(xs, ys, vm):
    if len(xs) != len(ys):
        return None
    for x, y in zip(xs, ys):
        vm = match(x, y, vm)
        if vm is None:
            return None
    return vm


def match_multiset(xs, ys, vm):
    if len(xs) != len(ys):
        return None
    if not xs:
        return vm
    x, rest = xs[0], xs[1:]
    for i, y in enumerate(ys):
        vm2 = match(x, y, vm)
        if vm2 is not None:
            vm3 = match_multiset(rest, ys[:i] + ys[i + 1:], vm2)
            if vm3 is not None:
                return vm3
    return None


def freshen_binders(n, counter, active=None):
    """Rename lambda binders apart (b0, b1, ...) so they cannot shadow or
    collide with declared constants during matching."""
    active = active or {}
    if isinstance(n, str):
        return active.get(n, n)
    head = n[0] if isinstance(n[0], str) else None
    if head == "int.star-contains":
        lam = n[1]
        sub = dict(active)
        new_binders = []
        for b in lam[1]:
            fresh = f"b{counter[0]}"
            counter[0] += 1
            sub[b[0]] = fresh
            new_binders.append([fresh, b[1]])
        body = freshen_binders(lam[2], counter, sub)
        pts = [freshen_binders(x, counter, active) for x in n[2:]]
        return ["int.star-contains", ["lambda", new_binders, body]] + pts
    return [freshen_binders(x, counter, active) for x in n]


def match_star(a, b, vm):
    """Star atoms match up to a permutation of their (binder, point) columns."""
    lam_a, pts_a = a[1], a[2:]
    lam_b, pts_b = b[1], b[2:]
    cols_a = list(zip([x[0] for x in lam_a[1]], pts_a))
    cols_b = list(zip([x[0] for x in lam_b[1]], pts_b))
    if len(cols_a) != len(cols_b):
        return None

    def assign(cols_a, cols_b, vm):
        if not cols_a:
            return match(lam_a[2], lam_b[2], vm)
        (ba, pa), rest = cols_a[0], cols_a[1:]
        for i, (bb, pb) in enumerate(cols_b):
            vm2 = match(pa, pb, vm)
            if vm2 is None:
                continue
            vm3 = bind(vm2, ba, bb)
            if vm3 is None:
                continue
            vm4 = assign(rest, cols_b[:i] + cols_b[i + 1:], vm3)
            if vm4 is not None:
                return vm4
        return None

    return assign(cols_a, cols_b, vm)


def alpha_equal(path_a, path_b):
    def trees(path):
        forms = parse(tokenize(open(path).read()))
        counter = [0]
        return [freshen_binders(normalize(f[1]), counter)
                for f in forms
                if isinstance(f, list) and f and f[0] == "assert"]
    ta, tb = trees(path_a), trees(path_b)
    if len(ta) != len(tb):
        return False
    vm = {}
    for x, y in zip(ta, tb):
        vm = match(x, y, vm)
        if vm is None:
            return False
    return True


def main():
    dry = "--dry-run" in sys.argv
    canon_groups = defaultdict(list)
    skel_groups = defaultdict(list)
    for fn in sorted(os.listdir(CORPUS)):
        if not fn.endswith(".smt2"):
            continue
        c, s = canonical(os.path.join(CORPUS, fn))
        canon_groups[c].append(fn)
        skel_groups[s].append(fn)

    removed = 0
    mapping = []  # (duplicate, kept representative, match kind)

    def drop(fn, kept_as, kind):
        nonlocal removed
        removed += 1
        mapping.append((fn, kept_as, kind))
        if not dry:
            os.remove(os.path.join(CORPUS, fn))
            txt = os.path.join(CORPUS, fn[:-5] + ".txt")
            if os.path.exists(txt):
                os.remove(txt)

    # stage 1: identical canonical form
    for group in sorted(canon_groups.values()):
        if len(group) > 1:
            print(f"duplicate group (keeping {group[0]}): {', '.join(group[1:])}")
            for fn in group[1:]:
                drop(fn, group[0], "identical canonical form")

    # stage 2: skeleton-equal survivors get the exact alpha-equivalence check
    canon_kept = {min(g) for g in canon_groups.values()}
    for group in sorted(skel_groups.values()):
        survivors = sorted(f for f in group if f in canon_kept)
        while len(survivors) > 1:
            rep, rest = survivors[0], survivors[1:]
            eq = [f for f in rest
                  if alpha_equal(os.path.join(CORPUS, rep),
                                 os.path.join(CORPUS, f))]
            if eq:
                print(f"duplicate group via alpha-equivalence "
                      f"(keeping {rep}): {', '.join(eq)}")
                for fn in eq:
                    drop(fn, rep, "alpha-equivalence (variable bijection)")
            survivors = [f for f in rest if f not in eq]

    if not dry and mapping:
        import csv
        out = os.path.join(HERE, "duplicates.csv")
        with open(out, "w", newline="") as f:
            w = csv.writer(f)
            w.writerow(["duplicate (removed)", "kept representative", "match kind"])
            for dup, rep, kind in sorted(mapping, key=lambda r: (r[1], r[0])):
                w.writerow([dup, rep, kind])
        print(f"mapping written to {out}")

    kept = len(canon_groups) if dry else \
        len([f for f in os.listdir(CORPUS) if f.endswith('.smt2')])
    print(f"{'would remove' if dry else 'removed'} {removed} duplicates; "
          f"{kept if not dry else '?'} benchmarks remain")


if __name__ == "__main__":
    main()
