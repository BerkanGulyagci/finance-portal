package com.finance.portal.alarm.application.service;

import com.finance.portal.admin.application.model.AdminUserView;
import com.finance.portal.admin.application.port.KeycloakUserAdminPort;
import com.finance.portal.alarm.application.model.AlarmTriggeredEvent;
import com.finance.portal.alarm.application.port.AlarmNotificationPort;
import com.finance.portal.alarm.domain.AlarmDirection;
import com.finance.portal.alarm.domain.AlarmMetric;
import com.finance.portal.common.application.logging.CentralBusinessLogService;
import com.finance.portal.common.domain.AssetType;
import com.finance.portal.portfolio.domain.Portfolio;
import com.finance.portal.portfolio.domain.PortfolioType;
import com.finance.portal.portfolio.presentation.dto.PortfolioHoldingResponse;
import com.finance.portal.portfolio.presentation.dto.PortfolioResponse;
import com.finance.portal.portfolio.repository.PortfolioRepository;
import com.finance.portal.portfolio.service.PortfolioService;
import com.finance.portal.preferences.service.UserPreferenceService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Ek kapsam: MarginAlarmEvaluator'ın ratio karşılaştırma, de-bounce (cooldown / alt-bant),
 * e-posta önbelleği, pozisyon-detayı para biçimi ve filtre (enabled/watchlist/asset-tip)
 * dalları. {@code @Scheduled} metodu doğrudan çağrılır.
 */
@ExtendWith(MockitoExtension.class)
class MarginAlarmEvaluatorMoreTest {

    @Mock PortfolioRepository portfolioRepository;
    @Mock PortfolioService portfolioService;
    @Mock UserPreferenceService userPreferenceService;
    @Mock AlarmNotificationPort notificationPort;
    @Mock KeycloakUserAdminPort keycloakUserAdminPort;
    @Mock CentralBusinessLogService businessLog;

    private MarginAlarmEvaluator newEvaluator(boolean enabled, long cooldownMinutes, int lowerBandPct) {
        return new MarginAlarmEvaluator(portfolioRepository, portfolioService, userPreferenceService,
                notificationPort, keycloakUserAdminPort, businessLog, enabled, cooldownMinutes, lowerBandPct);
    }

    private MarginAlarmEvaluator defaultEvaluator() {
        return newEvaluator(true, 360, 5);
    }

    private static Portfolio portfolio(String userId, PortfolioType type) {
        Portfolio p = new Portfolio();
        p.setId(UUID.randomUUID());
        p.setUserId(userId);
        p.setName("P");
        p.setPortfolioType(type);
        return p;
    }

    private static PortfolioHoldingResponse future(String symbol, BigDecimal ratio, String direction) {
        PortfolioHoldingResponse h = new PortfolioHoldingResponse();
        h.setSymbol(symbol);
        h.setName(symbol + " Vadeli");
        h.setAssetType(AssetType.FUTURE);
        h.setMarginRatio(ratio);
        h.setViopDirection(direction);
        h.setTotalQuantity(new BigDecimal("1"));
        h.setAverageCost(new BigDecimal("10.00"));
        h.setCurrentPrice(new BigDecimal("2.70"));
        h.setProfitLoss(new BigDecimal("-730.00"));
        h.setViopMarginPosted(new BigDecimal("145.00"));
        return h;
    }

    private static PortfolioResponse detailWith(PortfolioHoldingResponse... holdings) {
        PortfolioResponse r = new PortfolioResponse();
        List<PortfolioHoldingResponse> list = new ArrayList<>(List.of(holdings));
        r.setHoldings(list);
        return r;
    }

    private static AdminUserView verifiedUser(String id) {
        return new AdminUserView(id, "u", id + "@x.com", "U", "1",
                true, true, List.of(), null, false, null, null);
    }

    private void stubUserDetail(String userId, Portfolio p, PortfolioResponse detail) {
        when(portfolioService.getPortfolioById(eq(userId), eq(p.getId()))).thenReturn(detail);
    }

    // ── Temel tetikleme: eşik altı FUTURE → event + businessLog ──

    @Test
    @DisplayName("Eşik altı margin oranı bildirim olayı + AUDIT log üretir")
    void belowThreshold_fires() {
        Portfolio p = portfolio("user-1", PortfolioType.HOLDINGS);
        PortfolioHoldingResponse h = future("XU030", new BigDecimal("0.20"), "LONG");
        when(portfolioRepository.findAll()).thenReturn(List.of(p));
        when(userPreferenceService.getMarginAlertThresholdPct("user-1")).thenReturn(50);
        stubUserDetail("user-1", p, detailWith(h));
        when(keycloakUserAdminPort.getUser("user-1")).thenReturn(verifiedUser("user-1"));

        defaultEvaluator().evaluateMarginAlarms();

        ArgumentCaptor<AlarmTriggeredEvent> ev = ArgumentCaptor.forClass(AlarmTriggeredEvent.class);
        verify(notificationPort).notifyAlarmTriggered(ev.capture());
        AlarmTriggeredEvent e = ev.getValue();
        assertThat(e.userId()).isEqualTo("user-1");
        assertThat(e.recipientEmail()).isEqualTo("user-1@x.com");
        assertThat(e.alarmId()).isNull();
        assertThat(e.assetType()).isEqualTo(AssetType.FUTURE);
        assertThat(e.metric()).isEqualTo(AlarmMetric.MARGIN_RATIO);
        assertThat(e.direction()).isEqualTo(AlarmDirection.BELOW);
        assertThat(e.threshold()).isEqualByComparingTo("0.5000");
        assertThat(e.observedValue()).isEqualByComparingTo("0.20");
        // Pozisyon detayı para biçimi (TR locale + TRY)
        assertThat(e.note()).contains("LONG").contains("1 lot")
                .contains("giriş 10,00 TRY").contains("güncel 2,70 TRY")
                .contains("K/Z -730,00 TRY").contains("teminat 145,00 TRY");

        verify(businessLog).publish(anyString(), anyString(), eq("WARN"), anyString(),
                anyString(), any(), anyString(), anyString(), any(Map.class), eq("user-1"), anyString());
    }

    // ── Eşik = 0 → kullanıcı kapatmış, atla ──

    @Test
    @DisplayName("Eşik 0 ise kullanıcı tamamen atlanır (portföy detayı bile çekilmez)")
    void thresholdZero_skipUser() {
        Portfolio p = portfolio("user-0", PortfolioType.HOLDINGS);
        when(portfolioRepository.findAll()).thenReturn(List.of(p));
        when(userPreferenceService.getMarginAlertThresholdPct("user-0")).thenReturn(0);

        defaultEvaluator().evaluateMarginAlarms();

        verify(portfolioService, never()).getPortfolioById(anyString(), any());
        verifyNoInteractions(notificationPort);
    }

    // ── Oran eşiğin üzerinde → sağlıklı, tetikleme yok ──

    @Test
    @DisplayName("Oran eşiğin üzerinde/eşitse tetiklenmez")
    void atOrAboveThreshold_noFire() {
        Portfolio p = portfolio("user-2", PortfolioType.HOLDINGS);
        PortfolioHoldingResponse h = future("XU030", new BigDecimal("0.50"), "LONG"); // tam eşik
        when(portfolioRepository.findAll()).thenReturn(List.of(p));
        when(userPreferenceService.getMarginAlertThresholdPct("user-2")).thenReturn(50);
        stubUserDetail("user-2", p, detailWith(h));

        defaultEvaluator().evaluateMarginAlarms();

        verifyNoInteractions(notificationPort);
    }

    // ── marginRatio null → enricher dolduramadı, atla ──

    @Test
    @DisplayName("marginRatio null holding atlanır")
    void nullMarginRatio_skip() {
        Portfolio p = portfolio("user-3", PortfolioType.HOLDINGS);
        PortfolioHoldingResponse h = future("XU030", null, "LONG");
        when(portfolioRepository.findAll()).thenReturn(List.of(p));
        when(userPreferenceService.getMarginAlertThresholdPct("user-3")).thenReturn(50);
        stubUserDetail("user-3", p, detailWith(h));

        defaultEvaluator().evaluateMarginAlarms();

        verifyNoInteractions(notificationPort);
    }

    // ── Non-FUTURE ve closed holding atlanır ──

    @Test
    @DisplayName("FUTURE olmayan ve kapalı pozisyonlar atlanır")
    void nonFutureAndClosed_skip() {
        Portfolio p = portfolio("user-4", PortfolioType.HOLDINGS);
        PortfolioHoldingResponse stock = new PortfolioHoldingResponse();
        stock.setSymbol("THYAO");
        stock.setAssetType(AssetType.STOCK);
        stock.setMarginRatio(new BigDecimal("0.10"));
        PortfolioHoldingResponse closed = future("XU030", new BigDecimal("0.10"), "SHORT");
        closed.setClosed(true);
        PortfolioHoldingResponse nullHolding = null;

        when(portfolioRepository.findAll()).thenReturn(List.of(p));
        when(userPreferenceService.getMarginAlertThresholdPct("user-4")).thenReturn(50);
        List<PortfolioHoldingResponse> list = new ArrayList<>();
        list.add(stock);
        list.add(closed);
        list.add(nullHolding);
        PortfolioResponse detail = new PortfolioResponse();
        detail.setHoldings(list);
        stubUserDetail("user-4", p, detail);

        defaultEvaluator().evaluateMarginAlarms();

        verifyNoInteractions(notificationPort);
    }

    // ── De-bounce: cooldown içinde ikinci tur tetiklemez ──

    @Test
    @DisplayName("Cooldown içinde aynı sembol ikinci kez tetiklenmez")
    void debounce_withinCooldown_noSecondFire() {
        Portfolio p = portfolio("user-5", PortfolioType.HOLDINGS);
        PortfolioHoldingResponse h = future("XU030", new BigDecimal("0.20"), "LONG");
        when(portfolioRepository.findAll()).thenReturn(List.of(p));
        when(userPreferenceService.getMarginAlertThresholdPct("user-5")).thenReturn(50);
        stubUserDetail("user-5", p, detailWith(h));
        when(keycloakUserAdminPort.getUser("user-5")).thenReturn(verifiedUser("user-5"));

        MarginAlarmEvaluator ev = newEvaluator(true, 360, 5);
        ev.evaluateMarginAlarms();
        ev.evaluateMarginAlarms(); // ikinci tur — cooldown'da, ratio aynı

        verify(notificationPort, times(1)).notifyAlarmTriggered(any());
    }

    // ── De-bounce: alt-banda düşünce cooldown bypass edilir ──

    @Test
    @DisplayName("Margin call derinleşip alt-banda düşerse cooldown'a rağmen yeniden tetiklenir")
    void debounce_lowerBand_bypassesCooldown() {
        Portfolio p = portfolio("user-6", PortfolioType.HOLDINGS);
        when(portfolioRepository.findAll()).thenReturn(List.of(p));
        when(userPreferenceService.getMarginAlertThresholdPct("user-6")).thenReturn(50);
        when(keycloakUserAdminPort.getUser("user-6")).thenReturn(verifiedUser("user-6"));

        // 1. tur: %20 (0.20), 2. tur: %10 (0.10) — 10 puan düşüş > 5 puanlık alt-bant
        stubUserDetail("user-6", p, detailWith(future("XU030", new BigDecimal("0.20"), "LONG")));
        MarginAlarmEvaluator ev = newEvaluator(true, 360, 5);
        ev.evaluateMarginAlarms();

        when(portfolioService.getPortfolioById(eq("user-6"), eq(p.getId())))
                .thenReturn(detailWith(future("XU030", new BigDecimal("0.10"), "LONG")));
        ev.evaluateMarginAlarms();

        verify(notificationPort, times(2)).notifyAlarmTriggered(any());
    }

    @Test
    @DisplayName("Alt-banda yetmeyen küçük düşüş cooldown içinde tetiklemez")
    void debounce_smallDrop_stillSuppressed() {
        Portfolio p = portfolio("user-6b", PortfolioType.HOLDINGS);
        when(portfolioRepository.findAll()).thenReturn(List.of(p));
        when(userPreferenceService.getMarginAlertThresholdPct("user-6b")).thenReturn(50);
        when(keycloakUserAdminPort.getUser("user-6b")).thenReturn(verifiedUser("user-6b"));

        stubUserDetail("user-6b", p, detailWith(future("XU030", new BigDecimal("0.20"), "LONG")));
        MarginAlarmEvaluator ev = newEvaluator(true, 360, 5);
        ev.evaluateMarginAlarms();

        // %20 → %18: sadece 2 puanlık düşüş, alt-bant 5 puan → bastırılır
        when(portfolioService.getPortfolioById(eq("user-6b"), eq(p.getId())))
                .thenReturn(detailWith(future("XU030", new BigDecimal("0.18"), "LONG")));
        ev.evaluateMarginAlarms();

        verify(notificationPort, times(1)).notifyAlarmTriggered(any());
    }

    @Test
    @DisplayName("Cooldown 0 ise her turda yeniden tetiklenir")
    void zeroCooldown_firesEveryTime() {
        Portfolio p = portfolio("user-7", PortfolioType.HOLDINGS);
        PortfolioHoldingResponse h = future("XU030", new BigDecimal("0.20"), "LONG");
        when(portfolioRepository.findAll()).thenReturn(List.of(p));
        when(userPreferenceService.getMarginAlertThresholdPct("user-7")).thenReturn(50);
        stubUserDetail("user-7", p, detailWith(h));
        when(keycloakUserAdminPort.getUser("user-7")).thenReturn(verifiedUser("user-7"));

        MarginAlarmEvaluator ev = newEvaluator(true, 0, 5);
        ev.evaluateMarginAlarms();
        ev.evaluateMarginAlarms();

        verify(notificationPort, times(2)).notifyAlarmTriggered(any());
    }

    // ── enabled=false → hiç çalışmaz ──

    @Test
    @DisplayName("enabled=false ise hiçbir işlem yapılmaz")
    void disabled_noop() {
        newEvaluator(false, 360, 5).evaluateMarginAlarms();
        verifyNoInteractions(portfolioRepository);
        verifyNoInteractions(notificationPort);
    }

    // ── WATCHLIST filtrelenir, boş kalan portföy listesi erken döner ──

    @Test
    @DisplayName("Sadece WATCHLIST varsa erken dönülür")
    void onlyWatchlist_earlyReturn() {
        Portfolio w = portfolio("user-8", PortfolioType.WATCHLIST);
        when(portfolioRepository.findAll()).thenReturn(List.of(w));

        defaultEvaluator().evaluateMarginAlarms();

        verify(userPreferenceService, never()).getMarginAlertThresholdPct(anyString());
        verifyNoInteractions(notificationPort);
    }

    @Test
    @DisplayName("Boş portföy listesi erken dönülür")
    void emptyPortfolios_earlyReturn() {
        when(portfolioRepository.findAll()).thenReturn(List.of());
        defaultEvaluator().evaluateMarginAlarms();
        verify(userPreferenceService, never()).getMarginAlertThresholdPct(anyString());
    }

    // ── userId null portföy gruplamada atlanır ──

    @Test
    @DisplayName("userId null portföy gruplanmaz")
    void nullUserId_skippedInGrouping() {
        Portfolio noUser = portfolio(null, PortfolioType.HOLDINGS);
        Portfolio withUser = portfolio("user-9", PortfolioType.HOLDINGS);
        when(portfolioRepository.findAll()).thenReturn(List.of(noUser, withUser));
        when(userPreferenceService.getMarginAlertThresholdPct("user-9")).thenReturn(50);
        stubUserDetail("user-9", withUser, detailWith());

        defaultEvaluator().evaluateMarginAlarms();

        // null-user için eşik okunmaz
        verify(userPreferenceService, never()).getMarginAlertThresholdPct(eq(null));
        verify(userPreferenceService).getMarginAlertThresholdPct("user-9");
    }

    // ── findAll patlarsa scan sessiz döner ──

    @Test
    @DisplayName("Portföyler okunamazsa scan sessizce döner")
    void repositoryThrows_swallowed() {
        when(portfolioRepository.findAll()).thenThrow(new RuntimeException("db down"));
        defaultEvaluator().evaluateMarginAlarms();
        verifyNoInteractions(notificationPort);
    }

    // ── getPortfolioById patlarsa portföy atlanır ──

    @Test
    @DisplayName("getPortfolioById patlarsa o portföy atlanır, scan devam eder")
    void detailThrows_skipsPortfolio() {
        Portfolio p = portfolio("user-10", PortfolioType.HOLDINGS);
        when(portfolioRepository.findAll()).thenReturn(List.of(p));
        when(userPreferenceService.getMarginAlertThresholdPct("user-10")).thenReturn(50);
        when(portfolioService.getPortfolioById(eq("user-10"), eq(p.getId())))
                .thenThrow(new RuntimeException("enrich failed"));

        defaultEvaluator().evaluateMarginAlarms();

        verifyNoInteractions(notificationPort);
    }

    // ── detail null / holdings null → atla ──

    @Test
    @DisplayName("detail null veya holdings null ise atlanır")
    void nullDetailOrHoldings_skip() {
        Portfolio p1 = portfolio("user-11", PortfolioType.HOLDINGS);
        Portfolio p2 = portfolio("user-11", PortfolioType.HOLDINGS);
        when(portfolioRepository.findAll()).thenReturn(List.of(p1, p2));
        when(userPreferenceService.getMarginAlertThresholdPct("user-11")).thenReturn(50);
        when(portfolioService.getPortfolioById(eq("user-11"), eq(p1.getId()))).thenReturn(null);
        PortfolioResponse holdingsNull = new PortfolioResponse(); // holdings = null
        when(portfolioService.getPortfolioById(eq("user-11"), eq(p2.getId()))).thenReturn(holdingsNull);

        defaultEvaluator().evaluateMarginAlarms();

        verifyNoInteractions(notificationPort);
    }

    // ── evaluateUser içi exception bir kullanıcıyı bozsa diğeri işlenir ──

    @Test
    @DisplayName("Bir kullanıcı değerlendirmesi patlarsa diğer kullanıcı işlenmeye devam eder")
    void userEvaluationThrows_othersContinue() {
        Portfolio bad = portfolio("user-bad", PortfolioType.HOLDINGS);
        Portfolio good = portfolio("user-good", PortfolioType.HOLDINGS);
        when(portfolioRepository.findAll()).thenReturn(List.of(bad, good));
        when(userPreferenceService.getMarginAlertThresholdPct("user-bad"))
                .thenThrow(new RuntimeException("pref read failed"));
        when(userPreferenceService.getMarginAlertThresholdPct("user-good")).thenReturn(50);
        PortfolioHoldingResponse h = future("XU030", new BigDecimal("0.10"), "SHORT");
        stubUserDetail("user-good", good, detailWith(h));
        when(keycloakUserAdminPort.getUser("user-good")).thenReturn(verifiedUser("user-good"));

        defaultEvaluator().evaluateMarginAlarms();

        verify(notificationPort, times(1)).notifyAlarmTriggered(any());
    }

    // ── Pozisyon detayı: alanların bir kısmı null iken biçimleme bozulmaz ──

    @Test
    @DisplayName("Pozisyon detayı: yön/maliyet/güncel/teminat null kombinasyonu")
    void positionDetail_minimalFields() {
        Portfolio p = portfolio("user-12", PortfolioType.HOLDINGS);
        PortfolioHoldingResponse h = new PortfolioHoldingResponse();
        h.setSymbol("PETKM");
        h.setAssetType(AssetType.FUTURE);
        h.setMarginRatio(new BigDecimal("0.10"));
        // viopDirection null, totalQuantity null, averageCost null, currentPrice null
        h.setProfitLoss(new BigDecimal("-50.5")); // sadece K/Z var, teminat yok
        when(portfolioRepository.findAll()).thenReturn(List.of(p));
        when(userPreferenceService.getMarginAlertThresholdPct("user-12")).thenReturn(50);
        stubUserDetail("user-12", p, detailWith(h));
        when(keycloakUserAdminPort.getUser("user-12")).thenReturn(verifiedUser("user-12"));

        defaultEvaluator().evaluateMarginAlarms();

        ArgumentCaptor<AlarmTriggeredEvent> ev = ArgumentCaptor.forClass(AlarmTriggeredEvent.class);
        verify(notificationPort).notifyAlarmTriggered(ev.capture());
        String note = ev.getValue().note();
        assertThat(note).contains("K/Z -50,50 TRY");
        assertThat(note).doesNotContain("teminat");
        assertThat(note).doesNotContain("lot");
    }

    @Test
    @DisplayName("Pozisyon detayı: sadece teminat (K/Z null) parantezi doğru biçimlenir")
    void positionDetail_onlyMargin() {
        Portfolio p = portfolio("user-13", PortfolioType.HOLDINGS);
        PortfolioHoldingResponse h = new PortfolioHoldingResponse();
        h.setSymbol("EREGL");
        h.setAssetType(AssetType.FUTURE);
        h.setMarginRatio(new BigDecimal("0.05"));
        h.setViopDirection("SHORT");
        h.setTotalQuantity(new BigDecimal("2.5"));
        h.setViopMarginPosted(new BigDecimal("1234.56"));
        when(portfolioRepository.findAll()).thenReturn(List.of(p));
        when(userPreferenceService.getMarginAlertThresholdPct("user-13")).thenReturn(50);
        stubUserDetail("user-13", p, detailWith(h));
        when(keycloakUserAdminPort.getUser("user-13")).thenReturn(verifiedUser("user-13"));

        defaultEvaluator().evaluateMarginAlarms();

        ArgumentCaptor<AlarmTriggeredEvent> ev = ArgumentCaptor.forClass(AlarmTriggeredEvent.class);
        verify(notificationPort).notifyAlarmTriggered(ev.capture());
        String note = ev.getValue().note();
        assertThat(note).contains("SHORT").contains("2.5 lot")
                .contains("teminat 1.234,56 TRY");
        assertThat(note).doesNotContain("K/Z");
    }

    // ── E-posta önbelleği: aynı kullanıcı çok portföyde tek kez keycloak çağrılır ──

    @Test
    @DisplayName("Aynı kullanıcının iki portföyünde keycloak bir kez sorgulanır (email cache)")
    void emailCache_singleKeycloakLookup() {
        Portfolio p1 = portfolio("user-14", PortfolioType.HOLDINGS);
        Portfolio p2 = portfolio("user-14", PortfolioType.HOLDINGS);
        when(portfolioRepository.findAll()).thenReturn(List.of(p1, p2));
        when(userPreferenceService.getMarginAlertThresholdPct("user-14")).thenReturn(50);
        stubUserDetail("user-14", p1, detailWith(future("AAA", new BigDecimal("0.10"), "LONG")));
        when(portfolioService.getPortfolioById(eq("user-14"), eq(p2.getId())))
                .thenReturn(detailWith(future("BBB", new BigDecimal("0.10"), "SHORT")));
        when(keycloakUserAdminPort.getUser("user-14")).thenReturn(verifiedUser("user-14"));

        defaultEvaluator().evaluateMarginAlarms();

        verify(notificationPort, times(2)).notifyAlarmTriggered(any());
        verify(keycloakUserAdminPort, times(1)).getUser("user-14");
    }

    @Test
    @DisplayName("E-posta doğrulanmamışsa recipientEmail null gider")
    void unverifiedEmail_nullRecipient() {
        Portfolio p = portfolio("user-15", PortfolioType.HOLDINGS);
        when(portfolioRepository.findAll()).thenReturn(List.of(p));
        when(userPreferenceService.getMarginAlertThresholdPct("user-15")).thenReturn(50);
        stubUserDetail("user-15", p, detailWith(future("XU030", new BigDecimal("0.10"), "LONG")));
        when(keycloakUserAdminPort.getUser("user-15")).thenReturn(
                new AdminUserView("user-15", "u", "u@x.com", "U", "1",
                        false, true, List.of(), null, false, null, null));

        defaultEvaluator().evaluateMarginAlarms();

        ArgumentCaptor<AlarmTriggeredEvent> ev = ArgumentCaptor.forClass(AlarmTriggeredEvent.class);
        verify(notificationPort).notifyAlarmTriggered(ev.capture());
        assertThat(ev.getValue().recipientEmail()).isNull();
    }

    @Test
    @DisplayName("Keycloak hatası email null'a düşürür ama bildirim yine gider")
    void keycloakThrows_emailNull() {
        Portfolio p = portfolio("user-16", PortfolioType.HOLDINGS);
        when(portfolioRepository.findAll()).thenReturn(List.of(p));
        when(userPreferenceService.getMarginAlertThresholdPct("user-16")).thenReturn(50);
        stubUserDetail("user-16", p, detailWith(future("XU030", new BigDecimal("0.10"), "LONG")));
        when(keycloakUserAdminPort.getUser("user-16")).thenThrow(new RuntimeException("kc down"));

        defaultEvaluator().evaluateMarginAlarms();

        ArgumentCaptor<AlarmTriggeredEvent> ev = ArgumentCaptor.forClass(AlarmTriggeredEvent.class);
        verify(notificationPort).notifyAlarmTriggered(ev.capture());
        assertThat(ev.getValue().recipientEmail()).isNull();
    }

    // ── lowerBandPct negatif → 0'a kıstırılır (constructor branch) ──

    @Test
    @DisplayName("Negatif alt-bant 0'a kıstırılır")
    void negativeLowerBand_clampedToZero() {
        MarginAlarmEvaluator ev = newEvaluator(true, 360, -3);
        BigDecimal band = (BigDecimal) ReflectionTestUtils.getField(ev, "lowerBandPctPoints");
        assertThat(band).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("name null ise instrumentName sembole düşer")
    void nullName_instrumentNameFallsBackToSymbol() {
        Portfolio p = portfolio("user-17", PortfolioType.HOLDINGS);
        PortfolioHoldingResponse h = future("ASELS", new BigDecimal("0.10"), "LONG");
        h.setName(null);
        when(portfolioRepository.findAll()).thenReturn(List.of(p));
        when(userPreferenceService.getMarginAlertThresholdPct("user-17")).thenReturn(50);
        stubUserDetail("user-17", p, detailWith(h));
        when(keycloakUserAdminPort.getUser("user-17")).thenReturn(verifiedUser("user-17"));

        defaultEvaluator().evaluateMarginAlarms();

        ArgumentCaptor<AlarmTriggeredEvent> ev = ArgumentCaptor.forClass(AlarmTriggeredEvent.class);
        verify(notificationPort).notifyAlarmTriggered(ev.capture());
        assertThat(ev.getValue().instrumentName()).isEqualTo("ASELS");
    }
}
