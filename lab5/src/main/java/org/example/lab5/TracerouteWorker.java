package org.example.lab5;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Optional;

public final class TracerouteWorker implements Runnable {
    private final IcmpChannel channel;
    private final String host;
    private final int maxHops;
    private final int timeoutMs;
    private final EchoReplyHandler echoReplyHandler = new EchoReplyHandler();
    private final TimeExceededHandler timeExceededHandler = new TimeExceededHandler();
    private final HostUnreachableHandler hostUnreachableHandler = new HostUnreachableHandler();

    public TracerouteWorker(IcmpChannel channel, String host, int maxHops, int timeoutMs) {
        this.channel = channel;
        this.host = host;
        this.maxHops = maxHops;
        this.timeoutMs = timeoutMs;
    }

    @Override
    public void run() {
        InetAddress destination = resolve(host);
        if (destination == null) {
            return;
        }
        int identifier = IcmpIdentifiers.next();
        Console.println("Трассировка пути до " + host + " (" + destination.getHostAddress()
                + "), максимум " + maxHops + " прыжков");
        for (int ttl = 1; ttl <= maxHops; ttl++) {
            if (probeHop(destination, identifier, ttl)) {
                return;
            }
        }
        Console.println("Узел " + host + " не достигнут за " + maxHops + " прыжков");
    }

    private boolean probeHop(InetAddress destination, int identifier, int ttl) {
        long sentAt = System.currentTimeMillis();
        channel.sendEcho(destination, ttl, identifier, ttl, sentAt);
        Optional<ReceivedDatagram> reply = channel.receiveFor(identifier, destination, sentAt + timeoutMs);
        if (reply.isEmpty()) {
            Console.printf("  %2d  *  превышен интервал ожидания%n", ttl);
            return false;
        }
        return handleHopReply(destination, ttl, sentAt, reply.get());
    }

    private boolean handleHopReply(InetAddress destination, int ttl, long sentAt, ReceivedDatagram datagram) {
        IcmpMessage icmp = datagram.icmp();
        InetAddress from = datagram.ip().source();
        if (icmp.isTimeExceeded()) {
            timeExceededHandler.handle(ttl, from, sentAt);
            return false;
        }
        if (icmp.isEchoReply()) {
            Console.printf("  %2d  %s  (узел назначения)%n", ttl, from.getHostAddress());
            echoReplyHandler.handle(host, from, datagram, ttl);
            Console.println("Трассировка до " + destination.getHostAddress() + " завершена");
            return true;
        }
        if (icmp.isHostUnreachable() || icmp.isDestinationUnreachable()) {
            hostUnreachableHandler.handle(host, from, icmp.code());
            return true;
        }
        Console.printf("  %2d  %s  ICMP type=%d code=%d%n", ttl, from.getHostAddress(), icmp.type(), icmp.code());
        return false;
    }

    private static InetAddress resolve(String host) {
        try {
            return InetAddress.getByName(host);
        } catch (UnknownHostException e) {
            Console.println("Неизвестный хост: " + host);
            return null;
        }
    }
}
