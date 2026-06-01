package com.finance.portal.market.application.indicator;

import com.finance.portal.market.application.stock.model.YahooChartSnapshot;
import com.finance.portal.market.application.stock.port.YahooStockPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * BIST endeks fiyat geçmişi servisi.
 *
 * <p>Backend'in başka hiçbir yerinde BIST endeks fiyatı yayımlanmaz:
 * {@code MidasStockClient} bu sembolleri (XU*) dışlar, {@code AssetType} enum'unda
 * bunlara karşılık gelen bir değer yoktur, {@code ViopService} ise yalnız etiketleme
 * için kullanır. Bu servis MarketTicker (ve genel {@code /api/market/price-history}
 * INDICATOR pseudo-türü) için en minimal eklemeyi yapar: Yahoo Finance'in
 * {@code ^XU100 / ^XU030 / ^XU050} sembollerinden günlük kapanış serisi çeker.</p>
 *
 * <p>Sonuç {@link Cacheable} ile 60 dk önbelleğe alınır — endeksler intraday yavaş
 * hareket ettiği için ticker / detay kullanımına bu yeterli (Yahoo'ya minimum yük).</p>
 */
@Service
public class BistIndexService {

    private static final Logger log = LoggerFactory.getLogger(BistIndexService.class);

    /** BIST sözde-sembol → Yahoo Finance ticker eşlemesi (^ önekli). */
    private static final Map<String, String> YAHOO_SYMBOLS = Map.of(
            "XU100", "^XU100",
            "XU030", "^XU030",
            "XU050", "^XU050"
    );

    private final YahooStockPort yahooStockPort;

    public BistIndexService(YahooStockPort yahooStockPort) {
        this.yahooStockPort = yahooStockPort;
    }

    /** Verilen sembol (XU100/XU030/XU050) bu servisle desteklenir mi? */
    public boolean supports(String upperSymbol) {
        return upperSymbol != null && YAHOO_SYMBOLS.containsKey(upperSymbol.trim().toUpperCase(Locale.ROOT));
    }

    /**
     * Sembol + tarih aralığı için BIST endeks Yahoo chart cevabını çeker.
     * Cache anahtarı: sembol + Yahoo range string (1mo / 1y / 5y …) — tarih bilgisi range
     * granularitysine yuvarlandığı için ardışık çağrılar aynı cache'i paylaşır.
     * Spring AOP burada DIŞARIDAN çağrı yoluyla devreye girer (controller → bean.fetchChart);
     * self-invocation yok.
     */
    @Cacheable(cacheNames = "market.bistIndex.history",
               key = "(#symbol == null ? '' : #symbol.toUpperCase()) + ':' + T(com.finance.portal.market.application.indicator.BistIndexService).pickYahooRange(#from, #to)")
    public Optional<YahooChartSnapshot> fetchChart(String symbol, LocalDate from, LocalDate to) {
        if (symbol == null) {
            return Optional.empty();
        }
        String upper = symbol.trim().toUpperCase(Locale.ROOT);
        String yahooSymbol = YAHOO_SYMBOLS.get(upper);
        if (yahooSymbol == null) {
            return Optional.empty();
        }
        String yahooRange = pickYahooRange(from, to);
        try {
            YahooChartSnapshot snap = yahooStockPort.fetchChartWithParams(yahooSymbol, yahooRange, "1d");
            return Optional.ofNullable(snap);
        } catch (Exception ex) {
            log.warn("Yahoo BIST index fetch failed [{}]: {}", yahooSymbol, ex.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Tarih aralığını Yahoo'nun desteklediği range string'ine yuvarlar.
     * SpEL'den çağrılabilmesi için public-static.
     */
    public static String pickYahooRange(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            return "1mo";
        }
        long days = ChronoUnit.DAYS.between(from, to);
        if (days <= 7)    return "5d";
        if (days <= 35)   return "1mo";
        if (days <= 100)  return "3mo";
        if (days <= 200)  return "6mo";
        if (days <= 400)  return "1y";
        if (days <= 800)  return "2y";
        return "5y";
    }
}
