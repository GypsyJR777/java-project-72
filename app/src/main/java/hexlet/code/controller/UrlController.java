package hexlet.code.controller;

import hexlet.code.exception.DatabaseException;
import hexlet.code.model.Url;
import hexlet.code.model.UrlCheck;
import hexlet.code.repository.UrlCheckRepository;
import hexlet.code.repository.UrlRepository;
import hexlet.code.service.UrlCheckService;
import hexlet.code.utils.UrlNormalizer;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class UrlController {
    private static final String NOT_FOUND_MESSAGE = "Page not found";
    private static final String URLS_PATH = "/urls";

    private final UrlRepository urlRepository;
    private final UrlCheckRepository urlCheckRepository;
    private final UrlCheckService urlCheckService;

    public UrlController(
        UrlRepository urlRepository,
        UrlCheckRepository urlCheckRepository,
        UrlCheckService urlCheckService
    ) {
        this.urlRepository = urlRepository;
        this.urlCheckRepository = urlCheckRepository;
        this.urlCheckService = urlCheckService;
    }

    public void showHomePage(Context ctx) {
        renderHomePage(ctx, "", null, null);
    }

    public void createUrl(Context ctx) {
        String rawUrl = Optional.ofNullable(ctx.formParam("url")).orElse("").trim();
        URI parsedUrl;

        try {
            parsedUrl = new URI(rawUrl);
        } catch (URISyntaxException e) {
            renderInvalidUrl(ctx, rawUrl);
            return;
        }

        String normalizedUrl = UrlNormalizer.normalize(parsedUrl).orElse(null);
        if (normalizedUrl == null) {
            renderInvalidUrl(ctx, rawUrl);
            return;
        }

        Url existingUrl = urlRepository.findByName(normalizedUrl).orElse(null);
        if (existingUrl != null) {
            setFlash(ctx, "info", "Страница уже существует");
            ctx.redirect(urlPath(existingUrl.id()));
            return;
        }

        Url savedUrl = urlRepository.save(new Url(normalizedUrl, Instant.now()))
            .orElseThrow(() -> new DatabaseException("Failed to save URL"));
        setFlash(ctx, "success", "Страница успешно добавлена");
        ctx.redirect(urlPath(savedUrl.id()));
    }

    public void showUrlsPage(Context ctx) {
        List<Url> urls = urlRepository.findAll();
        Map<Long, UrlCheck> latestChecks = urlCheckRepository.findLatestChecks();
        Map<String, Object> model = baseTemplateData(ctx, null, null);
        model.put("urls", urls);
        model.put("latestChecks", latestChecks);
        ctx.render("urls/index.jte", model);
    }

    public void showUrlPage(Context ctx) {
        Optional<Url> maybeUrl = findRequestedUrl(ctx);
        if (maybeUrl.isEmpty()) {
            return;
        }

        Url url = maybeUrl.orElseThrow();
        Map<String, Object> model = baseTemplateData(ctx, null, null);
        model.put("url", url);
        model.put("checks", urlCheckRepository.findByUrlId(url.id()));
        ctx.render("urls/show.jte", model);
    }

    public void createCheck(Context ctx) {
        Optional<Url> maybeUrl = findRequestedUrl(ctx);
        if (maybeUrl.isEmpty()) {
            return;
        }

        Url url = maybeUrl.orElseThrow();
        try {
            UrlCheck urlCheck = urlCheckService.perform(url);
            if (urlCheck.statusCode() >= 400) {
                setFlash(ctx, "danger", "Произошла ошибка при проверке");
            } else {
                urlCheckRepository.save(urlCheck).orElseThrow(() -> new DatabaseException("Failed to save URL check"));
                setFlash(ctx, "success", "Страница успешно проверена");
            }
        } catch (RuntimeException e) {
            setFlash(ctx, "danger", "Произошла ошибка при проверке");
        }
        ctx.redirect(urlPath(url.id()));
    }

    private void renderHomePage(Context ctx, String currentUrl, String flashType, String flashMessage) {
        Map<String, Object> model = baseTemplateData(ctx, flashType, flashMessage);
        model.put("url", currentUrl);
        ctx.render("index.jte", model);
    }

    private void renderInvalidUrl(Context ctx, String rawUrl) {
        ctx.status(HttpStatus.UNPROCESSABLE_CONTENT);
        renderHomePage(ctx, rawUrl, "danger", "Некорректный URL");
    }

    private Map<String, Object> baseTemplateData(Context ctx, String flashType, String flashMessage) {
        Map<String, Object> model = new HashMap<>();
        FlashMessage resolvedFlash = flashMessage == null
            ? consumeFlash(ctx)
            : new FlashMessage(flashType, flashMessage);
        model.put("flashType", resolvedFlash.type());
        model.put("flashMessage", resolvedFlash.message());
        return model;
    }

    private void setFlash(Context ctx, String type, String message) {
        ctx.sessionAttribute("flashType", type);
        ctx.sessionAttribute("flashMessage", message);
    }

    private FlashMessage consumeFlash(Context ctx) {
        String flashType = ctx.consumeSessionAttribute("flashType");
        String flashMessage = ctx.consumeSessionAttribute("flashMessage");
        return new FlashMessage(flashType, flashMessage);
    }

    private Optional<Url> findRequestedUrl(Context ctx) {
        Optional<Long> maybeUrlId = parseUrlId(ctx);
        if (maybeUrlId.isEmpty()) {
            renderNotFound(ctx);
            return Optional.empty();
        }

        Optional<Url> url = urlRepository.find(maybeUrlId.orElseThrow());
        if (url.isEmpty()) {
            renderNotFound(ctx);
        }
        return url;
    }

    private Optional<Long> parseUrlId(Context ctx) {
        try {
            return Optional.of(Long.parseLong(ctx.pathParam("id")));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private void renderNotFound(Context ctx) {
        ctx.status(HttpStatus.NOT_FOUND);
        ctx.result(NOT_FOUND_MESSAGE);
    }

    private String urlPath(long urlId) {
        return URLS_PATH + "/" + urlId;
    }

    private record FlashMessage(String type, String message) {
    }
}
