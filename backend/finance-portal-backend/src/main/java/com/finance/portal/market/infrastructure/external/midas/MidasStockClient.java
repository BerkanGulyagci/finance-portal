package com.finance.portal.market.infrastructure.external.midas;

import com.finance.portal.market.application.stock.MidasStockDetail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class MidasStockClient {

    private static final Logger log = LoggerFactory.getLogger(MidasStockClient.class);
    private static final String BASE_URL = "https://getmidas.com/canli-borsa/%s-hisse";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0 Safari/537.36";

    private final RestTemplate restTemplate;

    public MidasStockClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public MidasStockDetail fetchDetail(String symbol) {
        String slug = symbol.toLowerCase().replace(".is", "");
        String url = String.format(BASE_URL, slug);
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.USER_AGENT, USER_AGENT);
            headers.set(HttpHeaders.ACCEPT, "text/html,application/xhtml+xml");
            headers.set(HttpHeaders.ACCEPT_LANGUAGE, "tr-TR,tr;q=0.9");
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            String html = response.getBody();
            if (html == null) return null;

            return parseDetail(html, symbol);
        } catch (Exception e) {
            log.warn("Failed to fetch Midas detail for {}: {}", symbol, e.getMessage());
            return null;
        }
    }

    private MidasStockDetail parseDetail(String html, String symbol) {
        MidasStockDetail d = new MidasStockDetail();
        d.setSymbol(symbol.replace(".IS", "").replace(".is", "").toUpperCase());

        // Company logo from img tag
        d.setLogoUrl(extractFirst(html, "<img[^>]+src=\"(https://webcdn\\.getmidas\\.com/uploads/[^\"]+logo[^\"]+)\""));

        // Company name from h1
        d.setName(extractFirst(html, "<h1[^>]*>([^<]+Hisse[^<]+)</h1>"));
        if (d.getName() != null) {
            // Clean: "A1CAP Hisse - A1 Capital..." → take part after " - "
            int dash = d.getName().indexOf(" - ");
            if (dash > 0) d.setName(d.getName().substring(dash + 3).trim());
        }

        // Current price — <p class="val">₺43,50</p> <span class="title">Güncel Fiyat</span>
        d.setCurrentPrice(extractBeforeLabel(html, "Güncel Fiyat", "val"));

        // Daily volume
        d.setDailyVolume(extractBeforeLabel(html, "Günlük İşlem Hacmi", "val"));

        // Daily change percent
        d.setDailyChangePercent(extractFirst(html, "\\(([\\d,\\.]+%)\\)"));

        // Daily change TL — from financial table
        d.setDailyChange(extractTableValue(html, "Günlük Değişim \\(TL\\)"));

        // Weekly high/low
        d.setWeeklyHigh(extractTableValue(html, "Haftalık En Yüksek"));
        d.setWeeklyLow(extractTableValue(html, "Haftalık En Düşük"));

        // Monthly high/low
        d.setMonthlyHigh(extractTableValue(html, "Aylık En Yüksek"));
        d.setMonthlyLow(extractTableValue(html, "Aylık En Düşük"));

        // Opening price
        d.setOpenPrice(extractTableValue(html, "Açılış Fiyatı"));

        // Bid/Ask
        d.setBid(extractTableValue(html, "Alış"));
        d.setAsk(extractTableValue(html, "Satış"));

        // Tavan/Taban
        d.setUpperLimit(extractTableValue(html, "Tavan"));
        d.setLowerLimit(extractTableValue(html, "Taban"));

        // Volume lot
        d.setVolumeLot(extractTableValue(html, "Günlük Hacim \\(Lot\\)"));

        // Market cap, capital
        d.setMarketCap(extractTableValue(html, "Piyasa Değeri"));
        d.setCapital(extractTableValue(html, "Sermaye"));

        // Ratios
        d.setPeRatio(extractTableValue(html, "F/K"));
        d.setPbRatio(extractTableValue(html, "PD/DD"));
        d.setFreeFloat(extractTableValue(html, "Halka Açıklık Oranı \\(%\\)"));
        d.setForeignRatio(extractTableValue(html, "Yabancı Oranı \\(%\\)"));
        d.setVolatility(extractTableValue(html, "Volatilite"));
        d.setNetProfit(extractTableValue(html, "Net Kâr"));

        // Company info
        d.setCeo(extractCompanyField(html, "CEO"));
        d.setEmployeeCount(extractCompanyField(html, "Çalışan Sayısı"));
        d.setFoundedDate(extractCompanyField(html, "Kuruluş Tarihi"));
        d.setIpoDate(extractCompanyField(html, "Halka Arz Tarihi"));
        d.setSector(extractCompanyField(html, "Sektör"));
        d.setAddress(extractCompanyField(html, "Adres"));
        d.setCountry(extractCompanyField(html, "Merkez"));

        // Description
        d.setDescription(extractDescription(html));

        // Shareholders
        d.setShareholders(extractShareholders(html));

        return d;
    }

    /** Extract value from financial metrics table: label → next ₺value or plain value */
    private String extractTableValue(String html, String label) {
        try {
            int idx = html.indexOf(label.replace("\\(", "(").replace("\\)", ")").replace("\\%", "%"));
            if (idx == -1) {
                // Try regex
                Matcher m = Pattern.compile(label).matcher(html);
                if (!m.find()) return null;
                idx = m.start();
            }
            String after = html.substring(idx + label.length(), Math.min(html.length(), idx + label.length() + 400));
            // Find ₺value or plain number
            Matcher m = Pattern.compile("₺([\\d\\.,]+)").matcher(after);
            if (m.find()) return "₺" + m.group(1);
            m = Pattern.compile(">([\\d\\.,]+%?)</").matcher(after);
            if (m.find()) return m.group(1);
            return null;
        } catch (Exception e) { return null; }
    }

    /** Extract company info field (CEO, Sektör, etc.) */
    private String extractCompanyField(String html, String label) {
        try {
            int idx = html.indexOf(label);
            if (idx == -1) return null;
            String after = html.substring(idx + label.length(), Math.min(html.length(), idx + label.length() + 500));
            Matcher m = Pattern.compile(">([^<]{2,200}?)</").matcher(after);
            while (m.find()) {
                String val = m.group(1).trim();
                if (!val.isEmpty() && !val.equals(label) && val.length() > 1) return val;
            }
            return null;
        } catch (Exception e) { return null; }
    }

    /** Extract value from element with given class that appears just before a label */
    private String extractBeforeLabel(String html, String label, String valueClass) {
        try {
            int idx = html.indexOf(label);
            if (idx == -1) return null;
            // Look backwards for the value element
            String before = html.substring(Math.max(0, idx - 300), idx);
            // Find last occurrence of class="val" or similar
            Pattern p = Pattern.compile("class=\"" + valueClass + "\">([^<]+)</", Pattern.DOTALL);
            Matcher m = p.matcher(before);
            String last = null;
            while (m.find()) last = m.group(1).trim();
            return last;
        } catch (Exception e) { return null; }
    }

    private String extractFirst(String html, String pattern) {
        try {
            Matcher m = Pattern.compile(pattern, Pattern.DOTALL).matcher(html);
            return m.find() ? m.group(1).trim() : null;
        } catch (Exception e) { return null; }
    }

    private String extractDescription(String html) {
        try {
            // Look for "Hakkında" section with substantial paragraphs
            int idx = html.indexOf("Hakkında");
            if (idx == -1) return null;
            String section = html.substring(idx, Math.min(html.length(), idx + 8000));
            Matcher m = Pattern.compile("<p[^>]*>([^<]{80,})</p>", Pattern.DOTALL).matcher(section);
            StringBuilder sb = new StringBuilder();
            int count = 0;
            while (m.find() && count < 4) {
                String para = m.group(1).trim().replaceAll("\\s+", " ");
                if (!para.isEmpty() && !para.contains("Midas") && !para.contains("telif")) {
                    if (sb.length() > 0) sb.append("\n\n");
                    sb.append(para);
                    count++;
                }
            }
            return sb.length() > 0 ? sb.toString() : null;
        } catch (Exception e) { return null; }
    }

    private List<MidasStockDetail.ShareholderItem> extractShareholders(String html) {
        List<MidasStockDetail.ShareholderItem> result = new ArrayList<>();
        try {
            int idx = html.indexOf("Ortaklık Yapısı");
            if (idx == -1) return result;
            String section = html.substring(idx, Math.min(html.length(), idx + 4000));

            // Table rows: name | percentage
            Pattern p = Pattern.compile(
                "<tr[^>]*>\\s*<td[^>]*>\\s*([A-ZÇĞİÖŞÜa-zçğışöüA-Z][^<]{3,150}?)\\s*</td>\\s*<td[^>]*>\\s*([\\d,\\.]+)\\s*</td>",
                Pattern.DOTALL
            );
            Matcher m = p.matcher(section);
            while (m.find() && result.size() < 10) {
                String name = m.group(1).trim().replaceAll("\\s+", " ");
                String pct = m.group(2).trim();
                if (!name.isEmpty() && !pct.isEmpty()) {
                    result.add(new MidasStockDetail.ShareholderItem(name, pct));
                }
            }
        } catch (Exception e) {
            log.debug("Failed to extract shareholders: {}", e.getMessage());
        }
        return result;
    }
}
