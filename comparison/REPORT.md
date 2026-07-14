# Three-solver comparison on the SQLSolver LIA\* benchmark corpus

*Generated 2026-07-06. Fully reproducible: `comparison/run_all.sh` regenerates
everything below from scratch (corpus → three solver runs → ground-truth
cross-check → tables/plots).*

## 1. Benchmark corpus

The corpus is produced by running the SQLSolver pipeline on four SQL
equivalence suites and exporting every LIA\* solver call to SMT-LIB
(`int.star-contains` extension). Each formula asserts "a counterexample
database exists", so **unsat = the SQL pair is equivalent**. The intended
semantics is over nonnegative integers (tuple multiplicities).

| suite | pairs | dumps |
|---|---|---|
| calcite | 232 | 80 |
| spark | 127 | 57 |
| tpc-c | 19 | 0 (no pair reaches the LIA\* stage) |
| tpc-h | 22 | 223 |
| **total** | 400 | **360** |

Star **parameters** (variables shared by all summands of a star and by the
enclosing formula) cannot be expressed inside `int.star-contains`'s closed
lambda, so the exporter (`Cvc5LiaStarSolver.translate`) eliminates them exactly
before export whenever it can (`pushUpParameter` + outward `removeParameter`,
attempted only for star-nesting depth ≤ 2 and capped at 30 s wall-clock).
Every file's first line states its fidelity:

| header | files | meaning |
|---|---|---|
| `; star parameters [...] eliminated exactly` | 194 | equisatisfiable with the pipeline's formula |
| *(none — `(set-logic HO_ALL)` first)* | 40 | no parameters; encoding is direct |
| `; WARNING: ... WEAKENS the formula` | 126 | parameters bound per-summand: **sat does not transfer** to the original formula, unsat does |

## 2. Solvers and protocol

All three solvers run on the same 360 files, 100 s per file:

- **SQLSolver** — its own LIA\* solver, reached by parsing each file back
  (`SmtToSqlSolver`) and solving (`LiaSolver`, OUTWARD config).
  `comparison/run_roundtrip.sh` → `sql_solver.csv`.
- **cvc5 (liastar branch, Normaliz on)** — `~/cvc5/liastar/build/bin/cvc5`,
  native `int.star-contains`. `comparison/run_cvc5.py` → `cvc5_all.csv` +
  per-file `.txt` next to each `.smt2`.
- **SLS-reachability** — `~/sls-reachability/smt_to_sls.py`
  (interpolation-based LIA\* via z3). `comparison/run_sls.py` (parallel driver,
  same per-file semantics as the original `run_sql.py`) → `sls_results.csv`.

## 3. Results (360 instances)

| solver | solved | sat | unsat | timeout | crash | error |
|---|---|---|---|---|---|---|
| SQLSolver | **204** | 164 | 40 | 3 | — | 153 |
| cvc5 (Normaliz) | 119 | 71 | 48 | 1 | 240 | — |
| SLS-reachability | 96 | 89 | 7 | 55 | — | 209 |

cvc5 numbers are from the liastar build `a18e71578` (2026-07-06), which fixes
both bugs found on earlier builds during this comparison: the spurious-sat
soundness bug (§5.2, fixed in `863fc1373`, flipped 4 answers sat→unsat) and
the in-fragment crash bug (§5.1, fixed in `a18e71578`, turned 51 crashes into
50 sound answers + 1 timeout). All 240 remaining crashes are on
out-of-fragment inputs (non-linear/UF/nested star bodies).

Cactus plots: `cactus_plot.png`, `cactus_plot_log.png`. Per-instance table:
`comparison.csv`; per-solver counts: `summary.csv`. The fragment-restricted
view (`filter_linear.py`, 114 benchmarks in `cvc5/linear/`): cvc5 solves
**113/114 with zero crashes**, SQLSolver 99/114, SLS 21/114.

Pairwise agreement where both answered sat/unsat: SQLSolver–cvc5 **99/100**
(the one disagreement is §5.4's `query026-call-4`), SQLSolver–SLS 16/25,
cvc5–SLS 14/20. Every disagreement is diagnosed in §5.

## 4. Independent ground truth: exact star elimination

`comparison/starfree_check.py`: when every lambda body in a file is a
conjunction of homogeneous equalities (`x = y`, `x = 0`), the solution set is
closed under vector addition and contains 0, so the star closure equals the
set itself and membership reduces *exactly* to a star-free formula —
decidable by any off-the-shelf solver, independent of any star decision
procedure. 48/360 files are reducible; both ℕ-summand and ℤ-summand variants
were decided for all of them (`starfree_check.csv`).

Scorecard against this ground truth:

| solver | answers on reducible files | contradictions |
|---|---|---|
| SQLSolver | 46 | **0** |
| cvc5 (`863fc1373`, current) | 22 | **0** |
| cvc5 (`19a8efd6b`, before fix) | 22 | 2 (spurious sat, §5.2) |
| SLS | 0 solved | 0 |

## 5. Bugs found (all reproduced on this corpus)

### 5.1 cvc5-liastar: crashes — in-fragment crashes **FIXED in `a18e71578`**
cvc5's `int.star-contains` supports only *linear* arithmetic in the star
predicate. Classifying every lambda body in the corpus splits the crash
population in two, with very different fates:

| star bodies in file | files | cvc5 outcome (current build) |
|---|---|---|
| linear, no nested stars (`cvc5/linear/`) | 114 | **113 solved, 1 timeout, zero crashes** |
| non-linear (`(* x y)` products), UF, or nested stars | 246 | 240 crash, 4 unsat, **2 sat (suspect: out-of-fragment)** |

Earlier builds crashed on 51 in-fragment (purely linear) files — all with
multiple stars nested under disjunctions, including the degenerate-star repros
(`cvc5_star_bug/min1_single_dim.smt2`, `min3_empty_relation.smt2`, two-zero-star
variants) and 33 silent deaths. Build `a18e71578` fixes all of them: every
former in-fragment crash now yields a sound answer agreeing with SQLSolver
(and with the §4 ground truth where applicable).

The 240 remaining crashes are **out-of-fragment input**: SQL join semantics
multiplies tuple multiplicities, so most files have variable products inside
star bodies (plus some UFs and nested stars) that cvc5 cannot express. These
still die as `Fatal failure … LiaStarUtils::removeIntegerItes`
(`liastar_utils.cpp:543`) rather than being rejected gracefully with an error
or `unknown`. Also note the 2 `sat` answers on non-linear files
(`tpc-h/query018-call-2/3.smt2`): answering instead of rejecting
out-of-fragment input is itself a bug, and those answers are unverifiable.

### 5.2 cvc5-liastar: spurious `sat` (soundness) — **FIXED in `863fc1373`**
Found on build `19a8efd6b`, minimized to
`cvc5_star_bug/min4_two_zero_stars.smt2` (two all-zero-body stars + one
disequality: trivially unsat, cvc5 answered sat; the trigger was two star
atoms of which at least one has an all-zero body). Real-corpus instances,
all machine-verified or corroborated unsat: `spark/query048-call-0/1.smt2`
(star-free reduction unsat per §4), `calcite/query218-call-0/1.smt2` (cvc5's
own model refuted by exact membership checking — the min2 equal-components
pattern). The rebuilt binary answers `unsat` on the minimal repro, all
sibling variants, and all four corpus instances, and has 0 ground-truth
contradictions (§4). Historical lesson kept for method: when two solvers
disagree, reduce to an independently checkable form before trusting either.

### 5.3 SLS harness: the smt2 adapter mistranslates stars
`smt_to_sls.py::cvc5_to_z3` replaces a `star-contains` atom by **its lambda
body alone**, resolving lambda binders to the same-named *global* constants and
discarding the point arguments (the summed vector) entirely; only a top-level
conjunct star is treated as the star predicate, and `star_variables` is set to
*every* declared symbol. On this corpus that yields 209 errors, and 9 spurious
`sat`s contradicted by SQLSolver's unsat (6 of them also by cvc5's unsat):
`calcite/query{039,044,051,082,115,120,148,156,190}-call-0.smt2`. Minimal
repro: `sls_bug/sever_repro.smt2` — `y = 3 ∧ y ∈ {(a) | a = 5}*` is UNSAT
(y must be a sum of 5s), but the adapter answers `sat` because dropping the
point argument turns "y is a sum of 5s" into "some variable a equals 5". SLS
results on this corpus therefore measure the adapter, not the SLS algorithm.

### 5.4 SQLSolver: over-approximation SAT is reported as SAT (unsound side)
`LiaSolver.solve()` returns SAT when `checkOverapp()` — an
*over-approximation* — is satisfiable (`LiaSolver.java:72-75`). Only the UNSAT
side of an over-approximation is sound; its SAT can be an artifact. Fresh
instance: `calcite/query026-call-4.smt2` — the sound bounded exact check
(`checkUnderapp`) is UNSAT, yet `solve()` answers SAT from the
over-approximation; cvc5 answers unsat. The spurious model is explicit about
the mechanism: it assigns star point coordinate `var9 = -1` (feeding
`u1 = var4 + var9 + var14`), a negative "row count" that no sum of nonnegative
summand vectors can produce. None of SQLSolver's 46 answers on the
ground-truth-reducible subset were wrong, but its `sat` answers in §3 carry no
general guarantee. Its `unsat` answers (= "queries equivalent") come from the
sound side.

### 5.5 SQLSolver: smt2 front-end coverage
The 153 `error` rows are files whose round-trip reader
(`SmtToSqlSolver.translateTerm`) lacks cases (uninterpreted functions,
`VARIABLE`-kind terms, …) — a coverage gap of the *test-side reader*, not the
pipeline. These files are still produced and solved by the pipeline itself.

## 6. Reproduction

```
comparison/run_all.sh              # everything below, in order
comparison/regenerate_dumps.sh     # pipeline -> cvc5/<suite>/*.smt2 + per-call CSVs
comparison/run_roundtrip.sh        # SQLSolver on dumps  -> sql_solver.csv
comparison/run_cvc5.py             # cvc5 on dumps       -> cvc5_all.csv + .txt
comparison/run_sls.py              # SLS on dumps        -> sls_results.csv
comparison/starfree_check.py       # ground truth        -> starfree_check.csv
comparison/make_comparison.py      # comparison.csv, summary.csv, cactus plots
```

Requirements: `./gradlew fatJar` (the exporter lives in the fat jar), the
liastar cvc5 binary at `~/cvc5/liastar/build/bin/cvc5` (built with Normaliz),
its Python bindings at `~/cvc5/liastar/build-python` (for SLS), and
`~/sls-reachability`.
