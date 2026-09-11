package org.example.lab5;

import java.util.ArrayList;
import java.util.List;

public final class LabOptions {
    public final String mode;
    public final int count;
    public final int timeoutMs;
    public final int maxHops;
    public final List<String> hosts;

    private LabOptions(String mode, int count, int timeoutMs, int maxHops, List<String> hosts) {
        this.mode = mode;
        this.count = count;
        this.timeoutMs = timeoutMs;
        this.maxHops = maxHops;
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
        if (!mode.equals("ping") && !mode.equals("traceroute")) {
            throw new IcmpException(usage());
        }
        int count = 4;
        int timeoutMs = 1000;
        int maxHops = 30;
        List<String> hosts = new ArrayList<>();
        for (int i = 1; i < args.length; i++) {
            String arg = args[i];
            if (arg.equals("-c") && i + 1 < args.length) {
                count = Integer.parseInt(args[++i]);
            } else if (arg.equals("-W") && i + 1 < args.length) {
                timeoutMs = Integer.parseInt(args[++i]);
            } else if (arg.equals("-m") && i + 1 < args.length) {
                maxHops = Integer.parseInt(args[++i]);
            } else if (arg.startsWith("-")) {
                throw new IcmpException("Неизвестный ключ: " + arg + "\n" + usage());
            } else {
                hosts.add(arg);
            }
        }
        if (hosts.isEmpty()) {
            throw new IcmpException(usage());
        }
        return new LabOptions(mode, count, timeoutMs, maxHops, hosts);
    }

    static String usage() {
        return """
                Лабораторная №5: параллельный ICMP ping и traceroute
                Использование:
                  ping [-c count] [-W timeout_ms] host [host...]
                  traceroute [-m max_hops] [-W timeout_ms] host [host...]
                Примеры:
                  ping 8.8.8.8 1.1.1.1 google.com
                  traceroute -m 20 8.8.8.8
                Нужны права администратора (Windows) или root (Linux).
                """;
    }
}
