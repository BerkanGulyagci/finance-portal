package com.finance.portal.market.application.index;

import com.fasterxml.jackson.core.type.TypeReference;
import com.finance.portal.common.application.exception.ResourceNotFoundException;
import com.finance.portal.common.infrastructure.cache.LastKnownGoodCache;
import com.finance.portal.market.application.index.BistIndexCatalog.IndexInfo;
import com.finance.portal.market.application.stock.StockQueryService;
import com.finance.portal.market.application.stock.StockSummary;
import com.finance.portal.market.application.stock.StockSymbolProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * BIST endeks listesi + tekil endeks + endeks bileşen (ilgili hisseler) sorgu servisi.
 *
 * <p>Veri: {@link BistIndexCatalog} kataloğundaki her endeks Yahoo'dan ({@code {KOD}.IS}) canlı özet olarak
 * çekilir — mevcut {@link StockQueryService#getSummariesFor} paralel toplayıcısı yeniden kullanılır (endeks
 * sembolü Yahoo açısından sıradan bir semboldür). Liste @Cacheable + LKG ile dayanıklıdır; kaynak çökerse
 * son başarılı liste servis edilir.</p>
 */
@Service
public class IndexQueryService {

    private static final Logger log = LoggerFactory.getLogger(IndexQueryService.class);
    private static final Duration INDEX_LKG_TTL = Duration.ofDays(3);
    /** İlgili hisse listesi üst sınırı (büyüklük endekslerinde 100+ bileşen olabilir; görüntü için yeterli). */
    private static final int MAX_CONSTITUENTS = 60;

    private final StockQueryService stockQueryService;
    private final StockSymbolProvider stockSymbolProvider;
    private final LastKnownGoodCache lkg;

    public IndexQueryService(StockQueryService stockQueryService,
                             StockSymbolProvider stockSymbolProvider,
                             LastKnownGoodCache lkg) {
        this.stockQueryService = stockQueryService;
        this.stockSymbolProvider = stockSymbolProvider;
        this.lkg = lkg;
    }

    /** Tüm BIST endeksleri — canlı fiyat/değişim ile. Veri gelmeyen endeks atlanır. */
    @Cacheable(cacheNames = "market.indices.list", key = "'indices'")
    public List<IndexSummary> getIndices() {
        return lkg.resilient("market.indices.list", INDEX_LKG_TTL,
                new TypeReference<List<IndexSummary>>() {}, this::fetchIndices);
    }

    private List<IndexSummary> fetchIndices() {
        List<IndexInfo> catalog = BistIndexCatalog.all();
        List<String> symbols = catalog.stream().map(IndexInfo::yahooSymbol).toList();
        Map<String, StockSummary> bySymbol = stockQueryService.getSummariesFor(symbols).stream()
                .filter(s -> s.getSymbol() != null && s.getPrice() != null)
                .collect(Collectors.toMap(s -> s.getSymbol().toUpperCase(Locale.ROOT), s -> s, (a, b) -> a));

        List<IndexSummary> out = new ArrayList<>();
        for (IndexInfo info : catalog) {
            StockSummary s = bySymbol.get(info.yahooSymbol().toUpperCase(Locale.ROOT));
            if (s == null) {
                log.debug("Endeks verisi gelmedi, atlanıyor: {}", info.code());
                continue;
            }
            out.add(toIndexSummary(info, s));
        }
        log.info("BIST endeks listesi: {}/{} endeks canlı geldi", out.size(), catalog.size());
        return out;
    }

    /** Tekil endeks özeti — önce cache'li listeden, yoksa tekil Yahoo çekimi. */
    public IndexSummary getIndex(String code) {
        IndexInfo info = BistIndexCatalog.byCode(code);
        if (info == null) {
            throw new ResourceNotFoundException("Index not found: " + code);
        }
        IndexSummary cached = getIndices().stream()
                .filter(i -> i.getCode().equalsIgnoreCase(info.code()))
                .findFirst()
                .orElse(null);
        if (cached != null) {
            return cached;
        }
        StockSummary s = stockQueryService.getStockSummary(info.yahooSymbol());
        return toIndexSummary(info, s);
    }

    /** Endeksle ilgili hisseler (büyüklük endeksleri canlı, sektör endeksleri küratörlü). Yoksa boş liste. */
    public List<StockSummary> getConstituents(String code) {
        IndexInfo info = BistIndexCatalog.byCode(code);
        if (info == null) {
            return List.of();
        }
        List<String> symbols = resolveConstituentSymbols(info.code());
        if (symbols.isEmpty()) {
            return List.of();
        }
        if (symbols.size() > MAX_CONSTITUENTS) {
            symbols = symbols.subList(0, MAX_CONSTITUENTS);
        }
        return stockQueryService.getSummariesFor(symbols);
    }

    /** Bileşen sembolleri (.IS'li). Büyüklük endeksleri StockSymbolProvider'dan canlı; sektör → küratörlü. */
    private List<String> resolveConstituentSymbols(String code) {
        return switch (code) {
            case "XU030" -> stockSymbolProvider.getBist30Symbols();
            case "XU050" -> stockSymbolProvider.getBist50Symbols();
            case "XU100" -> stockSymbolProvider.getBist100Symbols();
            // "Tüm"/500/Ana için tüm listeyi göstermek pratik değil → en likit BIST 100 temsilî gösterilir.
            case "XUTUM", "XU500", "XBANA" -> stockSymbolProvider.getBist100Symbols();
            default -> BistIndexCatalog.curatedConstituents(code).stream().map(s -> s + ".IS").toList();
        };
    }

    private IndexSummary toIndexSummary(IndexInfo info, StockSummary s) {
        IndexSummary x = new IndexSummary();
        x.setCode(info.code());
        x.setSymbol(info.yahooSymbol());
        x.setName(info.name());
        x.setCategory(info.category());
        x.setPrice(s.getPrice());
        x.setChange(s.getChange());
        x.setChangePercent(s.getChangePercent());
        x.setDayHigh(s.getDayHigh());
        x.setDayLow(s.getDayLow());
        x.setCurrency(s.getCurrency());
        return x;
    }
}
