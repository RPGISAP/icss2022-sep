grammar ICSS;

//--- LEXER: ---

// IF support:
IF   : [iI] [fF];
ELSE : [eE] [lL] [sS] [eE];
BOX_BRACKET_OPEN: '[';
BOX_BRACKET_CLOSE: ']';


//Literals
TRUE: 'TRUE';
FALSE: 'FALSE';
PIXELSIZE: [0-9]+ 'px';
PERCENTAGE: [0-9]+ '%';
SCALAR: [0-9]+;


//Kleur neemt prioriteit
COLOR: '#' [0-9a-f] [0-9a-f] [0-9a-f] [0-9a-f] [0-9a-f] [0-9a-f];

//Specifieke identifiers voor CSS selectors en classes
ID_IDENT: '#' [a-z0-9\-]+;
CLASS_IDENT: '.' [a-z0-9\-]+;

//Identifiers
LOWER_IDENT: [a-z] [a-z0-9\-]*;
CAPITAL_IDENT: [A-Z] [A-Za-z0-9_]*;

//Alle whitespace wordt geskipped
WS: [ \t\r\n]+ -> skip;

//
OPEN_BRACE: '{';
CLOSE_BRACE: '}';
SEMICOLON: ';';
COLON: ':';
PLUS: '+';
MIN: '-';
MUL: '*';
COMMA: ',';
ASSIGNMENT_OPERATOR: ':=';




// --- PARSER: ---
stylesheet
    :   (statement)* EOF
    ;

statement
    :   ruleset
    |   variableAssignment
    |   ifClause
    ;

ruleset
    :   selectorSet blok
    ;

selectorSet
    :   selector (COMMA selector)*
    ;

selector
    :   ID_IDENT        # idSelector
    |   CLASS_IDENT     # classSelector
    |   LOWER_IDENT     # tagSelector
    ;

blok
    :   OPEN_BRACE (declaration | ifClause | variableAssignment)* CLOSE_BRACE
    ;

declaration
    :   LOWER_IDENT COLON expression SEMICOLON
    ;

variableAssignment
    :   CAPITAL_IDENT ASSIGNMENT_OPERATOR expression SEMICOLON
    ;

ifClause
    :   IF BOX_BRACKET_OPEN boolExpression BOX_BRACKET_CLOSE blok (ELSE blok)?
    ;

// Wiskundige
expression
    :   additionExpr
    ;

additionExpr
    :   multiplicationExpr ( (PLUS | MIN) multiplicationExpr )*
    ;

multiplicationExpr
    :   primaryExpr ( MUL primaryExpr )*
    ;

primaryExpr
    :   PIXELSIZE
    |   PERCENTAGE
    |   SCALAR
    |   COLOR
    |   CAPITAL_IDENT         // variabel refereren
    |   LOWER_IDENT
    |   TRUE
    |   FALSE
    |   BOX_BRACKET_OPEN additionExpr BOX_BRACKET_CLOSE
    ;

// booleans voor if
boolExpression
    :   TRUE
    |   FALSE
    |   CAPITAL_IDENT         // variabele die bool oplevert
    ;

