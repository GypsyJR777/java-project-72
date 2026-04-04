package hexlet.code.model;

import java.time.Instant;

public record UrlCheck(
    Long id,
    Integer statusCode,
    String title,
    String h1,
    String description,
    Long urlId,
    Instant createdAt
) {
    public UrlCheck(Integer statusCode, String title, String h1, String description, Long urlId, Instant createdAt) {
        this(null, statusCode, title, h1, description, urlId, createdAt);
    }
}
