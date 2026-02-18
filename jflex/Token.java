public class Token {
    public TokenType type; // Ensure you have a TokenType enum defined
    public String lexeme;
    public int line;
    public int column;

    // Constructor compatible with both scanners
    public Token(TokenType type, String lexeme, int line, int column) {
        this.type = type;
        this.lexeme = lexeme;
        this.line = line;
        this.column = column;
    }

    @Override
    public String toString() {
        return String.format("Token: %-10s | Lexeme: '%s' | Line: %d, Col: %d",
                type, lexeme, line + 1, column + 1);
    }
}