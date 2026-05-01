package com.kantara.web;

import com.kantara.exception.KantaraException;
import com.kantara.processing.ProcessingService;
import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;

import java.util.List;
import java.util.Map;

public class KantaraServer {
    private final ProcessingService processingService;

    public KantaraServer() {
        this.processingService = new ProcessingService();
    }

    public Javalin create() {
        return Javalin.create(config -> {
            // Virtual threads for concurrency
            config.concurrency.useVirtualThreads = true;

            // Serve SPA from classpath:/public
            config.staticFiles.add(staticFiles -> {
                staticFiles.hostedPath = "/";
                staticFiles.directory = "/public";
                staticFiles.location = Location.CLASSPATH;
            });

            // SPA fallback for client-side routing
            config.spaRoot.addFile("/", "/public/index.html", Location.CLASSPATH);

            // Max upload size: 50MB
            config.http.maxRequestSize = 50_000_000L;

            // API routes
            config.routes.post("/api/process", new ProcessHandler(processingService));
            
            config.routes.get("/api/formats", ctx -> {
                ctx.json(Map.of(
                    "formats", List.of(
                        Map.of("id", "json", "name", "JSON", "description", "Structured JSON"),
                        Map.of("id", "markdown", "name", "Markdown", "description", "Clean Markdown")
                    )
                ));
            });
            
            config.routes.get("/api/health", ctx -> {
                ctx.json(Map.of("status", "ok", "version", "1.0.0"));
            });

            // Exception mapping
            config.routes.exception(KantaraException.class, (e, ctx) -> {
                ctx.status(400).json(Map.of("error", e.getMessage()));
            });
            config.routes.exception(Exception.class, (e, ctx) -> {
                e.printStackTrace();
                ctx.status(500).json(Map.of("error", "An unexpected error occurred: " + e.getMessage()));
            });
        });
    }
}
