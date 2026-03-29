package com.finance.portal.news.infrastructure.external;

import com.finance.portal.news.presentation.dto.NewsItemDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

@Component
public class BloombergHtRssClient {

    private static final Logger log = LoggerFactory.getLogger(BloombergHtRssClient.class);
    private static final String RSS_URL = "https://www.bloomberght.com/rss";

    private final RestTemplate restTemplate;

    public BloombergHtRssClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public List<NewsItemDto> fetchNews() {
        try {
            byte[] bytes = restTemplate.getForObject(RSS_URL, byte[].class);
            if (bytes == null || bytes.length == 0) return List.of();
            String xml = new String(bytes, StandardCharsets.UTF_8);
            return parseRss(xml);
        } catch (Exception e) {
            log.warn("Failed to fetch BloombergHT RSS: {}", e.getMessage());
            return List.of();
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
            String image       = directChildText(item, "image");

            result.add(new NewsItemDto(title, description, link, image, pubDate, "BloombergHT", null));
        }
        return result;
    }

    /** Only looks at direct children of parent to avoid picking up channel-level tags */
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
