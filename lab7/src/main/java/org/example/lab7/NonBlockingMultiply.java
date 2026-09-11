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
        int rank = MPI.COMM_WORLD.Rank();
        if (rank == 0) {
            runRoot(a, b, c, n, stripCount);
            return;
        }
        runWorker(n, stripCount);
    }

    static void runRoot(double[] a, double[] b, double[] c, int n, int stripCount) throws MPIException {
        int size = MPI.COMM_WORLD.Size();
        List<Request> requests = new ArrayList<>();
        postAllTransfers(a, b, c, n, stripCount, size, requests);
        BlockingMultiply.computeOwnedRows(a, b, c, n, stripCount, 0, size);
        PointToPoint.waitAll(requests);
    }

    static void postAllTransfers(
            double[] a,
            double[] b,
            double[] c,
            int n,
            int stripCount,
            int size,
            List<Request> requests
    ) throws MPIException {
        for (int dest = 1; dest < size; dest++) {
            postWorkerTransfers(a, b, c, n, stripCount, dest, size, requests);
        }
    }

    static void postWorkerTransfers(
            double[] a,
            double[] b,
            double[] c,
            int n,
            int stripCount,
            int dest,
            int size,
            List<Request> requests
    ) throws MPIException {
        RowRange range = WorkSplitter.rangeFor(dest, size, n);
        if (range.isEmpty()) {
            return;
        }
        requests.add(PointToPoint.isendDoubles(b, 0, n * n, dest, Tags.B));
        RowRange[] strips = WorkSplitter.strips(range, stripCount);
        for (int s = 0; s < strips.length; s++) {
            RowRange strip = strips[s];
            requests.add(PointToPoint.isendDoubles(
                    a, strip.doubleOffset(n), strip.doubleCount(n), dest, Tags.aStrip(s)));
            requests.add(PointToPoint.irecvDoubles(
                    c, strip.doubleOffset(n), strip.doubleCount(n), dest, Tags.cStrip(s)));
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
        pipelineStrips(aRows, b, cRows, range, n, stripCount);
    }

    static void pipelineStrips(
            double[] aRows,
            double[] b,
            double[] cRows,
            RowRange range,
            int n,
            int stripCount
    ) throws MPIException {
        RowRange[] strips = WorkSplitter.strips(range, stripCount);
        Request bRequest = PointToPoint.irecvDoubles(b, 0, n * n, 0, Tags.B);
        Request nextA = postStripRecv(aRows, range, strips, 0, n);
        PointToPoint.waitOne(bRequest);
        List<Request> resultSends = new ArrayList<>();
        for (int s = 0; s < strips.length; s++) {
            PointToPoint.waitOne(nextA);
            nextA = postStripRecv(aRows, range, strips, s + 1, n);
            RowRange strip = strips[s];
            int localOffset = strip.localOffset(range, n);
            MatrixUtils.multiplyRows(aRows, localOffset, b, cRows, localOffset, strip.count, n);
            resultSends.add(PointToPoint.isendDoubles(
                    cRows, localOffset, strip.doubleCount(n), 0, Tags.cStrip(s)));
        }
        PointToPoint.waitAll(resultSends);
    }

    static Request postStripRecv(double[] aRows, RowRange range, RowRange[] strips, int index, int n)
            throws MPIException {
        if (index >= strips.length) {
            return null;
        }
        RowRange strip = strips[index];
        return PointToPoint.irecvDoubles(
                aRows, strip.localOffset(range, n), strip.doubleCount(n), 0, Tags.aStrip(index));
    }
}
