package com.finance.portal.market.infrastructure.external.midas;

import com.finance.portal.market.application.stock.MidasStockDetail;
import com.finance.portal.market.application.stock.port.MidasStockPort;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class MidasStockAdapter implements MidasStockPort {

    private final MidasStockClient midasStockClient;

    public MidasStockAdapter(MidasStockClient midasStockClient) {
        this.midasStockClient = midasStockClient;
    }

    @Override
    public List<String> fetchSymbols(String path) {
        return midasStockClient.fetchSymbols(path);
    }

    @Override
    public MidasStockDetail fetchDetail(String symbol) {
        return midasStockClient.fetchDetail(symbol);
    }
}
