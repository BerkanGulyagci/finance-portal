package com.finance.portal.market.application.indicator;

import com.finance.portal.market.infrastructure.external.indicator.EconomicIndicatorClient;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class EconomicIndicatorService {

    // Fallback values — updated manually when needed
    private static final String FALLBACK_POLICY_RATE = "37";
    private static final String FALLBACK_INFLATION = "30.87"; // TÜİK Mart 2026

    private final EconomicIndicatorClient client;

    public EconomicIndicatorService(EconomicIndicatorClient client) {
        this.client = client;
    }

    @Cacheable(cacheNames = "market.indicators", key = "'all'")
    public Map<String, String> getIndicators() {
        Map<String, String> result = new LinkedHashMap<>();

        String policyRate = client.fetchPolicyRate();
        result.put("policyRate", policyRate != null ? policyRate : FALLBACK_POLICY_RATE);
        result.put("inflation", FALLBACK_INFLATION); // TÜİK scraping complex, use fallback

        return result;
    }
}
