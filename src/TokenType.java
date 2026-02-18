/**
 * TokenType.java
 * Enumeration of token types - MATCHES MERMAID DFA DIAGRAM EXACTLY
 *
 */
public enum TokenType {

    // ── Literals (from DFA) ──────────────────────────────────────────────
    IDENTIFIER,     // [A-Z][a-z0-9_]*  e.g. Count, Variable_name
    INTEGER,        // [+-]?[0-9]+      e.g. 42, -5, +100
    FLOAT,          // [+-]?[0-9]+\.[0-9]+  e.g. 3.14, -0.5
    FLOAT_EXP,      // FLOAT with e/E exponent  e.g. 1.5e10, 2.0E-3
    BOOLEAN,        // TRUE | FALSE (uppercase only, per DFA)

    // ── Operators (from DFA) ─────────────────────────────────────────────
    ARITH_OP,       // +  -  *  /  %
    REL_OP,         // ==  !=  <  >
    LOGICAL_OP,     // &&  ||  !
    ASSIGN_OP,      // =  +=  -=  *=  /=
    INC_DEC,        // ++  --

    // ── Structure (from DFA) ─────────────────────────────────────────────
    PUNCTUATOR,     // ( ) { } [ ] , ; :
    COMMENT,        // ## single-line (DFA only shows this, not multi-line)

    // ── Special ──────────────────────────────────────────────────────────
    ERROR,          // unrecognised/malformed lexeme
    EOF             // end of input
}
