/**
 * Token.java
 * Represents a single token produced by the ManualScanner.
 *
 * Output format:  <KEYWORD, "start", Line: 1, Col: 1>
 *
 * CS4031 - Compiler Construction | Assignment 01
 */
public class Token {

    private final TokenType type;
    private final String    lexeme;
    private final int       line;
    private final int       col;

    // ── Constructor ──────────────────────────────────────────────────────
    public Token(TokenType type, String lexeme, int line, int col) {
        this.type   = type;
        this.lexeme = lexeme;
        this.line   = line;
        this.col    = col;
    }

    // ── Getters ──────────────────────────────────────────────────────────
    public TokenType getType()   { return type;   }
    public String    getLexeme() { return lexeme; }
    public int       getLine()   { return line;   }
    public int       getCol()    { return col;    }

    // ── Formatted output  <TYPE, "lexeme", Line: L, Col: C> ─────────────
    @Override
    public String toString() {
        return String.format("<%s, \"%s\", Line: %d, Col: %d>",
                type, lexeme, line, col);
    }
}
