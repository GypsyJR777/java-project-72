package hexlet.code.service;

import hexlet.code.model.Url;
import hexlet.code.model.UrlCheck;
import java.time.Instant;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

public final class UrlCheckService {
    public UrlCheck perform(Url url) {
        HttpResponse<String> response = Unirest.get(url.name()).asString();
        String body = response.getBody() == null ? "" : response.getBody();
        Document document = Jsoup.parse(body);
        Element h1Element = document.selectFirst("h1");
        Element descriptionElement = document.selectFirst("meta[name=description]");
        String h1 = h1Element == null ? "" : h1Element.text();
        String description = descriptionElement == null ? "" : descriptionElement.attr("content");

        return new UrlCheck(
            response.getStatus(),
            document.title(),
            h1,
            description,
            url.id(),
            Instant.now()
        );
    }
}
