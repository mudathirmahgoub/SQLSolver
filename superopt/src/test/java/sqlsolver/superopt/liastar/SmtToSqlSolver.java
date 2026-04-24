package sqlsolver.superopt.liastar;

import io.github.cvc5.Command;
import io.github.cvc5.InputParser;
import io.github.cvc5.Solver;
import io.github.cvc5.SymbolManager;
import io.github.cvc5.Term;
import io.github.cvc5.TermManager;
import io.github.cvc5.modes.InputLanguage;

public class SmtToSqlSolver
{
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

    LiaStar formula = LiaStar.mkTrue(false);
    for (Term assertion : slv.getAssertions())
    {
      LiaStar liaStar = translateTerm(assertion);
      formula = LiaStar.mkAnd(false, formula, liaStar);
    }
    return formula;
  }

  private LiaStar translateTerm(Term assertion)
  {
    return LiaStar.mkTrue(false);
  }
}