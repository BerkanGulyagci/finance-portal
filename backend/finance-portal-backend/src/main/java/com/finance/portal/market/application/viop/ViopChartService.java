package com.finance.portal.market.application.viop;

import com.finance.portal.market.application.viop.model.ViopChartPoint;
import com.finance.portal.market.application.viop.port.ViopChartDataPort;
import com.finance.portal.market.infrastructure.external.viop.TurkishCharFixer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class ViopChartService {

    private static final Logger log = LoggerFactory.getLogger(ViopChartService.class);

    private static final ZoneId TURKEY_ZONE = ZoneId.of("Europe/Istanbul");
    private static final DateTimeFormatter DT_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final ViopChartDataPort viopChartDataPort;
    private final ViopIndexCodeMapper indexCodeMapper;

    public ViopChartService(ViopChartDataPort viopChartDataPort, ViopIndexCodeMapper indexCodeMapper) {
        this.viopChartDataPort = viopChartDataPort;
        this.indexCodeMapper = indexCodeMapper;
    }

    @Cacheable(
            cacheNames = "market.viop.chart",
            key = "#contractName + '|' + #period.name()",
            unless = "#result == null || #result.isEmpty()"
    )
    public List<ViopChartPoint> getChart(String contractName, ViopChartPeriod period) {
        String normalizedName = TurkishCharFixer.fix(contractName.trim());
        log.info("ViopChartService.getChart: contractName='{}', period={}", normalizedName, period);

        String endeksCode = indexCodeMapper.toIsYatirimEndeksCode(normalizedName)
                .orElseThrow(() -> {
                    log.warn("Unsupported VIOP contract for chart: '{}'", contractName);
                    return new UnsupportedViopContractException(
                            "Bu VİOP sözleşmesi için İş Yatırım grafik kodu üretilemedi: " + normalizedName);
                });

        log.info("Mapped contract '{}' -> endeks code '{}'", contractName, endeksCode);

        LocalDateTime to = LocalDateTime.now(TURKEY_ZONE);
        LocalDateTime from = to.minusDays(period.getDays());
        int candleMinutes = resolveCandleMinutes(period);

        log.info("Date range: from={}, to={}, candleMinutes={}",
                from.format(DT_FORMATTER), to.format(DT_FORMATTER), candleMinutes);

        List<ViopChartPoint> result = viopChartDataPort.fetchChart(endeksCode, from, to, candleMinutes);

        if (result.isEmpty()) {
            log.info("No chart data returned for endeks='{}', period={}", endeksCode, period);
            return List.of();
        }

        log.info("Returning {} chart points for endeks='{}'", result.size(), endeksCode);
        return result;
    }

    private int resolveCandleMinutes(ViopChartPeriod period) {
        return 60;
    }
}
