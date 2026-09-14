package org.example.lab5;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;

record IpPacket(
        int headerLength,
        int ttl,
        int protocol,
        InetAddress source,
        InetAddress destination,
        byte[] payload
) {
    static IpPacket parse(byte[] raw, int length) {
        if (length < 20) {
            throw new IllegalArgumentException("IP-пакет слишком короткий");
        }
        int headerLength = (raw[0] & 0x0F) * 4;
        int ttl = raw[8] & 0xFF;
        int protocol = raw[9] & 0xFF;
        InetAddress source = addressAt(raw, 12);
        InetAddress destination = addressAt(raw, 16);
        byte[] payload = Arrays.copyOfRange(raw, headerLength, length);
        return new IpPacket(headerLength, ttl, protocol, source, destination, payload);
    }

    static IpPacket wrapIcmp(InetAddress source, byte[] icmp) {
        InetAddress any = addressAt(new byte[4], 0);
        InetAddress from = source == null ? any : source;
        return new IpPacket(0, 0, 1, from, any, icmp);
    }

    private static InetAddress addressAt(byte[] raw, int offset) {
        try {
            return InetAddress.getByAddress(Arrays.copyOfRange(raw, offset, offset + 4));
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Некорректный IP-адрес в заголовке", e);
        }
    }
}
