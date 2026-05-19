package com.finance.portal.market.application.funds.port;

import com.finance.portal.market.application.funds.model.RasyonetFundDetailDto;
import com.finance.portal.market.application.funds.model.RasyonetFundDto;
import com.finance.portal.market.application.funds.model.RasyonetOsmanliFundBulletinDto;

import java.util.List;

/**
 * Rasyonet / Yatırım Direkt fon verisi adaptör portu.
 */
public interface RasyonetFundPort {

    List<RasyonetFundDto> fetchFundsBySourceCode(String sourceCode);

    List<RasyonetOsmanliFundBulletinDto> fetchOsmanliFundBulletin();

    RasyonetFundDetailDto fetchFundDetailRich(String code, String sourceCode);
}
