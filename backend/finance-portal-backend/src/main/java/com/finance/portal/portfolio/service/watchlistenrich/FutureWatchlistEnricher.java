package com.finance.portal.portfolio.service.watchlistenrich;

import com.finance.portal.market.application.viop.ViopContract;
import com.finance.portal.market.application.viop.ViopService;
import com.finance.portal.market.application.viop.model.ViopContractDetail;
import com.finance.portal.portfolio.presentation.dto.WatchlistItemResponse;
import com.finance.portal.portfolio.service.support.PortfolioDateTimeParse;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * FUTURE watchlist item zenginleştirmesi. Önce Akbank VİOP kontrat listesinde aranır;
 * eşleşme yoksa {@link StockLikeWatchlistEnricher}'a düşülür (Yahoo vadelileri ES=F vb.).
 * VİOP path'inde gerçek işlem hacmi yok (sadece açık pozisyon var), bu yüzden volume doldurulmaz.
 *
 * <p>Daha önce {@code PortfolioWatchlistMarketEnricher.enrichFuture + applyViopDetail} idi.
 */
@Component
public class FutureWatchlistEnricher {

    private final ViopService viopService;
    private final StockLikeWatchlistEnricher stockLikeWatchlistEnricher;

    public FutureWatchlistEnricher(ViopService viopService,
                                   StockLikeWatchlistEnricher stockLikeWatchlistEnricher) {
        this.viopService = viopService;
        this.stockLikeWatchlistEnricher = stockLikeWatchlistEnricher;
    }

    public void enrich(WatchlistItemResponse r, String symbol) {
        String contractName = symbol != null ? symbol.trim() : "";
        if (contractName.isBlank()) {
            return;
        }
        Optional<ViopContract> match = viopService.findMatchingContract(contractName);
        if (match.isPresent()) {
            applyViopDetail(r, viopService.buildDetailDto(match.get()));
            return;
        }
        // VİOP listesinde yok → Yahoo vadelileri (ES=F vb.) için StockLike enricher'a düş.
        stockLikeWatchlistEnricher.enrich(r, symbol);
    }

    private static void applyViopDetail(WatchlistItemResponse r, ViopContractDetail d) {
        BigDecimal current = d.getLastPrice();
        if (current == null) {
            current = d.getSettlementPrice();
        }
        if (current == null) {
            throw new IllegalArgumentException("VIOP price not available for: " + r.getSymbol());
        }

        r.setLastPrice(current);
        r.setCurrency("TRY");
        r.setHigh(d.getHigh());
        r.setLow(d.getLow());
        r.setChangePercent(d.getChangePercent());

        BigDecimal prevSet = d.getPrevSettlementPrice();
        if (prevSet != null) {
            r.setChange(current.subtract(prevSet).setScale(4, RoundingMode.HALF_UP));
            r.setOpen(prevSet);
        }

        // VİOP'ta gerçek işlem hacmi verisi yok (yalnız açık pozisyon/open interest var) →
        // "Hacim" sütununu yanıltıcı şekilde doldurma; boş bırak.

        LocalDateTime asOf = PortfolioDateTimeParse.parseLenient(d.getTime());
        r.setAsOf(asOf != null ? asOf : LocalDateTime.now());
    }
}
