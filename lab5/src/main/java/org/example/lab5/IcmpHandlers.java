package org.example.lab5;

import java.net.InetAddress;
import java.util.Locale;

final class EchoReplyHandler {
    void handle(String host, InetAddress from, ReceivedDatagram datagram, int sequence) {
        double rttMs = roundTripMs(datagram);
        Console.printf(
                Locale.US,
                "Ответ от %s (%s): seq=%d ttl=%d время=%.3f мс%n",
                host,
                from.getHostAddress(),
                sequence,
                datagram.ip().ttl(),
                rttMs
        );
    }

    private static double roundTripMs(ReceivedDatagram datagram) {
        long sentAt = datagram.icmp().timestampFromPayload();
        if (sentAt < 0) {
            return -1;
        }
        return (System.currentTimeMillis() - sentAt);
    }
}

final class TimeExceededHandler {
    void handle(int hop, InetAddress router, long sentAtMillis) {
        double rttMs = System.currentTimeMillis() - sentAtMillis;
        Console.printf(
                Locale.US,
                "  %2d  %s  %.3f мс  (время жизни истекло)%n",
                hop,
                router.getHostAddress(),
                rttMs
        );
    }
}

final class HostUnreachableHandler {
    void handle(String host, InetAddress from, int code) {
        Console.printf(
                "Хост %s недостижим (ICMP type=3 code=%d) от %s%n",
                host,
                code,
                from.getHostAddress()
        );
    }
}
