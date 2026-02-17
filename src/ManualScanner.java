/**
 * ManualScanner.java
 * ─────────────────────────────────────────────────────────────────────────
 * DFA-based Lexical Analyzer for CS4031 – Compiler Construction, Assignment 01.
 *
 * DFA States are defined in the inner enum {@code DFAState} and correspond
 * directly to the states shown in the Mermaid minimized-DFA diagram:
 *
 *   START, SIGN_OR_ARITH, INT_OR_FLOAT, FLOAT_FRAC, FLOAT_FINAL,
 *   FLOAT_EXP_START, FLOAT_EXP_FINAL, T_OR_IDENT, F_OR_IDENT,
 *   IDENT_FINAL, TRUE_R, TRUE_RU, FALSE_A, FALSE_AL, FALSE_ALS,
 *   BOOL_FINAL, COMMENT_H1, COMMENT_BODY, COMMENT_FINAL, ... etc.
 *
 * Longest-match (maximal munch) is applied: the scanner always tries to
 * consume as many characters as possible before emitting a token.
 *
 * Pattern priority (Section 3.12 of spec):
 *   1. Multi-line comments   #* ... *#
 *   2. Single-line comments  ## ...
 *   3. Multi-char operators  ** == != <= >= && || ++ -- += -= *= /=
 *   4. Keywords
 *   5. Boolean literals
 *   6. Identifiers
 *   7. Floating-point literals
 *   8. Integer literals
 *   9. String / character literals
 *  10. Single-char operators
 *  11. Punctuators
 *  12. Whitespace (skipped; line/col tracked)
 *
 * Usage:
 *   ManualScanner scanner = new ManualScanner(sourceCode);
 *   scanner.scan();
 *   scanner.printTokens();
 *   scanner.printStatistics();
 *
 * CS4031 – Compiler Construction | Assignment 01
 */

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class ManualScanner {

    // ════════════════════════════════════════════════════════════════════
    //  DFA STATE ENUM  (mirrors Mermaid diagram states)
    // ════════════════════════════════════════════════════════════════════

    /**
     * Every state named here maps 1-to-1 with a node in the Mermaid
     * minimized DFA diagram.  Accepting states carry the token type they
     * emit in their Javadoc.
     */
    private enum DFAState {

        // ── Global ─────────────────────────────────────────────────────
        /** Initial state – no characters consumed yet. */
        START,
        /** Trap / dead state – no valid token possible from here. */
        DEAD_STATE,

        // ── Numeric path ───────────────────────────────────────────────
        /** Seen '+' or '-'. Accepting: ARITH_OP. */
        SIGN_OR_ARITH,
        /** Seen one or more digits (may be int or start of float). Accepting: INTEGER. */
        INT_OR_FLOAT,
        /** Seen digits then '.': e.g. "3." – NOT yet accepting. */
        FLOAT_FRAC,
        /** Seen digits '.' digits. Accepting: FLOAT. */
        FLOAT_FINAL,
        /** Seen 'e' or 'E' after float fraction – NOT yet accepting. */
        FLOAT_EXP_START,
        /** Seen optional sign after 'e/E' – NOT yet accepting. */
        FLOAT_EXP_SIGN,
        /** Seen complete float exponent. Accepting: FLOAT_EXP. */
        FLOAT_EXP_FINAL,

        // ── Identifier path ─────────────────────────────────────────────
        /** Seen uppercase 'T' – could grow into identifier or TRUE. Accepting: IDENTIFIER. */
        T_OR_IDENT,
        /** Seen uppercase 'F' – could grow into identifier or FALSE. Accepting: IDENTIFIER. */
        F_OR_IDENT,
        /** Seen [A-Z][a-z0-9_]* – Accepting: IDENTIFIER. */
        IDENT_FINAL,

        // ── Boolean TRUE branch ─────────────────────────────────────────
        /** Seen "TR" – not yet accepting. */
        TRUE_R,
        /** Seen "TRU" – not yet accepting. */
        TRUE_RU,

        // ── Boolean FALSE branch ────────────────────────────────────────
        /** Seen "FA" – not yet accepting. */
        FALSE_A,
        /** Seen "FAL" – not yet accepting. */
        FALSE_AL,
        /** Seen "FALS" – not yet accepting. */
        FALSE_ALS,

        /** Seen complete "TRUE" or "FALSE". Accepting: BOOLEAN. */
        BOOL_FINAL,

        // ── Lowercase word (keyword / boolean true|false) ───────────────
        /** Reading a sequence of lowercase letters/digits.  Accepting (pending check): KEYWORD or BOOLEAN. */
        LOWER_WORD,

        // ── Single-line comment  ## ... \n ─────────────────────────────
        /** Seen first '#'. */
        COMMENT_H1,
        /** Seen '##'. Reading comment body (non-newline chars). */
        COMMENT_BODY,
        /** Seen newline ending the comment. Accepting: COMMENT. */
        COMMENT_FINAL,

        // ── Multi-line comment  #* ... *# ──────────────────────────────
        /** Seen '#*'. */
        MULTI_COMMENT_START,
        /** Inside the multi-line comment body. */
        MULTI_COMMENT_BODY,
        /** Seen '*' inside body (candidate for closing). */
        MULTI_COMMENT_STAR,
        /** Seen '*#' – comment closed. Accepting: COMMENT. */
        MULTI_COMMENT_FINAL,

        // ── String literal  " ... " ─────────────────────────────────────
        /** Inside a string literal. */
        STRING_BODY,
        /** Seen '\' escape character inside string. */
        STRING_ESCAPE,
        /** Seen closing '"'. Accepting: STRING. */
        STRING_FINAL,

        // ── Character literal  ' . ' ────────────────────────────────────
        /** Inside a character literal (no content yet). */
        CHAR_BODY,
        /** Seen the '\' escape character inside char literal. */
        CHAR_ESCAPE,
        /** Seen the content character, awaiting closing '\''. */
        CHAR_CLOSE,
        /** Seen closing '\''. Accepting: CHAR_LITERAL. */
        CHAR_FINAL,

        // ── Operator intermediate states ────────────────────────────────
        /** Seen '='. */
        AFTER_EQ,
        /** Seen '!'. */
        AFTER_BANG,
        /** Seen '&'. */
        AFTER_AMP,
        /** Seen '|'. */
        AFTER_PIPE,
        /** Seen '*'. */
        AFTER_STAR,
        /** Seen '/'. */
        AFTER_SLASH,
        /** Seen '<'. */
        AFTER_LESS,
        /** Seen '>'. */
        AFTER_GREATER,

        // ── Operator accepting states ───────────────────────────────────
        /** Accepting: ARITH_OP  (*  /  %  **). */
        ARITH_FINAL,
        /** Accepting: REL_OP. */
        REL_OP_FINAL,
        /** Accepting: LOGICAL_OP. */
        LOGICAL_FINAL,
        /** Accepting: ASSIGN_OP. */
        ASSIGN_FINAL,
        /** Accepting: INC_DEC. */
        INC_DEC_FINAL,
        /** Accepting: PUNCTUATOR. */
        PUNCT_FINAL
    }

    // ════════════════════════════════════════════════════════════════════
    //  CONSTANTS
    // ════════════════════════════════════════════════════════════════════

    /** All reserved keywords of the language (lowercase, exact match). */
    private static final Set<String> KEYWORDS = new HashSet<>(Arrays.asList(
        "start", "finish", "loop", "condition", "declare", "output",
        "input", "function", "return", "break", "continue", "else"
    ));

    /** Valid single escape characters inside strings / char literals. */
    private static final Set<Character> VALID_ESCAPES = new HashSet<>(
        Arrays.asList('"', '\'', '\\', 'n', 't', 'r')
    );

    /** Maximum length of an identifier (including the leading uppercase). */
    private static final int MAX_IDENT_LEN = 31;

    // ════════════════════════════════════════════════════════════════════
    //  INSTANCE FIELDS
    // ════════════════════════════════════════════════════════════════════

    private final String        source;       // full source text
    private       int           pos;          // current character index
    private       int           line;         // 1-based current line
    private       int           col;          // 1-based current column

    private final List<Token>   tokens  = new ArrayList<>();
    private final SymbolTable   symTable;
    private final ErrorHandler  errHandler;

    // ════════════════════════════════════════════════════════════════════
    //  CONSTRUCTOR
    // ════════════════════════════════════════════════════════════════════

    public ManualScanner(String source) {
        this.source     = source;
        this.pos        = 0;
        this.line       = 1;
        this.col        = 1;
        this.symTable   = new SymbolTable();
        this.errHandler = new ErrorHandler();
    }

    // ════════════════════════════════════════════════════════════════════
    //  PUBLIC API
    // ════════════════════════════════════════════════════════════════════

    /**
     * Scan the entire source and populate {@code tokens}.
     * Priority order matches Section 3.12 of the assignment spec.
     */
    public void scan() {
        while (pos < source.length()) {

            // ── Whitespace (priority 12 – skip, track position) ─────────
            if (isWhitespace(current())) {
                skipWhitespace();
                continue;
            }

            Token token = nextToken();
            if (token == null) continue;          // error was already reported

            // Record identifiers in symbol table
            if (token.getType() == TokenType.IDENTIFIER) {
                symTable.record(token);
            }

            // Discard pure-whitespace / comment tokens if desired –
            // here we keep COMMENTs but that can be toggled.
            tokens.add(token);
        }

        tokens.add(new Token(TokenType.EOF, "<EOF>", line, col));
    }

    /** Return the immutable token list (scan() must be called first). */
    public List<Token> getTokens() {
        return Collections.unmodifiableList(tokens);
    }

    /** Return the symbol table (scan() must be called first). */
    public SymbolTable getSymbolTable() { return symTable; }

    /** Return the error handler (scan() must be called first). */
    public ErrorHandler getErrorHandler() { return errHandler; }

    // ════════════════════════════════════════════════════════════════════
    //  PRINTING HELPERS
    // ════════════════════════════════════════════════════════════════════

    /** Print every token on its own line. */
    public void printTokens() {
        System.out.println("\n╔══════════════════════════════════════════════════════════════╗");
        System.out.println(  "║                        TOKEN STREAM                         ║");
        System.out.println(  "╠══════════════════════════════════════════════════════════════╣");
        for (Token t : tokens) {
            System.out.println("  " + t);
        }
        System.out.println(  "╚══════════════════════════════════════════════════════════════╝");
    }

    /** Print per-type counts, total tokens, lines processed, comments removed. */
    public void printStatistics() {
        Map<TokenType, Integer> counts = new EnumMap<>(TokenType.class);
        for (Token t : tokens) {
            counts.merge(t.getType(), 1, Integer::sum);
        }

        int commentCount = counts.getOrDefault(TokenType.COMMENT, 0);

        System.out.println("\n╔══════════════════════════════════════════════════════════════╗");
        System.out.println(  "║                         STATISTICS                          ║");
        System.out.println(  "╠══════════════════════════════════════════════════════════════╣");
        System.out.printf(   "  Total tokens produced   : %d%n", tokens.size());
        System.out.printf(   "  Lines processed         : %d%n", line);
        System.out.printf(   "  Comments removed        : %d%n", commentCount);
        System.out.printf(   "  Unique identifiers      : %d%n", symTable.size());
        System.out.printf(   "  Lexical errors          : %d%n", errHandler.errorCount());
        System.out.println(  "\n  Per-type breakdown:");
        System.out.printf(   "  %-20s  Count%n", "Token Type");
        System.out.println(  "  " + "─".repeat(30));
        for (TokenType tt : TokenType.values()) {
            int c = counts.getOrDefault(tt, 0);
            if (c > 0) {
                System.out.printf("  %-20s  %d%n", tt, c);
            }
        }
        System.out.println(  "╚══════════════════════════════════════════════════════════════╝");
    }

    // ════════════════════════════════════════════════════════════════════
    //  CORE DISPATCH  (Priority order per spec Section 3.12)
    // ════════════════════════════════════════════════════════════════════

    /**
     * Dispatch to the correct scan sub-method based on the current
     * character and (when needed) one character of lookahead.
     *
     * Returns a {@code Token}, or {@code null} if the character was an
     * unrecoverable error (already reported) and was consumed.
     */
    private Token nextToken() {
        char c    = current();
        int  sLin = line;
        int  sCol = col;

        // ── Priority 1 & 2: Comments ─────────────────────────────────
        if (c == '#') {
            char la = lookahead(1);
            if (la == '*') return scanMultiLineComment(sLin, sCol);
            if (la == '#') return scanSingleLineComment(sLin, sCol);
            // bare '#' is not a valid token
            errHandler.reportInvalidChar(sLin, sCol, c);
            advance();
            return null;
        }

        // ── Priority 9: String / char literals ────────────────────────
        if (c == '"') return scanString(sLin, sCol);
        if (c == '\'') return scanCharLiteral(sLin, sCol);

        // ── Priority 3: Multi-char operators ─────────────────────────
        //    (handled inside scanOperator which also covers single-char ops)
        if (isOperatorChar(c)) return scanOperator(sLin, sCol);

        // ── Priority 6 & (4,5 via sub-check): Words ──────────────────
        //    Uppercase → identifier
        //    Lowercase → keyword or boolean
        if (Character.isUpperCase(c)) return scanIdentifier(sLin, sCol);
        if (Character.isLowerCase(c)) return scanLowercaseWord(sLin, sCol);

        // ── Priority 7 & 8: Numbers (digit start) ────────────────────
        if (Character.isDigit(c)) return scanNumber(false, sLin, sCol);

        // ── Priority 11: Punctuators ──────────────────────────────────
        if (isPunctuator(c)) {
            advance();
            return new Token(TokenType.PUNCTUATOR, String.valueOf(c), sLin, sCol);
        }

        // ── Nothing matched: invalid character ───────────────────────
        errHandler.reportInvalidChar(sLin, sCol, c);
        advance();
        return null;
    }

    // ════════════════════════════════════════════════════════════════════
    //  SCAN METHODS – one per token family
    // ════════════════════════════════════════════════════════════════════

    // ── 1. Multi-line comment  #* ... *# ─────────────────────────────────

    /**
     * DFA path:
     *   START → COMMENT_H1 ('#')
     *         → MULTI_COMMENT_START ('*')
     *         → MULTI_COMMENT_BODY (anything)
     *         → MULTI_COMMENT_STAR ('*')
     *         → MULTI_COMMENT_FINAL ('#')   [accepting: COMMENT]
     */
    private Token scanMultiLineComment(int sLin, int sCol) {
        StringBuilder sb  = new StringBuilder();
        DFAState      st  = DFAState.COMMENT_H1;

        // Consume '#' and '*'
        sb.append(advance()); // '#'
        sb.append(advance()); // '*'
        st = DFAState.MULTI_COMMENT_START;

        while (pos < source.length()) {
            char c = current();
            switch (st) {
                case MULTI_COMMENT_START:
                case MULTI_COMMENT_BODY:
                    if (c == '*') { st = DFAState.MULTI_COMMENT_STAR; }
                    else          { st = DFAState.MULTI_COMMENT_BODY; }
                    sb.append(advance());
                    break;

                case MULTI_COMMENT_STAR:
                    if (c == '#') {
                        sb.append(advance()); // '#'
                        return new Token(TokenType.COMMENT, sb.toString(), sLin, sCol);
                    } else if (c == '*') {
                        sb.append(advance()); // stay in STAR state
                    } else {
                        st = DFAState.MULTI_COMMENT_BODY;
                        sb.append(advance());
                    }
                    break;

                default:
                    sb.append(advance());
            }
        }

        // End-of-file with open comment
        errHandler.reportUnterminatedComment(sLin, sCol);
        return new Token(TokenType.ERROR, sb.toString(), sLin, sCol);
    }

    // ── 2. Single-line comment  ## ... \n ────────────────────────────────

    /**
     * DFA path:
     *   START → COMMENT_H1 ('#')
     *         → COMMENT_BODY ('#')
     *         → COMMENT_FINAL ('\n')     [accepting: COMMENT]
     */
    private Token scanSingleLineComment(int sLin, int sCol) {
        StringBuilder sb = new StringBuilder();
        sb.append(advance()); // '#'
        sb.append(advance()); // '#'
        // DFAState = COMMENT_BODY

        while (pos < source.length() && current() != '\n') {
            sb.append(advance());
        }

        // Consume the newline if present  → COMMENT_FINAL
        if (pos < source.length() && current() == '\n') {
            sb.append(advance());
        }

        return new Token(TokenType.COMMENT, sb.toString(), sLin, sCol);
    }

    // ── Operator scanner ─────────────────────────────────────────────────

    /**
     * Handles all operator tokens, including:
     *   Multi-char (priority 3):  **  ==  !=  <=  >=  &&  ||  ++  --  +=  -=  *=  /=
     *   Single-char (priority 10): +  -  *  /  %  =  !  <  >  &  |
     *
     * Also handles signed number literals when '+' or '-' precedes a digit.
     */
    private Token scanOperator(int sLin, int sCol) {
        char c  = current();
        char la = lookahead(1);

        // ── INC_DEC: ++ or -- ────────────────────────────────────────
        if ((c == '+' && la == '+') || (c == '-' && la == '-')) {
            String lex = "" + advance() + advance();
            return new Token(TokenType.INC_DEC, lex, sLin, sCol);
        }

        // ── Signed number? (+digit or -digit) ────────────────────────
        if ((c == '+' || c == '-') && Character.isDigit(la)) {
            return scanNumber(true, sLin, sCol);
        }

        // ── ASSIGN_OP: +=  -=  *=  /= ───────────────────────────────
        if ((c == '+' || c == '-' || c == '*' || c == '/') && la == '=') {
            String lex = "" + advance() + advance();
            return new Token(TokenType.ASSIGN_OP, lex, sLin, sCol);
        }

        // ── ARITH_OP: ** ─────────────────────────────────────────────
        if (c == '*' && la == '*') {
            String lex = "" + advance() + advance();
            return new Token(TokenType.ARITH_OP, lex, sLin, sCol);
        }

        // ── ASSIGN_OP: = (single) ────────────────────────────────────
        if (c == '=' && la != '=') {
            advance();
            return new Token(TokenType.ASSIGN_OP, "=", sLin, sCol);
        }

        // ── REL_OP: ==  !=  <=  >= ───────────────────────────────────
        if ((c == '=' && la == '=') ||
            (c == '!' && la == '=') ||
            (c == '<' && la == '=') ||
            (c == '>' && la == '=')) {
            String lex = "" + advance() + advance();
            return new Token(TokenType.REL_OP, lex, sLin, sCol);
        }

        // ── REL_OP: <  > (single) ────────────────────────────────────
        if (c == '<' || c == '>') {
            advance();
            return new Token(TokenType.REL_OP, String.valueOf(c), sLin, sCol);
        }

        // ── LOGICAL_OP: &&  || ───────────────────────────────────────
        if ((c == '&' && la == '&') || (c == '|' && la == '|')) {
            String lex = "" + advance() + advance();
            return new Token(TokenType.LOGICAL_OP, lex, sLin, sCol);
        }

        // ── LOGICAL_OP: ! (single, not followed by =) ────────────────
        if (c == '!' && la != '=') {
            advance();
            return new Token(TokenType.LOGICAL_OP, "!", sLin, sCol);
        }

        // ── Single-char ARITH_OP: + - * / % ─────────────────────────
        if (c == '+' || c == '-' || c == '*' || c == '/' || c == '%') {
            advance();
            return new Token(TokenType.ARITH_OP, String.valueOf(c), sLin, sCol);
        }

        // ── Bare '&' or '|' – not doubled: error ─────────────────────
        errHandler.reportInvalidChar(sLin, sCol, c);
        advance();
        return null;
    }

    // ── Number (Integer & Float) ──────────────────────────────────────────

    /**
     * DFA path (from Mermaid diagram):
     *
     *   START ──(digit)──────────────────────→ INT_OR_FLOAT  [INTEGER]
     *   START ──(+/-)──→ SIGN_OR_ARITH ──(digit)─→ INT_OR_FLOAT  [INTEGER]
     *   INT_OR_FLOAT ──(digit)──→ INT_OR_FLOAT   (loop)
     *   INT_OR_FLOAT ──(.)──────→ FLOAT_FRAC     (non-accepting)
     *   FLOAT_FRAC   ──(digit)──→ FLOAT_FINAL    [FLOAT]
     *   FLOAT_FINAL  ──(digit)──→ FLOAT_FINAL    (loop)
     *   FLOAT_FINAL  ──(e|E)───→ FLOAT_EXP_START (non-accepting)
     *   FLOAT_EXP_START ──(+/-)─→ FLOAT_EXP_SIGN
     *   FLOAT_EXP_START ──(digit)→ FLOAT_EXP_FINAL [FLOAT_EXP]
     *   FLOAT_EXP_SIGN ──(digit)→ FLOAT_EXP_FINAL  [FLOAT_EXP]
     *   FLOAT_EXP_FINAL ──(digit)→ FLOAT_EXP_FINAL (loop)
     *
     * @param hasSign true if the caller already determined a leading +/- exists
     */
    private Token scanNumber(boolean hasSign, int sLin, int sCol) {
        StringBuilder sb   = new StringBuilder();
        DFAState      st   = DFAState.START;

        // ── Optional sign (SIGN_OR_ARITH → INT_OR_FLOAT) ─────────────
        if (hasSign) {
            sb.append(advance()); // consume '+' or '-'
            st = DFAState.SIGN_OR_ARITH;
        }

        // ── Consume leading digits → INT_OR_FLOAT ────────────────────
        if (pos < source.length() && Character.isDigit(current())) {
            while (pos < source.length() && Character.isDigit(current())) {
                sb.append(advance());
            }
            st = DFAState.INT_OR_FLOAT;
        } else {
            // Sign with no following digit (shouldn't happen here)
            return new Token(TokenType.ARITH_OP, sb.toString(), sLin, sCol);
        }

        // ── '.' → FLOAT_FRAC ─────────────────────────────────────────
        if (pos < source.length() && current() == '.') {
            char afterDot = lookahead(1);
            if (!Character.isDigit(afterDot)) {
                // e.g. "3."  – malformed float; emit as INTEGER and flag
                errHandler.reportMalformedFloat(sLin, sCol, sb.toString() + ".");
                return new Token(TokenType.INTEGER, sb.toString(), sLin, sCol);
            }
            sb.append(advance()); // '.'
            st = DFAState.FLOAT_FRAC;

            // Count decimal digits (max 6 per spec) → FLOAT_FINAL
            int decimals = 0;
            while (pos < source.length() && Character.isDigit(current())) {
                sb.append(advance());
                decimals++;
            }
            if (decimals == 0) {
                errHandler.reportMalformedFloat(sLin, sCol, sb.toString());
                return new Token(TokenType.ERROR, sb.toString(), sLin, sCol);
            }
            if (decimals > 6) {
                errHandler.reportMalformedFloat(sLin, sCol, sb.toString());
                // Still emit as FLOAT but flag the error
                return new Token(TokenType.FLOAT, sb.toString(), sLin, sCol);
            }
            st = DFAState.FLOAT_FINAL;

            // ── Optional exponent → FLOAT_EXP_START ──────────────────
            if (pos < source.length() && (current() == 'e' || current() == 'E')) {
                sb.append(advance()); // 'e' or 'E'
                st = DFAState.FLOAT_EXP_START;

                // Optional +/- sign → FLOAT_EXP_SIGN
                if (pos < source.length() && (current() == '+' || current() == '-')) {
                    sb.append(advance());
                    st = DFAState.FLOAT_EXP_SIGN;
                }

                // Must have at least one digit → FLOAT_EXP_FINAL
                if (pos < source.length() && Character.isDigit(current())) {
                    while (pos < source.length() && Character.isDigit(current())) {
                        sb.append(advance());
                    }
                    return new Token(TokenType.FLOAT_EXP, sb.toString(), sLin, sCol);
                } else {
                    errHandler.reportMalformedFloat(sLin, sCol, sb.toString());
                    return new Token(TokenType.ERROR, sb.toString(), sLin, sCol);
                }
            }

            return new Token(TokenType.FLOAT, sb.toString(), sLin, sCol);
        }

        // ── No '.': emit INTEGER ──────────────────────────────────────
        return new Token(TokenType.INTEGER, sb.toString(), sLin, sCol);
    }

    // ── Identifier  [A-Z][a-z0-9_]* ─────────────────────────────────────

    /**
     * DFA path (from Mermaid diagram):
     *
     *   START ──(A-Z excl T,F)──→ IDENT_FINAL  [IDENTIFIER]
     *   START ──(T)──────────────→ T_OR_IDENT   [IDENTIFIER]
     *   START ──(F)──────────────→ F_OR_IDENT   [IDENTIFIER]
     *
     *   T_OR_IDENT ──(R)──→ TRUE_R ──(U)──→ TRUE_RU ──(E)──→ BOOL_FINAL [BOOLEAN]
     *   F_OR_IDENT ──(A)──→ FALSE_A ──(L)──→ FALSE_AL ──(S)──→ FALSE_ALS
     *                                                          ──(E)──→ BOOL_FINAL [BOOLEAN]
     *
     *   Any intermediate state ──([a-z0-9_])──→ IDENT_FINAL (becomes identifier)
     *
     * NOTE: Identifiers in this language MUST start with uppercase [A-Z].
     *       The boolean keywords TRUE / FALSE are handled here.
     *       Lowercase 'true' / 'false' are handled by scanLowercaseWord().
     */
    private Token scanIdentifier(int sLin, int sCol) {
        StringBuilder sb = new StringBuilder();
        DFAState      st;

        char first = advance(); // consume first uppercase letter
        sb.append(first);

        // Set initial state based on first character
        if      (first == 'T') st = DFAState.T_OR_IDENT;
        else if (first == 'F') st = DFAState.F_OR_IDENT;
        else                   st = DFAState.IDENT_FINAL;

        // ── Walk the DFA ─────────────────────────────────────────────
        outer:
        while (pos < source.length()) {
            char c = current();

            switch (st) {

                // ── T_OR_IDENT ────────────────────────────────────────
                case T_OR_IDENT:
                    if (c == 'R') { sb.append(advance()); st = DFAState.TRUE_R; }
                    else if (Character.isLowerCase(c) || Character.isDigit(c) || c == '_')
                         { sb.append(advance()); st = DFAState.IDENT_FINAL; }
                    else break outer;
                    break;

                case TRUE_R:
                    if (c == 'U') { sb.append(advance()); st = DFAState.TRUE_RU; }
                    else if (Character.isLowerCase(c) || Character.isDigit(c) || c == '_')
                         { sb.append(advance()); st = DFAState.IDENT_FINAL; }
                    else break outer;
                    break;

                case TRUE_RU:
                    if (c == 'E') { sb.append(advance()); st = DFAState.BOOL_FINAL; break outer; }
                    else if (Character.isLowerCase(c) || Character.isDigit(c) || c == '_')
                         { sb.append(advance()); st = DFAState.IDENT_FINAL; }
                    else break outer;
                    break;

                // ── F_OR_IDENT ────────────────────────────────────────
                case F_OR_IDENT:
                    if (c == 'A') { sb.append(advance()); st = DFAState.FALSE_A; }
                    else if (Character.isLowerCase(c) || Character.isDigit(c) || c == '_')
                         { sb.append(advance()); st = DFAState.IDENT_FINAL; }
                    else break outer;
                    break;

                case FALSE_A:
                    if (c == 'L') { sb.append(advance()); st = DFAState.FALSE_AL; }
                    else if (Character.isLowerCase(c) || Character.isDigit(c) || c == '_')
                         { sb.append(advance()); st = DFAState.IDENT_FINAL; }
                    else break outer;
                    break;

                case FALSE_AL:
                    if (c == 'S') { sb.append(advance()); st = DFAState.FALSE_ALS; }
                    else if (Character.isLowerCase(c) || Character.isDigit(c) || c == '_')
                         { sb.append(advance()); st = DFAState.IDENT_FINAL; }
                    else break outer;
                    break;

                case FALSE_ALS:
                    if (c == 'E') { sb.append(advance()); st = DFAState.BOOL_FINAL; break outer; }
                    else if (Character.isLowerCase(c) || Character.isDigit(c) || c == '_')
                         { sb.append(advance()); st = DFAState.IDENT_FINAL; }
                    else break outer;
                    break;

                // ── IDENT_FINAL (keep consuming) ──────────────────────
                case IDENT_FINAL:
                    if (Character.isLowerCase(c) || Character.isDigit(c) || c == '_')
                         { sb.append(advance()); }
                    else break outer;
                    break;

                default:
                    break outer;
            }
        }

        // ── BOOL_FINAL branch: TRUE or FALSE ─────────────────────────
        if (st == DFAState.BOOL_FINAL) {
            return new Token(TokenType.BOOLEAN, sb.toString(), sLin, sCol);
        }

        // ── Otherwise: IDENTIFIER ─────────────────────────────────────
        String lex = sb.toString();
        if (lex.length() > MAX_IDENT_LEN) {
            errHandler.reportInvalidIdentifier(sLin, sCol, lex);
        }
        return new Token(TokenType.IDENTIFIER, lex, sLin, sCol);
    }

    // ── Lowercase word: keyword or boolean literal (true / false) ────────

    /**
     * Reads a run of lowercase letters/digits and classifies the lexeme as:
     *   KEYWORD  – if it matches one of the 12 reserved words
     *   BOOLEAN  – if it is "true" or "false"
     *   ERROR    – otherwise (identifier must start with uppercase)
     */
    private Token scanLowercaseWord(int sLin, int sCol) {
        StringBuilder sb = new StringBuilder();
        // DFAState = LOWER_WORD – keep consuming [a-z0-9]
        while (pos < source.length() &&
               (Character.isLowerCase(current()) || Character.isDigit(current()) || current() == '_')) {
            sb.append(advance());
        }

        String word = sb.toString();

        if (KEYWORDS.contains(word))
            return new Token(TokenType.KEYWORD, word, sLin, sCol);

        if (word.equals("true") || word.equals("false"))
            return new Token(TokenType.BOOLEAN, word, sLin, sCol);

        // Unknown lowercase word – not a valid identifier (must start uppercase)
        errHandler.reportInvalidIdentifier(sLin, sCol, word);
        return new Token(TokenType.ERROR, word, sLin, sCol);
    }

    // ── String literal  " ... " ──────────────────────────────────────────

    /**
     * DFA path:
     *   START ──(")──→ STRING_BODY
     *   STRING_BODY ──(\\)──→ STRING_ESCAPE ──(valid escape)──→ STRING_BODY
     *   STRING_BODY ──(")──→ STRING_FINAL  [accepting: STRING]
     */
    private Token scanString(int sLin, int sCol) {
        StringBuilder sb = new StringBuilder();
        sb.append(advance()); // opening '"'
        DFAState st = DFAState.STRING_BODY;

        while (pos < source.length()) {
            char c = current();

            if (c == '\n') {
                // Newline inside string without escape → error
                errHandler.reportUnterminatedString(sLin, sCol, sb.toString());
                return new Token(TokenType.ERROR, sb.toString(), sLin, sCol);
            }

            if (c == '\\') {
                sb.append(advance()); // '\'
                if (pos >= source.length()) break;
                char esc = current();
                if (VALID_ESCAPES.contains(esc)) {
                    sb.append(advance());
                } else {
                    // Invalid escape sequence – report and continue
                    errHandler.report(
                        ErrorHandler.ErrorType.INVALID_ESCAPE,
                        line, col,
                        "\\" + esc,
                        "Invalid escape sequence in string literal"
                    );
                    sb.append(advance());
                }
                continue;
            }

            if (c == '"') {
                sb.append(advance()); // closing '"'
                return new Token(TokenType.STRING, sb.toString(), sLin, sCol);
            }

            sb.append(advance());
        }

        errHandler.reportUnterminatedString(sLin, sCol, sb.toString());
        return new Token(TokenType.ERROR, sb.toString(), sLin, sCol);
    }

    // ── Character literal  ' . ' ─────────────────────────────────────────

    /**
     * DFA path:
     *   START ──(')──→ CHAR_BODY
     *   CHAR_BODY ──(\\)──→ CHAR_ESCAPE ──(valid esc)──→ CHAR_CLOSE
     *   CHAR_BODY ──(non-special)──→ CHAR_CLOSE
     *   CHAR_CLOSE ──(')──→ CHAR_FINAL   [accepting: CHAR_LITERAL]
     */
    private Token scanCharLiteral(int sLin, int sCol) {
        StringBuilder sb = new StringBuilder();
        sb.append(advance()); // opening '\''
        DFAState st = DFAState.CHAR_BODY;

        if (pos >= source.length()) {
            errHandler.reportUnterminatedChar(sLin, sCol, sb.toString());
            return new Token(TokenType.ERROR, sb.toString(), sLin, sCol);
        }

        char content = current();
        if (content == '\n' || content == '\'') {
            errHandler.reportUnterminatedChar(sLin, sCol, sb.toString());
            return new Token(TokenType.ERROR, sb.toString(), sLin, sCol);
        }

        if (content == '\\') {
            sb.append(advance()); // '\'
            if (pos < source.length() && VALID_ESCAPES.contains(current())) {
                sb.append(advance()); // valid escape char
            } else {
                errHandler.report(ErrorHandler.ErrorType.INVALID_ESCAPE,
                    line, col, sb.toString(), "Invalid escape in char literal");
            }
        } else {
            sb.append(advance()); // the single content character
        }

        st = DFAState.CHAR_CLOSE;

        // Expect closing '\''
        if (pos < source.length() && current() == '\'') {
            sb.append(advance());
            return new Token(TokenType.CHAR_LITERAL, sb.toString(), sLin, sCol);
        }

        // More than one character between quotes – skip to closing quote
        errHandler.report(ErrorHandler.ErrorType.INVALID_CHAR_LITERAL,
            sLin, sCol, sb.toString(), "Character literal must contain exactly one character");

        while (pos < source.length() && current() != '\'' && current() != '\n') {
            sb.append(advance());
        }
        if (pos < source.length() && current() == '\'') {
            sb.append(advance());
        }
        return new Token(TokenType.ERROR, sb.toString(), sLin, sCol);
    }

    // ════════════════════════════════════════════════════════════════════
    //  UTILITY / HELPER METHODS
    // ════════════════════════════════════════════════════════════════════

    /** Return current character without consuming it. */
    private char current() {
        return source.charAt(pos);
    }

    /**
     * Look ahead {@code offset} characters from current position.
     * Returns {@code '\0'} if that position is out of bounds.
     */
    private char lookahead(int offset) {
        int p = pos + offset;
        return (p < source.length()) ? source.charAt(p) : '\0';
    }

    /**
     * Consume the current character, advance position, update line/col,
     * and return the consumed character.
     */
    private char advance() {
        char c = source.charAt(pos++);
        if (c == '\n') { line++; col = 1; }
        else           { col++;           }
        return c;
    }

    /** Skip whitespace characters, updating line/col as we go. */
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

    private static boolean isOperatorChar(char c) {
        return "+-*/%=!<>&|".indexOf(c) >= 0;
    }

    // ════════════════════════════════════════════════════════════════════
    //  ENTRY POINT  –  main()
    // ════════════════════════════════════════════════════════════════════

    /**
     * Usage:
     *   java ManualScanner <source_file>
     *
     * If no argument is provided a short built-in test is run.
     */
    public static void main(String[] args) {
        String source;

        if (args.length > 0) {
            // Read source from file
            try {
                source = new String(Files.readAllBytes(Paths.get(args[0])));
            } catch (IOException e) {
                System.err.println("Error reading file: " + e.getMessage());
                return;
            }
        } else {
            // Built-in test program
            source =
                "## This is a single-line comment\n"
              + "#* This is a\n"
              + "   multi-line comment *#\n"
              + "declare Count = 42;\n"
              + "declare Pi = 3.14159;\n"
              + "declare Avogadro = 6.022e23;\n"
              + "declare Flag = true;\n"
              + "declare Msg = \"Hello\\nWorld\";\n"
              + "declare Ch = 'A';\n"
              + "loop (Count > 0) {\n"
              + "    Count = Count - 1;\n"
              + "    Count++;\n"
              + "    output(Count);\n"
              + "}\n"
              + "condition (Flag && Count == 0) {\n"
              + "    output(\"done\");\n"
              + "} else {\n"
              + "    output(\"not done\");\n"
              + "}\n";
        }

        System.out.println("══════════════════════════════════════════════════════════════");
        System.out.println("  CS4031 Manual Scanner – DFA-based Lexical Analyser");
        System.out.println("══════════════════════════════════════════════════════════════");

        ManualScanner scanner = new ManualScanner(source);
        scanner.scan();

        scanner.printTokens();
        scanner.printStatistics();
        scanner.getSymbolTable().print();
        scanner.getErrorHandler().printReport();
    }
}
