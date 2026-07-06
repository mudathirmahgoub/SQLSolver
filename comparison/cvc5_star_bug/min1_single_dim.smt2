; closure of {2} is {0,2,4,...}; 1 is odd -> must be UNSAT. cvc5 (liastar) returns SAT.
(set-logic HO_ALL)
(declare-const x Int)
(assert (and (= x 1) (int.star-contains (lambda ((a Int)) (= a 2)) x)))
(check-sat)
