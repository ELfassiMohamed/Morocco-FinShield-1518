package com.kantara.web;

import java.awt.Desktop;
import java.net.ConnectException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.time.Duration;

public class Main {
    public static void main(String[] args) {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 7070;
        String url = "http://localhost:" + port;

        if (isKantaraRunning(port)) {
            System.out.println("[Kantara] Existing server found at " + url);
            openBrowser(url);
            return;
        }

        KantaraServer server = new KantaraServer();
        try {
            server.create().start(port);
        } catch (RuntimeException e) {
            if (isKantaraRunning(port)) {
                System.out.println("[Kantara] Existing server found at " + url);
                openBrowser(url);
                return;
            }
            throw e;
        }

        System.out.println("[Kantara] Server started at " + url);
        openBrowser(url);
    }

    private static boolean isKantaraRunning(int port) {
        try {
            HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(500))
                .build();
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/health"))
                .timeout(Duration.ofMillis(800))
                .GET()
                .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200 && response.body().contains("\"status\":\"ok\"");
        } catch (ConnectException e) {
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private static void openBrowser(String url) {
        try {
            if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                System.out.println("[Kantara] Desktop API not supported. Open your browser at: " + url);
                return;
            }
            Desktop.getDesktop().browse(URI.create(url));
        } catch (Exception e) {
            System.out.println("[Kantara] Could not open browser automatically. Open your browser at: " + url);
        }
    }
}
