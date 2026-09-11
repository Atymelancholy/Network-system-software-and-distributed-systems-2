package org.example.lab7;

import java.util.Random;

final class MatrixUtils {
    private MatrixUtils() {
    }

    static double[] allocate(int n) {
        return new double[n * n];
    }

    static double[] allocateRows(int rowCount, int n) {
        return new double[rowCount * n];
    }

    static void fillRandom(double[] matrix, long seed) {
        Random random = new Random(seed);
        for (int i = 0; i < matrix.length; i++) {
            matrix[i] = random.nextDouble();
        }
    }

    static void zero(double[] matrix) {
        java.util.Arrays.fill(matrix, 0.0);
    }

    static void multiplyRows(
            double[] a,
            int aOffset,
            double[] b,
            double[] c,
            int cOffset,
            int rowCount,
            int n
    ) {
        for (int i = 0; i < rowCount; i++) {
            int aRow = aOffset + i * n;
            int cRow = cOffset + i * n;
            for (int k = 0; k < n; k++) {
                double aik = a[aRow + k];
                int bRow = k * n;
                for (int j = 0; j < n; j++) {
                    c[cRow + j] += aik * b[bRow + j];
                }
            }
        }
    }

    static boolean sampleMatches(double[] a, double[] b, double[] c, int n, int sampleRows) {
        int step = Math.max(n / sampleRows, 1);
        for (int row = 0; row < n; row += step) {
            if (!rowMatches(a, b, c, n, row)) {
                return false;
            }
        }
        return true;
    }

    static boolean rowMatches(double[] a, double[] b, double[] c, int n, int row) {
        for (int j = 0; j < n; j++) {
            double expected = 0.0;
            for (int k = 0; k < n; k++) {
                expected += a[row * n + k] * b[k * n + j];
            }
            if (Math.abs(expected - c[row * n + j]) > 1e-6 * Math.max(1.0, Math.abs(expected))) {
                return false;
            }
        }
        return true;
    }
}
