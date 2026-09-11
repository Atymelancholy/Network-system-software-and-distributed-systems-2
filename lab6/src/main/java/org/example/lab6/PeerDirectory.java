package org.example.lab6;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

final class PeerDirectory {
    private final ConcurrentHashMap<String, Peer> peers = new ConcurrentHashMap<>();
    private final Set<String> ignored = ConcurrentHashMap.newKeySet();

    void touch(String ip, String nick) {
        long now = System.currentTimeMillis();
        peers.compute(ip, (key, existing) -> {
            if (existing == null) {
                return new Peer(key, nick, now);
            }
            existing.refresh(nick, now);
            return existing;
        });
    }

    void remove(String ip) {
        peers.remove(ip);
    }

    boolean isIgnored(String ip) {
        return ignored.contains(ip);
    }

    boolean ignore(String ip) {
        return ignored.add(ip);
    }

    boolean unignore(String ip) {
        return ignored.remove(ip);
    }

    List<Peer> activePeers() {
        evictExpired();
        return new ArrayList<>(peers.values());
    }

    List<String> ignoredIps() {
        return ignored.stream().sorted().toList();
    }

    void evictExpired() {
        long now = System.currentTimeMillis();
        peers.values().removeIf(peer -> peer.expired(now, ChatConfig.PEER_TIMEOUT_MS));
    }
}
