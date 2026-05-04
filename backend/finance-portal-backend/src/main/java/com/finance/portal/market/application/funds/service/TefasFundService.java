package com.finance.portal.market.application.funds.service;

import com.finance.portal.market.application.funds.model.FundHistoryPoint;
import com.finance.portal.market.application.funds.model.FundHistoryResponse;
import com.finance.portal.market.application.funds.model.FundPeriod;
import com.finance.portal.market.application.funds.model.TefasFundItem;
import com.finance.portal.market.application.funds.model.TefasFundPageResponse;
import com.finance.portal.market.infrastructure.external.tefas.HangiKrediFundChartClient;
import com.finance.portal.market.infrastructure.external.tefas.TefasFundClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TefasFundService {

    private static final Logger log = LoggerFactory.getLogger(TefasFundService.class);

    private final TefasFundClient tefasFundClient;
    private final HangiKrediFundChartClient chartClient;

    public TefasFundService(TefasFundClient tefasFundClient, HangiKrediFundChartClient chartClient) {
        this.tefasFundClient = tefasFundClient;
        this.chartClient = chartClient;
    }

    @Cacheable(cacheNames = "market.tefas.funds", key = "'all:' + #kind")
    public List<TefasFundItem> getAllFunds(String kind) {
        log.info("Fetching TEFAS funds from source (kind={})", kind);
        List<TefasFundItem> funds = tefasFundClient.fetchFunds(kind);
        // Liste scraping sırasında id'leri kaydet
        funds.forEach(f -> {
            if (f.getHangiKrediId() != null && f.getHangiKrediId() > 0) {
                chartClient.registerFundId(f.getCode(), f.getHangiKrediId());
            }
        });
        return funds;
    }

    /** Fon detayını HangiKredi'den çeker — zengin veri (fiyat, risk, dağılım, grafik) */
    @Cacheable(cacheNames = "market.tefas.funds", key = "'detail:' + #code")
    public TefasFundItem getFundDetail(String code) {
        log.info("Fetching TEFAS fund detail for {}", code);
        TefasFundItem detail = tefasFundClient.fetchFundDetail(code);

        // Detay sayfasından id'yi kaydet
        if (detail != null && detail.getHangiKrediId() != null && detail.getHangiKrediId() > 0) {
            chartClient.registerFundId(code, detail.getHangiKrediId());
        }

        // Dönem getirilerini liste sayfasından al (detay sayfasında yok)
        List<TefasFundItem> listItems = getAllFunds("YAT");
        TefasFundItem listItem = listItems.stream()
                .filter(f -> code.equalsIgnoreCase(f.getCode()))
                .findFirst().orElse(null);

        // Liste verisinden id'yi de kaydet (detay'da yoksa)
        if (listItem != null && listItem.getHangiKrediId() != null && listItem.getHangiKrediId() > 0) {
            chartClient.registerFundId(code, listItem.getHangiKrediId());
        }

        if (detail != null) {
            // Dönem getirilerini liste verisinden kopyala
            if (listItem != null) {
                if (detail.getReturn1M() == null) detail.setReturn1M(listItem.getReturn1M());
                if (detail.getReturn3M() == null) detail.setReturn3M(listItem.getReturn3M());
                if (detail.getReturn6M() == null) detail.setReturn6M(listItem.getReturn6M());
                if (detail.getReturn1Y() == null) detail.setReturn1Y(listItem.getReturn1Y());
                if (detail.getReturn3Y() == null) detail.setReturn3Y(listItem.getReturn3Y());
                if (detail.getReturn5Y() == null) detail.setReturn5Y(listItem.getReturn5Y());
                if (detail.getNumberOfInvestors() == null) detail.setNumberOfInvestors(listItem.getNumberOfInvestors());
                // id yoksa listeden al
                if ((detail.getHangiKrediId() == null || detail.getHangiKrediId() == 0)
                        && listItem.getHangiKrediId() != null) {
                    detail.setHangiKrediId(listItem.getHangiKrediId());
                }
            }
            return detail;
        }

        // Fallback: liste verisini döndür
        return listItem;
    }

    /**
     * Fon tarihsel grafik verisi.
     * HangiKredi /api/investment-services/v1/chart/fund endpoint'inden çeker.
     */
    @Cacheable(cacheNames = "market.fund.history", key = "#code + ':' + #period.name()")
    public FundHistoryResponse getFundHistory(String code, FundPeriod period) {
        log.info("Fetching fund history: code={}, period={}", code, period);

        // getFundDetail cache'den dönse bile hangiKrediId içeriyor
        // Ama @Cacheable self-invocation'da çalışmaz — tefasFundClient'ı direkt kullan
        Long hangiKrediId = resolveHangiKrediId(code);
        log.info("Resolved hangiKrediId for {}: {}", code, hangiKrediId);

        List<FundHistoryPoint> points = chartClient.fetchChartData(code, hangiKrediId, period.getDays());

        if (points.isEmpty()) {
            log.warn("No chart data returned for code={}, period={}", code, period);
        }

        return new FundHistoryResponse(code.toUpperCase(), period.name(), points);
    }

    /**
     * HangiKredi fund id'sini bulur.
     * 1. chartClient in-memory cache'ine bak (daha önce register edilmiş olabilir)
     * 2. Yoksa tefasFundClient.fetchFundDetail ile HTML'den parse et
     */
    private Long resolveHangiKrediId(String code) {
        // 1. In-memory cache'e bak — getFundDetail veya getAllFunds daha önce çağrıldıysa burada olur
        Long cached = chartClient.getRegisteredId(code);
        if (cached != null && cached > 0) {
            log.info("Resolved HangiKredi id from cache: {} -> {}", code, cached);
            return cached;
        }

        // 2. Detay sayfasını doğrudan çek
        try {
            TefasFundItem detail = tefasFundClient.fetchFundDetail(code);
            if (detail != null) {
                Long id = detail.getHangiKrediId();
                log.info("fetchFundDetail returned id={} for code={}", id, code);
                if (id != null && id > 0) {
                    chartClient.registerFundId(code, id);
                    return id;
                }
            } else {
                log.warn("fetchFundDetail returned null for code={}", code);
            }
        } catch (Exception e) {
            log.warn("Failed to resolve id from detail for {}: {}", code, e.getMessage());
        }

        log.warn("Could not resolve HangiKredi id for code={}", code);
        return null;
    }

    /** Geriye dönük uyumluluk için — detay döndürür */
    public List<TefasFundItem> getFundByCode(String code) {
        TefasFundItem item = getFundDetail(code);
        return item != null ? List.of(item) : List.of();
    }

    public TefasFundPageResponse getPagedFunds(String kind, int page, int size) {
        List<TefasFundItem> all = getAllFunds(kind);
        int totalElements = all.size();
        int totalPages = size > 0 ? (int) Math.ceil((double) totalElements / size) : 0;
        int start = page * size;
        int end = Math.min(start + size, totalElements);
        List<TefasFundItem> content = (start >= totalElements) ? List.of() : all.subList(start, end);

        TefasFundPageResponse response = new TefasFundPageResponse();
        response.setContent(content);
        response.setPage(page);
        response.setSize(size);
        response.setTotalElements(totalElements);
        response.setTotalPages(totalPages);
        return response;
    }
}
