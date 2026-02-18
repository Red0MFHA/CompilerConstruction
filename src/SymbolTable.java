/**
 * SymbolTable.java
 * Tracks every IDENTIFIER token: name, inferred type, first occurrence,
 * and how many times it appears in the source.
 *
 * CS4031 - Compiler Construction | Assignment 01
 */
import java.util.LinkedHashMap;
import java.util.Map;

public class SymbolTable {
    //for file output
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        sb.append("\n╔══════════════════════════════════════════════════════════════╗\n");
        sb.append(  "║                        SYMBOL TABLE                         ║\n");
        sb.append(  "╠══════════════════════════════════════════════════════════════╣\n");

        sb.append(String.format("  %-25s | %-10s | %-14s | %s%n",
                "Identifier", "Type", "First Seen", "Freq"));
        sb.append("  ").append("─".repeat(60)).append("\n");

        for (Entry e : table.values()) {
            sb.append(e.toString()).append("\n");
        }

        sb.append("╚══════════════════════════════════════════════════════════════╝\n");

        return sb.toString();
    }


    // ── Inner entry class ────────────────────────────────────────────────
    public static class Entry {
        public final String name;
        public       String type;           // inferred from context (default: UNKNOWN)
        public final int    firstLine;
        public final int    firstCol;
        public       int    frequency;

        public Entry(String name, int firstLine, int firstCol) {
            this.name      = name;
            this.type      = "UNKNOWN";
            this.firstLine = firstLine;
            this.firstCol  = firstCol;
            this.frequency = 1;
        }

        @Override
        public String toString() {
            return String.format("  %-25s | %-10s | Line: %-4d  Col: %-4d | Freq: %d",
                    name, type, firstLine, firstCol, frequency);
        }
    }

    // ── Table storage (insertion-ordered) ────────────────────────────────
    private final Map<String, Entry> table = new LinkedHashMap<>();

    // ── Add / update ─────────────────────────────────────────────────────
    /**
     * Call this each time an IDENTIFIER token is produced.
     * If the identifier is new it is inserted; otherwise its frequency
     * counter is incremented.
     */
    public void record(Token token) {
        String key = token.getLexeme();
        if (table.containsKey(key)) {
            table.get(key).frequency++;
        } else {
            table.put(key, new Entry(key, token.getLine(), token.getCol()));
        }
    }

    /** Optionally set the inferred type for a symbol (parser may call this). */
    public void setType(String name, String type) {
        if (table.containsKey(name)) {
            table.get(name).type = type;
        }
    }

    /** Look up an identifier. Returns null if not present. */
    public Entry lookup(String name) {
        return table.get(name);
    }

    /** Number of distinct identifiers. */
    public int size() { return table.size(); }

    /** Get all entries for iteration (used by main for reporting). */
    public java.util.Collection<Entry> getAllEntries() {
        return table.values();
    }

    // ── Print ────────────────────────────────────────────────────────────
    public void print() {
        System.out.println("\n╔══════════════════════════════════════════════════════════════╗");
        System.out.println(  "║                        SYMBOL TABLE                         ║");
        System.out.println(  "╠══════════════════════════════════════════════════════════════╣");
        System.out.printf(   "  %-25s | %-10s | %-14s | %s%n",
                "Identifier", "Type", "First Seen", "Freq");
        System.out.println(  "  " + "─".repeat(60));
        for (Entry e : table.values()) {
            System.out.println(e);
        }
        System.out.println(  "╚══════════════════════════════════════════════════════════════╝");
    }
}
