package org.example.lab5;

import java.net.InetAddress;
import java.util.Arrays;

record ReceivedDatagram(IpPacket ip, IcmpMessage icmp) {
    static ReceivedDatagram parse(byte[] raw, int length, InetAddress from) {
        int offset = ipv4Offset(raw, length);
        if (offset >= 0) {
            byte[] ipBytes = Arrays.copyOfRange(raw, offset, length);
            IpPacket ip = IpPacket.parse(ipBytes, ipBytes.length);
            IcmpMessage icmp = IcmpMessage.parse(ip.payload());
            return new ReceivedDatagram(ip, icmp);
        }
        byte[] icmpBytes = Arrays.copyOf(raw, length);
        IcmpMessage icmp = IcmpMessage.parse(icmpBytes);
        return new ReceivedDatagram(IpPacket.wrapIcmp(from, icmpBytes), icmp);
    }

    private static int ipv4Offset(byte[] raw, int length) {
        if (length >= 20 && (raw[0] & 0xF0) == 0x40) {
            return 0;
        }
        if (length >= 34 && raw[12] == 0x08 && raw[13] == 0x00 && (raw[14] & 0xF0) == 0x40) {
            return 14;
        }
        return -1;
    }

    boolean belongsTo(int identifier, InetAddress target) {
        if (icmp.identifier() == identifier) {
            return true;
        }
        if (icmp.isEchoReply()) {
            return ip.source().equals(target);
        }
        InetAddress embedded = icmp.embeddedDestination();
        return embedded != null && embedded.equals(target);
    }

    boolean isIcmpResponse() {
        return ip.protocol() == 1
                && (icmp.isEchoReply() || icmp.isTimeExceeded() || icmp.isDestinationUnreachable());
    }
}
