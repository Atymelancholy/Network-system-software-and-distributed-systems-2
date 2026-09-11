package org.example.lab6;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.SocketTimeoutException;
import java.net.StandardSocketOptions;
import java.util.Arrays;

final class UdpChannel {
    private final InterfaceInfo local;
    private final InetAddress multicastGroup;
    private MulticastSocket socket;
    private boolean joined;

    UdpChannel(InterfaceInfo local) throws IOException {
        this.local = local;
        this.multicastGroup = InetAddress.getByName(ChatConfig.MULTICAST_GROUP);
        if (!multicastGroup.isMulticastAddress()) {
            throw new IOException("Адрес не является multicast: " + ChatConfig.MULTICAST_GROUP);
        }
    }

    void open() throws IOException {
        socket = new MulticastSocket(null);
        socket.setReuseAddress(true);
        socket.bind(new InetSocketAddress(ChatConfig.PORT));
        socket.setBroadcast(true);
        socket.setSoTimeout(ChatConfig.SOCKET_TIMEOUT_MS);
        socket.setOption(StandardSocketOptions.IP_MULTICAST_IF, local.networkInterface());
        socket.setOption(StandardSocketOptions.IP_MULTICAST_TTL, ChatConfig.MULTICAST_TTL);
        socket.setOption(StandardSocketOptions.IP_MULTICAST_LOOP, true);
        socket.setNetworkInterface(local.networkInterface());
        socket.setTimeToLive(ChatConfig.MULTICAST_TTL);
    }

    void joinGroup() throws IOException {
        if (joined) {
            return;
        }
        NetworkInterface nif = local.networkInterface();
        socket.joinGroup(new InetSocketAddress(multicastGroup, ChatConfig.PORT), nif);
        joined = true;
    }

    void leaveGroup() throws IOException {
        if (!joined) {
            return;
        }
        NetworkInterface nif = local.networkInterface();
        socket.leaveGroup(new InetSocketAddress(multicastGroup, ChatConfig.PORT), nif);
        joined = false;
    }

    boolean isJoined() {
        return joined;
    }

    void sendBroadcast(byte[] data) throws IOException {
        sendTo(local.broadcast(), data);
    }

    void sendMulticast(byte[] data) throws IOException {
        if (!joined) {
            throw new IOException("Хост не состоит в multicast-группе");
        }
        sendTo(multicastGroup, data);
    }

    Incoming receive() throws IOException {
        byte[] buffer = new byte[ChatConfig.BUFFER_SIZE];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        try {
            socket.receive(packet);
        } catch (SocketTimeoutException timeout) {
            return null;
        }
        byte[] data = Arrays.copyOf(packet.getData(), packet.getLength());
        return new Incoming(packet.getAddress(), data);
    }

    void close() {
        if (socket == null || socket.isClosed()) {
            return;
        }
        try {
            leaveGroup();
        } catch (IOException ignored) {
            joined = false;
        }
        socket.close();
    }

    private void sendTo(InetAddress target, byte[] data) throws IOException {
        DatagramPacket packet = new DatagramPacket(data, data.length, target, ChatConfig.PORT);
        socket.send(packet);
    }

    record Incoming(InetAddress from, byte[] data) {
    }
}
