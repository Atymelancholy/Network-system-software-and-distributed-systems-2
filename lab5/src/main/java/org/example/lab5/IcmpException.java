package org.example.lab5;

public final class IcmpException extends RuntimeException {
    public IcmpException(String message) {
        super(message);
    }

    public IcmpException(String message, Throwable cause) {
        super(message, cause);
    }
}
