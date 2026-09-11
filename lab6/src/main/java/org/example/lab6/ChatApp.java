package org.example.lab6;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ChatApp {
    private final InterfaceInfo local;
    private final UdpChannel channel;
    private final PeerDirectory peers = new PeerDirectory();
    private final PacketDeduper deduper = new PacketDeduper();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Object consoleLock = new Object();

    private volatile String nick;
    private volatile SendMode sendMode = SendMode.BOTH;
    private Thread receiver;
    private Thread announcer;

    public ChatApp(InterfaceInfo local, String nick) throws IOException {
        this.local = local;
        this.nick = sanitizeNick(nick);
        this.channel = new UdpChannel(local);
    }

    public void start() throws IOException {
        channel.open();
        channel.joinGroup();
        running.set(true);
        receiver = startThread("udp-receiver", this::receiveLoop);
        announcer = startThread("udp-announcer", this::announceLoop);
        printStartup();
        sendControl(PacketType.JOIN, "");
    }

    public void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        sendControlQuietly(PacketType.BYE, "");
        channel.close();
        interrupt(receiver);
        interrupt(announcer);
    }

    void setNick(String value) {
        nick = sanitizeNick(value);
        println("Ник: " + nick);
        sendControl(PacketType.HELLO, "");
    }

    void setSendMode(SendMode mode) {
        sendMode = mode;
        println("Режим отправки: " + mode.name().toLowerCase(Locale.ROOT));
    }

    void sendChat(String text) {
        String payload = clip(text, ChatConfig.MAX_TEXT_LENGTH);
        if (payload.isBlank()) {
            return;
        }
        if (sendMode != SendMode.BROADCAST && !channel.isJoined()) {
            println("Multicast недоступен: сначала /join или /mode broadcast");
            if (sendMode == SendMode.MULTICAST) {
                return;
            }
        }
        sendByMode(PacketType.MSG, payload);
    }

    void joinMulticast() {
        try {
            if (channel.isJoined()) {
                println("Уже в multicast-группе");
                return;
            }
            channel.joinGroup();
            println("Вступили в группу " + ChatConfig.MULTICAST_GROUP);
            sendControl(PacketType.JOIN, "");
        } catch (IOException e) {
            println("Не удалось войти в группу: " + e.getMessage());
        }
    }

    void leaveMulticast() {
        try {
            if (!channel.isJoined()) {
                println("Уже вне multicast-группы");
                return;
            }
            sendControl(PacketType.LEAVE, "");
            channel.leaveGroup();
            println("Вышли из multicast-группы");
        } catch (IOException e) {
            println("Не удалось выйти из группы: " + e.getMessage());
        }
    }

    void ignoreLocally(String ip) {
        String target = normalizeIp(ip);
        if (target == null) {
            println("Некорректный IP");
            return;
        }
        peers.ignore(target);
        peers.remove(target);
        println("Локально игнорируется: " + target);
    }

    void kick(String ip) {
        String target = normalizeIp(ip);
        if (target == null) {
            println("Некорректный IP");
            return;
        }
        peers.ignore(target);
        peers.remove(target);
        sendControl(PacketType.IGNORE, target);
        println("Разослан принудительный игнор для " + target);
    }

    void unignore(String ip) {
        String target = normalizeIp(ip);
        if (target == null) {
            println("Некорректный IP");
            return;
        }
        peers.unignore(target);
        sendControl(PacketType.UNIGNORE, target);
        println("Снят игнор с " + target);
    }

    void printPeers() {
        List<Peer> active = peers.activePeers();
        println("Запущенные приложения:");
        println("  * " + local.ipText() + "  " + nick + "  (этот хост)");
        if (active.isEmpty()) {
            println("  других участников пока нет");
        }
        for (Peer peer : active) {
            println("  * " + peer.ip() + "  " + peer.nick()
                    + "  (" + peer.ageSeconds(System.currentTimeMillis()) + " с назад)");
        }
        List<String> ignored = peers.ignoredIps();
        if (!ignored.isEmpty()) {
            println("Игнорируются: " + String.join(", ", ignored));
        }
    }

    void printNetworkInfo() {
        println("Интерфейс : " + local.displayName());
        println("IP        : " + local.ipText());
        println("Маска     : " + local.maskText() + "  (/" + local.prefixLength() + ")");
        println("Broadcast : " + local.broadcastText());
        println("Multicast : " + ChatConfig.MULTICAST_GROUP + ":" + ChatConfig.PORT
                + "  TTL=" + ChatConfig.MULTICAST_TTL);
        println("Порт      : " + ChatConfig.PORT);
        println("В группе  : " + (channel.isJoined() ? "да" : "нет"));
        println("Режим     : " + sendMode.name().toLowerCase(Locale.ROOT));
        printAllInterfaces();
    }

    private void printAllInterfaces() {
        try {
            println("IPv4-интерфейсы:");
            for (InterfaceInfo info : NetworkDetector.listAll()) {
                String mark = info.ipText().equals(local.ipText()) ? "  <- выбран" : "";
                println("  " + info.ipText()
                        + "  mask " + info.maskText()
                        + "  bcast " + info.broadcastText()
                        + "  (" + info.displayName() + ")" + mark);
            }
        } catch (Exception e) {
            println("Не удалось получить список интерфейсов: " + e.getMessage());
        }
    }

    private void receiveLoop() {
        while (running.get()) {
            try {
                UdpChannel.Incoming incoming = channel.receive();
                if (incoming != null) {
                    handleIncoming(incoming);
                }
            } catch (IOException e) {
                if (running.get()) {
                    println("Ошибка приёма: " + e.getMessage());
                }
            }
        }
    }

    private void announceLoop() {
        while (running.get()) {
            peers.evictExpired();
            sendControlQuietly(PacketType.HELLO, "");
            sleep(ChatConfig.HELLO_INTERVAL_MS);
        }
    }

    private void handleIncoming(UdpChannel.Incoming incoming) {
        String fromIp = incoming.from().getHostAddress();
        if (isSelf(fromIp)) {
            return;
        }
        if (peers.isIgnored(fromIp)) {
            return;
        }
        Packet packet = Packet.decode(new String(incoming.data(), StandardCharsets.UTF_8));
        if (packet == null) {
            return;
        }
        if (packet.type() != PacketType.MSG && deduper.seenRecently(packet, fromIp)) {
            return;
        }
        dispatch(packet, fromIp);
    }

    private void dispatch(Packet packet, String fromIp) {
        boolean firstSeen = peers.activePeers().stream()
                .noneMatch(peer -> peer.ip().equals(fromIp));
        peers.touch(fromIp, packet.nick());
        if (firstSeen && packet.type() != PacketType.BYE) {
            printEvent(packet, fromIp, "в сети");
        }
        switch (packet.type()) {
            case HELLO -> {
            }
            case MSG -> printMessage(packet, fromIp);
            case BYE -> onBye(packet, fromIp);
            case JOIN -> printEvent(packet, fromIp, "вошёл в multicast-группу");
            case LEAVE -> printEvent(packet, fromIp, "вышел из multicast-группы");
            case IGNORE -> onIgnore(packet);
            case UNIGNORE -> onUnignore(packet);
        }
    }

    private void onBye(Packet packet, String fromIp) {
        peers.remove(fromIp);
        printEvent(packet, fromIp, "покинул чат");
    }

    private void onIgnore(Packet packet) {
        String target = normalizeIp(packet.payload());
        if (target == null) {
            return;
        }
        peers.ignore(target);
        peers.remove(target);
        if (isSelf(target)) {
            println("Вас принудительно игнорируют участники чата");
            return;
        }
        println(packet.nick() + " просит игнорировать " + target);
    }

    private void onUnignore(Packet packet) {
        String target = normalizeIp(packet.payload());
        if (target == null) {
            return;
        }
        peers.unignore(target);
        println(packet.nick() + " снял игнор с " + target);
    }

    private void sendControl(PacketType type, String payload) {
        deliver(type, payload, true, true, false);
    }

    private void sendControlQuietly(PacketType type, String payload) {
        deliver(type, payload, true, true, true);
    }

    private void sendByMode(PacketType type, String payload) {
        SendMode mode = sendMode;
        boolean broadcast = mode != SendMode.MULTICAST;
        boolean multicast = mode != SendMode.BROADCAST;
        deliver(type, payload, broadcast, multicast, false);
    }

    private void deliver(
            PacketType type,
            String payload,
            boolean broadcast,
            boolean multicast,
            boolean quiet
    ) {
        Packet packet = new Packet(type, nick, local.ipText(), "B", payload);
        if (broadcast) {
            sendVia(packet, "B", quiet);
        }
        if (multicast) {
            sendVia(packet, "M", quiet);
        }
    }

    private void sendVia(Packet packet, String via, boolean quiet) {
        byte[] data = packet.withVia(via).encode().getBytes(StandardCharsets.UTF_8);
        try {
            if ("B".equals(via)) {
                channel.sendBroadcast(data);
                return;
            }
            if (!channel.isJoined()) {
                return;
            }
            channel.sendMulticast(data);
        } catch (IOException e) {
            if (!quiet) {
                println("Ошибка отправки (" + via + "): " + e.getMessage());
            }
        }
    }

    private void printStartup() {
        println("=== UDP P2P-чат, лабораторная №6 ===");
        printNetworkInfo();
        println("Ник: " + nick);
        println("Команда /help — список команд");
    }

    private void printMessage(Packet packet, String fromIp) {
        println("[" + now() + "][" + packet.via() + "] "
                + packet.nick() + "@" + fromIp + ": " + packet.payload());
    }

    private void printEvent(Packet packet, String fromIp, String event) {
        println("[" + now() + "] " + packet.nick() + "@" + fromIp + " " + event);
    }

    void println(String line) {
        synchronized (consoleLock) {
            System.out.println(line);
        }
    }

    private boolean isSelf(String ip) {
        return local.ipText().equals(ip);
    }

    private static String sanitizeNick(String value) {
        String nick = value == null ? "" : value.replace("|", " ").trim();
        if (nick.isBlank()) {
            nick = "anon";
        }
        return clip(nick, ChatConfig.MAX_NICK_LENGTH);
    }

    private static String clip(String value, int max) {
        if (value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }

    private static String normalizeIp(String raw) {
        try {
            InetAddress address = InetAddress.getByName(raw.trim());
            if (!(address instanceof java.net.Inet4Address)) {
                return null;
            }
            return address.getHostAddress();
        } catch (Exception e) {
            return null;
        }
    }

    private static String now() {
        return LocalTime.now().truncatedTo(ChronoUnit.SECONDS).toString();
    }

    private static Thread startThread(String name, Runnable task) {
        Thread thread = new Thread(task, name);
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    private static void interrupt(Thread thread) {
        if (thread != null) {
            thread.interrupt();
        }
    }

    private static void sleep(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
