package com.finance.portal.market.infrastructure.external.fx;

import com.finance.portal.market.application.fx.model.FxHistoryPoint;
import com.finance.portal.market.application.fx.port.TcmbFxHistoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * TCMB arşiv XML'lerinden tarihsel döviz kuru verisi çeker.
 * URL: https://www.tcmb.gov.tr/kurlar/YYYYMM/DDMMYYYY.xml
 *
 * Tüm iş günleri paralel çekilir (flat parallelism, deadlock yok).
 */
@Component
public class TcmbFxHistoryClient implements TcmbFxHistoryPort {

    private static final Logger log = LoggerFactory.getLogger(TcmbFxHistoryClient.class);
    private static final String BASE_URL = "https://www.tcmb.gov.tr/kurlar";
    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyyMM");
    private static final DateTimeFormatter DAY_FMT   = DateTimeFormatter.ofPattern("ddMMyyyy");
    private static final DateTimeFormatter DATE_FMT  = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // Ayrı bir executor — Tomcat thread pool'unu bloke etmez
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(16);

    private final RestTemplate restTemplate;

    public TcmbFxHistoryClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Belirtilen tarih aralığı için döviz kuru tarihsel verisini döndürür.
     * Tüm iş günleri tek seferde paralel çekilir.
     */
    @Override
    public List<FxHistoryPoint> fetchHistory(String currencyCode, LocalDate from, LocalDate to) {
        // Tüm iş günlerini listele
        List<LocalDate> workdays = new ArrayList<>();
        LocalDate d = from;
        while (!d.isAfter(to)) {
            if (d.getDayOfWeek().getValue() < 6) { // Pzt-Cum
                workdays.add(d);
            }
            d = d.plusDays(1);
        }

        log.debug("Fetching TCMB history for {} from {} to {}: {} workdays",
                currencyCode, from, to, workdays.size());

        // Hepsini paralel çek
        List<CompletableFuture<FxHistoryPoint>> futures = workdays.stream()
                .map(day -> CompletableFuture.supplyAsync(
                        () -> fetchDailyXml(currencyCode, day), EXECUTOR))
                .toList();

        // Sonuçları topla
        List<FxHistoryPoint> points = new ArrayList<>();
        for (int i = 0; i < futures.size(); i++) {
            try {
                FxHistoryPoint pt = futures.get(i).get(10, TimeUnit.SECONDS);
                if (pt != null) points.add(pt);
            } catch (Exception e) {
                // Tatil günü veya timeout — sessizce atla
                log.debug("Skipping day {}: {}", workdays.get(i), e.getMessage());
            }
        }

        points.sort(Comparator.comparing(FxHistoryPoint::getDate));
        log.info("TCMB history for {}: {} points ({} workdays checked)",
                currencyCode, points.size(), workdays.size());
        return points;
    }

    /**
     * Tek bir günün XML'ini çekip ilgili dövizin ForexSelling değerini döndürür.
     * Hafta sonu / tatil günleri 404 döner → null.
     */
    private FxHistoryPoint fetchDailyXml(String currencyCode, LocalDate date) {
        String month = date.format(MONTH_FMT); // "202504"
        String day   = date.format(DAY_FMT);   // "23042025"
        String url   = BASE_URL + "/" + month + "/" + day + ".xml";

        try {
            String xml = restTemplate.getForObject(url, String.class);
            if (xml == null || xml.isBlank() || xml.contains("Page Not Found")) return null;

            BigDecimal value = parseForexSelling(xml, currencyCode);
            if (value == null) return null;

            return new FxHistoryPoint(date.format(DATE_FMT), value);
        } catch (HttpClientErrorException.NotFound e) {
            return null; // Tatil günü
        } catch (Exception e) {
            log.debug("TCMB XML fetch failed for {} on {}: {}", currencyCode, date, e.getMessage());
            return null;
        }
    }

    private BigDecimal parseForexSelling(String xml, String currencyCode) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new InputSource(new StringReader(xml)));

            NodeList currencies = doc.getElementsByTagName("Currency");
            for (int i = 0; i < currencies.getLength(); i++) {
                Element el = (Element) currencies.item(i);
                String code = el.getAttribute("CurrencyCode");
                if (currencyCode.equalsIgnoreCase(code)) {
                    String selling = getChildText(el, "ForexSelling");
                    if (selling != null && !selling.isBlank()) {
                        return new BigDecimal(selling.trim()).setScale(4, RoundingMode.HALF_UP);
                    }
                }
            }
        } catch (Exception e) {
            log.debug("XML parse error for {}: {}", currencyCode, e.getMessage());
        }
        return null;
    }

    private String getChildText(Element parent, String tag) {
        NodeList nodes = parent.getElementsByTagName(tag);
        if (nodes.getLength() == 0) return null;
        String text = nodes.item(0).getTextContent();
        return text != null ? text.trim() : null;
    }
}
