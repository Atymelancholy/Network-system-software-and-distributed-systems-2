package org.example.lab7;

public final class LabSettings {
    public final boolean blocking;
    public final int n;
    public final int strips;

    public LabSettings(boolean blocking, int n, int strips) {
        this.blocking = blocking;
        this.n = n;
        this.strips = strips;
    }

    static LabSettings parse(String[] args) {
        boolean blocking = args.length == 0 || isBlocking(args[0]);
        int n = args.length > 1 ? Integer.parseInt(args[1]) : Lab7App.DEFAULT_N;
        int strips = args.length > 2 ? Integer.parseInt(args[2]) : Lab7App.DEFAULT_STRIPS;
        if (n <= 0 || strips <= 0) {
            throw new IllegalArgumentException("Размер матрицы и число полос должны быть > 0");
        }
        return new LabSettings(blocking, n, strips);
    }

    static boolean isBlocking(String mode) {
        String value = mode.toLowerCase();
        return value.equals("blocking") || value.equals("block") || value.equals("sync");
    }

    String modeName() {
        return blocking ? "блокирующий (Send/Recv)" : "неблокирующий (Isend/Irecv, CUDA Streams)";
    }
}
