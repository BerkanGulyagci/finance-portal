package com.finance.portal.market.application.calendar;

import com.finance.portal.market.application.calendar.model.EconomicCalendarEvent;
import com.finance.portal.market.application.calendar.port.EconomicCalendarPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EconomicCalendarServiceTest {

    @Mock
    private EconomicCalendarPort port;

    @InjectMocks
    private EconomicCalendarService service;

    private static EconomicCalendarEvent event(String time, String country, String evt, String currency) {
        EconomicCalendarEvent e = new EconomicCalendarEvent();
        e.setTime(time);
        e.setCountry(country);
        e.setEvent(evt);
        e.setCurrency(currency);
        return e;
    }

    @Test
    void shortRange_singleFetch_returnsPortResultDirectly() {
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 1, 20); // 19 days <= CHUNK_DAYS (30)

        EconomicCalendarEvent e1 = event("2026-01-05 08:30:00", "US", "CPI", "USD");
        EconomicCalendarEvent e2 = event("2026-01-10 12:00:00", "EU", "PMI", "EUR");
        when(port.fetch(from, to)).thenReturn(List.of(e1, e2));

        List<EconomicCalendarEvent> result = service.getEvents(from, to);

        assertThat(result).containsExactly(e1, e2);
        verify(port, times(1)).fetch(from, to);
    }

    @Test
    void reversedRange_isSwapped_beforeFetch() {
        LocalDate from = LocalDate.of(2026, 2, 20);
        LocalDate to = LocalDate.of(2026, 2, 1); // from > to -> swapped

        when(port.fetch(any(), any())).thenReturn(Collections.emptyList());

        service.getEvents(from, to);

        // single fetch with swapped (start=to, end=from)
        verify(port).fetch(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 20));
    }

    @Test
    void emptyUpstream_returnsEmptyList() {
        LocalDate from = LocalDate.of(2026, 3, 1);
        LocalDate to = LocalDate.of(2026, 3, 5);
        when(port.fetch(from, to)).thenReturn(Collections.emptyList());

        List<EconomicCalendarEvent> result = service.getEvents(from, to);

        assertThat(result).isEmpty();
    }

    @Test
    void longRange_isChunked_into30DaySegments() {
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 4, 1); // 90 days > CHUNK_DAYS(30) -> chunked

        when(port.fetch(any(), any())).thenReturn(Collections.emptyList());

        service.getEvents(from, to);

        ArgumentCaptor<LocalDate> startCap = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> endCap = ArgumentCaptor.forClass(LocalDate.class);
        // 90 days / 30 = 3 chunks, last partial -> ceil = 4 chunks
        verify(port, times(4)).fetch(startCap.capture(), endCap.capture());

        List<LocalDate> starts = startCap.getAllValues();
        List<LocalDate> ends = endCap.getAllValues();

        // First chunk starts at range start
        assertThat(starts.get(0)).isEqualTo(from);
        // Each chunk spans 30 days (chunkStart .. chunkStart+29)
        assertThat(ends.get(0)).isEqualTo(LocalDate.of(2026, 1, 30));
        // Subsequent chunk starts the day after previous chunk end (no gaps)
        assertThat(starts.get(1)).isEqualTo(ends.get(0).plusDays(1));
        assertThat(starts.get(2)).isEqualTo(ends.get(1).plusDays(1));
        // Last chunk end is clamped to range end
        assertThat(ends.get(ends.size() - 1)).isEqualTo(to);
        // No chunk extends beyond range end
        assertThat(ends).allSatisfy(d -> assertThat(d).isBeforeOrEqualTo(to));
    }

    @Test
    void chunkedResults_areDedupedAndSortedByTime() {
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 3, 1); // 59 days -> chunked

        EconomicCalendarEvent later = event("2026-02-15 10:00:00", "US", "NFP", "USD");
        EconomicCalendarEvent earlier = event("2026-01-15 09:00:00", "TR", "CBRT", "TRY");
        EconomicCalendarEvent dupOfEarlier = event("2026-01-15 09:00:00", "TR", "CBRT", "TRY");

        // chunk 1 returns later+earlier (out of order), chunk 2 returns a duplicate
        when(port.fetch(any(), any()))
                .thenReturn(new ArrayList<>(List.of(later, earlier)))
                .thenReturn(new ArrayList<>(List.of(dupOfEarlier)));

        List<EconomicCalendarEvent> result = service.getEvents(from, to);

        // dedup: 2 unique events; sorted ascending by time
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getTime()).isEqualTo("2026-01-15 09:00:00");
        assertThat(result.get(1).getTime()).isEqualTo("2026-02-15 10:00:00");
    }

    @Test
    void nullTimeEvents_sortLast_withoutNpe() {
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 3, 1); // chunked

        EconomicCalendarEvent withTime = event("2026-01-20 09:00:00", "US", "CPI", "USD");
        EconomicCalendarEvent nullTime = event(null, "EU", "Holiday", "EUR");

        when(port.fetch(any(), any()))
                .thenReturn(new ArrayList<>(List.of(nullTime, withTime)))
                .thenReturn(new ArrayList<>());

        List<EconomicCalendarEvent> result = service.getEvents(from, to);

        assertThat(result).hasSize(2);
        assertThat(result.get(0)).isSameAs(withTime);
        assertThat(result.get(1)).isSameAs(nullTime);
    }

    @Test
    void chunkException_isSwallowed_otherChunksStillMerged() {
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 3, 1); // chunked

        EconomicCalendarEvent ok = event("2026-02-10 08:00:00", "US", "Retail", "USD");

        when(port.fetch(any(), any()))
                .thenThrow(new RuntimeException("upstream 500"))
                .thenReturn(new ArrayList<>(List.of(ok)));

        List<EconomicCalendarEvent> result = service.getEvents(from, to);

        // failed chunk swallowed -> only the successful chunk's event survives
        assertThat(result).containsExactly(ok);
    }

    @Test
    void rangeOverMax_isClampedTo400Days() {
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = from.plusDays(500); // > MAX_RANGE_DAYS(400)

        when(port.fetch(any(), any())).thenReturn(Collections.emptyList());

        service.getEvents(from, to);

        ArgumentCaptor<LocalDate> endCap = ArgumentCaptor.forClass(LocalDate.class);
        verify(port, org.mockito.Mockito.atLeastOnce()).fetch(any(), endCap.capture());

        LocalDate maxEnd = from.plusDays(400);
        // no chunk end should exceed the clamped range end
        assertThat(endCap.getAllValues()).allSatisfy(d -> assertThat(d).isBeforeOrEqualTo(maxEnd));
        assertThat(endCap.getAllValues()).contains(maxEnd);
    }
}
