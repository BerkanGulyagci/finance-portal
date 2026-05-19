package com.finance.portal.market.infrastructure.external.viop;

import com.finance.portal.market.application.viop.ViopContract;
import com.finance.portal.market.application.viop.port.ViopContractListPort;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AkbankViopAdapter implements ViopContractListPort {

    private final AkbankViopClient akbankViopClient;

    public AkbankViopAdapter(AkbankViopClient akbankViopClient) {
        this.akbankViopClient = akbankViopClient;
    }

    @Override
    public List<ViopContract> fetchContracts() {
        return akbankViopClient.fetchContracts();
    }
}
