#!/bin/bash
# Regenerate the LIA* smt2 dump corpus for the benchmark suites by running the
# SQLSolver pipeline (fat jar) on each suite's q1.sql/q2.sql pair lists.
#
# For each suite this produces, inside cvc5/<suite>/:
#   queryNNN-call-K.smt2      one dump per LIA* solver call (NNN = 1-based pair
#                             index; K counts calls within the pair — the
#                             pipeline dumps once per param-removal config per
#                             U-expression translation)
#   sqlsolver_results.csv     the pipeline's own per-call verdicts + times
#   output.txt                stdout+stderr of the run
#   result.txt                per-pair EQ/NEQ/... verdicts
#
# Dumps are written by Cvc5LiaStarSolver.translate, which eliminates star
# parameters exactly before export when possible; files carry a header comment
# saying either "eliminated exactly" or "WARNING ... WEAKENS" (see
# superopt/.../Cvc5LiaStarSolver.java).
#
# Usage: comparison/regenerate_dumps.sh [suite ...]     default: all suites
set -uo pipefail
cd "$(dirname "$0")/.."

JAVA="$(/usr/libexec/java_home)/bin/java"
JAR=build/libs/sqlsolver-v1.1.0-all.jar
[ -f "$JAR" ] || { echo "missing $JAR — build with ./gradlew fatJar"; exit 1; }

schema_of() {
  case "$1" in
    calcite|spark) echo calcite_test.base.schema.sql ;;
    tpc-c)         echo tpcc.base.schema.sql ;;
    tpc-h)         echo tpch.base.schema.sql ;;
    *)             echo "unknown suite $1" >&2; exit 1 ;;
  esac
}

SUITES=("$@")
[ ${#SUITES[@]} -eq 0 ] && SUITES=(calcite spark tpc-c tpc-h)

for suite in "${SUITES[@]}"; do
  echo "=== regenerating $suite ($(wc -l < cvc5/$suite/q1.sql | tr -d ' ') pairs) ==="
  # clear leftovers of a previous run and the stale corpus
  rm -f cvc5/query*-call-*.smt2 sqlsolver_results.csv
  rm -f cvc5/$suite/query*-call-*.smt2 cvc5/$suite/query*-call-*.txt \
        cvc5/$suite/sqlsolver_results.csv

  DYLD_LIBRARY_PATH="$PWD/lib" "$JAVA" -Djava.library.path="$PWD/lib" \
    --enable-native-access=ALL-UNNAMED -jar "$JAR" \
    -sql1=cvc5/$suite/q1.sql -sql2=cvc5/$suite/q2.sql \
    -schema=sqlsolver_data/schemas/$(schema_of $suite) \
    -output=cvc5/$suite/result.txt -print \
    > cvc5/$suite/output.txt 2>&1
  echo "    pipeline exit: $?"

  # collect the dumps and the per-call results
  n=$(ls cvc5/query*-call-*.smt2 2>/dev/null | wc -l | tr -d ' ')
  [ "$n" != "0" ] && mv cvc5/query*-call-*.smt2 cvc5/$suite/
  [ -f sqlsolver_results.csv ] && mv sqlsolver_results.csv cvc5/$suite/sqlsolver_results.csv
  echo "    $n dumps -> cvc5/$suite/"
done
echo "done"
