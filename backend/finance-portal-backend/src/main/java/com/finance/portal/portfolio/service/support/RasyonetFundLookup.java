package com.finance.portal.portfolio.service.support;

import com.finance.portal.market.application.funds.model.RasyonetFundDto;
import com.finance.portal.market.application.funds.service.RasyonetFundService;

/**
 * Liste endpoint'lerinden fon kodu ile satır bulma (watchlist + holdings ortak).
 */
public final class RasyonetFundLookup {

    private RasyonetFundLookup() {
    }

    public static RasyonetFundDto findByCode(RasyonetFundService rasyonetFundService, String code) {
        String u = code.toUpperCase();
        for (RasyonetFundDto f : rasyonetFundService.getAllFunds()) {
            if (f.getCode() != null && u.equalsIgnoreCase(f.getCode())) {
                return f;
            }
        }
        for (RasyonetFundDto f : rasyonetFundService.getAllBesFunds()) {
            if (f.getCode() != null && u.equalsIgnoreCase(f.getCode())) {
                return f;
            }
        }
        for (RasyonetFundDto f : rasyonetFundService.getAllOksFunds()) {
            if (f.getCode() != null && u.equalsIgnoreCase(f.getCode())) {
                return f;
            }
        }
        return null;
    }
}
