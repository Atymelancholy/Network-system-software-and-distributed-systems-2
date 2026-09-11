package org.example.lab6;

enum PacketType {
    HELLO,
    MSG,
    BYE,
    JOIN,
    LEAVE,
    IGNORE,
    UNIGNORE;

    static PacketType fromName(String name) {
        try {
            return valueOf(name);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
