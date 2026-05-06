package com.finance.portal.market.application.viop;

import com.finance.portal.market.infrastructure.external.viop.IsYatirimChartPoint;
import com.finance.portal.market.infrastructure.external.viop.IsYatirimViopChartClient;
import com.finance.portal.market.presentation.dto.ViopChartPointDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * VİOP grafik verisi servis katmanı.
 * <p>
 * Akbank sözleşme adını alır, İş Yatırım endeks koduna dönüştürür,
 * grafik verisini çeker ve normalize ederek döner.
 */
@Service
public class ViopChartService {

    private static final Logger log = LoggerFactory.getLogger(ViopChartService.class);

    /**
     * Timestamp → dateTime dönüşümü için Türkiye saat dilimi.
     */
    private static final ZoneId TURKEY_ZONE = ZoneId.of("Europe/Istanbul");
    private static final DateTimeFormatter DT_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final IsYatirimViopChartClient chartClient;
    private final ViopIndexCodeMapper indexCodeMapper;

    public ViopChartService(IsYatirimViopChartClient chartClient, ViopIndexCodeMapper indexCodeMapper) {
        this.chartClient = chartClient;
        this.indexCodeMapper = indexCodeMapper;
    }

    /**
     * VİOP sözleşmesi için grafik verisi döner.
     *
     * @param contractName Akbank sözleşme adı (örn: "THYAO (30 Haz 26) Vadeli FIZ.")
     * @param period       Zaman aralığı (ONE_DAY, ONE_WEEK, ONE_MONTH, THREE_MONTHS, SIX_MONTHS, ONE_YEAR)
     * @return Normalize edilmiş grafik noktaları
     * @throws UnsupportedViopContractException Desteklenmeyen sözleşme türü
     */
    public List<ViopChartPointDto> getChart(String contractName, ViopChartPeriod period) {
        log.info("ViopChartService.getChart: contractName='{}', period={}", contractName, period);

        // Endeks kodu üret — desteklenmiyorsa exception fırlat
        String endeksCode = indexCodeMapper.toIsYatirimEndeksCode(contractName)
                .orElseThrow(() -> {
                    log.warn("Unsupported VIOP contract for chart: '{}'", contractName);
                    return new UnsupportedViopContractException(
                            "Bu VİOP sözleşmesi için İş Yatırım grafik kodu üretilemedi: " + contractName);
                });

        log.info("Mapped contract '{}' -> endeks code '{}'", contractName, endeksCode);

        // Tarih aralığını hesapla
        LocalDateTime to = LocalDateTime.now(TURKEY_ZONE);
        LocalDateTime from = to.minusDays(period.getDays());

        // Period'a göre uygun mum aralığı (dakika)
        // Kısa periyotlarda daha sık veri noktası, uzun periyotlarda daha seyrek
        int candleMinutes = resolveCandleMinutes(period);

        log.info("Date range: from={}, to={}, candleMinutes={}", from.format(DT_FORMATTER), to.format(DT_FORMATTER), candleMinutes);

        // İş Yatırım'dan veri çek
        List<IsYatirimChartPoint> rawPoints = chartClient.fetchChart(endeksCode, from, to, candleMinutes);

        if (rawPoints.isEmpty()) {
            log.info("No chart data returned for endeks='{}', period={}", endeksCode, period);
            return List.of();
        }

        // Normalize et
        List<ViopChartPointDto> result = rawPoints.stream()
                .map(this::toDto)
                .toList();

        log.info("Returning {} chart points for endeks='{}'", result.size(), endeksCode);
        return result;
    }

    /**
     * Seçilen periyoda göre uygun mum aralığını (dakika) döner.
     * İş Yatırım VİOP kontratları için test edilmiş sonuçlar:
     * - period=60 (saatlik) tüm periyotlarda en fazla veri noktasını döndürüyor.
     *   Örn: AEFES 6ay → p=60: 411 nokta, p=1440: 43 nokta
     *        USDTRY 1yıl → p=60: 1146 nokta, p=1440: 247 nokta
     */
    private int resolveCandleMinutes(ViopChartPeriod period) {
        // Tüm periyotlar için saatlik (60 dk) — İş Yatırım'da max veri noktası sağlar
        return 60;
    }

    private ViopChartPointDto toDto(IsYatirimChartPoint point) {
        String dateTime = Instant.ofEpochMilli(point.getTimestamp())
                .atZone(TURKEY_ZONE)
                .toLocalDateTime()
                .format(DT_FORMATTER);
        return new ViopChartPointDto(point.getTimestamp(), dateTime, point.getValue());
    }
}
