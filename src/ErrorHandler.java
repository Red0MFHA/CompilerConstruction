/**
 * ErrorHandler.java
 * Collects lexical errors and prints a formatted error report.
 * Supports continued scanning after errors (panic-mode recovery).
 *
 * Error format:  [LEXICAL ERROR] <type> at Line: L, Col: C  |  lexeme: "x"  |  reason
 *
 * CS4031 - Compiler Construction | Assignment 01
 */
import java.util.ArrayList;
import java.util.List;

public class ErrorHandler {

    // ── Error categories ─────────────────────────────────────────────────
    public enum ErrorType {
        INVALID_CHARACTER,      // @, $, etc. that cannot start any token
        MALFORMED_FLOAT,        // e.g. 3.  or  1.1234567 (>6 decimal places)
        MALFORMED_INTEGER,      // e.g. 1,000  (rare – caught as two tokens, but flag anyway)
        UNTERMINATED_STRING,    // "hello with no closing quote
        UNTERMINATED_CHAR,      // 'a  with no closing quote
        INVALID_CHAR_LITERAL,   // more than one character between single-quotes
        INVALID_ESCAPE,         // unrecognised escape sequence e.g. \q
        INVALID_IDENTIFIER,     // starts with digit, or exceeds 31 chars
        UNTERMINATED_COMMENT,   // #* with no matching *#
        UNKNOWN                 // catch-all
    }

    // ── Inner record ─────────────────────────────────────────────────────
    public static class LexicalError {
        public final ErrorType type;
        public final int       line;
        public final int       col;
        public final String    lexeme;
        public final String    reason;

        public LexicalError(ErrorType type, int line, int col,
                            String lexeme, String reason) {
            this.type   = type;
            this.line   = line;
            this.col    = col;
            this.lexeme = lexeme;
            this.reason = reason;
        }

        @Override
        public String toString() {
            return String.format("[LEXICAL ERROR] %-25s at Line: %-4d Col: %-4d | "
                               + "lexeme: \"%-15s\" | %s",
                    type, line, col, lexeme, reason);
        }
    }

    // ── Storage ──────────────────────────────────────────────────────────
    private final List<LexicalError> errors = new ArrayList<>();

    // ── Report ───────────────────────────────────────────────────────────
    public void report(ErrorType type, int line, int col,
                       String lexeme, String reason) {
        errors.add(new LexicalError(type, line, col, lexeme, reason));
    }

    /** Convenience – infer ErrorType from common patterns. */
    public void reportInvalidChar(int line, int col, char ch) {
        report(ErrorType.INVALID_CHARACTER, line, col,
               String.valueOf(ch),
               "Character '" + ch + "' is not a valid token start");
    }

    public void reportUnterminatedString(int line, int col, String partial) {
        report(ErrorType.UNTERMINATED_STRING, line, col, partial,
               "String literal has no closing double-quote");
    }

    public void reportUnterminatedChar(int line, int col, String partial) {
        report(ErrorType.UNTERMINATED_CHAR, line, col, partial,
               "Character literal has no closing single-quote");
    }

    public void reportUnterminatedComment(int line, int col) {
        report(ErrorType.UNTERMINATED_COMMENT, line, col, "#*",
               "Multi-line comment opened but never closed");
    }

    public void reportInvalidIdentifier(int line, int col, String lexeme) {
        if (lexeme.length() > 31) {
            report(ErrorType.INVALID_IDENTIFIER, line, col, lexeme,
                   "Identifier exceeds maximum length of 31 characters");
        } else {
            report(ErrorType.INVALID_IDENTIFIER, line, col, lexeme,
                   "Identifier must start with an uppercase letter [A-Z]");
        }
    }

    public void reportMalformedFloat(int line, int col, String lexeme) {
        report(ErrorType.MALFORMED_FLOAT, line, col, lexeme,
               "Float has more than 6 decimal places or missing fraction digits");
    }

    // ── Accessors ────────────────────────────────────────────────────────
    public boolean hasErrors()          { return !errors.isEmpty(); }
    public int     errorCount()         { return errors.size(); }
    public List<LexicalError> getErrors() { return errors; }

    // ── Print ────────────────────────────────────────────────────────────
    public void printReport() {
        if (errors.isEmpty()) {
            System.out.println("\n✔  No lexical errors detected.");
            return;
        }
        System.out.println("\n╔══════════════════════════════════════════════════════════════╗");
        System.out.println(  "║                     LEXICAL ERROR REPORT                    ║");
        System.out.println(  "╠══════════════════════════════════════════════════════════════╣");
        for (LexicalError e : errors) {
            System.out.println(e);
        }
        System.out.printf("%n  Total errors: %d%n", errors.size());
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
    }
}
