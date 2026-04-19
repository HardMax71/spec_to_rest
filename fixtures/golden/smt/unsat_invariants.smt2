(set-logic ALL)
(set-option :produce-models true)
(set-option :timeout 30000)
;; assertions
(assert (>= 1 10))
(assert (<= 1 5))
(assert (and (>= 1 10) (<= 1 5)))
(check-sat)