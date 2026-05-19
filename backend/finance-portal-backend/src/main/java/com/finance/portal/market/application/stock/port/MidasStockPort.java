package com.finance.portal.market.application.stock.port;

import com.finance.portal.market.application.stock.MidasStockDetail;

import java.util.List;

public interface MidasStockPort {

    List<String> fetchSymbols(String path);

    MidasStockDetail fetchDetail(String symbol);
}
