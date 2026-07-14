# cvc5 liastar `int.star-contains` bugs

Minimal reproductions, all over nonnegative integers. Run with the liastar cvc5
(`~/cvc5/liastar/build/bin/cvc5`). Corpus-wide numbers below refer to the 360
fresh dumps of 2026-07-06 (see `../REPORT.md`).

## Bug 1 — spurious SAT when cvc5 is built without Normaliz

The `int.star-contains` decision procedure (`src/theory/arith/liastar/liastar_extension.cpp`)
is wrapped in `#ifdef CVC5_USE_NORMALIZ`. With `USE_NORMALIZ=OFF` it is compiled out, yet
`src/theory/arith/theory_arith.cpp:97` unconditionally runs
`setIrrelevantKind(STAR_CONTAINS)` — so the operator is unconstrained and the SAT solver
sets it `true` freely. Every membership query then returns `sat`, e.g. these three (all
provably UNSAT):

    min1_single_dim.smt2        # (1)   in {a|a=2}*        closure {0,2,4,...}
    min2_equal_components.smt2  # (1,0) in {(a,b)|a=b}*    every sum has comp1=comp2
    min3_empty_relation.smt2    # (1,0) in {(a,b)|false}*  closure {(0,0)}

**Fix:** build with Normaliz — `./configure.sh production --normaliz` (dep already in
`build/deps/`; then `cmake -DUSE_NORMALIZ=ON build && ninja`).

## Bug 2 — Normaliz path crashes — in-fragment crashes **FIXED in `a18e71578`**

On earlier builds (`USE_NORMALIZ=ON`), the degenerate cases crashed
(`min1_single_dim.smt2` C++ runtime termination, `min3_empty_relation.smt2`
segfault), and across the 360-dump corpus 51 *purely linear* files crashed
(multiple stars under `or`; 18 segfault/abort, 33 silent deaths). Build
`a18e71578` (2026-07-06) fixes all of these: `min1`/`min3` and every former
in-fragment crasher now answer soundly — the linear, nesting-free set
(`../../cvc5/linear/`, 114 files) runs with **zero crashes** and 99/100
agreement with SQLSolver (the one disagreement being SQLSolver's own
over-approximation bug).

Still open: 240 *out-of-fragment* files (variable products / UFs / nested
stars inside the lambda — cvc5 only supports linear star predicates) die as
`Fatal failure … LiaStarUtils::removeIntegerItes (liastar_utils.cpp:543)`
instead of being rejected gracefully with an error or `unknown`.

## Bug 3 — spurious SAT *with* Normaliz (soundness) — **FIXED in `863fc1373`**

Found on build `19a8efd6b`; the rebuilt binary (`863fc1373-modified`,
2026-07-06) answers `unsat` on the minimal repro, every sibling variant, and
all four real-corpus instances, with 0 contradictions against the star-free
ground truth (`../starfree_check.py`). Bugs 1 and 2 (crashes) are still
present: `min1` segfaults, `min3` dies in the C++ runtime, 2-dim zero stars
crash. Record of the bug as found:

**Minimal reproduction** (`min4_two_zero_stars.smt2`, delta-debugged from
`query048-call-0.smt2`): two all-zero-body stars plus one disequality —

    (not (= w1 x1))
    (w1,w2,w3) in {(a,b,c) | a=0 /\ b=0 /\ c=0}*
    (x1,x2,x3) in {(a,b,c) | a=0 /\ b=0 /\ c=0}*

Both stars contain only the zero vector, so w1 = x1 = 0 and the formula is
trivially UNSAT. SQLSolver answers UNSAT (both INWARD and OUTWARD modes);
cvc5 answers **sat**. Boundary of the bug:

- a *single* zero-body star is answered correctly (4-dim `unsat`; 1-2-dim
  crash per Bug 2), and two equal-components stars are answered correctly —
  the wrong sat needs **two star atoms of which at least one has an all-zero
  body** (a zero star + an `a=b` star also reproduces it);
- with ≤ 2 dimensions the Bug 2 crash fires first (segfault / C++
  termination), masking this bug;
- if the two stars share their point variables, the answer is correct again —
  pointing at per-atom state (e.g. Normaliz cone results) being confused
  across *distinct* star-contains atoms.

Real-corpus instances of the same bug, all answered `sat`:

    ../../cvc5/spark/query048-call-0.smt2   -> sat   (machine-verified unsat)
    ../../cvc5/spark/query048-call-1.smt2   -> sat   (machine-verified unsat)
    ../../cvc5/calcite/query218-call-0.smt2 -> sat   (machine-verified: model refuted)
    ../../cvc5/calcite/query218-call-1.smt2 -> sat   (byte-identical to call-0)

The query048 files have only homogeneous-equality lambda bodies, so star
membership reduces *exactly* to a star-free formula (`../starfree_check.py`);
the reductions are `unsat` under both ℕ- and ℤ-summand semantics according to
this same cvc5 binary and z3. The underlying SQL pair is a truly-equivalent
3-way join reorder.

For query218, cvc5's own model was refuted by exact membership checking: the
model forces star atom #3 to hold with point `(0,1,0,0)`, but the lambda body
implies coord1 = coord2 in every summand (QF_LIA-checked), hence in every
finite sum — the same equal-components pattern as `min2_equal_components.smt2`
that Normaliz-off builds get wrong (Bug 1), now surfacing with Normaliz on.

Consequence: on these benchmarks a cvc5 `sat` is not trustworthy; only `unsat`
answers corroborate.
