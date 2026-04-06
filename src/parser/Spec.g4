grammar Spec;

// ─── Top-level ───────────────────────────────────────────────

specFile
    : importDecl* serviceDecl EOF
    ;

importDecl
    : IMPORT STRING_LIT
    ;

serviceDecl
    : SERVICE UPPER_IDENT LBRACE serviceMember* RBRACE
    ;

serviceMember
    : entityDecl
    | enumDecl
    | typeAlias
    | stateDecl
    | operationDecl
    | transitionDecl
    | invariantDecl
    | factDecl
    | functionDecl
    | predicateDecl
    | conventionBlock
    ;

// ─── Entity ──────────────────────────────────────────────────

entityDecl
    : ENTITY UPPER_IDENT (EXTENDS UPPER_IDENT)? LBRACE entityMember* RBRACE
    ;

entityMember
    : fieldDecl
    | entityInvariant
    ;

fieldDecl
    : lowerIdent COLON typeExpr (WHERE expr)?
    ;

entityInvariant
    : INVARIANT COLON expr
    ;

// ─── Enum ────────────────────────────────────────────────────

enumDecl
    : ENUM UPPER_IDENT LBRACE enumValue (COMMA enumValue)* COMMA? RBRACE
    ;

enumValue
    : UPPER_IDENT
    ;

// ─── Type alias ──────────────────────────────────────────────

typeAlias
    : TYPE UPPER_IDENT EQ typeExpr (WHERE expr)?
    ;

// ─── Type expressions ────────────────────────────────────────

typeExpr
    : baseType (ARROW multiplicity? baseType)?
    ;

baseType
    : primitiveType
    | SET LBRACK typeExpr RBRACK
    | MAP LBRACK typeExpr COMMA typeExpr RBRACK
    | SEQ LBRACK typeExpr RBRACK
    | OPTION LBRACK typeExpr RBRACK
    | UPPER_IDENT
    ;

primitiveType
    : STRING_TYPE
    | INT_TYPE
    | BOOL_TYPE
    | FLOAT_TYPE
    | DATETIME_TYPE
    | DURATION_TYPE
    | UUID_TYPE
    ;

multiplicity
    : ONE
    | LONE
    | SOME
    | SET_MULT
    ;

// ─── State ───────────────────────────────────────────────────

stateDecl
    : STATE LBRACE stateField* RBRACE
    ;

stateField
    : lowerIdent COLON typeExpr
    ;

// ─── Operation ───────────────────────────────────────────────

operationDecl
    : OPERATION UPPER_IDENT LBRACE operationClause* RBRACE
    ;

operationClause
    : inputClause
    | outputClause
    | requiresClause
    | ensuresClause
    ;

inputClause
    : INPUT COLON paramList
    ;

outputClause
    : OUTPUT COLON paramList
    ;

paramList
    : param (COMMA param)*
    ;

param
    : lowerIdent COLON typeExpr
    ;

requiresClause
    : REQUIRES COLON expr+
    ;

ensuresClause
    : ENSURES COLON expr+
    ;

// ─── Transition (state machine) ─────────────────────────────

transitionDecl
    : TRANSITION UPPER_IDENT LBRACE
        ENTITY COLON UPPER_IDENT
        FIELD COLON lowerIdent
        transitionRule*
      RBRACE
    ;

transitionRule
    : UPPER_IDENT ARROW UPPER_IDENT VIA UPPER_IDENT (WHEN expr)?
    ;

// ─── Invariant / Fact ────────────────────────────────────────

invariantDecl
    : INVARIANT (lowerIdent)? COLON expr
    ;

factDecl
    : FACT (lowerIdent)? COLON expr
    ;

// ─── Function / Predicate ────────────────────────────────────

functionDecl
    : FUNCTION lowerIdent LPAREN paramList? RPAREN COLON typeExpr EQ expr
    ;

predicateDecl
    : PREDICATE lowerIdent LPAREN paramList? RPAREN EQ expr
    ;

// ─── Convention overrides ────────────────────────────────────

conventionBlock
    : CONVENTIONS LBRACE conventionRule* RBRACE
    ;

conventionRule
    : UPPER_IDENT DOT lowerIdent STRING_LIT? EQ expr
    ;

// ─── Expressions ─────────────────────────────────────────────
//
// Single left-recursive rule. ANTLR4 assigns precedence by
// alternative order (first = lowest, last = highest).

expr
    // ── Logical (lowest precedence) ──
    : expr OR expr                                              # orExpr
    | expr AND expr                                             # andExpr
    | NOT expr                                                  # notExpr
    | expr IMPLIES expr                                         # impliesExpr
    | expr IFF expr                                             # iffExpr

    // ── Comparison ──
    | expr EQ expr                                              # eqExpr
    | expr NEQ expr                                             # neqExpr
    | expr LT expr                                              # ltExpr
    | expr GT expr                                              # gtExpr
    | expr LTE expr                                             # lteExpr
    | expr GTE expr                                             # gteExpr
    | expr IN expr                                              # inExpr
    | expr NOT IN expr                                          # notInExpr
    | expr SUBSET expr                                          # subsetExpr
    | expr MATCHES REGEX_LIT                                    # matchesExpr

    // ── Set operations ──
    | expr UNION expr                                           # unionExpr
    | expr INTERSECT expr                                       # intersectExpr
    | expr MINUS expr                                           # minusExpr

    // ── Arithmetic ──
    | expr PLUS expr                                            # addExpr
    | expr DASH expr                                            # subExpr
    | expr STAR expr                                            # mulExpr
    | expr SLASH expr                                           # divExpr

    // ── Unary prefix ──
    | HASH expr                                                 # cardinalityExpr
    | DASH expr                                                 # negExpr
    | CARET expr                                                # powerExpr

    // ── With (record update) ──
    | expr WITH LBRACE fieldAssign (COMMA fieldAssign)* RBRACE  # withExpr

    // ── Postfix ──
    | expr PRIME                                                # primeExpr
    | expr DOT lowerIdent                                        # fieldAccessExpr
    | expr DOT UPPER_IDENT                                      # enumAccessExpr
    | expr LBRACK expr RBRACK                                   # indexExpr
    | expr LPAREN argList? RPAREN                               # callExpr

    // ── Primary (highest precedence / atoms) ──
    | LPAREN expr RPAREN                                        # parenExpr
    | quantifierExpr                                            # quantExpr
    | someWrapExpr                                              # someWrapE
    | theExpr                                                   # theE
    | ifExpr                                                    # ifE
    | letExpr                                                   # letE
    | lambdaExpr                                                # lambdaE
    | constructorExpr                                           # constructorE
    | setOrMapLiteral                                           # setOrMapE
    | seqLiteral                                                # seqE
    | PRE LPAREN expr RPAREN                                    # preExpr
    | INT_LIT                                                   # intLitExpr
    | FLOAT_LIT                                                 # floatLitExpr
    | STRING_LIT                                                # stringLitExpr
    | TRUE                                                      # trueLitExpr
    | FALSE                                                     # falseLitExpr
    | NONE                                                      # noneLitExpr
    | UPPER_IDENT                                               # upperIdentExpr
    | lowerIdent                                                # lowerIdentExpr
    ;

// ─── Expression sub-rules ────────────────────────────────────

fieldAssign
    : lowerIdent EQ expr
    ;

argList
    : expr (COMMA expr)*
    ;

quantifierExpr
    : quantifier quantBinding (COMMA quantBinding)* PIPE expr
    ;

quantifier
    : ALL
    | SOME
    | NO
    | EXISTS
    ;

quantBinding
    : lowerIdent IN expr       // set membership: x in S
    | lowerIdent COLON expr    // type binding: op: Login
    ;

someWrapExpr
    : SOME LPAREN expr RPAREN
    ;

theExpr
    : THE lowerIdent IN expr PIPE expr
    ;

ifExpr
    : IF expr THEN expr ELSE expr
    ;

letExpr
    : LET lowerIdent EQ expr IN expr
    ;

lambdaExpr
    : lowerIdent FAT_ARROW expr
    ;

constructorExpr
    : UPPER_IDENT LBRACE fieldAssign (COMMA fieldAssign)* RBRACE
    ;

setOrMapLiteral
    : LBRACE RBRACE                                             // empty set
    | LBRACE lowerIdent IN expr PIPE expr RBRACE                 // set comprehension
    | LBRACE expr ARROW expr (COMMA expr ARROW expr)* RBRACE    // map literal
    | LBRACE expr (COMMA expr)* RBRACE                          // set literal
    ;

seqLiteral
    : LBRACK RBRACK                                             // empty sequence
    | LBRACK expr (COMMA expr)* RBRACK                          // sequence literal
    ;

// ─── Keyword-as-identifier ───────────────────────────────────
// Some keywords may also appear as identifiers (field names,
// variable names, etc.). This rule allows them in those positions.

lowerIdent
    : LOWER_IDENT
    | INPUT | OUTPUT | FIELD | STATE
    | ONE | LONE | SET_MULT
    | VIA | WHEN | WHERE | WITH | EXTENDS
    | REQUIRES | ENSURES
    | ENTITY | OPERATION | TRANSITION | INVARIANT | FACT
    | CONVENTIONS | FUNCTION | PREDICATE | TYPE | ENUM | IMPORT
    | SERVICE
    ;

// ═══════════════════════════════════════════════════════════════
// LEXER RULES
// ═══════════════════════════════════════════════════════════════

// ─── Keywords ────────────────────────────────────────────────

SERVICE     : 'service' ;
ENTITY      : 'entity' ;
ENUM        : 'enum' ;
TYPE        : 'type' ;
STATE       : 'state' ;
OPERATION   : 'operation' ;
TRANSITION  : 'transition' ;
INVARIANT   : 'invariant' ;
FACT        : 'fact' ;
CONVENTIONS : 'conventions' ;
IMPORT      : 'import' ;
FUNCTION    : 'function' ;
PREDICATE   : 'predicate' ;
EXTENDS     : 'extends' ;

INPUT       : 'input' ;
OUTPUT      : 'output' ;
REQUIRES    : 'requires' ;
ENSURES     : 'ensures' ;
FIELD       : 'field' ;

ONE         : 'one' ;
LONE        : 'lone' ;
SET_MULT    : 'set' ;

// Set/String/Int/etc. as type keywords
STRING_TYPE   : 'String' ;
INT_TYPE      : 'Int' ;
BOOL_TYPE     : 'Bool' ;
FLOAT_TYPE    : 'Float' ;
DATETIME_TYPE : 'DateTime' ;
DURATION_TYPE : 'Duration' ;
UUID_TYPE     : 'UUID' ;
SET           : 'Set' ;
MAP           : 'Map' ;
SEQ           : 'Seq' ;
OPTION        : 'Option' ;

// Expression keywords
AND         : 'and' ;
OR          : 'or' ;
NOT         : 'not' ;
IMPLIES     : 'implies' ;
IFF         : 'iff' ;
IN          : 'in' ;
SUBSET      : 'subset' ;
MATCHES     : 'matches' ;
UNION       : 'union' ;
INTERSECT   : 'intersect' ;
MINUS       : 'minus' ;

ALL         : 'all' ;
SOME        : 'some' ;
NO          : 'no' ;
EXISTS      : 'exists' ;

IF          : 'if' ;
THEN        : 'then' ;
ELSE        : 'else' ;
LET         : 'let' ;
PRE         : 'pre' ;
WITH        : 'with' ;
THE         : 'the' ;
WHERE       : 'where' ;
VIA         : 'via' ;
WHEN        : 'when' ;

TRUE        : 'true' ;
FALSE       : 'false' ;
NONE        : 'none' ;

// ─── Operators / punctuation ─────────────────────────────────

ARROW       : '->' ;
FAT_ARROW   : '=>' ;
PRIME       : '\'' ;

EQ          : '=' ;
NEQ         : '!=' ;
LTE         : '<=' ;
GTE         : '>=' ;
LT          : '<' ;
GT          : '>' ;

PLUS        : '+' ;
DASH        : '-' ;
STAR        : '*' ;
SLASH       : '/' ;
HASH        : '#' ;
CARET       : '^' ;

DOT         : '.' ;
COMMA       : ',' ;
COLON       : ':' ;
PIPE        : '|' ;

LBRACE      : '{' ;
RBRACE      : '}' ;
LPAREN      : '(' ;
RPAREN      : ')' ;
LBRACK      : '[' ;
RBRACK      : ']' ;

// ─── Literals ────────────────────────────────────────────────

REGEX_LIT
    : '/' ~[*/\r\n] ~[/\r\n]* '/'
    ;

FLOAT_LIT
    : [0-9]+ '.' [0-9]+
    ;

INT_LIT
    : [0-9]+
    ;

STRING_LIT
    : '"' (~["\\\r\n] | '\\' .)* '"'
    ;

// ─── Identifiers ────────────────────────────────────────────

UPPER_IDENT
    : [A-Z] [a-zA-Z0-9_]*
    ;

LOWER_IDENT
    : [a-z_] [a-zA-Z0-9_]*
    ;

// ─── Whitespace & comments ───────────────────────────────────

LINE_COMMENT
    : '//' ~[\r\n]* -> channel(HIDDEN)
    ;

BLOCK_COMMENT
    : '/*' .*? '*/' -> channel(HIDDEN)
    ;

WS
    : [ \t\r\n]+ -> channel(HIDDEN)
    ;
