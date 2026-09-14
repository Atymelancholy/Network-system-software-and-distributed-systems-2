package org.example.lab8;

import mpi.Intracomm;
import mpi.MPI;
import mpi.MPIException;
import org.example.lab7.MatrixUtils;
import org.example.lab7.RowRange;
import org.example.lab7.WorkSplitter;

import java.io.IOException;
import java.nio.file.Path;

final class GroupJob {
    final double computeSeconds;
    final boolean ok;

    GroupJob(double computeSeconds, boolean ok) {
        this.computeSeconds = computeSeconds;
        this.ok = ok;
    }

    static GroupJob run(Intracomm comm, Lab8Settings settings, int groupId) throws MPIException, IOException {
        int rank = comm.Rank();
        int size = comm.Size();
        int n = settings.n;
        RowRange range = WorkSplitter.rangeFor(rank, size, n);
        prepareResultFile(comm, settings.groupResult(groupId), n, rank);
        double[] localA = DoublesFile.readRows(settings.fileA(), n, range);
        double[] localB = DoublesFile.readRows(settings.fileB(), n, range);
        comm.Barrier();
        long started = System.nanoTime();
        double[] fullB = CollectiveMultiply.assembleMatrix(comm, localB, n);
        double[] localC = CollectiveMultiply.multiplyLocal(localA, fullB, range, n);
        comm.Barrier();
        double elapsed = (System.nanoTime() - started) / 1e9;
        DoublesFile.writeRows(settings.groupResult(groupId), n, range, localC);
        boolean ok = MatrixUtils.packedSampleMatches(localA, fullB, localC, range.count, n);
        ok = reduceOk(comm, ok);
        return new GroupJob(elapsed, ok);
    }

    static void prepareResultFile(Intracomm comm, Path path, int n, int rank) throws MPIException, IOException {
        if (rank == 0) {
            DoublesFile.createEmpty(path, n);
        }
        comm.Barrier();
    }

    static boolean reduceOk(Intracomm comm, boolean ok) throws MPIException {
        int[] send = new int[]{ok ? 1 : 0};
        int[] recv = new int[1];
        comm.Allreduce(send, 0, recv, 0, 1, MPI.INT, MPI.MIN);
        return recv[0] == 1;
    }
}
