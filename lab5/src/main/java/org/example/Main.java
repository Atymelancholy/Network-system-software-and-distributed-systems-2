package org.example;

import org.example.lab5.IcmpChannel;
import org.example.lab5.IcmpException;
import org.example.lab5.LabOptions;
import org.example.lab5.PingWorker;
import org.example.lab5.TracerouteWorker;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        useUtf8Console();
        LabOptions options;
        try {
            options = LabOptions.parse(args);
        } catch (IcmpException e) {
            System.err.println(e.getMessage());
            return;
        }
        try (IcmpChannel channel = IcmpChannel.open()) {
            List<Thread> workers = startWorkers(channel, options);
            joinAll(workers);
        } catch (IcmpException e) {
            System.err.println(e.getMessage());
        }
    }

    private static void useUtf8Console() {
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(System.err, true, StandardCharsets.UTF_8));
    }

    private static List<Thread> startWorkers(IcmpChannel channel, LabOptions options) {
        List<Thread> threads = new ArrayList<>();
        for (String host : options.hosts) {
            Thread thread = new Thread(workerFor(channel, options, host), "icmp-" + host);
            threads.add(thread);
            thread.start();
        }
        return threads;
    }

    private static Runnable workerFor(IcmpChannel channel, LabOptions options, String host) {
        if (options.mode.equals("traceroute")) {
            return new TracerouteWorker(channel, host, options.maxHops, options.timeoutMs);
        }
        return new PingWorker(channel, host, options.count, options.timeoutMs);
    }

    private static void joinAll(List<Thread> threads) {
        for (Thread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}
