package org.example.lab6;

final class ChatConfig {
    static final int PORT = 8888;
    static final String MULTICAST_GROUP = "239.255.1.1";
    static final int MULTICAST_TTL = 1;
    static final int BUFFER_SIZE = 4096;
    static final int HELLO_INTERVAL_MS = 2000;
    static final int PEER_TIMEOUT_MS = 8000;
    static final int SOCKET_TIMEOUT_MS = 500;
    static final int MAX_NICK_LENGTH = 32;
    static final int MAX_TEXT_LENGTH = 1500;

    private ChatConfig() {
    }
}
