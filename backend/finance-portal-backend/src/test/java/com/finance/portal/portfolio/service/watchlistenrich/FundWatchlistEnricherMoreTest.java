package com.finance.portal.portfolio.service.watchlistenrich;

import com.finance.portal.market.application.funds.model.RasyonetFundDetailDto;
import com.finance.portal.market.application.funds.model.RasyonetFundDto;
import com.finance.portal.market.application.funds.service.RasyonetFundService;
import com.finance.portal.portfolio.presentation.dto.WatchlistItemResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Branch-coverage tamamlayıcısı: {@link FundWatchlistEnricher} için
 * {@link FundWatchlistEnricherTest} tarafından ATLANAN kırmızı/sarı dalları kapatır
 * (JaCoCo nc/pc satırları): blank sourceCode, detay null-price, listed null-price,
 * null currency, priceHistory boş/null-nokta/null-prevClose/sıfır-prevClose,
 * tek-noktalı history (maxP/minP==null), günlük-yüzde denom==0, ad/tür null-blank.
 */
@ExtendWith(MockitoExtension.class)
class FundWatchlistEnricherMoreTest {

    @Mock RasyonetFundService rasyonetFundService;

    private FundWatchlistEnricher enricher;

    @BeforeEach
    void setUp() {
        enricher = new FundWatchlistEnricher(rasyonetFundService);
    }

    /** Tüm liste lookup'larını boş döndür (kod listede yok → listed == null). */
    private void stubEmptyLists() {
        lenient().when(rasyonetFundService.getAllFunds()).thenReturn(List.of());
        lenient().when(rasyonetFundService.getAllBesFunds()).thenReturn(List.of());
        lenient().when(rasyonetFundService.getAllOksFunds()).thenReturn(List.of());
    }

    private static RasyonetFundDto listedFund(String code, BigDecimal price) {
        RasyonetFundDto f = new RasyonetFundDto();
        f.setCode(code);
        f.setName("Liste Fonu");
        f.setFundType("Hisse Senedi");
        f.setPrice(price);
        f.setReturnOneDay(new BigDecimal("1.5"));
        f.setReturnOneMonth(new BigDecimal("5"));
        f.setReturnThreeMonths(new BigDecimal("12"));
        f.setReturnYearToDate(new BigDecimal("20"));
        f.setReturnOneYear(new BigDecimal("40"));
        f.setRiskLevel(6);
        return f;
    }

    private static RasyonetFundDetailDto detail(BigDecimal price) {
        RasyonetFundDetailDto d = new RasyonetFundDetailDto();
        d.setName("Detay Fonu");
        d.setFundType("Karma");
        d.setCurrencyCode("TRY");
        d.setPrice(price);
        d.setReturnOneDay(new BigDecimal("2"));
        d.setReturnOneMonth(new BigDecimal("6"));
        d.setReturnThreeMonths(new BigDecimal("13"));
        d.setReturnYearToDate(new BigDecimal("21"));
        d.setReturnOneYear(new BigDecimal("41"));
        d.setRiskLevel(5);
        return d;
    }

    private static List<RasyonetFundDetailDto.PricePoint> history(BigDecimal... prices) {
        List<RasyonetFundDetailDto.PricePoint> ph = new ArrayList<>();
        for (int i = 0; i < prices.length; i++) {
            ph.add(new RasyonetFundDetailDto.PricePoint("2025-02-" + (i + 1), prices[i]));
        }
        return ph;
    }

    // ── L41: listed var ama sourceCode BLANK → !isBlank() false-dalı ─────────────

    @Test
    @DisplayName("enrich: listed sourceCode boşsa atlanır, TMF default'tan bulunur")
    void enrich_blankSourceCode_skipsAndUsesDefaults() {
        RasyonetFundDto listed = listedFund("BLK", new BigDecimal("5"));
        listed.setSourceCode("   "); // blank → detailSources'a eklenmez
        when(rasyonetFundService.getAllFunds()).thenReturn(List.of(listed));

        RasyonetFundDetailDto d = detail(new BigDecimal("4.44"));
        when(rasyonetFundService.getFundDetailRich("BLK", "TMF")).thenReturn(d);

        WatchlistItemResponse r = new WatchlistItemResponse();
        enricher.enrich(r, "BLK");

        assertThat(r.getLastPrice()).isEqualByComparingTo("4.44");
        // blank sourceCode eklenmediği için ilk default TMF denenmeli
        verify(rasyonetFundService).getFundDetailRich("BLK", "TMF");
    }

    // ── L52: detay != null ama price == null → atlanır ──────────────────────────

    @Test
    @DisplayName("enrich: detay price null → o kaynak atlanır, sonraki kaynağa düşer")
    void enrich_detailNullPrice_skipped() {
        stubEmptyLists();
        RasyonetFundDetailDto nullPrice = detail(null); // price == null → L52 ikinci koşul false
        when(rasyonetFundService.getFundDetailRich("NPR", "TMF")).thenReturn(nullPrice);
        when(rasyonetFundService.getFundDetailRich("NPR", "TPF")).thenReturn(null);
        when(rasyonetFundService.getFundDetailRich("NPR", "TAF"))
                .thenReturn(detail(new BigDecimal("9.99")));

        WatchlistItemResponse r = new WatchlistItemResponse();
        enricher.enrich(r, "NPR");

        assertThat(r.getLastPrice()).isEqualByComparingTo("9.99");
        verify(rasyonetFundService).getFundDetailRich("NPR", "TAF");
    }

    // ── L58: listed != null ama price == null → throw (list-row fallback'a girmez) ─

    @Test
    @DisplayName("enrich: listed fiyatı null + detay yok → IllegalArgumentException")
    void enrich_listedNullPrice_throws() {
        RasyonetFundDto listed = listedFund("LNP", null); // price == null → L58 ikinci koşul false
        when(rasyonetFundService.getAllFunds()).thenReturn(List.of(listed));
        when(rasyonetFundService.getFundDetailRich(eq("LNP"), any())).thenReturn(null);

        WatchlistItemResponse r = new WatchlistItemResponse();
        assertThatThrownBy(() -> enricher.enrich(r, "LNP"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Fund price not found");
    }

    // ── L79: currencyCode == null → TRY default (blank değil, null) ──────────────

    @Test
    @DisplayName("enrich: currencyCode null → TRY varsayılır")
    void enrich_nullCurrency_defaultsTry() {
        stubEmptyLists();
        RasyonetFundDetailDto d = detail(new BigDecimal("11.10"));
        d.setCurrencyCode(null); // null → L79 ilk koşul false
        d.setPriceHistory(null);
        when(rasyonetFundService.getFundDetailRich("NCU", "TMF")).thenReturn(d);

        WatchlistItemResponse r = new WatchlistItemResponse();
        enricher.enrich(r, "NCU");

        assertThat(r.getCurrency()).isEqualTo("TRY");
        assertThat(r.getLastPrice()).isEqualByComparingTo("11.10");
    }

    // ── L113/L116: detay name & fundType null → set edilmez (false-dalları) ──────

    @Test
    @DisplayName("enrich: detay ad/tür null → fundName/fundType null kalır")
    void enrich_detailNullNameType_notSet() {
        stubEmptyLists();
        RasyonetFundDetailDto d = detail(new BigDecimal("7.00"));
        d.setName(null);
        d.setFundType(null);
        d.setPriceHistory(null);
        when(rasyonetFundService.getFundDetailRich("DNN", "TMF")).thenReturn(d);

        WatchlistItemResponse r = new WatchlistItemResponse();
        enricher.enrich(r, "DNN");

        assertThat(r.getFundName()).isNull();
        assertThat(r.getFundType()).isNull();
        // getiri alanları yine de set edilir
        assertThat(r.getFundReturnOneYear()).isEqualByComparingTo("41");
    }

    // ── L129 (ph.isEmpty()) + L84 fallback: boş history → returnOneDay% yolu ─────

    @Test
    @DisplayName("enrich: boş priceHistory → applyOhlc false, günlük-yüzde fallback")
    void enrich_emptyHistory_dailyPercentFallback() {
        stubEmptyLists();
        RasyonetFundDetailDto d = detail(new BigDecimal("8.00"));
        d.setPriceHistory(new ArrayList<>()); // isEmpty() == true → L129 ikinci koşul true
        when(rasyonetFundService.getFundDetailRich("EMP", "TMF")).thenReturn(d);

        WatchlistItemResponse r = new WatchlistItemResponse();
        enricher.enrich(r, "EMP");

        assertThat(r.getLastPrice()).isEqualByComparingTo("8.00");
        assertThat(r.getChangePercent()).isEqualByComparingTo("2.00"); // returnOneDay
        assertThat(r.getOpen()).isNotNull();
        assertThat(r.getChange()).isNotNull();
    }

    // ── L138 + L86 (open==null true / change==null false): null-nokta + null prevClose,
    //    returnOneDay null → open/change set EDİLMEZ ──────────────────────────────

    @Test
    @DisplayName("enrich: history null-nokta + null prevClose + returnOneDay null → open/change null")
    void enrich_nullPointAndNullPrevClose_noChangeNoOpen() {
        stubEmptyLists();
        RasyonetFundDetailDto d = detail(new BigDecimal("12.50"));
        d.setReturnOneDay(null); // fillChange erken döner → change set edilmez
        // n=3, prevClose = index(n-2)=index1 = null → applyOhlc false döner;
        // pencere içinde index1 null → L138 continue (p==null true dalı)
        d.setPriceHistory(history(new BigDecimal("12.00"), null, new BigDecimal("12.40")));
        when(rasyonetFundService.getFundDetailRich("NPC", "TMF")).thenReturn(d);

        WatchlistItemResponse r = new WatchlistItemResponse();
        enricher.enrich(r, "NPC");

        assertThat(r.getLastPrice()).isEqualByComparingTo("12.50");
        // high = max(points, nav) = max(12.40, 12.50) = 12.50; low = min(12.00, 12.50) = 12.00.
        // (maxP/minP gerçek noktalardan başlar, sonra nav ile birleşir — kaynak L144/L145)
        assertThat(r.getHigh()).isEqualByComparingTo("12.50");
        assertThat(r.getLow()).isEqualByComparingTo("12.00");
        assertThat(r.getChange()).isNull();
        assertThat(r.getOpen()).isNull();
    }

    // ── L144/L145 (maxP/minP == null) + L149 (n>=2 false): tek-noktalı ve tümü-null ─

    @Test
    @DisplayName("enrich: tek-elemanlı null history → high/low NAV'dan, n<2 fallback")
    void enrich_singleNullPoint_highLowFromNav() {
        stubEmptyLists();
        RasyonetFundDetailDto d = detail(new BigDecimal("15.00"));
        // n=1: pencere içinde tek nokta null → maxP/minP null kalır → L144/L145 nav'a düşer;
        // n>=2 false → applyOhlc false → returnOneDay(2)% fallback
        d.setPriceHistory(history((BigDecimal) null));
        when(rasyonetFundService.getFundDetailRich("SNP", "TMF")).thenReturn(d);

        WatchlistItemResponse r = new WatchlistItemResponse();
        enricher.enrich(r, "SNP");

        assertThat(r.getLastPrice()).isEqualByComparingTo("15.00");
        assertThat(r.getHigh()).isEqualByComparingTo("15.00"); // nav
        assertThat(r.getLow()).isEqualByComparingTo("15.00");  // nav
        assertThat(r.getChangePercent()).isEqualByComparingTo("2.00");
    }

    // ── L155 (prevClose.compareTo(ZERO) != 0 → false): prevClose == 0 ───────────

    @Test
    @DisplayName("enrich: prevClose sıfır → changePercent atlanır ama open/change set olur")
    void enrich_zeroPrevClose_skipsChangePercent() {
        stubEmptyLists();
        RasyonetFundDetailDto d = detail(new BigDecimal("12.50"));
        // n=3, prevClose=index1=0 → L155 false (changePercent set edilmez), true döner
        d.setPriceHistory(history(
                new BigDecimal("10.00"), BigDecimal.ZERO, new BigDecimal("12.50")));
        when(rasyonetFundService.getFundDetailRich("ZPC", "TMF")).thenReturn(d);

        WatchlistItemResponse r = new WatchlistItemResponse();
        enricher.enrich(r, "ZPC");

        assertThat(r.getOpen()).isEqualByComparingTo("0.0000"); // prevClose
        assertThat(r.getChange()).isEqualByComparingTo("12.50"); // 12.50 - 0
        assertThat(r.getChangePercent()).isNull(); // sıfıra bölme atlandı
        assertThat(r.getHigh()).isEqualByComparingTo("12.50");
        assertThat(r.getLow()).isEqualByComparingTo("0.0000");
    }

    // ── L175 (denom == 0): returnOneDay == -100 → impliedPrev hesaplanmaz ───────

    @Test
    @DisplayName("enrich: returnOneDay -100% → denom 0, change set edilmez")
    void enrich_minusHundredPercent_denomZero() {
        stubEmptyLists();
        RasyonetFundDetailDto d = detail(new BigDecimal("9.00"));
        d.setReturnOneDay(new BigDecimal("-100")); // 1 + (-100/100) = 0 → L175 true
        d.setPriceHistory(null);
        when(rasyonetFundService.getFundDetailRich("DZ0", "TMF")).thenReturn(d);

        WatchlistItemResponse r = new WatchlistItemResponse();
        enricher.enrich(r, "DZ0");

        assertThat(r.getLastPrice()).isEqualByComparingTo("9.00");
        assertThat(r.getChangePercent()).isEqualByComparingTo("-100.00"); // % set edildi
        assertThat(r.getChange()).isNull(); // denom 0 → erken dönüş, change yok
        assertThat(r.getOpen()).isNull();
    }

    // ── L96/L99 (list-row name/fundType null → false-dalları) ───────────────────

    @Test
    @DisplayName("enrich: list-row ad/tür null → fundName/fundType null kalır")
    void enrich_listRow_nullNameType() {
        RasyonetFundDto listed = listedFund("LNT", new BigDecimal("6.60"));
        listed.setName(null);
        listed.setFundType(null);
        when(rasyonetFundService.getAllFunds()).thenReturn(List.of(listed));
        when(rasyonetFundService.getFundDetailRich(eq("LNT"), any())).thenReturn(null);

        WatchlistItemResponse r = new WatchlistItemResponse();
        enricher.enrich(r, "LNT");

        assertThat(r.getLastPrice()).isEqualByComparingTo("6.60");
        assertThat(r.getFundName()).isNull();
        assertThat(r.getFundType()).isNull();
        assertThat(r.getCurrency()).isEqualTo("TRY");
        assertThat(r.getFundRiskLevel()).isEqualTo(6);
    }

    // ── L96/L99 BLANK varyantı: list-row ad/tür boş string → set edilmez ────────

    @Test
    @DisplayName("enrich: list-row ad/tür blank → fundName/fundType set edilmez")
    void enrich_listRow_blankNameType() {
        RasyonetFundDto listed = listedFund("LBT", new BigDecimal("4.20"));
        listed.setName("   ");
        listed.setFundType("  ");
        when(rasyonetFundService.getAllFunds()).thenReturn(List.of(listed));
        when(rasyonetFundService.getFundDetailRich(eq("LBT"), any())).thenReturn(null);

        WatchlistItemResponse r = new WatchlistItemResponse();
        enricher.enrich(r, "LBT");

        assertThat(r.getFundName()).isNull();
        assertThat(r.getFundType()).isNull();
        assertThat(r.getLastPrice()).isEqualByComparingTo("4.20");
    }
}
