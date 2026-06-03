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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Ek {@link ViopMaturityScheduler} branch testleri — {@code ViopMaturitySchedulerTest}'in
 * kapatmadığı kırmızı/sarı dalları hedefler:
 * <ul>
 *   <li>tarama-döngüsü "gürültü" filtreleri (FUTURE-olmayan tx, BUY/SELL-olmayan tip, boş/null sembol,
 *       null miktar, null/boş yön) — {@code scanAndClose} + {@code normalizeDir};</li>
 *   <li>settlement çözümü {@code lastPrice} ve grafik (lastClose) yollarından — {@code resolveSettlement} +
 *       {@code lastClose} + {@code isPositive};</li>
 *   <li>vadesi parse edilemeyen sembol atlanır;</li>
 *   <li>e-posta çözümü: doğrulanmamış kullanıcı ve null kullanıcı → email null;</li>
 *   <li>portföy adı null → {@code escape(null)}.</li>
 * </ul>
 * Statik {@code ViopService.parseContractMaturity} gerçekten parse edildiği için kontrat adları
 * gerçek formatta ve geçmiş tarihlidir.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ViopMaturitySchedulerMoreTest {

    @Mock PortfolioRepository portfolioRepository;
    @Mock ViopService viopService;
    @Mock ViopChartService viopChartService;
    @Mock NotificationService notificationService;
    @Mock KeycloakUserAdminPort keycloakUserAdminPort;

    private ViopMaturityScheduler scheduler;

    // Kesinlikle geçmiş vadeler (otomatik kapanma adayı):
    private static final String EXPIRED_LONG  = "USDTRY (30 OCA 24) VADELI"; // 2024-01-30
    private static final String EXPIRED_SHORT = "EURUSD (28 SUB 24) VADELI"; // 2024-02-28
    // Vade parse edilemez (tarih bloğu yok) → atlanmalı:
    private static final String UNPARSEABLE   = "GARAN";

    @BeforeEach
    void setUp() {
        scheduler = new ViopMaturityScheduler(portfolioRepository, viopService,
                viopChartService, notificationService, keycloakUserAdminPort);
    }

    private static Portfolio portfolio(String userId, String name) {
        Portfolio p = new Portfolio();
        p.setId(UUID.randomUUID());
        p.setUserId(userId);
        p.setName(name);
        p.setPortfolioType(PortfolioType.HOLDINGS);
        return p;
    }

    private static PortfolioTransaction tx(String symbol, AssetType asset, String direction,
                                           TransactionType type, BigDecimal qty) {
        PortfolioTransaction t = new PortfolioTransaction();
        t.setSymbol(symbol);
        t.setAssetType(asset);
        t.setTransactionType(type);
        t.setDirection(direction);
        t.setQuantity(qty);
        t.setPrice(new BigDecimal("100"));
        t.setCommission(BigDecimal.ZERO);
        t.setTransactionDate(LocalDateTime.now().minusMonths(6));
        return t;
    }

    private static PortfolioTransaction futureBuy(String symbol, String direction, BigDecimal qty) {
        return tx(symbol, AssetType.FUTURE, direction, TransactionType.BUY, qty);
    }

    private static ViopContractDetail detail(BigDecimal settlement, BigDecimal last) {
        ViopContractDetail d = new ViopContractDetail();
        d.setSettlementPrice(settlement);
        d.setLastPrice(last);
        return d;
    }

    private static ViopChartPoint point(Long ts, BigDecimal value) {
        return new ViopChartPoint(ts, ts == null ? null : "2024-01-01T00:00:00", value);
    }

    /** AllArgsConstructor: (id, username, email, firstName, lastName, emailVerified, enabled, roles, banUntil, permanentBan, banStatus, banReason) */
    private static AdminUserView user(String id, boolean emailVerified) {
        return new AdminUserView(id, "u", "u@x.com", "U", "1", emailVerified, true, List.of(), null, false, null, null);
    }

    // ---------------------------------------------------------------------------------------------
    // scanAndClose gürültü filtreleri: L120 (FUTURE değil), L122 (BUY/SELL değil),
    // L124 (sembol null + boş), L125 (qty null → ZERO), L270 (yön null + boş).
    // Hepsi açık pozisyon üretmez → bildirim/save yok.
    // ---------------------------------------------------------------------------------------------
    @Test
    @DisplayName("FUTURE-dışı / BUY-SELL-dışı / boş-sembol / null-qty / null-boş-yön işlemler taranıp atlanır")
    void noiseTransactions_allFilteredOut() {
        Portfolio p = portfolio("user-1", "P");
        // FUTURE değil → L120 TRUE dalı (continue)
        p.addTransaction(tx("THYAO", AssetType.STOCK, "LONG", TransactionType.BUY, new BigDecimal("10")));
        // FUTURE ama tipi BUY/SELL değil → L122 (continue)
        p.addTransaction(tx(EXPIRED_LONG, AssetType.FUTURE, "LONG", TransactionType.COUPON_INCOME, new BigDecimal("10")));
        // sembol null → L124 (null dalı)
        p.addTransaction(futureBuy(null, "LONG", new BigDecimal("10")));
        // sembol boş → L124 (isBlank dalı)
        p.addTransaction(futureBuy("   ", "LONG", new BigDecimal("10")));
        // qty null → L125 (ZERO) + yön null → L270 (null dalı). Net 0 → L132 idempotent atla.
        p.addTransaction(futureBuy(EXPIRED_LONG, null, null));
        // qty null + yön boş → L270 (isBlank dalı). Net 0 → atla.
        p.addTransaction(futureBuy(EXPIRED_SHORT, "   ", null));

        when(portfolioRepository.findAll()).thenReturn(List.of(p));

        scheduler.processMaturedFutures();

        // Hiçbir açık pozisyon kalmadı → kapanış yok, settlement sorulmadı, bildirim yok.
        verify(viopService, never()).getContractDetailCached(anyString());
        verify(viopChartService, never()).getChart(anyString(), any());
        verify(portfolioRepository, never()).save(any());
        verify(notificationService, never()).createAndSend(
                anyString(), any(), anyString(), anyString(), anyString(), any(), any());
    }

    // ---------------------------------------------------------------------------------------------
    // L132: net NEGATİF pozisyon (SELL > BUY) → signum() <= 0 → atla (idempotent, over-closed).
    // ---------------------------------------------------------------------------------------------
    @Test
    @DisplayName("Net negatif (fazla SATIŞ) pozisyon kapatılmış sayılır — atlanır")
    void netNegativePosition_skipped() {
        Portfolio p = portfolio("user-1", "P");
        p.addTransaction(futureBuy(EXPIRED_LONG, "LONG", new BigDecimal("3")));
        p.addTransaction(tx(EXPIRED_LONG, AssetType.FUTURE, "LONG", TransactionType.SELL, new BigDecimal("8")));

        when(portfolioRepository.findAll()).thenReturn(List.of(p));

        scheduler.processMaturedFutures();

        assertThat(p.getTransactions()).hasSize(2); // yeni kapanış yok
        verify(viopService, never()).getContractDetailCached(anyString());
        verify(portfolioRepository, never()).save(any());
    }

    // ---------------------------------------------------------------------------------------------
    // L136: vade parse edilemez (maturity.isEmpty()) → atla.
    // ---------------------------------------------------------------------------------------------
    @Test
    @DisplayName("Vadesi parse edilemeyen sembol (tarih bloğu yok) atlanır")
    void unparseableMaturity_skipped() {
        Portfolio p = portfolio("user-1", "P");
        p.addTransaction(futureBuy(UNPARSEABLE, "LONG", new BigDecimal("10")));

        when(portfolioRepository.findAll()).thenReturn(List.of(p));

        scheduler.processMaturedFutures();

        assertThat(p.getTransactions()).hasSize(1);
        verify(viopService, never()).getContractDetailCached(anyString());
        verify(portfolioRepository, never()).save(any());
    }

    // ---------------------------------------------------------------------------------------------
    // resolveSettlement: settlementPrice yok ama lastPrice pozitif → L161 FALSE, L162 TRUE.
    // ---------------------------------------------------------------------------------------------
    @Test
    @DisplayName("settlementPrice yoksa lastPrice'tan kapanır (detay yolu, ikinci dal)")
    void settlementMissing_fallsBackToLastPrice() {
        Portfolio p = portfolio("user-1", "P");
        p.addTransaction(futureBuy(EXPIRED_LONG, "LONG", new BigDecimal("4")));

        when(portfolioRepository.findAll()).thenReturn(List.of(p));
        // settlementPrice null → L161 isPositive FALSE; lastPrice 55 → L162 isPositive TRUE
        when(viopService.getContractDetailCached(EXPIRED_LONG)).thenReturn(detail(null, new BigDecimal("55")));
        when(keycloakUserAdminPort.getUser("user-1")).thenReturn(user("user-1", true));

        scheduler.processMaturedFutures();

        assertThat(p.getTransactions()).hasSize(2);
        PortfolioTransaction sell = p.getTransactions().get(1);
        assertThat(sell.getTransactionType()).isEqualTo(TransactionType.SELL);
        assertThat(sell.getPrice()).isEqualByComparingTo("55");
        verify(viopChartService, never()).getChart(anyString(), any()); // detay yeterliydi
        verify(portfolioRepository).save(p);
    }

    // ---------------------------------------------------------------------------------------------
    // resolveSettlement grafik yolu: detay var ama hiç pozitif fiyat yok (L161 FALSE, L162 FALSE) →
    // grafik döngüsü (L168) çalışır. ONE_MONTH lastClose <= 0 (L170 FALSE) → THREE_MONTHS pozitif
    // (L170 TRUE). lastClose döngüsü tüm dalları gezer: null-value skip, null-ts skip,
    // best==null ilk-atama, ts> karşılaştırması TRUE ve FALSE (L281-L285), L279 FALSE (boş değil).
    // ---------------------------------------------------------------------------------------------
    @Test
    @DisplayName("Detayda pozitif fiyat yoksa grafik lastClose'tan kapanır (en güncel pozitif nokta)")
    void settlementFromChart_picksLatestPositiveClose() {
        Portfolio p = portfolio("user-1", "P");
        p.addTransaction(futureBuy(EXPIRED_LONG, "LONG", new BigDecimal("2")));

        when(portfolioRepository.findAll()).thenReturn(List.of(p));
        // d != null (L160 TRUE) ama settlement & last yok → grafik yoluna düş.
        when(viopService.getContractDetailCached(EXPIRED_LONG)).thenReturn(detail(null, null));

        // ONE_MONTH: son nokta 0 → lastClose 0 → isPositive FALSE → döngü devam.
        when(viopChartService.getChart(eq(EXPIRED_LONG), eq(ViopChartPeriod.ONE_MONTH)))
                .thenReturn(List.of(point(100L, BigDecimal.ZERO)));
        // THREE_MONTHS: karışık liste; en güncel pozitif (ts=300 → 95) seçilmeli.
        when(viopChartService.getChart(eq(EXPIRED_LONG), eq(ViopChartPeriod.THREE_MONTHS)))
                .thenReturn(Arrays.asList(
                        point(100L, null),                 // value null → atla
                        point(null, new BigDecimal("40")), // timestamp null → atla
                        point(200L, new BigDecimal("80")), // best==null → ilk atama
                        point(300L, new BigDecimal("95")), // ts 300>200 → best güncellenir
                        point(150L, new BigDecimal("50"))  // ts 150>300 FALSE → best korunur
                ));

        when(keycloakUserAdminPort.getUser("user-1")).thenReturn(user("user-1", true));

        scheduler.processMaturedFutures();

        assertThat(p.getTransactions()).hasSize(2);
        PortfolioTransaction sell = p.getTransactions().get(1);
        assertThat(sell.getTransactionType()).isEqualTo(TransactionType.SELL);
        assertThat(sell.getPrice()).isEqualByComparingTo("95");
        verify(portfolioRepository).save(p);
    }

    // ---------------------------------------------------------------------------------------------
    // lookupUserEmail: L262 her iki eksik dal.
    //  - portföy A: kullanıcı email-doğrulanmamış → isEmailVerified() FALSE → email null.
    //  - portföy B: getUser null → (u != null) FALSE → email null.
    // İki portföy tek koşumda → ana döngü çok-portföy dalı da çalışır.
    // ---------------------------------------------------------------------------------------------
    @Test
    @DisplayName("E-posta çözümü: doğrulanmamış kullanıcı ve null kullanıcı → bildirim email'siz gider")
    void emailResolution_unverifiedAndNullUser() {
        Portfolio a = portfolio("user-unverified", "A");
        a.addTransaction(futureBuy(EXPIRED_LONG, "LONG", new BigDecimal("1")));
        Portfolio b = portfolio("user-null", "B");
        b.addTransaction(futureBuy(EXPIRED_SHORT, "SHORT", new BigDecimal("1")));

        when(portfolioRepository.findAll()).thenReturn(List.of(a, b));
        when(viopService.getContractDetailCached(EXPIRED_LONG)).thenReturn(detail(new BigDecimal("120"), null));
        when(viopService.getContractDetailCached(EXPIRED_SHORT)).thenReturn(detail(new BigDecimal("70"), null));
        when(keycloakUserAdminPort.getUser("user-unverified")).thenReturn(user("user-unverified", false));
        when(keycloakUserAdminPort.getUser("user-null")).thenReturn(null);

        scheduler.processMaturedFutures();

        // Her iki portföy de kapandı ve kaydedildi.
        verify(portfolioRepository).save(a);
        verify(portfolioRepository).save(b);
        // Bildirimler email = null ile (ve son arg da null) gönderildi.
        verify(notificationService).createAndSend(eq("user-unverified"), eq(NotificationType.PORTFOLIO),
                anyString(), anyString(), anyString(), isNull(), isNull());
        verify(notificationService).createAndSend(eq("user-null"), eq(NotificationType.PORTFOLIO),
                anyString(), anyString(), anyString(), isNull(), isNull());
    }

    // ---------------------------------------------------------------------------------------------
    // escape(null): L289 TRUE. Portföy adı null → buildEmailHtml içinde escape(portfolio.getName()).
    // ---------------------------------------------------------------------------------------------
    @Test
    @DisplayName("Portföy adı null olsa bile e-posta HTML'i üretilir (escape null-güvenli)")
    void nullPortfolioName_emailStillBuilt() {
        Portfolio p = portfolio("user-1", null); // ad null
        p.addTransaction(futureBuy(EXPIRED_LONG, "LONG", new BigDecimal("6")));

        when(portfolioRepository.findAll()).thenReturn(List.of(p));
        when(viopService.getContractDetailCached(EXPIRED_LONG)).thenReturn(detail(new BigDecimal("130"), null));
        when(keycloakUserAdminPort.getUser("user-1")).thenReturn(user("user-1", true));

        scheduler.processMaturedFutures();

        assertThat(p.getTransactions()).hasSize(2);
        verify(portfolioRepository).save(p);
        // HTML beşinci argüman; null-ad NPE atmadan oluştu (kapanan pozisyon sembolünü içerir).
        verify(notificationService).createAndSend(eq("user-1"), eq(NotificationType.PORTFOLIO),
                anyString(), anyString(), anyString(), eq("u@x.com"), isNull());
    }
}
