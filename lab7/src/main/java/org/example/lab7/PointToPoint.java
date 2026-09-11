package org.example.lab7;

import mpi.MPI;
import mpi.MPIException;
import mpi.Request;

import java.util.List;

final class PointToPoint {
    private PointToPoint() {
    }

    static void sendDoubles(double[] buffer, int offset, int count, int dest, int tag)
            throws MPIException {
        MPI.COMM_WORLD.Send(buffer, offset, count, MPI.DOUBLE, dest, tag);
    }

    static void recvDoubles(double[] buffer, int offset, int count, int source, int tag)
            throws MPIException {
        MPI.COMM_WORLD.Recv(buffer, offset, count, MPI.DOUBLE, source, tag);
    }

    static Request isendDoubles(double[] buffer, int offset, int count, int dest, int tag)
            throws MPIException {
        return MPI.COMM_WORLD.Isend(buffer, offset, count, MPI.DOUBLE, dest, tag);
    }

    static Request irecvDoubles(double[] buffer, int offset, int count, int source, int tag)
            throws MPIException {
        return MPI.COMM_WORLD.Irecv(buffer, offset, count, MPI.DOUBLE, source, tag);
    }

    static void waitAll(List<Request> requests) throws MPIException {
        if (requests.isEmpty()) {
            return;
        }
        Request.Waitall(requests.toArray(new Request[0]));
    }

    static void waitOne(Request request) throws MPIException {
        if (request != null) {
            request.Wait();
        }
    }
}
