package org.example.lab5;

import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.Arrays;

final class IPv4Datagram {
    private static final int HEADER_LENGTH = 20;

    private IPv4Datagram() {
    }

    static byte[] icmpEchoRequest(InetAddress source, InetAddress destination, byte[] icmp) {
        if (icmp.length < 8) {
            throw new IllegalArgumentException("ICMP слишком короткий");
        }
        byte[] packet = new byte[HEADER_LENGTH + icmp.length];
        ByteBuffer buffer = ByteBuffer.wrap(packet);
        buffer.put((byte) 0x45);
        buffer.put((byte) 0);
        buffer.putShort((short) packet.length);
        buffer.putShort((short) 0);
        buffer.putShort((short) 0);
        buffer.put((byte) 64);
        buffer.put((byte) 1);
        buffer.putShort((short) 0);
        buffer.put(source.getAddress());
        buffer.put(destination.getAddress());
        System.arraycopy(icmp, 0, packet, HEADER_LENGTH, icmp.length);
        int ipChecksum = IcmpChecksum.of(Arrays.copyOfRange(packet, 0, HEADER_LENGTH));
        packet[10] = (byte) (ipChecksum >> 8);
        packet[11] = (byte) ipChecksum;
        return packet;
    }
}
