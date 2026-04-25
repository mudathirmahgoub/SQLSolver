# Why `runBenchmarkBapaFol0000120` fails

**Test:** `sqlsolver.superopt.liastar.SmtBenchmarks#runBenchmarkBapaFol0000120` ([SmtBenchmarks.java:134-145](superopt/src/test/java/sqlsolver/superopt/liastar/SmtBenchmarks.java#L134-L145))
**Benchmark:** `/home/mudathir/all/sls-reachability/benchmarks/bapa/card/cvc5_bapa/fol_0000120.smt2`
**Expected:** `UNSAT` — **Actual:** `SAT`

The benchmark is genuinely UNSAT (verified independently with z3), but `LiaSolver.solveWithConfig(...)` returns `SAT`. Unlike the earlier `runBenchmarkMapaFol0000110` failure (see [runBenchmarkFol0000110_diagnosis.md](runBenchmarkFol0000110_diagnosis.md)), this is **not** the `-1 * x` abstraction problem — that fix is already applied in [LiaMulImpl.java:108-114](superopt/src/main/java/sqlsolver/superopt/liastar/LiaMulImpl.java#L108-L114). This benchmark contains **no subtraction** and never enters the constant-multiplication path. The root cause here is a different gap: the over-approximation that `LiaSolver` builds does not preserve the cardinality / pigeonhole reasoning the benchmark depends on.

## What the benchmark says

The body of the star is a 15-tuple of integers
`(u!7, u!8, u!9, u!11, u!12, u!14, u!15, u!17, u!18, u!19, a_hh, a_hg, a_hi, UNI, f)`.
The first ten components are characteristic indicators (each `= ite(cond, 1, 0)`); the last five are non-negative integers. The body asserts:

- `inner_u7  = ite(f > UNI, 1, 0)`
- `inner_u8  = ite(UNI > 0, 1, 0)`
- `inner_u9  = ite(f > 0, 1, 0)`
- `inner_u11 = ite(a_hi > UNI, 1, 0)`
- `inner_u12 = ite(a_hi > 0, 1, 0)`
- `inner_u14 = ite(a_hh > UNI, 1, 0)`
- `inner_u15 = ite(a_hh > 0, 1, 0)`
- `inner_u17 = ite(a_hg > UNI, 1, 0)`
- `inner_u18 = ite(a_hg > 0, 1, 0)`
- `inner_u19 = ite(min(a_hi, a_hh, a_hg) > 0, 1, 0)`
- `f, UNI, a_hi, a_hh, a_hg ≥ 0`

The outer A-formulas pin: `u!7 = 0`, `u!8 = n`, `n > 0`, `n > 3*t`, `u!9 ≤ t`, `u!11 = 0`, `u!12 ≥ n - t`, `u!14 = 0`, `u!15 ≥ n - t`, `u!17 = 0`, `u!18 ≥ n - t`, `u!19 = 0`.

### Pigeonhole proof of UNSAT

`u!8 = n` says exactly `n` summands have `UNI > 0`. Summands with `UNI = 0` force `a_hi = a_hh = a_hg = 0` (each is `≤ UNI` and `≥ 0`), so they contribute nothing to `u!12, u!15, u!18, u!19`. Restrict attention to the `n` "active" summands:

- `u!12 ≥ n - t` ⇒ at most `t` active summands have `a_hi = 0`
- `u!15 ≥ n - t` ⇒ at most `t` have `a_hh = 0`
- `u!18 ≥ n - t` ⇒ at most `t` have `a_hg = 0`
- Union of complements has size `≤ 3t < n`, so at least one active summand has all three of `a_hi, a_hh, a_hg > 0`
- That summand has `min(a_hi, a_hh, a_hg) > 0`, so `inner_u19 = 1`
- Sum over summands: `u!19 ≥ 1`, contradicting `u!19 = 0`

### Independent z3 verification

A direct unrolled encoding with `K = 6` summands and concrete `n = 4, t = 1` is in [/tmp/bapa120_pigeon.smt2](/tmp/bapa120_pigeon.smt2). z3 returns `unsat` in well under a second.

## Trace through `LiaSolver.solve()`

Output captured in [output.txt](output.txt) (driven by `Printer.output` because `LogicSupport.dumpLiaFormulas` defaults to `true`).

1. [LiaSolver.java:58-64](superopt/src/main/java/sqlsolver/superopt/liastar/LiaSolver.java#L58-L64) runs `checkUnderapp()`. The under-approximation unrolls the star to 2 summands and resolves to **UNSAT** ([output.txt:228](output.txt#L228)). Under-approximation `UNSAT` is uninformative (the original could still be `SAT` with more summands), so the code falls through.
2. [LiaSolver.java:66-74](superopt/src/main/java/sqlsolver/superopt/liastar/LiaSolver.java#L66-L74) runs `checkOverapp()`, which builds the over-approximation, hands it to z3, and gets **SAT** ([output.txt:332](output.txt#L332)).
3. [LiaSolver.java:71-72](superopt/src/main/java/sqlsolver/superopt/liastar/LiaSolver.java#L71-L72) treats over-approximation `SAT` as the definitive answer and returns `LiaSolverStatus.SAT`.

Two separate problems collude here:

### Problem A: the over-approximation is too weak

[LiaTransformer.makeImplications](superopt/src/main/java/sqlsolver/superopt/liastar/transformer/LiaTransformer.java#L272-L282) emits exactly three families of implications:

1. **`implyVarEq`** — `outer_a = outer_b` when body forces `inner_a = inner_b`
2. **`implyZeroImplication`** — `outer_a = 0 → outer_b = 0` when body forces `inner_a ≥ 0` and `inner_a = 0 → inner_b = 0`
3. **`implyVarNonNegative`** — `outer_a ≥ 0` when body forces `inner_a ≥ 0`

For this benchmark these expand to (see [output.txt:254 / :306-329](output.txt#L254)):

```
u!8  = 0 → UNI = 0
u!9  = 0 → u!7 = 0,   u!9  = 0 → f = 0
u!12 = 0 → u!11 = 0,  u!12 = 0 → u!19 = 0,  u!12 = 0 → a_hi = 0
u!15 = 0 → u!14 = 0,  u!15 = 0 → u!19 = 0,  u!15 = 0 → a_hh = 0
u!18 = 0 → u!17 = 0,  u!18 = 0 → u!19 = 0,  u!18 = 0 → a_hg = 0
... (and the symmetric directions for a_hh = 0 → ..., etc.)
```

None of these refer to the **values** of `u!12, u!15, u!18, u!19, u!8` — they only fire when something is exactly `0`. Since the outer A-formula gives `u!12, u!15, u!18 ≥ n - t > 0`, every implication of the form `u!X = 0 → ...` is vacuously true. z3 trivially satisfies the over-approximation.

What's missing is the **monotone bound** ("if body forces `inner_a ≤ inner_b` and both are non-negative, then `outer_a ≤ outer_b`") and, more critically, the **per-summand pigeon inequality** (`outer_u12 + outer_u15 + outer_u18 - outer_u19 ≤ 2 * outer_u8`) which captures the inclusion-exclusion that drives this proof.

I confirmed empirically:

- Adding only the monotone bound (`u!8 ≤ UNI`, `u!9 ≤ f`, `u!12 ≤ a_hi`, `u!15 ≤ a_hh`, `u!18 ≤ a_hg`, `u!19 ≤ a_hi`, `u!19 ≤ a_hh`, `u!19 ≤ a_hg`, `u!7 ≤ f`, `u!11 ≤ a_hi`, `u!14 ≤ a_hh`, `u!17 ≤ a_hg`) is **not enough** — z3 still returns `sat` (see [/tmp/bapa120_overapprox_with_bounds.smt2](/tmp/bapa120_overapprox_with_bounds.smt2)).
- Adding the per-summand pigeon bound `u!12 + u!15 + u!18 - u!19 ≤ 2 * u!8` **is** enough — z3 returns `unsat` (see [/tmp/bapa120_overapprox_with_pigeon.smt2](/tmp/bapa120_overapprox_with_pigeon.smt2)).

### Problem B: `solve()` treats over-approximation `SAT` as definitive

[LiaSolver.java:71-72](superopt/src/main/java/sqlsolver/superopt/liastar/LiaSolver.java#L71-L72) returns `SAT` when the over-approximation is `SAT`, but that conclusion is **unsound for an over-approximation**: an over-approximation can be `SAT` with the original formula being either `SAT` or `UNSAT`. Only `UNSAT` from an over-approximation is conclusive. The honest return here is `UNKNOWN`.

This was already noted in the previous diagnosis (footnote in [runBenchmarkFol0000110_diagnosis.md](runBenchmarkFol0000110_diagnosis.md)) and remains unfixed.

## Why the previous fixes don't cover this case

The bug-fix commit [`97b73c8`](.) corrected two genuine bugs in `SmtToSqlSolver` (swapped `GT`/`GEQ` translation; bound-variable name capture) and the follow-up commit [`b7cba86`](.) short-circuited constant multiplication in [LiaMulImpl.mergeMult](superopt/src/main/java/sqlsolver/superopt/liastar/LiaMulImpl.java#L108-L114). Together those fix `runBenchmarkMapaFol0000110` (which hinged on the unsound abstraction of `-1 * x`).

This new benchmark contains **no subtraction** and **no negative coefficients**: there's nothing for the constant-multiplication short-circuit to do. The body's only non-LIA constructs are nested `ite` expressions and an implicit `min` (encoded with nested `ite`). All terms remain in LIA after `simplifyMult` / `mergeMult`. The over-approximation step is the entire problem.

## Suggested fixes

In rough order of effort:

1. **Make `solve()` sound (small, mechanical).** Change [LiaSolver.java:72](superopt/src/main/java/sqlsolver/superopt/liastar/LiaSolver.java#L72) to return `LiaSolverStatus.UNKNOWN` when the over-approximation reports `SAT`. This does **not** make this test pass (`UNKNOWN ≠ UNSAT`), but it stops the solver from emitting confidently wrong `SAT` answers and aligns the result with the soundness contract of the algorithm. It will likely flip a number of other benchmarks from `SAT` to `UNKNOWN` — those answers were never sound to begin with, and that breakage is informative rather than regressive.

2. **Add the monotone-bound rule to `LiaTransformer.makeImplications` (medium).** Mirror the pattern of the existing three implication families: a new method `implyMonotone(v, w, f)` that for each pair `(i, j)` checks `Z3Support.isValidLia(f → (inner_w_i ≤ inner_w_j))` and `Z3Support.isValidLia(f → (inner_w_i ≥ 0))`, and on success emits `outer_v_i ≤ outer_v_j`. This is sound (sum of pointwise `≤` is `≤`) and useful for *many* benchmarks with indicator variables, even though it isn't sufficient for this one in isolation.

3. **Recognise indicator variables and emit cardinality bounds (larger).** Detect bound vars of the shape `inner_u = ite(cond, 1, 0)` and emit per-summand inclusion-exclusion inequalities. The general pattern: any per-summand inequality `Σ c_i · inner_w_i ≤ 0` implied by the body lifts to `Σ c_i · outer_v_i ≤ 0`. A bounded-coefficient search (coefficients in `{-2, -1, 0, 1, 2}` over the indicator dimensions, then validated with z3) is exponential in the indicator-set size but tractable for the ~10 indicator dimensions in these benchmarks. The pigeon inequality `u!12 + u!15 + u!18 - u!19 - 2 · u!8 ≤ 0` falls out of such a search.

4. **A different proof procedure entirely (large).** The under-approximation here is correct for `k = 2` and stays `UNSAT` for any concrete bound, but the unbounded `n` means no fixed `k` proves the original. A proof strategy that increases `k` while keeping `n` symbolic (e.g., a CEGAR loop that strengthens the over-approximation from each `SAT` witness) would close the loop more cleanly than enumerating invariant patterns.

I did **not** apply any of these in this pass: (1) is mechanically safe but flips several other benchmarks and warrants a deliberate decision about CSV outputs; (2) needs careful integration with `transformSls` and the `g` constraint pipeline; (3) and (4) are real engineering work. The diagnosis above is enough to scope whichever path you pick.

## Reproduction

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export PATH=$JAVA_HOME/bin:$PATH
./gradlew :superopt:test --tests "sqlsolver.superopt.liastar.SmtBenchmarks.runBenchmarkBapaFol0000120"
```

The detailed solver-internal dump is written to `output.txt` at the repo root. Independent z3 checks:

```bash
z3 -T:30 /tmp/bapa120_pigeon.smt2                     # → unsat (proves benchmark is UNSAT)
z3 -T:30 /tmp/bapa120_overapprox_with_bounds.smt2     # → sat   (monotone bound alone is too weak)
z3 -T:30 /tmp/bapa120_overapprox_with_pigeon.smt2     # → unsat (per-summand pigeon bound suffices)
```
