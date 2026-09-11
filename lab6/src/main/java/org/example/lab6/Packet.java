package org.example.lab6;

record Packet(
        PacketType type,
        String nick,
        String senderIp,
        String via,
        String payload
) {
    String encode() {
        return type.name() + "|" + nick + "|" + senderIp + "|" + via + "|" + payload;
    }

    Packet withVia(String nextVia) {
        return new Packet(type, nick, senderIp, nextVia, payload);
    }

    static Packet decode(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String[] parts = raw.split("\\|", 5);
        if (parts.length < 4) {
            return null;
        }
        PacketType type = PacketType.fromName(parts[0]);
        if (type == null) {
            return null;
        }
        String payload = parts.length > 4 ? parts[4] : "";
        return new Packet(type, parts[1], parts[2], parts[3], payload);
    }
}
