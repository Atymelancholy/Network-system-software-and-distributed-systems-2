package org.example.lab7;

final class LabSettings {
    final boolean blocking;
    final int n;
    final int strips;

    LabSettings(boolean blocking, int n, int strips) {
        this.blocking = blocking;
        this.n = n;
        this.strips = strips;
    }

    static LabSettings parse(String[] args) {
        boolean blocking = args.length == 0 || isBlocking(args[0]);
        int n = args.length > 1 ? Integer.parseInt(args[1]) : Lab7App.DEFAULT_N;
        int strips = args.length > 2 ? Integer.parseInt(args[2]) : Lab7App.DEFAULT_STRIPS;
        if (n <= 0 || strips <= 0) {
            throw new IllegalArgumentException("Matrix size and strip count must be > 0");
        }
        return new LabSettings(blocking, n, strips);
    }

    static boolean isBlocking(String mode) {
        String value = mode.toLowerCase();
        return value.equals("blocking") || value.equals("block") || value.equals("sync");
    }

    String modeName() {
        return blocking ? "blocking (Send/Recv)" : "non-blocking (Isend/Irecv, CUDA Streams)";
    }
}
