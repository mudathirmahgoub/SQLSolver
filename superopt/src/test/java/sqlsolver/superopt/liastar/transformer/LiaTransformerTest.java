package sqlsolver.superopt.liastar.transformer;

import org.junit.jupiter.api.Test;
import sqlsolver.sql.plan.Value;
import sqlsolver.superopt.liastar.LiaStar;
import sqlsolver.superopt.liastar.LiaVarImpl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LiaTransformerTest
{
    private final LiaStar x = LiaStar.mkVar(false, "x", Value.TYPE_INT);
    private final LiaStar y = LiaStar.mkVar(false, "y", Value.TYPE_INT);
    private final LiaStar c17 = LiaStar.mkConst(false, 17);
    private final LiaStar c0 =  LiaStar.mkConst(false, 0);
    private final LiaStar c5 = LiaStar.mkConst(false, 5);
    private final LiaStar c2 =  LiaStar.mkConst(false, 2);
    private final LiaStar c3 =  LiaStar.mkConst(false, 3);
    private final LiaStar c6 =  LiaStar.mkConst(false, 6);
    private final LiaStar c8 =  LiaStar.mkConst(false, 8);
    private final LiaStar c20 =  LiaStar.mkConst(false, 20);
    private final LiaStar c47 =  LiaStar.mkConst(false, 47);

    @Test
    public void simpleFormula ()
    {
        var cMinus1 =  LiaStar.mkConst(false, -1);

        // 5x + 2y >= 17 ∧ 3x − y ≤ 8 ∧ 2x + 3y ≤ 20
        // 5x + 2 y >= 17
        var f2Inequility1 = LiaStar.mkLe(false,
                c17,
                LiaStar.mkPlus(false,
                        LiaStar.mkMul(false,c5, x ),
                        LiaStar.mkMul(false, c2, y)));
        // 3x − y ≤ 8
        var f2Inequility2 = LiaStar.mkLe(false,
                LiaStar.mkPlus(false,
                        LiaStar.mkMul(false,c3, x ),
                        LiaStar.mkMul(false, cMinus1, y)),
                c8);
        // 2x + 3y ≤ 20
        var f2Inequility3= LiaStar.mkLe(false,
                LiaStar.mkPlus(false,
                        LiaStar.mkMul(false,c2, x ),
                        LiaStar.mkMul(false, c3, y)),
                c20);

        var f2 = LiaStar.mkConjunction(false,Arrays.asList(f2Inequility1,f2Inequility2,f2Inequility3));
        System.out.println("f2: " + f2);
        SlsAugmenter augmenter = new SlsAugmenter(Arrays.asList(new String[]{"x", "y"}), f2);
        System.out.println("sls: " + augmenter.sls() );
        while(augmenter.augment())
        {
            System.out.println("sls: " + augmenter.sls() );
        }
    }

   @Test
    public void liastarPaperExample ()
    {
        LiaTransformer transformer = new LiaTransformer();
        LiaStar g = null;
        List<String> v = Arrays.asList(new String[]{"x", "y"});

        var cMinus1 =  LiaStar.mkConst(false, -1);
        // y + 2x ≥ 17 ∧ 6x − y ≤ 47
        // y + 2x >= 17
        var f1Inequality1 =  LiaStar.mkLe(false,
                c17,
                LiaStar.mkPlus(false,
                        LiaStar.mkMul(false,c2, x ),
                        y));
        // 6x − y ≤ 47
        var f1Inequality2 =  LiaStar.mkLe(false,
                LiaStar.mkPlus(false,
                        LiaStar.mkMul(false,c6, x ),
                        LiaStar.mkMul(false,cMinus1, y )),
                c47);
        var f1 = LiaStar.mkConjunction(false, Arrays.asList(f1Inequality1, f1Inequality2));
        System.out.println("f1: " + f1);
        // 5x + 2y >= 17 ∧ 3x − y ≤ 8 ∧ 2x + 3y ≤ 20
        // 5x + 2 y >= 17
        var f2Inequility1 = LiaStar.mkLe(false,
                c17,
                LiaStar.mkPlus(false,
                        LiaStar.mkMul(false,c5, x ),
                        LiaStar.mkMul(false, c2, y)));
        // 3x − y ≤ 8
        var f2Inequility2 = LiaStar.mkLe(false,
                LiaStar.mkPlus(false,
                        LiaStar.mkMul(false,c3, x ),
                        LiaStar.mkMul(false, cMinus1, y)),
                c8);
        // 2x + 3y ≤ 20
        var f2Inequility3= LiaStar.mkLe(false,
                LiaStar.mkPlus(false,
                        LiaStar.mkMul(false,c2, x ),
                        LiaStar.mkMul(false, c3, y)),
                c20);      

        var f2 = LiaStar.mkConjunction(false,Arrays.asList(f2Inequility1,f2Inequility2,f2Inequility3));
        f2.updateInnerStar(true);
        System.out.println("f2: " + f2);
        LiaStar s = null;
        SlsAugmenter augmenter = new SlsAugmenter(Arrays.asList(new String[]{"a", "b"}), f2);
        while(augmenter.augment())
        {
            continue;
        }
        System.out.println("sls: " + augmenter.sls() );
//        var lia = transformer.transform(f1,v,v,f2,s);
//        System.out.println("lia: " + lia);
    }

    @Test
    public void liastarSQLSolverExample ()
    {
        LiaTransformer transformer = new LiaTransformer();
        LiaStar g = null;
        List<String> v = Arrays.asList(new String[]{"x", "y"});
        var v1 = LiaStar.mkVar(false, "v1", Value.TYPE_INT);
        var v2 = LiaStar.mkVar(false, "v2", Value.TYPE_INT);
        var v3 = LiaStar.mkVar(false, "v3", Value.TYPE_INT);
        var x1 = LiaStar.mkVar(false, "x1", Value.TYPE_INT);
        var x2 = LiaStar.mkVar(false, "x2", Value.TYPE_INT);
        var x3 = LiaStar.mkVar(false, "x3", Value.TYPE_INT);
        var x4 = LiaStar.mkVar(false, "x4", Value.TYPE_INT);
        var x5 = LiaStar.mkVar(false, "x5", Value.TYPE_INT);
        var x6 = LiaStar.mkVar(false, "x6", Value.TYPE_INT);
        var x7 = LiaStar.mkVar(false, "x7", Value.TYPE_INT);
        var x8 = LiaStar.mkVar(false, "x8", Value.TYPE_INT);
        var c1000 = LiaStar.mkConst(false, 1000);
        var c500 = LiaStar.mkConst(false, 500);
        var c0 = LiaStar.mkConst(false, 0);
        var x4EqX5 = LiaStar.mkNeq(false, x4,x5);
        var x7Leq1000 = LiaStar.mkLe(false, x7,c1000);
        var x7Gt1000 = LiaStar.mkLt(false, c1000, x7);
        var x8Lt500 = LiaStar.mkLt(false, x8,c500);
        var condition1 = LiaStar.mkConjunction(false, Arrays.asList(x4EqX5, x7Leq1000));
        var ite1 = LiaStar.mkIte(false, condition1, x6, c0);
        var condition2 = LiaStar.mkConjunction(false, Arrays.asList(x4EqX5, x7Gt1000,x8Lt500));
        var ite2 = LiaStar.mkIte(false, condition2, x6, c0);
        var condition3 = LiaStar.mkConjunction(false, Arrays.asList(x4EqX5,
                LiaStar.mkDisjunction(false, Arrays.asList(x7Leq1000,x8Lt500))
                ));
        var ite3 = LiaStar.mkIte(false, condition3, x6, c0);
        var eq1 = LiaStar.mkEq(false, x1,ite1);
        var eq2 = LiaStar.mkEq(false, x2,ite2);
        var eq3 = LiaStar.mkEq(false, x3,ite3);
        var f2 = LiaStar.mkConjunction(false,Arrays.asList(eq1, eq2, eq3));

        f2.updateInnerStar(true);
        System.out.println("f2: " + f2);
        var f1 = LiaStar.mkNeq(false, LiaStar.mkPlus(false, v1, v2), v3);
        LiaStar s = null;
        System.out.println("f1: " + f1);
        System.out.println("f2: " + f1);
        var lia = transformer.transform(f1,v,v,f2,s);
        System.out.println("lia: " + lia);

    }
}