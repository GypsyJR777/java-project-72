package hexlet.code;

import com.zaxxer.hikari.HikariDataSource;
import hexlet.code.database.DatabaseConfig;
import hexlet.code.exception.DatabaseException;
import hexlet.code.model.Url;
import hexlet.code.model.UrlCheck;
import hexlet.code.repository.UrlCheckRepository;
import hexlet.code.repository.UrlRepository;
import hexlet.code.utils.UrlNormalizer;
import io.javalin.Javalin;
import io.javalin.http.HttpStatus;
import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class AppTest {
    private static final String JDBC_PROPERTY = "JDBC_DATABASE_URL";
    private static MockWebServer mockWebServer;

    private Javalin app;
    private HikariDataSource dataSource;
    private HttpClient client;
    private String baseUrl;
    private UrlRepository urlRepository;
    private UrlCheckRepository urlCheckRepository;

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

        dataSource = DatabaseConfig.getDataSource();
        urlRepository = new UrlRepository(dataSource);
        urlCheckRepository = new UrlCheckRepository(dataSource);

        app = App.getApp();
        app.start(0);
        baseUrl = "http://127.0.0.1:" + app.port();
    }

    @AfterEach
    void tearDown() {
        if (app != null) {
            app.stop();
        }
        if (dataSource != null) {
            dataSource.close();
        }
        System.clearProperty("PORT");
        System.clearProperty(JDBC_PROPERTY);
    }

    @Test
    void rootPageRendersMainForm() throws IOException, InterruptedException {
        HttpResponse<String> response = get("/");

        Assertions.assertEquals(HttpStatus.OK.getCode(), response.statusCode());
        Assertions.assertTrue(response.body().contains("name=\"url\""));
        Assertions.assertTrue(response.body().contains("action=\"/urls\""));
    }

    @Test
    void invalidUrlReturnsStatus422() throws IOException, InterruptedException {
        HttpResponse<String> response = postForm("/urls", "url", "not-a-url");

        Assertions.assertEquals(HttpStatus.UNPROCESSABLE_CONTENT.getCode(), response.statusCode());
        Assertions.assertTrue(response.body().contains("Некорректный URL"));
        Assertions.assertTrue(response.body().contains("value=\"not-a-url\""));
    }

    @Test
    void createUrlSavesEntityAndRedirectsToShowPage() throws IOException, InterruptedException, DatabaseException {
        HttpResponse<String> createResponse = postForm("/urls", "url", "https://example.com/path?q=1");
        Url savedUrl = urlRepository.findByName("https://example.com").orElse(null);

        Assertions.assertEquals(HttpStatus.FOUND.getCode(), createResponse.statusCode());
        Assertions.assertNotNull(savedUrl);
        Assertions.assertEquals(
            "/urls/" + savedUrl.id(),
            createResponse.headers().firstValue("Location").orElseThrow()
        );
        Assertions.assertEquals("https://example.com", savedUrl.name());
    }

    @Test
    void showUrlPageDisplaysSavedUrl() throws IOException, InterruptedException, DatabaseException {
        Url savedUrl = urlRepository.save(
            new Url("https://example.com", Instant.parse("2026-03-31T00:00:00Z"))
        ).orElseThrow(() -> new DatabaseException("Failed to save URL"));

        HttpResponse<String> response = get("/urls/" + savedUrl.id());

        Assertions.assertEquals(HttpStatus.OK.getCode(), response.statusCode());
        Assertions.assertTrue(response.body().contains("data-test=\"url\""));
        Assertions.assertTrue(response.body().contains("https://example.com"));
        Assertions.assertTrue(response.body().contains("action=\"/urls/" + savedUrl.id() + "/checks\""));
        Assertions.assertTrue(response.body().contains("data-test=\"checks\""));
    }

    @Test
    void existingUrlDoesNotCreateDuplicateAndRedirectsToExistingPage()
        throws IOException, InterruptedException, DatabaseException {
        Url existingUrl = urlRepository.save(
            new Url("https://example.com", Instant.parse("2026-03-31T00:00:00Z"))
        ).orElseThrow(() -> new DatabaseException("Failed to save URL"));

        HttpResponse<String> createResponse = postForm("/urls", "url", "https://example.com/another/path");

        Assertions.assertEquals(HttpStatus.FOUND.getCode(), createResponse.statusCode());
        Assertions.assertEquals(
            "/urls/" + existingUrl.id(),
            createResponse.headers().firstValue("Location").orElseThrow()
        );
        Assertions.assertEquals(1, urlRepository.findAll().size());
    }

    @Test
    void urlsPageShowsNewestUrlsFirst() throws IOException, InterruptedException, DatabaseException {
        Url olderUrl = urlRepository.save(
            new Url("https://older.example.com", Instant.parse("2026-03-29T00:00:00Z"))
        ).orElseThrow(() -> new DatabaseException("Failed to save URL"));
        Url newerUrl = urlRepository.save(
            new Url("https://newer.example.com", Instant.parse("2026-03-30T00:00:00Z"))
        ).orElseThrow(() -> new DatabaseException("Failed to save URL"));

        HttpResponse<String> response = get("/urls");

        Assertions.assertEquals(HttpStatus.OK.getCode(), response.statusCode());
        Assertions.assertTrue(response.body().contains("data-test=\"urls\""));
        Assertions.assertTrue(response.body().contains("/urls/" + olderUrl.id()));
        Assertions.assertTrue(response.body().contains("/urls/" + newerUrl.id()));
        Assertions.assertTrue(response.body().indexOf(newerUrl.name()) < response.body().indexOf(olderUrl.name()));
    }

    @Test
    void createCheckSavesEntityAndRedirectsToUrlPage() throws Exception {
        enqueueHtmlResponse(200, longHtmlPage());
        Url savedUrl = urlRepository.save(
            new Url(mockWebServer.url("/page").toString(), Instant.parse("2026-03-31T00:00:00Z"))
        ).orElseThrow(() -> new DatabaseException("Failed to save URL"));

        HttpResponse<String> checkResponse = postEmpty("/urls/" + savedUrl.id() + "/checks");
        List<UrlCheck> checks = urlCheckRepository.findByUrlId(savedUrl.id());
        UrlCheck savedCheck = checks.getFirst();

        Assertions.assertEquals(HttpStatus.FOUND.getCode(), checkResponse.statusCode());
        Assertions.assertEquals("/urls/" + savedUrl.id(), checkResponse.headers().firstValue("Location").orElseThrow());
        Assertions.assertEquals(1, checks.size());
        Assertions.assertEquals(200, savedCheck.statusCode());
        Assertions.assertEquals("Very long title ".repeat(20).trim(), savedCheck.title());
        Assertions.assertEquals("Very long h1 ".repeat(25).trim(), savedCheck.h1());
        Assertions.assertEquals("Very long description ".repeat(20), savedCheck.description());
    }

    @Test
    void showUrlPageDisplaysSavedChecks() throws IOException, InterruptedException {
        Url url = urlRepository.save(
            new Url("https://example.com", Instant.parse("2026-03-31T00:00:00Z"))
        ).orElseThrow(() -> new DatabaseException("Failed to save URL"));
        urlCheckRepository.save(
            new UrlCheck(
                200,
                "Very long title ".repeat(20),
                "Very long h1 ".repeat(25),
                "Very long description ".repeat(20),
                url.id(),
                Instant.parse("2026-03-31T01:00:00Z")
            )
        ).orElseThrow(() -> new DatabaseException("Failed to save URL check"));

        HttpResponse<String> showResponse = get("/urls/" + url.id());

        Assertions.assertEquals(HttpStatus.OK.getCode(), showResponse.statusCode());
        Assertions.assertTrue(showResponse.body().contains("data-test=\"checks\""));
        Assertions.assertTrue(showResponse.body().contains("<td>1</td>"));
        Assertions.assertTrue(showResponse.body().contains("<td>200</td>"));
        Assertions.assertTrue(showResponse.body().contains(expectedTruncated("Very long h1 ".repeat(25))));
        Assertions.assertTrue(showResponse.body().contains(expectedTruncated("Very long title ".repeat(20))));
        Assertions.assertTrue(showResponse.body().contains(expectedTruncated("Very long description ".repeat(20))));
        Assertions.assertTrue(showResponse.body().contains("2026-03-31 01:00:00"));
    }

    @Test
    void urlsPageShowsLatestCheckStatusAndDate() throws Exception {
        Url url = urlRepository.save(
            new Url(mockWebServer.url("/latest").toString(), Instant.parse("2026-03-31T00:00:00Z"))
        ).orElseThrow(() -> new DatabaseException("Failed to save URL"));
        urlCheckRepository.save(
            new UrlCheck(
                200,
                "Home",
                "Hello",
                "desc",
                url.id(),
                Instant.parse("2026-03-31T01:00:00Z")
            )
        ).orElseThrow(() -> new DatabaseException("Failed to save URL check"));

        HttpResponse<String> response = get("/urls");

        Assertions.assertEquals(HttpStatus.OK.getCode(), response.statusCode());
        Assertions.assertTrue(response.body().contains("data-test=\"urls\""));
        Assertions.assertTrue(response.body().contains("<th>Код ответа</th>"));
        Assertions.assertTrue(response.body().contains("200"));
        Assertions.assertTrue(response.body().contains("http://localhost:" + mockWebServer.getPort()));
        Assertions.assertTrue(response.body().contains("2026-03-31 01:00:00"));
    }

    @Test
    void failedCheckDoesNotCreateRecord() throws Exception {
        enqueueHtmlResponse(500, "<html><body>Error</body></html>");
        Url savedUrl = urlRepository.save(
            new Url(mockWebServer.url("/error").toString(), Instant.parse("2026-03-31T00:00:00Z"))
        ).orElseThrow(() -> new DatabaseException("Failed to save URL"));

        HttpResponse<String> checkResponse = postEmpty("/urls/" + savedUrl.id() + "/checks");

        Assertions.assertEquals(HttpStatus.FOUND.getCode(), checkResponse.statusCode());
        Assertions.assertEquals("/urls/" + savedUrl.id(), checkResponse.headers().firstValue("Location").orElseThrow());
        Assertions.assertTrue(urlCheckRepository.findByUrlId(savedUrl.id()).isEmpty());
    }

    @Test
    void showUrlPageDisplaysFailedCheckFlashMessage() throws Exception {
        enqueueHtmlResponse(500, "<html><body>Error</body></html>");
        Url savedUrl = urlRepository.save(
            new Url(mockWebServer.url("/error").toString(), Instant.parse("2026-03-31T00:00:00Z"))
        ).orElseThrow(() -> new DatabaseException("Failed to save URL"));
        postEmpty("/urls/" + savedUrl.id() + "/checks");

        HttpResponse<String> showResponse = get("/urls/" + savedUrl.id());

        Assertions.assertEquals(HttpStatus.OK.getCode(), showResponse.statusCode());
        Assertions.assertTrue(showResponse.body().contains("Произошла ошибка при проверке"));
    }

    @Test
    void missingUrlReturns404() throws IOException, InterruptedException {
        HttpResponse<String> response = get("/urls/999");

        Assertions.assertEquals(HttpStatus.NOT_FOUND.getCode(), response.statusCode());
    }

    @Test
    void nonNumericUrlIdReturns404() throws IOException, InterruptedException {
        HttpResponse<String> response = get("/urls/not-a-number");

        Assertions.assertEquals(HttpStatus.NOT_FOUND.getCode(), response.statusCode());
    }

    @Test
    void checkMissingUrlReturns404() throws IOException, InterruptedException {
        HttpResponse<String> response = postEmpty("/urls/999/checks");

        Assertions.assertEquals(HttpStatus.NOT_FOUND.getCode(), response.statusCode());
    }

    @Test
    void checkNonNumericUrlReturns404() throws IOException, InterruptedException {
        HttpResponse<String> response = postEmpty("/urls/not-a-number/checks");

        Assertions.assertEquals(HttpStatus.NOT_FOUND.getCode(), response.statusCode());
    }

    @Test
    void flashMessageIsConsumedAfterFirstRead() throws IOException, InterruptedException {
        HttpResponse<String> createResponse = postForm("/urls", "url", "https://example.com/path?q=1");
        String location = createResponse.headers().firstValue("Location").orElseThrow();

        HttpResponse<String> firstResponse = get(location);
        HttpResponse<String> secondResponse = get(location);

        Assertions.assertTrue(firstResponse.body().contains("Страница успешно добавлена"));
        Assertions.assertFalse(secondResponse.body().contains("Страница успешно добавлена"));
    }

    @Test
    void normalizeUrlStripsPathAndKeepsPort() throws Exception {
        String normalizedUrl = UrlNormalizer.normalize(
            new URI("https://some-domain.org:8080/example/path")
        ).orElseThrow();

        Assertions.assertEquals("https://some-domain.org:8080", normalizedUrl);
    }

    @Test
    void normalizeUrlRejectsBlankValue() {
        Assertions.assertTrue(UrlNormalizer.normalize(URI.create("")).isEmpty());
    }

    @Test
    void normalizeUrlRejectsMalformedValue() {
        Assertions.assertThrows(URISyntaxException.class, () -> new URI("https://exa mple.com"));
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

        try (HikariDataSource configDataSource = DatabaseConfig.getDataSource()) {
            Assertions.assertTrue(configDataSource.getJdbcUrl().startsWith("jdbc:h2:mem:project"));
        }
    }

    @Test
    void databaseConfigUsesSystemPropertyJdbcUrl() {
        String jdbcUrl = "jdbc:h2:mem:custom_db;DB_CLOSE_DELAY=-1;MODE=PostgreSQL";
        System.setProperty(JDBC_PROPERTY, jdbcUrl);

        try (HikariDataSource configDataSource = DatabaseConfig.getDataSource()) {
            Assertions.assertEquals(jdbcUrl, configDataSource.getJdbcUrl());
        }
    }

    @Test
    void repositorySaveReturnsEmptyWhenGeneratedKeysAreMissing() throws DatabaseException {
        UrlRepository repository = new UrlRepository(dataSourceWithoutGeneratedKeys());

        Assertions.assertTrue(repository.save(new Url("https://example.com", Instant.now())).isEmpty());
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
            DataSource mockedDataSource = Mockito.mock(DataSource.class);
            Connection connection = Mockito.mock(Connection.class);
            PreparedStatement statement = Mockito.mock(PreparedStatement.class);
            ResultSet generatedKeys = Mockito.mock(ResultSet.class);

            Mockito.when(mockedDataSource.getConnection()).thenReturn(connection);
            Mockito.when(
                connection.prepareStatement(Mockito.anyString(), Mockito.eq(PreparedStatement.RETURN_GENERATED_KEYS))
            )
                .thenReturn(statement);
            Mockito.when(statement.getGeneratedKeys()).thenReturn(generatedKeys);
            Mockito.when(statement.executeUpdate()).thenReturn(1);
            Mockito.when(generatedKeys.next()).thenReturn(false);

            return mockedDataSource;
        } catch (DatabaseException | SQLException e) {
            throw new IllegalStateException("Failed to create mock DataSource", e);
        }
    }
}
