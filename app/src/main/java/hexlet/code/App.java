package hexlet.code;

import com.zaxxer.hikari.HikariDataSource;
import gg.jte.ContentType;
import gg.jte.TemplateEngine;
import gg.jte.resolve.ResourceCodeResolver;
import hexlet.code.database.DatabaseConfig;
import hexlet.code.database.DatabaseInitializer;
import hexlet.code.model.Url;
import hexlet.code.model.UrlCheck;
import hexlet.code.repository.UrlCheckRepository;
import hexlet.code.repository.UrlRepository;
import hexlet.code.service.UrlCheckService;
import hexlet.code.utils.UrlNormalizer;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.rendering.template.JavalinJte;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class App {
    private static final Logger LOGGER = LoggerFactory.getLogger(App.class);
    private static final int DEFAULT_PORT = 7070;
    private static final String NOT_FOUND_MESSAGE = "Page not found";

    private App() {
    }

    public static Javalin getApp() {
        HikariDataSource dataSource = initDataSource();
        UrlRepository urlRepository = new UrlRepository(dataSource);
        UrlCheckRepository urlCheckRepository = new UrlCheckRepository(dataSource);
        UrlCheckService urlCheckService = new UrlCheckService();
        Javalin app = Javalin.create(config -> {
            config.bundledPlugins.enableDevLogging();
            config.fileRenderer(new JavalinJte(createTemplateEngine()));
        });
        app.events(events -> events.serverStopped(dataSource::close));
        app.get("/", ctx -> renderHomePage(ctx, "", null, null));
        app.post("/urls", ctx -> handleCreateUrl(ctx, urlRepository));
        app.get("/urls", ctx -> renderUrlsPage(ctx, urlRepository, urlCheckRepository));
        app.get("/urls/{id}", ctx -> renderUrlPage(ctx, urlRepository, urlCheckRepository));
        app.post("/urls/{id}/checks", ctx -> handleCreateCheck(ctx, urlRepository, urlCheckRepository, urlCheckService));
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
            dataSource.close();
            throw new IllegalStateException("Failed to initialize database", e);
        }
    }

    private static void renderHomePage(Context ctx, String currentUrl, String flashType, String flashMessage) {
        Map<String, Object> model = baseTemplateData(ctx, flashType, flashMessage);
        model.put("url", currentUrl);
        ctx.render("index.jte", model);
    }

    private static void handleCreateUrl(Context ctx, UrlRepository urlRepository) throws SQLException {
        String rawUrl = Optional.ofNullable(ctx.formParam("url")).orElse("");

        try {
            String normalizedUrl = normalizeUrl(rawUrl);
            Optional<Url> existingUrl = urlRepository.findByName(normalizedUrl);
            if (existingUrl.isPresent()) {
                setFlash(ctx, "info", "Страница уже существует");
                ctx.redirect("/urls/" + existingUrl.get().id());
                return;
            }

            Url url = new Url(normalizedUrl, Timestamp.from(Instant.now()));
            Url savedUrl = urlRepository.save(url);
            setFlash(ctx, "success", "Страница успешно добавлена");
            ctx.redirect("/urls/" + savedUrl.id());
        } catch (IllegalArgumentException e) {
            ctx.status(422);
            renderHomePage(ctx, rawUrl, "danger", "Некорректный URL");
        }
    }

    private static void renderUrlsPage(
        Context ctx,
        UrlRepository urlRepository,
        UrlCheckRepository urlCheckRepository
    ) throws SQLException {
        List<Url> urls = urlRepository.findAll();
        Map<Long, UrlCheck> latestChecks = urlCheckRepository.findLatestChecks();
        Map<String, Object> model = baseTemplateData(ctx, null, null);
        model.put("urls", urls);
        model.put("latestChecks", latestChecks);
        ctx.render("urls/index.jte", model);
    }

    private static void renderUrlPage(
        Context ctx,
        UrlRepository urlRepository,
        UrlCheckRepository urlCheckRepository
    ) throws SQLException {
        Optional<Url> url = findRequestedUrl(ctx, urlRepository);
        if (url.isEmpty()) {
            return;
        }

        Map<String, Object> model = baseTemplateData(ctx, null, null);
        model.put("url", url.get());
        model.put("checks", urlCheckRepository.findByUrlId(url.get().id()));
        ctx.render("urls/show.jte", model);
    }

    private static void handleCreateCheck(
        Context ctx,
        UrlRepository urlRepository,
        UrlCheckRepository urlCheckRepository,
        UrlCheckService urlCheckService
    ) throws SQLException {
        Optional<Url> url = findRequestedUrl(ctx, urlRepository);
        if (url.isEmpty()) {
            return;
        }

        try {
            UrlCheck urlCheck = urlCheckService.perform(url.get());
            if (urlCheck.statusCode() >= 400) {
                setFlash(ctx, "danger", "Произошла ошибка при проверке");
            } else {
                urlCheckRepository.save(urlCheck);
                setFlash(ctx, "success", "Страница успешно проверена");
            }
            ctx.redirect(urlPath(url.get().id()));
        } catch (RuntimeException e) {
            setFlash(ctx, "danger", "Произошла ошибка при проверке");
            ctx.redirect(urlPath(url.get().id()));
        }
    }

    private static String normalizeUrl(String rawUrl) {
        return UrlNormalizer.normalize(rawUrl);
    }

    private static Map<String, Object> baseTemplateData(Context ctx, String flashType, String flashMessage) {
        Map<String, Object> model = new HashMap<>();
        FlashMessage resolvedFlash = flashMessage == null
            ? consumeFlash(ctx)
            : new FlashMessage(flashType, flashMessage);
        model.put("flashType", resolvedFlash.type());
        model.put("flashMessage", resolvedFlash.message());
        return model;
    }

    private static void setFlash(Context ctx, String type, String message) {
        ctx.sessionAttribute("flashType", type);
        ctx.sessionAttribute("flashMessage", message);
    }

    private static FlashMessage consumeFlash(Context ctx) {
        String flashType = ctx.consumeSessionAttribute("flashType");
        String flashMessage = ctx.consumeSessionAttribute("flashMessage");
        return new FlashMessage(flashType, flashMessage);
    }

    private static Optional<Url> findRequestedUrl(Context ctx, UrlRepository urlRepository) throws SQLException {
        Long urlId = parseUrlId(ctx);
        if (urlId == null) {
            renderNotFound(ctx);
            return Optional.empty();
        }

        Optional<Url> url = urlRepository.find(urlId);
        if (url.isEmpty()) {
            renderNotFound(ctx);
        }
        return url;
    }

    private static Long parseUrlId(Context ctx) {
        try {
            return Long.parseLong(ctx.pathParam("id"));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static void renderNotFound(Context ctx) {
        ctx.status(404);
        ctx.result(NOT_FOUND_MESSAGE);
    }

    private static String urlPath(long urlId) {
        return "/urls/" + urlId;
    }

    private static int resolvePort() {
        String port = System.getProperty("PORT");
        if (port == null || port.isBlank()) {
            port = System.getenv().getOrDefault("PORT", String.valueOf(DEFAULT_PORT));
        }
        try {
            return Integer.parseInt(port);
        } catch (NumberFormatException e) {
            LOGGER.warn("Invalid PORT value '{}', using default {}", port, DEFAULT_PORT);
            return DEFAULT_PORT;
        }
    }

    public static void main(String[] args) {
        int port = resolvePort();
        Javalin app = getApp();
        app.start(port);
        LOGGER.info("Application started on port {}", port);
    }

    private record FlashMessage(String type, String message) {
    }
}
