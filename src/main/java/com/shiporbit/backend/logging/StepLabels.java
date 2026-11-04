package com.shiporbit.backend.logging;

/** Step label segments: even depths are numbers (1, 2 ...), odd depths are letters (a ... z, aa, ab ...). */
public final class StepLabels {

    private StepLabels() {
    }

    /** depth 0 -> "3", depth 1 -> "c", depth 2 -> "3", depth 3 -> "c" ... (n starts at 1). */
    public static String segment(int depth, int n) {
        return depth % 2 == 0 ? Integer.toString(n) : letters(n);
    }

    /** 1 -> a, 26 -> z, 27 -> aa, 28 -> ab (spreadsheet-column style). */
    static String letters(int n) {
        StringBuilder sb = new StringBuilder();
        int x = n;
        while (x > 0) {
            x--;
            sb.append((char) ('a' + x % 26));
            x /= 26;
        }
        return sb.reverse().toString();
    }
}
