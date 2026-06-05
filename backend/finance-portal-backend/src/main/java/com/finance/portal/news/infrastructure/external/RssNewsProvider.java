package com.finance.portal.news.infrastructure.external;

import com.finance.portal.common.application.logging.CentralIntegrationLogService;
import com.finance.portal.common.application.logging.IntegrationLogSupport;
import com.finance.portal.news.application.model.NewsArticle;
import com.finance.portal.news.application.port.NewsProvider;
import com.finance.portal.news.domain.NewsDateUtil;
import com.finance.portal.news.domain.NewsHtmlUtil;
import com.finance.portal.news.infrastructure.config.NewsSourcesProperties;
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
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Yapılandırılmış (news.sources.rss) tüm RSS feed'lerini çeken tek sağlayıcı.
 * Anahtar gerektirmez; her feed'in görünen adı article.source, varsayılan kategorisi
 * de feed config'inden gelir (aggregator classifier ile daha spesifik kategori atayabilir).
 */
@Component
public class RssNewsProvider implements NewsProvider {

    private static final Logger log = LoggerFactory.getLogger(RssNewsProvider.class);
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36";
    private static final Pattern HTML_TAG = Pattern.compile("<[^>]+>");
    private static final Pattern IMG_SRC =
            Pattern.compile("<img[^>]+src=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
    private static final int MAX_DESC = 400;
    /** content:encoded ham HTML üst sınırı (cache şişmesini önler). */
    private static final int MAX_CONTENT = 20000;

    private final RestTemplate restTemplate;
    private final NewsSourcesProperties properties;
    private final CentralIntegrationLogService integrationLog;

    public RssNewsProvider(RestTemplate restTemplate,
                           NewsSourcesProperties properties,
                           CentralIntegrationLogService integrationLog) {
        this.restTemplate = restTemplate;
        this.properties = properties;
        this.integrationLog = integrationLog;
    }

    @Override
    public String id() {
        return "rss";
    }

    @Override
    public boolean isEnabled() {
        return properties.getRss() != null && !properties.getRss().isEmpty();
    }

    @Override
    public List<NewsArticle> fetch() {
        List<NewsArticle> all = new ArrayList<>();
        if (properties.getRss() == null) {
            return all;
        }
        for (NewsSourcesProperties.RssFeed feed : properties.getRss()) {
            if (feed.getUrl() == null || feed.getUrl().isBlank()) {
                continue;
            }
            try {
                all.addAll(fetchFeed(feed));
            } catch (Exception e) {
                log.warn("RSS feed failed [{}]: {}", feed.getName(), e.getMessage());
                integrationLog.publish(
                        IntegrationLogSupport.EVENT_NEWS_FETCH_FAILED, "WARN",
                        "RSS feed fetch failed: " + feed.getName(),
                        "rss", "rss_fetch", null, null, null, Boolean.TRUE,
                        Map.of("feed", String.valueOf(feed.getName()), "url", String.valueOf(feed.getUrl())),
                        RssNewsProvider.class.getName());
            }
        }
        return all;
    }

    private List<NewsArticle> fetchFeed(NewsSourcesProperties.RssFeed feed) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.USER_AGENT, USER_AGENT);
        headers.set(HttpHeaders.ACCEPT, "application/rss+xml, application/xml, text/xml, */*");
        ResponseEntity<byte[]> resp = restTemplate.exchange(
                feed.getUrl(), HttpMethod.GET, new HttpEntity<>(headers), byte[].class);
        byte[] bytes = resp.getBody();
        if (bytes == null || bytes.length == 0) {
            return List.of();
        }
        return parse(new String(bytes, StandardCharsets.UTF_8), feed);
    }

    private List<NewsArticle> parse(String xml, NewsSourcesProperties.RssFeed feed) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

        // RSS <item> veya Atom <entry>
        NodeList items = doc.getElementsByTagName("item");
        boolean atom = items.getLength() == 0;
        if (atom) {
            items = doc.getElementsByTagName("entry");
        }

        List<NewsArticle> result = new ArrayList<>();
        for (int i = 0; i < items.getLength(); i++) {
            Element item = (Element) items.item(i);
            String title = childText(item, "title");
            // Tam içerik: content:encoded (kriptofoni/ekonomimtv) veya <text> (Hürriyet); description fallback'i de.
            String encoded = firstNonBlank(childText(item, "content:encoded"),
                    childText(item, "encoded"), childText(item, "text"));
            String description = cleanHtml(firstNonBlank(
                    childText(item, "description"), childText(item, "summary"), childText(item, "content"), encoded));
            String link = atom ? atomLink(item) : childText(item, "link");
            String pubDate = firstNonBlank(childText(item, "pubDate"), childText(item, "published"),
                    childText(item, "updated"), childText(item, "dc:date"));
            String image = extractImage(item);
            // enclosure/media görseli yoksa (ör. kriptofoni) içerikteki ilk <img>'ı kullan.
            if (image == null && encoded != null) {
                image = firstImageIn(encoded);
            }

            if (title == null || link == null) {
                continue;
            }
            NewsArticle article = new NewsArticle(
                    title, description, link, image,
                    NewsDateUtil.toIso(pubDate), feed.getName(), null, feed.getCategory(),
                    feed.getLanguage() != null ? feed.getLanguage() : "tr",
                    truncate(encoded));
            result.add(article);
        }
        return result;
    }

    /**
     * media:content / media:thumbnail / enclosure / image>url içinden ilk görseli al.
     * media:* öncelikli — bazı feed'ler (Patronlar Dünyası) enclosure'da sabit jenerik placeholder,
     * gerçek görseli media:content'te veriyor; enclosure-only feed'ler (ekonomimtv) yine de kapsanır.
     */
    private String extractImage(Element item) {
        for (String tag : List.of("media:content", "media:thumbnail", "enclosure")) {
            NodeList nodes = item.getElementsByTagName(tag);
            if (nodes.getLength() > 0) {
                Element el = (Element) nodes.item(0);
                String url = el.getAttribute("url");
                if (url != null && !url.isBlank()) {
                    return url;
                }
            }
        }
        String imageUrl = childText(item, "image");
        return (imageUrl != null && imageUrl.startsWith("http")) ? imageUrl : null;
    }

    /** content:encoded HTML'inden ilk gerçek (http) görseli al. */
    private static String firstImageIn(String html) {
        if (html == null) {
            return null;
        }
        Matcher m = IMG_SRC.matcher(html);
        while (m.find()) {
            String src = m.group(1);
            if (src != null && src.startsWith("http")) {
                return src;
            }
        }
        return null;
    }

    private static String truncate(String s) {
        if (s == null) {
            return null;
        }
        return s.length() > MAX_CONTENT ? s.substring(0, MAX_CONTENT) : s;
    }

    private String atomLink(Element item) {
        NodeList links = item.getElementsByTagName("link");
        for (int i = 0; i < links.getLength(); i++) {
            Element l = (Element) links.item(i);
            String rel = l.getAttribute("rel");
            if (rel == null || rel.isBlank() || "alternate".equals(rel)) {
                String href = l.getAttribute("href");
                if (href != null && !href.isBlank()) {
                    return href;
                }
            }
        }
        return childText(item, "link");
    }

    private String childText(Element parent, String tag) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE && tag.equals(node.getNodeName())) {
                String text = node.getTextContent();
                return (text == null || text.isBlank()) ? null : text.trim();
            }
        }
        return null;
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }

    private static String cleanHtml(String s) {
        if (s == null) {
            return null;
        }
        // HTML etiketlerini sök + TÜM entity'leri çöz. NewsHtmlUtil sayısal entity'leri
        // (&#039; VE &#39;) ve çift-encode'u çözer; eski elle .replace() yalnız &#39;'i
        // yakalıyordu, baştaki sıfırlı &#039; ham kalıp "ABD&#039;li" gibi görünüyordu.
        String text = NewsHtmlUtil.decodeEntities(HTML_TAG.matcher(s).replaceAll(" "))
                .replaceAll("\\s+", " ").trim();
        return text.length() > MAX_DESC ? text.substring(0, MAX_DESC).trim() + "…" : text;
    }
}
