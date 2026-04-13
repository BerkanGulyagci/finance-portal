package com.finance.portal.market.application.viop;

import com.finance.portal.market.infrastructure.external.viop.AkbankViopClient;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ViopService {

    private final AkbankViopClient akbankViopClient;

    public ViopService(AkbankViopClient akbankViopClient) {
        this.akbankViopClient = akbankViopClient;
    }

    @Cacheable(cacheNames = "market.viop", key = "'all'")
    public List<ViopContract> getContracts() {
        return akbankViopClient.fetchContracts();
    }
}
