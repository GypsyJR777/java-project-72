package hexlet.code;

import io.javalin.Javalin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class App {
    private static final Logger LOGGER = LoggerFactory.getLogger(App.class);
    private static final int DEFAULT_PORT = 7070;

    private App() {
    }

    public static Javalin getApp() {
        var app = Javalin.create(config -> {
            config.bundledPlugins.enableDevLogging();
        });
        app.get("/", ctx -> ctx.result("Hello World"));
        return app;
    }

    private static int resolvePort() {
        var port = System.getenv().getOrDefault("PORT", String.valueOf(DEFAULT_PORT));
        try {
            return Integer.parseInt(port);
        } catch (NumberFormatException e) {
            LOGGER.warn("Invalid PORT value '{}', using default {}", port, DEFAULT_PORT);
            return DEFAULT_PORT;
        }
    }

    public static void main(String[] args) {
        var port = resolvePort();
        var app = getApp();
        app.start(port);
        LOGGER.info("Application started on port {}", port);
    }
}
