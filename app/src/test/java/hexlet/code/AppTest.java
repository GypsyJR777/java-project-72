package hexlet.code;

import com.zaxxer.hikari.HikariDataSource;
import hexlet.code.database.DatabaseConfig;
import hexlet.code.model.Url;
import hexlet.code.repository.UrlRepository;
import io.javalin.Javalin;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
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

    @Test
    void nonNumericUrlIdReturns404() throws IOException, InterruptedException {
        HttpResponse<String> response = get("/urls/not-a-number");

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
    void normalizeUrlStripsPathAndKeepsPort() throws ReflectiveOperationException {
        String normalizedUrl = (String) invokePrivateStatic(
            "normalizeUrl",
            new Class<?>[] {String.class},
            "https://some-domain.org:8080/example/path"
        );

        Assertions.assertEquals("https://some-domain.org:8080", normalizedUrl);
    }

    @Test
    void normalizeUrlRejectsBlankValue() {
        IllegalArgumentException exception = Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> invokePrivateStatic("normalizeUrl", new Class<?>[] {String.class}, "   ")
        );

        Assertions.assertEquals("URL is blank", exception.getMessage());
    }

    @Test
    void normalizeUrlRejectsMalformedValue() {
        IllegalArgumentException exception = Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> invokePrivateStatic("normalizeUrl", new Class<?>[] {String.class}, "https://exa mple.com")
        );

        Assertions.assertEquals("URL is invalid", exception.getMessage());
    }

    @Test
    void resolvePortFallsBackToDefaultForInvalidProperty() throws ReflectiveOperationException {
        System.setProperty("PORT", "invalid");

        int port = (int) invokePrivateStatic("resolvePort", new Class<?>[0]);

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

    private Object invokePrivateStatic(String methodName, Class<?>[] parameterTypes, Object... args)
        throws ReflectiveOperationException {
        Method method = App.class.getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);

        try {
            return method.invoke(null, args);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw e;
        }
    }

    private DataSource dataSourceWithoutGeneratedKeys() {
        ResultSet resultSet = (ResultSet) Proxy.newProxyInstance(
            ResultSet.class.getClassLoader(),
            new Class<?>[] {ResultSet.class},
            (proxy, method, args) -> {
                if (method.getName().equals("next")) {
                    return false;
                }
                if (method.getName().equals("close")) {
                    return null;
                }
                return defaultValue(method.getReturnType());
            }
        );

        PreparedStatement statement = (PreparedStatement) Proxy.newProxyInstance(
            PreparedStatement.class.getClassLoader(),
            new Class<?>[] {PreparedStatement.class},
            (proxy, method, args) -> {
                if (
                    method.getName().equals("setString")
                        || method.getName().equals("setTimestamp")
                        || method.getName().equals("close")
                ) {
                    return null;
                }
                if (method.getName().equals("executeUpdate")) {
                    return 1;
                }
                if (method.getName().equals("getGeneratedKeys")) {
                    return resultSet;
                }
                return defaultValue(method.getReturnType());
            }
        );

        Connection connection = (Connection) Proxy.newProxyInstance(
            Connection.class.getClassLoader(),
            new Class<?>[] {Connection.class},
            (proxy, method, args) -> {
                if (method.getName().equals("prepareStatement")) {
                    return statement;
                }
                if (method.getName().equals("close")) {
                    return null;
                }
                return defaultValue(method.getReturnType());
            }
        );

        return (DataSource) Proxy.newProxyInstance(
            DataSource.class.getClassLoader(),
            new Class<?>[] {DataSource.class},
            (proxy, method, args) -> {
                if (method.getName().equals("getConnection")) {
                    return connection;
                }
                return defaultValue(method.getReturnType());
            }
        );
    }

    private Object defaultValue(Class<?> returnType) {
        if (!returnType.isPrimitive()) {
            return null;
        }
        if (returnType.equals(boolean.class)) {
            return false;
        }
        if (returnType.equals(byte.class)) {
            return (byte) 0;
        }
        if (returnType.equals(short.class)) {
            return (short) 0;
        }
        if (returnType.equals(int.class)) {
            return 0;
        }
        if (returnType.equals(long.class)) {
            return 0L;
        }
        if (returnType.equals(float.class)) {
            return 0F;
        }
        if (returnType.equals(double.class)) {
            return 0D;
        }
        if (returnType.equals(char.class)) {
            return '\0';
        }
        return null;
    }
}
