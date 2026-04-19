(set-logic ALL)
(set-option :produce-models true)
(set-option :timeout 30000)
;; funcs
(declare-fun state_x () Int)
;; assertions
(assert (> state_x 100))
(check-sat)