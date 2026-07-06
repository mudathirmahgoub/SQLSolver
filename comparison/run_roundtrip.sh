#!/bin/bash
# Run SQLSolver's own LIA* solver on every dump in the corpus (apples-to-apples
# with cvc5/SLS on the same .smt2 files): each file is parsed back to a LiaStar
# formula via SmtToSqlSolver and solved by LiaSolver (OUTWARD config, 100s/file;
# see SmtBenchmarks.runAllSqlSolverBenchmarks).
#
# Output: sql_solver.csv at the repo root (filename,result,duration) with
# result in {SAT, UNSAT, UNKNOWN, timeout, error}. "error" rows are files the
# test-side smt2->LiaStar translator does not support (e.g. uninterpreted
# functions), not solver failures.
set -uo pipefail
cd "$(dirname "$0")/.."
./gradlew :superopt:test --rerun \
  --tests "sqlsolver.superopt.liastar.SmtBenchmarks.runAllSqlSolverBenchmarks"
echo "wrote sql_solver.csv"
