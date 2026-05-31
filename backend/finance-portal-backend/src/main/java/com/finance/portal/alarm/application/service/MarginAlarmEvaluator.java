package com.finance.portal.alarm.application.service;

import com.finance.portal.admin.application.model.AdminUserView;
import com.finance.portal.admin.application.port.KeycloakUserAdminPort;
import com.finance.portal.alarm.application.model.AlarmTriggeredEvent;
import com.finance.portal.alarm.application.port.AlarmNotificationPort;
import com.finance.portal.alarm.domain.AlarmDirection;
import com.finance.portal.alarm.domain.AlarmMetric;
import com.finance.portal.common.application.logging.BusinessLogSupport;
import com.finance.portal.common.application.logging.CentralBusinessLogService;
import com.finance.portal.common.domain.AssetType;
import com.finance.portal.portfolio.domain.Portfolio;
import com.finance.portal.portfolio.domain.PortfolioType;
import com.finance.portal.portfolio.presentation.dto.PortfolioHoldingResponse;
import com.finance.portal.portfolio.presentation.dto.PortfolioResponse;
import com.finance.portal.portfolio.repository.PortfolioRepository;
import com.finance.portal.portfolio.service.PortfolioService;
import com.finance.portal.preferences.service.UserPreferenceService;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * VİOP teminat (margin) uyarılarını periyodik olarak değerlendirir.
 *
 * <p><strong>Yeni model:</strong> Eski "her sembol için ayrı MARGIN_RATIO alarm satırı" yaklaşımı
 * yerine, kullanıcı profilinde tek bir eşik ({@code margin_alert_threshold_pct}, 0–100) tutulur.
 * Tüm açık FUTURE pozisyonlar bu eşik altına düşerse tek noktadan MARGIN_CALL bildirimi + e-posta
 * üretilir. Eski MARGIN_RATIO alarm satırları silinmez (enum geriye uyumlu) ancak
 * {@link AlarmEvaluationService#evaluateOne} zaten MARGIN_RATIO metriği için erken döner;
 * dolayısıyla legacy satırlar "uykuda" kalır.
 *
 * <p>İş akışı:
 * <ol>
 *   <li>{@code portfolioRepository.findAll()} → WATCHLIST hariç tüm portföyleri çek, userId'ye göre grupla.</li>
 *   <li>Her kullanıcı için {@link UserPreferenceService#getMarginAlertThresholdPct} eşiğini oku;
 *       0 ise (kapalı) atla.</li>
 *   <li>Her portföy için {@link PortfolioService#getPortfolioById} çağır → FutureHoldingEnricher
 *       {@code marginRatio} alanını doldurur (cache hit'te anında).</li>
 *   <li>Her açık FUTURE holding için {@code marginRatio * 100} ile eşiği karşılaştır; altındaysa
 *       de-bounce kontrolü (varsayılan 6 saat) yap ve tetikle.</li>
 *   <li>Bildirim yolu değişmedi: {@link AlarmNotificationPort#notifyAlarmTriggered} →
 *       MARGIN_CALL şablonu (kırmızı banner). alarmId null geçilir (artık Alarm satırına bağlı değil).</li>
 * </ol>
 *
 * <p>De-bounce: in-memory {@link ConcurrentHashMap}, (userId|symbol|direction) anahtarlı, restart'ta
 * sıfırlanır. Bilinçli tercih — kalıcı tablo yerine sade durum tutma. Cooldown ve "alt-bant" eşiği
 * konfigüre edilebilir (varsayılan: cooldown 6h, alt-bant -5 yüzde puanı).
 */
@Service
public class MarginAlarmEvaluator {

    private static final Logger log = LoggerFactory.getLogger(MarginAlarmEvaluator.class);
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final PortfolioRepository portfolioRepository;
    private final PortfolioService portfolioService;
    private final UserPreferenceService userPreferenceService;
    private final AlarmNotificationPort notificationPort;
    private final KeycloakUserAdminPort keycloakUserAdminPort;
    private final CentralBusinessLogService businessLog;
    private final boolean enabled;
    private final long cooldownMinutes;
    private final BigDecimal lowerBandPctPoints;

    /** Anahtar: userId|symbol|direction → en son tetik özeti. Restart'ta sıfırlanır (bilinçli). */
    private final Map<String, LastFire> lastFires = new ConcurrentHashMap<>();

    public MarginAlarmEvaluator(PortfolioRepository portfolioRepository,
                                PortfolioService portfolioService,
                                UserPreferenceService userPreferenceService,
                                AlarmNotificationPort notificationPort,
                                KeycloakUserAdminPort keycloakUserAdminPort,
                                CentralBusinessLogService businessLog,
                                @Value("${alarm.evaluation.enabled:true}") boolean enabled,
                                @Value("${alarm.margin-scan.cooldown-minutes:360}") long cooldownMinutes,
                                @Value("${alarm.margin-scan.lower-band-pct:5}") int lowerBandPct) {
        this.portfolioRepository = portfolioRepository;
        this.portfolioService = portfolioService;
        this.userPreferenceService = userPreferenceService;
        this.notificationPort = notificationPort;
        this.keycloakUserAdminPort = keycloakUserAdminPort;
        this.businessLog = businessLog;
        this.enabled = enabled;
        this.cooldownMinutes = cooldownMinutes;
        this.lowerBandPctPoints = BigDecimal.valueOf(Math.max(0, lowerBandPct));
    }

    @Scheduled(
            fixedRateString = "${alarm.margin-scan.fixed-rate-ms:60000}",
            initialDelayString = "${alarm.margin-scan.initial-delay-ms:30000}")
    @SchedulerLock(name = "user-margin-scan", lockAtMostFor = "PT2M", lockAtLeastFor = "PT30S")
    @WithSpan("MarginAlarmEvaluator.evaluateMarginAlarms")
    public void evaluateMarginAlarms() {
        if (!enabled) {
            return;
        }
        List<Portfolio> portfolios;
        try {
            portfolios = portfolioRepository.findAll().stream()
                    .filter(p -> p.getPortfolioType() != PortfolioType.WATCHLIST)
                    .toList();
        } catch (Exception ex) {
            log.warn("Margin scan: portföyler okunamadı: {}", ex.getMessage());
            return;
        }
        Span.current().setAttribute("margin_scan.portfolio_count", portfolios.size());
        if (portfolios.isEmpty()) {
            return;
        }

        // userId → portföy listesi (tek geçişte gruplama, kullanıcı başına eşik bir kere okunsun)
        Map<String, List<Portfolio>> byUser = new LinkedHashMap<>();
        for (Portfolio p : portfolios) {
            if (p.getUserId() == null) {
                continue;
            }
            byUser.computeIfAbsent(p.getUserId(), k -> new java.util.ArrayList<>()).add(p);
        }

        Map<String, String> emailCache = new HashMap<>();
        for (Map.Entry<String, List<Portfolio>> entry : byUser.entrySet()) {
            try {
                evaluateUser(entry.getKey(), entry.getValue(), emailCache);
            } catch (Exception ex) {
                log.warn("Margin scan: kullanıcı {} değerlendirmesi başarısız: {}", entry.getKey(), ex.getMessage());
            }
        }
    }

    @WithSpan("MarginAlarmEvaluator.evaluateUser")
    private void evaluateUser(String userId, List<Portfolio> portfolios, Map<String, String> emailCache) {
        int thresholdPct = userPreferenceService.getMarginAlertThresholdPct(userId);
        if (thresholdPct <= 0) {
            return; // kullanıcı uyarıyı kapatmış
        }
        BigDecimal thresholdRatio = BigDecimal.valueOf(thresholdPct).divide(HUNDRED, 4, RoundingMode.HALF_UP);
        Span.current().setAttribute("margin_scan.user_id", userId);
        Span.current().setAttribute("margin_scan.threshold_pct", thresholdPct);

        for (Portfolio p : portfolios) {
            PortfolioResponse detail;
            try {
                detail = portfolioService.getPortfolioById(userId, p.getId());
            } catch (Exception ex) {
                log.debug("Margin scan: portföy {} detayı alınamadı: {}", p.getId(), ex.getMessage());
                continue;
            }
            if (detail == null || detail.getHoldings() == null) {
                continue;
            }
            for (PortfolioHoldingResponse h : detail.getHoldings()) {
                if (h == null || h.isClosed()) {
                    continue;
                }
                if (h.getAssetType() != AssetType.FUTURE) {
                    continue;
                }
                BigDecimal observed = h.getMarginRatio();
                if (observed == null) {
                    continue; // enricher henüz dolduramadı → sonraki turda dene
                }
                if (observed.compareTo(thresholdRatio) >= 0) {
                    continue; // eşiğin üzerinde → sağlıklı
                }
                String key = dispatchKey(userId, h.getSymbol(), h.getViopDirection());
                if (!shouldFire(key, observed)) {
                    continue;
                }
                String email = emailCache.computeIfAbsent(userId, this::lookupUserEmail);
                fire(userId, email, h, thresholdRatio, observed, thresholdPct);
                lastFires.put(key, new LastFire(LocalDateTime.now(), observed));
            }
        }
    }

    /** Cooldown geçti mi VEYA değer alt-banda düştü mü? Belirsizse tetikleme. */
    private boolean shouldFire(String key, BigDecimal observedRatio) {
        LastFire last = lastFires.get(key);
        if (last == null) {
            return true;
        }
        LocalDateTime nextAllowed = last.firedAt().plusMinutes(cooldownMinutes);
        if (LocalDateTime.now().isAfter(nextAllowed)) {
            return true;
        }
        // Alt-bant: gözlenen yüzde, son tetiklenenin -X puan altına düştüyse cooldown'u bypass et
        // (margin call hızla derinleşiyorsa kullanıcıyı erken uyar).
        BigDecimal observedPct = observedRatio.multiply(HUNDRED);
        BigDecimal lastPct = last.observedRatio().multiply(HUNDRED);
        return observedPct.compareTo(lastPct.subtract(lowerBandPctPoints)) < 0;
    }

    @WithSpan("MarginAlarmEvaluator.fire")
    private void fire(String userId, String recipientEmail, PortfolioHoldingResponse holding,
                      BigDecimal thresholdRatio, BigDecimal observed, int thresholdPct) {
        Span.current().setAttribute("alarm.user_id", userId);
        Span.current().setAttribute("alarm.symbol", String.valueOf(holding.getSymbol()));
        Span.current().setAttribute("alarm.observed", String.valueOf(observed));
        Span.current().setAttribute("alarm.threshold_pct", thresholdPct);

        String positionDetail = buildPositionDetail(holding);

        // alarmId artık null (tek noktadan kullanıcı ayarı); AlarmNotificationAdapter MARGIN_CALL
        // şablonunu MARGIN_RATIO metric'ine göre seçer ve note'u "Pozisyon Detayı" satırı yapar.
        AlarmTriggeredEvent event = new AlarmTriggeredEvent(
                userId,
                recipientEmail,
                null,
                AssetType.FUTURE,
                holding.getSymbol(),
                holding.getName() != null ? holding.getName() : holding.getSymbol(),
                AlarmMetric.MARGIN_RATIO,
                AlarmDirection.BELOW,
                thresholdRatio,
                observed,
                positionDetail);
        notificationPort.notifyAlarmTriggered(event);

        log.info("Margin uyarısı tetiklendi: user={} symbol={} dir={} ratio={} < threshold={}",
                userId, holding.getSymbol(), holding.getViopDirection(), observed, thresholdRatio);

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("symbol", holding.getSymbol());
        meta.put("direction", holding.getViopDirection());
        meta.put("thresholdPct", thresholdPct);
        meta.put("threshold", thresholdRatio);
        meta.put("observed", observed);
        meta.put("marginPosted", holding.getViopMarginPosted());
        meta.put("pnl", holding.getProfitLoss());
        meta.put("quantity", holding.getTotalQuantity());
        businessLog.publish(BusinessLogSupport.CATEGORY_AUDIT,
                BusinessLogSupport.EVENT_MARGIN_CALL_TRIGGERED, "WARN",
                "Margin call triggered: " + holding.getSymbol(),
                BusinessLogSupport.ENTITY_ALARM, null,
                BusinessLogSupport.ACTION_TRIGGER, BusinessLogSupport.RESULT_SUCCESS,
                meta, userId, MarginAlarmEvaluator.class.getName());
    }

    private String lookupUserEmail(String userId) {
        try {
            AdminUserView u = keycloakUserAdminPort.getUser(userId);
            return u != null && u.isEmailVerified() ? u.getEmail() : null;
        } catch (Exception ex) {
            log.debug("Margin scan: {} kullanıcı e-postası alınamadı: {}", userId, ex.getMessage());
            return null;
        }
    }

    private static String dispatchKey(String userId, String symbol, String direction) {
        return userId + "|" + (symbol == null ? "" : symbol) + "|" + (direction == null ? "" : direction);
    }

    /** "LONG 5 lot, giriş 1234,5 → güncel 1180,2 (K/Z -2.715 / teminat 12.300)" tarzı tek-satır özet. */
    private static String buildPositionDetail(PortfolioHoldingResponse h) {
        StringBuilder sb = new StringBuilder();
        if (h.getViopDirection() != null) {
            sb.append(h.getViopDirection()).append(' ');
        }
        if (h.getTotalQuantity() != null) {
            sb.append(h.getTotalQuantity().stripTrailingZeros().toPlainString()).append(" lot");
        }
        if (h.getAverageCost() != null) {
            sb.append(", giriş ").append(h.getAverageCost().stripTrailingZeros().toPlainString());
        }
        if (h.getCurrentPrice() != null) {
            sb.append(" → güncel ").append(h.getCurrentPrice().stripTrailingZeros().toPlainString());
        }
        if (h.getProfitLoss() != null || h.getViopMarginPosted() != null) {
            sb.append(" (");
            if (h.getProfitLoss() != null) {
                sb.append("K/Z ").append(h.getProfitLoss().stripTrailingZeros().toPlainString());
            }
            if (h.getViopMarginPosted() != null) {
                if (h.getProfitLoss() != null) {
                    sb.append(" / ");
                }
                sb.append("teminat ").append(h.getViopMarginPosted().stripTrailingZeros().toPlainString());
            }
            sb.append(')');
        }
        return sb.toString();
    }

    /** De-bounce için en son tetik özetini tutar. */
    private record LastFire(LocalDateTime firedAt, BigDecimal observedRatio) {
    }
}
