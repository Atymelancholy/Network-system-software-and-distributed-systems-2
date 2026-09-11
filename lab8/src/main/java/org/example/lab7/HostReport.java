package org.example.lab7;

import mpi.MPI;
import mpi.MPIException;

public final class HostReport {
    private static final int TAG = 7;
    private static final int MAX_NAME = 256;

    private HostReport() {
    }

    public static void printAll() throws MPIException {
        int rank = MPI.COMM_WORLD.Rank();
        int size = MPI.COMM_WORLD.Size();
        char[] name = paddedName();
        if (rank != 0) {
            MPI.COMM_WORLD.Send(name, 0, MAX_NAME, MPI.CHAR, 0, TAG);
            return;
        }
        System.out.println("Узлы:");
        System.out.println("  rank 0: " + trimName(name));
        char[] incoming = new char[MAX_NAME];
        for (int source = 1; source < size; source++) {
            MPI.COMM_WORLD.Recv(incoming, 0, MAX_NAME, MPI.CHAR, source, TAG);
            System.out.println("  rank " + source + ": " + trimName(incoming));
        }
    }

    static char[] paddedName() throws MPIException {
        char[] buffer = new char[MAX_NAME];
        String host = MPI.Get_processor_name();
        int length = Math.min(host.length(), MAX_NAME);
        host.getChars(0, length, buffer, 0);
        return buffer;
    }

    static String trimName(char[] buffer) {
        int length = 0;
        while (length < buffer.length && buffer[length] != 0) {
            length++;
        }
        return new String(buffer, 0, length);
    }
}
