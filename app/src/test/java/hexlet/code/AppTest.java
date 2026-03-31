package hexlet.code;

import com.zaxxer.hikari.HikariDataSource;
import hexlet.code.database.DatabaseConfig;
import hexlet.code.model.Url;
import hexlet.code.model.UrlCheck;
import hexlet.code.repository.UrlCheckRepository;
import hexlet.code.repository.UrlRepository;
import hexlet.code.utils.UrlNormalizer;
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
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class AppTest {
    private static final String JDBC_PROPERTY = "JDBC_DATABASE_URL";
    private static MockWebServer mockWebServer;

    private Javalin app;
    private HttpClient client;
    private String baseUrl;

    @BeforeAll
    static void startMockServer() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
    }

    @AfterAll
    static void stopMockServer() throws IOException {
        mockWebServer.shutdown();
    }

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
        System.clearProperty("PORT");
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
    void createUrlSavesEntityAndRedirectsToShowPage() throws IOException, InterruptedException, SQLException {
        HttpResponse<String> createResponse = postForm("/urls", "url", "https://example.com/path?q=1");

        Assertions.assertEquals(302, createResponse.statusCode());
        Assertions.assertEquals("/urls/1", createResponse.headers().firstValue("Location").orElseThrow());

        Optional<Url> savedUrl = findByName("https://example.com");
        Assertions.assertTrue(savedUrl.isPresent());
        Assertions.assertEquals(1L, savedUrl.get().id());
    }

    @Test
    void showUrlPageDisplaysSavedUrl() throws IOException, InterruptedException, SQLException {
        Url savedUrl = saveUrl("https://example.com", "2026-03-31T00:00:00Z");

        HttpResponse<String> response = get("/urls/" + savedUrl.id());

        Assertions.assertEquals(200, response.statusCode());
        Assertions.assertTrue(response.body().contains("data-test=\"url\""));
        Assertions.assertTrue(response.body().contains("https://example.com"));
        Assertions.assertTrue(response.body().contains("action=\"/urls/" + savedUrl.id() + "/checks\""));
        Assertions.assertTrue(response.body().contains("data-test=\"checks\""));
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
    void createCheckSavesEntityAndRedirectsToUrlPage() throws Exception {
        enqueueHtmlResponse(200, longHtmlPage());
        saveUrl(mockWebServer.url("/page").toString(), "2026-03-31T00:00:00Z");

        HttpResponse<String> checkResponse = postEmpty("/urls/1/checks");

        Assertions.assertEquals(302, checkResponse.statusCode());
        Assertions.assertEquals("/urls/1", checkResponse.headers().firstValue("Location").orElseThrow());

        List<UrlCheck> checks = findChecksByUrlId(1L);
        Assertions.assertEquals(1, checks.size());
        Assertions.assertEquals(200, checks.get(0).statusCode());
    }

    @Test
    void showUrlPageDisplaysSavedChecks() throws IOException, InterruptedException, SQLException {
        Url url = saveUrl("https://example.com", "2026-03-31T00:00:00Z");
        saveUrlCheck(
            new UrlCheck(
                200,
                "Very long title ".repeat(20),
                "Very long h1 ".repeat(25),
                "Very long description ".repeat(20),
                url.id(),
                Timestamp.from(Instant.parse("2026-03-31T01:00:00Z"))
            )
        );

        HttpResponse<String> showResponse = get("/urls/" + url.id());

        Assertions.assertEquals(200, showResponse.statusCode());
        Assertions.assertTrue(showResponse.body().contains("data-test=\"checks\""));
        Assertions.assertTrue(showResponse.body().contains("<td>1</td>"));
        Assertions.assertTrue(showResponse.body().contains("<td>200</td>"));
        Assertions.assertTrue(showResponse.body().contains(expectedTruncated("Very long h1 ".repeat(25))));
        Assertions.assertTrue(showResponse.body().contains(expectedTruncated("Very long title ".repeat(20))));
        Assertions.assertTrue(showResponse.body().contains(expectedTruncated("Very long description ".repeat(20))));
    }

    @Test
    void urlsPageShowsLatestCheckStatusAndDate() throws Exception {
        Url url = saveUrl(mockWebServer.url("/latest").toString(), "2026-03-31T00:00:00Z");
        saveUrlCheck(
            new UrlCheck(
                200,
                "Home",
                "Hello",
                "desc",
                url.id(),
                Timestamp.from(Instant.parse("2026-03-31T01:00:00Z"))
            )
        );

        HttpResponse<String> response = get("/urls");

        Assertions.assertEquals(200, response.statusCode());
        Assertions.assertTrue(response.body().contains("data-test=\"urls\""));
        Assertions.assertTrue(response.body().contains("<th>Код ответа</th>"));
        Assertions.assertTrue(response.body().contains("200"));
        Assertions.assertTrue(response.body().contains("http://localhost:" + mockWebServer.getPort()));
    }

    @Test
    void failedCheckDoesNotCreateRecord() throws Exception {
        enqueueHtmlResponse(500, "<html><body>Error</body></html>");
        saveUrl(mockWebServer.url("/error").toString(), "2026-03-31T00:00:00Z");

        HttpResponse<String> checkResponse = postEmpty("/urls/1/checks");

        Assertions.assertEquals(302, checkResponse.statusCode());
        Assertions.assertEquals("/urls/1", checkResponse.headers().firstValue("Location").orElseThrow());
        Assertions.assertTrue(findChecksByUrlId(1L).isEmpty());
    }

    @Test
    void showUrlPageDisplaysFailedCheckFlashMessage() throws Exception {
        enqueueHtmlResponse(500, "<html><body>Error</body></html>");
        saveUrl(mockWebServer.url("/error").toString(), "2026-03-31T00:00:00Z");
        postEmpty("/urls/1/checks");

        HttpResponse<String> showResponse = get("/urls/1");

        Assertions.assertEquals(200, showResponse.statusCode());
        Assertions.assertTrue(showResponse.body().contains("Произошла ошибка при проверке"));
    }

    @Test
    void missingUrlReturns404() throws IOException, InterruptedException {
        HttpResponse<String> response = get("/urls/999");

        Assertions.assertEquals(404, response.statusCode());
    }

    @Test
    void nonNumericUrlIdReturns404() throws IOException, InterruptedException {
        HttpResponse<String> response = get("/urls/not-a-number");

        Assertions.assertEquals(404, response.statusCode());
    }

    @Test
    void checkMissingUrlReturns404() throws IOException, InterruptedException {
        HttpResponse<String> response = postEmpty("/urls/999/checks");

        Assertions.assertEquals(404, response.statusCode());
    }

    @Test
    void checkNonNumericUrlReturns404() throws IOException, InterruptedException {
        HttpResponse<String> response = postEmpty("/urls/not-a-number/checks");

        Assertions.assertEquals(404, response.statusCode());
    }

    @Test
    void flashMessageIsConsumedAfterFirstRead() throws IOException, InterruptedException {
        postForm("/urls", "url", "https://example.com/path?q=1");

        HttpResponse<String> firstResponse = get("/urls/1");
        HttpResponse<String> secondResponse = get("/urls/1");

        Assertions.assertTrue(firstResponse.body().contains("Страница успешно добавлена"));
        Assertions.assertFalse(secondResponse.body().contains("Страница успешно добавлена"));
    }

    @Test
    void normalizeUrlStripsPathAndKeepsPort() {
        String normalizedUrl = UrlNormalizer.normalize("https://some-domain.org:8080/example/path");

        Assertions.assertEquals("https://some-domain.org:8080", normalizedUrl);
    }

    @Test
    void normalizeUrlRejectsBlankValue() {
        IllegalArgumentException exception = Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> UrlNormalizer.normalize("   ")
        );

        Assertions.assertEquals("URL is blank", exception.getMessage());
    }

    @Test
    void normalizeUrlRejectsMalformedValue() {
        IllegalArgumentException exception = Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> UrlNormalizer.normalize("https://exa mple.com")
        );

        Assertions.assertEquals("URL is invalid", exception.getMessage());
    }

    @Test
    void resolvePortUsesSystemProperty() {
        System.setProperty("PORT", "9090");

        int port = App.resolvePort();

        Assertions.assertEquals(9090, port);
    }

    @Test
    void resolvePortFallsBackToDefaultForInvalidProperty() {
        System.setProperty("PORT", "invalid");

        int port = App.resolvePort();

        Assertions.assertEquals(7070, port);
        System.clearProperty("PORT");
    }

    @Test
    void databaseConfigUsesDefaultJdbcUrlWhenPropertyIsMissing() {
        System.clearProperty(JDBC_PROPERTY);

        try (HikariDataSource dataSource = DatabaseConfig.getDataSource()) {
            Assertions.assertTrue(dataSource.getJdbcUrl().startsWith("jdbc:h2:mem:project"));
        }
    }

    @Test
    void databaseConfigUsesSystemPropertyJdbcUrl() {
        String jdbcUrl = "jdbc:h2:mem:custom_db;DB_CLOSE_DELAY=-1;MODE=PostgreSQL";
        System.setProperty(JDBC_PROPERTY, jdbcUrl);

        try (HikariDataSource dataSource = DatabaseConfig.getDataSource()) {
            Assertions.assertEquals(jdbcUrl, dataSource.getJdbcUrl());
        }
    }

    @Test
    void repositorySaveThrowsWhenGeneratedKeysAreMissing() {
        UrlRepository repository = new UrlRepository(dataSourceWithoutGeneratedKeys());

        Assertions.assertThrows(
            SQLException.class,
            () -> repository.save(new Url("https://example.com", Timestamp.from(Instant.now())))
        );
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

    private HttpResponse<String> postEmpty(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + path))
            .POST(HttpRequest.BodyPublishers.noBody())
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

    private List<UrlCheck> findChecksByUrlId(long urlId) throws SQLException {
        try (HikariDataSource dataSource = DatabaseConfig.getDataSource()) {
            UrlCheckRepository repository = new UrlCheckRepository(dataSource);
            return repository.findByUrlId(urlId);
        }
    }

    private UrlCheck saveUrlCheck(UrlCheck urlCheck) throws SQLException {
        try (HikariDataSource dataSource = DatabaseConfig.getDataSource()) {
            UrlCheckRepository repository = new UrlCheckRepository(dataSource);
            return repository.save(urlCheck);
        }
    }

    private void enqueueHtmlResponse(int statusCode, String body) {
        mockWebServer.enqueue(
            new MockResponse()
                .setResponseCode(statusCode)
                .addHeader("Content-Type", "text/html")
                .setBody(body)
        );
    }

    private String longHtmlPage() {
        String title = "Very long title ".repeat(20);
        String h1 = "Very long h1 ".repeat(25);
        String description = "Very long description ".repeat(20);
        return """
            <html>
              <head>
                <title>%s</title>
                <meta name="description" content="%s">
              </head>
              <body>
                <h1>%s</h1>
              </body>
            </html>
            """.formatted(title, description, h1);
    }

    private String expectedTruncated(String text) {
        return text.substring(0, 196) + "....";
    }

    private DataSource dataSourceWithoutGeneratedKeys() {
        try {
            DataSource dataSource = Mockito.mock(DataSource.class);
            Connection connection = Mockito.mock(Connection.class);
            PreparedStatement statement = Mockito.mock(PreparedStatement.class);
            ResultSet generatedKeys = Mockito.mock(ResultSet.class);

            Mockito.when(dataSource.getConnection()).thenReturn(connection);
            Mockito.when(
                connection.prepareStatement(Mockito.anyString(), Mockito.eq(PreparedStatement.RETURN_GENERATED_KEYS))
            )
                .thenReturn(statement);
            Mockito.when(statement.getGeneratedKeys()).thenReturn(generatedKeys);
            Mockito.when(statement.executeUpdate()).thenReturn(1);
            Mockito.when(generatedKeys.next()).thenReturn(false);

            return dataSource;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to create mock DataSource", e);
        }
    }
}
