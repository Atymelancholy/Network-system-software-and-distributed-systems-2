package org.example.lab5;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

public final class IcmpChannel implements AutoCloseable {
    private static final int BUFFER_SIZE = 65535;
    private static final int PEEK_SLEEP_MS = 1;

    private final NativeSockets sockets;
    private final Object sendLock = new Object();
    private final Object recvLock = new Object();
    private final List<ReceivedDatagram> inbox = new ArrayList<>();

    public static IcmpChannel open() {
        return new IcmpChannel(NativeSockets.openRawIcmp());
    }

    private IcmpChannel(NativeSockets sockets) {
        this.sockets = sockets;
    }

    void sendEcho(InetAddress destination, int ttl, int identifier, int sequence, long timestampMillis) {
        byte[] packet = IcmpMessage.echoRequest(identifier, sequence, timestampMillis);
        synchronized (sendLock) {
            sockets.setTtl(ttl);
            sockets.sendTo(packet, destination);
        }
    }

    Optional<ReceivedDatagram> receiveFor(int identifier, InetAddress target, long deadlineMillis) {
        byte[] buffer = new byte[BUFFER_SIZE];
        while (System.currentTimeMillis() < deadlineMillis) {
            Optional<ReceivedDatagram> matched = takeMatching(buffer, identifier, target);
            if (matched.isPresent()) {
                return matched;
            }
            sleepBriefly();
        }
        return Optional.empty();
    }

    private Optional<ReceivedDatagram> takeMatching(byte[] buffer, int identifier, InetAddress target) {
        synchronized (recvLock) {
            drainSocket(buffer);
            Iterator<ReceivedDatagram> iterator = inbox.iterator();
            while (iterator.hasNext()) {
                ReceivedDatagram datagram = iterator.next();
                if (datagram.belongsTo(identifier, target)) {
                    iterator.remove();
                    return Optional.of(datagram);
                }
            }
            return Optional.empty();
        }
    }

    private void drainSocket(byte[] buffer) {
        for (int i = 0; i < 512; i++) {
            NativeSockets.RecvResult received = sockets.recv(buffer);
            if (!received.ok()) {
                return;
            }
            ReceivedDatagram datagram = parseOrNull(buffer, received.length(), received.source());
            if (datagram != null && datagram.isIcmpResponse()) {
                inbox.add(datagram);
            }
        }
    }

    private static ReceivedDatagram parseOrNull(byte[] buffer, int length, InetAddress from) {
        try {
            return ReceivedDatagram.parse(buffer, length, from);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static void sleepBriefly() {
        try {
            Thread.sleep(PEEK_SLEEP_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void close() {
        sockets.close();
    }
}
