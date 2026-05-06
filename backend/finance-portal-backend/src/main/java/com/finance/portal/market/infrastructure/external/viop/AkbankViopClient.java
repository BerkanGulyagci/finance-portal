package com.finance.portal.market.infrastructure.external.viop;

import com.finance.portal.market.application.viop.ViopContract;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class AkbankViopClient {

    private static final Logger log = LoggerFactory.getLogger(AkbankViopClient.class);
    private static final String URL = "https://yatirim.akbank.com/tr-tr/viop/Sayfalar/tum-viop-sozlesmeleri.aspx";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36";

    /** HTML charset meta tag pattern */
    private static final Pattern CHARSET_PATTERN = Pattern.compile(
            "charset=[\"']?([\\w-]+)[\"']?", Pattern.CASE_INSENSITIVE);

    private final RestTemplate restTemplate;

    public AkbankViopClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public List<ViopContract> fetchContracts() {
        try {
            log.info("Fetching VIOP contracts from Akbank...");
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.USER_AGENT, USER_AGENT);
            headers.set(HttpHeaders.ACCEPT, "text/html,application/xhtml+xml");
            headers.set(HttpHeaders.ACCEPT_LANGUAGE, "tr-TR,tr;q=0.9");
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            // byte[] olarak al — charset sorununu önlemek için
            ResponseEntity<byte[]> response = restTemplate.exchange(URL, HttpMethod.GET, entity, byte[].class);
            byte[] bodyBytes = response.getBody();

            if (bodyBytes == null) {
                log.warn("Akbank returned null response");
                return List.of();
            }

            // Charset'i tespit et: önce Content-Type header'ından, sonra HTML meta'dan
            Charset charset = detectCharset(response.getHeaders().getContentType(), bodyBytes);
            String html = new String(bodyBytes, charset);

            log.info("Received HTML response, length: {} chars, charset: {}", html.length(), charset);
            List<ViopContract> contracts = parseHtml(html);
            log.info("Successfully parsed {} VIOP contracts", contracts.size());

            return contracts;
        } catch (Exception e) {
            log.error("Failed to fetch VIOP contracts: {}", e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * Charset tespiti:
     * 1. UTF-8 BOM kontrolü
     * 2. UTF-8 olarak decode dene — Türkçe karakterler geçerliyse UTF-8 kullan
     * 3. Content-Type header'ından
     * 4. HTML meta charset tag'inden
     * 5. Fallback: windows-1252
     */
    private Charset detectCharset(org.springframework.http.MediaType contentType, byte[] bodyBytes) {
        // 1. UTF-8 BOM kontrolü
        if (bodyBytes.length >= 3
                && (bodyBytes[0] & 0xFF) == 0xEF
                && (bodyBytes[1] & 0xFF) == 0xBB
                && (bodyBytes[2] & 0xFF) == 0xBF) {
            log.debug("Charset: UTF-8 BOM detected");
            return StandardCharsets.UTF_8;
        }

        // 2. UTF-8 olarak decode dene — replacement char (\uFFFD) yoksa UTF-8'dir
        try {
            java.nio.charset.CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                    .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT);
            decoder.decode(java.nio.ByteBuffer.wrap(bodyBytes));
            log.debug("Charset: UTF-8 (validated by decoder)");
            return StandardCharsets.UTF_8;
        } catch (Exception e) {
            log.debug("UTF-8 decode failed, trying other charsets: {}", e.getMessage());
        }

        // 3. Content-Type header
        if (contentType != null && contentType.getCharset() != null) {
            log.debug("Charset from Content-Type header: {}", contentType.getCharset());
            return contentType.getCharset();
        }

        // 4. HTML meta tag — ilk 2KB'a bak
        String head = new String(bodyBytes, 0, Math.min(2048, bodyBytes.length), StandardCharsets.ISO_8859_1);
        Matcher m = CHARSET_PATTERN.matcher(head);
        if (m.find()) {
            String charsetName = m.group(1).trim();
            try {
                Charset detected = Charset.forName(charsetName);
                log.debug("Charset from HTML meta: {}", detected);
                return detected;
            } catch (Exception e) {
                log.warn("Unknown charset in HTML meta: {}", charsetName);
            }
        }

        // 5. Fallback: windows-1252 (Türkçe web sayfaları için yaygın)
        log.debug("Charset fallback: windows-1252");
        return Charset.forName("windows-1252");
    }

    private List<ViopContract> parseHtml(String html) {
        List<ViopContract> result = new ArrayList<>();

        // HTML entity'leri decode et — Türkçe karakterler entity olarak gelebilir
        // Örn: &Auml; &Ouml; &#287; (ğ) &#351; (ş) &#252; (ü) &#246; (ö) &#231; (ç) &#305; (ı) &#304; (İ)
        String decoded = decodeHtmlEntities(html);

        // Strip all HTML tags and split into non-empty tokens
        String[] lines = decoded.replaceAll("<[^>]+>", "\n").split("\n");
        List<String> tokens = new ArrayList<>();
        for (String line : lines) {
            String t = line.trim();
            if (!t.isEmpty()) tokens.add(t);
        }

        // Each contract block = 10 consecutive tokens:
        // [0] name (contains "Vadeli")
        // [1] changePercent (starts with %)
        // [2] lastPrice (numeric)
        // [3] high (numeric)
        // [4] low (numeric)
        // [5] openPositionCount (numeric)
        // [6] openPositionChange (numeric, may be negative)
        // [7] settlementPrice (numeric)
        // [8] prevSettlementPrice (numeric)
        // [9] time (HH:mm:ss)

        Pattern timePattern = Pattern.compile("^\\d{2}:\\d{2}:\\d{2}$");
        Pattern numericPattern = Pattern.compile("^-?[\\d.,]+$");
        Pattern pctPattern = Pattern.compile("^%[+\\-]?[\\d.,]+$");

        for (int i = 0; i < tokens.size() - 9; i++) {
            String t0 = tokens.get(i);
            // Contract name must contain "Vadeli"
            if (!t0.contains("Vadeli")) continue;

            String t1 = tokens.get(i + 1);
            String t9 = tokens.get(i + 9);

            // Validate: t1 should be a percent, t9 should be a time
            if (!pctPattern.matcher(t1).matches()) continue;
            if (!timePattern.matcher(t9).matches()) continue;

            ViopContract c = new ViopContract();
            // TurkishCharFixer'ı whitespace normalize'dan ÖNCE uygula
            // Akbank HTML'de Türkçe karakterler satır sonlarıyla bölünmüş olabilir
            String fixedName = TurkishCharFixer.fix(t0);
            c.setName(fixedName.replaceAll("\\s+", " ").trim());            c.setChangePercent(t1);
            c.setLastPrice(tokens.get(i + 2));
            c.setHigh(tokens.get(i + 3));
            c.setLow(tokens.get(i + 4));
            c.setOpenPositionCount(tokens.get(i + 5));
            c.setOpenPositionChange(tokens.get(i + 6));
            c.setSettlementPrice(tokens.get(i + 7));
            c.setPrevSettlementPrice(tokens.get(i + 8));
            c.setTime(t9);
            result.add(c);
            i += 9; // skip to next block
        }

        log.info("Parsed {} VIOP contracts", result.size());
        return result;
    }

    /**
     * Türkçe karakterlerin HTML entity karşılıklarını decode eder.
     * Akbank HTML'de Türkçe karakterler numeric veya named entity olarak gelebilir.
     */
    private String decodeHtmlEntities(String html) {
        if (html == null) return "";
        return html
                // Numeric entities — Türkçe karakterler
                .replace("&#287;", "ğ").replace("&#286;", "Ğ")
                .replace("&#351;", "ş").replace("&#350;", "Ş")
                .replace("&#252;", "ü").replace("&#220;", "Ü")
                .replace("&#246;", "ö").replace("&#214;", "Ö")
                .replace("&#231;", "ç").replace("&#199;", "Ç")
                .replace("&#305;", "ı").replace("&#304;", "İ")
                // Named entities
                .replace("&gbreve;", "ğ").replace("&Gbreve;", "Ğ")
                .replace("&scedil;", "ş").replace("&Scedil;", "Ş")
                .replace("&uuml;", "ü").replace("&Uuml;", "Ü")
                .replace("&ouml;", "ö").replace("&Ouml;", "Ö")
                .replace("&ccedil;", "ç").replace("&Ccedil;", "Ç")
                // Common HTML entities
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&nbsp;", " ")
                .replace("&quot;", "\"");
    }
}
