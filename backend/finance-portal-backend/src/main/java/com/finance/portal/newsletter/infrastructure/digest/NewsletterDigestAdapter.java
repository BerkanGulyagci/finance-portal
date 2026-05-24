package com.finance.portal.newsletter.infrastructure.digest;

import com.finance.portal.common.domain.AssetType;
import com.finance.portal.market.application.economy.EconomyService;
import com.finance.portal.market.application.economy.model.EconomyIndicator;
import com.finance.portal.newsletter.application.model.DigestData;
import com.finance.portal.newsletter.application.port.NewsletterDigestPort;
import com.finance.portal.portfolio.domain.PortfolioType;
import com.finance.portal.portfolio.presentation.dto.PortfolioHoldingResponse;
import com.finance.portal.portfolio.presentation.dto.PortfolioResponse;
import com.finance.portal.portfolio.presentation.dto.PortfolioTransactionResponse;
import com.finance.portal.portfolio.presentation.dto.WatchlistItemResponse;
import com.finance.portal.portfolio.service.PortfolioCurrencyConverter;
import com.finance.portal.portfolio.service.PortfolioService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bülten özetini portföy ve ekonomi servislerinden toplar (NewsletterDigestPort impl).
 * Dağılım/günlük değişim için pozisyonlar TL'ye çevrilir (PortfolioCurrencyConverter).
 */
@Component
public class NewsletterDigestAdapter implements NewsletterDigestPort {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private static final Map<AssetType, String> TYPE_LABEL = Map.of(
            AssetType.STOCK, "Hisse",
            AssetType.FUND, "Fon",
            AssetType.FX, "Döviz",
            AssetType.FUTURE, "Vadeli",
            AssetType.CRYPTO, "Kripto",
            AssetType.GOLD, "Altın",
            AssetType.COMMODITY, "Emtia",
            AssetType.BOND, "DİBS");

    private final PortfolioService portfolioService;
    private final EconomyService economyService;
    private final PortfolioCurrencyConverter currencyConverter;

    public NewsletterDigestAdapter(PortfolioService portfolioService,
                                   EconomyService economyService,
                                   PortfolioCurrencyConverter currencyConverter) {
        this.portfolioService = portfolioService;
        this.economyService = economyService;
        this.currencyConverter = currencyConverter;
    }

    @Override
    public DigestData buildFor(String userId) {
        List<PortfolioResponse> all = portfolioService.getUserPortfolios(userId);
        List<PortfolioResponse> portfolios = all.stream()
                .filter(p -> p.getPortfolioType() != PortfolioType.WATCHLIST)
                .toList();
        List<PortfolioResponse> watchlists = all.stream()
                .filter(p -> p.getPortfolioType() == PortfolioType.WATCHLIST)
                .toList();

        BigDecimal totalValue = BigDecimal.ZERO;
        BigDecimal totalCost = BigDecimal.ZERO;
        BigDecimal totalPnl = BigDecimal.ZERO;
        List<PortfolioHoldingResponse> holdings = new ArrayList<>();
        List<DigestData.PortfolioLine> portfolioLines = new ArrayList<>();
        List<DigestData.TxLine> txLines = new ArrayList<>();

        for (PortfolioResponse p : portfolios) {
            BigDecimal pv = nz(p.getTotalMarketValue());
            BigDecimal pc = nz(p.getTotalCost());
            BigDecimal pp = nz(p.getTotalProfitLoss());
            totalValue = totalValue.add(pv);
            totalCost = totalCost.add(pc);
            totalPnl = totalPnl.add(pp);
            portfolioLines.add(new DigestData.PortfolioLine(p.getName(), pv, pp, pct(pp, pc)));
            if (p.getHoldings() != null) {
                holdings.addAll(p.getHoldings());
            }
            if (p.getTransactions() != null) {
                for (PortfolioTransactionResponse tx : p.getTransactions()) {
                    BigDecimal total = nz(tx.getQuantity()).multiply(nz(tx.getPrice()));
                    txLines.add(new DigestData.TxLine(tx.getSymbol(),
                            tx.getTransactionType() != null ? tx.getTransactionType().name() : "BUY",
                            total, tx.getTransactionDate(), p.getName()));
                }
            }
        }
        portfolioLines.sort(Comparator.comparing(DigestData.PortfolioLine::value).reversed());
        txLines.sort(Comparator.comparing(DigestData.TxLine::date,
                Comparator.nullsLast(Comparator.reverseOrder())));
        List<DigestData.TxLine> recent = txLines.stream().limit(5).toList();

        BigDecimal totalPct = pct(totalPnl, totalCost);

        // Varlık türü dağılımı (TL'ye çevrilmiş)
        Map<AssetType, BigDecimal> byType = new LinkedHashMap<>();
        BigDecimal allocTotal = BigDecimal.ZERO;
        BigDecimal mvSum = BigDecimal.ZERO;
        BigDecimal weighted = BigDecimal.ZERO;
        for (PortfolioHoldingResponse h : holdings) {
            BigDecimal tryVal = nz(currencyConverter.toTry(h.getMarketValue(), h.getCurrency()));
            if (tryVal.signum() > 0) {
                byType.merge(h.getAssetType(), tryVal, BigDecimal::add);
                allocTotal = allocTotal.add(tryVal);
                mvSum = mvSum.add(tryVal);
                weighted = weighted.add(tryVal.multiply(nz(h.getChangePercent())));
            }
        }
        List<DigestData.Slice> allocation = new ArrayList<>();
        for (Map.Entry<AssetType, BigDecimal> e : byType.entrySet()) {
            BigDecimal share = allocTotal.signum() > 0
                    ? e.getValue().multiply(HUNDRED).divide(allocTotal, 1, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            allocation.add(new DigestData.Slice(label(e.getKey()), share));
        }
        allocation.sort(Comparator.comparing(DigestData.Slice::percent).reversed());

        BigDecimal dailyPct = mvSum.signum() > 0
                ? weighted.divide(mvSum, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        List<PortfolioHoldingResponse> withChange = holdings.stream()
                .filter(h -> h.getChangePercent() != null)
                .toList();
        List<DigestData.Mover> gainers = withChange.stream()
                .sorted(Comparator.comparing(PortfolioHoldingResponse::getChangePercent).reversed())
                .limit(3).map(this::toMover).toList();
        List<DigestData.Mover> losers = withChange.stream()
                .sorted(Comparator.comparing(PortfolioHoldingResponse::getChangePercent))
                .limit(3).map(this::toMover).toList();

        // Favoriler (izleme listeleri)
        List<DigestData.Fav> favorites = watchlists.stream()
                .flatMap(w -> {
                    try {
                        return portfolioService.getWatchlistItems(userId, w.getId()).stream();
                    } catch (Exception e) {
                        return java.util.stream.Stream.<WatchlistItemResponse>empty();
                    }
                })
                .limit(30)
                .map(it -> new DigestData.Fav(it.getSymbol(), label(it.getAssetType()),
                        it.getLastPrice(), it.getChangePercent()))
                .toList();

        return new DigestData(portfolios.size(), totalValue, totalPnl, totalPct, dailyPct,
                allocation, portfolioLines, gainers, losers, recent, favorites, buildMarket());
    }

    private DigestData.Market buildMarket() {
        List<EconomyIndicator> summary;
        try {
            summary = economyService.getSummary();
        } catch (Exception e) {
            summary = List.of();
        }
        return new DigestData.Market(
                value(summary, "usdTry"),
                value(summary, "bist100"),
                value(summary, "gramAltin"),
                yoy(summary, "tufe"),
                value(summary, "politikaFaizi"));
    }

    private DigestData.Mover toMover(PortfolioHoldingResponse h) {
        String name = (h.getName() != null && !h.getName().isBlank()) ? h.getName() : h.getSymbol();
        return new DigestData.Mover(h.getSymbol(), name, nz(h.getChangePercent()));
    }

    private static String label(AssetType type) {
        return TYPE_LABEL.getOrDefault(type, type != null ? type.name() : "—");
    }

    private static BigDecimal pct(BigDecimal pnl, BigDecimal cost) {
        return cost != null && cost.signum() > 0
                ? pnl.multiply(HUNDRED).divide(cost, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static BigDecimal value(List<EconomyIndicator> list, String key) {
        for (EconomyIndicator i : list) {
            if (key.equals(i.getKey())) return i.getValue();
        }
        return null;
    }

    private static BigDecimal yoy(List<EconomyIndicator> list, String key) {
        for (EconomyIndicator i : list) {
            if (key.equals(i.getKey())) return i.getYoyChangePercent();
        }
        return null;
    }
}
