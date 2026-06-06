import { useEffect, useLayoutEffect, useState, useRef, useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { ChevronDown, ChevronUp, LineChart } from 'lucide-react';
import {
  readTickerPrefs,
  readCustomTickerItems,
  readTickerSpeed,
  TICKER_PREFS_EVENT,
  TICKER_SPEED_PX,
} from '../../utils/tickerPrefs';
import {
  getFxTcmb,
  getCryptos,
  getGoldSpot,
  getFxHistory,
  getGoldHistory,
  getCryptoChart,
  getMarketPriceHistory,
  getIndex,
} from '../../api/marketApi';
import { useTranslation } from '../../context/LanguageContext';
import Sparkline from './Sparkline';
import {
  COLOR_UP, COLOR_DOWN, COLOR_NEUTRAL,
  tickerHref, trendSparkline, dirFromChangePct, dailyChangeFromSeries,
  sparkFromFxHistory, downsample, scaleSparkToEnd,
  sparkFromGeckoChart, sparkFromGoldHist,
} from './tickerUtils';

const TICKER_OPEN_STORAGE_KEY = 'finance-portal-market-ticker-open';
export function MarketTicker() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [allItems, setAllItems] = useState([]);
  const [customItems, setCustomItems] = useState([]);
  const [customDefs, setCustomDefs] = useState(readCustomTickerItems);
  const [enabledKeys, setEnabledKeys] = useState(readTickerPrefs);
  const [tickerSpeed, setTickerSpeed] = useState(readTickerSpeed);
  // Etkin varsayılan öğeler + kullanıcının eklediği özel öğeler.
  // Özel öğe, zaten varsayılanlarda olan bir enstrümansa (ör. BTC) tekrar eklenmez.
  const items = useMemo(() => {
    const base = allItems.filter(it => !it.key || enabledKeys.has(it.key));
    const tokens = new Set(base.map(it => (it.key || '').toLowerCase()));
    const extra = [];
    for (const it of customItems) {
      const tok = it.tok || (it.key || '').toLowerCase();
      if (tokens.has(tok)) continue;
      tokens.add(tok);
      extra.push(it);
    }
    return [...base, ...extra];
  }, [allItems, customItems, enabledKeys]);
  const [tickerOpen, setTickerOpen] = useState(() => {
    try {
      return localStorage.getItem(TICKER_OPEN_STORAGE_KEY) !== '0';
    } catch {
      return true;
    }
  });
  const trackRef = useRef(null);
  const containerRef = useRef(null);
  const offsetRef = useRef(0);
  const [isPaused, setIsPaused] = useState(false);
  // Şeridi dolduracak kopya sayısı (az öğede bile sürekli, kesintisiz kaysın diye dinamik hesaplanır).
  const [copies, setCopies] = useState(2);

  useEffect(() => {
    try {
      localStorage.setItem(TICKER_OPEN_STORAGE_KEY, tickerOpen ? '1' : '0');
    } catch {
      /* ignore */
    }
  }, [tickerOpen]);

  useEffect(() => {
    async function load() {
      try {
        const [fx, cryptos, goldSpot, histUsd, histEur, histGbp, goldHist] =
          await Promise.all([
            getFxTcmb().catch(() => null),
            getCryptos(0, 50).catch(() => []),
            getGoldSpot().catch(() => null),
            getFxHistory('USD', '1M').catch(() => null),
            getFxHistory('EUR', '1M').catch(() => null),
            getFxHistory('GBP', '1M').catch(() => null),
            getGoldHistory('1M', 'USD').catch(() => null),
          ]);

        const tickerCryptoDays = 14;
        const cryptoChartEntries = await Promise.all(
          ['btc', 'eth', 'bnb', 'sol'].map(sym => {
            const c = cryptos.find(x => x.symbol?.toLowerCase() === sym);
            if (!c?.id) return Promise.resolve([sym, null]);
            return getCryptoChart(c.id, tickerCryptoDays, 'try').then(d => [sym, d]).catch(() => [sym, null]);
          })
        );
        const cryptoCharts = Object.fromEntries(cryptoChartEntries);

        const result = [];
        const rates = fx?.rates ?? [];

        const pushTcmbPair = (sym, hist, rateRow) => {
          if (!rateRow) return;
          const val = parseFloat(rateRow.sell);
          const { spark, changePct, dir } = sparkFromFxHistory(hist);
          const sparkline = spark.length >= 2 ? spark : trendSparkline(val, 0);
          const ch = changePct != null && Number.isFinite(changePct) ? changePct : null;
          result.push({
            key: `fx:${sym}`,
            label: `${sym}/TRY`,
            value: val.toLocaleString('tr-TR', { minimumFractionDigits: 3 }),
            change: ch != null && Math.abs(ch) > 1e-6 ? ch : null,
            dir: ch != null ? dir : null,
            spark: sparkline,
          });
        };

        pushTcmbPair('USD', histUsd, rates.find(x => x.symbol === 'USD'));
        pushTcmbPair('EUR', histEur, rates.find(x => x.symbol === 'EUR'));
        pushTcmbPair('GBP', histGbp, rates.find(x => x.symbol === 'GBP'));

        // Banka kurları ("USD/TRY Banka" vb.) ticker'dan kaldırıldı — TCMB fx:USD/EUR/GBP yeterli.

        // Altın/ONS — spot + /api/v1/gold/history (USD ONS) ile büyük grafikle aynı seri.
        // YÜZDE = goldSpot.changePercent (GÜNLÜK, API'den) — grafik aylık seri ama yüzde günlük.
        if (goldSpot?.price) {
          const val = parseFloat(goldSpot.price);
          const spotChg = parseFloat(goldSpot.changePercent ?? 0);
          const g = sparkFromGoldHist(goldHist);
          let spark = g.spark;
          if (spark.length >= 2) spark = scaleSparkToEnd(spark, val);
          else spark = trendSparkline(val, spotChg);
          const ch = Number.isFinite(spotChg) && Math.abs(spotChg) > 1e-6 ? spotChg : null;
          result.push({
            key: 'gold:ons',
            label: t('ALTIN/ONS'),
            value: val.toLocaleString('tr-TR', { minimumFractionDigits: 2 }),
            change: ch,
            dir: dirFromChangePct(spotChg),
            spark,
          });
        }

        // Kripto — CoinGecko market_chart (detay sayfasındaki çizgi grafik verisi)
        ['btc', 'eth', 'bnb', 'sol'].forEach(sym => {
          const c = cryptos.find(x => x.symbol?.toLowerCase() === sym);
          if (!c) return;
          const val = parseFloat(c.currentPrice ?? 0);
          // YÜZDE = priceChangePercentage24h (CoinGecko GÜNLÜK 24s değişim). Grafik aylık seri
          // (görsel trend) ama yüzde günlük — önceden aylık ilk→son fark gösteriyordu (yanıltıcı).
          const chgApi = parseFloat(c.priceChangePercentage24h ?? 0);
          const chart = cryptoCharts[sym];
          const g = sparkFromGeckoChart(chart);
          const spark = g.spark.length >= 2 ? g.spark : trendSparkline(val, chgApi);
          const ch = Number.isFinite(chgApi) && Math.abs(chgApi) > 1e-6 ? chgApi : null;
          const d = dirFromChangePct(chgApi);
          result.push({
            key: `crypto:${sym}`,
            coinId: c.id,            // CoinGecko id (ör. "bitcoin") — detay route'u için
            label: c.symbol?.toUpperCase(),
            value: val.toLocaleString('tr-TR', { minimumFractionDigits: 0 }),
            change: ch,
            dir: d,
            spark,
          });
        });

        // BIST endeksleri (XU100/XU030/XU050) — backend INDICATOR pseudo-türünden Yahoo'dan günlük seri.
        // Backend boş veri döndürürse (örn. Yahoo sembol tuzağı, ağ hatası) console.warn ile loglarız ki
        // ileride sessiz başarısızlıkları teşhis edebilelim — UI'da sadece öğe atlanır.
        await Promise.all(
          ['XU100', 'XU030', 'XU050'].map(async (sym) => {
            try {
              // Spark için seri + GÜNLÜK % için getIndex (detay sayfasıyla AYNI resmi değer).
              // Endeks serisi SAATLİK (168 nokta/ay) → "son 2 nokta" son 2 SAAT olur, günlük değil;
              // o yüzden % resmi changePercent'ten alınır (BIST50 ticker vs detay tutarsızlığı fix).
              const [data, idx] = await Promise.all([
                getMarketPriceHistory('INDICATOR', sym, '1M'),
                getIndex(sym).catch(() => null),
              ]);
              const closes = (data?.closePrices ?? []).map(Number).filter(v => Number.isFinite(v) && v > 0);
              if (closes.length < 1) {
                console.warn(`[MarketTicker] BIST index ${sym} returned empty closePrices`, data);
                return;
              }
              const last = closes[closes.length - 1];
              const pct = idx?.changePercent != null ? Number(idx.changePercent) : null;  // resmi GÜNLÜK
              const spark = closes.length >= 2 ? downsample(closes, 28) : trendSparkline(last, pct ?? 0);
              const label = sym === 'XU100' ? 'BIST 100' : sym === 'XU030' ? 'BIST 30' : 'BIST 50';
              result.push({
                key: `bist:${sym}`,
                label,
                value: last.toLocaleString('tr-TR', { maximumFractionDigits: 2 }),
                change: pct != null && Math.abs(pct) > 1e-6 ? pct : null,
                dir: dirFromChangePct(pct),
                spark,
              });
            } catch (err) {
              // Endpoint hatası — neden olduğunu konsola yaz (gelecekteki ağ/CORS/500 teşhisi için).
              console.warn(`[MarketTicker] BIST index fetch failed: ${sym}`, err);
            }
          })
        );

        // NOT: TÜFE / Politika Faizi / ÜFE / Mevduat Faizi gibi makro/faiz göstergeleri ticker'dan
        // KALDIRILDI — ticker sürekli değişen CANLI fiyatlar içindir; aylık güncellenen statik makro
        // veriler buraya uygun değildi (yön/sparkline'ı da yoktu). Bu veriler Ekonomi sayfasında mevcut.

        setAllItems(result);
      } catch {
        setAllItems([]);
      }
    }
    load();
  }, []);

  // Hesap Ayarları'ndan ticker tercihleri değişince güncelle (aynı sekme)
  useEffect(() => {
    const onPrefs = () => {
      setEnabledKeys(readTickerPrefs());
      setCustomDefs(readCustomTickerItems());
      setTickerSpeed(readTickerSpeed());
    };
    window.addEventListener(TICKER_PREFS_EVENT, onPrefs);
    window.addEventListener('storage', onPrefs);
    return () => {
      window.removeEventListener(TICKER_PREFS_EVENT, onPrefs);
      window.removeEventListener('storage', onPrefs);
    };
  }, []);

  // Kullanıcının eklediği özel varlıklar — genel price-history'den fiyat + sparkline
  useEffect(() => {
    let cancelled = false;
    if (!customDefs.length) { setCustomItems([]); return undefined; }
    Promise.all(customDefs.map(async d => {
      try {
        const data = await getMarketPriceHistory(d.assetType, d.symbol, '3M');
        const rawCloses = (data?.closePrices ?? []).map(Number);
        const ts = data?.timestamps ?? [];
        // GÜNLÜK % — timestamp ile (hisse serisi saatlik olabilir; "son 2 saat" tuzağına düşme).
        const pct = dailyChangeFromSeries(rawCloses, ts);
        const closes = rawCloses.filter(v => Number.isFinite(v) && v > 0);
        if (closes.length < 1) return null;
        const last = closes[closes.length - 1];
        const spark = closes.length >= 2 ? downsample(closes, 28) : trendSparkline(last, pct ?? 0);
        // VİOP (FUTURE) custom kalemi gerçek döviz/varlıkla karışmasın diye "VİOP" etiketlenir
        // (ör. "USDTRY" VİOP vadeli ≠ "USD/TRY" döviz kuru).
        // t() ile çevrilir: backend'den Türkçe gelen isimler (GRAM ALTIN/BUĞDAY...) EN modunda
        // çeviri tablosundan İngilizceye döner; çeviri yoksa orijinal (Türkçe) kalır (güvenli).
        const baseLabel = t(d.name || d.symbol);
        const label = d.assetType === 'FUTURE' ? `${baseLabel} · VİOP` : baseLabel;
        return {
          key: `custom:${d.assetType}:${d.symbol}`,
          tok: `${d.assetType}:${d.symbol}`.toLowerCase(),
          custom: true,
          label,
          value: last.toLocaleString('tr-TR', { maximumFractionDigits: 4 }),
          change: pct != null && Math.abs(pct) > 1e-6 ? pct : null,
          dir: dirFromChangePct(pct),
          spark,
        };
      } catch {
        return null;
      }
    })).then(arr => { if (!cancelled) setCustomItems(arr.filter(Boolean)); });
    return () => { cancelled = true; };
  }, [customDefs]);

  // İçerik şeride sığıyor mu? Sığıyorsa kaydırma yok (tek kopya, soldan hizalı, sabit).
  useLayoutEffect(() => {
    const container = containerRef.current;
    const track = trackRef.current;
    if (!container || !track || !tickerOpen) return;
    const oneSetW = track.scrollWidth / copies;
    if (oneSetW <= 0) return;
    const needed = Math.max(2, Math.ceil(container.clientWidth / oneSetW) + 1);
    if (needed !== copies) setCopies(needed);
  }, [items, copies, tickerOpen]);

  // Kesintisiz kayan şerit (yalnız taşma varsa): track'i CSS transform ile sola taşı.
  // İçerik iki kez basılır; bir set genişliğine gelince başa sar.
  useEffect(() => {
    if (!tickerOpen || items.length === 0) {
      if (trackRef.current) trackRef.current.style.transform = 'translate3d(0,0,0)';
      offsetRef.current = 0;
      return undefined;
    }
    const track = trackRef.current;
    if (!track) return undefined;
    // px/sn — kullanıcı tercihinden (slow=20, normal=40, fast=80); bilinmeyen değerde varsayılan 40.
    const SPEED = TICKER_SPEED_PX[tickerSpeed] ?? 40;
    let raf;
    let last = performance.now();
    const step = (now) => {
      const dt = Math.min(0.05, (now - last) / 1000);
      last = now;
      if (!isPaused) {
        const oneSet = track.scrollWidth / copies;
        if (oneSet > 0) {
          offsetRef.current += SPEED * dt;
          if (offsetRef.current >= oneSet) offsetRef.current -= oneSet;
          track.style.transform = `translate3d(${-offsetRef.current}px,0,0)`;
        }
      }
      raf = requestAnimationFrame(step);
    };
    raf = requestAnimationFrame(step);
    return () => cancelAnimationFrame(raf);
  }, [isPaused, items, tickerOpen, copies, tickerSpeed]);

  if (!items.length) return null;

  if (!tickerOpen) {
    return (
      <div className="bg-gray-50 border-b border-gray-200">
        <button
          type="button"
          onClick={() => setTickerOpen(true)}
          className="w-full px-4 sm:px-6 lg:px-8 flex items-center justify-between h-9 text-left hover:bg-gray-100 transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-[#093eaa]/30 group"
          aria-expanded="false"
          aria-label={t('Piyasa şeridini göster')}
        >
          <span className="flex items-center gap-2 text-xs font-medium text-gray-500">
            <LineChart className="w-3.5 h-3.5 text-gray-400 shrink-0" aria-hidden />
            {t('Piyasa özeti gizli')}
          </span>
          <span className="flex items-center gap-1 text-xs font-semibold text-[#093eaa] group-hover:underline">
            {t('Göster')}
            <ChevronDown className="w-4 h-4 shrink-0" aria-hidden />
          </span>
        </button>
      </div>
    );
  }

  const TickerCard = ({ item }) => {
    const isUp = item.dir === 'up';
    const isDown = item.dir === 'down';
    const valueColor = isUp ? COLOR_UP : isDown ? COLOR_DOWN : COLOR_NEUTRAL;
    const sparkColor = isUp ? COLOR_UP : isDown ? COLOR_DOWN : COLOR_NEUTRAL;
    const href = tickerHref(item);

    // Ticker'da yüzde GÖSTERİLMEZ — sadece isim + fiyat + mini grafik (yön renkten belli).
    // Yüzde detay sayfalarında var; ticker'da sade ve tutarsızlık riski sıfır.
    const inner = (
      <>
        <div className="flex-1 min-w-0">
          <p className="text-[10px] font-bold text-gray-400 uppercase tracking-wider mb-0.5 truncate" title={item.label}>{item.label}</p>
          <p className="text-sm font-bold leading-tight" style={{ color: valueColor }}>{item.value}</p>
        </div>
        <Sparkline data={item.spark} color={sparkColor} />
      </>
    );

    const baseCls = 'flex items-center gap-3 shrink-0 px-5 border-r border-gray-200 last:border-r-0 min-w-[160px] max-w-[240px]';

    // Detay sayfası olan öğeler tıklanabilir; olmayanlar (ekonomi göstergesi) düz kutu.
    if (href) {
      return (
        <button
          type="button"
          onClick={() => navigate(href)}
          className={`${baseCls} text-left cursor-pointer hover:bg-[#eef4ff] transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-[#093eaa]/40`}
          title={`${item.label} ${t('detayına git')}`}
        >
          {inner}
        </button>
      );
    }
    return <div className={baseCls}>{inner}</div>;
  };

  return (
    <div className="bg-white border-b border-gray-200 flex">
      <button
        type="button"
        onClick={() => setTickerOpen(false)}
        className="shrink-0 w-10 flex items-center justify-center border-r border-gray-100 bg-gray-50 hover:bg-gray-100 text-gray-600 transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-[#093eaa]/40"
        title={t('Piyasa şeridini gizle')}
        aria-expanded="true"
        aria-label={t('Piyasa şeridini gizle')}
      >
        <ChevronUp className="w-4 h-4" aria-hidden />
      </button>
      <div
        ref={containerRef}
        className="flex-1 min-w-0 overflow-hidden"
        onMouseEnter={() => setIsPaused(true)}
        onMouseLeave={() => setIsPaused(false)}
      >
        <div ref={trackRef} className="flex items-stretch w-max select-none py-2 will-change-transform">
          {Array.from({ length: copies }).flatMap((_, c) =>
            items.map((item, i) => <TickerCard key={`${c}-${item.label}-${i}`} item={item} />)
          )}
        </div>
      </div>
    </div>
  );
}
