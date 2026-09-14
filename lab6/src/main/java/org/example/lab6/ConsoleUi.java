package org.example.lab6;

import java.util.Locale;
import java.util.Scanner;

public final class ConsoleUi {
    private final ChatApp app;

    public ConsoleUi(ChatApp app) {
        this.app = app;
    }

    public void run() {
        try (Scanner scanner = new Scanner(System.in)) {
            while (true) {
                System.out.print("> ");
                if (!scanner.hasNextLine()) {
                    return;
                }
                String line = scanner.nextLine().trim();
                if (line.isEmpty()) {
                    continue;
                }
                if (handleLine(line)) {
                    return;
                }
            }
        }
    }

    private boolean handleLine(String line) {
        if (!line.startsWith("/")) {
            app.sendChat(line);
            return false;
        }
        String[] parts = line.split("\\s+", 2);
        String command = parts[0].toLowerCase(Locale.ROOT);
        String argument = parts.length > 1 ? parts[1].trim() : "";
        return execute(command, argument);
    }

    private boolean execute(String command, String argument) {
        switch (command) {
            case "/help" -> printHelp();
            case "/info" -> app.printNetworkInfo();
            case "/peers" -> app.printPeers();
            case "/nick" -> changeNick(argument);
            case "/mode" -> changeMode(argument);
            case "/join" -> app.joinMulticast();
            case "/leave" -> app.leaveMulticast();
            case "/ignore" -> ignore(argument);
            case "/kick" -> kick(argument);
            case "/unignore" -> unignore(argument);
            case "/quit", "/exit" -> {
                return true;
            }
            default -> app.println("Неизвестная команда. /help — справка");
        }
        return false;
    }

    private void changeNick(String argument) {
        if (argument.isBlank()) {
            app.println("Использование: /nick <имя>");
            return;
        }
        app.setNick(argument);
    }

    private void changeMode(String argument) {
        SendMode mode = parseMode(argument);
        if (mode == null) {
            app.println("Использование: /mode all|broadcast|multicast");
            return;
        }
        app.setSendMode(mode);
    }

    private void ignore(String argument) {
        if (argument.isBlank()) {
            app.println("Использование: /ignore <ip>");
            return;
        }
        app.ignoreLocally(argument);
    }

    private void kick(String argument) {
        if (argument.isBlank()) {
            app.println("Использование: /kick <ip>");
            return;
        }
        app.kick(argument);
    }

    private void unignore(String argument) {
        if (argument.isBlank()) {
            app.println("Использование: /unignore <ip>");
            return;
        }
        app.unignore(argument);
    }

    private static SendMode parseMode(String argument) {
        return switch (argument.toLowerCase(Locale.ROOT)) {
            case "all", "both" -> SendMode.BOTH;
            case "broadcast", "b" -> SendMode.BROADCAST;
            case "multicast", "m" -> SendMode.MULTICAST;
            default -> null;
        };
    }

    private void printHelp() {
        app.println("""
                Команды:
                  /info                 параметры интерфейса (IP, маска, broadcast)
                  /peers                IP запущенных приложений
                  /nick <имя>           сменить ник
                  /mode all|broadcast|multicast
                                        all — слать обоими способами ([B] и [M])
                  /join                 войти в multicast-группу
                  /leave                самостоятельно выйти из группы
                  /ignore <ip>          игнорировать хост только у себя
                  /kick <ip>            принудительно игнорировать хост у всех
                  /unignore <ip>        снять игнор и разослать остальным
                  /quit                 выход
                  текст                 отправить сообщение
                """);
    }
}
