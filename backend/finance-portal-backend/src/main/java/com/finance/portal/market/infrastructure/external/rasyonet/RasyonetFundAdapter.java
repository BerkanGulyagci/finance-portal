package com.finance.portal.market.infrastructure.external.rasyonet;

import com.finance.portal.market.application.funds.model.RasyonetFundDetailDto;
import com.finance.portal.market.application.funds.model.RasyonetFundDto;
import com.finance.portal.market.application.funds.model.RasyonetOsmanliFundBulletinDto;
import com.finance.portal.market.application.funds.port.RasyonetFundPort;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RasyonetFundAdapter implements RasyonetFundPort {

    private final RasyonetFundClient rasyonetFundClient;

    public RasyonetFundAdapter(RasyonetFundClient rasyonetFundClient) {
        this.rasyonetFundClient = rasyonetFundClient;
    }

    @Override
    public List<RasyonetFundDto> fetchFundsBySourceCode(String sourceCode) {
        return rasyonetFundClient.fetchFundsBySourceCode(sourceCode);
    }

    @Override
    public List<RasyonetOsmanliFundBulletinDto> fetchOsmanliFundBulletin() {
        return rasyonetFundClient.fetchOsmanliFundBulletin();
    }

    @Override
    public RasyonetFundDetailDto fetchFundDetailRich(String code, String sourceCode) {
        return rasyonetFundClient.fetchFundDetailRich(code, sourceCode);
    }
}
