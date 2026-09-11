package org.example.lab5;

final class IcmpChecksum {
    private IcmpChecksum() {
    }

    static int of(byte[] data) {
        int sum = 0;
        int offset = 0;
        while (offset < data.length - 1) {
            sum += ((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF);
            offset += 2;
        }
        if (offset < data.length) {
            sum += (data[offset] & 0xFF) << 8;
        }
        return fold(sum);
    }

    private static int fold(int sum) {
        while ((sum >> 16) != 0) {
            sum = (sum & 0xFFFF) + (sum >> 16);
        }
        return ~sum & 0xFFFF;
    }
}
