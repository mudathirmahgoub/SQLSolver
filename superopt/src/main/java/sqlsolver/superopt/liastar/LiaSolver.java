package sqlsolver.superopt.liastar;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Context;
import com.microsoft.z3.Expr;
import com.microsoft.z3.FuncDecl;
import com.microsoft.z3.IntExpr;
import com.microsoft.z3.Model;
import com.microsoft.z3.Solver;
import com.microsoft.z3.Sort;
import com.microsoft.z3.Status;

import sqlsolver.common.utils.Printer;
import sqlsolver.superopt.liastar.parameter.InwardParamRemover;
import sqlsolver.superopt.logic.LogicSupport;
import sqlsolver.superopt.logic.SqlSolver;
import sqlsolver.superopt.uexpr.PredefinedFunctions;
import sqlsolver.superopt.util.Timeout;
import sqlsolver.superopt.util.Z3Support;

public class LiaSolver
{
  public static final String CONFIG_KEY_PARAM_REMOVAL_MODE = "PARAM_REMOVAL_MODE";
  public static final String CONFIG_VALUE_PARAM_REMOVAL_MODE_INWARD = "INWARD";
  public static final String CONFIG_VALUE_PARAM_REMOVAL_MODE_OUTWARD = "OUTWARD";

  /**
   * Which solver decides the linear LIA* formula that {@link #checkOverapp} arrives at:
   * {@link #CONFIG_VALUE_BACKEND_SQLSOLVER} (the default) eliminates the stars in SQLSolver
   * itself and calls z3, {@link #CONFIG_VALUE_BACKEND_CVC5} hands the star formula to cvc5.
   * Unset falls back to the {@value #PROPERTY_BACKEND} system property, so callers that do
   * not build the configuration themselves can still select the backend.
   */
  public static final String CONFIG_KEY_BACKEND = "LIASTAR_BACKEND";
  public static final String CONFIG_VALUE_BACKEND_SQLSOLVER = "SQLSOLVER";
  public static final String CONFIG_VALUE_BACKEND_CVC5 = "CVC5";
  public static final String PROPERTY_BACKEND = "sqlsolver.liastar.backend";

  /**
   * Wall-clock budget in millis for one cvc5 query, passed on as cvc5's own {@code tlimit}.
   * A cvc5 check runs in native code where an interrupt from the caller's watchdog does not
   * reach it, so without this a hard formula keeps a core busy after its benchmark was
   * abandoned. Unset falls back to the {@value #PROPERTY_CVC5_TLIMIT} system property, and
   * then to no limit.
   */
  public static final String CONFIG_KEY_CVC5_TLIMIT_MILLIS = "CVC5_TLIMIT_MILLIS";
  public static final String PROPERTY_CVC5_TLIMIT = "sqlsolver.liastar.cvc5.tlimit";

  private final Properties config;
  private final LiaStar liaFormula;

  /**
   * Solve satisfiability of a LIA* formula with certain configuration.
   * @param f the formula to solve
   * @param config the configuration; it contains several keys: <ul>
   *               {@code PARAM_REMOVAL_MODE} - which strategy to use during removal of parameters
   * </ul>
   * @return the satisfiability result
   */
  public static LiaSolverStatus solveWithConfig(LiaStar f, Properties config)
  {
    return new LiaSolver(config, f).solve();
  }

  public LiaSolver(Properties config, LiaStar f)
  {
    this.config = config;
    liaFormula = f;
  }

  /** Whether this solver decides the linear LIA* formula with cvc5 rather than with
   * SQLSolver's own star elimination; see {@link #CONFIG_KEY_BACKEND}. */
  private boolean useCvc5Backend()
  {
    final String backend =
        config.getProperty(CONFIG_KEY_BACKEND, System.getProperty(PROPERTY_BACKEND, ""));
    return backend.equalsIgnoreCase(CONFIG_VALUE_BACKEND_CVC5);
  }

  /** The cvc5 tlimit in millis; see {@link #CONFIG_KEY_CVC5_TLIMIT_MILLIS}. */
  private long cvc5TimeoutMillis()
  {
    final String millis = config.getProperty(
        CONFIG_KEY_CVC5_TLIMIT_MILLIS, System.getProperty(PROPERTY_CVC5_TLIMIT, "0"));
    try
    {
      return Long.parseLong(millis.trim());
    }
    catch (NumberFormatException e)
    {
      return 0;
    }
  }

  public LiaSolverStatus solve()
  {
    try
    {
      String result = checkUnderapp();
      if (result.equals("SAT"))
        return LiaSolverStatus.SAT;
    }
    catch (Exception e)
    {
    }

    try
    {
      String result = checkOverapp();
      if (result.equals("UNSAT"))
        return LiaSolverStatus.UNSAT;
      // SQLSolver's own star elimination may over-approximate, so a "SAT" from it says
      // nothing about the input formula and is dropped. cvc5 decides the star formula
      // itself, and the cvc5 path reports sat only when both the encoding and the steps
      // that produced the formula preserve satisfiability (see isSatPreserving), so there
      // the model is genuine.
      if (result.equals("SAT") && useCvc5Backend())
        return LiaSolverStatus.SAT;
      return LiaSolverStatus.UNKNOWN;
    }
    catch (Exception e)
    {
      Timeout.bypassTimeout(e);
      if (LogicSupport.dumpLiaFormulas)
      {
        e.printStackTrace();
      }
      return LiaSolverStatus.UNKNOWN;
    }
  }

  String checkUnderapp()
  {
    try
    {
      LiaStar curexp = liaFormula.deepcopy();
      curexp = LiaStar.calculateUnderApprox(curexp, curexp.embeddingLayers() > 4 ? 1 : 2);
      return solveLia(curexp);
    }
    catch (Exception e)
    {
      return "UNKNOWN";
    }
  }

  String checkOverapp() throws Exception
  {
    if (LogicSupport.dumpLiaFormulas)
      Printer.output.println("init: " + liaFormula);

    LiaStar tmpFormula = liaFormula.deepcopy();
    tmpFormula = tmpFormula.pushUpParameter(new HashSet<>());
    if (LogicSupport.dumpLiaFormulas)
      Printer.output.println("pushed up param: " + tmpFormula);
    final String removeParamMode = config.getProperty(CONFIG_KEY_PARAM_REMOVAL_MODE);
    if (removeParamMode.equals(CONFIG_VALUE_PARAM_REMOVAL_MODE_INWARD))
    {
      tmpFormula = InwardParamRemover.removeParameter(tmpFormula);
    }
    else if (removeParamMode.equals(CONFIG_VALUE_PARAM_REMOVAL_MODE_OUTWARD))
    {
      tmpFormula = tmpFormula.removeParameter();
    }
    if (LogicSupport.dumpLiaFormulas)
      Printer.output.println("remove param (mode: " + removeParamMode + "): " + tmpFormula);

    tmpFormula.simplifyMult(new HashMap<>());
    tmpFormula.mergeMult(new HashMap<>());
    if (LogicSupport.dumpLiaFormulas)
      Printer.output.println("remove multiplication: " + tmpFormula);

    // Parameters are gone, so every star body is closed, and multiplications have been
    // abstracted into fresh variables. One thing still separates the formula from cvc5's
    // fragment: int.star-contains takes a star whose body is star-free linear arithmetic, so
    // a star nested inside another star's body is not expressible. Reduce the nesting with
    // SQLSolver's own elimination first, leaving single star constraints -- any number of
    // sibling stars is fine -- and hand those to cvc5.
    if (useCvc5Backend())
    {
      // Nesting is the one thing SQLSolver can cheaply remove for cvc5: eliminating the inner
      // stars leaves single star constraints, which is the shape int.star-contains takes.
      LiaStar candidate = tmpFormula;
      final boolean nested = candidate.embeddingLayers() > 1;
      if (nested)
      {
        // measured to validate the fragment test: without this reduction, would the
        // whitelist below have rejected the formula?
        Cvc5LiaStarBackend.countNestingReduced();
        if (!isInCvc5Fragment(candidate))
          Cvc5LiaStarBackend.countOutOfFragmentBeforeReduction();
        candidate = flattenNestedStars(candidate);
        if (LogicSupport.dumpLiaFormulas)
          Printer.output.println("nested stars eliminated: " + candidate);
      }
      // cvc5 is only consulted on formulas it can actually take; anything else stays with
      // SQLSolver, so switching the backend can add answers but never remove them.
      if (isInCvc5Fragment(candidate))
      {
        // the nesting elimination reuses the semi-linear-set construction, which falls back
        // to an over-approximation, so where nesting was present only unsat carries back
        final LiaSolverStatus status = Cvc5LiaStarBackend.solve(
            candidate, cvc5TimeoutMillis(), isSatPreserving(liaFormula) && !nested);
        if (status == LiaSolverStatus.UNSAT)
          return "UNSAT";
        if (status == LiaSolverStatus.SAT)
          return "SAT";
        // cvc5 could not decide it: fall through and let SQLSolver try
        Cvc5LiaStarBackend.countUndecided();
      }
      else
      {
        Cvc5LiaStarBackend.countOutOfFragment();
        if (LogicSupport.dumpLiaFormulas)
          Printer.output.println("outside cvc5's fragment; solving with SQLSolver");
      }
    }

    return solveNestedLiastar(tmpFormula);
  }

  /**
   * Whether cvc5 can take this formula. cvc5 decides a star by translating its lambda body,
   * and that translation only covers linear integer arithmetic: at the boolean level
   * {@code AND OR NOT ITE} and the comparisons, at the term level variables, integer
   * constants, negation, addition, subtraction and multiplication by a constant
   * ({@code LiaStarUtils::removeItes} / {@code removeIntegerItes}). Anything else in a star
   * body -- another star, an uninterpreted function, a division, or a product of two
   * non-constant terms -- makes cvc5 abort with "Unexpected kind", so such a formula is left
   * to SQLSolver's own solver. Outside star bodies nothing is restricted: cvc5's arithmetic
   * and UF solvers handle the surrounding formula.
   */
  private static boolean isInCvc5Fragment(LiaStar f)
  {
    final boolean[] ok = {true};
    f.transformPostOrder(lia -> {
      if (lia instanceof LiaSumImpl sum && !isLinearStarBody(sum.constraints))
        ok[0] = false;
      return lia;
    });
    return ok[0];
  }

  /** Whether a star body stays inside the linear fragment described on
   * {@link #isInCvc5Fragment}; a whitelist, so an unforeseen node type is treated as
   * unsupported rather than crashing cvc5. */
  private static boolean isLinearStarBody(LiaStar body)
  {
    final boolean[] ok = {true};
    body.transformPostOrder(lia -> {
      if (lia instanceof LiaConstImpl || lia instanceof LiaVarImpl || lia instanceof LiaAndImpl
          || lia instanceof LiaOrImpl || lia instanceof LiaNotImpl || lia instanceof LiaEqImpl
          || lia instanceof LiaLeImpl || lia instanceof LiaLtImpl || lia instanceof LiaPlusImpl
          || lia instanceof LiaIteImpl)
      {
        return lia;
      }
      if (lia instanceof LiaMulImpl mul)
      {
        // only multiplication by a constant is linear
        if (!(mul.operand1 instanceof LiaConstImpl) && !(mul.operand2 instanceof LiaConstImpl))
          ok[0] = false;
        return lia;
      }
      if (lia instanceof LiaFuncImpl func)
      {
        // MINUS is emitted as native subtraction; every other function is uninterpreted
        if (!PredefinedFunctions.MINUS.contains(func.funcName, func.vars.size()))
          ok[0] = false;
        return lia;
      }
      // nested star, division, string, or anything unforeseen
      ok[0] = false;
      return lia;
    });
    return ok[0];
  }

  /**
   * Eliminates stars that sit inside another star's body, so that every remaining star has a
   * star-free (linear) body -- the shape {@code int.star-contains} accepts. Sibling stars are
   * left in place; only nesting is outside cvc5's fragment.
   *
   * <p>The elimination is {@code LiaStar.expandStar}, the same semi-linear-set construction
   * SQLSolver's own backend applies to every star, and it falls back to an over-approximation
   * when the equivalent construction fails -- hence the caller only trusts unsat for a formula
   * that needed this.
   */
  private static LiaStar flattenNestedStars(LiaStar f)
  {
    return f.transformPostOrder(lia -> {
      if (lia instanceof LiaSumImpl sum && !sum.constraints.isLia())
      {
        sum.constraints = sum.constraints.expandStar();
      }
      return lia;
    });
  }

  /**
   * Whether the steps {@link #checkOverapp} takes before handing the formula over preserve
   * satisfiability, so that a model cvc5 finds is a model of {@code original} too. Unsat needs
   * no such check: every step only ever weakens the formula, so unsat always transfers back --
   * which is exactly why this path is named after an over-approximation and why SQLSolver's own
   * backend discards its "sat".
   *
   * <p>Two of the steps weaken. {@code removeParameter} can fall back to decoupling a shared
   * parameter into per-summand inner vars ({@code LiaSumImpl.removeParameterFallback}, "a lossy
   * over-approximation"), and {@code simplifyMult}/{@code mergeMult} abstract a nonlinear term
   * into a fresh variable constrained only by a few implications. Both are ruled out here by the
   * absence of their trigger in the input -- no star parameter, no nonlinear term -- which is
   * cheaper and thread-safe, unlike reading the transformations' own static fallback flag.
   */
  private static boolean isSatPreserving(LiaStar original)
  {
    return Cvc5LiaStarSolver.collectStarParams(original).isEmpty()
        && !containsNonLinearTerm(original);
  }

  /**
   * Whether the formula multiplies or divides two non-constant terms. Such a term is what
   * {@code mergeMult}/{@code simplifyMult} abstract away; division counts as well because
   * {@link #solveLia} additionally constrains it to be exact (see
   * {@link #appendMultipleConditions}) where the cvc5 encoding does not.
   */
  private static boolean containsNonLinearTerm(LiaStar f)
  {
    final boolean[] found = {false};
    f.transformPostOrder(lia -> {
      if (lia instanceof LiaDivImpl)
      {
        found[0] = true;
      }
      else if (lia instanceof LiaMulImpl mul && !(mul.operand1 instanceof LiaConstImpl)
          && !(mul.operand2 instanceof LiaConstImpl))
      {
        found[0] = true;
      }
      return lia;
    });
    return found[0];
  }

  /** forall t1 t2. ((isnull(t1)<>0) /\ (isnull(t2)<>0)) -> t1 = t2 */
  private BoolExpr ruleNullEquals(Context ctx)
  {
    String strVar1 = "t1", strVar2 = "t2", strIsNull = "IsNull";
    Expr[] vars = new Expr[2];
    vars[0] = ctx.mkIntConst(strVar1);
    vars[1] = ctx.mkIntConst(strVar2);

    final Sort I = ctx.getIntSort();
    final FuncDecl func = ctx.mkFuncDecl(strIsNull, I, I);
    Expr isNull1 = ctx.mkApp(func, vars[0]);
    Expr isNull2 = ctx.mkApp(func, vars[1]);
    BoolExpr notNull1 = ctx.mkNot(ctx.mkEq(isNull1, ctx.mkInt(0)));
    BoolExpr notNull2 = ctx.mkNot(ctx.mkEq(isNull2, ctx.mkInt(0)));
    BoolExpr eq = ctx.mkEq(vars[0], vars[1]);

    Expr body = ctx.mkImplies(ctx.mkAnd(notNull1, notNull2), eq);
    return ctx.mkForall(vars, body, 1, null, null, null, null);
  }

  private BoolExpr appendRules(Context ctx, BoolExpr target)
  {
    target = ctx.mkAnd(ruleNullEquals(ctx), target);
    return target;
  }

  private Set<BoolExpr> getMultipleConditions(Context ctx, Expr expr)
  {
    Set<BoolExpr> conditions = new HashSet<>();
    if (!expr.isApp())
      return conditions;

    Expr[] args = expr.getArgs();
    if (expr.isIDiv() && args[0] instanceof IntExpr i0 && args[1] instanceof IntExpr i1)
    {
      conditions.add(ctx.mkEq(ctx.mkMod(i0, i1), ctx.mkInt(0)));
    }
    // recursion
    for (Expr sub : args)
    {
      conditions.addAll(getMultipleConditions(ctx, sub));
    }
    return conditions;
  }

  // upon occurrence of "div u1 u2": append "= (mod u1 u2) 0"
  // this restricts valid division between integers
  private BoolExpr appendMultipleConditions(Context ctx, BoolExpr target)
  {
    Set<BoolExpr> conditions = getMultipleConditions(ctx, target);
    for (BoolExpr condition : conditions)
    {
      target = ctx.mkAnd(condition, target);
    }
    return target;
  }

  String solveLia(LiaStar f)
  {
    try (final Context ctx = new Context())
    {
      BoolExpr target = ctx.mkTrue();

      Set<LiaVarImpl> vars = new HashSet<>();
      f.transformPostOrder(lia -> {
        if (lia instanceof LiaVarImpl var)
        {
          vars.add(var);
        }
        return lia;
      });
      Map<String, Expr> varDef = new HashMap<>();
      final BoolExpr varConstraints = Z3Support.defineVarsByVars(ctx, varDef, vars);
      target = ctx.mkAnd(target, varConstraints);

      BoolExpr coreExpr = (BoolExpr) f.transToSMT(ctx, varDef);
      coreExpr = appendMultipleConditions(ctx, coreExpr);
      target = ctx.mkAnd(target, coreExpr);

      // the formula f does not contain stars
      // append rules applicable to f
      target = appendRules(ctx, target);

      Solver s = (f.toString().contains(PredefinedFunctions.NAME_SQRT))
          ? ctx.mkSolver()
          : ctx.mkSolver(ctx.tryFor(ctx.mkTactic("qflia"), SqlSolver.Z3_TIMEOUT));
      //       Solver s = ctx.mkSolver();
      s.add(target);

      if (LogicSupport.dumpLiaFormulas)
      {
        Printer.output.println("FOL: " + s);
      }

      Status q = s.check();
      if (LogicSupport.dumpLiaFormulas)
      {
        Printer.output.println("smt solver: " + q.toString());
      }
      if(q == Status.SATISFIABLE)
      {
        Model model = s.getModel();
        System.out.println("z3 Model: \n" + model);
      }
      return switch (q)
      {
        case UNKNOWN -> "UNKNOWN";
        case SATISFIABLE -> "SAT";
        case UNSATISFIABLE -> "UNSAT";
      };
    }
  }

  String solveNestedLiastar(LiaStar f) throws Exception
  {
    if (LogicSupport.dumpLiaFormulas)
      Printer.output.println("liastar: " + f.toString());
    f = f.expandStar();

    if (LogicSupport.dumpLiaFormulas)
    {
      Printer.output.println("lia: " + f.toString());
      Printer.output.println("#variables in LIA without *: " + f.getVars().size());
    }

    return solveLia(f);
  }
}
