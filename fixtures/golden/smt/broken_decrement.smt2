(set-logic ALL)
(set-option :produce-models true)
(set-option :timeout 30000)
;; funcs
(declare-fun state_clicks () Int)
;; assertions
(assert (>= state_clicks 0))
(check-sat)