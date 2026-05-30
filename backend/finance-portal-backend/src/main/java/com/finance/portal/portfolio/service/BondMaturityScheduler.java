package com.finance.portal.portfolio.service;

import com.finance.portal.admin.application.model.AdminUserView;
import com.finance.portal.admin.application.port.KeycloakUserAdminPort;
import com.finance.portal.common.domain.AssetType;
import com.finance.portal.market.application.bond.evds.EvdsBondInstrument;
import com.finance.portal.market.application.bond.evds.EvdsBondService;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Her gece çalışan otomatik DİBS itfa görevi.
 *
 * <p>Açık BOND pozisyonlarının vade tarihini ({@link EvdsBondInstrument#getMaturityDate()})
 * kontrol eder; vade dolmuşsa otomatik {@code SELL} işlemi oluşturur — fiyat <b>100</b>
 * (par, 100 TL nominal üzerinden tam ödeme). Böylece:
 * <ul>
 *   <li>Açık pozisyon nominal × 100/100 = nominal TL ödeme ile kapanır.</li>
 *   <li>Realized K/Z otomatik hesaplanır ({@code PortfolioHoldingsBuilder} aynı /100 mantığını uygular).</li>
 *   <li>Kullanıcıya bildirim + e-posta gönderilir.</li>
 * </ul>
 *
 * <p>Kapsam: <b>yalnızca EVDS DİBS</b>. Eurobond bondları bu görevde kapsanmıyor (vadeleri TL
 * dışı + farklı veri kaynağı; ilerideki bir görev). TÜFE-endeksli bondlar için itfa fiyatı 100
 * yerine 100 × TÜFE_factor olmalı — şimdilik dışlanıyor (kullanıcı elle kapatır).
 *
 * <p>Cron: her gece 00:30 İstanbul saati. SchedulerLock ile çoklu instance'da tek koşumlu.
 */
@Component
public class BondMaturityScheduler {

    private static final Logger log = LoggerFactory.getLogger(BondMaturityScheduler.class);

    /** Vade gününde ödenen tutar TCMB konvansiyonunda 100 TL nominal başına 100 TL. */
    private static final BigDecimal PAR_PRICE = new BigDecimal("100");

    private final PortfolioRepository portfolioRepository;
    private final EvdsBondService evdsBondService;
    private final NotificationService notificationService;
    private final KeycloakUserAdminPort keycloakUserAdminPort;

    public BondMaturityScheduler(PortfolioRepository portfolioRepository,
                                 EvdsBondService evdsBondService,
                                 NotificationService notificationService,
                                 KeycloakUserAdminPort keycloakUserAdminPort) {
        this.portfolioRepository = portfolioRepository;
        this.evdsBondService = evdsBondService;
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

        Map<String, EvdsBondInstrument> bondCache = new HashMap<>();
        Map<String, String> emailCache = new HashMap<>();
        int processedHoldings = 0;
        int processedPortfolios = 0;

        for (Portfolio portfolio : portfolios) {
            List<MaturedHolding> matured = findMaturedBondHoldings(portfolio, today, bondCache);
            if (matured.isEmpty()) {
                continue;
            }

            for (MaturedHolding mh : matured) {
                createItfaTransaction(portfolio, mh, today);
                processedHoldings++;
            }
            portfolioRepository.save(portfolio);
            processedPortfolios++;

            String email = emailCache.computeIfAbsent(portfolio.getUserId(), this::lookupUserEmail);
            sendItfaNotification(portfolio, matured, email);
        }

        log.info("[BondMaturity] Tarama tamamlandı. portföy={} itfa edilen holding={}",
                processedPortfolios, processedHoldings);
    }

    /** Bir portföyün açık BOND pozisyonlarından bugün vadesi gelmiş olanları döndürür. */
    private List<MaturedHolding> findMaturedBondHoldings(Portfolio portfolio,
                                                         LocalDate today,
                                                         Map<String, EvdsBondInstrument> bondCache) {
        Map<String, BigDecimal> openQtyBySymbol = new HashMap<>();
        for (PortfolioTransaction tx : portfolio.getTransactions()) {
            if (tx.getAssetType() != AssetType.BOND) continue;
            String symbol = tx.getSymbol();
            if (symbol == null) continue;
            BigDecimal qty = tx.getQuantity() != null ? tx.getQuantity() : BigDecimal.ZERO;
            BigDecimal signed = tx.getTransactionType() == TransactionType.BUY ? qty : qty.negate();
            openQtyBySymbol.merge(symbol, signed, BigDecimal::add);
        }

        List<MaturedHolding> out = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> e : openQtyBySymbol.entrySet()) {
            BigDecimal openQty = e.getValue();
            if (openQty == null || openQty.signum() <= 0) continue;

            String symbol = e.getKey();
            EvdsBondInstrument bond = bondCache.computeIfAbsent(symbol, this::lookupBond);
            if (bond == null || bond.getMaturityDate() == null) continue;

            // Bugün veya daha eski vade → itfa et
            if (!bond.getMaturityDate().isAfter(today)) {
                out.add(new MaturedHolding(symbol, openQty, bond.getMaturityDate(),
                        bond.getInstrumentCode(), bond.getCategory() != null ? bond.getCategory().name() : null));
            }
        }
        return out;
    }

    /** Otomatik SELL @ par işlemi yaratır ve portföye ekler. */
    private void createItfaTransaction(Portfolio portfolio, MaturedHolding mh, LocalDate today) {
        PortfolioTransaction tx = new PortfolioTransaction();
        tx.setSymbol(mh.symbol);
        tx.setAssetType(AssetType.BOND);
        tx.setTransactionType(TransactionType.SELL);
        tx.setQuantity(mh.openQty);
        tx.setPrice(PAR_PRICE);
        tx.setCommission(BigDecimal.ZERO);
        // İtfa tarihi vade günü 00:00 (today'den geç olabilir; vade günündeki tarihi kullan)
        tx.setTransactionDate(mh.maturityDate.atStartOfDay());
        portfolio.addTransaction(tx);

        log.info("[BondMaturity] Otomatik itfa: portföy={} symbol={} nominal={} vade={}",
                portfolio.getId(), mh.symbol, mh.openQty, mh.maturityDate);
    }

    /** Kullanıcıya in-app + e-posta bildirimi gönder. Sessizce hata yakalar. */
    private void sendItfaNotification(Portfolio portfolio, List<MaturedHolding> matured, String email) {
        try {
            String title = "DİBS vade itfası — " + portfolio.getName();
            StringBuilder body = new StringBuilder();
            body.append("Portföyünüzdeki şu DİBS pozisyonlarının vadesi geldi ve otomatik kapatıldı:\n");
            for (MaturedHolding mh : matured) {
                body.append("• ").append(mh.symbol)
                        .append(" — ").append(mh.openQty.toPlainString()).append(" TL nominal")
                        .append(" (vade ").append(mh.maturityDate).append(")")
                        .append(" → nakit girişi ").append(mh.openQty.toPlainString()).append(" TL")
                        .append("\n");
            }
            body.append("\nGerçekleşmiş K/Z'iniz portföy özetinde güncel.");

            String html = buildEmailHtml(portfolio, matured);
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

    private String buildEmailHtml(Portfolio portfolio, List<MaturedHolding> matured) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div style=\"font-family:Arial,sans-serif;max-width:560px;margin:auto;\">");
        sb.append("<h2 style=\"color:#1f2937;\">DİBS vade itfası</h2>");
        sb.append("<p><strong>").append(escape(portfolio.getName())).append("</strong> portföyünüzdeki ")
                .append("şu pozisyonların vadesi geldi ve otomatik kapatıldı:</p>");
        sb.append("<ul>");
        for (MaturedHolding mh : matured) {
            sb.append("<li><strong>").append(escape(mh.symbol)).append("</strong> — ")
                    .append(mh.openQty.toPlainString()).append(" TL nominal, vade ")
                    .append(mh.maturityDate)
                    .append(". Nakit girişi: <strong>").append(mh.openQty.toPlainString()).append(" TL</strong>")
                    .append("</li>");
        }
        sb.append("</ul>");
        sb.append("<p style=\"color:#6b7280;font-size:12px;\">Otomatik itfa: vade gününde 100 TL nominal başına ")
                .append("100 TL ödeme varsayımıyla. Gerçekleşmiş K/Z portföy özetinde günceldir.</p>");
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

    /** Tek bir vadesi gelmiş açık BOND pozisyonu (taşıyıcı). */
    private record MaturedHolding(String symbol, BigDecimal openQty, LocalDate maturityDate,
                                  String instrumentCode, String categoryName) {
        @Override public boolean equals(Object o) {
            return o instanceof MaturedHolding m && Objects.equals(symbol, m.symbol);
        }
        @Override public int hashCode() { return Objects.hashCode(symbol); }
    }
}
