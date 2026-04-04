package hexlet.code.model;

import java.time.Instant;

public record Url(Long id, String name, Instant createdAt) {
    public Url(String name, Instant createdAt) {
        this(null, name, createdAt);
    }
}
