package com.finance.portal.market.presentation.controller;

import com.finance.portal.common.presentation.dto.ApiResponse;
import com.finance.portal.market.application.bond.evds.BondPeriod;
import com.finance.portal.market.application.bond.evds.EvdsBondHistoryPoint;
import com.finance.portal.market.application.bond.evds.EvdsBondInstrument;
import com.finance.portal.market.application.bond.evds.EvdsBondService;
import com.finance.portal.market.presentation.dto.EvdsBondHistoryPointDto;
import com.finance.portal.market.presentation.dto.EvdsBondItemDto;
import com.finance.portal.market.presentation.dto.EvdsBondPageResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * EVDS tabanlı DİBS API controller'ı.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>GET /api/market/bonds/evds                          — sayfalı kıymet listesi
 *   <li>GET /api/market/bonds/evds/{instrumentCode}         — kıymet detayı
 *   <li>GET /api/market/bonds/evds/{instrumentCode}/history — tarihsel veri
 * </ul>
 */
@RestController
@RequestMapping("/api/market/bonds/evds")
public class EvdsBondController {

    private static final Logger log = LoggerFactory.getLogger(EvdsBondController.class);

    /** Geçerli sortBy değerleri */
    private static final Set<String> VALID_SORT_FIELDS = Set.of(
            "instrumentCode", "issueDate", "maturityDate", "remainingDays",
            "indicatorValue", "dailyChangePercent", "couponRate"
    );

    private static final int MAX_PAGE_SIZE = 100;

    private final EvdsBondService evdsBondService;

    public EvdsBondController(EvdsBondService evdsBondService) {
        this.evdsBondService = evdsBondService;
    }

    // ─────────────────────────────────────────────────────────────────────
    // GET /api/market/bonds/evds
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Sayfalı, filtrelenebilir ve sıralanabilir EVDS DİBS kıymet listesi.
     *
     * @param page              sayfa numarası (0-based, default 0)
     * @param size              sayfa boyutu (default 50, max 100)
     * @param search            instrumentCode içinde arama (büyük/küçük harf duyarsız)
     * @param type              tür filtresi: DİBS | Devlet Tahvili | Hazine Bonosu
     * @param minRemainingDays  kalan gün alt sınırı
     * @param maxRemainingDays  kalan gün üst sınırı
     * @param sortBy            sıralama alanı (default: maturityDate)
     * @param sortDir           sıralama yönü: asc | desc (default: asc)
     */
    @GetMapping
    public ResponseEntity<ApiResponse<EvdsBondPageResponse>> getBonds(
            @RequestParam(defaultValue = "0")             int page,
            @RequestParam(defaultValue = "50")            int size,
            @RequestParam(defaultValue = "")              String search,
            @RequestParam(defaultValue = "")              String type,
            @RequestParam(required = false)               Integer minRemainingDays,
            @RequestParam(required = false)               Integer maxRemainingDays,
            @RequestParam(defaultValue = "maturityDate")  String sortBy,
            @RequestParam(defaultValue = "asc")           String sortDir) {

        // Validasyon
        if (!VALID_SORT_FIELDS.contains(sortBy)) {
            return ResponseEntity.badRequest().body(ApiResponse.error(
                    "Geçersiz sortBy değeri: '" + sortBy + "'. " +
                    "Geçerli değerler: " + String.join(", ", VALID_SORT_FIELDS)));
        }
        if (!sortDir.equalsIgnoreCase("asc") && !sortDir.equalsIgnoreCase("desc")) {
            return ResponseEntity.badRequest().body(ApiResponse.error(
                    "Geçersiz sortDir değeri: '" + sortDir + "'. Geçerli değerler: asc, desc"));
        }

        // Sayfa boyutu sınırla
        size = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        page = Math.max(page, 0);

        // Cache'den tüm aktif kıymetleri al
        List<EvdsBondInstrument> all = evdsBondService.getEvdsBondsAll();

        // Filtrele
        List<EvdsBondInstrument> filtered = applyFilters(all, search, type, minRemainingDays, maxRemainingDays);

        // Sırala
        List<EvdsBondInstrument> sorted = applySort(filtered, sortBy, sortDir);

        // Paginate
        long totalItems = sorted.size();
        int fromIdx = page * size;
        int toIdx   = (int) Math.min((long) fromIdx + size, totalItems);

        List<EvdsBondItemDto> pageItems = (fromIdx >= totalItems)
                ? List.of()
                : sorted.subList(fromIdx, toIdx).stream()
                        .map(EvdsBondItemDto::from)
                        .collect(Collectors.toList());

        EvdsBondPageResponse response = EvdsBondPageResponse.of(pageItems, page, size, totalItems);

        log.debug("[EvdsBondController] GET /api/market/bonds/evds page={} size={} total={} filtered={}",
                page, size, all.size(), totalItems);

        return ResponseEntity.ok(ApiResponse.success(response,
                "EVDS bond list retrieved successfully. Source: TCMB EVDS"));
    }

    // ─────────────────────────────────────────────────────────────────────
    // GET /api/market/bonds/evds/{instrumentCode}
    // ─────────────────────────────────────────────────────────────────────

    @GetMapping("/{instrumentCode}")
    public ResponseEntity<ApiResponse<EvdsBondItemDto>> getBondDetail(
            @PathVariable String instrumentCode) {

        String code = normalizeCode(instrumentCode);
        try {
            EvdsBondInstrument instrument = evdsBondService.getEvdsBondDetail(code);
            return ResponseEntity.ok(ApiResponse.success(
                    EvdsBondItemDto.from(instrument),
                    "EVDS bond detail retrieved successfully. Source: TCMB EVDS"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404)
                    .body(ApiResponse.error("Kıymet bulunamadı: " + code));
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // GET /api/market/bonds/evds/{instrumentCode}/history
    // ─────────────────────────────────────────────────────────────────────

    @GetMapping("/{instrumentCode}/history")
    public ResponseEntity<ApiResponse<List<EvdsBondHistoryPointDto>>> getBondHistory(
            @PathVariable String instrumentCode,
            @RequestParam(defaultValue = "ONE_MONTH") String period) {

        String code = normalizeCode(instrumentCode);
        BondPeriod bondPeriod = parsePeriod(period);
        if (bondPeriod == null) {
            return ResponseEntity.badRequest().body(ApiResponse.error(
                    "Geçersiz period: '" + period + "'. " +
                    "Geçerli değerler: ONE_WEEK, ONE_MONTH, THREE_MONTHS, SIX_MONTHS, ONE_YEAR"));
        }

        List<EvdsBondHistoryPoint> history = evdsBondService.getEvdsBondHistory(code, bondPeriod);
        List<EvdsBondHistoryPointDto> dtos = history.stream()
                .map(EvdsBondHistoryPointDto::from)
                .toList();

        return ResponseEntity.ok(ApiResponse.success(dtos,
                "EVDS bond history retrieved successfully. Source: TCMB EVDS"));
    }

    // ─────────────────────────────────────────────────────────────────────
    // Filter / Sort helpers
    // ─────────────────────────────────────────────────────────────────────

    private List<EvdsBondInstrument> applyFilters(
            List<EvdsBondInstrument> list,
            String search, String type,
            Integer minDays, Integer maxDays) {

        return list.stream()
                .filter(b -> {
                    // search — instrumentCode içinde
                    if (search != null && !search.isBlank()) {
                        String q = search.trim().toUpperCase();
                        if (b.getInstrumentCode() == null || !b.getInstrumentCode().contains(q)) {
                            return false;
                        }
                    }
                    // type filtresi
                    if (type != null && !type.isBlank()) {
                        if (!type.equalsIgnoreCase(b.getType())) return false;
                    }
                    // kalan gün alt sınırı
                    if (minDays != null && b.getRemainingDays() < minDays) return false;
                    // kalan gün üst sınırı
                    if (maxDays != null && b.getRemainingDays() > maxDays) return false;
                    return true;
                })
                .collect(Collectors.toList());
    }

    private List<EvdsBondInstrument> applySort(
            List<EvdsBondInstrument> list, String sortBy, String sortDir) {

        Comparator<EvdsBondInstrument> comparator = switch (sortBy) {
            case "instrumentCode"    -> Comparator.comparing(
                    b -> b.getInstrumentCode() != null ? b.getInstrumentCode() : "");
            case "issueDate"         -> Comparator.comparing(
                    b -> b.getIssueDate() != null ? b.getIssueDate().toString() : "");
            case "remainingDays"     -> Comparator.comparingInt(
                    b -> b.getRemainingDays());
            case "indicatorValue"    -> Comparator.comparing(
                    b -> b.getIndicatorValue() != null ? b.getIndicatorValue() : BigDecimal.ZERO);
            case "dailyChangePercent"-> Comparator.comparing(
                    b -> b.getDailyChangePercent() != null ? b.getDailyChangePercent() : BigDecimal.ZERO);
            case "couponRate"        -> Comparator.comparing(
                    b -> b.getCouponRate() != null ? b.getCouponRate() : BigDecimal.ZERO);
            default /* maturityDate */-> Comparator.comparing(
                    b -> b.getMaturityDate() != null ? b.getMaturityDate().toString() : "");
        };

        if ("desc".equalsIgnoreCase(sortDir)) {
            comparator = comparator.reversed();
        }

        return list.stream().sorted(comparator).collect(Collectors.toList());
    }

    // ─────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────

    private String normalizeCode(String code) {
        if (code == null) return "";
        return code.trim().toUpperCase();
    }

    private BondPeriod parsePeriod(String period) {
        if (period == null) return BondPeriod.ONE_MONTH;
        try {
            return BondPeriod.valueOf(period.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
