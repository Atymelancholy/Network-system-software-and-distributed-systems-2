package org.example.lab7;

public final class RowRange {
    public final int start;
    public final int count;

    public RowRange(int start, int count) {
        this.start = start;
        this.count = count;
    }

    public int doubleOffset(int matrixWidth) {
        return start * matrixWidth;
    }

    public int doubleCount(int matrixWidth) {
        return count * matrixWidth;
    }

    public int localOffset(RowRange owner, int matrixWidth) {
        return (start - owner.start) * matrixWidth;
    }

    public boolean isEmpty() {
        return count <= 0;
    }
}
