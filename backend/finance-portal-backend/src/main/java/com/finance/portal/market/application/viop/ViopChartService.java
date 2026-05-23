package com.finance.portal.market.application.viop;

import com.finance.portal.market.application.viop.model.ViopChartPoint;
import com.finance.portal.market.application.viop.port.ViopChartDataPort;
import com.finance.portal.market.infrastructure.external.viop.TurkishCharFixer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * VİOP sözleşme grafiği — İş Yatırım kaynağından.
 *
 * <p><b>Dayanıklılık:</b> İş Yatırım kaynağı aynı kontrat için aralıklı olarak (flaky)
 * boş yanıt döndürebiliyor; bu durumda kullanıcı "grafik alınamadı" görüyordu. Bunu
 * önlemek için:
 * <ul>
 *   <li>Bellekte (LRU, en çok {@value #MAX_ENTRIES} kayıt) <b>son başarılı seri</b> tutulur;</li>
 *   <li>{@value #FRESH_TTL_MS} ms içindeki istek doğrudan bu seriyi döndürür (kaynağı yormaz);</li>
 *   <li>Tazeleme gerektiğinde kaynak <b>3 kez</b> denenir;</li>
 *   <li>Kaynak yine boş dönerse <b>son başarılı seri (eski de olsa)</b> sunulur — grafiğin
 *       boş kalmasından iyidir (noktalarda tarih olduğu için kullanıcı tazeliği görür).</li>
 * </ul>
 * Önceki Redis {@code @Cacheable} ("market.viop.chart") yerine bu bellek-içi yapı kullanılır;
 * çünkü boş sonuç cache'lenmediği için flaky boş yanıtlarda eski iyi veri korunamıyordu.
 */
@Service
public class ViopChartService {

    private static final Logger log = LoggerFactory.getLogger(ViopChartService.class);

    private static final ZoneId TURKEY_ZONE = ZoneId.of("Europe/Istanbul");
    private static final DateTimeFormatter DT_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    /** Taze sayılan süre — bu süre içindeki istek doğrudan bellekten döner. */
    private static final long FRESH_TTL_MS = 120_000L;
    private static final int MAX_FETCH_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 350L;
    private static final int MAX_ENTRIES = 500;

    private final ViopChartDataPort viopChartDataPort;
    private final ViopIndexCodeMapper indexCodeMapper;

    /** Son başarılı seriler (endeksKodu|period → seri). LRU + erişim sırası. */
    private final Map<String, CachedSeries> lastGood = Collections.synchronizedMap(
            new LinkedHashMap<>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, CachedSeries> eldest) {
                    return size() > MAX_ENTRIES;
                }
            });

    public ViopChartService(ViopChartDataPort viopChartDataPort, ViopIndexCodeMapper indexCodeMapper) {
        this.viopChartDataPort = viopChartDataPort;
        this.indexCodeMapper = indexCodeMapper;
    }

    public List<ViopChartPoint> getChart(String contractName, ViopChartPeriod period) {
        String normalizedName = TurkishCharFixer.fix(contractName.trim());

        String endeksCode = indexCodeMapper.toIsYatirimEndeksCode(normalizedName)
                .orElseThrow(() -> {
                    log.warn("Unsupported VIOP contract for chart: '{}'", contractName);
                    return new UnsupportedViopContractException(
                            "Bu VİOP sözleşmesi için İş Yatırım grafik kodu üretilemedi: " + normalizedName);
                });

        String key = endeksCode + "|" + period.name();
        long now = System.currentTimeMillis();

        CachedSeries cached = lastGood.get(key);
        if (cached != null && now - cached.timestamp() < FRESH_TTL_MS) {
            return cached.data();
        }

        List<ViopChartPoint> fresh = fetchWithRetry(endeksCode, period);
        if (!fresh.isEmpty()) {
            lastGood.put(key, new CachedSeries(now, fresh));
            log.info("VIOP chart endeks='{}' period={} → {} nokta (taze)", endeksCode, period, fresh.size());
            return fresh;
        }

        // Kaynak boş döndü — son başarılı seri varsa onu sun (grafik boş kalmasın).
        if (cached != null && !cached.data().isEmpty()) {
            long ageMin = (now - cached.timestamp()) / 60_000L;
            log.info("VIOP chart endeks='{}' period={}: kaynak boş, son başarılı seri sunuluyor "
                    + "({} nokta, ~{} dk önce)", endeksCode, period, cached.data().size(), ageMin);
            return cached.data();
        }

        log.info("VIOP chart endeks='{}' period={}: veri yok ve önbellekte son başarılı seri yok",
                endeksCode, period);
        return List.of();
    }

    /** Kaynaktan çeker; boş/hata olursa kısa gecikmeyle birkaç kez tekrar dener. */
    private List<ViopChartPoint> fetchWithRetry(String endeksCode, ViopChartPeriod period) {
        LocalDateTime to = LocalDateTime.now(TURKEY_ZONE);
        LocalDateTime from = to.minusDays(period.getDays());
        int candleMinutes = resolveCandleMinutes(period);

        for (int attempt = 1; attempt <= MAX_FETCH_ATTEMPTS; attempt++) {
            try {
                List<ViopChartPoint> r = viopChartDataPort.fetchChart(endeksCode, from, to, candleMinutes);
                if (r != null && !r.isEmpty()) {
                    return r;
                }
                log.debug("VIOP fetch endeks='{}' period={} boş (deneme {}/{})",
                        endeksCode, period, attempt, MAX_FETCH_ATTEMPTS);
            } catch (Exception e) {
                log.debug("VIOP fetch endeks='{}' period={} hata (deneme {}/{}): {}",
                        endeksCode, period, attempt, MAX_FETCH_ATTEMPTS, e.getMessage());
            }
            if (attempt < MAX_FETCH_ATTEMPTS) {
                sleep(RETRY_DELAY_MS);
            }
        }
        return List.of();
    }

    private int resolveCandleMinutes(ViopChartPeriod period) {
        return 60;
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Bellekte tutulan son başarılı seri + zaman damgası. */
    private record CachedSeries(long timestamp, List<ViopChartPoint> data) {
    }
}
