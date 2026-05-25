package com.finance.portal.market.infrastructure.bond.hmb;

import com.finance.portal.common.application.logging.CentralIntegrationLogService;
import com.finance.portal.common.application.logging.IntegrationLogSupport;
import com.finance.portal.market.application.bond.eurobond.model.HmbBond;
import com.finance.portal.market.application.bond.eurobond.port.HmbIsinSource;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * HMB "Merkezi Yönetim Dış Borç Stoku Tahvil Listesi" kaynağı.
 * Açılışta resource CSV tohumunu yükler (ISIN + statik künye); {@link #refreshFromXlsx} aktif ISIN
 * kümesini aylık xlsx'ten tazeler. xlsx=zip → {@code xl/sharedStrings.xml} içindeki TAM ISIN olan
 * {@code <t>} değerleri (dipnot ISIN'leri cümle içinde olduğundan hariç). POI gerektirmez.
 */
@Component
public class HmbIsinListProvider implements HmbIsinSource {

    private static final Logger log = LoggerFactory.getLogger(HmbIsinListProvider.class);
    private static final String SEED = "eurobond/hmb-bonds.csv";
    private static final String UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";
    private static final Pattern ISIN = Pattern.compile("^[A-Z]{2}[A-Z0-9]{9}[0-9]$");
    private static final Pattern T_TAG = Pattern.compile("<t[^>]*>([^<]*)</t>");

    private final RestTemplate restTemplate;
    private final CentralIntegrationLogService integrationLog;

    /** ISIN → statik künye (tohumdan). LinkedHashMap → tablo sırası korunur. */
    private volatile Map<String, HmbBond> registry = new LinkedHashMap<>();
    private volatile List<String> activeIsins = List.of();
    private volatile String lastXlsxUrl = null;

    public HmbIsinListProvider(RestTemplate restTemplate, CentralIntegrationLogService integrationLog) {
        this.restTemplate = restTemplate;
        this.integrationLog = integrationLog;
    }

    @PostConstruct
    void loadSeed() {
        Map<String, HmbBond> reg = new LinkedHashMap<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(
                new ClassPathResource(SEED).getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                String s = line.trim();
                if (s.isEmpty() || s.startsWith("#")) {
                    continue;
                }
                String[] c = s.split(",", -1);
                String isin = c[0].trim().toUpperCase();
                if (!ISIN.matcher(isin).matches()) {
                    continue;
                }
                reg.put(isin, new HmbBond(isin,
                        c.length > 1 ? blankToNull(c[1]) : null,
                        c.length > 2 ? blankToNull(c[2]) : null,
                        c.length > 3 ? blankToNull(c[3]) : null,
                        c.length > 4 ? blankToNull(c[4]) : null));
            }
        } catch (Exception e) {
            log.warn("HMB tohum CSV okunamadı: {}", e.getMessage());
        }
        this.registry = reg;
        this.activeIsins = List.copyOf(reg.keySet());
        log.info("HMB tahvil tohum listesi yüklendi: {} ISIN", activeIsins.size());
    }

    @Override
    public List<String> isins() {
        return activeIsins;
    }

    @Override
    public List<HmbBond> bonds() {
        Map<String, HmbBond> reg = registry;
        List<HmbBond> out = new ArrayList<>(activeIsins.size());
        for (String isin : activeIsins) {
            out.add(reg.getOrDefault(isin, new HmbBond(isin, null, null, null, null)));
        }
        return out;
    }

    @Override
    public String lastXlsxUrl() {
        return lastXlsxUrl;
    }

    @Override
    public int refreshFromXlsx(String xlsxUrl) {
        if (xlsxUrl == null || !xlsxUrl.toLowerCase().endsWith(".xlsx")) {
            throw new IllegalArgumentException("Geçerli bir .xlsx URL'i verin.");
        }
        byte[] bytes = download(xlsxUrl);
        if (bytes == null || bytes.length == 0) {
            integrationLog.publish(IntegrationLogSupport.EVENT_EXTERNAL_API_FAILED, "WARN",
                    "HMB xlsx indirilemedi", IntegrationLogSupport.PROVIDER_HMB, "isin_refresh",
                    null, null, null, false, Map.of("url", xlsxUrl), HmbIsinListProvider.class.getName());
            throw new IllegalStateException("xlsx indirilemedi: " + xlsxUrl);
        }
        List<String> found = parseIsins(bytes);
        if (found.isEmpty()) {
            integrationLog.publish(IntegrationLogSupport.EVENT_EXTERNAL_API_PARSE_FAILED, "WARN",
                    "HMB xlsx içinde ISIN bulunamadı", IntegrationLogSupport.PROVIDER_HMB, "isin_refresh",
                    null, null, null, false, Map.of("url", xlsxUrl), HmbIsinListProvider.class.getName());
            throw new IllegalStateException("xlsx içinde ISIN bulunamadı (format değişmiş olabilir): " + xlsxUrl);
        }
        this.activeIsins = List.copyOf(found);
        this.lastXlsxUrl = xlsxUrl;
        integrationLog.publish("HMB_ISIN_REFRESHED", "INFO",
                "HMB ISIN listesi güncellendi: " + found.size(), IntegrationLogSupport.PROVIDER_HMB, "isin_refresh",
                "200", null, false, false, Map.of("count", found.size(), "url", xlsxUrl), HmbIsinListProvider.class.getName());
        log.info("HMB aktif ISIN listesi güncellendi: {} ISIN (kaynak={})", found.size(), xlsxUrl);
        return found.size();
    }

    private byte[] download(String url) {
        try {
            HttpHeaders h = new HttpHeaders();
            h.set("User-Agent", UA);
            ResponseEntity<byte[]> r = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(h), byte[].class);
            return r.getStatusCode().is2xxSuccessful() ? r.getBody() : null;
        } catch (Exception e) {
            log.warn("HMB xlsx indirilemedi {} : {}", url, e.getMessage());
            return null;
        }
    }

    private static List<String> parseIsins(byte[] xlsx) {
        List<String> out = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(xlsx))) {
            ZipEntry e;
            while ((e = zip.getNextEntry()) != null) {
                if (!"xl/sharedStrings.xml".equals(e.getName())) {
                    continue;
                }
                String xml = new String(zip.readAllBytes(), StandardCharsets.UTF_8);
                Matcher m = T_TAG.matcher(xml);
                while (m.find()) {
                    String v = m.group(1).trim().toUpperCase();
                    if (ISIN.matcher(v).matches() && seen.add(v)) {
                        out.add(v);
                    }
                }
                break;
            }
        } catch (Exception ex) {
            log.warn("xlsx parse hatası: {}", ex.getMessage());
        }
        return out;
    }

    private static String blankToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
