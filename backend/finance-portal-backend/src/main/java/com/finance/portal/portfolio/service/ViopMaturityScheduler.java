package com.finance.portal.portfolio.service;

import com.finance.portal.admin.application.model.AdminUserView;
import com.finance.portal.admin.application.port.KeycloakUserAdminPort;
import com.finance.portal.common.domain.AssetType;
import com.finance.portal.market.application.viop.ViopChartPeriod;
import com.finance.portal.market.application.viop.ViopChartService;
import com.finance.portal.market.application.viop.ViopService;
import com.finance.portal.market.application.viop.model.ViopChartPoint;
import com.finance.portal.market.application.viop.model.ViopContractDetail;
import com.finance.portal.notification.application.service.NotificationService;
import com.finance.portal.notification.domain.NotificationType;
import com.finance.portal.portfolio.domain.Portfolio;
import com.finance.portal.portfolio.domain.PortfolioTransaction;
import com.finance.portal.portfolio.domain.PortfolioType;
import com.finance.portal.portfolio.domain.TransactionType;
import com.finance.portal.portfolio.repository.PortfolioRepository;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Her gece çalışan otomatik VİOP (FUTURE) vade kapanış görevi.
 *
 * <p>Açık VİOP pozisyonlarının vadesini ({@link ViopService#parseContractMaturity(String)} — kontrat
 * adından, ör. {@code "USDTRY (30 HAZ 26) VADELI"}) kontrol eder; vade dolmuşsa pozisyonu <b>son
 * uzlaşma (settlement) fiyatından otomatik kapatır</b>. Aksi halde kontrat aktif listeden düşünce
 * holding "hayalet" gibi açık kalıp değerlenemiyor ve o ana kadarki K/Z portföyde realized olarak
 * görünmüyordu.
 *
 * <p><b>Yön korunur (LONG/SHORT):</b> Kapanış, pozisyonun <i>kendi yönünü</i> taşıyan bir
 * {@code SELL} işlemi olarak yaratılır. Realized K/Z'yi bu görev HESAPLAMAZ — manuel kapatmayla
 * birebir aynı yol kullanılır: {@code PortfolioHoldingsBuilder} realized'ı
 * {@code spot × multiplier × dirSign} olarak çözer (SHORT → dirSign = −1, yani fiyat düşüşü = kâr).
 * Böylece çarpan ve yön ayrımı dokunulmadan kalır.
 *
 * <p><b>Settlement fiyatı:</b> önce aktif kontrat detayı (vade günü hâlâ listede ise
 * {@code settlementPrice} → {@code lastPrice}), bulunamazsa İş Yatırım grafiğinin son kapanışı.
 * İkisi de yoksa pozisyon açık bırakılır ve kullanıcıya "manuel kapatın" bildirimi düşer
 * (sessiz ghost'tan iyidir).
 *
 * <p>İdempotent: tamamen kapatılmış pozisyonlar ({@code openQty ≤ 0}) atlanır. Cron: her gece
 * 00:35 İstanbul saati; SchedulerLock ile çoklu instance'da tek koşumlu.
 */
@Component
public class ViopMaturityScheduler {

    private static final Logger log = LoggerFactory.getLogger(ViopMaturityScheduler.class);

    private final PortfolioRepository portfolioRepository;
    private final ViopService viopService;
    private final ViopChartService viopChartService;
    private final NotificationService notificationService;
    private final KeycloakUserAdminPort keycloakUserAdminPort;

    public ViopMaturityScheduler(PortfolioRepository portfolioRepository,
                                 ViopService viopService,
                                 ViopChartService viopChartService,
                                 NotificationService notificationService,
                                 KeycloakUserAdminPort keycloakUserAdminPort) {
        this.portfolioRepository = portfolioRepository;
        this.viopService = viopService;
        this.viopChartService = viopChartService;
        this.notificationService = notificationService;
        this.keycloakUserAdminPort = keycloakUserAdminPort;
    }

    @Scheduled(cron = "${portfolio.viop-maturity-cron:0 35 0 * * *}")
    @SchedulerLock(name = "portfolio-viop-maturity", lockAtMostFor = "PT15M", lockAtLeastFor = "PT1M")
    @Transactional
    public void processMaturedFutures() {
        LocalDate today = LocalDate.now();
        log.info("[ViopMaturity] Tarama başladı (today={})", today);

        List<Portfolio> portfolios = portfolioRepository.findAll().stream()
                .filter(p -> p.getPortfolioType() != PortfolioType.WATCHLIST)
                .toList();

        Map<String, String> emailCache = new HashMap<>();
        int closedCount = 0;
        int processedPortfolios = 0;

        for (Portfolio portfolio : portfolios) {
            List<ClosedFuture> closed = new ArrayList<>();
            List<ManualFuture> manual = new ArrayList<>();
            scanAndClose(portfolio, today, closed, manual);

            if (!closed.isEmpty()) {
                portfolioRepository.save(portfolio);
                processedPortfolios++;
                closedCount += closed.size();
            }
            if (!closed.isEmpty() || !manual.isEmpty()) {
                String email = emailCache.computeIfAbsent(portfolio.getUserId(), this::lookupUserEmail);
                sendNotification(portfolio, closed, manual, email);
            }
        }

        log.info("[ViopMaturity] Tarama tamamlandı. portföy={} kapatılan pozisyon={}",
                processedPortfolios, closedCount);
    }

    /** Portföyün açık VİOP pozisyonlarını (sembol + yön bazında) tarar; vadesi dolanları kapatır. */
    private void scanAndClose(Portfolio portfolio, LocalDate today,
                              List<ClosedFuture> closed, List<ManualFuture> manual) {
        // Açık miktar = BUY(+) − SELL(−), (sembol, yön) çiftine göre. LONG ve SHORT ayrı havuzlar.
        Map<PosKey, BigDecimal> openByPos = new HashMap<>();
        for (PortfolioTransaction tx : portfolio.getTransactions()) {
            if (tx.getAssetType() != AssetType.FUTURE) continue;
            TransactionType tt = tx.getTransactionType();
            if (tt != TransactionType.BUY && tt != TransactionType.SELL) continue;
            String symbol = tx.getSymbol();
            if (symbol == null || symbol.isBlank()) continue;
            BigDecimal qty = tx.getQuantity() != null ? tx.getQuantity() : BigDecimal.ZERO;
            BigDecimal signed = tt == TransactionType.BUY ? qty : qty.negate();
            openByPos.merge(new PosKey(symbol, normalizeDir(tx.getDirection())), signed, BigDecimal::add);
        }

        for (Map.Entry<PosKey, BigDecimal> e : openByPos.entrySet()) {
            BigDecimal openQty = e.getValue();
            if (openQty == null || openQty.signum() <= 0) continue; // kapanmış pozisyon → atla (idempotent)
            PosKey pos = e.getKey();

            Optional<LocalDate> maturity = ViopService.parseContractMaturity(pos.symbol());
            if (maturity.isEmpty() || maturity.get().isAfter(today)) {
                continue; // vade bilinmiyor ya da henüz gelmemiş
            }

            BigDecimal settlement = resolveSettlement(pos.symbol());
            if (settlement != null && settlement.signum() > 0) {
                createCloseTransaction(portfolio, pos.symbol(), pos.direction(), openQty, settlement, maturity.get());
                closed.add(new ClosedFuture(pos.symbol(), pos.direction(), openQty, settlement, maturity.get()));
            } else {
                manual.add(new ManualFuture(pos.symbol(), pos.direction(), openQty, maturity.get(),
                        "uzlaşma fiyatı bulunamadı"));
                log.warn("[ViopMaturity] {} (yön {}) vadesi {} doldu ama settlement yok — manuel kapatılmalı",
                        pos.symbol(), pos.direction(), maturity.get());
            }
        }
    }

    /**
     * Settlement fiyatı: (1) aktif kontrat detayı {@code settlementPrice} → {@code lastPrice}
     * (vade günü hâlâ listede), (2) İş Yatırım grafiğinin son kapanışı (kontrat listeden düşmüşse).
     */
    private BigDecimal resolveSettlement(String symbol) {
        try {
            ViopContractDetail d = viopService.getContractDetailCached(symbol);
            if (d != null) {
                if (isPositive(d.getSettlementPrice())) return d.getSettlementPrice();
                if (isPositive(d.getLastPrice())) return d.getLastPrice();
            }
        } catch (Exception ex) {
            log.debug("[ViopMaturity] kontrat detayı alınamadı {}: {}", symbol, ex.getMessage());
        }
        try {
            for (ViopChartPeriod p : List.of(ViopChartPeriod.ONE_MONTH, ViopChartPeriod.THREE_MONTHS, ViopChartPeriod.ONE_WEEK)) {
                BigDecimal last = lastClose(viopChartService.getChart(symbol, p));
                if (isPositive(last)) return last;
            }
        } catch (Exception ex) {
            log.debug("[ViopMaturity] grafik settlement alınamadı {}: {}", symbol, ex.getMessage());
        }
        return null;
    }

    /** Pozisyonun KENDİ yönünü taşıyan SELL → openQty 0'a iner → realized K/Z builder'da yön-duyarlı çözülür. */
    private void createCloseTransaction(Portfolio portfolio, String symbol, String direction,
                                        BigDecimal openQty, BigDecimal settlement, LocalDate maturity) {
        PortfolioTransaction tx = new PortfolioTransaction();
        tx.setSymbol(symbol);
        tx.setAssetType(AssetType.FUTURE);
        tx.setTransactionType(TransactionType.SELL);
        tx.setDirection(direction);
        tx.setQuantity(openQty);
        tx.setPrice(settlement);
        tx.setCommission(BigDecimal.ZERO);
        tx.setTransactionDate(maturity.atStartOfDay());
        portfolio.addTransaction(tx);
        log.info("[ViopMaturity] Otomatik kapanış: portföy={} symbol={} yön={} qty={} settlement={}",
                portfolio.getId(), symbol, direction, openQty.toPlainString(), settlement.toPlainString());
    }

    private void sendNotification(Portfolio portfolio, List<ClosedFuture> closed,
                                  List<ManualFuture> manual, String email) {
        try {
            String title = "VİOP vade kapanışı — " + portfolio.getName();
            StringBuilder body = new StringBuilder();
            if (!closed.isEmpty()) {
                body.append("Vadesi dolan şu VİOP pozisyonları son uzlaşma fiyatından otomatik kapatıldı:\n");
                for (ClosedFuture c : closed) {
                    body.append("• ").append(c.symbol()).append(" (").append(c.direction()).append(") — ")
                            .append(c.qty().toPlainString()).append(" adet, uzlaşma ")
                            .append(c.settlement().toPlainString())
                            .append(" (vade ").append(c.maturity()).append(")\n");
                }
                body.append("Gerçekleşmiş K/Z'iniz portföy özetinde güncel.\n");
            }
            if (!manual.isEmpty()) {
                if (body.length() > 0) body.append('\n');
                body.append("Vadesi doldu ancak uzlaşma fiyatı bulunamadığı için otomatik kapatılamadı (manuel kapatın):\n");
                for (ManualFuture m : manual) {
                    body.append("• ").append(m.symbol()).append(" (").append(m.direction()).append(") — ")
                            .append(m.qty().toPlainString()).append(" adet (").append(m.reason()).append(")\n");
                }
            }
            notificationService.createAndSend(
                    portfolio.getUserId(),
                    NotificationType.PORTFOLIO,
                    title,
                    body.toString(),
                    buildEmailHtml(portfolio, closed, manual),
                    email,
                    null);
        } catch (Exception ex) {
            log.warn("[ViopMaturity] Bildirim gönderilemedi (portföy {}): {}", portfolio.getId(), ex.getMessage());
        }
    }

    private String buildEmailHtml(Portfolio portfolio, List<ClosedFuture> closed, List<ManualFuture> manual) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div style=\"font-family:Arial,sans-serif;max-width:560px;margin:auto;\">");
        sb.append("<h2 style=\"color:#1f2937;\">VİOP vade kapanışı</h2>");
        if (!closed.isEmpty()) {
            sb.append("<p><strong>").append(escape(portfolio.getName())).append("</strong> portföyünüzdeki ")
                    .append("şu vadeli pozisyonların vadesi doldu ve son uzlaşma fiyatından otomatik kapatıldı:</p><ul>");
            for (ClosedFuture c : closed) {
                sb.append("<li><strong>").append(escape(c.symbol())).append("</strong> (")
                        .append(c.direction()).append(") — ").append(c.qty().toPlainString())
                        .append(" adet · uzlaşma ").append(c.settlement().toPlainString())
                        .append(" · vade ").append(c.maturity()).append("</li>");
            }
            sb.append("</ul><p>Gerçekleşmiş K/Z'iniz portföy özetinde güncel.</p>");
        }
        if (!manual.isEmpty()) {
            sb.append("<p style=\"color:#b45309;\">Aşağıdaki pozisyonların vadesi doldu ancak uzlaşma fiyatı ")
                    .append("bulunamadığı için otomatik kapatılamadı — manuel kapatın:</p><ul>");
            for (ManualFuture m : manual) {
                sb.append("<li><strong>").append(escape(m.symbol())).append("</strong> (")
                        .append(m.direction()).append(") — ").append(m.qty().toPlainString()).append(" adet</li>");
            }
            sb.append("</ul>");
        }
        sb.append("</div>");
        return sb.toString();
    }

    private String lookupUserEmail(String userId) {
        try {
            AdminUserView u = keycloakUserAdminPort.getUser(userId);
            return u != null && u.isEmailVerified() ? u.getEmail() : null;
        } catch (Exception ex) {
            log.debug("[ViopMaturity] {} kullanıcı bilgisi alınamadı: {}", userId, ex.getMessage());
            return null;
        }
    }

    private static String normalizeDir(String d) {
        if (d == null || d.isBlank()) return "LONG";
        return "SHORT".equalsIgnoreCase(d.trim()) ? "SHORT" : "LONG";
    }

    private static boolean isPositive(BigDecimal v) {
        return v != null && v.signum() > 0;
    }

    private static BigDecimal lastClose(List<ViopChartPoint> pts) {
        if (pts == null || pts.isEmpty()) return null;
        ViopChartPoint best = null;
        for (ViopChartPoint pt : pts) {
            if (pt.getValue() == null || pt.getTimestamp() == null) continue;
            if (best == null || pt.getTimestamp() > best.getTimestamp()) best = pt;
        }
        return best != null ? best.getValue() : null;
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private record PosKey(String symbol, String direction) {}
    private record ClosedFuture(String symbol, String direction, BigDecimal qty, BigDecimal settlement, LocalDate maturity) {}
    private record ManualFuture(String symbol, String direction, BigDecimal qty, LocalDate maturity, String reason) {}
}
