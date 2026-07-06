; relation is empty (body false); closure = {(0,0)} only; (1,0) -> must be UNSAT. cvc5 returns SAT.
(set-logic HO_ALL)
(declare-const x Int)(declare-const y Int)
(assert (and (= x 1) (= y 0) (int.star-contains (lambda ((a Int)(b Int)) false) x y)))
(check-sat)
