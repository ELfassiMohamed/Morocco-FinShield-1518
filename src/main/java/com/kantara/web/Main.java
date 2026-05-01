package com.kantara.web;

import java.awt.Desktop;
import java.net.URI;

public class Main {
    public static void main(String[] args) {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 7070;

        KantaraServer server = new KantaraServer();
        server.create().start(port);

        String url = "http://localhost:" + port;
        System.out.println("[Kantara] Server started at " + url);

        // Auto-open browser
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
            } else {
                System.out.println("[Kantara] Desktop API not supported. Open your browser at: " + url);
            }
        } catch (Exception e) {
            System.out.println("[Kantara] Could not open browser automatically. Open your browser at: " + url);
        }
    }
}
