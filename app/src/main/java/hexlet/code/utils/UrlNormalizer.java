package hexlet.code.utils;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;

public final class UrlNormalizer {
    private UrlNormalizer() {
    }

    public static String normalize(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new IllegalArgumentException("URL is blank");
        }

        try {
            URI uri = new URI(rawUrl);
            java.net.URL parsedUrl = uri.toURL();
            String protocol = parsedUrl.getProtocol();
            String authority = parsedUrl.getAuthority();

            if (protocol == null || protocol.isBlank() || authority == null || authority.isBlank()) {
                throw new IllegalArgumentException("URL is invalid");
            }

            return protocol + "://" + authority;
        } catch (URISyntaxException | MalformedURLException e) {
            throw new IllegalArgumentException("URL is invalid", e);
        }
    }
}
