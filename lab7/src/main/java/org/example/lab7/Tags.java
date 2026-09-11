package org.example.lab7;

final class Tags {
    static final int B = 20;
    static final int A = 30;
    static final int C = 40;

    private Tags() {
    }

    static int aStrip(int stripIndex) {
        return A + stripIndex;
    }

    static int cStrip(int stripIndex) {
        return C + stripIndex;
    }
}
