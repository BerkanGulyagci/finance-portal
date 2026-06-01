package com.finance.portal.market.application.economy;

import com.finance.portal.market.application.economy.model.EconomySeriesPoint;
import com.finance.portal.market.application.economy.model.LoanRates;
import com.finance.portal.market.application.economy.port.EconomyDataPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoanRateServiceTest {

    private static final String CODE_PERSONAL = "TP.KTF10";
    private static final String CODE_VEHICLE = "TP.KTF11";
    private static final String CODE_HOUSING = "TP.KTF12";
    private static final String CODE_COMMERCIAL = "TP.KTF17";

    @Mock
    private EconomyDataPort economyDataPort;

    private LoanRateService service;

    @BeforeEach
    void setUp() {
        service = new LoanRateService(economyDataPort);
    }

    private static EconomySeriesPoint point(String period, String value) {
        return new EconomySeriesPoint(period, new BigDecimal(value), 0L);
    }

    @Test
    @DisplayName("happy path: maps latest of each loan type + period from personal series")
    void getLoanRates_happyPath() {
        when(economyDataPort.fetchSeries(eq(CODE_PERSONAL), any(), any()))
                .thenReturn(List.of(point("01-05-2026", "50"), point("08-05-2026", "52")));
        when(economyDataPort.fetchSeries(eq(CODE_VEHICLE), any(), any()))
                .thenReturn(List.of(point("08-05-2026", "48")));
        when(economyDataPort.fetchSeries(eq(CODE_HOUSING), any(), any()))
                .thenReturn(List.of(point("08-05-2026", "40")));
        when(economyDataPort.fetchSeries(eq(CODE_COMMERCIAL), any(), any()))
                .thenReturn(List.of(point("08-05-2026", "55")));

        LoanRates r = service.getLoanRates();

        assertThat(r.getPersonal()).isEqualByComparingTo("52");
        assertThat(r.getVehicle()).isEqualByComparingTo("48");
        assertThat(r.getHousing()).isEqualByComparingTo("40");
        assertThat(r.getCommercial()).isEqualByComparingTo("55");
        // period comes from latest personal point
        assertThat(r.getPeriod()).isEqualTo("08-05-2026");
        assertThat(r.getSource()).isEqualTo("TCMB EVDS");
    }

    @Test
    @DisplayName("empty upstream: all rate fields + period null, source still set")
    void getLoanRates_emptyUpstream() {
        when(economyDataPort.fetchSeries(any(), any(), any())).thenReturn(Collections.emptyList());

        LoanRates r = service.getLoanRates();

        assertThat(r.getPersonal()).isNull();
        assertThat(r.getVehicle()).isNull();
        assertThat(r.getHousing()).isNull();
        assertThat(r.getCommercial()).isNull();
        assertThat(r.getPeriod()).isNull();
        assertThat(r.getSource()).isEqualTo("TCMB EVDS");
    }

    @Test
    @DisplayName("partial: personal empty -> personal+period null, others mapped")
    void getLoanRates_partialPersonalMissing() {
        when(economyDataPort.fetchSeries(eq(CODE_PERSONAL), any(), any()))
                .thenReturn(Collections.emptyList());
        when(economyDataPort.fetchSeries(eq(CODE_VEHICLE), any(), any()))
                .thenReturn(List.of(point("08-05-2026", "48")));
        when(economyDataPort.fetchSeries(eq(CODE_HOUSING), any(), any()))
                .thenReturn(List.of(point("08-05-2026", "40")));
        when(economyDataPort.fetchSeries(eq(CODE_COMMERCIAL), any(), any()))
                .thenReturn(List.of(point("08-05-2026", "55")));

        LoanRates r = service.getLoanRates();

        assertThat(r.getPersonal()).isNull();
        assertThat(r.getPeriod()).isNull(); // period sourced from personal
        assertThat(r.getVehicle()).isEqualByComparingTo("48");
        assertThat(r.getHousing()).isEqualByComparingTo("40");
        assertThat(r.getCommercial()).isEqualByComparingTo("55");
    }

    @Test
    @DisplayName("date window passed to port is [now-3m, now]")
    void getLoanRates_usesThreeMonthWindow() {
        final LocalDate[] captured = new LocalDate[2];
        when(economyDataPort.fetchSeries(any(), any(), any())).thenAnswer(inv -> {
            captured[0] = inv.getArgument(1);
            captured[1] = inv.getArgument(2);
            return Collections.emptyList();
        });

        service.getLoanRates();

        assertThat(captured[1]).isEqualTo(LocalDate.now());
        assertThat(captured[0]).isEqualTo(LocalDate.now().minusMonths(3));
    }
}
