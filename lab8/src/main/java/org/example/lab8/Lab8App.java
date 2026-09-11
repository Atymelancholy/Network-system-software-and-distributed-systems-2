package org.example.lab8;

import mpi.MPI;
import mpi.MPIException;
import org.example.lab7.HostReport;
import org.example.lab7.Lab7App;
import org.example.lab7.LabSettings;
import org.example.lab7.MatrixUtils;

import java.io.IOException;
import java.nio.file.Files;

public final class Lab8App {
    public static void main(String[] args) throws Exception {
        args = MPI.Init(args);
        try {
            run(args);
        } finally {
            MPI.Finalize();
        }
    }

    static void run(String[] args) throws MPIException, IOException {
        Lab8Settings settings = Lab8Settings.parse(args);
        int rank = MPI.COMM_WORLD.Rank();
        int size = MPI.COMM_WORLD.Size();
        printHeader(settings, rank, size);
        HostReport.printAll();
        SharedInputs.prepare(settings, rank);
        double pairwise = runPairwise(settings, rank);
        double collective = runCollectiveWorld(settings, rank);
        GroupPlan plan = makePlan(settings, rank, size);
        GroupFactory.printGroups(plan);
        GroupJob job = GroupJob.run(GroupFactory.communicator(plan), settings, plan.colorOf(rank));
        printFooter(settings, plan, pairwise, collective, job, rank);
    }

    static GroupPlan makePlan(Lab8Settings settings, int rank, int size) throws MPIException {
        GroupPlan local = rank == 0 ? GroupPlan.create(settings.groups, size, settings.seed) : null;
        return GroupPlan.broadcast(local);
    }

    static double runPairwise(Lab8Settings settings, int rank) throws MPIException {
        WorldRun run = WorldRun.start(rank, settings.n);
        Lab7App.multiply(new LabSettings(settings.blocking, settings.n, settings.strips), run.a, run.b, run.c);
        return run.finish(rank, "Парные операции (" + settings.pairwiseModeName() + ")");
    }

    static double runCollectiveWorld(Lab8Settings settings, int rank) throws MPIException {
        WorldRun run = WorldRun.start(rank, settings.n);
        CollectiveMultiply.fromRoot(MPI.COMM_WORLD, run.a, run.b, run.c, settings.n);
        return run.finish(rank, "Коллективные на COMM_WORLD (Bcast/Scatterv/Gatherv)");
    }

    static void printHeader(Lab8Settings settings, int rank, int size) throws MPIException {
        if (rank != 0) {
            return;
        }
        System.out.println("ЛР №8 MPI: коллективные операции, группы, файлы");
        System.out.println("MPI COMM_WORLD size = " + size + ", host = " + MPI.Get_processor_name());
        System.out.println("Групп (запрошено): " + settings.groups);
        System.out.println("Матрица: " + settings.n + " x " + settings.n);
        System.out.println("Парный режим: " + settings.pairwiseModeName());
        System.out.println("Каталог файлов: " + settings.dataDir.toAbsolutePath());
        if (size < 3) {
            System.out.println("Для сдачи запускайте минимум на 3 процессах и 3 компьютерах.");
        }
    }

    static void printFooter(
            Lab8Settings settings,
            GroupPlan plan,
            double pairwise,
            double collective,
            GroupJob job,
            int rank
    ) throws MPIException {
        double[] times = GroupTimes.collect(plan, job.computeSeconds);
        int[] checks = GroupTimes.collectFlags(plan, job.ok);
        if (rank != 0) {
            return;
        }
        System.out.println("Коллективные операции в группах (файлы + Allgatherv):");
        for (int groupId = 0; groupId < plan.groupCount; groupId++) {
            System.out.printf("  %s: %.3f с, проверка %s%n",
                    plan.describe(groupId),
                    times[groupId],
                    checks[groupId] == 1 ? "OK" : "ОШИБКА");
            System.out.println("  файл результата: " + settings.groupResult(groupId).toAbsolutePath());
        }
        System.out.printf("COMM_WORLD коллективные / парные = %.2f%n", collective / pairwise);
        System.out.println("Группы / парные:");
        for (int i = 0; i < times.length; i++) {
            System.out.printf("  группа %d / парные = %.2f%n", i, times[i] / pairwise);
        }
    }
}

final class WorldRun {
    final double[] a;
    final double[] b;
    final double[] c;
    final long started;

    WorldRun(double[] a, double[] b, double[] c, long started) {
        this.a = a;
        this.b = b;
        this.c = c;
        this.started = started;
    }

    static WorldRun start(int rank, int n) throws MPIException {
        double[] a = rank == 0 ? MatrixUtils.allocate(n) : null;
        double[] b = rank == 0 ? MatrixUtils.allocate(n) : null;
        double[] c = rank == 0 ? MatrixUtils.allocate(n) : null;
        Lab7App.prepareRootMatrices(a, b, c, rank);
        MPI.COMM_WORLD.Barrier();
        return new WorldRun(a, b, c, System.nanoTime());
    }

    double finish(int rank, String title) throws MPIException {
        MPI.COMM_WORLD.Barrier();
        double elapsed = (System.nanoTime() - started) / 1e9;
        if (rank == 0) {
            int n = (int) Math.round(Math.sqrt(a.length));
            boolean ok = MatrixUtils.sampleMatches(a, b, c, n, 4);
            System.out.printf("%s: %.3f с, проверка %s%n", title, elapsed, ok ? "OK" : "ОШИБКА");
        }
        return elapsed;
    }
}

final class SharedInputs {
    private SharedInputs() {
    }

    static void prepare(Lab8Settings settings, int rank) throws MPIException, IOException {
        if (rank == 0) {
            Files.createDirectories(settings.dataDir);
            double[] a = MatrixUtils.allocate(settings.n);
            double[] b = MatrixUtils.allocate(settings.n);
            MatrixUtils.fillRandom(a, 1L);
            MatrixUtils.fillRandom(b, 2L);
            DoublesFile.writeAll(settings.fileA(), a);
            DoublesFile.writeAll(settings.fileB(), b);
            System.out.println("Записаны общие файлы: " + settings.fileA() + ", " + settings.fileB());
        }
        MPI.COMM_WORLD.Barrier();
    }
}

final class GroupTimes {
    private GroupTimes() {
    }

    static double[] collect(GroupPlan plan, double seconds) throws MPIException {
        double[] mine = new double[]{leaderValue(plan, seconds)};
        double[] all = new double[MPI.COMM_WORLD.Size()];
        MPI.COMM_WORLD.Gather(mine, 0, 1, MPI.DOUBLE, all, 0, 1, MPI.DOUBLE, 0);
        return firstPerGroup(plan, all);
    }

    static int[] collectFlags(GroupPlan plan, boolean ok) throws MPIException {
        double[] values = collect(plan, ok ? 1.0 : 0.0);
        int[] flags = new int[values.length];
        for (int i = 0; i < values.length; i++) {
            flags[i] = (int) values[i];
        }
        return flags;
    }

    static double leaderValue(GroupPlan plan, double value) throws MPIException {
        int rank = MPI.COMM_WORLD.Rank();
        int groupId = plan.colorOf(rank);
        return rank == plan.membersOf(groupId)[0] ? value : -1.0;
    }

    static double[] firstPerGroup(GroupPlan plan, double[] all) {
        double[] result = new double[plan.groupCount];
        for (int groupId = 0; groupId < plan.groupCount; groupId++) {
            result[groupId] = all[plan.membersOf(groupId)[0]];
        }
        return result;
    }
}
