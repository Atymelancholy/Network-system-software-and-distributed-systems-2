package org.example.lab8;

import org.example.lab7.RowRange;
import org.example.lab7.WorkSplitter;

final class Counts {
    final int[] counts;
    final int[] displs;

    Counts(int[] counts, int[] displs) {
        this.counts = counts;
        this.displs = displs;
    }

    static Counts of(int processCount, int n) {
        int[] counts = new int[processCount];
        int[] displs = new int[processCount];
        for (int rank = 0; rank < processCount; rank++) {
            RowRange range = WorkSplitter.rangeFor(rank, processCount, n);
            counts[rank] = range.doubleCount(n);
            displs[rank] = range.doubleOffset(n);
        }
        return new Counts(counts, displs);
    }
}
