import { useEffect, useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import { ArrowLeft, TrendingUp, TrendingDown, BarChart2 } from 'lucide-react';
import {
  AreaChart, Area, XAxis, YAxis, CartesianGrid, Tooltip,
  ResponsiveContainer
} from 'recharts';
import { getStockMidasDetail, getMarketPriceHistory } from '../../../api/marketApi';
import TrendBadge from '../../../components/common/TrendBadge';
import { SkeletonDetail } from '../../../components/common/Skeleton';
import InstrumentActionButtons from '../../../components/instrument/InstrumentActionButtons';
import { buildTrendItem } from '../../../utils/trendUtils';
import { registerOverlay } from 'klinecharts';
import RelatedViopContracts from './components/RelatedViopContracts';
import CandlestickChart from './components/CandlestickChart';
import LineChart from './components/LineChart';
import { useTranslation } from '../../../context/LanguageContext';

// ── Custom Overlay Kayıtları (uygulama başında bir kez çalışır) ──────────────
// customRect: 2 köşe noktasıyla dikdörtgen çizer
registerOverlay({
  name: 'customRect',
  totalStep: 3,
  needDefaultPointFigure: true,
  needDefaultXAxisFigure: true,
  needDefaultYAxisFigure: true,
  createPointFigures: ({ overlay, coordinates }) => {
    if (coordinates.length === 2) {
      const x = Math.min(coordinates[0].x, coordinates[1].x);
      const y = Math.min(coordinates[0].y, coordinates[1].y);
      const w = Math.abs(coordinates[1].x - coordinates[0].x);
      const h = Math.abs(coordinates[1].y - coordinates[0].y);
      return [{
        type: 'rect',
        attrs: { x, y, width: w, height: h },
        styles: { style: 'stroke_fill', color: 'rgba(22,119,255,0.15)', borderColor: '#1677ff', borderSize: 1 },
      }];
    }
    return [];
  },
});

// customCircle: merkez + kenar noktasıyla çember çizer
registerOverlay({
  name: 'customCircle',
  totalStep: 3,
  needDefaultPointFigure: true,
  needDefaultXAxisFigure: true,
  needDefaultYAxisFigure: true,
  createPointFigures: ({ overlay, coordinates }) => {
    if (coordinates.length === 2) {
      const dx = coordinates[1].x - coordinates[0].x;
      const dy = coordinates[1].y - coordinates[0].y;
      const r = Math.sqrt(dx * dx + dy * dy);
      return [{
        type: 'circle',
        attrs: { x: coordinates[0].x, y: coordinates[0].y, r },
        styles: { style: 'stroke_fill', color: 'rgba(22,119,255,0.15)', borderColor: '#1677ff', borderSize: 1 },
      }];
    }
    return [];
  },
});


/* ─── Custom Tooltip ─── */
function ChartTooltip({ active, payload, label }) {
  if (!active || !payload?.length) return null;
  return (
    <div className="bg-[#093eaa] text-white text-xs px-3 py-2 rounded-lg shadow-lg pointer-events-none">
      <p className="font-bold">₺{parseFloat(payload[0].value).toFixed(2)}</p>
      <p className="opacity-80 mt-0.5">{label}</p>
    </div>
  );
}

/* ─── Interactive Chart ─── */
function StockChart({ timestamps, prices }) {
  if (!timestamps?.length || !prices?.length) {
    return (
      <div className="h-64 flex items-center justify-center text-gray-300 text-sm bg-gray-50 rounded-xl">
        Grafik verisi yok
      </div>
    );
  }

  const data = timestamps
    .map((t, i) => {
      if (prices[i] == null) return null;
      const d = new Date(t * 1000);
      const label = d.toLocaleString('tr-TR', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' });
      return { time: label, price: parseFloat(prices[i]) };
    })
    .filter(Boolean);

  const vals = data.map(d => d.price);
  const minVal = Math.min(...vals) * 0.9995;
  const maxVal = Math.max(...vals) * 1.0005;
  const isPos = vals[vals.length - 1] >= vals[0];
  const color = isPos ? '#10b981' : '#ef4444';

  return (
    <ResponsiveContainer width="100%" height={280}>
      <AreaChart data={data} margin={{ top: 10, right: 20, left: 0, bottom: 0 }}>
        <defs>
          <linearGradient id="grad" x1="0" y1="0" x2="0" y2="1">
            <stop offset="5%"  stopColor={color} stopOpacity={0.2} />
            <stop offset="95%" stopColor={color} stopOpacity={0} />
          </linearGradient>
        </defs>
        <CartesianGrid strokeDasharray="3 3" stroke="#f1f5f9" vertical={false} />
        <XAxis dataKey="time" tick={{ fontSize: 10, fill: '#94a3b8' }} tickLine={false} axisLine={false}
          interval={Math.floor(data.length / 5)} />
        <YAxis domain={[minVal, maxVal]} tick={{ fontSize: 10, fill: '#94a3b8' }}
          tickLine={false} axisLine={false} tickFormatter={v => v.toFixed(2)} width={60} />
        <Tooltip content={<ChartTooltip />} cursor={{ stroke: '#093eaa', strokeWidth: 1, strokeDasharray: '4 4' }} />
        <Area type="monotone" dataKey="price" stroke={color} strokeWidth={2.5}
          fill="url(#grad)" dot={false} activeDot={{ r: 5, fill: color, stroke: '#fff', strokeWidth: 2 }} />
      </AreaChart>
    </ResponsiveContainer>
  );
}


function MetricCard({ label, value, highlight }) {
  const { t } = useTranslation();
  if (!value) return null;
  return (
    <div className="bg-gray-50 rounded-xl p-3 sm:p-4">
      <p className="text-xs text-gray-400 mb-1">{t(label)}</p>
      <p className={`text-sm font-bold ${highlight ? 'text-[#093eaa]' : 'text-gray-900'}`}>{value}</p>
    </div>
  );
}

/* ─── Info Row ─── */
function InfoRow({ label, value }) {
  const { t } = useTranslation();
  if (!value) return null;
  return (
    <div className="flex justify-between items-start py-3.5 border-b border-gray-100 last:border-0">
      <span className="text-sm text-gray-500 w-32 sm:w-44 shrink-0">{t(label)}</span>
      <span className="text-sm text-gray-900 font-medium text-right max-w-xs">{value}</span>
    </div>
  );
}

/* ─── Main Page ─── */
export default function StockDetailPage() {
  const { t } = useTranslation();
  const { symbol } = useParams();
  const [midas, setMidas] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [chartMode, setChartMode] = useState('tv');
  const [trendItem, setTrendItem] = useState(null);

  useEffect(() => {
    setLoading(true);
    Promise.all([
      getStockMidasDetail(symbol).catch(() => null),
    ]).then(([m]) => { setMidas(m); })
      .catch(e => setError(!e.response ? t('Sunucuya ulaşılamıyor.') : `${t('Hata')} (${e.response.status})`))
      .finally(() => setLoading(false));
  }, [symbol]);

  // Trend rozeti — 1Y kapanış serisinden (MA20/50 + 52h konumu)
  useEffect(() => {
    let cancelled = false;
    setTrendItem(null);
    getMarketPriceHistory('STOCK', symbol, '1Y')
      .then(res => { if (!cancelled) setTrendItem(buildTrendItem(res?.closePrices ?? [], 'STOCK')); })
      .catch(() => { if (!cancelled) setTrendItem(null); });
    return () => { cancelled = true; };
  }, [symbol]);

  const ticker = symbol?.replace('.IS', '').replace('.is', '').toUpperCase();
  const isPos = midas?.dailyChangePercent && !midas.dailyChangePercent.startsWith('-');

  return (
    <div className="max-w-4xl mx-auto space-y-3 sm:space-y-6">
      {/* Back + Title */}
      <div className="flex items-center gap-3">
        <Link to="/market/stocks" className="text-gray-400 hover:text-[#093eaa] transition-colors">
          <ArrowLeft className="w-5 h-5" />
        </Link>
        <div className="flex items-center gap-3">
          {midas?.logoUrl && (
            <img
              src={`/api/v1/proxy/image?url=${encodeURIComponent(midas.logoUrl)}`}
              alt={midas.name}
              className="w-10 h-10 rounded-xl object-contain border border-gray-100 p-0.5 bg-white shadow-sm"
              onError={e => { e.target.style.display = 'none'; }}
            />
          )}
          <div>
            <div className="flex items-center gap-2 flex-wrap">
              <h1 className="text-xl sm:text-2xl font-bold text-gray-900">
                {ticker} {t('Hisse')} {midas?.name ? `- ${midas.name}` : ''}
              </h1>
              {trendItem && <TrendBadge item={trendItem} size="sm" />}
            </div>
            <p className="text-xs text-gray-400 mt-0.5">{t('Borsa İstanbul · Veriler 15 dk gecikmeli · Kaynak: Midas')}</p>
          </div>
        </div>
      </div>

      {loading && <SkeletonDetail />}

      {error && <div className="bg-red-50 border border-red-200 text-red-600 text-sm px-6 py-4 rounded-2xl">{error}</div>}

      {!loading && !error && (
        <>
          {/* ── Price + Volume + Chart ── */}
          <div className="bg-white rounded-2xl border border-gray-200 shadow-sm overflow-hidden">
            <div className="grid grid-cols-1 sm:grid-cols-2 divide-y sm:divide-y-0 sm:divide-x divide-gray-100">
              <div className="p-4 sm:p-6">
                <p className="text-3xl sm:text-4xl font-bold text-gray-900">
                  {midas?.currentPrice ?? '-'}
                </p>
                <p className="text-sm text-gray-400 mt-1">{t('Güncel Fiyat')}</p>
              </div>
              <div className="p-4 sm:p-6">
                <p className="text-xl sm:text-2xl font-bold text-gray-900 truncate">
                  {midas?.dailyVolume ?? '-'}
                </p>
                <p className="text-sm text-gray-400 mt-1">{t('Günlük İşlem Hacmi')}</p>
              </div>
            </div>

            <div className="px-4 pt-2 pb-2">
              {/* Grafik mod seçici (segmented) + Karşılaştır */}
              <div className="flex items-center justify-between gap-2 mb-3 flex-wrap">
                <div className="inline-flex items-center gap-0.5 rounded-lg border border-gray-200 bg-gray-50 p-0.5">
                  {[{ key: 'tv', label: 'Mum Grafik' }, { key: 'line', label: 'Çizgi' }].map(m => (
                    <button key={m.key} onClick={() => setChartMode(m.key)}
                      className={`px-3 py-1.5 rounded-md text-xs font-semibold transition-all ${chartMode === m.key ? 'bg-white text-[#093eaa] shadow-sm' : 'text-gray-500 hover:text-gray-800'}`}>
                      {t(m.label)}
                    </button>
                  ))}
                </div>
                <div className="flex items-center gap-2 flex-wrap">
                  <Link
                    to={`/market/stocks/compare?add=${encodeURIComponent(symbol)}`}
                    className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-semibold border border-[#093eaa]/30 text-[#093eaa] bg-[#093eaa]/5 hover:bg-[#093eaa]/10 transition-colors"
                  >
                    <BarChart2 className="w-3.5 h-3.5" />
                    {t('Karşılaştır')}
                  </Link>
                  <InstrumentActionButtons
                    assetType="STOCK"
                    symbol={symbol}
                    name={midas?.name || ticker}
                    price={midas?.currentPrice}
                  />
                </div>
              </div>

              {chartMode === 'tv' && <CandlestickChart symbol={symbol} />}

              {chartMode === 'line' && <LineChart symbol={symbol} />}
            </div>

            {midas?.dailyChangePercent && (
              <div className="px-6 pb-5">
                <span className={`text-sm font-bold flex items-center gap-1.5 ${isPos ? 'text-emerald-600' : 'text-rose-600'}`}>
                  {isPos ? <TrendingUp className="w-4 h-4" /> : <TrendingDown className="w-4 h-4" />}
                  {t('Günlük Değişim:')} {midas.dailyChange ?? ''} ({midas.dailyChangePercent})
                </span>
              </div>
            )}
          </div>

          {/* ── Financial Metrics Table ── */}
          <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-3 sm:p-6">
            <h2 className="text-base font-bold text-gray-900 mb-4">{ticker} {t('Hisse ve Finansal Bilgileri')}</h2>
            <div className="grid grid-cols-2 sm:grid-cols-3 gap-2 sm:gap-3">
              <MetricCard label="Son İşlem Fiyatı"     value={midas?.currentPrice} highlight />
              <MetricCard label="Alış"                 value={midas?.bid} />
              <MetricCard label="Satış"                value={midas?.ask} />
              <MetricCard label="Açılış Fiyatı"        value={midas?.openPrice} />
              <MetricCard label="Günlük Değişim %"     value={midas?.dailyChangePercent} />
              <MetricCard label="Günlük Değişim (TL)"  value={midas?.dailyChange} />
              <MetricCard label="Günlük Hacim (TL)"    value={midas?.dailyVolume} />
              <MetricCard label="Günlük Hacim (Lot)"   value={midas?.volumeLot} />
              <MetricCard label="Tavan"                value={midas?.upperLimit} />
              <MetricCard label="Taban"                value={midas?.lowerLimit} />
              <MetricCard label="Haftalık En Yüksek"   value={midas?.weeklyHigh} />
              <MetricCard label="Haftalık En Düşük"    value={midas?.weeklyLow} />
              <MetricCard label="Aylık En Yüksek"      value={midas?.monthlyHigh} />
              <MetricCard label="Aylık En Düşük"       value={midas?.monthlyLow} />
              <MetricCard label="Piyasa Değeri"        value={midas?.marketCap} />
              <MetricCard label="Sermaye"              value={midas?.capital} />
              <MetricCard label="F/K"                  value={midas?.peRatio} />
              <MetricCard label="PD/DD"                value={midas?.pbRatio} />
              <MetricCard label="Halka Açıklık (%)"    value={midas?.freeFloat} />
              <MetricCard label="Yabancı Oranı (%)"    value={midas?.foreignRatio} />
              <MetricCard label="Volatilite"           value={midas?.volatility} />
              <MetricCard label="Net Kâr"              value={midas?.netProfit} />
            </div>
          </div>

          {/* ── Ortaklık Yapısı ── */}
          {midas?.shareholders?.length > 0 && (
            <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-3 sm:p-6">
              <h2 className="text-base font-bold text-gray-900 mb-6">{ticker} {t('Ortaklık Yapısı')}</h2>
              <table className="w-full">
                <thead className="bg-gray-50">
                  <tr>
                    <th className="text-left px-4 py-3 text-xs font-bold text-gray-500 uppercase tracking-wider">{t('Ticari Ünvan')}</th>
                    <th className="text-right px-4 py-3 text-xs font-bold text-gray-500 uppercase tracking-wider">{t('Pay Oranı (%)')}</th>
                  </tr>
                </thead>
                <tbody>
                  {midas.shareholders.map((s, i) => (
                    <tr key={i} className="border-t border-gray-100 hover:bg-gray-50 transition-colors">
                      <td className="px-4 py-3 text-sm text-gray-800">{s.name}</td>
                      <td className="px-4 py-3 text-sm font-semibold text-gray-900 text-right">{s.sharePercent}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          {/* ── Şirket Hakkında ── */}
          <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-3 sm:p-6">
            <h2 className="text-base font-bold text-gray-900 mb-1">{ticker} - {t('Şirket Hakkında')}</h2>
            {midas?.name && (
              <p className="text-sm text-gray-400 mb-6">
                {midas.name} {t('şirketiyle ilgili bilmen gerekenleri aşağıda bulabilirsin.')}
              </p>
            )}
            <InfoRow label="CEO"              value={midas?.ceo} />
            <InfoRow label="Kuruluş Tarihi"   value={midas?.foundedDate} />
            <InfoRow label="Halka Arz Tarihi" value={midas?.ipoDate} />
            <InfoRow label="Sektör"           value={midas?.sector} />
            <InfoRow label="Çalışan Sayısı"   value={midas?.employeeCount} />
            <InfoRow label="Adres"            value={midas?.address} />
            <InfoRow label="Merkez"           value={midas?.country} />
          </div>

          {/* ── İlişkili VİOP Kontratları ── */}
          <RelatedViopContracts symbol={ticker} />

          {/* ── Şirket Açıklaması ── */}
          {(midas?.description || midas?.logoUrl) && (
            <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-3 sm:p-6">
              {/* Logo + Name header */}
              <div className="flex items-center gap-4 mb-6">
                {midas.logoUrl && (
                  <img
                    src={`/api/v1/proxy/image?url=${encodeURIComponent(midas.logoUrl)}`}
                    alt={midas.name}
                    className="w-16 h-16 rounded-xl object-contain border border-gray-100 p-1 bg-white"
                    onError={e => { e.target.style.display = 'none'; }}
                  />
                )}
                <h2 className="text-xl font-bold text-gray-900">{midas.name ?? ticker}</h2>
              </div>
              {midas.description && midas.description.split('\n\n').map((para, i) => (
                <p key={i} className="text-sm text-gray-600 leading-relaxed mb-4 last:mb-0">{para}</p>
              ))}
            </div>
          )}

          {!midas && (
            <div className="bg-white rounded-2xl border border-gray-200 p-12 text-center">
              <p className="text-gray-400">{t('Bu hisse için detay verisi bulunamadı.')}</p>
            </div>
          )}
        </>
      )}
    </div>
  );
}
