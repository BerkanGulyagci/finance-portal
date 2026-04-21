package com.finance.portal.market.application.bond;

import com.finance.portal.market.infrastructure.external.bond.ZiraatBondClient;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class BondService {

    private final ZiraatBondClient ziraatBondClient;

    public BondService(ZiraatBondClient ziraatBondClient) {
        this.ziraatBondClient = ziraatBondClient;
    }

    @Cacheable(cacheNames = "market.bonds", key = "'all'")
    public List<BondItem> getBonds() {
        return ziraatBondClient.fetchBonds();
    }
}
