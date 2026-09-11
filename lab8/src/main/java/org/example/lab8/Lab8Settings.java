package org.example.lab8;

import org.example.lab7.Lab7App;

import java.nio.file.Path;

final class Lab8Settings {
    static final int DEFAULT_GROUPS = 2;
    static final int DEFAULT_N = 1024;
    static final String DEFAULT_DATA_DIR = "data";
    static final long DEFAULT_SEED = 42L;

    final int groups;
    final int n;
    final boolean blocking;
    final int strips;
    final Path dataDir;
    final long seed;

    Lab8Settings(int groups, int n, boolean blocking, int strips, Path dataDir, long seed) {
        this.groups = groups;
        this.n = n;
        this.blocking = blocking;
        this.strips = strips;
        this.dataDir = dataDir;
        this.seed = seed;
    }

    static Lab8Settings parse(String[] args) {
        int groups = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_GROUPS;
        int n = args.length > 1 ? Integer.parseInt(args[1]) : DEFAULT_N;
        boolean blocking = args.length > 2 && isBlocking(args[2]);
        int strips = args.length > 3 ? Integer.parseInt(args[3]) : Lab7App.DEFAULT_STRIPS;
        Path dataDir = Path.of(args.length > 4 ? args[4] : DEFAULT_DATA_DIR);
        long seed = args.length > 5 ? Long.parseLong(args[5]) : DEFAULT_SEED;
        if (groups <= 0 || n <= 0 || strips <= 0) {
            throw new IllegalArgumentException("groups, n и strips должны быть > 0");
        }
        return new Lab8Settings(groups, n, blocking, strips, dataDir, seed);
    }

    static boolean isBlocking(String mode) {
        String value = mode.toLowerCase();
        return value.equals("blocking") || value.equals("block") || value.equals("sync");
    }

    String pairwiseModeName() {
        return blocking ? "блокирующий (Send/Recv)" : "неблокирующий (Isend/Irecv)";
    }

    Path fileA() {
        return dataDir.resolve("A.bin");
    }

    Path fileB() {
        return dataDir.resolve("B.bin");
    }

    Path groupResult(int groupId) {
        return dataDir.resolve("group-" + groupId + "-C.bin");
    }
}
