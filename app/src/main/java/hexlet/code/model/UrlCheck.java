package hexlet.code.model;

import java.sql.Timestamp;

public record UrlCheck(
    Long id,
    Integer statusCode,
    String title,
    String h1,
    String description,
    Long urlId,
    Timestamp createdAt
) {
    private static final int MAX_TEXT_LENGTH = 200;
    private static final String ELLIPSIS = "....";

    public UrlCheck(Integer statusCode, String title, String h1, String description, Long urlId, Timestamp createdAt) {
        this(null, statusCode, title, h1, description, urlId, createdAt);
    }

    public String titleForDisplay() {
        return truncate(title);
    }

    public String h1ForDisplay() {
        return truncate(h1);
    }

    public String descriptionForDisplay() {
        return truncate(description);
    }

    private String truncate(String value) {
        if (value == null) {
            return "";
        }
        if (value.length() <= MAX_TEXT_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_TEXT_LENGTH - ELLIPSIS.length()) + ELLIPSIS;
    }
}
