package org.example.lab5;

public final class SmurfRunner {
    private SmurfRunner() {
    }

    public static void run(LabOptions options) {
        try (NativeSockets sockets = NativeSockets.openSmurf()) {
            new SmurfWorker(
                    sockets,
                    options.smurfVictim,
                    options.smurfBroadcast,
                    options.count,
                    options.intervalMs
            ).run();
        }
    }
}
