package hexlet.code;

import com.zaxxer.hikari.HikariDataSource;
import hexlet.code.database.DatabaseConfig;
import hexlet.code.model.Url;
import hexlet.code.repository.UrlRepository;
import io.javalin.Javalin;
import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AppTest {
    private static final String JDBC_PROPERTY = "JDBC_DATABASE_URL";

    private Javalin app;
    private HttpClient client;
    private String baseUrl;

    @BeforeEach
    void setUp() {
        String jdbcUrl = "jdbc:h2:mem:test_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;MODE=PostgreSQL";
        System.setProperty(JDBC_PROPERTY, jdbcUrl);

        CookieManager cookieManager = new CookieManager();
        cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
        client = HttpClient.newBuilder()
            .cookieHandler(cookieManager)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

        app = App.getApp();
        app.start(0);
        baseUrl = "http://127.0.0.1:" + app.port();
    }

    @AfterEach
    void tearDown() {
        if (app != null) {
            app.stop();
        }
        System.clearProperty(JDBC_PROPERTY);
    }

    @Test
    void rootPageRendersMainForm() throws IOException, InterruptedException {
        HttpResponse<String> response = get("/");

        Assertions.assertEquals(200, response.statusCode());
        Assertions.assertTrue(response.body().contains("name=\"url\""));
        Assertions.assertTrue(response.body().contains("action=\"/urls\""));
    }

    @Test
    void invalidUrlReturnsStatus422() throws IOException, InterruptedException {
        HttpResponse<String> response = postForm("/urls", "url", "not-a-url");

        Assertions.assertEquals(422, response.statusCode());
        Assertions.assertTrue(response.body().contains("Некорректный URL"));
        Assertions.assertTrue(response.body().contains("value=\"not-a-url\""));
    }

    @Test
    void createUrlSavesEntityAndOpensItsPage() throws IOException, InterruptedException, SQLException {
        HttpResponse<String> createResponse = postForm("/urls", "url", "https://example.com/path?q=1");

        Assertions.assertEquals(302, createResponse.statusCode());
        Assertions.assertEquals("/urls/1", createResponse.headers().firstValue("Location").orElseThrow());

        Optional<Url> savedUrl = findByName("https://example.com");
        Assertions.assertTrue(savedUrl.isPresent());
        Assertions.assertEquals(1L, savedUrl.get().id());

        HttpResponse<String> showResponse = get("/urls/1");

        Assertions.assertEquals(200, showResponse.statusCode());
        Assertions.assertTrue(showResponse.body().contains("Страница успешно добавлена"));
        Assertions.assertTrue(showResponse.body().contains("data-test=\"url\""));
        Assertions.assertTrue(showResponse.body().contains("https://example.com"));
        Assertions.assertTrue(showResponse.body().contains("action=\"/urls/1/checks\""));
        Assertions.assertTrue(showResponse.body().contains("data-test=\"checks\""));
    }

    @Test
    void existingUrlDoesNotCreateDuplicateAndRedirectsToExistingPage()
        throws IOException, InterruptedException, SQLException {
        Url existingUrl = saveUrl("https://example.com", "2026-03-31T00:00:00Z");

        HttpResponse<String> createResponse = postForm("/urls", "url", "https://example.com/another/path");

        Assertions.assertEquals(302, createResponse.statusCode());
        Assertions.assertEquals(
            "/urls/" + existingUrl.id(),
            createResponse.headers().firstValue("Location").orElseThrow()
        );
        Assertions.assertEquals(1, findAllUrls().size());

        HttpResponse<String> showResponse = get("/urls/" + existingUrl.id());

        Assertions.assertEquals(200, showResponse.statusCode());
        Assertions.assertTrue(showResponse.body().contains("Страница уже существует"));
    }

    @Test
    void urlsPageShowsNewestUrlsFirst() throws IOException, InterruptedException, SQLException {
        Url olderUrl = saveUrl("https://older.example.com", "2026-03-29T00:00:00Z");
        Url newerUrl = saveUrl("https://newer.example.com", "2026-03-30T00:00:00Z");

        HttpResponse<String> response = get("/urls");

        Assertions.assertEquals(200, response.statusCode());
        Assertions.assertTrue(response.body().contains("data-test=\"urls\""));
        Assertions.assertTrue(response.body().contains("/urls/" + olderUrl.id()));
        Assertions.assertTrue(response.body().contains("/urls/" + newerUrl.id()));
        Assertions.assertTrue(response.body().indexOf(newerUrl.name()) < response.body().indexOf(olderUrl.name()));
    }

    @Test
    void missingUrlReturns404() throws IOException, InterruptedException {
        HttpResponse<String> response = get("/urls/999");

        Assertions.assertEquals(404, response.statusCode());
    }

    private HttpResponse<String> get(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + path))
            .GET()
            .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> postForm(String path, String name, String value)
        throws IOException, InterruptedException {
        String body = encodeFormField(name, value);
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + path))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private String encodeFormField(String name, String value) {
        return URLEncoder.encode(name, StandardCharsets.UTF_8)
            + "="
            + URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private Url saveUrl(String name, String createdAt) throws SQLException {
        try (HikariDataSource dataSource = DatabaseConfig.getDataSource()) {
            UrlRepository repository = new UrlRepository(dataSource);
            return repository.save(new Url(name, Timestamp.from(Instant.parse(createdAt))));
        }
    }

    private Optional<Url> findByName(String name) throws SQLException {
        try (HikariDataSource dataSource = DatabaseConfig.getDataSource()) {
            UrlRepository repository = new UrlRepository(dataSource);
            return repository.findByName(name);
        }
    }

    private List<Url> findAllUrls() throws SQLException {
        try (HikariDataSource dataSource = DatabaseConfig.getDataSource()) {
            UrlRepository repository = new UrlRepository(dataSource);
            return repository.findAll();
        }
    }
}
