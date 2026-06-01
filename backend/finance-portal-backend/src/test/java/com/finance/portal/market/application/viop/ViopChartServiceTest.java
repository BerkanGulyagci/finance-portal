package com.finance.portal.market.application.viop;

import com.finance.portal.market.application.viop.model.ViopChartPoint;
import com.finance.portal.market.application.viop.port.ViopChartDataPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link ViopChartService} birim testleri. {@link ViopChartDataPort} ve
 * {@link ViopIndexCodeMapper} mock'lanır. Retry/fallback dayanıklılık yolları kapsanır.
 */
@ExtendWith(MockitoExtension.class)
class ViopChartServiceTest {

    @Mock
    private ViopChartDataPort chartDataPort;

    @Mock
    private ViopIndexCodeMapper indexCodeMapper;

    private ViopChartService service;

    @BeforeEach
    void setUp() {
        service = new ViopChartService(chartDataPort, indexCodeMapper);
    }

    private static ViopChartPoint point(long ts, String value) {
        return new ViopChartPoint(ts, "2026-01-01T10:00:00", new BigDecimal(value));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> lastGoodMap() {
        return (Map<String, Object>) ReflectionTestUtils.getField(service, "lastGood");
    }

    /** Reflection ile private CachedSeries record örneği yaratır (yaşlandırılmış zaman damgasıyla). */
    private static Object cachedSeries(long timestamp, List<ViopChartPoint> data) throws Exception {
        Class<?> cls = Class.forName(
                "com.finance.portal.market.application.viop.ViopChartService$CachedSeries");
        Constructor<?> ctor = cls.getDeclaredConstructor(long.class, List.class);
        ctor.setAccessible(true);
        return ctor.newInstance(timestamp, data);
    }

    // ──────────────────────────────────────────── endeks kodu çözümleme

    @Test
    @DisplayName("getChart: F_ prefixli giriş mapper'a sokulmadan doğrudan endeks kodu olur")
    void getChart_fPrefixBypassesMapper() {
        when(chartDataPort.fetchChart(eq("F_AKBNK0626"), any(), any(), anyInt()))
                .thenReturn(List.of(point(1L, "10")));
        List<ViopChartPoint> r = service.getChart("f_akbnk0626", ViopChartPeriod.ONE_DAY);
        assertThat(r).hasSize(1);
        verifyNoInteractions(indexCodeMapper);
        verify(chartDataPort).fetchChart(eq("F_AKBNK0626"), any(), any(), eq(60));
    }

    @Test
    @DisplayName("getChart: Akbank adı mapper ile İş Yatırım koduna çevrilir")
    void getChart_mapsViaIndexCodeMapper() {
        when(indexCodeMapper.toIsYatirimEndeksCode("AKBNK (30 Haz 26) Vadeli FIZ."))
                .thenReturn(Optional.of("F_AKBNK0626"));
        when(chartDataPort.fetchChart(eq("F_AKBNK0626"), any(), any(), anyInt()))
                .thenReturn(List.of(point(1L, "10"), point(2L, "11")));
        List<ViopChartPoint> r = service.getChart("AKBNK (30 Haz 26) Vadeli FIZ.", ViopChartPeriod.ONE_MONTH);
        assertThat(r).hasSize(2);
        verify(chartDataPort).fetchChart(eq("F_AKBNK0626"), any(), any(), eq(60));
    }

    @Test
    @DisplayName("getChart: mapper boş dönerse UnsupportedViopContractException")
    void getChart_unsupportedContract() {
        when(indexCodeMapper.toIsYatirimEndeksCode(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getChart("OPSIYON Call", ViopChartPeriod.ONE_DAY))
                .isInstanceOf(UnsupportedViopContractException.class)
                .hasMessageContaining("grafik kodu üretilemedi");
        verifyNoInteractions(chartDataPort);
    }

    @Test
    @DisplayName("getChart: giriş trim edilip (TurkishCharFixer) mapper'a temiz ad gider")
    void getChart_trimsInput() {
        // ASCII-temiz ad → TurkishCharFixer no-op, sadece trim uygulanır
        when(indexCodeMapper.toIsYatirimEndeksCode("AKBNK (30 Haz 26) Vadeli FIZ."))
                .thenReturn(Optional.of("F_AKBNK0626"));
        when(chartDataPort.fetchChart(eq("F_AKBNK0626"), any(), any(), anyInt()))
                .thenReturn(List.of(point(1L, "10")));
        List<ViopChartPoint> r = service.getChart("   AKBNK (30 Haz 26) Vadeli FIZ.   ", ViopChartPeriod.ONE_DAY);
        assertThat(r).hasSize(1);
        verify(indexCodeMapper).toIsYatirimEndeksCode("AKBNK (30 Haz 26) Vadeli FIZ.");
    }

    // ──────────────────────────────────────────── period → gün penceresi

    @Test
    @DisplayName("getChart: period gün sayısına göre from penceresini hesaplar")
    void getChart_periodWindow() {
        when(chartDataPort.fetchChart(eq("F_X0626"), any(), any(), anyInt()))
                .thenReturn(List.of(point(1L, "10")));
        service.getChart("F_X0626", ViopChartPeriod.ONE_YEAR);

        org.mockito.ArgumentCaptor<LocalDateTime> fromCap = org.mockito.ArgumentCaptor.forClass(LocalDateTime.class);
        org.mockito.ArgumentCaptor<LocalDateTime> toCap = org.mockito.ArgumentCaptor.forClass(LocalDateTime.class);
        verify(chartDataPort).fetchChart(eq("F_X0626"), fromCap.capture(), toCap.capture(), eq(60));
        long days = java.time.Duration.between(fromCap.getValue(), toCap.getValue()).toDays();
        assertThat(days).isEqualTo(365);
    }

    // ──────────────────────────────────────────── cache / retry / fallback

    @Test
    @DisplayName("getChart: başarılı seri cache'lenir, TTL içindeki 2. çağrı kaynağı yormaz")
    void getChart_freshCacheHit() {
        when(chartDataPort.fetchChart(eq("F_X0626"), any(), any(), anyInt()))
                .thenReturn(List.of(point(1L, "10")));
        List<ViopChartPoint> first = service.getChart("F_X0626", ViopChartPeriod.ONE_DAY);
        List<ViopChartPoint> second = service.getChart("F_X0626", ViopChartPeriod.ONE_DAY);
        assertThat(first).hasSize(1);
        assertThat(second).isSameAs(first);
        verify(chartDataPort, times(1)).fetchChart(eq("F_X0626"), any(), any(), anyInt());
    }

    @Test
    @DisplayName("getChart: kaynak boş + cache yok → boş liste")
    void getChart_emptyNoCache() {
        when(chartDataPort.fetchChart(any(), any(), any(), anyInt())).thenReturn(List.of());
        List<ViopChartPoint> r = service.getChart("F_X0626", ViopChartPeriod.ONE_DAY);
        assertThat(r).isEmpty();
        // 3 deneme yapılmalı
        verify(chartDataPort, times(3)).fetchChart(any(), any(), any(), anyInt());
    }

    @Test
    @DisplayName("getChart: kaynak null dönerse boş kabul edilir (3 deneme)")
    void getChart_nullTreatedEmpty() {
        when(chartDataPort.fetchChart(any(), any(), any(), anyInt())).thenReturn(null);
        assertThat(service.getChart("F_X0626", ViopChartPeriod.ONE_DAY)).isEmpty();
        verify(chartDataPort, times(3)).fetchChart(any(), any(), any(), anyInt());
    }

    @Test
    @DisplayName("getChart: fetch exception fırlatır → tekrar dener, sonunda boş")
    void getChart_exceptionRetriesThenEmpty() {
        when(chartDataPort.fetchChart(any(), any(), any(), anyInt()))
                .thenThrow(new RuntimeException("flaky"));
        assertThat(service.getChart("F_X0626", ViopChartPeriod.ONE_DAY)).isEmpty();
        verify(chartDataPort, times(3)).fetchChart(any(), any(), any(), anyInt());
    }

    @Test
    @DisplayName("getChart: ilk denemeler boş/hata, son deneme başarılı → seri döner")
    void getChart_retryThenSuccess() {
        when(chartDataPort.fetchChart(any(), any(), any(), anyInt()))
                .thenThrow(new RuntimeException("flaky"))
                .thenReturn(List.of())
                .thenReturn(List.of(point(1L, "10")));
        List<ViopChartPoint> r = service.getChart("F_X0626", ViopChartPeriod.ONE_DAY);
        assertThat(r).hasSize(1);
        verify(chartDataPort, times(3)).fetchChart(any(), any(), any(), anyInt());
    }

    @Test
    @DisplayName("getChart: kaynak boş ama yaşlı başarılı seri var → son başarılı seri sunulur (stale fallback)")
    void getChart_staleFallback() throws Exception {
        // TTL'den eski bir cache girişi enjekte et (now - 10dk)
        long agedTs = System.currentTimeMillis() - 600_000L;
        List<ViopChartPoint> stale = List.of(point(99L, "42"));
        lastGoodMap().put("F_X0626|ONE_DAY", cachedSeries(agedTs, stale));

        when(chartDataPort.fetchChart(any(), any(), any(), anyInt())).thenReturn(List.of());
        List<ViopChartPoint> r = service.getChart("F_X0626", ViopChartPeriod.ONE_DAY);

        assertThat(r).isSameAs(stale);
        // taze görülmediği için kaynağa gidildi (3 deneme), ama boş dönünce stale sunuldu
        verify(chartDataPort, times(3)).fetchChart(any(), any(), any(), anyInt());
    }

    @Test
    @DisplayName("getChart: yaşlı cache var, kaynak taze veri döndürürse taze veri cache'i günceller")
    void getChart_staleRefreshedWithFresh() throws Exception {
        long agedTs = System.currentTimeMillis() - 600_000L;
        lastGoodMap().put("F_X0626|ONE_DAY", cachedSeries(agedTs, List.of(point(99L, "42"))));

        List<ViopChartPoint> fresh = List.of(point(1L, "10"), point(2L, "11"));
        when(chartDataPort.fetchChart(any(), any(), any(), anyInt())).thenReturn(fresh);
        List<ViopChartPoint> r = service.getChart("F_X0626", ViopChartPeriod.ONE_DAY);

        assertThat(r).isSameAs(fresh);
        verify(chartDataPort, times(1)).fetchChart(any(), any(), any(), anyInt());
    }

    @Test
    @DisplayName("getChart: farklı period aynı kontrat için ayrı cache anahtarı kullanır")
    void getChart_perPeriodKey() {
        when(chartDataPort.fetchChart(any(), any(), any(), anyInt()))
                .thenReturn(List.of(point(1L, "10")));
        service.getChart("F_X0626", ViopChartPeriod.ONE_DAY);
        service.getChart("F_X0626", ViopChartPeriod.ONE_WEEK);
        // iki farklı period → iki ayrı fetch
        verify(chartDataPort, times(2)).fetchChart(any(), any(), any(), anyInt());
        assertThat(lastGoodMap()).containsKeys("F_X0626|ONE_DAY", "F_X0626|ONE_WEEK");
    }
}
