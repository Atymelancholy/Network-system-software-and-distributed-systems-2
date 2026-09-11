package org.example.lab5;

import com.sun.jna.Library;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Platform;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;

import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

final class NativeSockets implements AutoCloseable {
    static final int MSG_PEEK = 0x2;

    private static final int AF_INET = 2;
    private static final int SOCK_RAW = 3;
    private static final int IPPROTO_ICMP = 1;
    private static final int IPPROTO_IP = 0;

    private static final int SIO_RCVALL = 0x98000001;
    private static final int RCVALL_ON = 1;

    private final boolean windows = Platform.isWindows();
    private int posixFd = -1;
    private Pointer windowsSendSocket;
    private Pointer windowsRecvSocket;

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

    RecvResult recv(byte[] buffer, int flags) {
        SockaddrIn from = new SockaddrIn();
        IntByReference fromLen = new IntByReference(from.size());
        int received = invokeRecvFrom(buffer, flags, from, fromLen);
        if (received < 0) {
            return RecvResult.empty(isWouldBlock());
        }
        from.read();
        return new RecvResult(received, false);
    }

    @Override
    public void close() {
        if (windows) {
            closeWindows(windowsSendSocket);
            closeWindows(windowsRecvSocket);
            windowsSendSocket = null;
            windowsRecvSocket = null;
            return;
        }
        if (posixFd >= 0) {
            Posix.INSTANCE.close(posixFd);
            posixFd = -1;
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
        windowsRecvSocket = openWindowsSocket(IPPROTO_IP, "приёма IP");
        bindReceiveSocket();
        enableReceiveAll();
    }

    private Pointer openWindowsSocket(int protocol, String role) {
        Pointer socket = Winsock.INSTANCE.socket(AF_INET, SOCK_RAW, protocol);
        if (isInvalid(socket)) {
            throw new IcmpException("Не удалось создать raw-сокет для " + role
                    + ". Запустите программу от имени администратора. Код: " + lastError());
        }
        return socket;
    }

    private void bindReceiveSocket() {
        InetAddress local = localIpv4();
        SockaddrIn address = new SockaddrIn();
        address.setFamily(AF_INET);
        address.setAddress(local.getAddress());
        address.write();
        int result = Winsock.INSTANCE.bind(windowsRecvSocket, address, address.size());
        if (result != 0) {
            throw new IcmpException("bind(" + local.getHostAddress() + ") не удался: " + lastError());
        }
    }

    private void enableReceiveAll() {
        Memory mode = new Memory(4);
        mode.setInt(0, RCVALL_ON);
        IntByReference bytesReturned = new IntByReference();
        int result = Winsock.INSTANCE.WSAIoctl(
                windowsRecvSocket,
                SIO_RCVALL,
                mode,
                4,
                Pointer.NULL,
                0,
                bytesReturned,
                Pointer.NULL,
                Pointer.NULL
        );
        if (result != 0) {
            throw new IcmpException("Не удалось включить приём ICMP Time Exceeded (SIO_RCVALL): "
                    + lastError() + ". Нужны права администратора.");
        }
    }

    private static InetAddress localIpv4() {
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
            setNonBlocking(windowsRecvSocket);
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

    private int invokeRecvFrom(byte[] buffer, int flags, SockaddrIn from, IntByReference fromLen) {
        if (windows) {
            return Winsock.INSTANCE.recvfrom(windowsRecvSocket, buffer, buffer.length, flags, from, fromLen);
        }
        return Posix.INSTANCE.recvfrom(posixFd, buffer, buffer.length, flags, from, fromLen);
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

    private boolean isWouldBlock() {
        int error = lastError();
        if (windows) {
            return error == 10035;
        }
        return error == 11 || error == 35;
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

    record RecvResult(int length, boolean empty) {
        static RecvResult empty(boolean wouldBlock) {
            return new RecvResult(-1, wouldBlock);
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
