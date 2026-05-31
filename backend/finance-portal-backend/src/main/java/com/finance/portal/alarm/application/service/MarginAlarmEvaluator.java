package com.finance.portal.alarm.application.service;

import com.finance.portal.alarm.application.model.AlarmTriggeredEvent;
import com.finance.portal.alarm.application.port.AlarmNotificationPort;
import com.finance.portal.alarm.domain.Alarm;
import com.finance.portal.alarm.domain.AlarmFrequency;
import com.finance.portal.alarm.domain.AlarmMetric;
import com.finance.portal.alarm.domain.AlarmStatus;
import com.finance.portal.alarm.repository.AlarmRepository;
import com.finance.portal.common.application.logging.BusinessLogSupport;
import com.finance.portal.common.application.logging.CentralBusinessLogService;
import com.finance.portal.common.domain.AssetType;
import com.finance.portal.portfolio.presentation.dto.PortfolioHoldingResponse;
import com.finance.portal.portfolio.presentation.dto.PortfolioResponse;
import com.finance.portal.portfolio.service.PortfolioService;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * VİOP teminat (margin) alarmlarını periyodik olarak değerlendirir.
 *
 * <p>Genel AlarmEvaluationService MARGIN_RATIO'yu atlar çünkü teminat oranı kullanıcının açık
 * pozisyonuna bağlıdır (qty, giriş fiyatı, yön) — jenerik market probe bunu üretemez. Bu evaluator:
 * <ol>
 *   <li>Tüm ACTIVE + MARGIN_RATIO alarmlarını çeker.</li>
 *   <li>Her alarm için kullanıcının portföylerini tarayıp eşleşen FUTURE holding'i bulur
 *       (sembol + viopDirection aynı olmalı).</li>
 *   <li>PortfolioService.getPortfolioById zaten FutureHoldingEnricher'ı çalıştırdığı için
 *       holding.marginRatio dolu gelir — yeniden hesaplamayız, aynı kaynaktan tüketiriz.</li>
 *   <li>Oran threshold altındaysa ve cooldown geçtiyse {@link AlarmNotificationPort} üzerinden
 *       MARGIN_CALL bildirimi + e-posta tetikler ve AUDIT loga yazar.</li>
 * </ol>
 *
 * <p>Pozisyon kapalıysa (kullanıcı manuel kapattıysa) alarmı pasif duruma çekmez —
 * yalnızca atlar (eşleşen holding bulunamaz). Kullanıcı isterse alarmı manuel siler.
 */
@Service
public class MarginAlarmEvaluator {

    private static final Logger log = LoggerFactory.getLogger(MarginAlarmEvaluator.class);

    private final AlarmRepository alarmRepository;
    private final AlarmNotificationPort notificationPort;
    private final PortfolioService portfolioService;
    private final CentralBusinessLogService businessLog;
    private final boolean enabled;
    private final long recurringCooldownMinutes;

    public MarginAlarmEvaluator(AlarmRepository alarmRepository,
                                AlarmNotificationPort notificationPort,
                                PortfolioService portfolioService,
                                CentralBusinessLogService businessLog,
                                @Value("${alarm.evaluation.enabled:true}") boolean enabled,
                                @Value("${alarm.recurring-cooldown-minutes:60}") long recurringCooldownMinutes) {
        this.alarmRepository = alarmRepository;
        this.notificationPort = notificationPort;
        this.portfolioService = portfolioService;
        this.businessLog = businessLog;
        this.enabled = enabled;
        this.recurringCooldownMinutes = recurringCooldownMinutes;
    }

    @Scheduled(
            fixedRateString = "${alarm.margin-evaluation.fixed-rate-ms:60000}",
            initialDelayString = "${alarm.margin-evaluation.initial-delay-ms:30000}")
    @SchedulerLock(name = "margin-alarm-evaluation", lockAtMostFor = "PT2M", lockAtLeastFor = "PT30S")
    @WithSpan("MarginAlarmEvaluator.evaluateMarginAlarms")
    public void evaluateMarginAlarms() {
        if (!enabled) {
            return;
        }
        List<Alarm> active = alarmRepository.findByStatusAndMetric(AlarmStatus.ACTIVE, AlarmMetric.MARGIN_RATIO);
        Span.current().setAttribute("margin_alarm.active_count", active.size());
        if (active.isEmpty()) {
            return;
        }
        log.debug("Evaluating {} active margin alarm(s)", active.size());
        for (Alarm alarm : active) {
            try {
                evaluateOne(alarm);
            } catch (Exception ex) {
                log.warn("Margin alarm {} evaluation failed: {}", alarm.getId(), ex.getMessage());
            }
        }
    }

    @WithSpan("MarginAlarmEvaluator.evaluateOne")
    private void evaluateOne(Alarm alarm) {
        Span.current().setAttribute("alarm.id", String.valueOf(alarm.getId()));
        Span.current().setAttribute("alarm.symbol", String.valueOf(alarm.getSymbol()));

        if (alarm.getAssetType() != AssetType.FUTURE) {
            // AlarmService.validateMarginAlarm normalde engeller; eski/bozuk satırlar için savunma.
            return;
        }

        PortfolioHoldingResponse holding = findOpenFutureHolding(alarm.getUserId(), alarm.getSymbol());
        if (holding == null) {
            // Pozisyon yok (kapatılmış / portföye eklenmemiş) → bu turda atla.
            return;
        }

        BigDecimal observed = holding.getMarginRatio();
        if (observed == null) {
            // Enricher henüz fiyat çekemediyse (cache miss, network hatası) — turla
            return;
        }

        alarm.setLastObservedValue(observed);

        BigDecimal threshold = alarm.getThreshold();
        if (threshold == null) {
            alarmRepository.save(alarm);
            return;
        }

        // direction zorunlu BELOW (AlarmService.validateMarginAlarm garantiler) →
        // observed < threshold koşulu margin call'u tetikler.
        boolean conditionMet = observed.compareTo(threshold) < 0;
        if (!conditionMet) {
            alarmRepository.save(alarm);
            return;
        }

        // RECURRING: cooldown dolmadıysa tetikleme (ama gözlenen değeri kaydet)
        if (alarm.getFrequency() == AlarmFrequency.RECURRING && alarm.getLastTriggeredAt() != null) {
            LocalDateTime nextAllowed = alarm.getLastTriggeredAt().plusMinutes(recurringCooldownMinutes);
            if (LocalDateTime.now().isBefore(nextAllowed)) {
                alarmRepository.save(alarm);
                return;
            }
        }

        trigger(alarm, observed, holding);
    }

    @WithSpan("MarginAlarmEvaluator.trigger")
    private void trigger(Alarm alarm, BigDecimal observed, PortfolioHoldingResponse holding) {
        Span.current().setAttribute("alarm.id", String.valueOf(alarm.getId()));
        Span.current().setAttribute("alarm.observed", String.valueOf(observed));
        Span.current().setAttribute("alarm.threshold", String.valueOf(alarm.getThreshold()));

        LocalDateTime now = LocalDateTime.now();
        alarm.setLastTriggeredAt(now);
        if (alarm.getTriggeredAt() == null) {
            alarm.setTriggeredAt(now);
        }
        if (alarm.getFrequency() == AlarmFrequency.ONCE) {
            alarm.setStatus(AlarmStatus.TRIGGERED);
        }
        alarmRepository.save(alarm);

        // Pozisyon özetini event.note üzerinden taşı — AlarmNotificationAdapter.buildMarginCallHtml'de
        // "Pozisyon Detayı" satırı olarak gösterilir. (Mevcut note varsa onunla birleştir.)
        String positionDetail = buildPositionDetail(holding);
        String mergedNote = (alarm.getNote() != null && !alarm.getNote().isBlank())
                ? alarm.getNote() + " | " + positionDetail
                : positionDetail;

        AlarmTriggeredEvent event = new AlarmTriggeredEvent(
                alarm.getUserId(), alarm.getRecipientEmail(), alarm.getId(),
                alarm.getAssetType(), alarm.getSymbol(),
                holding.getName() != null ? holding.getName() : alarm.getInstrumentName(),
                alarm.getMetric(), alarm.getDirection(), alarm.getThreshold(), observed, mergedNote);

        notificationPort.notifyAlarmTriggered(event);
        log.info("Margin alarm {} triggered for user {} ({} ratio={} < threshold={})",
                alarm.getId(), alarm.getUserId(), alarm.getSymbol(), observed, alarm.getThreshold());

        // AUDIT log — observability/logging konvansiyonu (CATEGORY_AUDIT + dedicated event type).
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("symbol", alarm.getSymbol());
        meta.put("direction", holding.getViopDirection());
        meta.put("threshold", alarm.getThreshold());
        meta.put("observed", observed);
        meta.put("marginPosted", holding.getViopMarginPosted());
        meta.put("pnl", holding.getProfitLoss());
        meta.put("quantity", holding.getTotalQuantity());
        businessLog.publish(BusinessLogSupport.CATEGORY_AUDIT,
                BusinessLogSupport.EVENT_MARGIN_CALL_TRIGGERED, "WARN",
                "Margin call triggered: " + alarm.getSymbol(),
                BusinessLogSupport.ENTITY_ALARM,
                alarm.getId() != null ? alarm.getId().toString() : null,
                BusinessLogSupport.ACTION_TRIGGER, BusinessLogSupport.RESULT_SUCCESS, meta,
                alarm.getUserId(), MarginAlarmEvaluator.class.getName());
    }

    /**
     * Kullanıcının portföylerinde verilen sembolle eşleşen ilk açık (closed=false) FUTURE holding'i bulur.
     * Aynı sembolde birden fazla portföyde pozisyon varsa ilkini tutar — alarm sembol bazlı olduğu için
     * yön ayrımı için ek bir filtre yapmıyoruz (kullanıcı LONG + SHORT iki ayrı pozisyon için iki ayrı
     * alarm kuracaksa direction bilgisi alarm üzerinde tutulmadığından şu an aynı sembolde tek alarm =
     * tek margin uyarısı semantiği).
     */
    private PortfolioHoldingResponse findOpenFutureHolding(String userId, String symbol) {
        if (userId == null || symbol == null || symbol.isBlank()) {
            return null;
        }
        String target = symbol.trim().toUpperCase(Locale.ROOT);
        List<PortfolioResponse> portfolios;
        try {
            portfolios = portfolioService.getUserPortfolios(userId);
        } catch (Exception ex) {
            log.debug("Margin alarm: portfolios fetch failed for user {}: {}", userId, ex.getMessage());
            return null;
        }
        if (portfolios == null) {
            return null;
        }
        for (PortfolioResponse p : portfolios) {
            try {
                // Cache hit'te enriched yanıt (marginRatio dahil) anında döner; miss'te paralel enrich.
                PortfolioResponse detail = portfolioService.getPortfolioById(userId, p.getId());
                List<PortfolioHoldingResponse> holdings = detail != null ? detail.getHoldings() : null;
                if (holdings == null) {
                    continue;
                }
                for (PortfolioHoldingResponse h : holdings) {
                    if (h.isClosed()) {
                        continue;
                    }
                    if (h.getAssetType() != AssetType.FUTURE) {
                        continue;
                    }
                    if (h.getSymbol() == null) {
                        continue;
                    }
                    if (target.equals(h.getSymbol().trim().toUpperCase(Locale.ROOT))) {
                        return h;
                    }
                }
            } catch (Exception ex) {
                log.debug("Margin alarm: portfolio {} detail fetch failed: {}", p.getId(), ex.getMessage());
            }
        }
        return null;
    }

    /** "LONG 5 lot, giriş 1234,5 → güncel 1180,2 (K/Z -2.715 ₺ teminat 12.300 ₺)" tarzı tek-satır özet. */
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
}
