package org.example.lab5;

final class IcmpTypes {
    static final int ECHO_REPLY = 0;
    static final int DESTINATION_UNREACHABLE = 3;
    static final int ECHO_REQUEST = 8;
    static final int TIME_EXCEEDED = 11;

    static final int CODE_TTL_EXPIRED_IN_TRANSIT = 0;
    static final int CODE_HOST_UNREACHABLE = 1;

    private IcmpTypes() {
    }
}
