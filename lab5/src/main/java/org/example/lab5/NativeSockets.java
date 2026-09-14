package org.example.lab5;

import com.sun.jna.Library;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Platform;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class NativeSockets implements AutoCloseable {
    private static final int AF_INET = 2;
    private static final int SOCK_RAW = 3;
    private static final int IPPROTO_ICMP = 1;
    private static final int IPPROTO_IP = 0;

    private static final int SIO_RCVALL = 0x98000001;
    private static final int RCVALL_ON = 1;
    private static final int RCVALL_IPLEVEL = 3;

    private final boolean windows = Platform.isWindows();
    private int posixFd = -1;
    private Pointer windowsSendSocket;
    private final List<Pointer> windowsRecvSockets = new ArrayList<>();
    private int windowsRecvIndex;

    static NativeSockets openRawIcmp() {
        NativeSockets sockets = new NativeSockets();
        sockets.createRawIcmpSocket();
        sockets.enableNonBlocking();
        return sockets;
    }

    void setTtl(int ttl) {
        byte[] value = intBytes(ttl);
        int result = setsockopt(IPPROTO_IP, ipTtlOption(), value);
        if (result != 0) {
            throw new IcmpException("Не удалось установить TTL: " + lastError());
        }
    }

    void sendTo(byte[] packet, InetAddress destination) {
        SockaddrIn address = destinationAddress(destination);
        int sent = invokeSendTo(packet, address);
        if (sent < 0) {
            throw new IcmpException("Ошибка sendto: " + lastError());
        }
    }

    RecvResult recv(byte[] buffer) {
        if (windows) {
            return recvWindows(buffer);
        }
        return recvFromPosix(buffer);
    }

    @Override
    public void close() {
        if (windows) {
            closeWindows(windowsSendSocket);
            windowsSendSocket = null;
            for (Pointer socket : windowsRecvSockets) {
                closeWindows(socket);
            }
            windowsRecvSockets.clear();
            return;
        }
        if (posixFd >= 0) {
            Posix.INSTANCE.close(posixFd);
            posixFd = -1;
        }
    }

    private RecvResult recvWindows(byte[] buffer) {
        List<Pointer> sockets = windowsReadSockets();
        if (sockets.isEmpty()) {
            return RecvResult.empty();
        }
        for (int i = 0; i < sockets.size(); i++) {
            int index = (windowsRecvIndex + i) % sockets.size();
            RecvResult result = recvFromWindows(sockets.get(index), buffer);
            if (result.ok()) {
                windowsRecvIndex = (index + 1) % sockets.size();
                return result;
            }
        }
        return RecvResult.empty();
    }

    private List<Pointer> windowsReadSockets() {
        List<Pointer> sockets = new ArrayList<>();
        if (windowsSendSocket != null) {
            sockets.add(windowsSendSocket);
        }
        sockets.addAll(windowsRecvSockets);
        return sockets;
    }

    private RecvResult recvFromWindows(Pointer socket, byte[] buffer) {
        SockaddrIn from = new SockaddrIn();
        IntByReference fromLen = new IntByReference(from.size());
        int received = Winsock.INSTANCE.recvfrom(socket, buffer, buffer.length, 0, from, fromLen);
        if (received < 0) {
            return RecvResult.empty();
        }
        from.read();
        return new RecvResult(received, inetFrom(from));
    }

    private RecvResult recvFromPosix(byte[] buffer) {
        SockaddrIn from = new SockaddrIn();
        IntByReference fromLen = new IntByReference(from.size());
        int received = Posix.INSTANCE.recvfrom(posixFd, buffer, buffer.length, 0, from, fromLen);
        if (received < 0) {
            return RecvResult.empty();
        }
        from.read();
        return new RecvResult(received, inetFrom(from));
    }

    private static InetAddress inetFrom(SockaddrIn from) {
        try {
            return InetAddress.getByAddress(from.addressBytes());
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void closeWindows(Pointer socket) {
        if (socket != null) {
            Winsock.INSTANCE.closesocket(socket);
        }
    }

    private void createRawIcmpSocket() {
        if (windows) {
            createWindowsSocket();
            return;
        }
        createPosixSocket();
    }

    private void createWindowsSocket() {
        ensureWinsockStarted();
        windowsSendSocket = openWindowsSocket(IPPROTO_ICMP, "отправки ICMP");
        for (InetAddress local : localIpv4Addresses()) {
            Pointer socket = openWindowsSocket(IPPROTO_IP, "приёма IP");
            if (bindTo(socket, local) && enableReceiveAll(socket)) {
                windowsRecvSockets.add(socket);
            } else {
                closeWindows(socket);
            }
        }
    }

    private Pointer openWindowsSocket(int protocol, String role) {
        Pointer socket = Winsock.INSTANCE.socket(AF_INET, SOCK_RAW, protocol);
        if (isInvalid(socket)) {
            throw new IcmpException("Не удалось создать raw-сокет для " + role
                    + ". Запустите программу от имени администратора. Код: " + lastError());
        }
        return socket;
    }

    private boolean bindTo(Pointer socket, InetAddress local) {
        SockaddrIn address = new SockaddrIn();
        address.setFamily(AF_INET);
        address.setAddress(local.getAddress());
        address.write();
        return Winsock.INSTANCE.bind(socket, address, address.size()) == 0;
    }

    private boolean enableReceiveAll(Pointer socket) {
        return wsaIoctlRcvall(socket, RCVALL_ON) || wsaIoctlRcvall(socket, RCVALL_IPLEVEL);
    }

    private boolean wsaIoctlRcvall(Pointer socket, int modeValue) {
        Memory mode = new Memory(4);
        mode.setInt(0, modeValue);
        IntByReference bytesReturned = new IntByReference();
        return Winsock.INSTANCE.WSAIoctl(
                socket,
                SIO_RCVALL,
                mode,
                4,
                Pointer.NULL,
                0,
                bytesReturned,
                Pointer.NULL,
                Pointer.NULL
        ) == 0;
    }

    private static List<InetAddress> localIpv4Addresses() {
        List<InetAddress> addresses = new ArrayList<>();
        try {
            for (NetworkInterface networkInterface : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!networkInterface.isUp()) {
                    continue;
                }
                for (InetAddress address : Collections.list(networkInterface.getInetAddresses())) {
                    if (address instanceof Inet4Address && !address.isLoopbackAddress()) {
                        addresses.add(address);
                    }
                }
            }
        } catch (Exception ignored) {
        }
        if (addresses.isEmpty()) {
            addresses.add(probeDefaultIpv4());
        }
        return addresses;
    }

    private static InetAddress probeDefaultIpv4() {
        try (java.net.DatagramSocket probe = new java.net.DatagramSocket()) {
            probe.connect(InetAddress.getByName("8.8.8.8"), 80);
            InetAddress local = probe.getLocalAddress();
            if (local != null && !local.isAnyLocalAddress()) {
                return local;
            }
        } catch (Exception ignored) {
        }
        throw new IcmpException("Не удалось определить локальный IPv4-адрес интерфейса");
    }

    private void createPosixSocket() {
        posixFd = Posix.INSTANCE.socket(AF_INET, SOCK_RAW, IPPROTO_ICMP);
        if (posixFd < 0) {
            throw new IcmpException("Не удалось создать raw ICMP-сокет. "
                    + "Запустите программу через sudo или выдайте cap_net_raw. Код: " + lastError());
        }
    }

    private void enableNonBlocking() {
        if (windows) {
            setNonBlocking(windowsSendSocket);
            for (Pointer socket : windowsRecvSockets) {
                setNonBlocking(socket);
            }
            return;
        }
        int flags = Posix.INSTANCE.fcntl(posixFd, posixGetFl(), 0);
        if (flags < 0 || Posix.INSTANCE.fcntl(posixFd, posixSetFl(), flags | posixNonBlock()) < 0) {
            throw new IcmpException("fcntl(O_NONBLOCK) не удался: " + lastError());
        }
    }

    private void setNonBlocking(Pointer socket) {
        IntByReference mode = new IntByReference(1);
        int result = Winsock.INSTANCE.ioctlsocket(socket, Winsock.FIONBIO, mode);
        if (result != 0) {
            throw new IcmpException("ioctlsocket(FIONBIO) не удался: " + lastError());
        }
    }

    private int setsockopt(int level, int option, byte[] value) {
        if (windows) {
            return Winsock.INSTANCE.setsockopt(windowsSendSocket, level, option, value, value.length);
        }
        return Posix.INSTANCE.setsockopt(posixFd, level, option, value, value.length);
    }

    private int invokeSendTo(byte[] packet, SockaddrIn address) {
        address.write();
        if (windows) {
            return Winsock.INSTANCE.sendto(windowsSendSocket, packet, packet.length, 0, address, address.size());
        }
        return Posix.INSTANCE.sendto(posixFd, packet, packet.length, 0, address, address.size());
    }

    private SockaddrIn destinationAddress(InetAddress destination) {
        SockaddrIn address = new SockaddrIn();
        address.setFamily(AF_INET);
        address.setAddress(destination.getAddress());
        return address;
    }

    private int ipTtlOption() {
        if (windows || Platform.isMac()) {
            return 4;
        }
        return 2;
    }

    private int posixGetFl() {
        return 3;
    }

    private int posixSetFl() {
        return 4;
    }

    private int posixNonBlock() {
        if (Platform.isMac()) {
            return 4;
        }
        return 2048;
    }

    private int lastError() {
        if (windows) {
            return Winsock.INSTANCE.WSAGetLastError();
        }
        return Native.getLastError();
    }

    private static byte[] intBytes(int value) {
        return ByteBuffer.allocate(4).order(ByteOrder.nativeOrder()).putInt(value).array();
    }

    private static boolean isInvalid(Pointer socket) {
        return socket == null || Pointer.nativeValue(socket) == -1L;
    }

    private static void ensureWinsockStarted() {
        Memory data = new Memory(512);
        int result = Winsock.INSTANCE.WSAStartup((short) 0x0202, data);
        if (result != 0) {
            throw new IcmpException("WSAStartup не удался: " + result);
        }
    }

    record RecvResult(int length, InetAddress source) {
        static RecvResult empty() {
            return new RecvResult(-1, null);
        }

        boolean ok() {
            return length > 0;
        }
    }

    public interface Posix extends Library {
        Posix INSTANCE = Native.load("c", Posix.class);

        int socket(int domain, int type, int protocol);

        int sendto(int sockfd, byte[] buf, int len, int flags, SockaddrIn dest, int destLen);

        int recvfrom(int sockfd, byte[] buf, int len, int flags, SockaddrIn src, IntByReference srcLen);

        int setsockopt(int sockfd, int level, int optname, byte[] val, int len);

        int fcntl(int fd, int cmd, int arg);

        int close(int fd);
    }

    public interface Winsock extends Library {
        Winsock INSTANCE = Native.load("ws2_32", Winsock.class);
        int FIONBIO = 0x8004667E;

        int WSAStartup(short version, Memory data);

        Pointer socket(int af, int type, int protocol);

        int sendto(Pointer s, byte[] buf, int len, int flags, SockaddrIn dest, int destLen);

        int recvfrom(Pointer s, byte[] buf, int len, int flags, SockaddrIn src, IntByReference srcLen);

        int setsockopt(Pointer s, int level, int optname, byte[] val, int len);

        int bind(Pointer s, SockaddrIn addr, int len);

        int ioctlsocket(Pointer s, int cmd, IntByReference argp);

        int WSAIoctl(
                Pointer s,
                int code,
                Pointer inBuffer,
                int inSize,
                Pointer outBuffer,
                int outSize,
                IntByReference bytesReturned,
                Pointer overlapped,
                Pointer completion
        );

        int closesocket(Pointer s);

        int WSAGetLastError();
    }
}
