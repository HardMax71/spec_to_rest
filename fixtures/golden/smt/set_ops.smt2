(set-logic ALL)
(set-option :produce-models true)
(set-option :timeout 30000)
;; funcs
(declare-fun counters_dom (Int) Bool)
(declare-fun counters_map (Int) Int)
;; assertions
(assert (forall ((id Int)) (=> (counters_dom id) (> id 0))))
(assert (forall ((id Int)) (=> (counters_dom id) (or (= (counters_map id) 0) (= (counters_map id) 1) (= (counters_map id) 2) (= (counters_map id) 3) (= (counters_map id) 5)))))
(check-sat)
