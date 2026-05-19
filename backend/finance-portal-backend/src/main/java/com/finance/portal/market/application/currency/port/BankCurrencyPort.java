package com.finance.portal.market.application.currency.port;

import com.finance.portal.market.application.currency.model.HesapkurduFxItem;

import java.util.List;

/**
 * Banka döviz kurları (Hesapkurdu) adaptör portu.
 */
public interface BankCurrencyPort {

    List<HesapkurduFxItem> fetchBankRates();
}
