package sqlsolver.superopt.liastar;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import io.github.cvc5.Command;
import io.github.cvc5.InputParser;
import io.github.cvc5.Kind;
import io.github.cvc5.Solver;
import io.github.cvc5.SymbolManager;
import io.github.cvc5.Term;
import io.github.cvc5.TermManager;
import io.github.cvc5.modes.InputLanguage;

public class SmtToSqlSolver
{
  public Stack<Map<String, LiaStar>> symbolTable = new Stack<>();
  public LiaStar minusOne = LiaStar.mkConst(false, -1);
  private static int index = 0;
  LiaStar translateFile(String filename)
  {
    TermManager tm = new TermManager();
    Solver slv = new Solver(tm);
    slv.setOption("dag-thresh", "0");
    InputParser parser = new InputParser(slv);
    parser.setFileInput(InputLanguage.SMT_LIB_2_6, filename);
    SymbolManager sm = parser.getSymbolManager();

    // parse commands until finished
    while (true)
    {
      Command cmd = parser.nextCommand();
      if (cmd.isNull())
      {
        break;
      }
      if (!cmd.getCommandName().equals("check-sat"))
      {
        cmd.invoke(slv, sm);
      }
    }

    System.out.println("Finished parsing commands");
    Map<String, LiaStar> symbols = new HashMap<>();
    // declare all variables; function symbols (declare-fun with arity > 0)
    // are translated at their application sites (APPLY_UF) instead
    for (Term t : sm.getDeclaredTerms())
    {
      if (t.getSort().isFunction())
      {
        continue;
      }
      String name = t.toString();
      symbols.put(name, LiaStar.mkVar(false, name));
    }
    symbolTable.push(symbols);
    LiaStar formula = LiaStar.mkTrue(false);
    for (Term assertion : slv.getAssertions())
    {
      LiaStar liaStar = translateTerm(assertion);
      formula = LiaStar.mkAnd(false, formula, liaStar);
    }
    return formula;
  }

  private LiaStar translateTerm(Term t)
  {
    // base cases
    if (t.isBooleanValue())
    {
      boolean value = t.getBooleanValue();
      return value ? LiaStar.mkTrue(false) : LiaStar.mkFalse(false);
    }
    if (t.isIntegerValue())
    {
      BigInteger value = t.getIntegerValue();
      return LiaStar.mkConst(false, value.longValue());
    }
    Kind k = t.getKind();
    if (k == Kind.CONSTANT || k == Kind.VARIABLE)
    {
      return symbolTable.peek().get(t.toString());
    }
    if (k == Kind.STAR_CONTAINS)
    {
      Term lambdaTerm = t.getChild(0);
      Term cvc5BoundVariables = lambdaTerm.getChild(0);
      Term cvc5Body = lambdaTerm.getChild(1);
      List<String> outerVector = new ArrayList();
      LiaStar formula = null;
      for (int i = 1; i < t.getNumChildren(); i++)
      {
        Term child = t.getChild(i);
        if (child.isIntegerValue())
        {
          String varName = "const_" + index;
          index++;
          LiaStar newVar = LiaStar.mkVar(false, varName);
          BigInteger value = child.getIntegerValue();
          LiaStar liaStarConst = LiaStar.mkConst(false, value.longValue());
          LiaStar equality = LiaStar.mkEq(false, newVar, liaStarConst);
          if (formula == null)
          {
            formula = equality;
          }
          else
          {
            formula = LiaStar.mkAnd(false, formula, equality);
          }
          outerVector.add(varName);
        }
        else if (child.getKind() == Kind.CONSTANT)
        {
          outerVector.add(child.toString());
        }
        else
        {
          String message = "Unsupported Kind: " + child.getKind() + " in term: " + child;
          throw new UnsupportedOperationException(message);
        }
      }
      List<String> innerVector = new ArrayList();
      Map<String, LiaStar> symbols = new HashMap<>();
      for (int i = 0; i < cvc5BoundVariables.getNumChildren(); i++)
      {
        String varName = cvc5BoundVariables.getChild(i).toString();
        String freshName = "inner_" + index + "_" + varName;
        index++;
        innerVector.add(freshName);
        symbols.put(varName, LiaStar.mkVar(false, freshName));
      }
      symbolTable.push(symbols);
      LiaStar body = translateTerm(cvc5Body);
      symbolTable.pop();

      LiaStar star = LiaStar.mkSum(false, outerVector, innerVector, body);
      if (formula == null)
      {
        return star;
      }
      else
      {
        return LiaStar.mkAnd(false, formula, star);
      }
    }
    if (k == Kind.APPLY_UF)
    {
      // uninterpreted function application: child 0 is the function symbol,
      // the remaining children are its (integer) arguments
      List<LiaStar> args = new ArrayList<>();
      for (int i = 1; i < t.getNumChildren(); i++)
      {
        args.add(translateTerm(t.getChild(i)));
      }
      return LiaStar.mkFunc(false, t.getChild(0).toString(), args, true);
    }
    List<LiaStar> children = new ArrayList<>();
    for (int i = 0; i < t.getNumChildren(); i++)
    {
      LiaStar sqlTerm = translateTerm(t.getChild(i));
      children.add(sqlTerm);
    }
    switch (k)
    {
      case EQUAL ->
      {
        return LiaStar.mkEq(false, children.get(0), children.get(1));
      }
      case DISTINCT ->
      {
        var equal = LiaStar.mkEq(false, children.get(0), children.get(1));
        return LiaStar.mkNot(false, equal);
      }
      case NOT ->
      {
        return LiaStar.mkNot(false, children.get(0));
      }
      case AND ->
      {
        return LiaStar.mkConjunction(false, children);
      }
      case OR ->
      {
        return LiaStar.mkDisjunction(false, children);
      }
      case IMPLIES ->
      {
        assert (children.size() == 2);
        return LiaStar.mkImplies(false, children.get(0), children.get(1));
      }
      case ITE ->
      {
        assert (children.size() == 3);
        return LiaStar.mkIte(false, children.get(0), children.get(1), children.get(2));
      }
      case ADD ->
      {
        var addition = LiaStar.mkPlus(false, children.get(0), children.get(1));
        for (int i = 2; i < t.getNumChildren(); i++)
        {
          addition = LiaStar.mkPlus(false, addition, children.get(i));
        }
        return addition;
      }
      case SUB ->
      {
        assert (children.size() == 2);
        LiaStar a = children.get(0);
        LiaStar b = LiaStar.mkMul(false, minusOne, children.get(1));
        return LiaStar.mkPlus(false, a, b);
      }
      case MULT ->
      {
        assert (children.size() == 2);
        return LiaStar.mkMul(false, children.get(0), children.get(1));
      }
      case LT ->
      {
        assert (children.size() == 2);
        return LiaStar.mkLt(false, children.get(0), children.get(1));
      }
      case LEQ ->
      {
        assert (children.size() == 2);
        return LiaStar.mkLe(false, children.get(0), children.get(1));
      }
      case GT ->
      {
        assert (children.size() == 2);
        return LiaStar.mkLt(false, children.get(1), children.get(0));
      }
      case GEQ ->
      {
        assert (children.size() == 2);
        return LiaStar.mkLe(false, children.get(1), children.get(0));
      }
      default ->
      {
        break;
      }
    }
    String message = "Unsupported Kind: " + k + " in term: " + t;
    throw new UnsupportedOperationException(message);
  }
}