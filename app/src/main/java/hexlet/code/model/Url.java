package hexlet.code.model;

import java.sql.Timestamp;

public record Url(Long id, String name, Timestamp createdAt) {
    public Url(String name, Timestamp createdAt) {
        this(null, name, createdAt);
    }
}
