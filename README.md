# SQLSolver: Proving Query Equivalence Using Linear Integer Arithmetic

SQLSolver is an automated prover that verifies the equivalence of SQL queries, which is presented in
the paper "[Proving Query Equivalence Using Linear Integer Arithmetic](https://dl.acm.org/doi/abs/10.1145/3626768)" (SIGMOD 2024).
We also provide an online [demo](https://sqlsolver.systems/sqlsolver/home) and you can try it :-)

## Table of Contents

- [Environment setup](#environment-setup)
  - [Requirements](#requirements)
  - [Install Python](#install-python)
  - [Install Java and Gradle](#install-java-and-gradle)
- [Quick Start](#quick-start)
  - [Compile](#compile)
  - [Building the JAR file](#building-the-jar-file)
  - [Using the JAR file](#using-the-jar-file)
    - [Example](#example) 
- [API](#api)
- [Benchmark](#benchmark)
- [LIA* backend: SQLSolver or cvc5](#lia-backend-sqlsolver-or-cvc5)
- [File Structure](#file-structure)
- [Citation](#citation)
- [Contact](#contact)
- [Contributors](#contributors)

## Environment setup

### Requirements

- Ubuntu (22.04.1 LTS is tested)
- z3 4.8.9 (SMT solver)
- antlr 4.8 (Generate tokenizer and parser for SQL AST)
- Python 3
- JDK 21 or newer (the main sources target Java 17, `superopt`'s tests target Java 21)
- Gradle 9.6.0 — no separate install needed, the Gradle wrapper (`./gradlew`) downloads it

z3 and antlr library have been put in `lib/` off-the-shelf.

#### Install Python

Python is typically installed by default.
Type `python3 --version` to check whether Python 3 is installed.
In cases where it is not installed, use this following instruction:

```shell
sudo apt install python3
```

#### Install Java and Gradle

Gradle itself does not have to be installed: the repository ships the Gradle wrapper
(`gradlew`, `gradlew.bat` and `gradle/wrapper/`), which downloads and runs the exact
version the build is pinned to — Gradle 9.6.0, see
[`gradle/wrapper/gradle-wrapper.properties`](gradle/wrapper/gradle-wrapper.properties).
Use `./gradlew` (`gradlew.bat` on Windows) in place of `gradle` in every command below.

Only a JDK is required. Install JDK 21 or newer and make it the default, e.g.:

```shell
# Installing JDK 21 (Debian/Ubuntu)
sudo apt update
sudo apt install -y openjdk-21-jdk
sudo update-alternatives --config java   # if an older JDK is still the default

java -version                            # should report 21 or newer
```

A JDK older than 21 fails with `error: invalid source release: 21` while compiling
`superopt`'s tests.

## Quick Start

### Compile 

We use Gradle as the project build tool, invoked through the wrapper so that the
pinned Gradle 9.6.0 is used regardless of what is installed on the machine.
After all the above dependencies are installed, you can compile the SQLSolver project
with the following command.

```shell
./gradlew compileJava
```

### Building the JAR file

After Compilation, you can build a JAR file using the following command.

```shell
./gradlew fatJar
```

The JAR file will be generated in the `build/libs/` directory relative
to the current directory.

### Using the JAR file

To use the JAR file of SQLSolver, you need to put libz3.so and libz3java.so in a custom directory: `<path/to/lib>`
and then use the command:
```shell
export LD_LIBRARY_PATH=<path/to/lib>
```

The code below shows how to use SQLSolver's JAR File to verify SQLs.
- `-sql1=<path/to/query1>`: the first file whose path is `<path/to/query1>` to store the SQLs.
- `-sql2=<path/to/query2>`: the second file whose path is `<path/to/query2>` to store the SQLs.
- `-schema=<path/to/schema>`: the schema file whose path is `<path/to/schema>` to store the schema.
- `-print`: output the result to standard output stream.
- `-output=<path/to/output>`: output the result to a file whose path is `<path/to/output>`.

Each SQL file has multiple SQL statements and should store a SQL statement in one line,
and the corresponding lines in both files will be considered as pairs of SQL statements to be verified for equivalence.

```shell
java -jar sqlsolver.jar -sql1=<path/to/query1> -sql2=<path/to/query2> -schema=<path/to/schema> [-print] [-output=<path/to/output>]
```

Since SQLSolver uses the parser of [Calcite](https://calcite.apache.org/) to parse SQL queries, **please ensure that queries in SQL files satisfy the syntax requirements of Calcite parser**.

#### Example

The example here shows how to use SQLSolver's JAR File to verify three simple SQLs.


First SQL file with path `./example_sql1.sql`:
```sql
SELECT i, j FROM a
SELECT x, y FROM b
```

Second SQL file with path `./example_sql2.sql`:
```sql
SELECT T.COL1, T.COL2 FROM (SELECT i AS COL1, j AS COL2 FROM a) AS T
SELECT T.COL1, T.COL2 FROM (SELECT x AS COL1, y AS COL2 FROM b) AS T
```

Schema file with path `./example_schema.sql`:
```sql
CREATE TABLE a ( i INT PRIMARY KEY, j INT, k INT );
CREATE TABLE b ( x INT PRIMARY KEY, y INT, z INT );
```

Then, use the following command to verify the three SQL pairs:
```shell
java -jar sqlsolver.jar -sql1=./example_sql1.sql -sql2=./example_sql2.sql -schema=./example_schema.sql -output=./result
```

You will get a file named `result` in the current directory with the following content:
```text
EQ
EQ
```

## API
The Java users can directly download SQLSolver's source code and access the package `sqlsolver.api`.
The entry of SQLSolver is in `sqlsolver.api.entry.Verification`.

There are two main interfaces:

```java
  /**
   * Verify two sql equivalence.
   */
  VerificationResult verify(String sql0, String sql1, String schema) 
  /**
   * Verify pairwise sql equivalence in the sqlList.
   * The two sql to verify are in the same index of both lists.
   * The two sqlList (sqlList0 and sqlList1) should have same size.
   */
  List<VerificationResult> verify(List<String> sqlList0, List<String> sqlList1, String schema)
  /**
   * Verify pairwise sql equivalence in the sqlList.
   * It resembles Verification#verify(List, List, String) except that it sets a time limitation (seconds) for proving each pair of SQL queries.
   */
  List<VerificationResult> verify(List<String> sqlList0, List<String> sqlList1, String schema, long timeout)
```

The VerificationResult is an enum class for verification result which has four cases:

- **EQ**: two SQL queries are equivalent
- **NEQ**: two SQL queries are not equivalent
- **UNKNOWN**: SQLSolver cannot determine the equivalence of two SQL queries due to some reasons, such as unsupported SQL features and syntax errors
- **TIMEOUT**: SQLSolver can not determine the equivalence within a given time limitation

Note that verifying the equivalence of two SQL queries is an undecidable problem.
Thus, the verification algorithm of SQLSolver does not guarantee to identify all equivalent queries.
SQLSolver may output NEQ or UNKNOWN for some equivalent query pairs.

If you invoke the API with the parameter `timeout`, SQLSolver may still run beyond the time limitation.
Beause SQLSolver invokes external libraries/binaries like Z3.
Sometimes SQLSolver has to wait for those libraries to finish.
You can also configure the time limitation for Z3 via the configuration file `sqlsolver.properties`.
An example `sqlsolver.properties` is under the project root directory.

You can import SQLSolver as a Jar file or directly download and compile the source code in your project.

By calling the interface of SQLSolver, you can get the SQLs verification result.
Before you use SQLSolver, you should put `libz3.so` and `libz3java.so` in a custom directory: `<path/to/lib>`
and then use the command:
```shell
export LD_LIBRARY_PATH=<path/to/lib>
```

Here is a simple example that shows how to use SQLSolver's interface:

```java
import sqlsolver.api.entry.Verification;
import sqlsolver.superopt.logic.VerificationResult;

public class Main {
  public static void main(String[] args) {
    String sql1 = "SELECT i, j FROM a";
    String sql2 = "SELECT T.COL1, T.COL2 FROM (SELECT i AS COL1, j AS COL2 FROM a) AS T";
    String schema = "CREATE TABLE a ( i INT PRIMARY KEY, j INT, k INT );\n" +
            "CREATE TABLE b ( x INT PRIMARY KEY, y INT, z INT );";

    VerificationResult result = Verification.verify(sql1, sql2, schema);
    Printer.output.println(result);
  }
}
```

This program will output a single `EQ` that indicates the verification result of two SQLs `sql1` and `sql2` 
under the schema `schema`.

## Benchmark

In our paper, SQLSolver is evaluated on four benchmarks, including test cases derived from Calcite, Spark SQL, TPC-C, and TPC-H.
The files of SQL queries and schemas are listed in the following table.

Each file of SQL queries consists of multiple pairs of SQL queries.
Each query in the odd-numbered line is equivalent to the query in the next line.
For example, the first query is equivalent to the second query in each file.

| Benchmark | File of Schema                                                                   | File of SQL Queries                                                    |
|:-----------:|:--------------------------------------------------------------------------:|:----------------------------------------------------------------:|
| Calcite   | [Calcite Schema](/sqlsolver_data/schemas/calcite_test.base.schema.sql)   | [Calcite Test Set](sqlsolver_data/calcite/calcite_tests)           |
| Spark SQL | [Spark SQL Schema](/sqlsolver_data/schemas/calcite_test.base.schema.sql) | [Spark SQL Test Set](sqlsolver_data/db_rule_instances/spark_tests) |
| TPC-C     | [TPC-C Schema](/sqlsolver_data/schemas/tpcc.base.schema.sql)             | [TPC-C Test Set](sqlsolver_data/prepared/rules.tpcc.spark.txt)     |
| TPC-H     | [TPC-H Schema](/sqlsolver_data/schemas/tpch.base.schema.sql)             | [TPC-H Test Set](sqlsolver_data/prepared/rules.tpch.spark.txt)     |

## LIA* backend: SQLSolver or cvc5

SQLSolver's star solver supports more than cvc5's LIA* fragment does — uninterpreted
functions, multiplication, nested stars, and variables that occur free inside a star body
("parameters"). Its pipeline reduces all of that away before solving: parameters are pushed
up and removed, and multiplications are abstracted into fresh variables
(`LiaSolver.checkOverapp`). What remains at that point is a LIA* formula with linear,
closed star bodies — precisely what cvc5 accepts as `int.star-contains`.

From there the two backends differ:

| backend | how the linear LIA* formula is decided |
|---|---|
| `sqlsolver` (default) | SQLSolver eliminates each star itself, computing a semi-linear set (`LiaStar.expandStar` → `LiaTransformer`), and gives the resulting plain LIA formula to z3. The construction falls back to an over-approximation whenever the equivalent one fails, so only *unsat* is conclusive — a "sat" is discarded as unknown. |
| `cvc5` | The formula is handed to cvc5 as-is, encoded with `int.star-contains` by `Cvc5LiaStarSolver.toSmt2Script` — the same encoder that writes the exported cvc5 benchmark files. cvc5 decides the star formula, so *sat* is conclusive too (`Cvc5LiaStarBackend`). |

Everything before that point is identical under both backends, including the
under-approximation attempt that can settle *sat* early.

Select the backend on the benchmark runner:

```bash
./gradlew :superopt:smtBenchmarks -PbenchArgs="bapa --timeout=100 --jobs=1 --backend=cvc5"
```

or, for any other entry point into the pipeline (e.g. the query-equivalence driver), with a
system property:

```bash
-Dsqlsolver.liastar.backend=cvc5 [-Dsqlsolver.liastar.cvc5.tlimit=100000]
```

`sqlsolver.liastar.cvc5.tlimit` bounds one cvc5 query in millis (0 = no limit). The
benchmark runner sets it from `--timeout` automatically: a cvc5 check runs in native code,
where an interrupt from the runner's watchdog would not reach it.

Soundness note: *unsat* from the cvc5 backend always transfers back to the input formula.
*Sat* is reported only when the encoding is exact — a star that still has a free variable
has to be bound per-summand under a closed lambda, which weakens the formula, and such a sat
is downgraded to unknown.

## File Structure

This repository includes the source code and benchmarks.

```
|-- api               # SQLSolver's entry.
|-- lib               # Required external library.
|-- common            # Common utilites.
|-- sql               # Data structures of SQL AST and query plan.
|-- stmt              # Manipulation program of queries.
|-- superopt          # Core algorithm of SQLSolver
|-- sqlsolver_data    # Data input/output directory, such as benchmarks
```

## Citation
If you use SQLSolver in your projects or research, please kindly cite our [paper](https://dl.acm.org/doi/abs/10.1145/3626768):
```
@article{sqlsolver,  
  author = {Ding, Haoran and Wang, Zhaoguo and Yang, Yicun and Zhang, Dexin and Xu, Zhenglin and Chen, Haibo and Piskac, Ruzica and Li, Jinyang},  
  title = {Proving Query Equivalence Using Linear Integer Arithmetic},  
  year = {2023},  
  issue_date = {December 2023},  
  publisher = {Association for Computing Machinery},  
  address = {New York, NY, USA},  
  volume = {1},  
  number = {4},  
  journal = {Proc. ACM Manag. Data},  
  month = {Dec},  
  articleno = {227},  
  numpages = {26},  
}
```
## Contact

If you have any questions, please submit an issue or contact our <a href="mailto:nhaorand@gmail.com">email</a>.

## Contributors

Students
- [Haoran Ding](https://haoran-ding.github.io/), Shanghai Jiao Tong University
- [Zhuoran Wei](https://zhuoran-wei.github.io/zhuoran-wei/), Shanghai Jiao Tong University
- Zhenglin Xu, Shanghai Jiao Tong University
- [Yicun Yang](https://yicun0720.github.io/yicun/), Shanghai Jiao Tong University
- Dexin Zhang, Princeton University

Professors
- [Zhaoguo Wang](https://ipads.se.sjtu.edu.cn/pub/members/zhaoguo_wang), [IPADS](https://ipads.se.sjtu.edu.cn/start), Shanghai Jiao Tong University
- [Haibo Chen](https://ipads.se.sjtu.edu.cn/pub/members/haibo_chen), [IPADS](https://ipads.se.sjtu.edu.cn/start), Shanghai Jiao Tong University
- [Ruzica Piskac](https://www.cs.yale.edu/homes/piskac/), [ROSE](https://rose.yale.edu/), Yale University
- [Jinyang Li](https://www.news.cs.nyu.edu/~jinyang/), New York University
