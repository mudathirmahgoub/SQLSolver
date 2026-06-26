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

import static sqlsolver.superopt.uexpr.PredefinedFunctions.MINUS;

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
      visit(z.constraints, builder);
      builder.append(") ");
      // Point coordinates of int.star-contains, one per lambda dimension.
      // The first outerVector.size() dimensions are the summed dimensions:
      // outer[i] = sum over summands of inner[i] (matching the canonical
      // semantics in LiaSumImpl.expandStarWithK). Every remaining dimension
      // (surplus inner vars and free constraint vars) is a per-summand
      // existential that expandStarWithK does NOT tie to any outer value, so it
      // must map to a FRESH, otherwise-unconstrained variable. Reusing the
      // bound name (which also denotes a global declared const) would capture
      // that global and impose a spurious global = sum-of-summands equation.
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