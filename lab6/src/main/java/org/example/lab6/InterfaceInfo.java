package org.example.lab6;

import java.net.InetAddress;
import java.net.NetworkInterface;

public record InterfaceInfo(
        NetworkInterface networkInterface,
        InetAddress ip,
        InetAddress subnetMask,
        InetAddress broadcast,
        short prefixLength
) {
    String displayName() {
        return networkInterface.getDisplayName();
    }

    String ipText() {
        return ip.getHostAddress();
    }

    String maskText() {
        return subnetMask.getHostAddress();
    }

    String broadcastText() {
        return broadcast.getHostAddress();
    }
}
