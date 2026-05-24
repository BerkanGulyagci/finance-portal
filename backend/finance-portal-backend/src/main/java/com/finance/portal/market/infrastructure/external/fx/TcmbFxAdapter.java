package com.finance.portal.market.infrastructure.external.fx;

import com.finance.portal.market.application.fx.model.TcmbFxCurrencyRow;
import com.finance.portal.market.application.fx.model.TcmbFxFeed;
import com.finance.portal.market.application.fx.port.TcmbFxPort;
import com.finance.portal.market.infrastructure.external.fx.dto.TcmbCurrencyDto;
import com.finance.portal.market.infrastructure.external.fx.dto.TcmbExchangeRatesDto;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class TcmbFxAdapter implements TcmbFxPort {

    private final TcmbFxClient tcmbFxClient;

    public TcmbFxAdapter(TcmbFxClient tcmbFxClient) {
        this.tcmbFxClient = tcmbFxClient;
    }

    @Override
    public TcmbFxFeed fetchLatestRates() {
        TcmbExchangeRatesDto dto = tcmbFxClient.fetchLatestRates();
        List<TcmbFxCurrencyRow> rows = dto.getCurrencies().stream()
                .map(this::toRow)
                .collect(Collectors.toList());
        return new TcmbFxFeed(dto.getDate(), rows);
    }

    private TcmbFxCurrencyRow toRow(TcmbCurrencyDto currency) {
        return new TcmbFxCurrencyRow(
                currency.getCurrencyCode(),
                currency.getUnit(),
                currency.getForexBuying(),
                currency.getForexSelling(),
                currency.getBanknoteBuying(),
                currency.getBanknoteSelling());
    }
}
