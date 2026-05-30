import { useEffect, useState, useCallback, useMemo } from 'react';
import { useParams, Link } from 'react-router-dom';
import {
  ArrowLeft, RefreshCw, TrendingUp, TrendingDown, Star, BarChart2,
} from 'lucide-react';
import { getFxHistory, getFxTcmb, getFxOpen, getBankCurrencyRates } from '../../../api/marketApi.js';
import { FX_META, FlagImg } from './utils/fxMeta';
import { useTranslation } from '../../../context/LanguageContext';
import UniversalCompareButton from '../../../components/common/UniversalCompareButton';
import TrendBadge from '../../../components/common/TrendBadge';
import InstrumentActionButtons from '../../../components/instrument/InstrumentActionButtons';
import { buildTrendItem } from '../../../utils/trendUtils';
import FxChart from './components/FxChart';
import OpenRateCard from './components/OpenRateCard';
import BankRatesCard from './components/BankRatesCard';
import FxConverter from './components/FxConverter';

// ── Helper ────────────────────────────────────────────────────────────────────
function fmt(v, dec = 4) {
  if (v == null) return '-';
  return parseFloat(v).toLocaleString('tr-TR', { minimumFractionDigits: dec, maximumFractionDigits: dec });
}

// (RANGES/RANGE_LABELS/FX_MA/FX_SUB_INDICATORS/DRAWING_TOOLS, kline overlay
//  kayıtları ve DrawingToolbar + FxChart bileşenleri components/FxChart.jsx'e;
//  OpenRateCard, BankRatesCard, FxConverter components/ altındaki kendi
//  dosyalarına taşındı.)

export default function FxDetailPage() {
  const { t } = useTranslation();
  const { symbol } = useParams();
  const sym = symbol?.toUpperCase();

  const [history, setHistory]       = useState(null);
  const [loading, setLoading]       = useState(true);
  const [loadingChart, setLoadingChart] = useState(false);
  const [range, setRange]           = useState('1M');
  const [currentRate, setCurrentRate] = useState(null);
  const [allRates, setAllRates]     = useState([]);

  // Yan panel verileri
  const [allBankRows, setAllBankRows] = useState([]);
  const [loadingBanks, setLoadingBanks] = useState(true);
  const [openTryPerUnit, setOpenTryPerUnit] = useState(null);

  // Watchlist
  const [inWatchlist, setInWatchlist] = useState(() => {
    try { return JSON.parse(localStorage.getItem('fx_watchlist') ?? '[]').includes(sym); } catch { return false; }
  });
  function toggleWatchlist() {
    try {
      const wl = JSON.parse(localStorage.getItem('fx_watchlist') ?? '[]');
      const updated = inWatchlist ? wl.filter(x => x !== sym) : [...wl, sym];
      localStorage.setItem('fx_watchlist', JSON.stringify(updated));
      setInWatchlist(!inWatchlist);
    } catch {}
  }

  // TCMB rates
  useEffect(() => {
    getFxTcmb()
      .then(data => {
        const rates = data?.rates ?? [];
        setAllRates(rates);
        setCurrentRate(rates.find(r => r.symbol === sym) ?? null);
      })
      .catch(() => {});
  }, [sym]);

  // Banka kurları — tüm bankaları çek, bu döviz için hepsini göster (kuru olmayan banka da listelenir)
  useEffect(() => {
    setLoadingBanks(true);
    getBankCurrencyRates()
      .then(data => setAllBankRows(Array.isArray(data) ? data : []))
      .catch(() => setAllBankRows([]))
      .finally(() => setLoadingBanks(false));
  }, []);

  const bankRows = useMemo(() => {
    const banks = [...new Set(allBankRows.map(r => r.bankName).filter(Boolean))];
    const rows = banks.map(b => {
      const row = allBankRows.find(r => r.bankName === b && r.currencyCode === sym);
      return { bankName: b, buyRate: row?.buyRate ?? null, sellRate: row?.sellRate ?? null, has: !!row };
    });
    // Kuru olan bankalar üstte, ardından alfabetik
    return rows.sort((a, b) => (Number(b.has) - Number(a.has)) || a.bankName.localeCompare(b.bankName, 'tr'));
  }, [allBankRows, sym]);

  // Open Exchange Rates — base TRY → 1 sym = (1 / value) TRY
  useEffect(() => {
    setOpenTryPerUnit(null);
    getFxOpen('TRY')
      .then(data => {
        const item = (data?.rates ?? []).find(r => r.symbol === sym);
        const v = item?.sell != null ? parseFloat(item.sell) : null;
        if (v && v > 0) setOpenTryPerUnit(1 / v);
      })
      .catch(() => {});
  }, [sym]);

  // History
  const loadHistory = useCallback(() => {
    setLoadingChart(true);
    getFxHistory(symbol, range)
      .then(data => { setHistory(data); setLoading(false); })
      .catch(() => { setHistory(null); setLoading(false); })
      .finally(() => setLoadingChart(false));
  }, [symbol, range]);
  useEffect(() => { setLoading(true); loadHistory(); }, [loadHistory]);

  const chartPoints = useMemo(
    () => history?.points?.map(p => ({ date: p.date, value: parseFloat(p.close) })) ?? [],
    [history],
  );
  const isUp = chartPoints.length > 1 && chartPoints[chartPoints.length - 1].value >= chartPoints[0].value;
  const strokeColor = isUp ? '#10b981' : '#ef4444';

  const trendItem = useMemo(() => buildTrendItem(chartPoints.map(p => p.value), 'FX'), [chartPoints]);

  const buy  = currentRate?.buy;
  const sell = currentRate?.sell;
  const unit = currentRate?.unit;
  const effBuy  = currentRate?.effectiveBuy;
  const effSell = currentRate?.effectiveSell;

  const firstVal = chartPoints.length > 1 ? chartPoints[0].value : null;
  const lastVal  = chartPoints.length > 1 ? chartPoints[chartPoints.length - 1].value : null;
  const change   = firstVal && lastVal ? lastVal - firstVal : null;
  const changePct = firstVal && change != null ? (change / firstVal) * 100 : null;
  const changePositive = (change ?? 0) >= 0;

  const statCards = [
    { label: 'Döviz Alış',   en: 'Forex Buying',     value: fmt(buy) },
    { label: 'Döviz Satış',  en: 'Forex Selling',    value: fmt(sell) },
    ...(effBuy != null  ? [{ label: 'Efektif Alış',  en: 'Banknote Buying',  value: fmt(effBuy) }]  : []),
    ...(effSell != null ? [{ label: 'Efektif Satış', en: 'Banknote Selling', value: fmt(effSell) }] : []),
    { label: 'Birim',        en: 'Unit',             value: unit ?? '-' },
  ];

  return (
    <div className="space-y-6 m3-fade-in">
      {/* Üst aksiyon çubuğu */}
      <div className="flex items-center justify-between flex-wrap gap-3">
        <Link to="/market/fx" className="m3-state inline-flex items-center gap-1.5 text-sm text-[#093eaa] font-semibold hover:underline rounded-full px-2 py-1 -ml-2">
          <ArrowLeft className="w-4 h-4" /> {t('Döviz Kurları')}
        </Link>
        <button
          onClick={toggleWatchlist}
          className={`m3-state inline-flex items-center gap-2 px-4 py-2 rounded-full text-sm font-semibold border transition-all ${
            inWatchlist ? 'bg-amber-50 border-amber-300 text-amber-700' : 'bg-white border-[#e2e8f0] text-[#5a6472] hover:border-[#093eaa]/40'
          }`}>
          <Star className={`w-4 h-4 ${inWatchlist ? 'fill-amber-400 text-amber-400' : ''}`} />
          {inWatchlist ? t('İzleme Listesinde') : t('İzleme Listesine Ekle')}
        </button>
      </div>

      {/* Başlık kartı — sol: kimlik+fiyat+istatistik · sağ: döviz çevirici */}
      <div className="bg-white rounded-3xl border border-[#e2e8f0] shadow-[0_1px_3px_rgba(15,23,42,0.06)] p-6">
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          {/* Sol */}
          <div className="flex flex-col">
            <div className="flex items-center gap-4">
              <div className="w-14 h-14 rounded-full bg-[#eef2f8] ring-1 ring-[#e2e8f0] flex items-center justify-center overflow-hidden shrink-0">
                <FlagImg cc={FX_META[sym]?.cc} size={40} />
              </div>
              <div>
                <div className="flex items-center gap-2 flex-wrap">
                  <h1 className="text-2xl sm:text-3xl font-black text-[#1a1c1e] leading-tight">{sym}/TRY</h1>
                  <span className="bg-[#eef2f8] px-2.5 py-1 rounded-full text-xs font-semibold text-[#5a6472]">{FX_META[sym]?.ad ?? sym}</span>
                </div>
                {loading ? (
                  <div className="flex items-center gap-1.5 py-2">
                    <div className="w-2 h-2 bg-[#093eaa] rounded-full animate-bounce" />
                    <div className="w-2 h-2 bg-[#093eaa]/60 rounded-full animate-bounce [animation-delay:120ms]" />
                    <div className="w-2 h-2 bg-[#093eaa]/30 rounded-full animate-bounce [animation-delay:240ms]" />
                  </div>
                ) : (
                  <div className="flex items-end gap-3 flex-wrap mt-2">
                    {sell != null && <span className="text-3xl sm:text-4xl font-black text-[#1a1c1e] tabular-nums tracking-tight">{fmt(sell)}</span>}
                    {change != null && (
                      <span className={`flex items-center gap-1 text-sm font-bold mb-1.5 ${changePositive ? 'text-emerald-600' : 'text-rose-600'}`}>
                        {changePositive ? <TrendingUp className="w-4 h-4" /> : <TrendingDown className="w-4 h-4" />}
                        {changePositive ? '+' : ''}{fmt(change)} ({changePositive ? '+' : ''}{fmt(changePct, 2)}%)
                      </span>
                    )}
                    {trendItem && <span className="mb-1"><TrendBadge item={trendItem} size="sm" /></span>}
                  </div>
                )}
              </div>
            </div>

            {/* Portföye Ekle + Alarm (yalnız giriş yapan kullanıcı) */}
            <div className="mt-4">
              <InstrumentActionButtons
                assetType="FX"
                symbol={sym}
                name={FX_META[sym]?.ad ?? sym}
                price={sell}
              />
            </div>

            {/* İstatistik çubuğu (sola alta yaslı) */}
            {currentRate && (
              <div className="mt-5 pt-5 border-t border-[#eef2f8] grid grid-cols-2 sm:grid-cols-3 gap-2 m3-stagger">
                {statCards.map(item => (
                  <div key={item.label} className="bg-[#f6f8fc] rounded-2xl p-3 text-center">
                    <p className="text-xs text-[#5a6472] font-semibold leading-tight">{t(item.label)}</p>
                    <p className="text-[10px] text-[#9aa6b6] mb-1.5">{item.en}</p>
                    <p className="text-sm font-bold text-[#1a1c1e] tabular-nums">{item.value}</p>
                  </div>
                ))}
              </div>
            )}
          </div>

          {/* Sağ — döviz çevirici (önceden boş duran alan) */}
          <div className="lg:border-l lg:border-[#eef2f8] lg:pl-6 flex flex-col justify-center">
            <FxConverter symbol={sym} allRates={allRates} embedded />
          </div>
        </div>
      </div>

      {/* Grafik (sol) + yan panel (sağ) — kolonlar üstten hizalı (grafik kartı esnemesin) */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6 lg:items-start">
        <div className="lg:col-span-2 bg-white rounded-3xl border border-[#e2e8f0] shadow-[0_1px_3px_rgba(15,23,42,0.06)] p-5">
          {/* Grafik başlığı + karşılaştır butonları (grafiğin içine alındı) */}
          <div className="flex items-center justify-between gap-2 mb-3 flex-wrap">
            <span className="font-bold text-[#1a1c1e] text-sm flex items-center gap-2">
              <span className="rounded-full ring-1 ring-[#e2e8f0] overflow-hidden inline-flex"><FlagImg cc={FX_META[sym]?.cc} size={18} /></span>
              {sym}/TRY
            </span>
            <div className="flex items-center gap-2 flex-wrap">
              <button onClick={loadHistory} className="m3-state p-2 rounded-full bg-[#eef2f8] hover:bg-[#e3eaf6] transition-all text-[#5a6472]" title={t('Yenile')}>
                <RefreshCw className={`w-4 h-4 ${loadingChart ? 'animate-spin' : ''}`} />
              </button>
              <Link to={`/market/compare?symbols=${sym}`} title={t('Dövizleri kendi karşılaştırma sayfasında kıyasla')}
                className="m3-state inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full text-xs font-bold border border-[#093eaa]/25 text-[#093eaa] bg-[#093eaa]/5 hover:bg-[#093eaa]/10 transition-all">
                <BarChart2 className="w-3.5 h-3.5" /> {t('Karşılaştır')}
              </Link>
              <UniversalCompareButton assetType="FX" symbol={sym} name={`${sym}/TRY`} />
            </div>
          </div>

          <FxChart
            chartPoints={chartPoints}
            lineColor={strokeColor}
            mainLabel={`${sym}/TRY`}
            range={range}
            onRangeChange={setRange}
            loadingChart={loadingChart}
          />
        </div>

        <div className="space-y-6">
          <OpenRateCard sym={sym} tryPerUnit={openTryPerUnit} />
          <BankRatesCard rates={bankRows} loading={loadingBanks} />
        </div>
      </div>
    </div>
  );
}
