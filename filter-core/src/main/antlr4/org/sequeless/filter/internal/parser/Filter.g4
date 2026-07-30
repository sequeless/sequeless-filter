grammar Filter;

filter       : disjunction EOF ;
disjunction  : conjunction ( OR conjunction )* ;
conjunction  : primary ( AND primary )* ;
primary      : LPAREN disjunction RPAREN | condition ;

condition    : fieldOrAny MEETS WORD LPAREN argList? RPAREN  # FunctionCondition
             | fieldOrAny opPhrase value                      # BinaryCondition
             | fieldOrAny opPhrase                            # UnaryCondition
             ;
fieldOrAny   : path  # FieldTarget
             | ANY   # AnyTarget
             ;
path         : WORD ( DOT WORD )* ;
// opPhrase is dynamically resolved against the OperatorRegistry by the visitor —
// no grammar change needed when new operators are added to the registry.
opPhrase     : (WORD | EQ | NEQ | GTE | LTE | GT | LT)+ ;
argList      : arg ( COMMA arg )* ;
arg          : NUMBER | STRING | WORD ;
value        : STRING | NUMBER | TRUE | FALSE | NULL
             | LBRACKET ( value ( COMMA value )* )? RBRACKET ;

// --- Keywords (case-insensitive) ---
AND      : [Aa][Nn][Dd] ;
OR       : [Oo][Rr] ;
ANY      : [Aa][Nn][Yy] ;
// 'meets' introduces function-call syntax: field meets fnName(args)
MEETS    : [Mm][Ee][Ee][Tt][Ss] ;
TRUE     : 'true' ;
FALSE    : 'false' ;
NULL     : 'null' ;

// --- Punctuation ---
DOT      : '.' ;
COMMA    : ',' ;
LPAREN   : '(' ;
RPAREN   : ')' ;
LBRACKET : '[' ;
RBRACKET : ']' ;

// --- Comparison symbols (longest match first) ---
GTE      : '>=' ;
LTE      : '<=' ;
NEQ      : '!=' ;
GT       : '>'  ;
LT       : '<'  ;
EQ       : '='  ;

NUMBER   : '-'? [0-9]+ ( '.' [0-9]+ )? ;

// String literals use doubled-quote escaping (SQL-style):
//   single-quoted: 'it''s fine'  -> Java value  it's fine
//   double-quoted: "say ""hello""" -> Java value  say "hello"
// Backslash escaping is NOT supported.
STRING   : '\'' ( ~'\'' | '\'\'' )* '\''
         | '"'  ( ~'"'  | '""'   )* '"' ;

WORD     : [a-zA-Z_] [a-zA-Z_0-9]* ;
WS       : [ \t\r\n]+ -> skip ;
