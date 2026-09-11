package org.example.lab5;

import java.net.InetAddress;
import java.util.Optional;

public final class IcmpChannel implements AutoCloseable {
    private static final int BUFFER_SIZE = 65535;
    private static final int PEEK_SLEEP_MS = 1;

    private final NativeSockets sockets;
    private final Object sendLock = new Object();
    private final Object recvLock = new Object();

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

    Optional<ReceivedDatagram> receiveFor(int identifier, long deadlineMillis) {
        byte[] buffer = new byte[BUFFER_SIZE];
        while (System.currentTimeMillis() < deadlineMillis) {
            Optional<ReceivedDatagram> matched = peekAndTakeIfMine(buffer, identifier);
            if (matched.isPresent()) {
                return matched;
            }
            sleepBriefly();
        }
        return Optional.empty();
    }

    private Optional<ReceivedDatagram> peekAndTakeIfMine(byte[] buffer, int identifier) {
        synchronized (recvLock) {
            NativeSockets.RecvResult peeked = sockets.recv(buffer, NativeSockets.MSG_PEEK);
            if (!peeked.ok()) {
                return Optional.empty();
            }
            ReceivedDatagram datagram = parseOrNull(buffer, peeked.length());
            if (datagram == null || !datagram.isIcmpResponse()) {
                sockets.recv(buffer, 0);
                return Optional.empty();
            }
            if (!datagram.belongsTo(identifier)) {
                return Optional.empty();
            }
            NativeSockets.RecvResult consumed = sockets.recv(buffer, 0);
            if (!consumed.ok()) {
                return Optional.empty();
            }
            return Optional.ofNullable(parseOrNull(buffer, consumed.length()));
        }
    }

    private static ReceivedDatagram parseOrNull(byte[] buffer, int length) {
        try {
            return ReceivedDatagram.parse(buffer, length);
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
