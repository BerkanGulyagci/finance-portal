package com.finance.portal.market.application.economy;

import com.finance.portal.market.application.economy.model.DepositRates;
import com.finance.portal.market.application.economy.model.EconomyIndicator;
import com.finance.portal.market.application.economy.model.EconomySeriesPoint;
import com.finance.portal.market.application.economy.port.EconomyDataPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DepositRateServiceTest {

    private static final String CODE_1M = "TP.TRY.MT01";
    private static final String CODE_3M = "TP.TRY.MT02";
    private static final String CODE_6M = "TP.TRY.MT03";
    private static final String CODE_1Y = "TP.TRY.MT04";

    @Mock
    private EconomyDataPort economyDataPort;
    @Mock
    private EconomyService economyService;

    private DepositRateService service;

    @BeforeEach
    void setUp() {
        service = new DepositRateService(economyDataPort, economyService);
        ReflectionTestUtils.setField(service, "stopaj6m", new BigDecimal("10"));
        ReflectionTestUtils.setField(service, "stopaj1y", new BigDecimal("7.5"));
        ReflectionTestUtils.setField(service, "stopajOver1y", new BigDecimal("5"));
    }

    private static EconomySeriesPoint point(String period, String value) {
        return new EconomySeriesPoint(period, new BigDecimal(value), 0L);
    }

    @Test
    @DisplayName("happy path: maps latest point of each tenor + inflation + config stopaj")
    void getDepositRates_happyPath() {
        when(economyDataPort.fetchSeries(eq(CODE_1M), any(), any()))
                .thenReturn(List.of(point("2026-04", "40"), point("2026-05", "42")));
        when(economyDataPort.fetchSeries(eq(CODE_3M), any(), any()))
                .thenReturn(List.of(point("2026-04", "44"), point("2026-05", "45")));
        when(economyDataPort.fetchSeries(eq(CODE_6M), any(), any()))
                .thenReturn(List.of(point("2026-05", "47")));
        when(economyDataPort.fetchSeries(eq(CODE_1Y), any(), any()))
                .thenReturn(List.of(point("2026-05", "49")));

        EconomyIndicator tufe = new EconomyIndicator();
        tufe.setKey("tufe");
        tufe.setYoyChangePercent(new BigDecimal("38.50"));
        EconomyIndicator other = new EconomyIndicator();
        other.setKey("usdTry");
        other.setYoyChangePercent(new BigDecimal("99"));
        when(economyService.getSummary()).thenReturn(List.of(other, tufe));

        DepositRates r = service.getDepositRates();

        // latest = last element of each series
        assertThat(r.getUpTo1Month()).isEqualByComparingTo("42");
        assertThat(r.getUpTo3Months()).isEqualByComparingTo("45");
        assertThat(r.getUpTo6Months()).isEqualByComparingTo("47");
        assertThat(r.getUpTo1Year()).isEqualByComparingTo("49");
        // period taken from the 3-month series latest point
        assertThat(r.getPeriod()).isEqualTo("2026-05");
        // inflation from the indicator whose key == "tufe"
        assertThat(r.getInflationYoy()).isEqualByComparingTo("38.50");
        // stopaj from config
        assertThat(r.getStopaj6m()).isEqualByComparingTo("10");
        assertThat(r.getStopaj1y()).isEqualByComparingTo("7.5");
        assertThat(r.getStopajOver1y()).isEqualByComparingTo("5");
        assertThat(r.getSource()).isEqualTo("TCMB EVDS");
    }

    @Test
    @DisplayName("empty upstream: all rate fields null, but stopaj/source still populated")
    void getDepositRates_emptyUpstream() {
        when(economyDataPort.fetchSeries(any(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(economyService.getSummary()).thenReturn(Collections.emptyList());

        DepositRates r = service.getDepositRates();

        assertThat(r.getUpTo1Month()).isNull();
        assertThat(r.getUpTo3Months()).isNull();
        assertThat(r.getUpTo6Months()).isNull();
        assertThat(r.getUpTo1Year()).isNull();
        assertThat(r.getPeriod()).isNull();
        assertThat(r.getInflationYoy()).isNull();
        // config + source always set
        assertThat(r.getStopaj6m()).isEqualByComparingTo("10");
        assertThat(r.getSource()).isEqualTo("TCMB EVDS");
    }

    @Test
    @DisplayName("inflation: tufe indicator missing -> inflationYoy null")
    void getDepositRates_noTufeIndicator() {
        when(economyDataPort.fetchSeries(any(), any(), any())).thenReturn(Collections.emptyList());
        EconomyIndicator other = new EconomyIndicator();
        other.setKey("usdTry");
        other.setYoyChangePercent(new BigDecimal("12"));
        when(economyService.getSummary()).thenReturn(List.of(other));

        DepositRates r = service.getDepositRates();

        assertThat(r.getInflationYoy()).isNull();
    }

    @Test
    @DisplayName("graceful fallback: getSummary throws -> inflationYoy null, rates still mapped")
    void getDepositRates_summaryThrows() {
        when(economyDataPort.fetchSeries(eq(CODE_3M), any(), any()))
                .thenReturn(List.of(point("2026-05", "45")));
        when(economyDataPort.fetchSeries(eq(CODE_1M), any(), any())).thenReturn(Collections.emptyList());
        when(economyDataPort.fetchSeries(eq(CODE_6M), any(), any())).thenReturn(Collections.emptyList());
        when(economyDataPort.fetchSeries(eq(CODE_1Y), any(), any())).thenReturn(Collections.emptyList());
        when(economyService.getSummary()).thenThrow(new RuntimeException("EVDS down"));

        DepositRates r = service.getDepositRates();

        assertThat(r.getInflationYoy()).isNull();
        assertThat(r.getUpTo3Months()).isEqualByComparingTo("45");
        assertThat(r.getPeriod()).isEqualTo("2026-05");
        assertThat(r.getSource()).isEqualTo("TCMB EVDS");
    }

    @Test
    @DisplayName("date window passed to port is [now-3m, now]")
    void getDepositRates_usesThreeMonthWindow() {
        final LocalDate[] captured = new LocalDate[2];
        when(economyDataPort.fetchSeries(any(), any(), any())).thenAnswer(inv -> {
            captured[0] = inv.getArgument(1);
            captured[1] = inv.getArgument(2);
            return Collections.emptyList();
        });
        when(economyService.getSummary()).thenReturn(Collections.emptyList());

        service.getDepositRates();

        assertThat(captured[1]).isEqualTo(LocalDate.now());
        assertThat(captured[0]).isEqualTo(LocalDate.now().minusMonths(3));
    }
}
