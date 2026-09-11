package org.example.lab6;

final class PacketDeduper {
    private static final long WINDOW_MS = 400;
    private String lastKey = "";
    private long lastAt;

    boolean seenRecently(Packet packet, String fromIp) {
        String key = fromIp + "|" + packet.type() + "|" + packet.nick() + "|" + packet.payload();
        long now = System.currentTimeMillis();
        if (key.equals(lastKey) && now - lastAt < WINDOW_MS) {
            return true;
        }
        lastKey = key;
        lastAt = now;
        return false;
    }
}
