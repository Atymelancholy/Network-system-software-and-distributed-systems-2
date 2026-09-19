package org.example.lab7;

import mpi.MPI;
import mpi.MPIException;
import mpi.Request;

import java.util.ArrayList;
import java.util.List;

final class NonBlockingMultiply {
    private NonBlockingMultiply() {
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
        List<Request> requests = new ArrayList<>();
        for (int dest = 1; dest < size; dest++) {
            postWorkerTransfers(a, b, c, n, dest, size, requests);
        }
        BlockingMultiply.computeOwnedRows(a, b, c, n, stripCount, 0, size);
        PointToPoint.waitAll(requests);
    }

    static void postWorkerTransfers(
            double[] a,
            double[] b,
            double[] c,
            int n,
            int dest,
            int size,
            List<Request> requests
    ) throws MPIException {
        RowRange range = WorkSplitter.rangeFor(dest, size, n);
        if (range.isEmpty()) {
            return;
        }
        requests.add(PointToPoint.isendDoubles(b, 0, n * n, dest, Tags.B));
        requests.add(PointToPoint.isendDoubles(
                a, range.doubleOffset(n), range.doubleCount(n), dest, Tags.A));
        requests.add(PointToPoint.irecvDoubles(
                c, range.doubleOffset(n), range.doubleCount(n), dest, Tags.C));
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
        overlapRecvAndCompute(aRows, b, cRows, range, n, stripCount);
    }

    static void overlapRecvAndCompute(
            double[] aRows,
            double[] b,
            double[] cRows,
            RowRange range,
            int n,
            int stripCount
    ) throws MPIException {
        Request bRequest = PointToPoint.irecvDoubles(b, 0, n * n, 0, Tags.B);
        Request aRequest = PointToPoint.irecvDoubles(aRows, 0, range.doubleCount(n), 0, Tags.A);
        PointToPoint.waitOne(bRequest);
        PointToPoint.waitOne(aRequest);
        computePacked(aRows, b, cRows, range, n, stripCount);
        Request cRequest = PointToPoint.isendDoubles(cRows, 0, range.doubleCount(n), 0, Tags.C);
        PointToPoint.waitOne(cRequest);
    }

    static void computePacked(
            double[] aRows,
            double[] b,
            double[] cRows,
            RowRange range,
            int n,
            int stripCount
    ) {
        for (RowRange strip : WorkSplitter.strips(range, stripCount)) {
            int localOffset = strip.localOffset(range, n);
            MatrixUtils.multiplyRows(aRows, localOffset, b, cRows, localOffset, strip.count, n);
        }
    }
}
