package org.example.lab7;

import mpi.MPI;
import mpi.MPIException;

public final class Lab7App {
    public static final int DEFAULT_N = 3072;
    public static final int DEFAULT_STRIPS = 8;

    public static void main(String[] args) throws MPIException {
        args = MPI.Init(args);
        try {
            run(args);
        } finally {
            MPI.Finalize();
        }
    }

    static void run(String[] args) throws MPIException {
        LabSettings settings = LabSettings.parse(args);
        int rank = MPI.COMM_WORLD.Rank();
        int size = MPI.COMM_WORLD.Size();
        printHeader(settings, rank, size);
        HostReport.printAll();
        double[] a = rank == 0 ? MatrixUtils.allocate(settings.n) : null;
        double[] b = rank == 0 ? MatrixUtils.allocate(settings.n) : null;
        double[] c = rank == 0 ? MatrixUtils.allocate(settings.n) : null;
        prepareRootMatrices(a, b, c, rank);
        long started = System.nanoTime();
        multiply(settings, a, b, c);
        double elapsed = (System.nanoTime() - started) / 1e9;
        printFooter(settings, a, b, c, rank, elapsed);
    }

    public static void prepareRootMatrices(double[] a, double[] b, double[] c, int rank) {
        if (rank != 0) {
            return;
        }
        MatrixUtils.fillRandom(a, 1L);
        MatrixUtils.fillRandom(b, 2L);
        MatrixUtils.zero(c);
    }

    public static void multiply(LabSettings settings, double[] a, double[] b, double[] c) throws MPIException {
        if (settings.blocking) {
            BlockingMultiply.run(a, b, c, settings.n, settings.strips);
            return;
        }
        NonBlockingMultiply.run(a, b, c, settings.n, settings.strips);
    }

    static void printHeader(LabSettings settings, int rank, int size) throws MPIException {
        if (rank != 0) {
            return;
        }
        System.out.println("MPI COMM_WORLD size = " + size
                + ", host = " + MPI.Get_processor_name());
        System.out.println("Режим: " + settings.modeName());
        System.out.println("Матрица: " + settings.n + " x " + settings.n);
        System.out.println("Полос на процесс: " + settings.strips);
        if (size < 3) {
            System.out.println("Для сдачи ЛР запускайте минимум на 3 процессах (-np 3) и 3 компьютерах.");
        }
    }

    static void printFooter(
            LabSettings settings,
            double[] a,
            double[] b,
            double[] c,
            int rank,
            double elapsed
    ) {
        if (rank != 0) {
            return;
        }
        double gflops = elapsed > 0 ? 2.0 * settings.n * settings.n * settings.n / elapsed / 1e9 : 0.0;
        boolean ok = MatrixUtils.sampleMatches(a, b, c, settings.n, 4);
        System.out.printf("Время: %.3f с%n", elapsed);
        System.out.printf("Производительность: %.3f GFLOPS%n", gflops);
        System.out.println("Проверка: " + (ok ? "OK" : "ОШИБКА"));
    }
}
