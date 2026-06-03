package com.finance.portal.market.application.bond.evds;

import com.finance.portal.market.application.bond.evds.model.BondCategory;
import com.finance.portal.market.application.bond.evds.model.TcmbDibsParser;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;

/**
 * DİBS finansal kategorisini <b>TCMB {@code dibs1.txt}</b> yayınından (otoriter kaynak) belirler.
 *
 * <p>CBRT kod-deseni tahmini ({@link BondClassifier#classify}) bazı aileleri karıştırıyordu —
 * özellikle TÜFE-endeksli kıymetler ({@code 121TDOZ} gibi kodlar) yanlışlıkla Kuponlu'ya düşüyor,
 * bu da {@code BondMaturityScheduler} itfasını bozuyordu (par 100 yerine gösterge değeri ödenmeli).
 * Bu servis, TCMB dosyasındaki <b>bölüm üyeliğini</b> otorite alır; {@link TcmbDibsParser} ile
 * ISIN→aile çıkarır, {@link BondClassifier#classifyFromFamily} ile aile + ISIN suffix'ten kesin
 * {@link BondCategory}'ye eşler (kupon/ana para stripi ayrımları korunur).
 *
 * <p><b>Dayanıklılık:</b> başlangıçta ({@link PostConstruct}) ve her gün ({@link Scheduled}) çekilir.
 * Çekim başarısızsa ya da makul sayıda kayıt gelmezse <b>son başarılı harita korunur</b> (immutable
 * volatile swap). Harita boşsa / ISIN bulunamazsa çağıran taraf mevcut heuristik sınıflandırıcıya
 * düşer — yani TCMB erişilemese bile uygulama çalışmaya devam eder.
 */
@Component
public class TcmbDibsClassificationService {

    private static final Logger log = LoggerFactory.getLogger(TcmbDibsClassificationService.class);

    /** TCMB DİBS günlük gösterge değerleri (Bölüm 1). */
    private static final String DEFAULT_URL =
            "https://www.tcmb.gov.tr/wps/wcm/connect/060e026b-d4e7-4987-8a20-e7e1bc6e4bbe/"
            + "dibs1.txt?MOD=AJPERES&CACHEID=ROOTWORKSPACE-060e026b-d4e7-4987-8a20-e7e1bc6e4bbe-pVvwNCN";

    /** Bu sayının altında kayıt gelirse yanıt bozuk/eksik sayılır, eski harita korunur. */
    private static final int MIN_EXPECTED = 500;

    private final RestTemplate restTemplate;
    private final String dibsUrl;

    /**
     * Immutable-swap: yenileme TÜM haritayı sıfırdan kurar ve volatile referansı atomik olarak yeni
     * IMMUTABLE haritaya siver. Okuyucu hep tutarlı snapshot görür; iç put/remove olmaz (Sonar
     * S3077 bu kullanım için yanlış-pozitif).
     */
    @SuppressWarnings("java:S3077")
    private volatile Map<String, BondCategory> registry = Map.of();

    public TcmbDibsClassificationService(
            RestTemplate restTemplate,
            @Value("${market.bond.tcmb-dibs-url:" + DEFAULT_URL + "}") String dibsUrl) {
        this.restTemplate = restTemplate;
        this.dibsUrl = dibsUrl;
    }

    @PostConstruct
    void init() {
        refresh();
    }

    /** Günlük yenileme (TCMB dosyayı her iş günü günceller). 06:20 İstanbul. */
    @Scheduled(cron = "${market.bond.tcmb-dibs-cron:0 20 6 * * *}")
    public void refresh() {
        try {
            // UTF-8 garantisi: byte olarak çek, açıkça decode et (StringHttpMessageConverter
            // varsayılanı ISO-8859-1 olup Türkçe karakterleri bozardı).
            byte[] bytes = restTemplate.getForObject(dibsUrl, byte[].class);
            if (bytes == null || bytes.length == 0) {
                log.warn("[TcmbDibs] Boş yanıt — mevcut sınıflandırma korunuyor ({} kayıt).", registry.size());
                return;
            }
            String body = new String(bytes, StandardCharsets.UTF_8);
            Map<String, BondCategory> reg = TcmbDibsParser.parse(body);

            if (reg.size() < MIN_EXPECTED) {
                log.warn("[TcmbDibs] Sadece {} kayıt parse edildi (<{}) — yanıt bozuk olabilir, "
                        + "mevcut sınıflandırma korunuyor ({} kayıt).", reg.size(), MIN_EXPECTED, registry.size());
                return;
            }
            this.registry = Map.copyOf(reg);
            log.info("[TcmbDibs] TCMB DİBS sınıflandırması yüklendi: {} kıymet.", reg.size());
        } catch (Exception e) {
            log.warn("[TcmbDibs] dibs1.txt alınamadı — mevcut sınıflandırma korunuyor ({} kayıt): {}",
                    registry.size(), e.getMessage());
        }
    }

    /**
     * ISIN için otoriter {@link BondCategory} ya da {@code null} (TCMB haritasında yoksa →
     * çağıran taraf heuristik sınıflandırıcıya düşer).
     */
    public BondCategory categoryOf(String isin) {
        if (isin == null) {
            return null;
        }
        return registry.get(isin.trim().toUpperCase(Locale.ROOT));
    }

    /** Yüklü otoriter kayıt sayısı (0 → TCMB henüz alınamadı, heuristik fallback aktif). */
    public int size() {
        return registry.size();
    }
}
