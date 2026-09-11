package org.example.lab6;

final class Peer {
    private final String ip;
    private volatile String nick;
    private volatile long lastSeenMillis;

    Peer(String ip, String nick, long lastSeenMillis) {
        this.ip = ip;
        this.nick = nick;
        this.lastSeenMillis = lastSeenMillis;
    }

    String ip() {
        return ip;
    }

    String nick() {
        return nick;
    }

    void refresh(String nextNick, long nowMillis) {
        this.nick = nextNick;
        this.lastSeenMillis = nowMillis;
    }

    long ageSeconds(long nowMillis) {
        return Math.max(0, (nowMillis - lastSeenMillis) / 1000);
    }

    boolean expired(long nowMillis, long timeoutMillis) {
        return nowMillis - lastSeenMillis > timeoutMillis;
    }
}
