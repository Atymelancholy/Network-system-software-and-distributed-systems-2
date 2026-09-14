package org.example.lab5;

import java.util.ArrayList;
import java.util.List;

public final class LabOptions {
    public final String mode;
    public final int count;
    public final int timeoutMs;
    public final int maxHops;
    public final int intervalMs;
    public final String smurfVictim;
    public final String smurfBroadcast;
    public final List<String> hosts;

    private LabOptions(
            String mode,
            int count,
            int timeoutMs,
            int maxHops,
            int intervalMs,
            String smurfVictim,
            String smurfBroadcast,
            List<String> hosts
    ) {
        this.mode = mode;
        this.count = count;
        this.timeoutMs = timeoutMs;
        this.maxHops = maxHops;
        this.intervalMs = intervalMs;
        this.smurfVictim = smurfVictim;
        this.smurfBroadcast = smurfBroadcast;
        this.hosts = hosts;
    }

    public static LabOptions parse(String[] args) {
        if (args.length == 0) {
            System.out.println("Аргументы не заданы. Запускаю демо: ping 8.8.8.8 1.1.1.1");
            System.out.println("Чтобы задать свои: Run → Edit Configurations → Program arguments");
            args = new String[]{"ping", "8.8.8.8", "1.1.1.1"};
        }
        if (args.length < 2) {
            throw new IcmpException(usage());
        }
        String mode = args[0];
        if (!mode.equals("ping") && !mode.equals("traceroute") && !mode.equals("smurf")) {
            throw new IcmpException(usage());
        }
        int count = mode.equals("smurf") ? 5 : 4;
        int timeoutMs = 1000;
        int maxHops = 30;
        int intervalMs = 200;
        String smurfVictim = null;
        String smurfBroadcast = null;
        List<String> hosts = new ArrayList<>();
        for (int i = 1; i < args.length; i++) {
            String arg = args[i];
            if (arg.equals("-c") && i + 1 < args.length) {
                count = Integer.parseInt(args[++i]);
            } else if (arg.equals("-W") && i + 1 < args.length) {
                timeoutMs = Integer.parseInt(args[++i]);
            } else if (arg.equals("-m") && i + 1 < args.length) {
                maxHops = Integer.parseInt(args[++i]);
            } else if (arg.equals("-i") && i + 1 < args.length) {
                intervalMs = Integer.parseInt(args[++i]);
            } else if (arg.equals("-victim") && i + 1 < args.length) {
                smurfVictim = args[++i];
            } else if (arg.equals("-bcast") && i + 1 < args.length) {
                smurfBroadcast = args[++i];
            } else if (arg.startsWith("-")) {
                throw new IcmpException("Неизвестный ключ: " + arg + "\n" + usage());
            } else {
                hosts.add(arg);
            }
        }
        if (mode.equals("smurf")) {
            if (smurfVictim == null && !hosts.isEmpty()) {
                smurfVictim = hosts.get(0);
            }
            if (smurfBroadcast == null && hosts.size() >= 2) {
                smurfBroadcast = hosts.get(1);
            }
            if (smurfVictim == null || smurfBroadcast == null) {
                throw new IcmpException("Для smurf укажите -victim и -bcast (или два адреса позиционно).\n" + usage());
            }
            return new LabOptions(mode, count, timeoutMs, maxHops, intervalMs, smurfVictim, smurfBroadcast, List.of());
        }
        if (hosts.isEmpty()) {
            throw new IcmpException(usage());
        }
        return new LabOptions(mode, count, timeoutMs, maxHops, intervalMs, null, null, hosts);
    }

    static String usage() {
        return """
                Лабораторная №5: ICMP ping, traceroute, Smurf (учебный стенд)
                Использование:
                  ping [-c count] [-W timeout_ms] host [host...]
                  traceroute [-m max_hops] [-W timeout_ms] host [host...]
                  smurf -victim IP -bcast IP [-c count] [-i interval_ms]
                  smurf VICTIM_IP BCAST_IP [-c count] [-i interval_ms]
                Примеры:
                  ping 8.8.8.8 1.1.1.1
                  traceroute -m 20 8.8.8.8
                  smurf -victim 10.149.49.50 -bcast 10.149.49.255 -c 5
                Smurf — только на изолированной лабораторной сети с разрешения преподавателя.
                Нужны права администратора (Windows) или root (Linux).
                """;
    }
}
