package org.example.lab8;

import mpi.Intracomm;
import mpi.MPI;
import mpi.MPIException;
import org.example.lab7.MatrixUtils;
import org.example.lab7.RowRange;
import org.example.lab7.WorkSplitter;

final class CollectiveMultiply {
    private CollectiveMultiply() {
    }

    static void fromRoot(Intracomm comm, double[] a, double[] b, double[] c, int n) throws MPIException {
        int rank = comm.Rank();
        int size = comm.Size();
        RowRange range = WorkSplitter.rangeFor(rank, size, n);
        Counts layout = Counts.of(size, n);
        double[] localA = scatterRows(comm, a, n, rank, range, layout);
        double[] fullB = broadcastMatrix(comm, b, n, rank);
        double[] localC = multiplyLocal(localA, fullB, range, n);
        gatherRows(comm, localC, c, n, rank, range, layout);
    }

    static double[] assembleMatrix(Intracomm comm, double[] localRows, int n) throws MPIException {
        int size = comm.Size();
        Counts layout = Counts.of(size, n);
        double[] full = MatrixUtils.allocate(n);
        comm.Allgatherv(
                localRows, 0, localRows.length, MPI.DOUBLE,
                full, 0, layout.counts, layout.displs, MPI.DOUBLE
        );
        return full;
    }

    static double[] scatterRows(
            Intracomm comm,
            double[] rootMatrix,
            int n,
            int rank,
            RowRange range,
            Counts layout
    ) throws MPIException {
        double[] local = MatrixUtils.allocateRows(range.count, n);
        double[] send = rank == 0 ? rootMatrix : new double[1];
        comm.Scatterv(
                send, 0, layout.counts, layout.displs, MPI.DOUBLE,
                local, 0, range.doubleCount(n), MPI.DOUBLE, 0
        );
        return local;
    }

    static double[] broadcastMatrix(Intracomm comm, double[] rootMatrix, int n, int rank)
            throws MPIException {
        double[] full = rank == 0 ? rootMatrix : MatrixUtils.allocate(n);
        comm.Bcast(full, 0, n * n, MPI.DOUBLE, 0);
        return full;
    }

    static double[] multiplyLocal(double[] localA, double[] fullB, RowRange range, int n) {
        double[] localC = MatrixUtils.allocateRows(range.count, n);
        if (!range.isEmpty()) {
            MatrixUtils.multiplyRows(localA, 0, fullB, localC, 0, range.count, n);
        }
        return localC;
    }

    static void gatherRows(
            Intracomm comm,
            double[] localC,
            double[] rootC,
            int n,
            int rank,
            RowRange range,
            Counts layout
    ) throws MPIException {
        double[] recv = rank == 0 ? rootC : new double[1];
        comm.Gatherv(
                localC, 0, range.doubleCount(n), MPI.DOUBLE,
                recv, 0, layout.counts, layout.displs, MPI.DOUBLE, 0
        );
    }
}
