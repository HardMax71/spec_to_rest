---
title: "Grammar design"
description: "Design principles, lexical rules, the full EBNF, operator precedence, and syntactic sugar"
---

### 2.1 Design principles

The grammar follows these priorities:

1. Readability over conciseness, prefer keywords to symbols
2. Familiar syntax, brace-delimited blocks, dot-access, infix operators
3. Minimal ceremony, no import boilerplate for common cases
4. Unambiguous, every construct has exactly one parse
5. Incremental, a minimal spec is valid; more detail can be added progressively

### 2.2 Lexical rules

```text
(* ---- Lexical Grammar ---- *)

(* Identifiers *)
IDENT           = LETTER (LETTER | DIGIT | '_')* ;
UPPER_IDENT     = UPPER_LETTER (LETTER | DIGIT | '_')* ;
LOWER_IDENT     = LOWER_LETTER (LETTER | DIGIT | '_')* ;

LETTER          = 'a'..'z' | 'A'..'Z' ;
UPPER_LETTER    = 'A'..'Z' ;
LOWER_LETTER    = 'a'..'z' ;
DIGIT           = '0'..'9' ;

(* Literals *)
INT_LIT         = DIGIT+ ;
FLOAT_LIT       = DIGIT+ '.' DIGIT+ ;
STRING_LIT      = '"' (CHAR | ESCAPE)* '"' ;
BOOL_LIT        = 'true' | 'false' ;
CHAR            = <any Unicode except '"' and '\'> ;
ESCAPE          = '\' ('n' | 't' | 'r' | '\' | '"') ;
REGEX_LIT       = '/' (REGEX_CHAR)+ '/' ;
REGEX_CHAR      = <any Unicode except '/' and unescaped newline> ;

(* Keywords -- reserved, cannot be used as identifiers *)
KEYWORD         = 'service' | 'entity' | 'state' | 'operation'
                | 'input' | 'output' | 'requires' | 'ensures'
                | 'invariant' | 'fact' | 'conventions' | 'import'
                | 'module' | 'type' | 'enum' | 'transition'
                | 'one' | 'lone' | 'some' | 'set' | 'seq'
                | 'all' | 'no' | 'exists' | 'let' | 'in'
                | 'and' | 'or' | 'not' | 'implies' | 'iff'
                | 'if' | 'then' | 'else'
                | 'true' | 'false' | 'none'
                | 'pre' | 'where' | 'with' | 'the' | 'matches'
                | 'extends' | 'via' | 'when'
                | 'union' | 'intersect' | 'minus' | 'subset'
                | 'function' | 'predicate'
                | 'String' | 'Int' | 'Bool' | 'Float'
                | 'DateTime' | 'Duration'
                | 'Set' | 'Map' | 'Seq' | 'Option' ;

(* Operators *)
ARROW           = '->' ;
FAT_ARROW       = '=>' ;
PRIME           = "'" ;
DOT             = '.' ;
COMMA           = ',' ;
COLON           = ':' ;
PIPE            = '|' ;
HASH            = '#' ;
CARET           = '^' ;
AMPERSAND       = '&' ;
AT              = '@' ;
EQ              = '=' ;
NEQ             = '!=' ;
LT              = '<' ;
GT              = '>' ;
LTE             = '<=' ;
GTE             = '>=' ;
PLUS            = '+' ;
MINUS           = '-' ;
STAR            = '*' ;
SLASH           = '/' ;
LBRACE          = '{' ;
RBRACE          = '}' ;
LPAREN          = '(' ;
RPAREN          = ')' ;
LBRACKET        = '[' ;
RBRACKET        = ']' ;

(* Comments *)
LINE_COMMENT    = '//' <any>* NEWLINE ;
BLOCK_COMMENT   = '/*' <any>* '*/' ;

(* Whitespace -- ignored except as separator *)
WS              = (' ' | '\t' | '\r' | '\n')+ ;
```

### 2.3 Full EBNF grammar

```text
(* ============================================================ *)
(* Top-Level Structure                                           *)
(* ============================================================ *)

spec_file       = { import_decl } service_decl ;

import_decl     = 'import' STRING_LIT ;

service_decl    = 'service' UPPER_IDENT '{'
                    { service_member }
                  '}' ;

service_member  = entity_decl
                | enum_decl
                | type_alias
                | state_decl
                | operation_decl
                | transition_decl
                | invariant_decl
                | fact_decl
                | function_decl
                | predicate_decl
                | convention_block
                ;

(* ============================================================ *)
(* Entity Declarations                                           *)
(* ============================================================ *)

entity_decl     = 'entity' UPPER_IDENT ['extends' UPPER_IDENT] '{'
                    { entity_member }
                  '}' ;

entity_member   = field_decl
                | entity_invariant
                ;

field_decl      = LOWER_IDENT ':' type_expr [field_constraint] ;

field_constraint = 'where' expr ;

entity_invariant = 'invariant' ':' expr ;

(* ============================================================ *)
(* Enum Declarations                                             *)
(* ============================================================ *)

enum_decl       = 'enum' UPPER_IDENT '{'
                    enum_value { ',' enum_value } [',']
                  '}' ;

enum_value      = UPPER_IDENT ;

(* ============================================================ *)
(* Type Aliases and Refinement Types                             *)
(* ============================================================ *)

type_alias      = 'type' UPPER_IDENT '=' type_expr ['where' expr] ;

(* ============================================================ *)
(* Type Expressions                                              *)
(* ============================================================ *)

(* Note: type_expr uses a base + suffix design to avoid left    *)
(* recursion. The relation arrow '->' is parsed as an optional  *)
(* suffix on a base type, not as a separate recursive rule.     *)

type_expr       = base_type [ '->' multiplicity base_type ] ;

base_type       = primitive_type
                | compound_type
                | option_type
                | UPPER_IDENT          (* entity, enum, or type alias ref *)
                ;

primitive_type  = 'String' | 'Int' | 'Bool' | 'Float'
                | 'DateTime' | 'Duration' ;

compound_type   = set_type | map_type | seq_type ;

set_type        = 'Set' '[' type_expr ']' ;

map_type        = 'Map' '[' type_expr ',' type_expr ']' ;

seq_type        = 'Seq' '[' type_expr ']' ;

option_type     = 'Option' '[' type_expr ']' ;

multiplicity    = 'one' | 'lone' | 'some' | 'set' ;

(* ============================================================ *)
(* State Declarations                                            *)
(* ============================================================ *)

state_decl      = 'state' '{'
                    { state_field }
                  '}' ;

state_field     = LOWER_IDENT ':' type_expr ;

(* ============================================================ *)
(* Operation Declarations                                        *)
(* ============================================================ *)

operation_decl  = 'operation' UPPER_IDENT '{'
                    [input_clause]
                    [output_clause]
                    [requires_clause]
                    [ensures_clause]
                  '}' ;

input_clause    = 'input' ':' param_list ;

output_clause   = 'output' ':' param_list ;

param_list      = param { ',' param } ;

param           = LOWER_IDENT ':' type_expr ;

requires_clause = 'requires' ':' expr_list ;

ensures_clause  = 'ensures' ':' expr_list ;

(* expr_list uses NEWLINE as separator. Within requires/ensures *)
(* blocks, each line is an independent expression; all lines    *)
(* are implicitly conjoined (AND'd). A NEWLINE is significant   *)
(* here: it separates consecutive expressions. Explicit 'and'   *)
(* within a single line still works for inline conjunction.     *)

expr_list       = expr { NEWLINE expr } ;

(* ============================================================ *)
(* Transition Declarations (State Machines)                      *)
(* ============================================================ *)

transition_decl = 'transition' UPPER_IDENT '{'
                    'entity' ':' UPPER_IDENT
                    'field' ':' LOWER_IDENT
                    { transition_rule }
                  '}' ;

transition_rule = enum_value '->' enum_value 'via' UPPER_IDENT
                  ['when' expr] ;

(* ============================================================ *)
(* Invariants and Facts                                          *)
(* ============================================================ *)

invariant_decl  = 'invariant' [LOWER_IDENT] ':' expr ;

fact_decl       = 'fact' [LOWER_IDENT] ':' expr ;

(* ============================================================ *)
(* User-Defined Functions and Predicates                         *)
(* ============================================================ *)

(* Functions return a typed value; predicates return Bool.       *)
(* Both are pure (no state mutation).                            *)

function_decl   = 'function' LOWER_IDENT '(' [param_list] ')'
                  ':' type_expr '=' expr ;

predicate_decl  = 'predicate' LOWER_IDENT '(' [param_list] ')'
                  '=' expr ;

(* ============================================================ *)
(* Convention Overrides                                           *)
(* ============================================================ *)

convention_block = 'conventions' '{'
                     { convention_rule }
                   '}' ;

convention_rule  = convention_target '.' convention_prop '=' convention_val ;

convention_target = UPPER_IDENT ;

convention_prop  = LOWER_IDENT
                 | LOWER_IDENT STRING_LIT   (* e.g., http_header "Location" *)
                 ;

convention_val   = STRING_LIT | INT_LIT | BOOL_LIT | expr ;

(* ============================================================ *)
(* Expressions                                                   *)
(* ============================================================ *)

(* Precedence from lowest (top) to highest (bottom).            *)
(* See section 2.4 for the full precedence table.               *)

expr            = or_expr ;

or_expr         = and_expr { 'or' and_expr } ;

and_expr        = not_expr { 'and' not_expr } ;

not_expr        = 'not' not_expr
                | implies_expr
                ;

implies_expr    = comparison_expr ['implies' comparison_expr]
                | comparison_expr ['iff' comparison_expr]
                ;

comparison_expr = set_op_expr { comp_op set_op_expr } ;

comp_op         = '=' | '!=' | '<' | '>' | '<=' | '>='
                | 'in' | 'not' 'in'
                | 'subset'                       (* A subset B *)
                | 'matches'                      (* s matches /regex/ *)
                ;

(* ---- Set-level infix operators ---- *)
(* These sit between comparison and additive so that             *)
(* 'A union B subset C' parses as '(A union B) subset C'.       *)

set_op_expr     = additive_expr
                  { ('union' | 'intersect' | 'minus') additive_expr } ;

additive_expr   = multiplicative_expr { ('+' | '-') multiplicative_expr } ;

multiplicative_expr = unary_expr { ('*' | '/') unary_expr } ;

unary_expr      = '#' unary_expr          (* cardinality *)
                | '-' unary_expr          (* negation *)
                | '^' unary_expr          (* transitive closure *)
                | with_expr
                ;

(* ---- 'with' record-update expression ---- *)
(* Parsed at a level between unary and postfix so that           *)
(*   pre(todos)[id] with { status = DONE }                      *)
(* parses as (pre(todos)[id]) with { status = DONE }.           *)

with_expr       = postfix_expr ['with' '{' field_assign
                    { ',' field_assign } '}'] ;

field_assign    = LOWER_IDENT '=' expr ;

(* ---- Postfix operators ---- *)

postfix_expr    = primary_expr { postfix_op } ;

postfix_op      = PRIME                   (* primed: store' *)
                | '.' LOWER_IDENT         (* field access *)
                | '[' expr ']'            (* indexing *)
                | '(' [arg_list] ')'      (* function call *)
                ;

arg_list        = expr { ',' expr } ;

(* ---- Primary expressions ---- *)

primary_expr    = INT_LIT
                | FLOAT_LIT
                | STRING_LIT
                | BOOL_LIT
                | REGEX_LIT
                | 'none'                          (* Option empty value *)
                | IDENT
                | 'pre' '(' IDENT ')'            (* pre-state reference *)
                | quantifier_expr
                | the_expr
                | some_wrap_expr
                | set_or_map_expr
                | sequence_literal
                | constructor_expr
                | lambda_expr
                | if_expr
                | let_expr
                | '(' expr ')'
                ;

(* ---- Quantifier expressions ---- *)
(* 'some' as quantifier: 'some x in S | P' -- uses 'in' and '|'. *)
(* Multi-variable: 'all x in A, y in B | P'.                     *)

quantifier_expr = quantifier quant_binding { ',' quant_binding }
                  '|' expr ;

quant_binding   = LOWER_IDENT 'in' expr ;

quantifier      = 'all' | 'some' | 'no' | 'exists' ;

(* ---- Disambiguation: some(expr) vs some x in ... ---- *)
(* 'some' followed by '(' is ambiguous. We resolve it by        *)
(* lookahead: if we see 'some' '(' expr ')' where the token     *)
(* after ')' is NOT 'in', it is an Option wrapper. If 'some'    *)
(* is followed by LOWER_IDENT 'in', it is a quantifier (handled *)
(* by quantifier_expr above). The dedicated some_wrap_expr rule *)
(* matches only the Option-wrapping case.                        *)

some_wrap_expr  = 'some' '(' expr ')' ;
  (* Constraint: must not be followed by 'in'; if the intent is *)
  (* a quantifier with parenthesized domain, use explicit form  *)
  (* 'some x in (S) | P'.                                       *)

(* ---- 'the' expression (definite description) ---- *)
(* Selects the unique element satisfying a predicate.            *)
(* Example: the s in sessions | sessions[s].access_token = t    *)

the_expr        = 'the' LOWER_IDENT 'in' expr '|' expr ;

(* ---- Lambda expressions ---- *)
(* Used in higher-order built-in calls like sum(coll, i => ...) *)

lambda_expr     = LOWER_IDENT '=>' expr ;

(* ---- Constructor expressions ---- *)
(* Creates an entity/record value: Name { field = val, ... }     *)

constructor_expr = UPPER_IDENT '{' field_assign { ',' field_assign } '}' ;

(* ---- Set, map, and relation literals ---- *)
(* We unify set literals, set comprehensions, and map/relation   *)
(* pair literals into a single rule with ordered alternatives.   *)

set_or_map_expr = '{' '}'                                   (* empty set/map *)
                | '{' LOWER_IDENT 'in' expr '|' expr '}'   (* set comprehension *)
                | '{' expr '->' expr
                    { ',' expr '->' expr } '}'              (* map/relation literal *)
                | '{' expr { ',' expr } '}'                 (* set literal *)
                ;

(* ---- Sequence literals ---- *)

sequence_literal = '[' ']'                                  (* empty sequence *)
                 | '[' expr { ',' expr } ']'                (* non-empty sequence *)
                 ;

(* ---- Conditional and let ---- *)

if_expr         = 'if' expr 'then' expr 'else' expr ;

let_expr        = 'let' LOWER_IDENT '=' expr 'in' expr ;

(* ============================================================ *)
(* Module System                                                 *)
(* ============================================================ *)

(* Files can import other spec files. The import makes all
   entities, types, and enums from the imported file available
   in the current scope. *)

(* Example:
     import "common/types.spec"
     import "auth/entities.spec"
*)
```

### 2.4 Operator precedence (highest to lowest)

| Precedence  | Operators                                                            | Associativity |
| ----------- | -------------------------------------------------------------------- | ------------- |
| 1 (highest) | `#` (cardinality), unary `-`, `^` (transitive closure)               | Right         |
| 2           | `'` (prime), `.` (access), `[]` (index), `()` (call)                 | Left          |
| 2.5         | `with { ... }` (record update)                                       | Left          |
| 3           | `*`, `/`                                                             | Left          |
| 4           | `+`, `-`                                                             | Left          |
| 4.5         | `union`, `intersect`, `minus`                                        | Left          |
| 5           | `=`, `!=`, `<`, `>`, `<=`, `>=`, `in`, `not in`, `subset`, `matches` | Non-assoc     |
| 6           | `not`                                                                | Right         |
| 7           | `and`                                                                | Left          |
| 8           | `or`                                                                 | Left          |
| 9           | `implies`, `iff`                                                     | Right         |
| 10 (lowest) | `all`, `some`, `no`, `exists`, `the` (quantifiers)                   | N/A           |

### 2.5 Syntactic sugar

The grammar supports several conveniences:

- Trailing commas are allowed in enum values, param lists, and set literals
- Multi-line ensures/requires, each line in an ensures or requires block is implicitly
  conjoined (AND'd together); newlines are significant separators within `expr_list` (see the
  `expr_list` production). Explicit `and` within a single line still works for inline conjunction.
- Primed state shorthand, `store'` means "the state of `store` after this operation executes"
- pre() shorthand, `pre(store)` is equivalent to referring to `store` without a prime (the
  state before the operation); it exists for readability in ensures clauses where the unprimed name
  might be ambiguous
- Cardinality shorthand, `#store` means `|store|` (the number of entries)
- Record update, `expr with { field = val, ... }` creates a copy of the record with specified
  fields changed
- some(v), wraps a value in `Option[T]`; distinct from the `some` quantifier which always uses
  the `some x in S | P` form
- Constructors, `TypeName { field = val, ... }` creates a new entity/record value
- Map/relation pairs, `{a -> b, c -> d}` creates a map/relation literal
- Sequence literals, `[a, b, c]` creates a `Seq` value
- Lambda shorthand, `x => expr` for inline functions passed to `sum`, etc.
