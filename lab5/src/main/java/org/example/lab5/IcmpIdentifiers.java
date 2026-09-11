package org.example.lab5;

import java.util.concurrent.atomic.AtomicInteger;

final class IcmpIdentifiers {
    private static final AtomicInteger NEXT = new AtomicInteger(1);

    private IcmpIdentifiers() {
    }

    static int next() {
        return NEXT.getAndUpdate(value -> value >= 0xFFFF ? 1 : value + 1);
    }
}
