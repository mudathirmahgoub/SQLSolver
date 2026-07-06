; every (a,b) has a=b, so every sum has comp1=comp2; (1,0) differs -> must be UNSAT. cvc5 returns SAT.
(set-logic HO_ALL)
(declare-const x Int)
(declare-const y Int)
(assert (and (= x 1) (= y 0) (int.star-contains (lambda ((a Int)(b Int)) (= a b)) x y)))
(check-sat)
