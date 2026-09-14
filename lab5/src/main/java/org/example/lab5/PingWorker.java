package org.example.lab5;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Optional;

public final class PingWorker implements Runnable {
    private static final int DEFAULT_TTL = 64;

    private final IcmpChannel channel;
    private final String host;
    private final int count;
    private final int timeoutMs;
    private final EchoReplyHandler echoReplyHandler = new EchoReplyHandler();
    private final HostUnreachableHandler hostUnreachableHandler = new HostUnreachableHandler();

    public PingWorker(IcmpChannel channel, String host, int count, int timeoutMs) {
        this.channel = channel;
        this.host = host;
        this.count = count;
        this.timeoutMs = timeoutMs;
    }

    @Override
    public void run() {
        InetAddress destination = resolve(host);
        if (destination == null) {
            return;
        }
        int identifier = IcmpIdentifiers.next();
        Console.println("PING " + host + " (" + destination.getHostAddress() + ")");
        for (int sequence = 1; sequence <= count; sequence++) {
            pingOnce(destination, identifier, sequence);
        }
    }

    private void pingOnce(InetAddress destination, int identifier, int sequence) {
        long sentAt = System.currentTimeMillis();
        channel.sendEcho(destination, DEFAULT_TTL, identifier, sequence, sentAt);
        long deadline = sentAt + timeoutMs;
        Optional<ReceivedDatagram> reply = channel.receiveFor(identifier, destination, deadline);
        if (reply.isEmpty()) {
            Console.println("Истекло время ожидания ответа от " + host + " seq=" + sequence);
            return;
        }
        dispatch(destination, sequence, reply.get());
    }

    private void dispatch(InetAddress destination, int sequence, ReceivedDatagram datagram) {
        IcmpMessage icmp = datagram.icmp();
        if (icmp.isEchoReply()) {
            echoReplyHandler.handle(host, datagram.ip().source(), datagram, sequence);
            return;
        }
        if (icmp.isHostUnreachable() || icmp.isDestinationUnreachable()) {
            hostUnreachableHandler.handle(host, datagram.ip().source(), icmp.code());
            return;
        }
        Console.println("Неожиданный ICMP от " + destination.getHostAddress()
                + " type=" + icmp.type() + " code=" + icmp.code());
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
