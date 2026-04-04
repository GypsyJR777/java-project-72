package hexlet.code.utils;

import java.net.URI;

public final class UrlNormalizer {
    private static final int MAX_PORT = 65535;

    private UrlNormalizer() {
    }

    public static String normalize(URI uri) {
        String protocol = uri.getScheme();
        String host = uri.getHost();
        int port = uri.getPort();
        String authority = uri.getRawAuthority();

        if (
                !isSupportedScheme(protocol) || host == null || host.isBlank()
                        || port > MAX_PORT || authority == null || authority.isBlank()
        ) {
            throw new IllegalArgumentException("URL is invalid");
        }

        return port == -1
                ? protocol + "://" + host
                : protocol + "://" + host + ":" + port;
    }

    private static boolean isSupportedScheme(String protocol) {
        return "http".equalsIgnoreCase(protocol) || "https".equalsIgnoreCase(protocol);
    }
}
