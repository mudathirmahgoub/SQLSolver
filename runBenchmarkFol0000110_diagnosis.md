# Why `runBenchmarkFol0000110` fails

**Test:** `sqlsolver.superopt.liastar.SmtBenchmarks#runBenchmarkFol0000110`
**Benchmark:** `/home/mudathir/all/sls-reachability/benchmarks/bapa/arith/cvc5_mapa/fol_0000110.smt2`
**Expected:** `UNSAT` — **Actual:** `SAT`

The benchmark is genuinely UNSAT (verified independently with z3 on a hand-encoded under-approximation), but `LiaSolver.solveWithConfig(...)` returns `SAT`.

## Trace through `LiaSolver.solve()`

Output from [output.txt](output.txt) (produced by `Printer.output` during the run):

1. [LiaSolver.java:58-61](superopt/src/main/java/sqlsolver/superopt/liastar/LiaSolver.java#L58-L61) runs `checkUnderapp()`. The under-approximation correctly resolves to **UNSAT** ([output.txt:93](output.txt#L93)). UNSAT under-approximation isn't conclusive (the original could still be SAT), so the code falls through.
2. [LiaSolver.java:66-72](superopt/src/main/java/sqlsolver/superopt/liastar/LiaSolver.java#L66-L72) runs `checkOverapp()`, which returns **SAT** ([output.txt:164](output.txt#L164)).
3. Code at [LiaSolver.java:71-72](superopt/src/main/java/sqlsolver/superopt/liastar/LiaSolver.java#L71-L72) treats over-approximation `SAT` as the definitive answer, returning `LiaSolverStatus.SAT`.

## Root cause: unsound abstraction of `-1 * x`

`SmtToSqlSolver.translateTerm` handles `(- a b)` by emitting `a + (-1 * b)` ([SmtToSqlSolver.java:188-194](superopt/src/test/java/sqlsolver/superopt/liastar/SmtToSqlSolver.java#L188-L194)). So the inner relation's term `(- UNI f0)` becomes `UNI + (-1 * f!0!2)`.

When this multiplication is processed inside the star, [LiaMulImpl.mergeMult](superopt/src/main/java/sqlsolver/superopt/liastar/LiaMulImpl.java#L109-L128) always abstracts it to a fresh variable (here `var25`), regardless of whether one operand is a constant. Then [LiaSumImpl.mergeMult:44-87](superopt/src/main/java/sqlsolver/superopt/liastar/LiaSumImpl.java#L44-L87) attaches three "weak" replacement constraints. Crucially, `collectAllVars()` only returns `LiaVarImpl` nodes — **the `-1` constant factor is silently dropped**. So with `vars = {f!0!2}`, the rule "all factors ≥ 0 → product ≥ 0" emits:

```
(or (not (<= 0 f!0!2)) (<= 0 var25))   ; f!0!2 ≥ 0  →  var25 ≥ 0
```

But `var25 = -1 * f!0!2`, so when `f!0!2 > 0` we should have `var25 < 0`. This constraint is **unsound for negative constant factors** and (combined with dropping the actual `var25 = -f!0!2` equality) destroys the relationship that proves UNSAT. You can see the rewritten constraint at [output.txt:112](output.txt#L112).

## The downstream fact that's lost

In the original formula every inner tuple satisfies `inner_u7 = inner_UNI - inner_f0` (when `inner_u4 = 0`), so summing across the star gives:

```
u!7 = u!5 - u!6 = n - u!6
```

With `u!6 ≤ t` and `a_ev ≥ n - t`, the constraint
```
2*(a_ev + u!7 - n) < n - t + 1
```
becomes `2*(a_ev - u!6) < n - t + 1`. Lower-bounding `a_ev - u!6 ≥ n - 2t` gives `2(n - 2t) < n - t + 1`, i.e. `n ≤ 3t`, contradicting `n > 3t`.

The over-approximation never derives `u!7 = u!5 - u!6`, so it finds spurious models.

## Independent verification with z3

The under-approximation with 2 summands was hand-encoded into [/tmp/underapprox_check.smt2](/tmp/underapprox_check.smt2) and z3 returns `unsat`, confirming the benchmark really is UNSAT.

## Suggested fixes (in increasing depth)

1. **Cheapest:** in [LiaMulImpl.mergeMult](superopt/src/main/java/sqlsolver/superopt/liastar/LiaMulImpl.java#L109), short-circuit when one operand is a `LiaConstImpl` — multiplication by a constant is linear and should stay in LIA, not be abstracted to a fresh variable with weak constraints.
2. **Translation-side alternative:** in [SmtToSqlSolver.java:188-194](superopt/src/test/java/sqlsolver/superopt/liastar/SmtToSqlSolver.java#L188-L194), avoid synthesizing the `-1 * b` multiplication. `Sub` could be modeled as `mkPlus(a, mkNegate(b))` if the IR has negation, or by pushing the negation into a constant-coefficient form.
3. **Deeper:** in [LiaSumImpl.mergeMult:73-82](superopt/src/main/java/sqlsolver/superopt/liastar/LiaSumImpl.java#L73-L82), include constant factors when determining the sign of the product (e.g., if the product of constant factors is negative, the rule flips: "all variable factors ≥ 0 → product ≤ 0").

Also worth noting: [LiaSolver.java:71-72](superopt/src/main/java/sqlsolver/superopt/liastar/LiaSolver.java#L71-L72) treats `SAT` from the over-approximation as a definitive answer, which isn't sound for an over-approximation in general; ideally that branch should return `UNKNOWN` unless the witness is verified against the original formula.

## Reproduction

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export PATH=$JAVA_HOME/bin:$PATH
./gradlew :superopt:test --tests "sqlsolver.superopt.liastar.SmtBenchmarks.runBenchmarkFol0000110"
```

Detailed solver-internal dump is written to `output.txt` at the repo root (because `LogicSupport.dumpLiaFormulas` defaults to `true` and `Printer` writes there).
