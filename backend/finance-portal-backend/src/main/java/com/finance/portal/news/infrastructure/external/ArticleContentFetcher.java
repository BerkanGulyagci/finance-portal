package com.finance.portal.news.infrastructure.external;

import com.finance.portal.news.application.port.ArticleContentPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Haber detayında "tam içeriği" en iyi çaba ile çeker: makale sayfasını indirip
 * ana metni (paragrafları) çıkarır. Telif/erişim nedeniyle başarısız olursa null döner
 * (frontend özete düşer). Ayrıca eksik görseller için og:image çıkarır. Sonuçlar LRU cache'lenir.
 */
@Component
public class ArticleContentFetcher implements ArticleContentPort {

    private static final Logger log = LoggerFactory.getLogger(ArticleContentFetcher.class);
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36";
    private static final int MAX_CONTENT = 14000;
    private static final int MIN_CONTENT = 250;

    private static final Pattern SCRIPT_STYLE =
            Pattern.compile("<(script|style|noscript)[^>]*>.*?</\\1>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    // Boilerplate kapsayıcılar tamamen atılır (nav menü, header dil değiştirici, footer, sidebar, form).
    private static final Pattern BOILERPLATE =
            Pattern.compile("<(nav|header|footer|aside|form)[^>]*>.*?</\\1>",
                    Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern P_TAG =
            Pattern.compile("<p[^>]*>(.*?)</p>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern HTML_TAG = Pattern.compile("<[^>]+>");
    private static final Pattern OG_IMAGE_1 =
            Pattern.compile("<meta[^>]+property=[\"']og:image[\"'][^>]+content=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern OG_IMAGE_2 =
            Pattern.compile("<meta[^>]+content=[\"']([^\"']+)[\"'][^>]+property=[\"']og:image[\"']", Pattern.CASE_INSENSITIVE);

    private final RestTemplate restTemplate;
    private final Map<String, String> contentCache = lru(300);
    private final Map<String, String> imageCache = lru(500);

    public ArticleContentFetcher(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /** Makale tam metni (paragraflar) ya da çıkarılamazsa null. */
    public String fetchContent(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        if (contentCache.containsKey(url)) {
            return contentCache.get(url);
        }
        String content = null;
        try {
            String html = fetchHtml(url);
            if (html != null) {
                content = extractContent(html);
            }
        } catch (Exception e) {
            log.debug("Article content fetch failed for {}: {}", url, e.getMessage());
        }
        contentCache.put(url, content); // null da cache'lenir (tekrar denememek için)
        return content;
    }

    /** Sayfanın og:image'i ya da null. */
    public String fetchOgImage(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        if (imageCache.containsKey(url)) {
            return imageCache.get(url);
        }
        String image = null;
        try {
            String html = fetchHtml(url);
            if (html != null) {
                String head = html.length() > 8000 ? html.substring(0, 8000) : html;
                Matcher m = OG_IMAGE_1.matcher(head);
                if (m.find()) {
                    image = m.group(1);
                } else {
                    m = OG_IMAGE_2.matcher(head);
                    if (m.find()) {
                        image = m.group(1);
                    }
                }
            }
        } catch (Exception e) {
            log.debug("og:image fetch failed for {}: {}", url, e.getMessage());
        }
        imageCache.put(url, image);
        return image;
    }

    private static final Pattern CHARSET_META =
            Pattern.compile("charset\\s*=\\s*[\"']?\\s*([a-zA-Z0-9_\\-]+)", Pattern.CASE_INSENSITIVE);

    private String fetchHtml(String url) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.USER_AGENT, USER_AGENT);
        headers.set(HttpHeaders.ACCEPT, "text/html,application/xhtml+xml");
        ResponseEntity<byte[]> resp =
                restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), byte[].class);
        byte[] body = resp.getBody();
        if (body == null || body.length == 0) {
            return null;
        }
        // Charset'i ham String.class'a bırakma (ISO-8859-1 varsayıp Türkçe'yi bozuyor) — byte'tan doğru çöz.
        return new String(body, detectCharset(resp.getHeaders(), body));
    }

    private static Charset detectCharset(HttpHeaders headers, byte[] body) {
        // 1) HTTP Content-Type header charset'i
        try {
            MediaType ct = headers.getContentType();
            if (ct != null && ct.getCharset() != null) {
                return ct.getCharset();
            }
        } catch (Exception ignored) {
        }
        // 2) HTML <meta charset=...> (ilk 4KB'ı latin1 olarak tara)
        String head = new String(body, 0, Math.min(body.length, 4096), StandardCharsets.ISO_8859_1);
        Matcher m = CHARSET_META.matcher(head);
        if (m.find()) {
            try {
                return Charset.forName(m.group(1).trim());
            } catch (Exception ignored) {
            }
        }
        // 3) Türk haber siteleri için makul varsayılan
        return StandardCharsets.UTF_8;
    }

    private String extractContent(String html) {
        String cleaned = SCRIPT_STYLE.matcher(html).replaceAll(" ");
        cleaned = BOILERPLATE.matcher(cleaned).replaceAll(" ");
        // Sayfada birden çok makale/widget paragrafı olabilir (AA "EN SON" widget'ı, Investing teaser'ları).
        // Ana gövde = birbirine en yakın (yoğun) ve toplam en uzun ardışık paragraf kümesi (readability mantığı).
        return densestParagraphCluster(cleaned);
    }

    private static final int CLUSTER_GAP = 2000; // ardışık paragraflar arası izin verilen max HTML mesafesi (karakter)

    private String densestParagraphCluster(String html) {
        Matcher p = P_TAG.matcher(html);
        List<String> best = new ArrayList<>();
        int bestLen = 0;
        List<String> current = new ArrayList<>();
        int currentLen = 0;
        int prevEnd = -1;
        while (p.find()) {
            String text = stripTags(p.group(1));
            if (text.length() < 50 || looksLikeJunk(text)) {
                continue;
            }
            if (prevEnd >= 0 && p.start() - prevEnd > CLUSTER_GAP) {
                if (currentLen > bestLen) {
                    bestLen = currentLen;
                    best = current;
                }
                current = new ArrayList<>();
                currentLen = 0;
            }
            current.add(text);
            currentLen += text.length();
            prevEnd = p.end();
        }
        if (currentLen > bestLen) {
            best = current;
        }
        if (best.isEmpty()) {
            return null;
        }
        String joined = String.join("\n\n", best).trim();
        if (joined.length() < MIN_CONTENT) {
            return null;
        }
        return joined.length() > MAX_CONTENT ? joined.substring(0, MAX_CONTENT).trim() + "…" : joined;
    }

    /**
     * RSS {@code <content:encoded>} ham HTML'inden ana metni çıkarır. Makale gövdesi zaten
     * verildiği için {@code <article>} kapsamı aranmaz; paragraf bulunamazsa tüm metne düşer.
     */
    @Override
    public String extractFromHtml(String html) {
        if (html == null || html.isBlank()) {
            return null;
        }
        String cleaned = SCRIPT_STYLE.matcher(html).replaceAll(" ");
        String joined = paragraphsFrom(cleaned, 40);
        if (joined != null) {
            return joined;
        }
        // <p> yoksa (farklı işaretleme) tüm etiketleri sıyır
        String flat = stripTags(cleaned);
        if (flat == null || flat.length() < MIN_CONTENT) {
            return null;
        }
        return flat.length() > MAX_CONTENT ? flat.substring(0, MAX_CONTENT).trim() + "…" : flat;
    }

    /** Verilen HTML kapsamındaki <p> paragraflarını çıkarıp \n\n ile birleştirir (junk elenir). */
    private String paragraphsFrom(String scope, int minParagraphLen) {
        List<String> paragraphs = new ArrayList<>();
        Matcher p = P_TAG.matcher(scope);
        while (p.find()) {
            String text = stripTags(p.group(1));
            if (text.length() >= minParagraphLen && !looksLikeJunk(text)) {
                paragraphs.add(text);
            }
        }
        if (paragraphs.isEmpty()) {
            return null;
        }
        String joined = String.join("\n\n", paragraphs).trim();
        if (joined.length() < MIN_CONTENT) {
            return null;
        }
        return joined.length() > MAX_CONTENT ? joined.substring(0, MAX_CONTENT).trim() + "…" : joined;
    }

    private static final Pattern DOMAIN_TOKEN =
            Pattern.compile("[a-z0-9-]+\\.(com|net|org|tr|io)\\b", Pattern.CASE_INSENSITIVE);
    private static final String[] JUNK_KEYWORDS = {
            "function(", "cookie", "çerez", "abone ol", "tüm hakları", "künye", "i̇letişim", "iletişim",
            "reklam", "bize ulaşın", "insan kaynakları", "en çok aranan", "izleyici temsilci",
            "gizlilik", "kullanım koşul", "telif", "bizi takip", "uygulamamızı indir",
            "yorum yaz", "ilgili haberler", "haberin devamı", "©",
            "güncelleme tarihi", "güncelleme :", "son dakika haberleri",
    };

    // Kaynak sitelerin (ör. NTV) makale başına koyduğu "başlık tekrarı" boilerplate'i:
    // "Canlı İzle Son Dakika <başlık> <gg.aa.yyyy ss:dd>" — başında bu kalıp + içinde tarih-saat.
    // Gerçek haber paragrafı böyle başlayıp tarih-saatle bitmez → güvenle elenir.
    private static final Pattern DATE_TIME = Pattern.compile("\\b\\d{1,2}\\.\\d{1,2}\\.\\d{4}\\s+\\d{1,2}:\\d{2}\\b");

    private static boolean looksLikeJunk(String text) {
        String low = text.toLowerCase(java.util.Locale.forLanguageTag("tr"));
        for (String kw : JUNK_KEYWORDS) {
            if (low.contains(kw)) {
                return true;
            }
        }
        // Başlık-tekrar boilerplate: "canlı izle"/"son dakika" ile başlayan + tarih-saat içeren paragraf
        String trimmed = low.stripLeading();
        if ((trimmed.startsWith("canlı izle") || trimmed.startsWith("son dakika"))
                && DATE_TIME.matcher(text).find()) {
            return true;
        }
        // Birden çok yazı sistemi (Latin + Kiril + Arap) = dil değiştirici menü (ör. AA "EDITION ... Русский العربية")
        if (hasForeignScriptMix(text)) {
            return true;
        }
        // Çok sayıda alan adı (ntv.com.tr ntvspor.net …) içeren satır = footer/menü link listesi
        Matcher m = DOMAIN_TOKEN.matcher(low);
        int domains = 0;
        while (m.find()) {
            if (++domains >= 2) {
                return true;
            }
        }
        return false;
    }

    /** Aynı metinde hem Kiril hem Arap harfi geçiyorsa neredeyse kesin dil-değiştirici/menü. */
    private static boolean hasForeignScriptMix(String text) {
        boolean cyrillic = false;
        boolean arabic = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= 0x0400 && c <= 0x04FF) {
                cyrillic = true;
            } else if (c >= 0x0600 && c <= 0x06FF) {
                arabic = true;
            }
            if (cyrillic && arabic) {
                return true;
            }
        }
        return false;
    }

    private static final Pattern NUM_ENTITY = Pattern.compile("&#(x?[0-9a-fA-F]+);");
    private static final Pattern NAMED_ENTITY = Pattern.compile("&([a-zA-Z]+);");
    private static final Map<String, String> NAMED = Map.ofEntries(
            Map.entry("nbsp", " "), Map.entry("amp", "&"), Map.entry("lt", "<"), Map.entry("gt", ">"),
            Map.entry("quot", "\""), Map.entry("apos", "'"), Map.entry("rsquo", "'"), Map.entry("lsquo", "'"),
            Map.entry("ldquo", "\""), Map.entry("rdquo", "\""), Map.entry("ndash", "–"), Map.entry("mdash", "—"),
            Map.entry("hellip", "…"), Map.entry("shy", ""), Map.entry("uuml", "ü"), Map.entry("Uuml", "Ü"), Map.entry("ouml", "ö"),
            Map.entry("Ouml", "Ö"), Map.entry("ccedil", "ç"), Map.entry("Ccedil", "Ç"), Map.entry("szlig", "ß"),
            Map.entry("eacute", "é"), Map.entry("aacute", "á"), Map.entry("agrave", "à"), Map.entry("acirc", "â"));

    private static String stripTags(String s) {
        if (s == null) {
            return "";
        }
        String noTags = HTML_TAG.matcher(s).replaceAll(" ");
        return decodeEntities(noTags).replace("­", "").replaceAll("\\s+", " ").trim();
    }

    /** HTML varlıklarını çözer: sayısal (&#304; / &#x131;) + yaygın isimli (Türkçe ü/ö/ç dahil). */
    private static String decodeEntities(String s) {
        if (s == null) {
            return "";
        }
        if (s.indexOf('&') < 0) {
            return s;
        }
        // Bazı kaynaklar (Patronlar Dünyası) çift-encode'lu: "&amp;ccedil;" → tek geçişte "&ccedil;" kalır.
        // Değişiklik durana dek (en çok 3 geçiş) tekrarla.
        String out = s;
        for (int pass = 0; pass < 3 && out.indexOf('&') >= 0; pass++) {
            String before = out;
            out = decodeOnce(out);
            if (out.equals(before)) {
                break;
            }
        }
        return out;
    }

    private static String decodeOnce(String s) {
        String out = NUM_ENTITY.matcher(s).replaceAll(mr -> {
            String g = mr.group(1);
            try {
                int cp = (g.charAt(0) == 'x' || g.charAt(0) == 'X')
                        ? Integer.parseInt(g.substring(1), 16) : Integer.parseInt(g);
                return Matcher.quoteReplacement(new String(Character.toChars(cp)));
            } catch (Exception e) {
                return Matcher.quoteReplacement(mr.group(0));
            }
        });
        out = NAMED_ENTITY.matcher(out).replaceAll(mr -> {
            String val = NAMED.get(mr.group(1));
            return Matcher.quoteReplacement(val != null ? val : mr.group(0));
        });
        return out;
    }

    private static <K, V> Map<K, V> lru(int max) {
        return Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > max;
            }
        });
    }
}
