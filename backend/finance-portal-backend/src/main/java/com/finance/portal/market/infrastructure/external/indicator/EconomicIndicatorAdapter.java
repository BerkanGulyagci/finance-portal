package com.finance.portal.market.infrastructure.external.indicator;

import com.finance.portal.market.application.indicator.port.EconomicIndicatorPort;
import org.springframework.stereotype.Component;

@Component
public class EconomicIndicatorAdapter implements EconomicIndicatorPort {

    private final EconomicIndicatorClient economicIndicatorClient;

    public EconomicIndicatorAdapter(EconomicIndicatorClient economicIndicatorClient) {
        this.economicIndicatorClient = economicIndicatorClient;
    }

    @Override
    public String fetchPolicyRate() {
        return economicIndicatorClient.fetchPolicyRate();
    }
}
