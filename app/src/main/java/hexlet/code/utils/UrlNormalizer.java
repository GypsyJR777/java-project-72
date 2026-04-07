package hexlet.code.utils;

import java.net.URI;
import java.util.Optional;

public final class UrlNormalizer {
    private static final int MAX_PORT = 65535;

    private UrlNormalizer() {
    }

    public static Optional<String> normalize(URI uri) {
        String protocol = uri.getScheme();
        String host = uri.getHost();
        int port = uri.getPort();

        if (!isSupportedScheme(protocol) || host == null || host.isBlank() || port > MAX_PORT) {
            return Optional.empty();
        }

        String normalizedUrl = String.format(
            "%s://%s%s",
            protocol,
            host,
            port == -1 ? "" : ":" + port
        ).toLowerCase();

        return Optional.of(normalizedUrl);
    }

    private static boolean isSupportedScheme(String protocol) {
        return "http".equalsIgnoreCase(protocol) || "https".equalsIgnoreCase(protocol);
    }
}
