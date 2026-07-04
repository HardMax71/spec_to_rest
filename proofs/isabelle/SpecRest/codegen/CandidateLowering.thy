theory CandidateLowering
  imports Strategies SpecRest_IR.IR_FreeVars
begin

text \<open>Fresh-output candidate lowering for the Dafny kernel contract.

  A refined-string output whose value the ensures constrain only through
  pre-state observations (freshness against a relation, distinctness against
  existing rows) cannot be constructed by a verified body: under the
  schema-derived state invariant an exact-length refinement makes the value
  space finite, so unconditional freshness is unprovable, and any provable
  construction would be deterministic and therefore predictable, which is
  wrong for secrets regardless. The environment must sample such values and
  the kernel must validate them.

  The lowering turns each such output position into an extra input (the
  candidate): the position's movable ensures conjuncts are copied into the
  requires with \<open>pre\<close> stripped and the position replaced by the candidate,
  and a glue conjunct \<open>position = candidate\<close> is appended to the ensures. A
  conjunct is movable when it mentions the position, references no primed
  state, and is independent of the let binders it sits under. Positions
  pinned by an ensures equality are never lowered.

  The sampler specification is derived here as well, so the emitters never
  interpret refinements themselves: \<open>samplerOf\<close> yields the exact length and
  the exact character set, and \<open>charset_exact\<close> ties the set to the class
  semantics in both directions.\<close>

subsection \<open>Character classes and the sampler\<close>

text \<open>The sampler works over natural-number codepoints so that HOL's \<open>char\<close>
  type never enters the export closure (its extracted equality and enum
  machinery are pathological for the generated Scala). The Scala boundary
  converts pattern strings to codepoint lists and the derived charset back,
  the same mechanical trust as every other literal crossing.\<close>

datatype class_item =
  CiChar nat
| CiRange nat nat

fun classSem :: "class_item list \<Rightarrow> nat \<Rightarrow> bool" where
  "classSem [] c = False"
| "classSem (CiChar x # items) c = (c = x \<or> classSem items c)"
| "classSem (CiRange lo hi # items) c = (lo \<le> c \<and> c \<le> hi \<or> classSem items c)"

fun expandItem :: "class_item \<Rightarrow> nat list" where
  "expandItem (CiChar x) = [x]"
| "expandItem (CiRange lo hi) = [lo ..< Suc hi]"

fun charsetOf :: "class_item list \<Rightarrow> nat list" where
  "charsetOf [] = []"
| "charsetOf (i # items) = expandItem i @ charsetOf items"

lemma in_expand_item:
  "c \<in> set (expandItem i) \<longleftrightarrow> classSem [i] c"
  by (cases i) auto

lemma charset_exact:
  "c \<in> set (charsetOf items) \<longleftrightarrow> classSem items c"
proof (induction items)
  case Nil then show ?case by simp
next
  case (Cons i items)
  then show ?case
    using in_expand_item[of c i] by (cases i) auto
qed

text \<open>Class body parsing: literal characters and \<open>a-z\<close> ranges only. Escapes,
  nested brackets, and negation are rejected; a rejected refinement simply
  keeps its unlowered contract. Codepoints: \<open>0x2D\<close> dash, \<open>0x5B\<close>/\<open>0x5D\<close>
  brackets, \<open>0x5C\<close> backslash, \<open>0x5E\<close> caret, \<open>0x24\<close> dollar, \<open>0x2B\<close> plus.\<close>

fun parseItems :: "nat list \<Rightarrow> class_item list option" where
  "parseItems [] = Some []"
| "parseItems (a # b # c # rest) =
     (if b = 0x2D
      then (if a \<le> c
            then map_option ((#) (CiRange a c)) (parseItems rest)
            else None)
      else map_option ((#) (CiChar a)) (parseItems (b # c # rest)))"
| "parseItems (a # rest) = map_option ((#) (CiChar a)) (parseItems rest)"

fun stripAnchors :: "nat list \<Rightarrow> nat list" where
  "stripAnchors cs =
     (let hd_stripped = (case cs of c # r \<Rightarrow> (if c = 0x5E then r else cs) | _ \<Rightarrow> cs)
      in (case rev hd_stripped of
            c # r \<Rightarrow> (if c = 0x24 then rev r else hd_stripped)
          | _ \<Rightarrow> hd_stripped))"

fun badClassChar :: "nat \<Rightarrow> bool" where
  "badClassChar c = (c = 0x5C \<or> c = 0x5B \<or> c = 0x5D \<or> c = 0x5E)"

fun anyBadClassChar :: "nat list \<Rightarrow> bool" where
  "anyBadClassChar [] = False"
| "anyBadClassChar (c # cs) = (badClassChar c \<or> anyBadClassChar cs)"

definition parseClassPlus :: "nat list \<Rightarrow> class_item list option" where
  "parseClassPlus pat =
     (case stripAnchors pat of
        c # rest \<Rightarrow>
          (if c = 0x5B
           then (case rev rest of
                   p # rb # body \<Rightarrow>
                     (if p = 0x2B \<and> rb = 0x5D \<and> body \<noteq> [] \<and>
                         \<not> anyBadClassChar (rev body)
                      then parseItems (rev body)
                      else None)
                 | _ \<Rightarrow> None)
           else None)
      | _ \<Rightarrow> None)"

definition defaultCharset :: "nat list" where
  "defaultCharset = [0x30 ..< 0x3A] @ [0x61 ..< 0x67]"

text \<open>\<open>samplerFor\<close> is the single source of truth for candidacy: the pattern
  codepoint lists arrive alongside the constraint (the Scala boundary
  explodes the very patterns the constraint carries; a count mismatch fails
  closed).\<close>

fun samplerFor :: "string_constraint \<Rightarrow> nat list list \<Rightarrow> (int \<times> nat list) option" where
  "samplerFor (StringConstraint mn mx pats fils exs) patCodes =
     (if fils \<noteq> [] \<or> exs \<noteq> [] \<or> length pats \<noteq> length patCodes then None
      else (case mn of
              None \<Rightarrow> None
            | Some n \<Rightarrow>
                (if n < 1 \<or> (case mx of None \<Rightarrow> False | Some m \<Rightarrow> m < n)
                 then None
                 else (case patCodes of
                         [] \<Rightarrow> Some (n, defaultCharset)
                       | [p] \<Rightarrow>
                           map_option
                             (\<lambda>items. (n, charsetOf items))
                             (parseClassPlus p)
                       | _ \<Rightarrow> None))))"

lemma samplerFor_charset_sound:
  assumes "samplerFor sc [p] = Some (n, cs)"
  assumes "c \<in> set cs"
  shows "\<exists>items. parseClassPlus p = Some items \<and> classSem items c"
proof -
  obtain mn mx pats fils exs where sc: "sc = StringConstraint mn mx pats fils exs"
    by (cases sc)
  from assms(1) sc obtain items where
    "parseClassPlus p = Some items" and "cs = charsetOf items"
    by (auto split: if_splits option.splits list.splits)
  with assms(2) show ?thesis by (auto simp add: charset_exact)
qed

subsection \<open>Positions, movability, and the lowering\<close>

datatype candidate =
  CandidateFull
    (candParam: "String.literal")
    (candOutput: "String.literal")
    (candField: "String.literal option")
    (candAlias: "String.literal")

fun isPrimeE :: "expr \<Rightarrow> bool" where
  "isPrimeE (PrimeF _ _) = True"
| "isPrimeE _ = False"

definition hasPrime :: "expr \<Rightarrow> bool" where
  "hasPrime e = list_ex isPrimeE (allSubexprs e)"

text \<open>\<open>stripPre\<close>: a requires clause evaluates in the pre-state, so \<open>pre(x)\<close>
  is the identity there. Same mutual shape as \<open>subst\<close>.\<close>

fun stripPre :: "expr \<Rightarrow> expr"
and stripPre_list :: "expr list \<Rightarrow> expr list"
and stripPre_fields :: "field_assign list \<Rightarrow> field_assign list"
and stripPre_entries :: "map_entry list \<Rightarrow> map_entry list"
and stripPre_bindings :: "quantifier_binding list \<Rightarrow> quantifier_binding list"
where
  "stripPre (PreF e _)                     = stripPre e"
| "stripPre (IdentifierF n sp)             = IdentifierF n sp"
| "stripPre (BinaryOpF op l r sp)          = BinaryOpF op (stripPre l) (stripPre r) sp"
| "stripPre (UnaryOpF op e sp)             = UnaryOpF op (stripPre e) sp"
| "stripPre (FieldAccessF b f sp)          = FieldAccessF (stripPre b) f sp"
| "stripPre (EnumAccessF b m sp)           = EnumAccessF (stripPre b) m sp"
| "stripPre (IndexF b i sp)                = IndexF (stripPre b) (stripPre i) sp"
| "stripPre (CallF c args sp)              = CallF c (stripPre_list args) sp"
| "stripPre (PrimeF e sp)                  = PrimeF (stripPre e) sp"
| "stripPre (WithF b upds sp)              = WithF (stripPre b) (stripPre_fields upds) sp"
| "stripPre (IfF c t e sp)                 = IfF (stripPre c) (stripPre t) (stripPre e) sp"
| "stripPre (LetF v vl body sp)            = LetF v (stripPre vl) (stripPre body) sp"
| "stripPre (LambdaF p b sp)               = LambdaF p (stripPre b) sp"
| "stripPre (ConstructorF n fs sp)         = ConstructorF n (stripPre_fields fs) sp"
| "stripPre (SetLiteralF xs sp)            = SetLiteralF (stripPre_list xs) sp"
| "stripPre (MapLiteralF es sp)            = MapLiteralF (stripPre_entries es) sp"
| "stripPre (SetComprehensionF v d p sp)   = SetComprehensionF v (stripPre d) (stripPre p) sp"
| "stripPre (SeqLiteralF xs sp)            = SeqLiteralF (stripPre_list xs) sp"
| "stripPre (MatchesF e pat sp)            = MatchesF (stripPre e) pat sp"
| "stripPre (SomeWrapF e sp)               = SomeWrapF (stripPre e) sp"
| "stripPre (TheF v d b sp)                = TheF v (stripPre d) (stripPre b) sp"
| "stripPre (QuantifierF q bs body sp)     = QuantifierF q (stripPre_bindings bs) (stripPre body) sp"
| "stripPre (IntLitF n sp)                 = IntLitF n sp"
| "stripPre (FloatLitF n sp)               = FloatLitF n sp"
| "stripPre (StringLitF n sp)              = StringLitF n sp"
| "stripPre (BoolLitF v sp)                = BoolLitF v sp"
| "stripPre (NoneLitF sp)                  = NoneLitF sp"
| "stripPre_list []                        = []"
| "stripPre_list (e # es)                  = stripPre e # stripPre_list es"
| "stripPre_fields []                      = []"
| "stripPre_fields (FieldAssignFull f v sp # fs) =
     FieldAssignFull f (stripPre v) sp # stripPre_fields fs"
| "stripPre_entries []                     = []"
| "stripPre_entries (MapEntryFull k v sp # es) =
     MapEntryFull (stripPre k) (stripPre v) sp # stripPre_entries es"
| "stripPre_bindings []                    = []"
| "stripPre_bindings (QuantifierBindingFull n d kk sp # bs) =
     QuantifierBindingFull n (stripPre d) kk sp # stripPre_bindings bs"

text \<open>\<open>substFieldCand out fld cand\<close>: replace \<open>out.fld\<close> by the candidate
  identifier, honouring shadowing of \<open>out\<close> exactly as \<open>subst\<close> does.\<close>

fun substFieldCand :: "String.literal \<Rightarrow> String.literal \<Rightarrow> String.literal \<Rightarrow> expr \<Rightarrow> expr"
and substFieldCand_list :: "String.literal \<Rightarrow> String.literal \<Rightarrow> String.literal \<Rightarrow> expr list \<Rightarrow> expr list"
and substFieldCand_fields :: "String.literal \<Rightarrow> String.literal \<Rightarrow> String.literal \<Rightarrow> field_assign list \<Rightarrow> field_assign list"
and substFieldCand_entries :: "String.literal \<Rightarrow> String.literal \<Rightarrow> String.literal \<Rightarrow> map_entry list \<Rightarrow> map_entry list"
and substFieldCand_bindings :: "String.literal \<Rightarrow> String.literal \<Rightarrow> String.literal \<Rightarrow> quantifier_binding list \<Rightarrow> quantifier_binding list"
where
  "substFieldCand x f r (FieldAccessF b g sp) =
     (case b of
        IdentifierF n _ \<Rightarrow>
          (if n = x \<and> g = f then IdentifierF r sp
           else FieldAccessF b g sp)
      | _ \<Rightarrow> FieldAccessF (substFieldCand x f r b) g sp)"
| "substFieldCand x f r (IdentifierF n sp)     = IdentifierF n sp"
| "substFieldCand x f r (BinaryOpF op l rr sp) =
     BinaryOpF op (substFieldCand x f r l) (substFieldCand x f r rr) sp"
| "substFieldCand x f r (UnaryOpF op e sp)     = UnaryOpF op (substFieldCand x f r e) sp"
| "substFieldCand x f r (EnumAccessF b m sp)   = EnumAccessF (substFieldCand x f r b) m sp"
| "substFieldCand x f r (IndexF b i sp)        =
     IndexF (substFieldCand x f r b) (substFieldCand x f r i) sp"
| "substFieldCand x f r (CallF c args sp)      = CallF c (substFieldCand_list x f r args) sp"
| "substFieldCand x f r (PrimeF e sp)          = PrimeF (substFieldCand x f r e) sp"
| "substFieldCand x f r (PreF e sp)            = PreF (substFieldCand x f r e) sp"
| "substFieldCand x f r (WithF b upds sp)      =
     WithF (substFieldCand x f r b) (substFieldCand_fields x f r upds) sp"
| "substFieldCand x f r (IfF c t e sp)         =
     IfF (substFieldCand x f r c) (substFieldCand x f r t) (substFieldCand x f r e) sp"
| "substFieldCand x f r (LetF v vl body sp)    =
     LetF v (substFieldCand x f r vl)
            (if v = x then body else substFieldCand x f r body) sp"
| "substFieldCand x f r (LambdaF p b sp)       =
     LambdaF p (if p = x then b else substFieldCand x f r b) sp"
| "substFieldCand x f r (ConstructorF n fs sp) =
     ConstructorF n (substFieldCand_fields x f r fs) sp"
| "substFieldCand x f r (SetLiteralF xs sp)    = SetLiteralF (substFieldCand_list x f r xs) sp"
| "substFieldCand x f r (MapLiteralF es sp)    = MapLiteralF (substFieldCand_entries x f r es) sp"
| "substFieldCand x f r (SetComprehensionF v d p sp) =
     SetComprehensionF v (substFieldCand x f r d)
                         (if v = x then p else substFieldCand x f r p) sp"
| "substFieldCand x f r (SeqLiteralF xs sp)    = SeqLiteralF (substFieldCand_list x f r xs) sp"
| "substFieldCand x f r (MatchesF e pat sp)    = MatchesF (substFieldCand x f r e) pat sp"
| "substFieldCand x f r (SomeWrapF e sp)       = SomeWrapF (substFieldCand x f r e) sp"
| "substFieldCand x f r (TheF v d b sp)        =
     TheF v (substFieldCand x f r d)
            (if v = x then b else substFieldCand x f r b) sp"
| "substFieldCand x f r (QuantifierF q bs body sp) =
     QuantifierF q (substFieldCand_bindings x f r bs)
                   (if string_in_list x (qb_names bs) then body
                    else substFieldCand x f r body) sp"
| "substFieldCand _ _ _ (IntLitF n sp)         = IntLitF n sp"
| "substFieldCand _ _ _ (FloatLitF n sp)       = FloatLitF n sp"
| "substFieldCand _ _ _ (StringLitF n sp)      = StringLitF n sp"
| "substFieldCand _ _ _ (BoolLitF v sp)        = BoolLitF v sp"
| "substFieldCand _ _ _ (NoneLitF sp)          = NoneLitF sp"
| "substFieldCand_list _ _ _ []                = []"
| "substFieldCand_list x f r (e # es)          =
     substFieldCand x f r e # substFieldCand_list x f r es"
| "substFieldCand_fields _ _ _ []              = []"
| "substFieldCand_fields x f r (FieldAssignFull g v sp # fs) =
     FieldAssignFull g (substFieldCand x f r v) sp # substFieldCand_fields x f r fs"
| "substFieldCand_entries _ _ _ []             = []"
| "substFieldCand_entries x f r (MapEntryFull k v sp # es) =
     MapEntryFull (substFieldCand x f r k) (substFieldCand x f r v) sp
       # substFieldCand_entries x f r es"
| "substFieldCand_bindings _ _ _ []            = []"
| "substFieldCand_bindings x f r (QuantifierBindingFull n d kk sp # bs) =
     QuantifierBindingFull n (substFieldCand x f r d) kk sp
       # substFieldCand_bindings x f r bs"

text \<open>Conjuncts of an ensures expression, carrying the let binders they sit
  under. Only conjunctions and lets are descended: a conjunct under an
  \<open>if\<close> or a disjunction is conditional and never movable.\<close>

fun conjunctsUnder :: "String.literal list \<Rightarrow> expr \<Rightarrow> (String.literal list \<times> expr) list" where
  "conjunctsUnder bs (BinaryOpF BAnd l r _) = conjunctsUnder bs l @ conjunctsUnder bs r"
| "conjunctsUnder bs (LetF v _ body _)      = conjunctsUnder (v # bs) body"
| "conjunctsUnder bs e                      = [(bs, e)]"

fun isIdentOf :: "String.literal \<Rightarrow> expr \<Rightarrow> bool" where
  "isIdentOf n (IdentifierF m _) = (m = n)"
| "isIdentOf _ _ = False"

fun isFieldOf :: "String.literal \<Rightarrow> String.literal \<Rightarrow> expr \<Rightarrow> bool" where
  "isFieldOf n f (FieldAccessF (IdentifierF m _) g _) = (m = n \<and> g = f)"
| "isFieldOf _ _ _ = False"

fun pinsDirect :: "String.literal \<Rightarrow> expr \<Rightarrow> bool" where
  "pinsDirect n (BinaryOpF BEq l r _) = (isIdentOf n l \<or> isIdentOf n r)"
| "pinsDirect _ _ = False"

fun pinsField :: "String.literal \<Rightarrow> String.literal \<Rightarrow> expr \<Rightarrow> bool" where
  "pinsField n f (BinaryOpF BEq l r _) = (isFieldOf n f l \<or> isFieldOf n f r)"
| "pinsField _ _ _ = False"

definition ensConjuncts :: "operation_decl \<Rightarrow> (String.literal list \<times> expr) list" where
  "ensConjuncts op = concat (map (conjunctsUnder []) (operEnsures op))"

definition candName :: "String.literal \<Rightarrow> String.literal" where
  "candName n = STR ''cand_'' + n"

text \<open>\<open>freeMentionsField out fld\<close>: does \<open>out.fld\<close> occur with \<open>out\<close> free?
  Shadowing-aware, mirroring \<open>subst\<close>; expression equality must never be
  demanded here (the extracted structural equality for \<open>expr\<close> exceeds the
  JVM method-size limit), so mention detection is a dedicated walker.\<close>

fun freeMentionsField :: "String.literal \<Rightarrow> String.literal \<Rightarrow> expr \<Rightarrow> bool"
and freeMentionsField_list :: "String.literal \<Rightarrow> String.literal \<Rightarrow> expr list \<Rightarrow> bool"
and freeMentionsField_fields :: "String.literal \<Rightarrow> String.literal \<Rightarrow> field_assign list \<Rightarrow> bool"
and freeMentionsField_entries :: "String.literal \<Rightarrow> String.literal \<Rightarrow> map_entry list \<Rightarrow> bool"
and freeMentionsField_bindings :: "String.literal \<Rightarrow> String.literal \<Rightarrow> quantifier_binding list \<Rightarrow> bool"
where
  "freeMentionsField x f (FieldAccessF b g sp) =
     (case b of
        IdentifierF n _ \<Rightarrow> (n = x \<and> g = f)
      | _ \<Rightarrow> freeMentionsField x f b)"
| "freeMentionsField x f (IdentifierF n sp)     = False"
| "freeMentionsField x f (BinaryOpF op l r sp)  =
     (freeMentionsField x f l \<or> freeMentionsField x f r)"
| "freeMentionsField x f (UnaryOpF op e sp)     = freeMentionsField x f e"
| "freeMentionsField x f (EnumAccessF b m sp)   = freeMentionsField x f b"
| "freeMentionsField x f (IndexF b i sp)        =
     (freeMentionsField x f b \<or> freeMentionsField x f i)"
| "freeMentionsField x f (CallF c args sp)      = freeMentionsField_list x f args"
| "freeMentionsField x f (PrimeF e sp)          = freeMentionsField x f e"
| "freeMentionsField x f (PreF e sp)            = freeMentionsField x f e"
| "freeMentionsField x f (WithF b upds sp)      =
     (freeMentionsField x f b \<or> freeMentionsField_fields x f upds)"
| "freeMentionsField x f (IfF c t e sp)         =
     (freeMentionsField x f c \<or> freeMentionsField x f t \<or> freeMentionsField x f e)"
| "freeMentionsField x f (LetF v vl body sp)    =
     (freeMentionsField x f vl \<or> (if v = x then False else freeMentionsField x f body))"
| "freeMentionsField x f (LambdaF p b sp)       =
     (if p = x then False else freeMentionsField x f b)"
| "freeMentionsField x f (ConstructorF n fs sp) = freeMentionsField_fields x f fs"
| "freeMentionsField x f (SetLiteralF xs sp)    = freeMentionsField_list x f xs"
| "freeMentionsField x f (MapLiteralF es sp)    = freeMentionsField_entries x f es"
| "freeMentionsField x f (SetComprehensionF v d pr sp) =
     (freeMentionsField x f d \<or> (if v = x then False else freeMentionsField x f pr))"
| "freeMentionsField x f (SeqLiteralF xs sp)    = freeMentionsField_list x f xs"
| "freeMentionsField x f (MatchesF e pat sp)    = freeMentionsField x f e"
| "freeMentionsField x f (SomeWrapF e sp)       = freeMentionsField x f e"
| "freeMentionsField x f (TheF v d b sp)        =
     (freeMentionsField x f d \<or> (if v = x then False else freeMentionsField x f b))"
| "freeMentionsField x f (QuantifierF q bs body sp) =
     (freeMentionsField_bindings x f bs \<or>
      (if string_in_list x (qb_names bs) then False else freeMentionsField x f body))"
| "freeMentionsField _ _ (IntLitF n sp)         = False"
| "freeMentionsField _ _ (FloatLitF n sp)       = False"
| "freeMentionsField _ _ (StringLitF n sp)      = False"
| "freeMentionsField _ _ (BoolLitF v sp)        = False"
| "freeMentionsField _ _ (NoneLitF sp)          = False"
| "freeMentionsField_list _ _ []                = False"
| "freeMentionsField_list x f (e # es)          =
     (freeMentionsField x f e \<or> freeMentionsField_list x f es)"
| "freeMentionsField_fields _ _ []              = False"
| "freeMentionsField_fields x f (FieldAssignFull g v sp # fs) =
     (freeMentionsField x f v \<or> freeMentionsField_fields x f fs)"
| "freeMentionsField_entries _ _ []             = False"
| "freeMentionsField_entries x f (MapEntryFull k v sp # es) =
     (freeMentionsField x f k \<or> freeMentionsField x f v \<or> freeMentionsField_entries x f es)"
| "freeMentionsField_bindings _ _ []            = False"
| "freeMentionsField_bindings x f (QuantifierBindingFull n d kk sp # bs) =
     (freeMentionsField x f d \<or> freeMentionsField_bindings x f bs)"

text \<open>A conjunct moves to the requires when it mentions the position freely,
  reads no primed state, and its substituted form is closed under the
  enclosing binders and every operation output.\<close>

definition movableOf ::
  "String.literal list \<Rightarrow> (String.literal list \<times> expr) list
     \<Rightarrow> (expr \<Rightarrow> bool) \<Rightarrow> (expr \<Rightarrow> expr) \<Rightarrow> expr list" where
  "movableOf outs cjs mentionsPos substPos =
     map (\<lambda>(bs, e). stripPre (substPos e))
       (filter
          (\<lambda>(bs, e).
             mentionsPos e \<and> \<not> hasPrime e \<and>
             \<not> list_ex (\<lambda>v. string_in_list v (free_vars (substPos e))) (bs @ outs))
          cjs)"

definition directCandidate ::
  "String.literal list \<Rightarrow> String.literal list \<Rightarrow> operation_decl \<Rightarrow> param_decl
     \<Rightarrow> (param_decl \<times> expr list \<times> expr \<times> candidate) option" where
  "directCandidate samplable taken op p =
     (case prmType p of
        NamedTypeF alias _ \<Rightarrow>
          (if \<not> string_in_list alias samplable
              \<or> string_in_list (candName (prmName p)) taken
              \<or> list_ex (\<lambda>(bs, e). pinsDirect (prmName p) e) (ensConjuncts op)
           then None
           else
             (let cn = candName (prmName p);
                  outs = map prmName (operOutputs op);
                  reqs = movableOf outs (ensConjuncts op)
                           (\<lambda>e. string_in_list (prmName p) (free_vars e))
                           (subst (prmName p) (IdentifierF cn None));
                  glue = BinaryOpF BEq (IdentifierF (prmName p) None)
                                       (IdentifierF cn None) None
              in Some (ParamDeclFull cn (NamedTypeF alias None) None, reqs, glue,
                       CandidateFull cn (prmName p) None alias)))
      | _ \<Rightarrow> None)"

definition fieldCandidate ::
  "String.literal list \<Rightarrow> String.literal list \<Rightarrow> operation_decl \<Rightarrow> String.literal
     \<Rightarrow> field_decl \<Rightarrow> (param_decl \<times> expr list \<times> expr \<times> candidate) option" where
  "fieldCandidate samplable taken op outName fld =
     (case fldType fld of
        NamedTypeF alias _ \<Rightarrow>
          (if \<not> string_in_list alias samplable
              \<or> string_in_list (candName (fldName fld)) taken
              \<or> list_ex (\<lambda>(bs, e). pinsField outName (fldName fld) e) (ensConjuncts op)
           then None
           else
             (let cn = candName (fldName fld);
                  outs = map prmName (operOutputs op);
                  reqs = movableOf outs (ensConjuncts op)
                           (freeMentionsField outName (fldName fld))
                           (substFieldCand outName (fldName fld) cn);
                  glue = BinaryOpF BEq
                           (FieldAccessF (IdentifierF outName None) (fldName fld) None)
                           (IdentifierF cn None) None
              in (if reqs = [] then None
                  else Some (ParamDeclFull cn (NamedTypeF alias None) None, reqs, glue,
                             CandidateFull cn outName (Some (fldName fld)) alias))))
      | _ \<Rightarrow> None)"

text \<open>Direct refined-string outputs lower even without movable conjuncts (the
  length requires alone justifies environment sampling); entity fields lower
  only when a distinctness conjunct actually mentions them, so unconstrained
  fields keep their prover-chosen values.\<close>

fun outputCandidates ::
  "String.literal list \<Rightarrow> entity_decl list \<Rightarrow> String.literal list \<Rightarrow> operation_decl
     \<Rightarrow> param_decl list \<Rightarrow> (param_decl \<times> expr list \<times> expr \<times> candidate) list"
and fieldCandidatesOf ::
  "String.literal list \<Rightarrow> String.literal list \<Rightarrow> operation_decl \<Rightarrow> String.literal
     \<Rightarrow> field_decl list \<Rightarrow> (param_decl \<times> expr list \<times> expr \<times> candidate) list" where
  "outputCandidates samplable ents taken op [] = []"
| "outputCandidates samplable ents taken op (p # ps) =
     (case directCandidate samplable taken op p of
        Some r \<Rightarrow> r # outputCandidates samplable ents taken op ps
      | None \<Rightarrow>
          (case prmType p of
             NamedTypeF tn _ \<Rightarrow>
               (case find (\<lambda>e. entName e = tn) ents of
                  Some ent \<Rightarrow>
                    fieldCandidatesOf samplable taken op (prmName p) (entFields ent)
                      @ outputCandidates samplable ents taken op ps
                | None \<Rightarrow> outputCandidates samplable ents taken op ps)
           | _ \<Rightarrow> outputCandidates samplable ents taken op ps))"
| "fieldCandidatesOf samplable taken op outName [] = []"
| "fieldCandidatesOf samplable taken op outName (f # fs) =
     (case fieldCandidate samplable taken op outName f of
        Some r \<Rightarrow> r # fieldCandidatesOf samplable taken op outName fs
      | None \<Rightarrow> fieldCandidatesOf samplable taken op outName fs)"

definition lowerFreshOutputs ::
  "String.literal list \<Rightarrow> entity_decl list \<Rightarrow> operation_decl
     \<Rightarrow> operation_decl \<times> candidate list" where
  "lowerFreshOutputs samplable ents op =
     (let taken = map prmName (operInputs op) @ map prmName (operOutputs op);
          found = outputCandidates samplable ents taken op (operOutputs op)
      in (OperationDeclFull (operName op)
            (operInputs op @ map (\<lambda>(p, _, _, _). p) found)
            (operOutputs op)
            (operRequires op @ concat (map (\<lambda>(_, rs, _, _). rs) found))
            (operEnsures op @ map (\<lambda>(_, _, g, _). g) found)
            (operRequiresAuth op)
            (operSpan op),
          map (\<lambda>(_, _, _, c). c) found))"

lemmas samplerFor_code [code]        = samplerFor.simps
lemmas lowerFreshOutputs_code [code] = lowerFreshOutputs_def

end
