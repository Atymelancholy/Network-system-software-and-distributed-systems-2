package org.example.lab7;

final class RowRange {
    final int start;
    final int count;

    RowRange(int start, int count) {
        this.start = start;
        this.count = count;
    }

    int doubleOffset(int matrixWidth) {
        return start * matrixWidth;
    }

    int doubleCount(int matrixWidth) {
        return count * matrixWidth;
    }

    int localOffset(RowRange owner, int matrixWidth) {
        return (start - owner.start) * matrixWidth;
    }

    boolean isEmpty() {
        return count <= 0;
    }
}
