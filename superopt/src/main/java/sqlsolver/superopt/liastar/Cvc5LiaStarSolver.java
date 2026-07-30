package sqlsolver.superopt.liastar;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static sqlsolver.superopt.uexpr.PredefinedFunctions.MINUS;
import sqlsolver.superopt.util.Timeout;

public class Cvc5LiaStarSolver
{
  public static String fileName = "";
  public static String lastFileName = "";
  public static int index = 1;
  public static Path path;
  static Set<String> smtConstants = new HashSet<>();
  static Map<String, LiaFuncImpl> smtFunctions = new HashMap<>();
  // Fresh, otherwise-unconstrained Int consts introduced for the surplus
  // (non-outer) point coordinates of int.star-contains; declared at the top.
  static List<String> freshSumVars = new ArrayList<>();
  static int freshIndex = 0;
  public static PrintWriter writer = null;
  public static String csvFile = "sqlsolver_results.csv";
  // Wall-clock budget for the exact parameter-elimination attempt in
  // translate(); past it the dump falls back to the WARNING (weakened) form.
  public static long ELIMINATION_BUDGET_SECONDS = 30;
  static
  {
    try
    {
      writer = new PrintWriter(csvFile);
      writer.println("sqlsolver file,sqlsolver result,sql duration");
      writer.close();
    }
    catch (Exception e)
    {
      e.printStackTrace();
    }
  }

  public static void translate(LiaStar fstar) throws IOException
  {
    // A variable free in a star body (a "parameter") denotes one value shared
    // by all summands and equal to its occurrences outside the star, but
    // int.star-contains cannot express that coupling: its lambda must be
    // closed, so the encoding below binds such vars per-summand. Eliminate
    // parameters exactly first (pushUpParameter + outward removeParameter,
    // which asserts the param constraints once outside the star); if only the
    // lossy per-summand fallback applies, keep the original formula and mark
    // the file as a weakening.
    String header = "";
    final Set<String> starParams = collectStarParams(fstar);
    if (!starParams.isEmpty())
    {
      LiaStar transformed = null;
      // Deeply nested stars make removeParameter's case-splitting explode
      // (hours on large tpc-h formulas), so only attempt elimination on
      // shallow formulas and give the attempt a hard wall-clock budget.
      if (fstar.embeddingLayers() <= 2)
      {
        final LiaStar input = fstar;
        final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
          Thread t = new Thread(r, "cvc5-dump-param-elimination");
          t.setDaemon(true);
          return t;
        });
        final Future<LiaStar> future = executor.submit(() -> {
          LiaStar copy = input.deepcopy();
          copy = copy.pushUpParameter(new HashSet<>());
          LiaSumImpl.removeParameterFallbackUsed = false;
          copy = copy.removeParameter();
          return (!LiaSumImpl.removeParameterFallbackUsed && collectStarParams(copy).isEmpty())
              ? copy
              : null;
        });
        try
        {
          transformed = future.get(ELIMINATION_BUDGET_SECONDS, TimeUnit.SECONDS);
        }
        catch (Throwable e)
        {
          // best-effort transformation: fall through to the warning path on any
          // failure or budget expiry, except genuine verification timeouts
          future.cancel(true);
          if (e instanceof ExecutionException ee && ee.getCause() != null)
          {
            Timeout.bypassTimeout(ee.getCause());
          }
        }
        finally
        {
          executor.shutdownNow();
        }
      }
      if (transformed != null)
      {
        fstar = transformed;
        header = "; star parameters " + starParams + " eliminated exactly before export\n";
      }
      else
      {
        header = "; WARNING: star parameters " + starParams + " could not be eliminated\n"
            + "; exactly. They are bound per-summand in the lambda below with fresh\n"
            + "; unconstrained sums, which WEAKENS the formula: sat of this file does not\n"
            + "; imply sat of the original (only unsat transfers).\n";
      }
    }

    smtConstants = new HashSet<>();
    smtFunctions = new HashMap<>();
    freshSumVars = new ArrayList<>();
    freshIndex = 0;
    fstar.transformPostOrder(lia -> {
      if (lia instanceof LiaVarImpl var)
      {
        smtConstants.add(var.toString());
      }
      if (lia instanceof LiaFuncImpl f)
      {
        // MINUS is an interpreted operator (emitted as native subtraction),
        // so it must not be declared as an uninterpreted function.
        if (!MINUS.contains(f.funcName, f.vars.size()))
        {
          smtFunctions.put(f.funcName + f.vars.size(), f);
        }
      }
      return lia;
    });

    // Visit the formula first so that the fresh existential variables introduced
    // by star (int.star-contains) translation are known before declarations.
    StringBuilder body = new StringBuilder();
    visit(fstar, body);

    StringBuilder builder = new StringBuilder();
    builder.append(header);
    builder.append("(set-logic HO_ALL)\n");

    for (String smtConstant : smtConstants)
    {
      builder.append("(declare-const ").append(smtConstant).append(" Int)\n");
    }
    for (String freshVar : freshSumVars)
    {
      builder.append("(declare-const ").append(freshVar).append(" Int)\n");
    }
    for (Map.Entry<String, LiaFuncImpl> entry : smtFunctions.entrySet())
    {
      LiaFuncImpl f = entry.getValue();
      builder.append("(declare-fun ").append(f.funcName).append(" (");
      for (LiaStar var : f.vars)
      {
        builder.append("Int ");
      }
      builder.append(") Int)\n");
    }
    builder.append("(assert ").append(body).append(")\n");
    builder.append("(check-sat)\n");
    if (lastFileName.equals(fileName))
    {
      index++;
    }
    else
    {
      index = 0;
    }
    path = Path.of("cvc5/" + fileName + "-call-" + index + ".smt2");
    lastFileName = fileName;
    Files.writeString(path, builder, StandardCharsets.UTF_8);
  }

  // Union of collectParamNames over every star in the formula: the free vars
  // of star bodies, i.e. the variables the closed-lambda encoding would
  // decouple from their outer occurrences.
  private static Set<String> collectStarParams(LiaStar f)
  {
    final Set<String> params = new HashSet<>();
    f.transformPostOrder(lia -> {
      if (lia instanceof LiaSumImpl sum)
      {
        params.addAll(sum.collectParamNames());
      }
      return lia;
    });
    return params;
  }

  private static void visit(LiaStar lia, StringBuilder builder)
  {
    if (lia instanceof LiaConstImpl n)
    {
      builder.append(n);
    }
    else if (lia instanceof LiaVarImpl v)
    {
      builder.append(v);
    }
    else if (lia instanceof LiaAndImpl z)
    {
      builder.append("(and ");
      visit(z.operand1, builder);
      builder.append(" ");
      visit(z.operand2, builder);
      builder.append(")");
    }
    else if (lia instanceof LiaNotImpl z)
    {
      builder.append("(not ");
      visit(z.operand, builder);
      builder.append(")");
    }
    else if (lia instanceof LiaOrImpl z)
    {
      builder.append("(or ");
      visit(z.operand1, builder);
      builder.append(" ");
      visit(z.operand2, builder);
      builder.append(")");
    }
    else if (lia instanceof LiaEqImpl z)
    {
      builder.append("(= ");
      visit(z.operand1, builder);
      builder.append(" ");
      visit(z.operand2, builder);
      builder.append(")");
    }
    else if (lia instanceof LiaLeImpl z)
    {
      builder.append("(<= ");
      visit(z.operand1, builder);
      builder.append(" ");
      visit(z.operand2, builder);
      builder.append(")");
    }
    else if (lia instanceof LiaLtImpl z)
    {
      builder.append("(< ");
      visit(z.operand1, builder);
      builder.append(" ");
      visit(z.operand2, builder);
      builder.append(")");
    }
    else if (lia instanceof LiaPlusImpl z)
    {
      builder.append("(+ ");
      visit(z.operand1, builder);
      builder.append(" ");
      visit(z.operand2, builder);
      builder.append(")");
    }
    else if (lia instanceof LiaDivImpl z)
    {
      builder.append("(/ ");
      visit(z.operand1, builder);
      builder.append(" ");
      visit(z.operand2, builder);
      builder.append(")");
    }
    else if (lia instanceof LiaMulImpl z)
    {
      builder.append("(* ");
      visit(z.operand1, builder);
      builder.append(" ");
      visit(z.operand2, builder);
      builder.append(")");
    }
    else if (lia instanceof LiaIteImpl z)
    {
      builder.append("(ite ");
      visit(z.cond, builder);
      builder.append(" ");
      visit(z.operand1, builder);
      builder.append(" ");
      visit(z.operand2, builder);
      builder.append(")");
    }
    else if (lia instanceof LiaFuncImpl z)
    {
      if (MINUS.contains(z.funcName, z.vars.size()))
      {
        // MINUS is interpreted: emit native subtraction, mirroring
        // LiaFuncImpl.transToSMT (ctx.mkSub), not an uninterpreted function.
        builder.append("(- ");
        visit(z.vars.get(0), builder);
        builder.append(" ");
        visit(z.vars.get(1), builder);
        builder.append(")");
      }
      else
      {
        builder.append("(").append(z.funcName).append(" ");
        for (LiaStar v : z.vars)
        {
          visit(v, builder);
          builder.append(" ");
        }
        builder.append(")");
      }
    }
    else if (lia instanceof LiaSumImpl z)
    {
      // outerVector names are emitted as point coordinates below but are plain
      // strings, not LiaVarImpl nodes: declare them even when they occur
      // nowhere else in the formula.
      smtConstants.addAll(z.outerVector);
      builder.append("(int.star-contains (lambda (");
      List<String> freeVariables = new ArrayList<>();
      for (String v : z.innerVector)
      {
        builder.append("(").append(v).append(" Int) ");
        freeVariables.add(v);
      }

      z.constraints.transformPostOrder(l -> {
        if (l instanceof LiaVarImpl var)
        {
          String varName = l.toString();
          if (!freeVariables.contains(varName))
          {
            freeVariables.add(l.toString());
            builder.append("(").append(varName).append(" Int) ");
          }
          return l;
        }
        return l;
      });

      builder.append(") ");
      // int.star-contains no longer assumes nonnegative summand vectors
      // (cvc5's ARITH_LIA_STAR_NONNEGATIVE lemma is gone): the lambda
      // predicate itself must constrain every bound variable.
      builder.append("(and ");
      for (String v : freeVariables)
      {
        builder.append("(>= ").append(v).append(" 0) ");
      }
      visit(z.constraints, builder);
      builder.append(")) ");
      // Point coordinates of int.star-contains, one per lambda dimension.
      // The first outerVector.size() dimensions are the summed dimensions:
      // outer[i] = sum over summands of inner[i]. Surplus innerVector dims are
      // genuinely per-summand existentials (the solver's expansions rename
      // only innerVector per summand copy), so their sums map to FRESH,
      // otherwise-unconstrained variables. Free constraint vars ("parameters")
      // are NOT per-summand — they share one value across summands and with
      // their outer occurrences — but this closed-lambda encoding cannot
      // express that: translate() eliminates them exactly beforehand when
      // possible and otherwise marks the file with a weakening warning.
      for (String v : z.outerVector)
      {
        builder.append(v).append(" ");
      }
      for (int i = z.outerVector.size(); i < freeVariables.size(); i++)
      {
        final String fresh = "sumFresh" + (freshIndex++);
        freshSumVars.add(fresh);
        builder.append(fresh).append(" ");
      }
      builder.append(")");
    }
    else
    {
      String message = fileName + "\n" + lia.getClass().getName() + "\n" + lia;
      throw new UnsupportedOperationException(message);
    }
  }
}