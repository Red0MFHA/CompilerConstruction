import java.io.*;

%%

%public
%class Scanner
%type Token
%line
%column


DIGIT   = [0-9]
UPPER   = [A-Z]
ID      = {UPPER}([a-z0-9_]){0,30}
INT     = [+-]?{DIGIT}+
FLOAT   = [+-]?{DIGIT}+ \. {DIGIT}{1,6} ([eE] [+-]? {DIGIT}+)?
WS      = [ \t\r\n]+

// Comments
M_COMMENT = "#|" [^#]* "|#"
S_COMMENT = "##" [^\n]*

%%

/* 1 & 2: Comments (Priority 1) */
{M_COMMENT} { /* Skip */ }
{S_COMMENT} { /* Skip */ }

/* 3: Multi-character Operators [cite: 76] */
"**"  { return new Token(TokenType.POW, yytext(), yyline, yycolumn); }
"=="  { return new Token(TokenType.EQ, yytext(), yyline, yycolumn); }
"!="  { return new Token(TokenType.NEQ, yytext(), yyline, yycolumn); }
"<="  { return new Token(TokenType.LTE, yytext(), yyline, yycolumn); }
">="  { return new Token(TokenType.GTE, yytext(), yyline, yycolumn); }
"&&"  { return new Token(TokenType.AND, yytext(), yyline, yycolumn); }
"||"  { return new Token(TokenType.OR, yytext(), yyline, yycolumn); }
"++"  { return new Token(TokenType.INC, yytext(), yyline, yycolumn); }
"--"  { return new Token(TokenType.DEC, yytext(), yyline, yycolumn); }
"+="  { return new Token(TokenType.PLUS_ASSIGN, yytext(), yyline, yycolumn); }
"-="  { return new Token(TokenType.MIN_ASSIGN, yytext(), yyline, yycolumn); }
"*="  { return new Token(TokenType.MULT_ASSIGN, yytext(), yyline, yycolumn); }
"/="  { return new Token(TokenType.DIV_ASSIGN, yytext(), yyline, yycolumn); }

/* 4: Keywords [cite: 33, 77] */
"start"     { return new Token(TokenType.START, yytext(), yyline, yycolumn); }
"finish"    { return new Token(TokenType.FINISH, yytext(), yyline, yycolumn); }
"loop"      { return new Token(TokenType.LOOP, yytext(), yyline, yycolumn); }
"condition" { return new Token(TokenType.CONDITION, yytext(), yyline, yycolumn); }
"declare"   { return new Token(TokenType.DECLARE, yytext(), yyline, yycolumn); }
"output"    { return new Token(TokenType.OUTPUT, yytext(), yyline, yycolumn); }
"input"     { return new Token(TokenType.INPUT, yytext(), yyline, yycolumn); }
"function"  { return new Token(TokenType.FUNCTION, yytext(), yyline, yycolumn); }
"return"    { return new Token(TokenType.RETURN, yytext(), yyline, yycolumn); }
"break"     { return new Token(TokenType.BREAK, yytext(), yyline, yycolumn); }
"continue"  { return new Token(TokenType.CONTINUE, yytext(), yyline, yycolumn); }
"else"      { return new Token(TokenType.ELSE, yytext(), yyline, yycolumn); }

/* 5: Boolean Literals  */
"true"  { return new Token(TokenType.BOOL_LIT, yytext(), yyline, yycolumn); }
"false" { return new Token(TokenType.BOOL_LIT, yytext(), yyline, yycolumn); }

/* 6, 7, 8: ID and Numbers */
{ID}    { return new Token(TokenType.ID, yytext(), yyline, yycolumn); }
{FLOAT} { return new Token(TokenType.FLOAT_LIT, yytext(), yyline, yycolumn); }
{INT}   { return new Token(TokenType.INT_LIT, yytext(), yyline, yycolumn); }

/* 9: String and Character Literals */
\"([^\n\"]|\\\")*\" { return new Token(TokenType.STRING_LIT, yytext(), yyline, yycolumn); }
'([^\\\n]|\\['\\ntr])' { return new Token(TokenType.CHAR_LIT, yytext(), yyline, yycolumn); }

/* 10: Single-character Operators  */
"+" { return new Token(TokenType.PLUS, yytext(), yyline, yycolumn); }
"-" { return new Token(TokenType.MINUS, yytext(), yyline, yycolumn); }
"*" { return new Token(TokenType.MULT, yytext(), yyline, yycolumn); }
"/" { return new Token(TokenType.DIV, yytext(), yyline, yycolumn); }
"%" { return new Token(TokenType.MOD, yytext(), yyline, yycolumn); }
"=" { return new Token(TokenType.ASSIGN, yytext(), yyline, yycolumn); }
"<" { return new Token(TokenType.LT, yytext(), yyline, yycolumn); }
">" { return new Token(TokenType.GT, yytext(), yyline, yycolumn); }
"!" { return new Token(TokenType.NOT, yytext(), yyline, yycolumn); }

/* 11: Punctuators  */
"(" { return new Token(TokenType.LPAREN, yytext(), yyline, yycolumn); }
")" { return new Token(TokenType.RPAREN, yytext(), yyline, yycolumn); }
"{" { return new Token(TokenType.LBRACE, yytext(), yyline, yycolumn); }
"}" { return new Token(TokenType.RBRACE, yytext(), yyline, yycolumn); }
"[" { return new Token(TokenType.LBRACK, yytext(), yyline, yycolumn); }
"]" { return new Token(TokenType.RBRACK, yytext(), yyline, yycolumn); }
"," { return new Token(TokenType.COMMA, yytext(), yyline, yycolumn); }
";" { return new Token(TokenType.SEMI, yytext(), yyline, yycolumn); }
":" { return new Token(TokenType.COLON, yytext(), yyline, yycolumn); }

/* 12: Whitespace*/
{WS} { /* Ignore but JFlex tracks line numbers via %line */ }

. { return new Token(TokenType.ERROR, yytext(), yyline, yycolumn); }