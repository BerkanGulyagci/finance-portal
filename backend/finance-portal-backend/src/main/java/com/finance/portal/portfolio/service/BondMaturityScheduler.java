package com.finance.portal.portfolio.service;

import com.finance.portal.admin.application.model.AdminUserView;
import com.finance.portal.admin.application.port.KeycloakUserAdminPort;
import com.finance.portal.common.domain.AssetType;
import com.finance.portal.market.application.bond.eurobond.EurobondService;
import com.finance.portal.market.application.bond.evds.EvdsBondInstrument;
import com.finance.portal.market.application.bond.evds.EvdsBondService;
import com.finance.portal.market.application.bond.evds.model.BondCategory;
import com.finance.portal.market.application.gold.GoldMarketService;
import com.finance.portal.market.application.gold.GoldSpotResponse;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Her gece çalışan otomatik DİBS itfa görevi.
 *
 * <p>Açık BOND pozisyonlarının vade tarihini ({@link EvdsBondInstrument#getMaturityDate()})
 * kontrol eder; vade dolmuşsa otomatik {@code SELL} işlemi oluşturur. Itfa fiyatı
 * {@link BondCategory} ailesine göre belirlenir:
 * <ul>
 *   <li><b>Par (100 TL/nominal)</b>: kuponsuz/sabit-kupon TL bondları, TLREF-endeksli,
 *       FX-cinsli, standart kira sertifikası, ana para stripi.</li>
 *   <li><b>EVDS son Gösterge Değeri</b>: TÜFE-endeksli aile + COUPON_STRIP (TÜFE kümülatif
 *       çarpan ve tek kupon ödemesi gösterge değerinin içinde).</li>
 *   <li><b>Canlı gram altın TL fiyatı</b>: Altın-endeksli senet ve altın kira sertifikası
 *       (birim = 1 gr has altın).</li>
 * </ul>
 *
 * <p>Kuponlu kategoriler (FIXED_COUPON_BOND / TLREF_INDEXED_BOND / LEASE_CERTIFICATE)
 * için ayrıca bir {@link TransactionType#COUPON_INCOME} işlemi yaratılır — vade gününde
 * ödenecek son kupon dilimi. Konvansiyon: {@code quantity = TL kupon tutarı, price = 1}
 * (aynen {@code PortfolioServiceImpl.addCouponIncome} ile).
 *
 * <p>Eurobondlar bu görevde KAPSANMAZ; HMB ISIN listesindeki sembol görünürse SELL
 * yaratılmaz, kullanıcıya "manuel kapatın" bildirimi düşer.
 * Tam otomatik Eurobond itfa için {@link EurobondMaturityScheduler}.
 *
 * <p>Cron: her gece 00:30 İstanbul saati. SchedulerLock ile çoklu instance'da tek koşumlu.
 */
@Component
public class BondMaturityScheduler {

    private static final Logger log = LoggerFactory.getLogger(BondMaturityScheduler.class);

    /** Vade gününde ödenen tutar TCMB konvansiyonunda 100 TL nominal başına 100 TL. */
    private static final BigDecimal PAR_PRICE = new BigDecimal("100");

    /**
     * Kupon frekansı yedeği: TCMB konvansiyonu yarı yıllık (2/yıl). EvdsBondInstrument'ta
     * paymentsPerYear alanı bulunmadığı için varsayılan olarak kullanılır.
     */
    private static final int DEFAULT_COUPON_PAYMENTS_PER_YEAR = 2;

    /** Par (100) ödeme yapan kategoriler. */
    private static final Set<BondCategory> PAR_CATEGORIES = Set.of(
            BondCategory.ZERO_COUPON_BOND,
            BondCategory.ZERO_COUPON_BILL,
            BondCategory.PRINCIPAL_STRIP,
            BondCategory.FIXED_COUPON_BOND,
            BondCategory.TLREF_INDEXED_BOND,
            BondCategory.LEASE_CERTIFICATE,
            BondCategory.FX_DENOMINATED_BOND,
            BondCategory.FX_LEASE_CERTIFICATE
    );

    /** EVDS "son Gösterge Değeri" ile ödenen TÜFE-endeksli/kupon stripi ailesi. */
    private static final Set<BondCategory> INDICATOR_CATEGORIES = Set.of(
            BondCategory.COUPON_STRIP,
            BondCategory.INFLATION_COUPON_STRIP,
            BondCategory.INFLATION_INDEXED_BOND,
            BondCategory.INFLATION_PRINCIPAL_STRIP,
            BondCategory.INFLATION_INDEXED_LEASE_CERTIFICATE
    );

    /** Canlı gram altın TL fiyatı ile ödenen kategoriler. */
    private static final Set<BondCategory> GRAM_GOLD_CATEGORIES = Set.of(
            BondCategory.GOLD_INDEXED_BOND,
            BondCategory.GOLD_INDEXED_LEASE_CERTIFICATE
    );

    /** Vade gününde ek olarak son kupon ödemesi yapılan kategoriler. */
    private static final Set<BondCategory> FINAL_COUPON_CATEGORIES = Set.of(
            BondCategory.FIXED_COUPON_BOND,
            BondCategory.TLREF_INDEXED_BOND,
            BondCategory.LEASE_CERTIFICATE
    );

    private final PortfolioRepository portfolioRepository;
    private final EvdsBondService evdsBondService;
    private final EurobondService eurobondService;
    private final GoldMarketService goldMarketService;
    private final NotificationService notificationService;
    private final KeycloakUserAdminPort keycloakUserAdminPort;

    public BondMaturityScheduler(PortfolioRepository portfolioRepository,
                                 EvdsBondService evdsBondService,
                                 EurobondService eurobondService,
                                 GoldMarketService goldMarketService,
                                 NotificationService notificationService,
                                 KeycloakUserAdminPort keycloakUserAdminPort) {
        this.portfolioRepository = portfolioRepository;
        this.evdsBondService = evdsBondService;
        this.eurobondService = eurobondService;
        this.goldMarketService = goldMarketService;
        this.notificationService = notificationService;
        this.keycloakUserAdminPort = keycloakUserAdminPort;
    }

    @Scheduled(cron = "${portfolio.bond-maturity-cron:0 30 0 * * *}")
    @SchedulerLock(name = "portfolio-bond-maturity", lockAtMostFor = "PT15M", lockAtLeastFor = "PT1M")
    @Transactional
    public void processMaturedBonds() {
        LocalDate today = LocalDate.now();
        log.info("[BondMaturity] Tarama başladı (today={})", today);

        List<Portfolio> portfolios = portfolioRepository.findAll().stream()
                .filter(p -> p.getPortfolioType() != PortfolioType.WATCHLIST)
                .toList();

        // Eurobond ISIN seti tek seferlik (cron başında snapshot) — HMB ISIN listesini sembol-bazlı filtre
        // olarak kullanırız; aksi durumda her holding-iterasyonunda servis çağrısı olurdu.
        List<String> eurobondIsinList = safeCurrentIsins();
        Set<String> eurobondIsins = new java.util.HashSet<>(eurobondIsinList);
        log.debug("[BondMaturity] Eurobond ISIN sayısı (skip-set): {}", eurobondIsins.size());

        Map<String, EvdsBondInstrument> bondCache = new HashMap<>();
        Map<String, String> emailCache = new HashMap<>();
        // Gram altın TL canlı fiyatı — varsa cron başında bir kez çekilir, GRAM_GOLD kategorilerinde kullanılır.
        BigDecimal goldGramTry = lookupGoldGramTry();
        int processedHoldings = 0;
        int processedPortfolios = 0;

        for (Portfolio portfolio : portfolios) {
            ScanResult scan = findMaturedBondHoldings(portfolio, today, bondCache, eurobondIsins);
            if (scan.isEmpty()) {
                continue;
            }

            List<MaturedHolding> closed = new ArrayList<>();
            for (MaturedHolding mh : scan.matured) {
                boolean ok = createItfaTransaction(portfolio, mh, goldGramTry);
                if (ok) {
                    closed.add(mh);
                    processedHoldings++;
                }
            }
            if (!closed.isEmpty() || !scan.manualClose.isEmpty()) {
                if (!closed.isEmpty()) {
                    portfolioRepository.save(portfolio);
                    processedPortfolios++;
                }
                String email = emailCache.computeIfAbsent(portfolio.getUserId(), this::lookupUserEmail);
                sendItfaNotification(portfolio, closed, scan.manualClose, email);
            }
        }

        log.info("[BondMaturity] Tarama tamamlandı. portföy={} itfa edilen holding={}",
                processedPortfolios, processedHoldings);
    }

    /** Bir portföyün açık BOND pozisyonlarından bugün vadesi gelmiş olanları döndürür. */
    private ScanResult findMaturedBondHoldings(Portfolio portfolio,
                                                LocalDate today,
                                                Map<String, EvdsBondInstrument> bondCache,
                                                Set<String> eurobondIsins) {
        Map<String, BigDecimal> openQtyBySymbol = new HashMap<>();
        for (PortfolioTransaction tx : portfolio.getTransactions()) {
            if (tx.getAssetType() != AssetType.BOND) continue;
            if (tx.getTransactionType() == TransactionType.COUPON_INCOME) continue; // kupon TL nakit girişi, qty ≠ nominal
            String symbol = tx.getSymbol();
            if (symbol == null) continue;
            BigDecimal qty = tx.getQuantity() != null ? tx.getQuantity() : BigDecimal.ZERO;
            BigDecimal signed = tx.getTransactionType() == TransactionType.BUY ? qty : qty.negate();
            openQtyBySymbol.merge(symbol, signed, BigDecimal::add);
        }

        List<MaturedHolding> out = new ArrayList<>();
        List<ManualCloseHolding> manual = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> e : openQtyBySymbol.entrySet()) {
            BigDecimal openQty = e.getValue();
            if (openQty == null || openQty.signum() <= 0) continue;

            String symbol = e.getKey();

            // Eurobond ISIN'i → bu görevde SELL yaratılmaz; ayrı bir bildirim listesine düşer ve
            // EurobondMaturityScheduler tam otomatik kapatır. Buradaki amaç "ghost'lanmasın".
            if (eurobondIsins.contains(symbol)) {
                manual.add(new ManualCloseHolding(symbol, openQty,
                        "Eurobond — otomatik kapanış EurobondMaturityScheduler tarafından yapılır"));
                continue;
            }

            EvdsBondInstrument bond = bondCache.computeIfAbsent(symbol, this::lookupBond);
            if (bond == null || bond.getMaturityDate() == null) continue;

            // Bugün veya daha eski vade → itfa et
            if (!bond.getMaturityDate().isAfter(today)) {
                out.add(new MaturedHolding(
                        symbol,
                        openQty,
                        bond.getMaturityDate(),
                        bond.getInstrumentCode(),
                        bond.getCategory() != null ? bond.getCategory() : BondCategory.UNKNOWN,
                        bond.getIndicatorValue(),
                        bond.getCouponRate()));
            }
        }
        return new ScanResult(out, manual);
    }

    /**
     * Kategori-aware SELL işlemi (gerekirse ayrıca COUPON_INCOME) yaratır ve portföye ekler.
     * Fiyat çözülemezse (örn. INDICATOR kategorisinde indicatorValue null) işlem atlanır,
     * holding daha sonra kullanıcı tarafından manuel kapatılır.
     *
     * @return true → SELL eklendi; false → atlandı.
     */
    private boolean createItfaTransaction(Portfolio portfolio, MaturedHolding mh, BigDecimal goldGramTry) {
        BondCategory cat = mh.category();
        BigDecimal price;

        if (GRAM_GOLD_CATEGORIES.contains(cat)) {
            if (goldGramTry == null || goldGramTry.signum() <= 0) {
                log.warn("[BondMaturity] {} altın gram TL alınamadı — itfa atlandı (manuel kapatılmalı)",
                        mh.symbol);
                return false;
            }
            price = goldGramTry;
        } else if (INDICATOR_CATEGORIES.contains(cat)) {
            if (mh.lastIndicator() == null || mh.lastIndicator().signum() <= 0) {
                log.warn("[BondMaturity] {} EVDS gösterge değeri boş — TÜFE-endeksli itfa atlandı",
                        mh.symbol);
                return false;
            }
            price = mh.lastIndicator();
        } else if (PAR_CATEGORIES.contains(cat)) {
            price = PAR_PRICE;
        } else {
            log.warn("[BondMaturity] {} kategori={} eşleşmedi — fallback par (100 TL)", mh.symbol, cat);
            price = PAR_PRICE;
        }

        PortfolioTransaction tx = new PortfolioTransaction();
        tx.setSymbol(mh.symbol);
        tx.setAssetType(AssetType.BOND);
        tx.setTransactionType(TransactionType.SELL);
        tx.setQuantity(mh.openQty);
        tx.setPrice(price);
        tx.setCommission(BigDecimal.ZERO);
        // İtfa tarihi vade günü 00:00 (today'den geç olabilir; vade günündeki tarihi kullan)
        tx.setTransactionDate(mh.maturityDate.atStartOfDay());
        portfolio.addTransaction(tx);

        log.info("[BondMaturity] Otomatik itfa: portföy={} symbol={} kategori={} nominal={} fiyat={} vade={}",
                portfolio.getId(), mh.symbol, cat, mh.openQty, price, mh.maturityDate);

        // Vade gününde son kupon ödemesi (FIXED_COUPON_BOND / TLREF / LEASE_CERTIFICATE)
        BigDecimal couponAmount = computeFinalCouponTl(mh);
        if (couponAmount != null && couponAmount.signum() > 0) {
            emitFinalCouponTx(portfolio, mh, couponAmount);
        }

        return true;
    }

    /**
     * Vade günü ödenecek son kupon dilimi (TL). FIXED_COUPON_BOND / TLREF_INDEXED_BOND /
     * LEASE_CERTIFICATE haricinde sıfır/null döner.
     *
     * <p>Formül: {@code nominal × (couponRate/100) / paymentsPerYear}.
     * {@code paymentsPerYear} yedeği {@link #DEFAULT_COUPON_PAYMENTS_PER_YEAR} (TCMB yarı yıllık
     * konvansiyon). EvdsBondInstrument'ta frekans alanı yoktur — bu yaklaşım dokümante edildi.
     */
    private BigDecimal computeFinalCouponTl(MaturedHolding mh) {
        if (!FINAL_COUPON_CATEGORIES.contains(mh.category())) return null;
        BigDecimal rate = mh.couponRate();
        if (rate == null || rate.signum() <= 0) return null;
        return mh.openQty
                .multiply(rate)
                .divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP)
                .divide(BigDecimal.valueOf(DEFAULT_COUPON_PAYMENTS_PER_YEAR), 4, RoundingMode.HALF_UP);
    }

    /**
     * COUPON_INCOME işlemini ekler. {@code PortfolioServiceImpl.addCouponIncome} ile aynı
     * konvansiyon: {@code quantity = TL kupon tutarı, price = 1, symbol = bondSymbol}.
     */
    private void emitFinalCouponTx(Portfolio portfolio, MaturedHolding mh, BigDecimal amountTl) {
        PortfolioTransaction cx = new PortfolioTransaction();
        cx.setSymbol(mh.symbol);
        cx.setAssetType(AssetType.BOND);
        cx.setTransactionType(TransactionType.COUPON_INCOME);
        cx.setQuantity(amountTl);
        cx.setPrice(BigDecimal.ONE);
        cx.setCommission(BigDecimal.ZERO);
        cx.setTransactionDate(mh.maturityDate.atStartOfDay());
        portfolio.addTransaction(cx);
        log.info("[BondMaturity] Son kupon: portföy={} symbol={} tutar={} TL", portfolio.getId(), mh.symbol, amountTl);
    }

    /** Kullanıcıya in-app + e-posta bildirimi gönder. Sessizce hata yakalar. */
    private void sendItfaNotification(Portfolio portfolio,
                                       List<MaturedHolding> matured,
                                       List<ManualCloseHolding> manualClose,
                                       String email) {
        try {
            String title = "DİBS vade itfası — " + portfolio.getName();
            StringBuilder body = new StringBuilder();
            if (!matured.isEmpty()) {
                body.append("Portföyünüzdeki şu DİBS pozisyonlarının vadesi geldi ve otomatik kapatıldı:\n");
                for (MaturedHolding mh : matured) {
                    BigDecimal coupon = computeFinalCouponTl(mh);
                    body.append("• ").append(mh.symbol)
                            .append(" — ").append(mh.openQty.toPlainString()).append(" nominal")
                            .append(" (vade ").append(mh.maturityDate).append(", kategori ")
                            .append(mh.category()).append(")\n");
                    if (coupon != null && coupon.signum() > 0) {
                        body.append("    + son kupon: ").append(coupon.toPlainString()).append(" TL\n");
                    }
                }
            }
            if (!manualClose.isEmpty()) {
                if (body.length() > 0) body.append("\n");
                body.append("Aşağıdaki pozisyonların vadesi geldi ancak otomatik kapatılmadı (manuel kapatın):\n");
                for (ManualCloseHolding mc : manualClose) {
                    body.append("• ").append(mc.symbol)
                            .append(" — ").append(mc.openQty.toPlainString()).append(" nominal")
                            .append(" (").append(mc.reason).append(")\n");
                }
            }
            body.append("\nGerçekleşmiş K/Z'iniz portföy özetinde güncel.");

            String html = buildEmailHtml(portfolio, matured, manualClose);
            notificationService.createAndSend(
                    portfolio.getUserId(),
                    NotificationType.PORTFOLIO,
                    title,
                    body.toString(),
                    html,
                    email,
                    null);
        } catch (Exception ex) {
            log.warn("[BondMaturity] Bildirim gönderilemedi (portföy {}): {}",
                    portfolio.getId(), ex.getMessage());
        }
    }

    private String buildEmailHtml(Portfolio portfolio,
                                   List<MaturedHolding> matured,
                                   List<ManualCloseHolding> manualClose) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div style=\"font-family:Arial,sans-serif;max-width:560px;margin:auto;\">");
        sb.append("<h2 style=\"color:#1f2937;\">DİBS vade itfası</h2>");
        if (!matured.isEmpty()) {
            sb.append("<p><strong>").append(escape(portfolio.getName())).append("</strong> portföyünüzdeki ")
                    .append("şu pozisyonların vadesi geldi ve otomatik kapatıldı:</p>");
            sb.append("<ul>");
            for (MaturedHolding mh : matured) {
                sb.append("<li><strong>").append(escape(mh.symbol)).append("</strong> — ")
                        .append(mh.openQty.toPlainString()).append(" nominal, vade ")
                        .append(mh.maturityDate)
                        .append(" (kategori ").append(mh.category()).append(")");
                BigDecimal coupon = computeFinalCouponTl(mh);
                if (coupon != null && coupon.signum() > 0) {
                    sb.append("<br><span style=\"color:#16a34a;\">+ son kupon: ")
                            .append(coupon.toPlainString()).append(" TL</span>");
                }
                sb.append("</li>");
            }
            sb.append("</ul>");
        }
        if (!manualClose.isEmpty()) {
            sb.append("<p style=\"color:#b45309;\">Aşağıdaki pozisyonların vadesi geldi ancak ")
                    .append("otomatik kapatılmadı — manuel kapatmanız gerekiyor:</p>");
            sb.append("<ul>");
            for (ManualCloseHolding mc : manualClose) {
                sb.append("<li><strong>").append(escape(mc.symbol)).append("</strong> — ")
                        .append(mc.openQty.toPlainString()).append(" nominal (")
                        .append(escape(mc.reason)).append(")</li>");
            }
            sb.append("</ul>");
        }
        sb.append("<p style=\"color:#6b7280;font-size:12px;\">Otomatik itfa: kategoriye göre par (100), ")
                .append("EVDS son gösterge değeri veya canlı gram altın TL fiyatı kullanılmıştır. ")
                .append("Sabit/TLREF kuponlu ve standart kira sertifikalarında ayrıca son kupon ödemesi ")
                .append("kredilendirilir. Gerçekleşmiş K/Z portföy özetinde günceldir.</p>");
        sb.append("</div>");
        return sb.toString();
    }

    private EvdsBondInstrument lookupBond(String symbol) {
        try {
            return evdsBondService.getEvdsBondDetail(symbol);
        } catch (Exception ex) {
            log.debug("[BondMaturity] {} EVDS detail alınamadı: {}", symbol, ex.getMessage());
            return null;
        }
    }

    /** HMB ISIN listesini güvenle çek; servis erişilemezse boş liste döner. */
    private List<String> safeCurrentIsins() {
        try {
            List<String> isins = eurobondService.currentIsins();
            return isins != null ? isins : List.of();
        } catch (Exception ex) {
            log.warn("[BondMaturity] Eurobond ISIN listesi alınamadı: {}", ex.getMessage());
            return List.of();
        }
    }

    /** Canlı has altın gram TL fiyatı (BIST üzerinden). Bulunamazsa null. */
    private BigDecimal lookupGoldGramTry() {
        try {
            GoldSpotResponse spot = goldMarketService.getSpotGold();
            if (spot != null) {
                if (spot.getOfficialPureGoldGramTry() != null) {
                    return spot.getOfficialPureGoldGramTry();
                }
                if (spot.getGramCloseTry() != null) {
                    return spot.getGramCloseTry();
                }
            }
        } catch (Exception e) {
            log.debug("[BondMaturity] Altın gram TL alınamadı: {}", e.getMessage());
        }
        return null;
    }

    private String lookupUserEmail(String userId) {
        try {
            AdminUserView u = keycloakUserAdminPort.getUser(userId);
            return u != null && u.isEmailVerified() ? u.getEmail() : null;
        } catch (Exception ex) {
            log.debug("[BondMaturity] {} kullanıcı bilgisi alınamadı: {}", userId, ex.getMessage());
            return null;
        }
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /**
     * Tek bir vadesi gelmiş açık BOND pozisyonu (taşıyıcı).
     * {@code category} hesap-motoru kararı, {@code lastIndicator} TÜFE-endeksli ödeme,
     * {@code couponRate} son kupon hesabı için gereklidir — hepsi {@link EvdsBondInstrument}
     * üzerinden bir kez okunur.
     */
    private record MaturedHolding(String symbol, BigDecimal openQty, LocalDate maturityDate,
                                  String instrumentCode, BondCategory category,
                                  BigDecimal lastIndicator, BigDecimal couponRate) {
        @Override public boolean equals(Object o) {
            return o instanceof MaturedHolding m && Objects.equals(symbol, m.symbol);
        }
        @Override public int hashCode() { return Objects.hashCode(symbol); }
    }

    /** Otomatik kapatılmayan ve kullanıcıya bildirilecek pozisyon (Eurobond gibi). */
    private record ManualCloseHolding(String symbol, BigDecimal openQty, String reason) {}

    /** Tarama sonucu — otomatik kapatılacaklar + manuel-kapat uyarılacaklar. */
    private record ScanResult(List<MaturedHolding> matured, List<ManualCloseHolding> manualClose) {
        boolean isEmpty() { return matured.isEmpty() && manualClose.isEmpty(); }
    }
}
