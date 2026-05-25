package com.finance.portal.market.infrastructure.external.bond.businessinsider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.portal.common.application.logging.CentralIntegrationLogService;
import com.finance.portal.common.application.logging.IntegrationLogSupport;
import com.finance.portal.market.application.bond.eurobond.model.EurobondChartPoint;
import com.finance.portal.market.application.bond.eurobond.model.EurobondDetail;
import com.finance.portal.market.application.bond.eurobond.model.EurobondRef;
import com.finance.portal.market.application.bond.eurobond.port.BusinessInsiderBondPort;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Business Insider tahvil verisi kazıyıcısı. Akış: suggest(ISIN) → /bonds/{slug} (Jsoup) → Chart_GetChartData.
 *
 * <p><b>Ban-koruması:</b> tüm BI istekleri global olarak en az {@code MIN_INTERVAL_MS} aralıkla
 * seri yapılır (havuz kaç thread olursa olsun); 403/429 alınırsa {@code DEGRADE_MS} boyunca BI'ye
 * hiç gidilmez (RATE_LIMITED entegrasyon logu) → liste HMB'ye düşer. Sonuçlar servis katmanında
 * cache'lendiği için normalde çok az istek atılır.</p>
 */
@Component
public class BusinessInsiderBondClient implements BusinessInsiderBondPort {

    private static final Logger log = LoggerFactory.getLogger(BusinessInsiderBondClient.class);
    private static final String BASE = "https://markets.businessinsider.com";
    private static final String UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";
    private static final DateTimeFormatter YMD = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final long MIN_INTERVAL_MS = 220;     // istekler arası min boşluk (≈4-5 req/s)
    private static final long DEGRADE_MS = 90_000;       // 403/429 sonrası BI'ye gitmeme süresi

    private static final Pattern SUGGEST = Pattern.compile("\"([^\"|]+)\\|([A-Z0-9]{12})\\|([^\"|]*)\\|([^\"|]*)\\|(\\d+)\"");
    private static final Pattern TK_DATA = Pattern.compile("\"tkData\"\\s*:\\s*\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern INSTR_ID = Pattern.compile("\"InstrumentID\"\\s*:\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern PRICE = Pattern.compile("price-section__current-value\"\\s*>\\s*([0-9.,]+)");
    private static final Pattern CHANGE_PCT = Pattern.compile("price-section__relative-value\"\\s*>\\s*\\(?\\s*([+\\-]?[0-9.,]+)\\s*%");

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final CentralIntegrationLogService integrationLog;

    private final Object gate = new Object();
    private volatile long lastReqAt = 0;
    private volatile long degradedUntil = 0;

    public BusinessInsiderBondClient(RestTemplate restTemplate, ObjectMapper objectMapper,
                                     CentralIntegrationLogService integrationLog) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.integrationLog = integrationLog;
    }

    @Override
    public Optional<EurobondRef> resolve(String isin) {
        if (isin == null || isin.isBlank()) {
            return Optional.empty();
        }
        String q = isin.trim().toUpperCase();
        String url = BASE + "/ajax/SearchController_Suggest?max_results=10&query=" + UriUtils.encode(q, StandardCharsets.UTF_8);
        String body = get(url, "suggest");
        if (body == null) {
            return Optional.empty();
        }
        Matcher m = SUGGEST.matcher(body);
        while (m.find()) {
            if (m.group(2).equalsIgnoreCase(q)) {
                return Optional.of(new EurobondRef(
                        m.group(2).toUpperCase(),
                        m.group(1).toLowerCase(),
                        m.group(4).trim(),
                        m.group(3).trim(),
                        Long.parseLong(m.group(5))));
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<EurobondDetail> fetchDetail(EurobondRef ref) {
        if (ref == null || ref.slug() == null) {
            return Optional.empty();
        }
        String url = BASE + "/bonds/" + ref.slug();
        String html = get(url, "detail");
        if (html == null || html.isBlank()) {
            return Optional.empty();
        }
        EurobondDetail d = new EurobondDetail();
        d.setIsin(ref.isin());
        d.setName(ref.name());
        d.setIssuer(ref.issuer());
        d.setInstrumentId(ref.instrumentId());
        d.setDetailUrl(url);

        Document doc = Jsoup.parse(html);
        for (Element tr : doc.select("tr")) {
            Elements tds = tr.children().select("td");
            if (tds.size() < 2) {
                continue;
            }
            String label = tds.get(0).text().replaceAll("\\s+", " ").trim();
            String value = tds.get(1).text().trim();
            if (!label.isEmpty() && !value.isEmpty()) {
                assignField(d, label, value);
            }
        }

        Matcher pm = PRICE.matcher(html);
        if (pm.find()) {
            d.setLastPrice(parseNum(pm.group(1)));
        }
        Matcher cm = CHANGE_PCT.matcher(html);
        if (cm.find()) {
            d.setChangePercent(parseNum(cm.group(1)));
        }
        Matcher tk = TK_DATA.matcher(html);
        if (tk.find()) {
            d.setTkData(tk.group(1));
        }
        if (d.getInstrumentId() <= 0) {
            Matcher im = INSTR_ID.matcher(html);
            if (im.find()) {
                d.setInstrumentId(Long.parseLong(im.group(1)));
            }
        }
        return Optional.of(d);
    }

    private static void assignField(EurobondDetail d, String label, String value) {
        if (label.equalsIgnoreCase("ISIN")) { if (d.getIsin() == null) d.setIsin(value); return; }
        if (label.equalsIgnoreCase("Name")) { d.setName(value); return; }
        if (label.contains("Country")) { d.setCountry(value); return; }
        if (label.contains("Issuer")) { d.setIssuer(value); return; }
        if (label.contains("Issue Volume")) { d.setIssueVolume(value); return; }
        if (label.equalsIgnoreCase("Currency")) { d.setCurrency(value); return; }
        if (label.contains("Issue Price")) { d.setIssuePrice(value); return; }
        if (label.contains("Issue Date")) { d.setIssueDate(value); return; }
        if (label.contains("Denomination")) { d.setDenomination(value); return; }
        if (label.contains("Payment Type")) { d.setPaymentType(value); return; }
        if (label.contains("Maturity")) { d.setMaturityDate(value); return; }
        if (label.contains("Coupon Payment")) { d.setCouponPaymentDate(value); return; }
        if (label.contains("Payments per Year")) { d.setPaymentsPerYear(value); return; }
        if (label.contains("Coupon Start")) { d.setCouponStartDate(value); return; }
        if (label.contains("Final Coupon")) { d.setFinalCouponDate(value); return; }
        if (label.contains("Coupon") && value.endsWith("%")) { d.setCouponRate(value); }
    }

    @Override
    public List<EurobondChartPoint> fetchChart(String tkData, LocalDate from, LocalDate to) {
        List<EurobondChartPoint> out = new ArrayList<>();
        if (tkData == null || tkData.isBlank() || from == null || to == null) {
            return out;
        }
        String url = BASE + "/Ajax/Chart_GetChartData?instrumentType=Bond"
                + "&tkData=" + UriUtils.encode(tkData, StandardCharsets.UTF_8)
                + "&from=" + from.format(YMD) + "&to=" + to.format(YMD);
        String body = get(url, "chart");
        if (body == null || body.isBlank()) {
            return out;
        }
        try {
            JsonNode arr = objectMapper.readTree(body);
            if (arr.isArray()) {
                for (JsonNode n : arr) {
                    String date = text(n, "Date");
                    BigDecimal close = decimal(n, "Close");
                    if (date == null || close == null) {
                        continue;
                    }
                    String day = date.length() >= 10 ? date.substring(0, 10) : date;
                    out.add(new EurobondChartPoint(day, close, decimal(n, "Open"), decimal(n, "High"), decimal(n, "Low")));
                }
            }
        } catch (Exception e) {
            log.debug("BI chart parse hatası: {}", e.getMessage());
        }
        return out;
    }

    /** Ban-korumalı GET: degrade penceresinde hiç gitme, istekleri serileştir, 403/429'da degrade et. */
    private String get(String url, String op) {
        if (System.currentTimeMillis() < degradedUntil) {
            return null;
        }
        synchronized (gate) {
            long wait = MIN_INTERVAL_MS - (System.currentTimeMillis() - lastReqAt);
            if (wait > 0) {
                try { Thread.sleep(wait); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
            }
            lastReqAt = System.currentTimeMillis();
        }
        try {
            HttpHeaders h = new HttpHeaders();
            h.set("User-Agent", UA);
            h.set("Accept", "*/*");
            h.set("Accept-Language", "en-US,en;q=0.9");
            ResponseEntity<String> r = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(h), String.class);
            return r.getStatusCode().is2xxSuccessful() ? r.getBody() : null;
        } catch (HttpStatusCodeException ex) {
            int sc = ex.getStatusCode().value();
            if (sc == 403 || sc == 429) {
                degradedUntil = System.currentTimeMillis() + DEGRADE_MS;
                integrationLog.publish(IntegrationLogSupport.EVENT_EXTERNAL_API_RATE_LIMITED, "WARN",
                        "Business Insider " + sc + " — " + (DEGRADE_MS / 1000) + "sn devre dışı (ban-koruması)",
                        IntegrationLogSupport.PROVIDER_BUSINESS_INSIDER, op, String.valueOf(sc), null, null, true,
                        Map.of("status", sc), BusinessInsiderBondClient.class.getName());
            } else {
                integrationLog.publish(IntegrationLogSupport.EVENT_EXTERNAL_API_FAILED, "WARN",
                        "Business Insider HTTP " + sc, IntegrationLogSupport.PROVIDER_BUSINESS_INSIDER, op,
                        String.valueOf(sc), null, null, false, Map.of("status", sc),
                        BusinessInsiderBondClient.class.getName());
            }
            return null;
        } catch (Exception e) {
            integrationLog.publish(IntegrationLogSupport.EVENT_EXTERNAL_API_FAILED, "WARN",
                    "Business Insider istek hatası: " + e.getMessage(), IntegrationLogSupport.PROVIDER_BUSINESS_INSIDER, op,
                    null, null, null, false, Map.of(), BusinessInsiderBondClient.class.getName());
            return null;
        }
    }

    private static BigDecimal parseNum(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim().replace("%", "").replace("(", "").replace(")", "").replace("+", "").trim();
        if (s.isEmpty()) {
            return null;
        }
        if (s.contains(".") && s.contains(",")) {
            s = s.replace(",", "");               // 1,234.56
        } else if (s.contains(",") && !s.contains(".")) {
            s = s.replace(",", ".");              // 101,37
        }
        try { return new BigDecimal(s); } catch (Exception e) { return null; }
    }

    private static String text(JsonNode n, String f) {
        return n != null && n.hasNonNull(f) ? n.get(f).asText(null) : null;
    }

    private static BigDecimal decimal(JsonNode n, String f) {
        if (n == null || !n.hasNonNull(f)) {
            return null;
        }
        try {
            return n.get(f).decimalValue();
        } catch (Exception e) {
            try { return new BigDecimal(n.get(f).asText()); } catch (Exception ex) { return null; }
        }
    }
}
