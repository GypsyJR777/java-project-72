package hexlet.code.utils;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public final class ViewUtils {
    private static final int MAX_TEXT_LENGTH = 200;
    private static final String ELLIPSIS = "....";
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        .withZone(ZoneOffset.UTC);

    private ViewUtils() {
    }

    public static String truncate(String value) {
        if (value == null) {
            return "";
        }
        if (value.length() <= MAX_TEXT_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_TEXT_LENGTH - ELLIPSIS.length()) + ELLIPSIS;
    }

    public static String formatDateTime(Instant value) {
        return value == null ? "" : DATE_TIME_FORMATTER.format(value);
    }
}
