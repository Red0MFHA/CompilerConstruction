/**
 * ManualScanner.java
 * ─────────────────────────────────────────────────────────────────────────
 * Minimal DFA-based Lexical Analyzer
 * 
 * Implements ONLY these 7 token categories:
 *   1. Integer Literal         [+-]?[0-9]+
 *   2. Identifier              [A-Z][a-z0-9_]*
 *   3. Single-line Comment     ##.*
 *   4. Floating Point          [+-]?[0-9]+\.[0-9]+(e[+-]?[0-9]+)?
 *   5. Boolean                 TRUE | FALSE
 *   6. Operators:
 *      - Relational: == != < <= > >=
 *      - Arithmetic: + - * / % **
 *      - Logical: && || !
 *      - Assignment: = += -= *= /=
 *      - Inc/Dec: ++ --
 *   7. Punctuators:            [ ] ( ) { } , ; :
 *
 * CS4031 – Compiler Construction | Assignment 01
 */

import java.io.*;
import java.nio.file.*;
import java.util.*;


public class ManualScanner {

    // ════════════════════════════════════════════════════════════════════
    //  DFA STATE ENUM
    // ════════════════════════════════════════════════════════════════════

    private enum DFAState {
        START,
        DEAD_STATE,

        // Numeric path
        SIGN_OR_ARITH,      // Seen +/- (accepting: ARITH_OP)
        INT_OR_FLOAT,       // Reading digits (accepting: INTEGER)
        FLOAT_FRAC,         // After '.' (not accepting yet)
        FLOAT_FINAL,        // Has decimal digits (accepting: FLOAT)
        FLOAT_EXP_START,    // After 'e'/'E' (not accepting)
        FLOAT_EXP_FINAL,    // Has exponent (accepting: FLOAT_EXP)

        // Identifier / Boolean path
        T_OR_IDENT,         // Starts with 'T' (accepting: IDENTIFIER)
        TRUE_R,             // "TR"
        TRUE_RU,            // "TRU"
        
        F_OR_IDENT,         // Starts with 'F' (accepting: IDENTIFIER)
        FALSE_A,            // "FA"
        FALSE_AL,           // "FAL"
        FALSE_ALS,          // "FALS"
        
        IDENT_FINAL,        // Generic identifier (accepting: IDENTIFIER)
        BOOL_FINAL,         // TRUE or FALSE (accepting: BOOLEAN)

        // Comment path
        COMMENT_H1,         // Seen first '#'
        COMMENT_BODY,       // Reading comment text
        COMMENT_FINAL,      // After newline (accepting: COMMENT)

        // Operator accepting states
        ARITH_FINAL,        // Accepting: ARITH_OP
        REL_OP_FINAL,       // Accepting: REL_OP
        LOGICAL_FINAL,      // Accepting: LOGICAL_OP
        ASSIGN_FINAL,       // Accepting: ASSIGN_OP
        INC_DEC_FINAL,      // Accepting: INC_DEC
        PUNCT_FINAL         // Accepting: PUNCTUATOR
    }

    // ════════════════════════════════════════════════════════════════════
    //  INSTANCE FIELDS
    // ════════════════════════════════════════════════════════════════════

    private final String        source;
    private       int           pos;
    private       int           line;
    private       int           col;

    private final List<Token>   tokens     = new ArrayList<>();
    private final SymbolTable   symTable   = new SymbolTable();
    private final ErrorHandler  errHandler = new ErrorHandler();

    // ════════════════════════════════════════════════════════════════════
    //  CONSTRUCTOR
    // ════════════════════════════════════════════════════════════════════

    public ManualScanner(String source) {
        this.source = source;
        this.pos    = 0;
        this.line   = 1;
        this.col    = 1;
    }

    // ════════════════════════════════════════════════════════════════════
    //  PUBLIC API
    // ════════════════════════════════════════════════════════════════════

    public void scan() {
        while (pos < source.length()) {
            // Skip whitespace
            if (isWhitespace(current())) {
                skipWhitespace();
                continue;
            }

            Token token = nextToken();
            if (token == null) continue; // Error already reported

            // Record identifiers in symbol table
            if (token.getType() == TokenType.IDENTIFIER) {
                symTable.record(token);
            }

            tokens.add(token);
        }

        tokens.add(new Token(TokenType.EOF, "<EOF>", line, col));
    }

    public List<Token> getTokens()          { return Collections.unmodifiableList(tokens); }
    public SymbolTable getSymbolTable()     { return symTable;   }
    public ErrorHandler getErrorHandler()   { return errHandler; }
    public int getLineCount()               { return line;       }

    // ════════════════════════════════════════════════════════════════════
    //  TOKEN DISPATCHER
    // ════════════════════════════════════════════════════════════════════

    private Token nextToken() {
        char c    = current();
        int  sLin = line;
        int  sCol = col;

        // ── 3. Single-line comment: ## ───────────────────────────────────
        if (c == '#') {
            char la = lookahead(1);
            if (la == '#') return scanComment(sLin, sCol);
            // Single '#' → error
            errHandler.reportInvalidChar(sLin, sCol, c);
            advance();
            return null;
        }

        // ── 1 & 4. Numbers: digit or signed number ───────────────────────
        if (Character.isDigit(c)) {
            return scanNumber(false, sLin, sCol);
        }

        // ── 6. Operators starting with + or - ────────────────────────────
        if (c == '+' || c == '-') {
            return handlePlusMinus(sLin, sCol);
        }

        // ── 2 & 5. Identifier / Boolean: [A-Z] ───────────────────────────
        if (Character.isUpperCase(c)) {
            return scanIdentifier(sLin, sCol);
        }

        // ── 6. Arithmetic: * / % (including **) ───────────────────────────
        if (c == '*' || c == '/' || c == '%') {
            return handleArithOp(sLin, sCol);
        }

        // ── 6. Relational/Assignment: = ! < > ─────────────────────────────
        if (c == '=' || c == '!' || c == '<' || c == '>') {
            return handleComparison(sLin, sCol);
        }

        // ── 6. Logical: && || ─────────────────────────────────────────────
        if (c == '&' || c == '|') {
            return handleLogical(sLin, sCol);
        }

        // ── 7. Punctuators ─────────────────────────────────────────────────
        if (isPunctuator(c)) {
            advance();
            return new Token(TokenType.PUNCTUATOR, String.valueOf(c), sLin, sCol);
        }

        // ── Unrecognized character → error ─────────────────────────────────
        errHandler.reportInvalidChar(sLin, sCol, c);
        advance();
        return null;
    }

    // ════════════════════════════════════════════════════════════════════
    //  CATEGORY 1 & 4: INTEGER and FLOATING POINT
    // ════════════════════════════════════════════════════════════════════

    /**
     * Scans: [+-]?[0-9]+(\.[0-9]+(e[+-]?[0-9]+)?)?
     * 
     * Tokens produced:
     *   - INTEGER:    42, +100, -5
     *   - FLOAT:      3.14, -0.5
     *   - FLOAT_EXP:  1.5e10, 2.0E-3
     */
    private Token scanNumber(boolean hasSign, int sLin, int sCol) {
        StringBuilder sb = new StringBuilder();

        // Optional sign
        if (hasSign) {
            sb.append(advance());
        }

        // Integer part: consume all digits
        while (pos < source.length() && Character.isDigit(current())) {
            sb.append(advance());
        }

        // Check for decimal point
        if (pos < source.length() && current() == '.') {
            char afterDot = lookahead(1);
            if (!Character.isDigit(afterDot)) {
                // e.g. "42." with no digit after → stay as INTEGER
                return new Token(TokenType.INTEGER, sb.toString(), sLin, sCol);
            }

            sb.append(advance()); // '.'

            // Fractional part
            while (pos < source.length() && Character.isDigit(current())) {
                sb.append(advance());
            }

            // Check for exponent
            if (pos < source.length() && (current() == 'e' || current() == 'E')) {
                sb.append(advance()); // 'e' or 'E'

                // Optional +/- in exponent
                if (pos < source.length() && (current() == '+' || current() == '-')) {
                    sb.append(advance());
                }

                // Exponent digits (required)
                if (pos < source.length() && Character.isDigit(current())) {
                    while (pos < source.length() && Character.isDigit(current())) {
                        sb.append(advance());
                    }
                    return new Token(TokenType.FLOAT_EXP, sb.toString(), sLin, sCol);
                } else {
                    // 'e' with no digits → error
                    errHandler.reportMalformedFloat(sLin, sCol, sb.toString());
                    return new Token(TokenType.ERROR, sb.toString(), sLin, sCol);
                }
            }

            return new Token(TokenType.FLOAT, sb.toString(), sLin, sCol);
        }

        // No decimal point → INTEGER
        return new Token(TokenType.INTEGER, sb.toString(), sLin, sCol);
    }

    // ════════════════════════════════════════════════════════════════════
    //  CATEGORY 2 & 5: IDENTIFIER and BOOLEAN
    // ════════════════════════════════════════════════════════════════════

    /**
     * Scans: [A-Z][a-z0-9_]*
     * 
     * Special cases:
     *   - TRUE  → BOOLEAN token
     *   - FALSE → BOOLEAN token
     *   - All other uppercase starts → IDENTIFIER
     */
    private Token scanIdentifier(int sLin, int sCol) {
        StringBuilder sb = new StringBuilder();
        DFAState st;

        char first = advance();
        sb.append(first);

        // Set initial state
        if      (first == 'T') st = DFAState.T_OR_IDENT;
        else if (first == 'F') st = DFAState.F_OR_IDENT;
        else                   st = DFAState.IDENT_FINAL;

        // Walk the DFA
        outer:
        while (pos < source.length()) {
            char c = current();

            switch (st) {
                // ── TRUE path: T → R → U → E ──────────────────────────
                case T_OR_IDENT:
                    if (c == 'R') {
                        sb.append(advance());
                        st = DFAState.TRUE_R;
                    } else if (isIdentChar(c)) {
                        sb.append(advance());
                        st = DFAState.IDENT_FINAL;
                    } else {
                        break outer;
                    }
                    break;

                case TRUE_R:
                    if (c == 'U') {
                        sb.append(advance());
                        st = DFAState.TRUE_RU;
                    } else if (isIdentChar(c)) {
                        sb.append(advance());
                        st = DFAState.IDENT_FINAL;
                    } else {
                        break outer;
                    }
                    break;

                case TRUE_RU:
                    if (c == 'E') {
                        sb.append(advance());
                        st = DFAState.BOOL_FINAL;
                        break outer; // Complete TRUE
                    } else if (isIdentChar(c)) {
                        sb.append(advance());
                        st = DFAState.IDENT_FINAL;
                    } else {
                        break outer;
                    }
                    break;

                // ── FALSE path: F → A → L → S → E ─────────────────────
                case F_OR_IDENT:
                    if (c == 'A') {
                        sb.append(advance());
                        st = DFAState.FALSE_A;
                    } else if (isIdentChar(c)) {
                        sb.append(advance());
                        st = DFAState.IDENT_FINAL;
                    } else {
                        break outer;
                    }
                    break;

                case FALSE_A:
                    if (c == 'L') {
                        sb.append(advance());
                        st = DFAState.FALSE_AL;
                    } else if (isIdentChar(c)) {
                        sb.append(advance());
                        st = DFAState.IDENT_FINAL;
                    } else {
                        break outer;
                    }
                    break;

                case FALSE_AL:
                    if (c == 'S') {
                        sb.append(advance());
                        st = DFAState.FALSE_ALS;
                    } else if (isIdentChar(c)) {
                        sb.append(advance());
                        st = DFAState.IDENT_FINAL;
                    } else {
                        break outer;
                    }
                    break;

                case FALSE_ALS:
                    if (c == 'E') {
                        sb.append(advance());
                        st = DFAState.BOOL_FINAL;
                        break outer; // Complete FALSE
                    } else if (isIdentChar(c)) {
                        sb.append(advance());
                        st = DFAState.IDENT_FINAL;
                    } else {
                        break outer;
                    }
                    break;

                // ── Generic identifier ─────────────────────────────────
                case IDENT_FINAL:
                    if (isIdentChar(c)) {
                        sb.append(advance());
                    } else {
                        break outer;
                    }
                    break;

                default:
                    break outer;
            }
        }

        // Return BOOLEAN if we reached BOOL_FINAL
        if (st == DFAState.BOOL_FINAL) {
            return new Token(TokenType.BOOLEAN, sb.toString(), sLin, sCol);
        }

        // Otherwise IDENTIFIER
        return new Token(TokenType.IDENTIFIER, sb.toString(), sLin, sCol);
    }

    // ════════════════════════════════════════════════════════════════════
    //  CATEGORY 3: SINGLE-LINE COMMENT
    // ════════════════════════════════════════════════════════════════════

    /**
     * Scans: ##.*\n
     */
    private Token scanComment(int sLin, int sCol) {
        StringBuilder sb = new StringBuilder();
        sb.append(advance()); // '#'
        sb.append(advance()); // '#'

        // Consume until newline
        while (pos < source.length() && current() != '\n') {
            sb.append(advance());
        }

        // Consume the newline
        if (pos < source.length() && current() == '\n') {
            sb.append(advance());
        }

        return new Token(TokenType.COMMENT, sb.toString(), sLin, sCol);
    }

    // ════════════════════════════════════════════════════════════════════
    //  CATEGORY 6: OPERATORS
    // ════════════════════════════════════════════════════════════════════

    // ── 6.2 & 6.4 & 6.5: + or - ──────────────────────────────────────────
    /**
     * Handles:
     *   ++    INC_DEC
     *   --    INC_DEC
     *   +=    ASSIGN_OP
     *   -=    ASSIGN_OP
     *   +123  Signed INTEGER/FLOAT
     *   +     ARITH_OP
     *   -     ARITH_OP
     */
    private Token handlePlusMinus(int sLin, int sCol) {
        char c  = current();
        char la = lookahead(1);

        // ++ or --
        if ((c == '+' && la == '+') || (c == '-' && la == '-')) {
            String lex = "" + advance() + advance();
            return new Token(TokenType.INC_DEC, lex, sLin, sCol);
        }

        // += or -=
        if (la == '=') {
            String lex = "" + advance() + advance();
            return new Token(TokenType.ASSIGN_OP, lex, sLin, sCol);
        }

        // Signed number
        if (Character.isDigit(la)) {
            return scanNumber(true, sLin, sCol);
        }

        // Single + or -
        advance();
        return new Token(TokenType.ARITH_OP, String.valueOf(c), sLin, sCol);
    }

    // ── 6.2 & 6.4: * / % ─────────────────────────────────────────────────
    /**
     * Handles:
     *   **    ARITH_OP (exponentiation)
     *   *=    ASSIGN_OP
     *   /=    ASSIGN_OP
     *   *     ARITH_OP
     *   /     ARITH_OP
     *   %     ARITH_OP
     */
    private Token handleArithOp(int sLin, int sCol) {
        char c  = current();
        char la = lookahead(1);

        // ** (exponentiation)
        if (c == '*' && la == '*') {
            String lex = "" + advance() + advance();
            return new Token(TokenType.ARITH_OP, lex, sLin, sCol);
        }

        // *= or /=
        if ((c == '*' || c == '/') && la == '=') {
            String lex = "" + advance() + advance();
            return new Token(TokenType.ASSIGN_OP, lex, sLin, sCol);
        }

        // Single *, /, or %
        advance();
        return new Token(TokenType.ARITH_OP, String.valueOf(c), sLin, sCol);
    }

    // ── 6.1 & 6.4: = ! < > ──────────────────────────────────────────────
    /**
     * Handles:
     *   ==    REL_OP
     *   !=    REL_OP
     *   <=    REL_OP
     *   >=    REL_OP
     *   <     REL_OP
     *   >     REL_OP
     *   =     ASSIGN_OP
     *   !     LOGICAL_OP
     */
    private Token handleComparison(int sLin, int sCol) {
        char c  = current();
        char la = lookahead(1);

        // == or !=
        if ((c == '=' && la == '=') || (c == '!' && la == '=')) {
            String lex = "" + advance() + advance();
            return new Token(TokenType.REL_OP, lex, sLin, sCol);
        }

        // <= or >=
        if ((c == '<' && la == '=') || (c == '>' && la == '=')) {
            String lex = "" + advance() + advance();
            return new Token(TokenType.REL_OP, lex, sLin, sCol);
        }

        // Single <  or >
        if (c == '<' || c == '>') {
            advance();
            return new Token(TokenType.REL_OP, String.valueOf(c), sLin, sCol);
        }

        // Single =
        if (c == '=' && la != '=') {
            advance();
            return new Token(TokenType.ASSIGN_OP, "=", sLin, sCol);
        }

        // Single ! (not !=)
        if (c == '!') {
            advance();
            return new Token(TokenType.LOGICAL_OP, "!", sLin, sCol);
        }

        // Shouldn't reach here
        errHandler.reportInvalidChar(sLin, sCol, c);
        advance();
        return null;
    }

    // ── 6.3: && || ───────────────────────────────────────────────────────
    /**
     * Handles:
     *   &&    LOGICAL_OP
     *   ||    LOGICAL_OP
     * 
     * Single & or | → error
     */
    private Token handleLogical(int sLin, int sCol) {
        char c  = current();
        char la = lookahead(1);

        // && or ||
        if ((c == '&' && la == '&') || (c == '|' && la == '|')) {
            String lex = "" + advance() + advance();
            return new Token(TokenType.LOGICAL_OP, lex, sLin, sCol);
        }

        // Single & or | → error
        errHandler.reportInvalidChar(sLin, sCol, c);
        advance();
        return null;
    }

    // ════════════════════════════════════════════════════════════════════
    //  UTILITY METHODS
    // ════════════════════════════════════════════════════════════════════

    private char current() {
        return source.charAt(pos);
    }

    private char lookahead(int offset) {
        int p = pos + offset;
        return (p < source.length()) ? source.charAt(p) : '\0';
    }

    private char advance() {
        char c = source.charAt(pos++);
        if (c == '\n') {
            line++;
            col = 1;
        } else {
            col++;
        }
        return c;
    }

    private void skipWhitespace() {
        while (pos < source.length() && isWhitespace(current())) {
            advance();
        }
    }

    private static boolean isWhitespace(char c) {
        return c == ' ' || c == '\t' || c == '\r' || c == '\n';
    }

    private static boolean isPunctuator(char c) {
        return "(){}[],;:".indexOf(c) >= 0;
    }

    private static boolean isIdentChar(char c) {
        // [a-z0-9_]
        return Character.isLowerCase(c) || Character.isDigit(c) || c == '_';
    }

    // ════════════════════════════════════════════════════════════════════
    //  OUTPUT METHODS
    // ════════════════════════════════════════════════════════════════════

    public void printTokens() {
        System.out.println("\n╔══════════════════════════════════════════════════════════════╗");
        System.out.println(  "║                        TOKEN STREAM                         ║");
        System.out.println(  "╠══════════════════════════════════════════════════════════════╣");
        for (Token t : tokens) {
            System.out.println("  " + t);
        }
        System.out.println(  "╚══════════════════════════════════════════════════════════════╝");
    }

    public void printStatistics() {
        Map<TokenType, Integer> counts = new EnumMap<>(TokenType.class);
        for (Token t : tokens) {
            counts.merge(t.getType(), 1, Integer::sum);
        }

        int commentCount = counts.getOrDefault(TokenType.COMMENT, 0);

        System.out.println("\n╔══════════════════════════════════════════════════════════════╗");
        System.out.println(  "║                         STATISTICS                          ║");
        System.out.println(  "╠══════════════════════════════════════════════════════════════╣");
        System.out.printf(   "  Total tokens            : %d%n", tokens.size());
        System.out.printf(   "  Lines processed         : %d%n", line);
        System.out.printf(   "  Comments removed        : %d%n", commentCount);
        System.out.printf(   "  Unique identifiers      : %d%n", symTable.size());
        System.out.printf(   "  Lexical errors          : %d%n", errHandler.errorCount());
        System.out.println(  "\n  Token breakdown:");
        System.out.printf(   "  %-20s  Count%n", "Type");
        System.out.println(  "  " + "─".repeat(30));
        for (TokenType tt : TokenType.values()) {
            int c = counts.getOrDefault(tt, 0);
            if (c > 0) {
                System.out.printf("  %-20s  %d%n", tt, c);
            }
        }
        System.out.println(  "╚══════════════════════════════════════════════════════════════╝");
    }
    public String getTokensAsString() {
        StringBuilder sb = new StringBuilder();

        sb.append("\n╔══════════════════════════════════════════════════════════════╗\n");
        sb.append(  "║                        TOKEN STREAM                         ║\n");
        sb.append(  "╠══════════════════════════════════════════════════════════════╣\n");

        for (Token t : tokens) {
            sb.append("  ").append(t).append("\n");
        }

        sb.append(  "╚══════════════════════════════════════════════════════════════╝\n");

        return sb.toString();
    }
    public String getStatisticsAsString() {

        Map<TokenType, Integer> counts = new EnumMap<>(TokenType.class);
        for (Token t : tokens) {
            counts.merge(t.getType(), 1, Integer::sum);
        }

        int commentCount = counts.getOrDefault(TokenType.COMMENT, 0);

        StringBuilder sb = new StringBuilder();

        sb.append("\n╔══════════════════════════════════════════════════════════════╗\n");
        sb.append(  "║                         STATISTICS                          ║\n");
        sb.append(  "╠══════════════════════════════════════════════════════════════╣\n");

        sb.append(String.format("  Total tokens            : %d%n", tokens.size()));
        sb.append(String.format("  Lines processed         : %d%n", line));
        sb.append(String.format("  Comments removed        : %d%n", commentCount));
        sb.append(String.format("  Unique identifiers      : %d%n", symTable.size()));
        sb.append(String.format("  Lexical errors          : %d%n", errHandler.errorCount()));

        sb.append("\n  Token breakdown:\n");
        sb.append(String.format("  %-20s  Count%n", "Type"));
        sb.append("  ").append("─".repeat(30)).append("\n");

        for (TokenType tt : TokenType.values()) {
            int c = counts.getOrDefault(tt, 0);
            if (c > 0) {
                sb.append(String.format("  %-20s  %d%n", tt, c));
            }
        }

        sb.append("╚══════════════════════════════════════════════════════════════╝\n");

        return sb.toString();
    }

    // ════════════════════════════════════════════════════════════════════
    //  MAIN - TEST DRIVER
    // ════════════════════════════════════════════════════════════════════

//    public static void main(String[] args) {
//        String source;
//
//        // ──────────────────────────────────────────────────────────────────
//        // OPTION 1: HARDCODED FILE PATH (easy for IntelliJ "Run" button)
//        // ──────────────────────────────────────────────────────────────────
//        // Uncomment the line below and put your test file path here:
//        String HARDCODED_PATH = "D:/Work/JAVA/COmpiler construction/COMPILER_CONSTRUCTION_PROJECT/test1.lang";
//
//        // For Windows: use double backslashes    "C:\\Users\\..\\test.txt"
//        // For Mac/Linux: use forward slashes     "/Users/../test.txt"
//        //String HARDCODED_PATH = null;  // Set to null to disable
//
//        if (HARDCODED_PATH != null) {
//            // Use hardcoded path
//            try {
//                source = new String(Files.readAllBytes(Paths.get(HARDCODED_PATH)));
//                System.out.println("Reading from: " + HARDCODED_PATH);
//            } catch (IOException e) {
//                System.err.println("Error reading file: " + e.getMessage());
//                System.err.println("Make sure the path is correct!");
//                return;
//            }
//        } else if (args.length > 0) {
//            // Use command-line argument
//            try {
//                source = new String(Files.readAllBytes(Paths.get(args[0])));
//                System.out.println("Reading from: " + args[0]);
//            } catch (IOException e) {
//                System.err.println("Error reading file: " + e.getMessage());
//                return;
//            }
//        } else {
//            // Use built-in mini test
//            source =
//                "## This is a comment\n" +
//                "Count = 42;\n" +
//                "Pi = 3.14159;\n" +
//                "Avogadro = 6.022e23;\n" +
//                "Flag = TRUE;\n" +
//                "Result = FALSE;\n" +
//                "X = X + 1;\n" +
//                "Y++;\n" +
//                "Z = A ** B;\n" +
//                "Test_var = 100;\n" +
//                "(X >= Y) && (Z <= 10);\n" +
//                "Array[5];\n";
//            System.out.println("Using built-in test code");
//        }
//
//        System.out.println("══════════════════════════════════════════════════════════════");
//        System.out.println("  CS4031 Manual Scanner – Minimal Implementation");
//        System.out.println("══════════════════════════════════════════════════════════════\n");
//
//        ManualScanner scanner = new ManualScanner(source);
//        scanner.scan();
//
//        scanner.printTokens();
//        scanner.printStatistics();
//        scanner.getSymbolTable().print();
//        scanner.getErrorHandler().printReport();
//    }
public static void main(String[] args) {

    // List of test files
    String[] testFiles = {
            "D:/Work/JAVA/COmpiler construction/COMPILER_CONSTRUCTION_PROJECT/test1.lang",
            "D:/Work/JAVA/COmpiler construction/COMPILER_CONSTRUCTION_PROJECT/test2.lang",
            "D:/Work/JAVA/COmpiler construction/COMPILER_CONSTRUCTION_PROJECT/test3.lang",
            "D:/Work/JAVA/COmpiler construction/COMPILER_CONSTRUCTION_PROJECT/test4.lang",
            "D:/Work/JAVA/COmpiler construction/COMPILER_CONSTRUCTION_PROJECT/test5.lang"
    };

    // Output file
    String outputFile = "D:/Work/JAVA/COmpiler construction/COMPILER_CONSTRUCTION_PROJECT/Test_Result.txt";

    try (PrintWriter writer = new PrintWriter(new FileWriter(outputFile))) {

        writer.println("══════════════════════════════════════════════════════════════");
        writer.println("        CS4031 Manual Scanner - All Test Results");
        writer.println("══════════════════════════════════════════════════════════════\n");

        for (String filePath : testFiles) {

            writer.println("------------------------------------------------------------");
            writer.println("Reading from: " + filePath);
            writer.println("------------------------------------------------------------");

            try {
                String source = new String(Files.readAllBytes(Paths.get(filePath)));

                ManualScanner scanner = new ManualScanner(source);
                scanner.scan();

                // Capture scanner output
                writer.println("\nTOKENS:");
                writer.println(scanner.getTokensAsString());

                writer.println("\nSTATISTICS:");
                writer.println(scanner.getStatisticsAsString());

                writer.println("\nSYMBOL TABLE:");
                writer.println(scanner.getSymbolTable().toString());

                writer.println("\nERROR REPORT:");
                writer.println(scanner.getErrorHandler().toString());

                writer.println("\n\n");

            } catch (IOException e) {
                writer.println("Error reading file: " + e.getMessage());
            }
        }

        System.out.println("All test results written to: " + outputFile);

    } catch (IOException e) {
        System.err.println("Error writing result file: " + e.getMessage());
    }
}
}
