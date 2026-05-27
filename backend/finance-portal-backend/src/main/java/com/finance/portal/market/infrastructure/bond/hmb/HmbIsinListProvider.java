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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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

    /**
     * HMB dış borç tahvil ISIN'leri Türkiye Hazinesi'nin ihraççı kodlarını taşır:
     * — US900123... (USD, ABD piyasası), XS... (Eurobond), JP579200... (Samurai/JPY).
     * Yüklenen xlsx'in gerçekten Hazine listesi olduğunu doğrulamak için kullanılır.
     */
    private static final Set<String> TR_SOVEREIGN_PREFIXES = Set.of("XS", "US", "JP");

    /**
     * HMB'nin birden fazla "tahvil xlsx"i var; bizim ilgilendiğimiz tek dosya
     * "Dış Borç Stoku Tahvil Listesi" (Eurobond stok). Diğer kuzen dosya
     * "Yurt Dışı Tahvil İhraçları" daha geniş kapsamlı — kabul edilmemeli.
     * URL slug'ında ASCII karşılığını arıyoruz (HMB filename'lerinde Türkçe karakter yok).
     */
    private static final String EXPECTED_URL_KEYWORD = "dis_borc_stoku";

    /** Geçerli bir HMB listesinde olması beklenen minimum ISIN sayısı (yanlış dosya filtresi). */
    static final int MIN_EXPECTED_ISINS = 20;

    /** Toplam ISIN içinde TR sovereign formatında (XS/US/JP) olması gereken minimum oran. */
    static final double MIN_TR_PREFIX_RATIO = 0.50;

    /**
     * Yeni xlsx'in mevcut aktif liste ile minimum örtüşme oranı. Aylık güncellemede
     * çoğu ISIN değişmez — sıfıra yakın örtüşme = yanlış/alakasız dosya.
     */
    static final double MIN_OVERLAP_RATIO = 0.30;

    private final RestTemplate restTemplate;
    private final CentralIntegrationLogService integrationLog;

    /**
     * Immutable-swap pattern: yenileme her seferinde TÜM Map'i sıfırdan kurar ve
     * volatile referansı atomik olarak yeni IMMUTABLE Map'e siver. Okuyucular hep
     * tutarlı bir snapshot görür; iç değişiklik (put/remove) hiç olmaz. Bu yüzden
     * volatile non-primitive Sonar S3077'si bu kullanım için yanlış-pozitiftir.
     */
    @SuppressWarnings({"java:S3077"})
    private volatile Map<String, HmbBond> registry = Map.of();

    @SuppressWarnings({"java:S3077"})
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
                String isin = c[0].trim().toUpperCase(Locale.ROOT);
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
        this.registry = Map.copyOf(reg);
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
    public int refreshFromXlsx(String xlsxUrl, boolean force) {
        if (xlsxUrl == null || !xlsxUrl.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw new IllegalArgumentException("Geçerli bir .xlsx URL'i verin.");
        }
        String lowerUrl = xlsxUrl.toLowerCase(Locale.ROOT);
        if (!force && !lowerUrl.contains(EXPECTED_URL_KEYWORD)) {
            throw new IllegalArgumentException(
                    "Bu URL HMB \"Dış Borç Stoku Tahvil Listesi\" gibi görünmüyor. " +
                    "Beklenen dosya adı: ...Merkezi_Yonetim_Dis_Borc_Stoku_Tahvil_Listesi-*.xlsx " +
                    "(Yurt_Disi_Tahvil_Ihraclari farklı bir liste — kabul edilmez). " +
                    "HMB filename şeması değiştiyse force=true ile override edebilirsiniz.");
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

        List<String> previous = this.activeIsins;
        validateHmbContent(xlsxUrl, found, previous);

        // Option A — cumulative union. Eski xlsx'te olan ama yenisinde olmayan ISIN'ler
        // kullanıcı portföylerinde olabilir; bu yüzden aktif listeden DROP edilmez,
        // sadece yenisinde olan ISIN'ler aktif kümeye eklenir.
        List<String> merged = unionPreservingOrder(previous, found);
        int added = merged.size() - previous.size();
        this.activeIsins = List.copyOf(merged);
        this.lastXlsxUrl = xlsxUrl;
        integrationLog.publish("HMB_ISIN_REFRESHED", "INFO",
                "HMB ISIN listesi güncellendi: " + merged.size() + " (yeni: " + added + ")",
                IntegrationLogSupport.PROVIDER_HMB, "isin_refresh",
                "200", null, false, false,
                Map.of("count", merged.size(), "added", added, "found", found.size(), "url", xlsxUrl),
                HmbIsinListProvider.class.getName());
        log.info("HMB aktif ISIN listesi güncellendi: cumulative={} (xlsx'te {} ISIN, +{} yeni, kaynak={})",
                merged.size(), found.size(), added, xlsxUrl);
        return merged.size();
    }

    /**
     * Yanlış/alakasız xlsx yüklenmesini engeller. Geçerli bir HMB Eurobond listesi:
     * <ul>
     *   <li>en az {@value #MIN_EXPECTED_ISINS} ISIN içerir,</li>
     *   <li>ISIN'lerin yarısından fazlası TR ihraççı prefiksinde (XS/US/JP) olur,</li>
     *   <li>mevcut aktif liste boş değilse, onunla en az %{@value #MIN_OVERLAP_RATIO}*100 örtüşür.</li>
     * </ul>
     */
    private void validateHmbContent(String xlsxUrl, List<String> found, List<String> previous) {
        if (found.size() < MIN_EXPECTED_ISINS) {
            reject(xlsxUrl, "Yüklenen xlsx HMB Eurobond listesi gibi görünmüyor: yalnızca "
                    + found.size() + " ISIN bulundu (en az " + MIN_EXPECTED_ISINS + " bekleniyor).");
        }

        long trCount = found.stream()
                .filter(i -> i.length() >= 2 && TR_SOVEREIGN_PREFIXES.contains(i.substring(0, 2)))
                .count();
        double trRatio = (double) trCount / found.size();
        if (trRatio < MIN_TR_PREFIX_RATIO) {
            reject(xlsxUrl, "Yüklenen xlsx HMB Eurobond listesi gibi görünmüyor: ISIN'lerin yalnızca %"
                    + Math.round(trRatio * 100) + "'i TR ihraççı formatında (XS/US/JP); en az %"
                    + Math.round(MIN_TR_PREFIX_RATIO * 100) + " bekleniyor.");
        }

        if (!previous.isEmpty()) {
            Set<String> prevSet = new HashSet<>(previous);
            long overlap = found.stream().filter(prevSet::contains).count();
            double overlapRatio = (double) overlap / previous.size();
            if (overlapRatio < MIN_OVERLAP_RATIO) {
                reject(xlsxUrl, "Yüklenen xlsx mevcut Eurobond listesiyle yalnızca %"
                        + Math.round(overlapRatio * 100) + " örtüşüyor (en az %"
                        + Math.round(MIN_OVERLAP_RATIO * 100)
                        + " bekleniyor). Yanlış/alakasız dosya yüklenmiş olabilir.");
            }
        }
    }

    private void reject(String xlsxUrl, String message) {
        integrationLog.publish(IntegrationLogSupport.EVENT_EXTERNAL_API_PARSE_FAILED, "WARN",
                "HMB xlsx içerik doğrulaması başarısız", IntegrationLogSupport.PROVIDER_HMB, "isin_refresh",
                null, null, null, false, Map.of("url", xlsxUrl, "reason", message),
                HmbIsinListProvider.class.getName());
        throw new IllegalArgumentException(message);
    }

    private static List<String> unionPreservingOrder(List<String> existing, List<String> incoming) {
        LinkedHashSet<String> set = new LinkedHashSet<>(existing);
        set.addAll(incoming);
        return new ArrayList<>(set);
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
                    String v = m.group(1).trim().toUpperCase(Locale.ROOT);
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
