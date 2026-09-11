package org.example;

import org.example.lab6.ChatApp;
import org.example.lab6.ConsoleUi;
import org.example.lab6.InterfaceInfo;
import org.example.lab6.NetworkDetector;

public class Main {
    public static void main(String[] args) throws Exception {
        String nick = args.length > 0 ? args[0] : System.getProperty("user.name");
        InterfaceInfo local = args.length > 1
                ? NetworkDetector.byIp(args[1])
                : NetworkDetector.detect();
        ChatApp app = new ChatApp(local, nick);
        Runtime.getRuntime().addShutdownHook(new Thread(app::stop, "chat-shutdown"));
        app.start();
        try {
            new ConsoleUi(app).run();
        } finally {
            app.stop();
        }
    }
}
