package org.example.lab5;

final class Console {
    private static final Object LOCK = new Object();

    private Console() {
    }

    static void println(String line) {
        synchronized (LOCK) {
            System.out.println(line);
        }
    }

    static void printf(String format, Object... args) {
        printf(java.util.Locale.getDefault(), format, args);
    }

    static void printf(java.util.Locale locale, String format, Object... args) {
        synchronized (LOCK) {
            System.out.printf(locale, format, args);
        }
    }
}
