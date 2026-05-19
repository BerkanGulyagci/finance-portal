package com.finance.portal.market.application.viop.port;

import com.finance.portal.market.application.viop.ViopContract;

import java.util.List;

public interface ViopContractListPort {

    List<ViopContract> fetchContracts();
}
