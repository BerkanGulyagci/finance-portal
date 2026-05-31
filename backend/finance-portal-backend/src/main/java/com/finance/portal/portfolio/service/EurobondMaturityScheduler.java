package com.finance.portal.portfolio.service;

import com.finance.portal.admin.application.model.AdminUserView;
import com.finance.portal.admin.application.port.KeycloakUserAdminPort;
import com.finance.portal.common.domain.AssetType;
import com.finance.portal.market.application.bond.eurobond.EurobondService;
import com.finance.portal.market.application.bond.eurobond.model.EurobondDetail;
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
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Her gece çalışan otomatik Eurobond itfa görevi.
 *
 * <p>{@link BondMaturityScheduler} sadece TCMB EVDS iç-piyasa DİBS'lerini kapatır;
 * bu görev HMB ISIN listesindeki (Hazine dış borç) eurobondların vade ödemesini yapar:
 * <ul>
 *   <li>Vade fiyatı = par (100, kote para birimi). TL karşılığı vade gününde Business
 *       Insider quote pipeline'ı zaten canlı TCMB kuru ile çarpıyor — yani
 *       {@code EurobondDetail.lastPriceTry} pratik olarak {@code par × currentFxToTry / 1}
 *       seviyesinde olur. Realized hesabını {@code PortfolioHoldingsBuilder /100} ile
 *       tutarlı tutmak için fiyatı <b>par × fxRate</b> = {@code 100 × fxRate} olarak
 *       yazarız → realized = qty × 100 × fxRate / 100 = qty × fxRate (Model 1, cost
 *       basis tarihsel TL ile aynı konvansiyon).</li>
 *   <li>Eğer BI detayında final kupon kaydedilebilirse {@code COUPON_INCOME} eklenir.
 *       Konvansiyon {@code quantity = TL kupon tutarı, price = 1}.</li>
 * </ul>
 *
 * <p>Cron: 00:35 İstanbul (BondMaturityScheduler'dan 5 dk sonra; lock çakışmasını önler).
 */
@Component
public class EurobondMaturityScheduler {

    private static final Logger log = LoggerFactory.getLogger(EurobondMaturityScheduler.class);

    private static final BigDecimal PAR = new BigDecimal("100");

    /** TCMB konvansiyonu: yarı yıllık. EurobondDetail.paymentsPerYear "1,0"/"2,0" gibi gelir. */
    private static final int DEFAULT_PAYMENTS_PER_YEAR = 2;

    /** BI detayında maturityDate "M/d/yyyy" formatında string olarak gelir. */
    private static final DateTimeFormatter BI_DATE_FMT = DateTimeFormatter.ofPattern("M/d/yyyy", Locale.US);

    /** Eurobond ISIN prefix'leri (HMB listesi dışı yedek tanı için). */
    private static final Set<String> EUROBOND_ISIN_PREFIXES = Set.of("US", "XS", "EUO");

    private final PortfolioRepository portfolioRepository;
    private final EurobondService eurobondService;
    private final NotificationService notificationService;
    private final KeycloakUserAdminPort keycloakUserAdminPort;

    public EurobondMaturityScheduler(PortfolioRepository portfolioRepository,
                                     EurobondService eurobondService,
                                     NotificationService notificationService,
                                     KeycloakUserAdminPort keycloakUserAdminPort) {
        this.portfolioRepository = portfolioRepository;
        this.eurobondService = eurobondService;
        this.notificationService = notificationService;
        this.keycloakUserAdminPort = keycloakUserAdminPort;
    }

    @Scheduled(cron = "${portfolio.eurobond-maturity-cron:0 35 0 * * *}")
    @SchedulerLock(name = "portfolio-eurobond-maturity", lockAtMostFor = "PT15M", lockAtLeastFor = "PT1M")
    @Transactional
    public void processMaturedEurobonds() {
        LocalDate today = LocalDate.now();
        log.info("[EurobondMaturity] Tarama başladı (today={})", today);

        List<Portfolio> portfolios = portfolioRepository.findAll().stream()
                .filter(p -> p.getPortfolioType() != PortfolioType.WATCHLIST)
                .toList();

        Set<String> isinSet = safeCurrentIsins();
        Map<String, EurobondDetail> detailCache = new HashMap<>();
        Map<String, String> emailCache = new HashMap<>();
        int processedHoldings = 0;
        int processedPortfolios = 0;

        for (Portfolio portfolio : portfolios) {
            List<MaturedEurobond> matured = findMaturedEurobonds(portfolio, today, isinSet, detailCache);
            if (matured.isEmpty()) continue;

            List<MaturedEurobond> closed = new ArrayList<>();
            for (MaturedEurobond me : matured) {
                if (createItfaTransactions(portfolio, me)) {
                    closed.add(me);
                    processedHoldings++;
                }
            }
            if (!closed.isEmpty()) {
                portfolioRepository.save(portfolio);
                processedPortfolios++;
                String email = emailCache.computeIfAbsent(portfolio.getUserId(), this::lookupUserEmail);
                sendNotification(portfolio, closed, email);
            }
        }
        log.info("[EurobondMaturity] Tarama tamamlandı. portföy={} itfa edilen={}",
                processedPortfolios, processedHoldings);
    }

    /** Portföydeki açık eurobond pozisyonlarından vadesi gelmiş olanları döndürür. */
    private List<MaturedEurobond> findMaturedEurobonds(Portfolio portfolio,
                                                       LocalDate today,
                                                       Set<String> isinSet,
                                                       Map<String, EurobondDetail> detailCache) {
        Map<String, BigDecimal> openQtyBySymbol = new HashMap<>();
        for (PortfolioTransaction tx : portfolio.getTransactions()) {
            if (tx.getAssetType() != AssetType.BOND) continue;
            if (tx.getTransactionType() == TransactionType.COUPON_INCOME) continue;
            String symbol = tx.getSymbol();
            if (symbol == null) continue;
            if (!looksLikeEurobond(symbol, isinSet)) continue;
            BigDecimal qty = tx.getQuantity() != null ? tx.getQuantity() : BigDecimal.ZERO;
            BigDecimal signed = tx.getTransactionType() == TransactionType.BUY ? qty : qty.negate();
            openQtyBySymbol.merge(symbol, signed, BigDecimal::add);
        }

        List<MaturedEurobond> out = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> e : openQtyBySymbol.entrySet()) {
            BigDecimal openQty = e.getValue();
            if (openQty == null || openQty.signum() <= 0) continue;

            String symbol = e.getKey();
            EurobondDetail d = detailCache.computeIfAbsent(symbol, this::lookupDetail);
            if (d == null) continue;

            LocalDate maturity = parseDate(d.getMaturityDate());
            if (maturity == null) continue;
            if (maturity.isAfter(today)) continue;

            out.add(new MaturedEurobond(
                    symbol,
                    openQty,
                    maturity,
                    d.getCurrency(),
                    d.getFxRate(),
                    d.getLastPriceTry(),
                    parsePercent(d.getCouponRate()),
                    parsePaymentsPerYear(d.getPaymentsPerYear()),
                    parseDate(d.getFinalCouponDate())));
        }
        return out;
    }

    /**
     * Eurobond için SELL @ par-in-TL işlemini (+ varsa son kupon) ekler. Fiyat
     * çözülemezse (FX null) işlem atlanır → kullanıcı manuel kapatır.
     *
     * @return true → SELL eklendi.
     */
    private boolean createItfaTransactions(Portfolio portfolio, MaturedEurobond me) {
        // TL fiyat: par × canlı FX kuru. EurobondDetail.lastPriceTry zaten "lastPrice × fxRate",
        // burada "100 × fxRate" istiyoruz; quote fluktüasyonuna bağlı kalmadan par üzerinden hesap.
        BigDecimal fx = me.fxRate();
        if (fx == null || fx.signum() <= 0) {
            // Yedek: TRY denomination için 1.0 say (lastPriceTry zaten TL).
            if ("TRY".equalsIgnoreCase(me.currency())) {
                fx = BigDecimal.ONE;
            } else {
                log.warn("[EurobondMaturity] {} FX kuru alınamadı — itfa atlandı (manuel kapatın)", me.symbol);
                return false;
            }
        }
        BigDecimal priceTl = PAR.multiply(fx).setScale(8, RoundingMode.HALF_UP);

        PortfolioTransaction tx = new PortfolioTransaction();
        tx.setSymbol(me.symbol);
        tx.setAssetType(AssetType.BOND);
        tx.setTransactionType(TransactionType.SELL);
        tx.setQuantity(me.openQty);
        tx.setPrice(priceTl);
        tx.setCommission(BigDecimal.ZERO);
        tx.setTransactionDate(me.maturityDate.atStartOfDay());
        portfolio.addTransaction(tx);

        log.info("[EurobondMaturity] Otomatik itfa: portföy={} ISIN={} nominal={} fiyatTL={} vade={}",
                portfolio.getId(), me.symbol, me.openQty, priceTl, me.maturityDate);

        BigDecimal coupon = computeFinalCouponTl(me, fx);
        if (coupon != null && coupon.signum() > 0) {
            PortfolioTransaction cx = new PortfolioTransaction();
            cx.setSymbol(me.symbol);
            cx.setAssetType(AssetType.BOND);
            cx.setTransactionType(TransactionType.COUPON_INCOME);
            cx.setQuantity(coupon);
            cx.setPrice(BigDecimal.ONE);
            cx.setCommission(BigDecimal.ZERO);
            cx.setTransactionDate(me.maturityDate.atStartOfDay());
            portfolio.addTransaction(cx);
            log.info("[EurobondMaturity] Son kupon: portföy={} ISIN={} tutar={} TL",
                    portfolio.getId(), me.symbol, coupon);
        }
        return true;
    }

    /**
     * Vade günü son kupon TL tutarı: nominal × (couponRate/100) / paymentsPerYear × FX.
     * BI {@code finalCouponDate} bugünden farklıysa (önceki dönemlerde ödendiyse) null döner.
     */
    private BigDecimal computeFinalCouponTl(MaturedEurobond me, BigDecimal fx) {
        if (me.couponPct() == null || me.couponPct().signum() <= 0) return null;
        int freq = me.paymentsPerYear() > 0 ? me.paymentsPerYear() : DEFAULT_PAYMENTS_PER_YEAR;
        // finalCouponDate >= maturityDate ise vade gününde ödenecek — yoksa skip
        if (me.finalCouponDate() != null && me.finalCouponDate().isBefore(me.maturityDate)) {
            return null;
        }
        BigDecimal couponNative = me.openQty
                .multiply(me.couponPct())
                .divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP)
                .divide(BigDecimal.valueOf(freq), 8, RoundingMode.HALF_UP);
        return couponNative.multiply(fx).setScale(4, RoundingMode.HALF_UP);
    }

    private void sendNotification(Portfolio portfolio, List<MaturedEurobond> closed, String email) {
        try {
            String title = "Eurobond vade itfası — " + portfolio.getName();
            StringBuilder body = new StringBuilder();
            body.append("Portföyünüzdeki şu eurobond pozisyonlarının vadesi geldi ve otomatik kapatıldı:\n");
            for (MaturedEurobond me : closed) {
                body.append("• ").append(me.symbol)
                        .append(" — ").append(me.openQty.toPlainString()).append(" nominal ")
                        .append(me.currency() != null ? me.currency() : "")
                        .append(" (vade ").append(me.maturityDate).append(")\n");
                BigDecimal coupon = computeFinalCouponTl(me, me.fxRate() != null ? me.fxRate() : BigDecimal.ONE);
                if (coupon != null && coupon.signum() > 0) {
                    body.append("    + son kupon: ").append(coupon.toPlainString()).append(" TL\n");
                }
            }
            body.append("\nGerçekleşmiş K/Z'iniz portföy özetinde güncel.");

            String html = buildEmailHtml(portfolio, closed);
            notificationService.createAndSend(
                    portfolio.getUserId(),
                    NotificationType.PORTFOLIO,
                    title,
                    body.toString(),
                    html,
                    email,
                    null);
        } catch (Exception ex) {
            log.warn("[EurobondMaturity] Bildirim gönderilemedi (portföy {}): {}",
                    portfolio.getId(), ex.getMessage());
        }
    }

    private String buildEmailHtml(Portfolio portfolio, List<MaturedEurobond> closed) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div style=\"font-family:Arial,sans-serif;max-width:560px;margin:auto;\">");
        sb.append("<h2 style=\"color:#1f2937;\">Eurobond vade itfası</h2>");
        sb.append("<p><strong>").append(escape(portfolio.getName())).append("</strong> portföyünüzdeki ")
                .append("şu eurobondların vadesi geldi ve otomatik kapatıldı:</p>");
        sb.append("<ul>");
        for (MaturedEurobond me : closed) {
            sb.append("<li><strong>").append(escape(me.symbol)).append("</strong> — ")
                    .append(me.openQty.toPlainString()).append(" nominal ")
                    .append(escape(me.currency() != null ? me.currency() : ""))
                    .append(", vade ").append(me.maturityDate);
            BigDecimal coupon = computeFinalCouponTl(me, me.fxRate() != null ? me.fxRate() : BigDecimal.ONE);
            if (coupon != null && coupon.signum() > 0) {
                sb.append("<br><span style=\"color:#16a34a;\">+ son kupon: ")
                        .append(coupon.toPlainString()).append(" TL</span>");
            }
            sb.append("</li>");
        }
        sb.append("</ul>");
        sb.append("<p style=\"color:#6b7280;font-size:12px;\">Itfa fiyatı: par (kote 100), TL karşılığı ")
                .append("canlı TCMB satış kuru ile yazılır (Model 1: cost basis tarihsel TL kuru ile aynı ")
                .append("konvansiyonda). Gerçekleşmiş K/Z portföy özetinde günceldir.</p>");
        sb.append("</div>");
        return sb.toString();
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private boolean looksLikeEurobond(String symbol, Set<String> isinSet) {
        if (symbol == null) return false;
        String up = symbol.trim().toUpperCase(Locale.ROOT);
        if (isinSet.contains(up)) return true;
        for (String p : EUROBOND_ISIN_PREFIXES) {
            if (up.startsWith(p)) return true;
        }
        return false;
    }

    private EurobondDetail lookupDetail(String isin) {
        try {
            return eurobondService.detail(isin);
        } catch (Exception ex) {
            log.debug("[EurobondMaturity] {} detail alınamadı: {}", isin, ex.getMessage());
            return null;
        }
    }

    private Set<String> safeCurrentIsins() {
        try {
            List<String> isins = eurobondService.currentIsins();
            return isins != null ? new HashSet<>(isins) : Set.of();
        } catch (Exception ex) {
            log.warn("[EurobondMaturity] ISIN listesi alınamadı: {}", ex.getMessage());
            return Set.of();
        }
    }

    /** BI {@code "M/d/yyyy"} → {@link LocalDate}. Hata/boş → null. */
    private static LocalDate parseDate(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return LocalDate.parse(s.trim(), BI_DATE_FMT);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    /** BI {@code "5.200%"} → 5.20. Hata/boş → null. */
    private static BigDecimal parsePercent(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            String cleaned = s.replace("%", "").replace(",", ".").trim();
            if (cleaned.isEmpty()) return null;
            return new BigDecimal(cleaned);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /** BI {@code "2,0"} / {@code "1,0"} → int. Hata/boş → 0. */
    private static int parsePaymentsPerYear(String s) {
        if (s == null || s.isBlank()) return 0;
        try {
            String cleaned = s.replace(",", ".").trim();
            return new BigDecimal(cleaned).intValue();
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private String lookupUserEmail(String userId) {
        try {
            AdminUserView u = keycloakUserAdminPort.getUser(userId);
            return u != null && u.isEmailVerified() ? u.getEmail() : null;
        } catch (Exception ex) {
            log.debug("[EurobondMaturity] {} kullanıcı bilgisi alınamadı: {}", userId, ex.getMessage());
            return null;
        }
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** Vadesi gelmiş açık Eurobond pozisyonu (taşıyıcı). */
    private record MaturedEurobond(String symbol, BigDecimal openQty, LocalDate maturityDate,
                                   String currency, BigDecimal fxRate, BigDecimal lastPriceTry,
                                   BigDecimal couponPct, int paymentsPerYear, LocalDate finalCouponDate) {}
}
