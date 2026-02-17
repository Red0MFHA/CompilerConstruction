/**
 * TokenType.java
 * Enumeration of all token types recognized by the ManualScanner.
 *
 * CS4031 - Compiler Construction | Assignment 01
 */
public enum TokenType {

    // ── Literals ─────────────────────────────────────────────────────────
    KEYWORD,        // start, finish, loop, condition, declare, output, input,
                    // function, return, break, continue, else
    IDENTIFIER,     // [A-Z][a-z0-9_]{0,30}  e.g. Count, Total_sum
    INTEGER,        // [+-]?[0-9]+            e.g. 42, -5, +100
    FLOAT,          // [+-]?[0-9]+\.[0-9]{1,6}  e.g. 3.14, -0.5
    FLOAT_EXP,      // FLOAT with exponent    e.g. 1.5e10, 2.0E-3
    STRING,         // "(chars)*"             e.g. "hello\nworld"
    CHAR_LITERAL,   // '(char)'               e.g. 'a', '\n'
    BOOLEAN,        // true | false

    // ── Operators ────────────────────────────────────────────────────────
    ARITH_OP,       // +  -  *  /  %  **
    REL_OP,         // ==  !=  <  >  <=  >=
    LOGICAL_OP,     // &&  ||  !
    ASSIGN_OP,      // =  +=  -=  *=  /=
    INC_DEC,        // ++  --

    // ── Structure ────────────────────────────────────────────────────────
    PUNCTUATOR,     // ( ) { } [ ] , ; :
    COMMENT,        // ## single-line       #* ... *# multi-line

    // ── Special ──────────────────────────────────────────────────────────
    ERROR,          // unrecognised/malformed lexeme
    EOF             // end of input
}
