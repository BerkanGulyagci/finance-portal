package com.finance.portal.portfolio.service.enrich;

import com.finance.portal.market.application.fx.model.FxLatestRates;
import com.finance.portal.market.application.fx.model.FxRateItem;
import com.finance.portal.market.application.service.MarketFxService;
import com.finance.portal.market.application.viop.UnsupportedViopContractException;
import com.finance.portal.market.application.viop.ViopChartPeriod;
import com.finance.portal.market.application.viop.ViopChartService;
import com.finance.portal.market.application.viop.ViopContract;
import com.finance.portal.market.application.viop.ViopService;
import com.finance.portal.market.application.viop.model.ViopChartPoint;
import com.finance.portal.market.application.viop.model.ViopContractDetail;
import com.finance.portal.portfolio.application.viop.spec.ViopContractSpec;
import com.finance.portal.portfolio.application.viop.spec.ViopContractSpecRegistry;
import com.finance.portal.portfolio.application.viop.valuation.ViopValuationService;
import com.finance.portal.portfolio.presentation.dto.PortfolioHoldingResponse;
import com.finance.portal.portfolio.service.support.PortfolioDateTimeParse;
import com.finance.portal.portfolio.service.support.PortfolioMovingAverage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import com.finance.portal.market.application.fx.model.FxHistoryPoint;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.TreeMap;

import static com.finance.portal.portfolio.service.enrich.PortfolioEnrichmentMath.MONEY_SCALE;
import static com.finance.portal.portfolio.service.enrich.PortfolioEnrichmentMath.PRICE_SCALE;

/**
 * FUTURE (VİOP) holding zenginleştirmesi. Akbank kontrat listesi (anlık fiyat + uzlaşma) +
 * İş Yatırım grafik (52w aralığı + günlük MA20/MA50) kombinasyonu.
 *
 * <p>Akbank cache geçici boş / eşleşme başarısız olsa bile grafik enrichment'ı çalışsın diye
 * grafik kısmı liste yolundan ayrılmıştır (önce grafik denenir, sonra liste). Kontrat ismi
 * Akbank kayıt adıyla farklıysa grafik bir kez daha kanonik isimle denenir.
 *
 * <p>VİOP'ta gerçek hacim verisi yok (sadece açık pozisyon), bu yüzden {@code volume} doldurulmaz.
 * Davranış {@code PortfolioHoldingMarketEnricher.enrichFutureHolding(...)} eski kodundan aynen taşındı.
 */
@Component
public class FutureHoldingEnricher {

    private static final Logger log = LoggerFactory.getLogger(FutureHoldingEnricher.class);

    private final ViopService viopService;
    private final ViopChartService viopChartService;
    private final ViopContractSpecRegistry specRegistry;
    private final ViopValuationService valuationService;
    private final MarketFxService marketFxService;
    private final com.finance.portal.market.application.fx.port.TcmbFxHistoryPort tcmbFxHistoryPort;

    public FutureHoldingEnricher(ViopService viopService,
                                 ViopChartService viopChartService,
                                 ViopContractSpecRegistry specRegistry,
                                 ViopValuationService valuationService,
                                 MarketFxService marketFxService,
                                 com.finance.portal.market.application.fx.port.TcmbFxHistoryPort tcmbFxHistoryPort) {
        this.viopService = viopService;
        this.viopChartService = viopChartService;
        this.specRegistry = specRegistry;
        this.valuationService = valuationService;
        this.marketFxService = marketFxService;
        this.tcmbFxHistoryPort = tcmbFxHistoryPort;
    }

    public void enrich(PortfolioHoldingResponse holding) {
        String contractName = holding.getSymbol() != null ? holding.getSymbol().trim() : null;
        if (contractName == null || contractName.isBlank()) {
            return;
        }
        // Liste başarısız olsa bile (geçici boş cache vb.) 52w / MA kaybolmasın.
        applyViopYearChartMetrics(holding, contractName);

        ViopContractDetail d;
        try {
            Optional<ViopContract> match = viopService.findMatchingContract(contractName);
            if (match.isEmpty()) {
                log.debug("VIOP contracts list had no row matching holding symbol={}", contractName);
                return;
            }
            d = viopService.buildDetailDto(match.get());
        } catch (Exception ex) {
            log.warn("VIOP enrichment failed for holding symbol={}: {}", contractName, ex.getMessage());
            return;
        }

        BigDecimal current = d.getLastPrice();
        if (current == null) {
            current = d.getSettlementPrice();
        }
        if (current == null) {
            log.debug("VIOP no price for contract {}", contractName);
            return;
        }

        // VİOP spec: çarpan + marjin oranı + para birimi YAML'dan
        ViopContractSpec spec = specRegistry.resolveOrFallback(contractName);
        BigDecimal multiplier = spec.multiplier();
        String direction = holding.getViopDirection(); // null = LONG (geriye uyum)

        // USD-kote kontratta ham fiyat USD'dir; portföy TL-bazlı olduğu için fiyatı USD/TRY ile
        // çarparız. TRY kontratta (66 tane) fxNow=fxEntry=1 → eski davranış AYNEN (etkilenmez).
        //
        // K/Z KONVANSİYONU = resmi VİOP mark-to-market (Borsa İstanbul standardı):
        //   K/Z = (current − entry) × multiplier × adet × yön × GÜNCEL kur (fxNow)
        // yani giriş bacağı da GÜNCEL kurla TL'leşir → K/Z saf parite (EUR/USD) hareketidir,
        // tek bir kurla TL'ye çevrilir. (Resmi örnek: 800 USD × 6,84 kapanış-kuru = 5.472 TL;
        // giriş kuru K/Z'ye GİRMEZ.) Bu, eurobond Model-1'in total-return mantığından KASITLI
        // olarak FARKLIDIR — VİOP gerçek hayatta günlük nakit-uzlaşmayla bu şekilde netleşir.
        //
        // TEMİNAT İSTİSNASI: başlangıç teminatı (marginPosted) per-date'tir — ALIŞ GÜNÜNÜN kuru
        // (fxEntry) ile bağlanır. Resmi örnek de teminatı açılış kurundan hesaplar (350 USD ×
        // 6,67 açılış-kuru). Yani fxEntry yalnız TEMİNAT için; K/Z ve fiyat gösterimi fxNow.
        // Tarihsel kur bulunamazsa fxEntry, fxNow'a düşer (güvenli).
        BigDecimal fxNow = resolveFxRate(spec);
        LocalDate buyDate = holding.getFirstBuyDate() != null
                ? holding.getFirstBuyDate().toLocalDate() : null;
        BigDecimal fxEntry = resolveEntryFxRate(spec, buyDate, fxNow);

        BigDecimal qty = holding.getTotalQuantity() != null ? holding.getTotalQuantity() : BigDecimal.ZERO;
        BigDecimal totalCost = holding.getTotalCost() != null ? holding.getTotalCost() : BigDecimal.ZERO;
        BigDecimal avgEntry = holding.getAverageCost() != null ? holding.getAverageCost()
                : (qty.signum() > 0 ? totalCost.divide(qty, PRICE_SCALE, RoundingMode.HALF_UP) : BigDecimal.ZERO);

        // İDEMPOTENCY: Enricher bu holding üzerinde detay yolunda 2 KEZ çağrılabilir (builder inline +
        // PortfolioServiceImpl parallel). USD-kote'de önceki çağrı averageCost'u GÜNCEL kur (fxNow) ile
        // TL'ye çevirmiş + currency "TRY"ye set etmiş olabilir → avgEntry zaten TL'dir. Aşağıdaki hesaplar
        // (marginPosted) onu tekrar ×fxEntry yaparsa çifte-FX üretir. Ham USD'ye GERİ döndürürken
        // BÖLEN, gösterimi çeviren kurla AYNI olmalı = fxNow (averageCost gösterimi ×fxNow yapılıyor —
        // resmi mark-to-market: maliyet de güncel kurla TL'leşir ki K/Z = current_TL − avg_TL tutsun).
        // İlk çağrıda currency "USD"/null → ham, dokunulmaz.
        boolean alreadyTlConverted = "USD".equalsIgnoreCase(spec.currency())
                && "TRY".equalsIgnoreCase(holding.getCurrency())
                && fxNow.signum() > 0;
        if (alreadyTlConverted) {
            avgEntry = avgEntry.divide(fxNow, PRICE_SCALE, RoundingMode.HALF_UP);
        }

        // Notional (risk göstergesi) = qty × (GÜNCEL fiyat × fxNow) × multiplier  → TL
        BigDecimal notional = valuationService.notional(qty, current, spec, fxNow);
        // Margin posted (bağlı sermaye) = qty × (giriş × fxEntry) × multiplier × marginRate → TL
        // (teminat alış anında bağlanır → o GÜNÜN kuru; resmi örnek de açılış kurundan hesaplar)
        BigDecimal marginPosted = valuationService.marginPostedPerDate(qty, avgEntry, spec, fxEntry);
        // P&L = qty × ((current − avgEntry) × fxNow) × multiplier × yön → TL
        // RESMİ MARK-TO-MARKET: giriş de GÜNCEL kurla TL'leşir → saf parite hareketi, tek kur.
        // (K/Z kur kazancını taşımaz; o teminat/maliyet tarafında zaten per-date yansır.)
        BigDecimal pnl = valuationService.pnl(qty, avgEntry, current, spec, direction, fxNow);
        BigDecimal leverage = valuationService.leverage(notional, marginPosted);

        // Market value PORTFÖY TOPLAMINA = margin + cumulative pnl (notional değil!)
        // Notional'i toplama eklemek kaldıraçlı pozisyonda portföyü 8-10x şişirir → yanıltıcı.
        BigDecimal mv = valuationService.portfolioContribution(marginPosted, pnl);

        // USD-kote kontratta tablodaki TÜM para alanları TL'ye çevrilir (portföy TL-bazlı):
        // piyasa değeri/K-Z/maliyet zaten fxRate ile TL; fiyat alanlarını da TL'ye çevirip
        // currency'yi "TRY" yaparız → tabloda tutarlı TL (USD karışmaz). Modal ayrı: orada
        // kontrat fiyatı "$" gösterilir (kullanıcı kotasyon birimini görsün diye).
        boolean isUsdQuote = "USD".equalsIgnoreCase(spec.currency());
        // Güncel fiyat gösterimi → BUGÜNÜN kuru (fxNow).
        BigDecimal currentTl = isUsdQuote ? current.multiply(fxNow) : current;
        holding.setCurrentPrice(currentTl);
        // Ortalama alış (giriş fiyatı) gösterimi → BUGÜNÜN kuru (fxNow) ile TL. Resmi mark-to-market:
        // maliyet de güncel kurla TL'leşir → tablo iç-tutarlı: K/Z = (mevcut_TL − ortalama_TL) × mult.
        // (Per-date giriş kuru SADECE teminat tarafında; gösterim/K-Z güncel kur.) İDEMPOTENT: lokal
        // HAM USD {@code avgEntry}'den hesaplanır (read-modify-write DEĞİL) → 2 kez çağrılsa bile
        // ×fxNow² (çifte-FX) olmaz (divide-back yukarıda fxNow ile yapıldı).
        if (isUsdQuote && holding.getAverageCost() != null) {
            holding.setAverageCost(avgEntry.multiply(fxNow));
        }
        holding.setMarketValue(mv);
        holding.setProfitLoss(pnl);

        // K/Z YÜZDESİ — pay/payda KUR TUTARLILIĞI. K/Z (pnl) GÜNCEL kurla (fxNow, mark-to-market)
        // hesaplanır; ama gösterilen teminat (totalCost) per-date GİRİŞ kuruyla (fxEntry) bağlıdır.
        // Frontend % = pnl / totalCost yapsaydı pay(fxNow)/payda(fxEntry) → USD-kote'de % kur oranı
        // kadar şişerdi. Burada paydayı pnl ile AYNI kura (fxNow) hizalayıp doğrudan set ederiz:
        //   % = pnl / (avgEntry × fxNow × mult × marginRate)   [teminatın güncel-kur karşılığı]
        // Böylece kaldıraçlı gerçek getiri korunur, sadece kur uyuşmazlığı giderilir. TRY kontratta
        // fxNow=1 → payda = totalCost ile aynı → davranış DEĞİŞMEZ (eski % korunur). marginAmount
        // (scrape, zaten TL) yolunda fxNow uygulanmaz → o zaten kur-bağımsız, tutarsızlık yok.
        if (isUsdQuote && pnl != null) {
            BigDecimal marginAtNow = valuationService.marginPosted(qty, avgEntry, spec, fxNow);
            if (marginAtNow != null && marginAtNow.signum() > 0) {
                holding.setProfitLossPercent(pnl.divide(marginAtNow, 10, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .setScale(2, RoundingMode.HALF_UP));
            }
        }

        // VİOP "Toplam Maliyet" semantiği = YATIRILAN TEMİNAT (kullanıcının cebinden çıkan para),
        // NOT qty × entry (spot mantığı — VİOP'ta tam fiyatı ödemezsin, sadece teminatı bağlarsın).
        // Builder qty × entry'i öncelikli set ediyor; burada override ediyoruz. Bu sayede:
        //   - Portföy "Toplam Maliyet" toplamı = açık pozisyonlar için bağlanan toplam teminat ✓
        //   - "Reel K/Z" hesabı (mv − totalCost) anlamlı kalır (önceden +1350% gibi absürt sonuçlar)
        //   - K/Z % hesabı = pnl / teminat = gerçek getiri oranı
        holding.setTotalCost(marginPosted);

        // USD-kote kontratta tüm para alanları TL'ye çevrildiğinden etiket de TRY olmalı
        // (yoksa tablo "53.541 USD" gibi yanlış gösterir — değer TL ama birim USD çelişkisi).
        holding.setCurrency(isUsdQuote ? "TRY" : (spec.currency() != null ? spec.currency() : "TRY"));
        holding.setName(d.getName() != null ? d.getName() : contractName);
        LocalDateTime asOf = PortfolioDateTimeParse.parseLenient(d.getTime());
        holding.setAsOf(asOf != null ? asOf : LocalDateTime.now());

        // Method-scope direction sign: changePct, change ve realized hesaplarında ortak kullanılır.
        BigDecimal dirSign = valuationService.directionSign(direction);

        // Günlük yüzde — SHORT için yön çevrilir (fiyat düşüşü = SHORT için kar)
        BigDecimal changePct = d.getChangePercent();
        if (changePct != null) {
            holding.setChangePercent(changePct.multiply(dirSign));
        }
        // Gün yüksek/düşük de fiyat → USD kontratta TL'ye çevir (tablo tutarlı TL).
        BigDecimal high = d.getHigh();
        BigDecimal low  = d.getLow();
        holding.setDayHigh(isUsdQuote && high != null ? high.multiply(fxNow) : high);
        holding.setDayLow(isUsdQuote && low != null ? low.multiply(fxNow) : low);

        // VİOP-spesifik alanlar — frontend HoldingsTable + StockDetailPage burada okur
        holding.setViopMultiplier(multiplier);
        holding.setViopMarginRate(spec.marginRate());
        holding.setViopNotional(notional);
        holding.setViopMarginPosted(marginPosted);
        holding.setViopLeverage(leverage);
        if (holding.getViopDirection() == null) {
            holding.setViopDirection("LONG"); // geriye uyumluluk
        }

        // Teminat sağlığı (margin call kontrolü): equity / initialMargin.
        // 1.0 = tam, 0.5 = yarısı yendi, 0 = tükendi, negatif = margin call (alarmı tetikler).
        //
        // KUR TUTARLILIĞI: Gerçek VİOP'ta margin call DAİMA aynı-an kıyasıdır — equity ve teminat
        // referansı AYNI günün (bugünün) değerleridir. equity = teminat + K/Z; K/Z mark-to-market ile
        // GÜNCEL kurda (fxNow). Eğer payda fxEntry (giriş kuru) teminatı olsaydı, pay(fxNow)/payda(fxEntry)
        // → iki farklı kur tabanı karışır, oran kur oranı kadar kayardı (K/Z% ile de tutarsız olurdu).
        // Bu yüzden USD-kote'de oranı GÜNCEL-kur teminat referansıyla (marginAtNow) hesaplarız:
        //   marginRatio = (marginAtNow + pnl) / marginAtNow  = 1 + K/Z%(fxNow)
        // Böylece teminat durumu ile K/Z% AYNI tabanda olur ve margin-call eşiği gerçek-an kıyasıdır.
        // GÖSTERİLEN teminat (totalCost/viopMarginPosted) per-date GİRİŞ kuruyla kalır (sabit referans,
        // resmi örnek) — yalnız oranın PAYDASI güncel kura hizalanır. TRY kontratta fxNow=1 →
        // marginAtNow == marginPosted → davranış AYNEN korunur. scrape marginAmount yolunda da
        // marginPosted kur-bağımsız (zaten TL) → marginAtNow == marginPosted, değişmez.
        BigDecimal marginBase = marginPosted;
        if (isUsdQuote) {
            BigDecimal marginAtNow = valuationService.marginPosted(qty, avgEntry, spec, fxNow);
            if (marginAtNow != null && marginAtNow.signum() > 0) {
                marginBase = marginAtNow;
            }
        }
        BigDecimal marginRatio = (marginBase != null && marginBase.signum() > 0)
                ? marginBase.add(pnl != null ? pnl : BigDecimal.ZERO)
                        .divide(marginBase, 4, RoundingMode.HALF_UP)
                : null;
        holding.setMarginRatio(marginRatio);
        holding.setMarginStatus(classifyMarginStatus(marginRatio));

        // Not: Realized P/L FUTURE düzeltmesi (multiplier × dirSign) Builder'da yapılır.
        // Enricher PortfolioServiceImpl tarafından parallel cache amacıyla BİR KEZ DAHA
        // çağrılır → idempotent olmayan read+multiply+write burada 1000× bug üretir.

        // Günlük "change" (kontrat başına TL fark) — direction-aware, USD-kote ise fx ile TL'ye:
        //   LONG : (current − prevSettle) × fx × multiplier        — fiyat ↑ = kar
        //   SHORT: (current − prevSettle) × fx × multiplier × −1   — fiyat ↓ = kar (= LONG'un tersi)
        // Frontend bu değeri qty ile çarpıp günlük K/Z'yi hesaplar; direction'ı tekrar uygulamaz.
        // fxNow TRY kontratta 1 → değişmez; USD'de mv/pnl ile tutarlı TL üretir. (Günlük değişim →
        // bugünün kuru; giriş-tarihi kuru burada uygun değil, change tek-günlük fiyat hareketidir.)
        BigDecimal prevSet = d.getPrevSettlementPrice();
        if (prevSet != null) {
            holding.setChange(current.subtract(prevSet).multiply(fxNow).multiply(multiplier).multiply(dirSign)
                    .setScale(MONEY_SCALE, RoundingMode.HALF_UP));
        }

        // Kontrat ismi Akbank kayıt adıyla farklıysa grafik enrichment'ı kanonik isimle bir kez daha dene.
        String canonical = d.getName() != null ? d.getName() : contractName;
        if (!canonical.trim().equals(contractName)) {
            applyViopYearChartMetrics(holding, canonical);
        }
    }

    /**
     * İş Yatırım grafik serisinden (1Y → 6A → 3A → 1A, ilk dolu seri) min/max aralığı ve
     * günlük son kapanışlardan MA20/MA50 hesaplar.
     */
    private void applyViopYearChartMetrics(PortfolioHoldingResponse holding, String chartContractName) {
        if (chartContractName == null || chartContractName.isBlank()) {
            return;
        }
        String trimmed = chartContractName.trim();
        List<ViopChartPoint> pts = null;
        try {
            for (ViopChartPeriod p : List.of(
                    ViopChartPeriod.ONE_YEAR,
                    ViopChartPeriod.SIX_MONTHS,
                    ViopChartPeriod.THREE_MONTHS,
                    ViopChartPeriod.ONE_MONTH)) {
                List<ViopChartPoint> chunk = viopChartService.getChart(trimmed, p);
                if (chunk != null && chunk.size() >= 2) {
                    pts = chunk;
                    break;
                }
            }
        } catch (UnsupportedViopContractException ex) {
            log.debug("VIOP chart unsupported for '{}': {}", trimmed, ex.getMessage());
            return;
        } catch (Exception ex) {
            log.debug("VIOP chart fetch failed for '{}': {}", trimmed, ex.getMessage());
            return;
        }
        if (pts == null || pts.isEmpty()) {
            return;
        }
        List<ViopChartPoint> sorted = new ArrayList<>(pts);
        sorted.sort(Comparator.comparing(ViopChartPoint::getTimestamp, Comparator.nullsLast(Long::compareTo)));

        List<BigDecimal> allVals = new ArrayList<>();
        TreeMap<LocalDate, BigDecimal> dailyLast = new TreeMap<>();
        for (ViopChartPoint p : sorted) {
            if (p.getValue() == null) {
                continue;
            }
            allVals.add(p.getValue());
            LocalDate day = chartPointToLocalDate(p);
            if (day != null) {
                dailyLast.put(day, p.getValue());
            }
        }
        if (allVals.isEmpty()) {
            return;
        }
        BigDecimal lo = allVals.stream().min(BigDecimal::compareTo).orElse(null);
        BigDecimal hi = allVals.stream().max(BigDecimal::compareTo).orElse(null);
        if (lo != null && hi != null) {
            holding.setFiftyTwoWeekLow(lo.setScale(PRICE_SCALE, RoundingMode.HALF_UP));
            holding.setFiftyTwoWeekHigh(hi.setScale(PRICE_SCALE, RoundingMode.HALF_UP));
        }

        List<BigDecimal> dailyCloses = new ArrayList<>(dailyLast.values());
        BigDecimal ma20 = PortfolioMovingAverage.simpleMa(dailyCloses, 20);
        BigDecimal ma50 = PortfolioMovingAverage.simpleMa(dailyCloses, 50);
        if (ma20 != null) {
            holding.setMa20(ma20);
        }
        if (ma50 != null) {
            holding.setMa50(ma50);
        }
    }

    /**
     * Kontratın para birimine göre fiyat→TL çarpanını çözer.
     * <ul>
     *   <li>TRY (veya null) kontrat → {@code 1} (çevrim yok, davranış değişmez).</li>
     *   <li>USD-kote kontrat (EURUSD, XAUUSD, … — 7 sözleşme) → 1 USD→TL TCMB satış kuru.</li>
     * </ul>
     * Kur çekilemezse (kaynak çöktü / sembol yok) güvenli fallback {@code 1} (ham USD — en azından
     * çökmez; valuation da fxRate≤0'ı 1'e indirger) + warn. Per-date (giriş) kuru ileride
     * geliştirilebilir — şu an hem giriş hem güncel için anlık kur (eurobond Model 1 ile aynı yaklaşım).
     */
    private BigDecimal resolveFxRate(ViopContractSpec spec) {
        if (spec == null || !"USD".equalsIgnoreCase(spec.currency())) {
            return BigDecimal.ONE; // TRY kontrat → çevrim yok
        }
        BigDecimal rate = lookupUsdTryRate();
        if (rate == null || rate.signum() <= 0) {
            log.warn("VIOP USD-kote kontrat için USD/TRY kuru alınamadı (spec code={}); "
                    + "fxRate=1 fallback (fiyat ham USD kalır)", spec.code());
            return BigDecimal.ONE;
        }
        return rate;
    }

    /**
     * GİRİŞ (alış günü) USD/TRY kuru — per-date FX (TEMİNAT için). USD-kote kontratta, başlangıç
     * teminatı O GÜNÜN kuruyla bağlanır (resmi VİOP örneği de açılış kurundan hesaplar). TCMB
     * tarihsel serisinden {@code buyDate}'e ≤ en yakın (forward-fill) kapanış.
     *
     * <p>TRY kontratta 1 döner (etkisiz). buyDate null ya da tarihsel kur bulunamazsa GÜVENLİ
     * fallback: {@code fxNow} (anlık kur) — yani en kötü durumda eski (anlık-kur) davranışına döner,
     * hata/çökme üretmez. unit USD'de 1.
     *
     * <p><b>BİLİNEN KISIT (TODO — düşük öncelik, ~%5 etki):</b> {@code buyDate} olarak pozisyonun
     * {@code firstBuyDate}'i (İLK alımın tarihi) geçiliyor. Aynı kontrattan FARKLI tarihlerde +
     * FARKLI kurlarda ÇOKLU alım yapılırsa, teminatın TAMAMI ilk alımın kuruyla bağlanır; oysa
     * resmi VİOP'ta her lot kendi alış-günü kuruyla bağlanmalı. {@code PortfolioHoldingsBuilder}
     * her lot'un (tarih, cost, qty)'sini {@code lots[]} listesinde TUTUYOR → ileride enricher'a
     * per-lot tarih akıtıp her lot'u kendi kuruyla toplayarak düzeltilebilir. Etki SADECE: USD-kote
     * + çoklu-alım + farklı kurlar + YAML-oran yolu (scrape marginAmount yoksa) kesişiminde; tek-alım
     * pozisyonlarda (yaygın durum) ETKİ YOK. K/Z (mark-to-market, fxNow) bundan ETKİLENMEZ.
     */
    private BigDecimal resolveEntryFxRate(ViopContractSpec spec, LocalDate buyDate, BigDecimal fxNow) {
        if (spec == null || !"USD".equalsIgnoreCase(spec.currency())) {
            return BigDecimal.ONE; // TRY kontrat → çevrim yok
        }
        if (buyDate == null) {
            return fxNow; // tarih yok → anlık kura düş (eski davranış)
        }
        try {
            // buyDate'i kapsayacak küçük pencere (±10 gün): hafta sonu/tatilde en yakın iş günü.
            List<FxHistoryPoint> pts = tcmbFxHistoryPort.fetchHistory(
                    "USD", buyDate.minusDays(10), buyDate.plusDays(2));
            BigDecimal best = null;
            LocalDate bestDay = null;
            for (FxHistoryPoint p : pts) {
                if (p.getDate() == null || p.getClose() == null || p.getClose().signum() <= 0) {
                    continue;
                }
                LocalDate day;
                try {
                    day = LocalDate.parse(p.getDate().substring(0, 10));
                } catch (Exception e) {
                    continue;
                }
                if (day.isAfter(buyDate)) {
                    continue; // alış gününden SONRAKİ kuru kullanma
                }
                if (bestDay == null || day.isAfter(bestDay)) {
                    bestDay = day;
                    best = p.getClose(); // buyDate'e ≤ en yakın (forward-fill)
                }
            }
            if (best != null && best.signum() > 0) {
                return best;
            }
        } catch (Exception e) {
            log.debug("VIOP giriş kuru (per-date) alınamadı buyDate={}: {}", buyDate, e.getMessage());
        }
        return fxNow; // tarihsel kur yok → anlık kura düş (güvenli)
    }

    /**
     * 1 USD → TL satış kuru (TCMB). Bulunamazsa null. {@code BondHoldingEnricher.lookupFxRateToTry}
     * ile aynı desen (TCMB güncel kur, satış yoksa alış; unit>1 ise normalize).
     */
    private BigDecimal lookupUsdTryRate() {
        try {
            FxLatestRates latest = marketFxService.getTcmbLatestRates("USD");
            if (latest == null || latest.getRates() == null) {
                return null;
            }
            FxRateItem rate = latest.getRates().stream()
                    .filter(r -> "USD".equalsIgnoreCase(r.getSymbol()))
                    .findFirst().orElse(null);
            if (rate == null) {
                return null;
            }
            BigDecimal sell = rate.getSell() != null ? rate.getSell() : rate.getBuy();
            if (sell == null) {
                return null;
            }
            int unit = rate.getUnit() > 1 ? rate.getUnit() : 1;
            return unit > 1
                    ? sell.divide(BigDecimal.valueOf(unit), 8, RoundingMode.HALF_UP)
                    : sell;
        } catch (Exception e) {
            log.debug("VIOP USD/TRY kuru alınamadı: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Teminat oranını üç bantta sınıflar:
     * <ul>
     *   <li>{@code HEALTHY} — oran &gt; 0.50 (teminatın yarısından fazlası sağlam)</li>
     *   <li>{@code WARNING} — 0.25 &lt; oran ≤ 0.50 (yarısı yendi, dikkat)</li>
     *   <li>{@code CRITICAL} — oran ≤ 0.25 (negatif/margin call dahil)</li>
     * </ul>
     */
    private static String classifyMarginStatus(BigDecimal ratio) {
        if (ratio == null) {
            return null;
        }
        if (ratio.compareTo(new BigDecimal("0.50")) > 0) {
            return "HEALTHY";
        }
        if (ratio.compareTo(new BigDecimal("0.25")) > 0) {
            return "WARNING";
        }
        return "CRITICAL";
    }

    private static LocalDate chartPointToLocalDate(ViopChartPoint p) {
        String dt = p.getDateTime();
        if (dt == null || dt.length() < 10) {
            return null;
        }
        try {
            return LocalDate.parse(dt.substring(0, 10));
        } catch (Exception e) {
            return null;
        }
    }
}
