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

public class Cvc5LiaStarSolver
{
  public static String fileName = "";
  public static String lastFileName = "";
  public static int index = 1;
  public static Path path;
  static Set<String> smtConstants = new HashSet<>();
  static Map<String, LiaFuncImpl> smtFunctions = new HashMap<>();
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
    fstar.transformPostOrder(lia -> {
      if (lia instanceof LiaVarImpl var)
      {
        smtConstants.add(var.toString());
      }
      if (lia instanceof LiaFuncImpl f)
      {
        smtFunctions.put(f.funcName + f.vars.size(), f);
      }
      return lia;
    });

    StringBuilder builder = new StringBuilder();
    builder.append("(set-logic HO_ALL)\n");

    for (String smtConstant : smtConstants)
    {
      builder.append("(declare-const ").append(smtConstant).append(" Int)\n");
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
    builder.append("(assert ");
    visit(fstar, builder);
    builder.append(")\n");
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
      builder.append("(").append(z.funcName).append(" ");
      for (LiaStar v : z.vars)
      {
        visit(v, builder);
        builder.append(" ");
      }
      builder.append(")");
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
      for (String v : z.outerVector)
      {
        builder.append(v).append(" ");
      }
      for (int i = z.outerVector.size(); i < z.innerVector.size(); i++)
      {
        builder.append(z.innerVector.get(i)).append(" ");
      }
      for (int i = z.innerVector.size(); i < freeVariables.size(); i++)
      {
        builder.append(freeVariables.get(i)).append(" ");
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