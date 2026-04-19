(set-logic ALL)
(set-option :produce-models true)
(set-option :timeout 30000)
;; sorts
(declare-sort Float 0)
(declare-sort Status 0)
(declare-sort String 0)
(declare-sort Widget 0)
;; funcs
(declare-fun Status_ACTIVE () Status)
(declare-fun Status_INACTIVE () Status)
(declare-fun Widget_name (Widget) String)
(declare-fun Widget_weight (Widget) Float)
(declare-fun widgets_dom (Int) Bool)
(declare-fun widgets_map (Int) Widget)
;; assertions
(assert (not (= Status_ACTIVE Status_INACTIVE)))
(assert (forall ((k_widgets Int)) (widgets_dom k_widgets)))
(check-sat)