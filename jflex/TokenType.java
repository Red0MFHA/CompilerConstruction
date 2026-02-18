public enum TokenType {
    // Keywords [cite: 33]
    START, FINISH, LOOP, CONDITION, DECLARE, OUTPUT, INPUT,
    FUNCTION, RETURN, BREAK, CONTINUE, ELSE,

    // Literals
    ID, INT_LIT, FLOAT_LIT, STRING_LIT, CHAR_LIT, BOOL_LIT,

    // Operators [cite: 52-60]
    PLUS, MINUS, MULT, DIV, MOD, POW,        // Arithmetic
    EQ, NEQ, LTE, GTE, LT, GT,               // Relational
    AND, OR, NOT,                            // Logical
    ASSIGN, PLUS_ASSIGN, MIN_ASSIGN, MULT_ASSIGN, DIV_ASSIGN, // Assignment
    INC, DEC,                                // Inc/Dec

    // Punctuators [cite: 62, 63]
    LPAREN, RPAREN, LBRACE, RBRACE, LBRACK, RBRACK,
    COMMA, SEMI, COLON,

    ERROR, EOF
}