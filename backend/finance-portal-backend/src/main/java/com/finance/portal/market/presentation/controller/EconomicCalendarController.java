package com.finance.portal.market.presentation.controller;

import com.finance.portal.common.presentation.dto.ApiResponse;
import com.finance.portal.market.application.calendar.EconomicCalendarService;
import com.finance.portal.market.application.calendar.model.EconomicCalendarEvent;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * Ekonomik takvim — Finnhub destekli, küresel makro veri açıklamaları.
 *
 * <p>{@code GET /api/market/economy/calendar?from=YYYY-MM-DD&to=YYYY-MM-DD}
 */
@RestController
@RequestMapping("/api/market/economy/calendar")
public class EconomicCalendarController {

    private final EconomicCalendarService service;

    public EconomicCalendarController(EconomicCalendarService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<EconomicCalendarEvent>>> getCalendar(
            @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam("to")   @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        List<EconomicCalendarEvent> events = service.getEvents(from, to);
        return ResponseEntity.ok(ApiResponse.success(events, "Ekonomik takvim olayları"));
    }
}
