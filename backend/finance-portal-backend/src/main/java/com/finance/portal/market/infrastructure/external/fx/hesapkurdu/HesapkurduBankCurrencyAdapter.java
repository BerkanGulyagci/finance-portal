package com.finance.portal.market.infrastructure.external.fx.hesapkurdu;

import com.finance.portal.market.application.currency.model.HesapkurduFxItem;
import com.finance.portal.market.application.currency.port.BankCurrencyPort;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class HesapkurduBankCurrencyAdapter implements BankCurrencyPort {

    private final HesapkurduBankCurrencyClient hesapkurduBankCurrencyClient;

    public HesapkurduBankCurrencyAdapter(HesapkurduBankCurrencyClient hesapkurduBankCurrencyClient) {
        this.hesapkurduBankCurrencyClient = hesapkurduBankCurrencyClient;
    }

    @Override
    public List<HesapkurduFxItem> fetchBankRates() {
        return hesapkurduBankCurrencyClient.fetchBankRates();
    }
}
