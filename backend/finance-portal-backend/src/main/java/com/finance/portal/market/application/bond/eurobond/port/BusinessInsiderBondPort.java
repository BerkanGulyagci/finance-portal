package com.finance.portal.market.application.bond.eurobond.port;

import com.finance.portal.market.application.bond.eurobond.model.EurobondChartPoint;
import com.finance.portal.market.application.bond.eurobond.model.EurobondDetail;
import com.finance.portal.market.application.bond.eurobond.model.EurobondRef;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Business Insider tahvil verisi: ISIN çöz → detay → grafik. */
public interface BusinessInsiderBondPort {

    /** SearchController_Suggest ile ISIN'i çözer (slug + instrumentId + isim + ihraçcı). */
    Optional<EurobondRef> resolve(String isin);

    /** Detay sayfasını (Jsoup) ayrıştırır: Bond Data tablosu + canlı fiyat + tkData. */
    Optional<EurobondDetail> fetchDetail(EurobondRef ref);

    /** Chart_GetChartData ile OHLC serisi (tkData detay sayfasından gelir). */
    List<EurobondChartPoint> fetchChart(String tkData, LocalDate from, LocalDate to);
}
