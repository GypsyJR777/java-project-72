package hexlet.code;

import com.zaxxer.hikari.HikariDataSource;
import gg.jte.ContentType;
import gg.jte.TemplateEngine;
import gg.jte.resolve.ResourceCodeResolver;
import hexlet.code.controller.UrlController;
import hexlet.code.database.DatabaseConfig;
import hexlet.code.database.DatabaseInitializer;
import hexlet.code.exception.DatabaseException;
import hexlet.code.repository.UrlCheckRepository;
import hexlet.code.repository.UrlRepository;
import hexlet.code.service.UrlCheckService;
import io.javalin.Javalin;
import io.javalin.http.HttpStatus;
import io.javalin.rendering.template.JavalinJte;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class App {
    private static final int DEFAULT_PORT = 7070;

    private App() {
    }

    public static Javalin getApp() {
        HikariDataSource dataSource = initDataSource();
        UrlController urlController = new UrlController(
            new UrlRepository(dataSource),
            new UrlCheckRepository(dataSource),
            new UrlCheckService()
        );

        Javalin app = Javalin.create(config -> {
            config.bundledPlugins.enableDevLogging();
            config.fileRenderer(new JavalinJte(createTemplateEngine()));
        });

        app.events(events -> events.serverStopped(dataSource::close));
        app.exception(DatabaseException.class, (e, ctx) -> {
            log.error("Database request processing failed", e);
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR);
            ctx.result("Internal server error");
        });
        app.get("/", urlController::showHomePage);
        app.post("/urls", urlController::createUrl);
        app.get("/urls", urlController::showUrlsPage);
        app.get("/urls/{id}", urlController::showUrlPage);
        app.post("/urls/{id}/checks", urlController::createCheck);
        return app;
    }

    private static TemplateEngine createTemplateEngine() {
        ClassLoader classLoader = App.class.getClassLoader();
        ResourceCodeResolver codeResolver = new ResourceCodeResolver("templates", classLoader);
        Path tempDirectory = Paths.get(System.getProperty("java.io.tmpdir"), "jte-classes");
        return TemplateEngine.create(codeResolver, tempDirectory, ContentType.Html);
    }

    private static HikariDataSource initDataSource() {
        HikariDataSource dataSource = DatabaseConfig.getDataSource();
        try {
            DatabaseInitializer.run(dataSource);
            return dataSource;
        } catch (SQLException e) {
            log.error("Failed to initialize database", e);
            dataSource.close();
            throw new IllegalStateException("Failed to initialize database", e);
        }
    }

    static int resolvePort() {
        String port = System.getProperty("PORT");
        if (port == null || port.isBlank()) {
            port = System.getenv().getOrDefault("PORT", String.valueOf(DEFAULT_PORT));
        }
        try {
            return Integer.parseInt(port);
        } catch (NumberFormatException e) {
            log.warn("Invalid PORT value '{}', using default {}", port, DEFAULT_PORT);
            return DEFAULT_PORT;
        }
    }

    public static void main(String[] args) {
        int port = resolvePort();
        Javalin app = getApp();
        app.start(port);
        log.info("Application started on port {}", port);
    }
}
