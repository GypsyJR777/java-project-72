package hexlet.code.service;

import hexlet.code.model.Url;
import hexlet.code.model.UrlCheck;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

public final class UrlCheckService {
    public UrlCheck perform(Url url) {
        HttpResponse<String> response = Unirest.get(url.name()).asString();
        String body = Optional.ofNullable(response.getBody()).orElse("");
        Document document = Jsoup.parse(body);

        return new UrlCheck(
            response.getStatus(),
            document.title(),
            extractFirstText(document, "h1"),
            extractMetaContent(document, "description"),
            url.id(),
            Timestamp.from(Instant.now())
        );
    }

    private String extractFirstText(Document document, String selector) {
        Element element = document.selectFirst(selector);
        return element == null ? "" : element.text();
    }

    private String extractMetaContent(Document document, String metaName) {
        Element element = document.selectFirst("meta[name=" + metaName + "]");
        return element == null ? "" : element.attr("content");
    }
}
