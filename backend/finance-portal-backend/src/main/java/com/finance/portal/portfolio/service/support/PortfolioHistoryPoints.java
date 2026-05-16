package com.finance.portal.portfolio.service.support;

import com.finance.portal.market.application.precious.PreciousMetalHistoryPoint;
import com.finance.portal.market.application.precious.PreciousMetalHistoryResponse;
import com.finance.portal.market.application.silver.SilverHistoryPoint;
import com.finance.portal.market.application.silver.SilverHistoryResponse;

import java.math.BigDecimal;
import java.util.List;

/**
 * Gümüş / kıymetli metal tarihsel serilerinde son ve önceki noktalar.
 */
public final class PortfolioHistoryPoints {

    private PortfolioHistoryPoints() {
    }

    public record SilverWindow(SilverHistoryPoint latest, SilverHistoryPoint prev) {
    }

    public static SilverWindow silverWindow(SilverHistoryResponse resp) {
        if (resp == null || resp.getPoints() == null || resp.getPoints().isEmpty()) {
            return new SilverWindow(null, null);
        }
        List<SilverHistoryPoint> pts = resp.getPoints();
        SilverHistoryPoint latest = pts.get(pts.size() - 1);
        SilverHistoryPoint prev = pts.size() >= 2 ? pts.get(pts.size() - 2) : null;
        return new SilverWindow(latest, prev);
    }

    public record PreciousWindow(PreciousMetalHistoryPoint latest, PreciousMetalHistoryPoint prev) {
    }

    public static PreciousWindow preciousWindow(PreciousMetalHistoryResponse resp) {
        if (resp == null || resp.getPoints() == null || resp.getPoints().isEmpty()) {
            return new PreciousWindow(null, null);
        }
        List<PreciousMetalHistoryPoint> pts = resp.getPoints();
        PreciousMetalHistoryPoint latest = pts.get(pts.size() - 1);
        PreciousMetalHistoryPoint prev = pts.size() >= 2 ? pts.get(pts.size() - 2) : null;
        return new PreciousWindow(latest, prev);
    }

    public static BigDecimal preciousPointValue(PreciousMetalHistoryPoint p, String cat) {
        if (p == null) {
            return null;
        }
        return switch (cat) {
            case "GRAM_TRY" -> p.getTryGram();
            case "KG_TRY" -> p.getTryKg();
            case "USD_ONS" -> p.getUsdOns();
            case "EUR_ONS" -> p.getEurOns();
            default -> p.getValue();
        };
    }
}
