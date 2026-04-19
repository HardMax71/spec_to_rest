(set-logic ALL)
(set-option :produce-models true)
(set-option :timeout 30000)
;; sorts
(declare-sort UrlMapping 0)
;; funcs
(declare-fun metadata_dom (Int) Bool)
(declare-fun metadata_map (Int) UrlMapping)
(declare-fun UrlMapping_click_count (UrlMapping) Int)
;; assertions
(assert (forall ((c Int)) (=> (metadata_dom c) (>= (UrlMapping_click_count (metadata_map c)) 0))))
(check-sat)