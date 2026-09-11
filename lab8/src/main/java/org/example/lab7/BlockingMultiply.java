package org.example.lab7;

import mpi.MPI;
import mpi.MPIException;

final class BlockingMultiply {
    private BlockingMultiply() {
    }

    static void run(double[] a, double[] b, double[] c, int n, int stripCount) throws MPIException {
        if (MPI.COMM_WORLD.Rank() == 0) {
            runRoot(a, b, c, n, stripCount);
            return;
        }
        runWorker(n, stripCount);
    }

    static void runRoot(double[] a, double[] b, double[] c, int n, int stripCount) throws MPIException {
        int size = MPI.COMM_WORLD.Size();
        for (int dest = 1; dest < size; dest++) {
            sendWorkerInputs(a, b, n, stripCount, dest, size);
        }
        computeOwnedRows(a, b, c, n, stripCount, 0, size);
        for (int source = 1; source < size; source++) {
            receiveWorkerResult(c, n, stripCount, source, size);
        }
    }

    static void sendWorkerInputs(double[] a, double[] b, int n, int stripCount, int dest, int size)
            throws MPIException {
        RowRange range = WorkSplitter.rangeFor(dest, size, n);
        if (range.isEmpty()) {
            return;
        }
        PointToPoint.sendDoubles(b, 0, n * n, dest, Tags.B);
        RowRange[] strips = WorkSplitter.strips(range, stripCount);
        for (int s = 0; s < strips.length; s++) {
            RowRange strip = strips[s];
            PointToPoint.sendDoubles(a, strip.doubleOffset(n), strip.doubleCount(n), dest, Tags.aStrip(s));
        }
    }

    static void receiveWorkerResult(double[] c, int n, int stripCount, int source, int size)
            throws MPIException {
        RowRange range = WorkSplitter.rangeFor(source, size, n);
        RowRange[] strips = WorkSplitter.strips(range, stripCount);
        for (int s = 0; s < strips.length; s++) {
            RowRange strip = strips[s];
            PointToPoint.recvDoubles(c, strip.doubleOffset(n), strip.doubleCount(n), source, Tags.cStrip(s));
        }
    }

    static void runWorker(int n, int stripCount) throws MPIException {
        int rank = MPI.COMM_WORLD.Rank();
        int size = MPI.COMM_WORLD.Size();
        RowRange range = WorkSplitter.rangeFor(rank, size, n);
        if (range.isEmpty()) {
            return;
        }
        double[] b = MatrixUtils.allocate(n);
        double[] aRows = MatrixUtils.allocateRows(range.count, n);
        double[] cRows = MatrixUtils.allocateRows(range.count, n);
        PointToPoint.recvDoubles(b, 0, n * n, 0, Tags.B);
        receiveAllStrips(aRows, range, n, stripCount);
        computePackedRows(aRows, b, cRows, range, n, stripCount);
        sendAllStrips(cRows, range, n, stripCount);
    }

    static void receiveAllStrips(double[] aRows, RowRange range, int n, int stripCount)
            throws MPIException {
        RowRange[] strips = WorkSplitter.strips(range, stripCount);
        for (int s = 0; s < strips.length; s++) {
            RowRange strip = strips[s];
            PointToPoint.recvDoubles(aRows, strip.localOffset(range, n), strip.doubleCount(n), 0, Tags.aStrip(s));
        }
    }

    static void sendAllStrips(double[] cRows, RowRange range, int n, int stripCount) throws MPIException {
        RowRange[] strips = WorkSplitter.strips(range, stripCount);
        for (int s = 0; s < strips.length; s++) {
            RowRange strip = strips[s];
            PointToPoint.sendDoubles(cRows, strip.localOffset(range, n), strip.doubleCount(n), 0, Tags.cStrip(s));
        }
    }

    static void computeOwnedRows(double[] a, double[] b, double[] c, int n, int stripCount, int rank, int size) {
        RowRange range = WorkSplitter.rangeFor(rank, size, n);
        if (range.isEmpty()) {
            return;
        }
        for (RowRange strip : WorkSplitter.strips(range, stripCount)) {
            MatrixUtils.multiplyRows(
                    a,
                    strip.doubleOffset(n),
                    b,
                    c,
                    strip.doubleOffset(n),
                    strip.count,
                    n
            );
        }
    }

    static void computePackedRows(double[] aRows, double[] b, double[] cRows, RowRange owner, int n, int stripCount) {
        for (RowRange strip : WorkSplitter.strips(owner, stripCount)) {
            int localOffset = strip.localOffset(owner, n);
            MatrixUtils.multiplyRows(aRows, localOffset, b, cRows, localOffset, strip.count, n);
        }
    }
}
