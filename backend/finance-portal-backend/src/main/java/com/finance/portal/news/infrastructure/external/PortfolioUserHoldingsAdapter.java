package com.finance.portal.news.infrastructure.external;

import com.finance.portal.news.application.port.UserHoldingsPort;
import com.finance.portal.portfolio.presentation.dto.PortfolioResponse;
import com.finance.portal.portfolio.service.PortfolioService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link UserHoldingsPort} adapter'ı — portföy modülünden kullanıcının HOLDINGS enstrümanlarını çeker.
 * Sembole göre tekilleştirir; hata olursa boş döner (kişisel haber best-effort).
 */
@Component
public class PortfolioUserHoldingsAdapter implements UserHoldingsPort {

    private static final Logger log = LoggerFactory.getLogger(PortfolioUserHoldingsAdapter.class);

    private final PortfolioService portfolioService;

    public PortfolioUserHoldingsAdapter(PortfolioService portfolioService) {
        this.portfolioService = portfolioService;
    }

    @Override
    public List<Holding> holdingsForUser(String userId) {
        if (userId == null || userId.isBlank()) {
            return List.of();
        }
        try {
            Map<String, Holding> unique = new LinkedHashMap<>();
            for (PortfolioResponse p : portfolioService.getUserPortfolios(userId)) {
                if (p.getHoldings() == null) {
                    continue;
                }
                p.getHoldings().forEach(h -> {
                    if (h.getSymbol() == null || h.getSymbol().isBlank()) {
                        return;
                    }
                    unique.putIfAbsent(h.getSymbol().toUpperCase(), new Holding(
                            h.getSymbol(),
                            h.getName(),
                            h.getAssetType() != null ? h.getAssetType().name() : null));
                });
            }
            return List.copyOf(unique.values());
        } catch (Exception e) {
            log.debug("holdingsForUser failed for {}: {}", userId, e.getMessage());
            return List.of();
        }
    }
}
