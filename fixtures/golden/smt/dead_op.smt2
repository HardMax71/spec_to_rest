(set-logic ALL)
(set-option :produce-models true)
(set-option :timeout 30000)
;; sorts
(declare-sort E 0)
;; funcs
(declare-fun E_n (E) Int)
(declare-fun state_x () Int)
(check-sat)
