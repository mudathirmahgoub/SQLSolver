package sqlsolver.superopt.util;

import sqlsolver.common.utils.Printer;
import sqlsolver.superopt.logic.LogicSupport;

public class PrettyPrinter extends AbstractPrettyPrinter
{
  @Override
  protected void printString(String str)
  {
    Printer.output.print(str);
  }

  @Override
  protected void printNewLine()
  {
    Printer.output.println();
  }
}
