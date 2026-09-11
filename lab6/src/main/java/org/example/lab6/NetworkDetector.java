package org.example.lab6;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;

public final class NetworkDetector {
    private NetworkDetector() {
    }

    public static InterfaceInfo detect() throws SocketException, UnknownHostException {
        List<InterfaceInfo> found = listAll();
        if (found.isEmpty()) {
            throw new IllegalStateException("Не найден IPv4-интерфейс с broadcast-адресом");
        }
        return pickBest(found);
    }

    public static InterfaceInfo byIp(String ip) throws SocketException, UnknownHostException {
        String wanted = InetAddress.getByName(ip.trim()).getHostAddress();
        return listAll().stream()
                .filter(info -> info.ipText().equals(wanted))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Нет IPv4-интерфейса " + ip));
    }

    static List<InterfaceInfo> listAll() throws SocketException, UnknownHostException {
        List<InterfaceInfo> result = new ArrayList<>();
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        while (interfaces.hasMoreElements()) {
            collectFromInterface(interfaces.nextElement(), result);
        }
        return result;
    }

    private static void collectFromInterface(NetworkInterface nif, List<InterfaceInfo> result)
            throws SocketException, UnknownHostException {
        if (!nif.isUp() || nif.isLoopback()) {
            return;
        }
        for (InterfaceAddress address : nif.getInterfaceAddresses()) {
            InterfaceInfo info = fromAddress(nif, address);
            if (info != null) {
                result.add(info);
            }
        }
    }

    private static InterfaceInfo fromAddress(NetworkInterface nif, InterfaceAddress address)
            throws UnknownHostException {
        if (!(address.getAddress() instanceof Inet4Address ip)) {
            return null;
        }
        if (ip.isLinkLocalAddress()) {
            return null;
        }
        short prefix = address.getNetworkPrefixLength();
        if (prefix < 0 || prefix > 32) {
            return null;
        }
        InetAddress mask = maskFromPrefix(prefix);
        InetAddress broadcast = address.getBroadcast();
        if (broadcast == null) {
            broadcast = computeBroadcast(ip, prefix);
        }
        return new InterfaceInfo(nif, ip, mask, broadcast, prefix);
    }

    static InetAddress maskFromPrefix(short prefix) throws UnknownHostException {
        int maskBits = prefix == 0 ? 0 : -1 << (32 - prefix);
        return InetAddress.getByAddress(toBytes(maskBits));
    }

    static InetAddress computeBroadcast(Inet4Address ip, short prefix) throws UnknownHostException {
        int ipBits = toInt(ip.getAddress());
        int maskBits = prefix == 0 ? 0 : -1 << (32 - prefix);
        return InetAddress.getByAddress(toBytes(ipBits | ~maskBits));
    }

    private static InterfaceInfo pickBest(List<InterfaceInfo> found) {
        List<InterfaceInfo> physical = found.stream()
                .filter(info -> !isVirtual(info))
                .toList();
        List<InterfaceInfo> pool = physical.isEmpty() ? found : physical;
        return pool.stream()
                .min(Comparator.comparingInt(NetworkDetector::priority))
                .orElse(pool.getFirst());
    }

    private static int priority(InterfaceInfo info) {
        if (info.ip().isSiteLocalAddress()) {
            return 0;
        }
        return 1;
    }

    private static boolean isVirtual(InterfaceInfo info) {
        String name = (info.networkInterface().getName() + " " + info.displayName())
                .toLowerCase(Locale.ROOT);
        return name.contains("virtual")
                || name.contains("vmware")
                || name.contains("vbox")
                || name.contains("hyper-v")
                || name.contains("hyperv")
                || name.contains("vethernet")
                || name.contains("docker")
                || name.contains("wsl")
                || name.contains("bluetooth")
                || name.contains("loopback")
                || name.contains("vpn")
                || name.contains("tap")
                || name.contains("tun");
    }

    private static int toInt(byte[] bytes) {
        return ((bytes[0] & 0xFF) << 24)
                | ((bytes[1] & 0xFF) << 16)
                | ((bytes[2] & 0xFF) << 8)
                | (bytes[3] & 0xFF);
    }

    private static byte[] toBytes(int value) {
        return new byte[] {
                (byte) (value >>> 24),
                (byte) (value >>> 16),
                (byte) (value >>> 8),
                (byte) value
        };
    }
}
