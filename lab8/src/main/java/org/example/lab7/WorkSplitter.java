package org.example.lab7;

public final class WorkSplitter {
    private WorkSplitter() {
    }

    public static RowRange rangeFor(int rank, int processCount, int n) {
        int base = n / processCount;
        int remainder = n % processCount;
        int start = rank * base + Math.min(rank, remainder);
        int count = base + (rank < remainder ? 1 : 0);
        return new RowRange(start, count);
    }

    public static RowRange[] strips(RowRange range, int stripCount) {
        if (range.isEmpty()) {
            return new RowRange[0];
        }
        int parts = Math.min(Math.max(stripCount, 1), range.count);
        RowRange[] result = new RowRange[parts];
        int base = range.count / parts;
        int remainder = range.count % parts;
        int start = range.start;
        for (int i = 0; i < parts; i++) {
            int count = base + (i < remainder ? 1 : 0);
            result[i] = new RowRange(start, count);
            start += count;
        }
        return result;
    }
}
