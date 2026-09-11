package org.example.lab5;

import java.nio.ByteBuffer;
import java.util.Arrays;

record IcmpMessage(int type, int code, int identifier, int sequence, byte[] payload) {
    static byte[] echoRequest(int identifier, int sequence, long timestampMillis) {
        ByteBuffer buffer = ByteBuffer.allocate(16);
        buffer.put((byte) IcmpTypes.ECHO_REQUEST);
        buffer.put((byte) 0);
        buffer.putShort((short) 0);
        buffer.putShort((short) identifier);
        buffer.putShort((short) sequence);
        buffer.putLong(timestampMillis);
        byte[] packet = buffer.array();
        int checksum = IcmpChecksum.of(packet);
        packet[2] = (byte) (checksum >> 8);
        packet[3] = (byte) checksum;
        return packet;
    }

    static IcmpMessage parse(byte[] icmp) {
        int type = icmp[0] & 0xFF;
        int code = icmp[1] & 0xFF;
        EchoFields fields = readFields(type, icmp);
        byte[] payload = Arrays.copyOfRange(icmp, 8, icmp.length);
        return new IcmpMessage(type, code, fields.identifier, fields.sequence, payload);
    }

    long timestampFromPayload() {
        if (payload.length < 8) {
            return -1;
        }
        return ByteBuffer.wrap(payload, 0, 8).getLong();
    }

    boolean isEchoReply() {
        return type == IcmpTypes.ECHO_REPLY;
    }

    boolean isTimeExceeded() {
        return type == IcmpTypes.TIME_EXCEEDED
                && code == IcmpTypes.CODE_TTL_EXPIRED_IN_TRANSIT;
    }

    boolean isHostUnreachable() {
        return type == IcmpTypes.DESTINATION_UNREACHABLE
                && code == IcmpTypes.CODE_HOST_UNREACHABLE;
    }

    boolean isDestinationUnreachable() {
        return type == IcmpTypes.DESTINATION_UNREACHABLE;
    }

    private static EchoFields readFields(int type, byte[] icmp) {
        if (type == IcmpTypes.ECHO_REPLY || type == IcmpTypes.ECHO_REQUEST) {
            return echoFieldsAt(icmp, 4);
        }
        if (type == IcmpTypes.TIME_EXCEEDED || type == IcmpTypes.DESTINATION_UNREACHABLE) {
            return fieldsFromEmbeddedIp(icmp);
        }
        return new EchoFields(0, 0);
    }

    private static EchoFields fieldsFromEmbeddedIp(byte[] icmp) {
        if (icmp.length < 9) {
            return new EchoFields(0, 0);
        }
        byte[] embedded = Arrays.copyOfRange(icmp, 8, icmp.length);
        IpPacket inner = IpPacket.parse(embedded, embedded.length);
        if (inner.payload().length < 8) {
            return new EchoFields(0, 0);
        }
        return echoFieldsAt(inner.payload(), 4);
    }

    private static EchoFields echoFieldsAt(byte[] data, int offset) {
        int identifier = ((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF);
        int sequence = ((data[offset + 2] & 0xFF) << 8) | (data[offset + 3] & 0xFF);
        return new EchoFields(identifier, sequence);
    }

    private record EchoFields(int identifier, int sequence) {
    }
}
