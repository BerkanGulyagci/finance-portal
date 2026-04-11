package com.finance.portal.news.infrastructure.external;

import com.finance.portal.news.presentation.dto.NewsItemDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class BloombergHtRssClient {

    private static final Logger log = LoggerFactory.getLogger(BloombergHtRssClient.class);
    private static final String RSS_URL = "https://www.bloomberght.com/rss";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36";

    // og:image pattern in HTML
    private static final Pattern OG_IMAGE_PATTERN =
            Pattern.compile("<meta[^>]+property=[\"']og:image[\"'][^>]+content=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern OG_IMAGE_PATTERN2 =
            Pattern.compile("<meta[^>]+content=[\"']([^\"']+)[\"'][^>]+property=[\"']og:image[\"']", Pattern.CASE_INSENSITIVE);

    private final RestTemplate restTemplate;

    public BloombergHtRssClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public List<NewsItemDto> fetchNews() {
        try {
            byte[] bytes = restTemplate.getForObject(RSS_URL, byte[].class);
            if (bytes == null || bytes.length == 0) return List.of();
            String xml = new String(bytes, StandardCharsets.UTF_8);
            List<NewsItemDto> items = parseRss(xml);
            enrichWithOgImages(items);
            return items;
        } catch (Exception e) {
            log.warn("Failed to fetch BloombergHT RSS: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * For items without a valid image, fetch og:image from the article page.
     * Only fetches for first 10 items to avoid too many requests.
     */
    private void enrichWithOgImages(List<NewsItemDto> items) {
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (int i = 0; i < items.size(); i++) {
            final NewsItemDto item = items.get(i);
            if (item.getUrl() == null) continue;

            futures.add(CompletableFuture.runAsync(() -> {
                try {
                    String ogImage = fetchOgImage(item.getUrl());
                    if (ogImage != null) {
                        item.setImageUrl(ogImage);
                    }
                } catch (Exception e) {
                    log.debug("Failed to fetch og:image for {}: {}", item.getUrl(), e.getMessage());
                }
            }));
        }

        // Wait for all with timeout
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(30, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
            log.debug("og:image enrichment timed out or failed: {}", e.getMessage());
        }
    }

    private String fetchOgImage(String url) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.USER_AGENT, USER_AGENT);
            headers.set(HttpHeaders.ACCEPT, "text/html");
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            String html = response.getBody();
            if (html == null) return null;

            // Only search first 5KB for og:image (it's in <head>)
            String head = html.length() > 5000 ? html.substring(0, 5000) : html;

            Matcher m = OG_IMAGE_PATTERN.matcher(head);
            if (m.find()) return m.group(1);

            m = OG_IMAGE_PATTERN2.matcher(head);
            if (m.find()) return m.group(1);

            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private List<NewsItemDto> parseRss(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

        NodeList items = doc.getElementsByTagName("item");
        List<NewsItemDto> result = new ArrayList<>();

        for (int i = 0; i < items.getLength(); i++) {
            Element item = (Element) items.item(i);
            String title       = directChildText(item, "title");
            String description = directChildText(item, "description");
            String link        = directChildText(item, "link");
            String pubDate     = directChildText(item, "pubDate");

            result.add(new NewsItemDto(title, description, link, null, pubDate, "BloombergHT", null));
        }
        return result;
    }

    private String directChildText(Element parent, String tag) {
        org.w3c.dom.NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            org.w3c.dom.Node node = children.item(i);
            if (node.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE
                    && tag.equals(node.getNodeName())) {
                String text = node.getTextContent();
                return (text == null || text.isBlank()) ? null : text.trim();
            }
        }
        return null;
    }
}
