(set-logic ALL)
(set-option :produce-models true)
(set-option :timeout 30000)
;; funcs
(declare-fun state_count () Int)
;; assertions
(assert (>= state_count 0))
(check-sat)