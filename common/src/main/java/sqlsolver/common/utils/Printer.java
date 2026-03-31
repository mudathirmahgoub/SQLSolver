package sqlsolver.common.utils;

import java.io.FileOutputStream;
import java.io.PrintStream;

public class Printer
{
  public static PrintStream output;
  static
  {
    try
    {
      output = new PrintStream(new FileOutputStream("output.txt"));
    }
    catch (Exception e)
    {     
      e.printStackTrace();
    }
  }
}
