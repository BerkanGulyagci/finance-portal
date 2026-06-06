import { useEffect, useMemo, useState } from 'react';
import {
  ResponsiveContainer,
  ComposedChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
} from 'recharts';
import { getCryptoFearGreed } from '../../../../api/marketApi';
import { useTranslation } from '../../../../context/LanguageContext';

// ── Korku & Açgözlülük (Fear & Greed) renk skalası ──────────────────────────
// Düşük (korku) = kırmızı, yüksek (açgözlülük) = yeşil.
const FNG_COLOR = '#7c3aed';            // F&G çizgisi (mor — fiyat mavisinden ayrışsın)
const PRICE_COLOR = '#093eaa';          // coin fiyatı (kurumsal mavi)

/** classification (backend, İngilizce sabit) → renk/etiket-anahtarı eşlemesi. */
const CLASSIFICATION_META = {
  'Extreme Fear': { color: '#dc2626', key: 'Aşırı Korku' },
  Fear:           { color: '#f97316', key: 'Korku' },
  Neutral:        { color: '#eab308', key: 'Nötr' },
  Greed:          { color: '#84cc16', key: 'Açgözlülük' },
  'Extreme Greed':{ color: '#16a34a', key: 'Aşırı Açgözlülük' },
};

/** Sayısal değerden (0-100) duygu rengi — classification gelmezse yedek. */
function valueColor(v) {
  if (v == null) return '#9ca3af';
  if (v < 25) return '#dc2626';
  if (v < 45) return '#f97316';
  if (v < 55) return '#eab308';
  if (v < 75) return '#84cc16';
  return '#16a34a';
}

/** ms timestamp → gün anahtarı (UTC, "YYYY-MM-DD") — gün-bazında eşleştirme için. */
function dayKey(ms) {
  const d = new Date(Number(ms));
  if (Number.isNaN(d.getTime())) return null;
  return `${d.getUTCFullYear()}-${String(d.getUTCMonth() + 1).padStart(2, '0')}-${String(d.getUTCDate()).padStart(2, '0')}`;
}

/** ms → kısa, yerelleştirilmiş tarih etiketi (X ekseni / tooltip). */
function dateLabel(ms, locale) {
  return new Date(Number(ms)).toLocaleDateString(locale, { day: '2-digit', month: 'short' });
}

/**
 * F&G (günlük) ile fiyat serisini GÜN BAZINDA birleştirir.
 *
 * Strateji: F&G eksenini taban al (talep edilen `days` penceresini o kapsar).
 * Fiyat serisini gün-anahtarına indeksle (aynı güne birden çok fiyat noktası
 * düşerse — saatlik/farklı granülerlik — o günün SONuncusunu/kapanışını tut,
 * fiyat dizisi zaten artan zaman sıralı geldiği için son atama kalır).
 * Her F&G günü için eşleşen fiyatı bağla; fiyat yoksa null bırak (recharts
 * connectNulls ile çizgi köprülenir). Sonuç artan zaman sıralı döner.
 */
function mergeByDay(fngRows, priceData) {
  const priceByDay = new Map();
  for (const p of priceData ?? []) {
    const k = dayKey(p?.ts);
    if (k != null && p?.price != null) priceByDay.set(k, Number(p.price));
  }

  return (fngRows ?? [])
    .map((r) => {
      const ts = Number(r?.timestamp);
      const k = dayKey(ts);
      const value = r?.value == null ? null : Number(r.value);
      return {
        ts,
        dayKey: k,
        value,
        classification: r?.classification ?? null,
        price: k != null && priceByDay.has(k) ? priceByDay.get(k) : null,
      };
    })
    .filter((row) => row.dayKey != null && !Number.isNaN(row.ts))
    .sort((a, b) => a.ts - b.ts);
}

/** Custom tooltip: aynı günün fiyatı + F&G değeri & (yerelleştirilmiş) sınıfı. */
function FngTooltip({ active, payload, t, language, locale }) {
  if (!active || !payload?.length) return null;
  const row = payload[0]?.payload;
  if (!row) return null;
  const meta = CLASSIFICATION_META[row.classification];
  const clsLabel = row.classification
    ? (language === 'tr' && meta ? t(meta.key) : row.classification)
    : null;
  const clsColor = meta?.color ?? valueColor(row.value);

  return (
    <div className="rounded-lg border border-gray-200 bg-white px-3 py-2 shadow-md text-xs">
      <p className="font-semibold text-gray-700 mb-1">{dateLabel(row.ts, locale)}</p>
      {row.price != null && (
        <p className="text-gray-600">
          <span className="font-medium" style={{ color: PRICE_COLOR }}>{t('Fiyat')}:</span>{' '}
          {Number(row.price).toLocaleString(locale, { maximumFractionDigits: 2 })}
        </p>
      )}
      {row.value != null && (
        <p className="text-gray-600">
          <span className="font-medium" style={{ color: FNG_COLOR }}>{t('Korku & Açgözlülük')}:</span>{' '}
          <span style={{ color: clsColor, fontWeight: 600 }}>
            {row.value}{clsLabel ? ` (${clsLabel})` : ''}
          </span>
        </p>
      )}
    </div>
  );
}

/**
 * Korku & Açgözlülük (Fear & Greed) vs coin fiyatı — çift eksenli recharts kartı.
 *
 * Props:
 *  - priceData: CryptoDetailPage'in chartData state'i [{ ts (ms), price }, ...]
 *               (ZATEN mevcut; tekrar çekilmez).
 *  - coinName:  başlıkta gösterilecek coin adı (ör. "Bitcoin").
 *  - days:      F&G penceresi (gün), varsayılan 90.
 */
export default function FearGreedChart({ priceData = [], coinName, days = 90 }) {
  const { t, language } = useTranslation();
  const locale = language === 'tr' ? 'tr-TR' : 'en-US';

  // Tek bir "fetch" state'i tutarız: { days, rows, error }. Effect İÇİNDE senkron
  // setState YOK (set-state-in-effect lint kuralı + gereksiz cascade-render yok);
  // tüm güncellemeler async callback'lerden gelir. loading = "elimizdeki veri bu
  // `days` için mi" karşılaştırmasından TÜRETİLİR (ayrı setLoading'e gerek yok).
  const [result, setResult] = useState({ days: null, rows: [], error: false });

  useEffect(() => {
    let alive = true;
    getCryptoFearGreed(days)
      .then((rows) => { if (alive) setResult({ days, rows: Array.isArray(rows) ? rows : [], error: false }); })
      .catch(() => { if (alive) setResult({ days, rows: [], error: true }); });
    return () => { alive = false; };
  }, [days]);

  const loaded = result.days === days;        // bu `days` için sonuç geldi mi
  const loading = !loaded;                     // gelmediyse yükleniyor
  const error = loaded && result.error;

  const merged = useMemo(
    () => mergeByDay(loaded ? result.rows : [], priceData),
    [loaded, result.rows, priceData],
  );

  // Üst rozet: en yeni F&G noktası.
  const latest = useMemo(() => {
    for (let i = merged.length - 1; i >= 0; i--) {
      if (merged[i].value != null) return merged[i];
    }
    return null;
  }, [merged]);

  const latestMeta = latest ? CLASSIFICATION_META[latest.classification] : null;
  const latestColor = latest ? (latestMeta?.color ?? valueColor(latest.value)) : '#9ca3af';
  const latestLabel = latest?.classification
    ? (language === 'tr' && latestMeta ? t(latestMeta.key) : latest.classification)
    : null;

  const title = coinName
    ? `${coinName} ${t('Fiyatı vs Korku & Açgözlülük')}`
    : t('Korku & Açgözlülük');

  const hasData = merged.some((r) => r.value != null);

  return (
    <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-3 sm:p-5">
      <div className="flex items-center justify-between gap-3 mb-3 flex-wrap">
        <p className="text-xs font-bold text-gray-500 uppercase tracking-wider">{title}</p>
        {latest && (
          <span
            className="inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-bold text-white"
            style={{ backgroundColor: latestColor }}
            title={t('Güncel Korku & Açgözlülük endeksi')}
          >
            {latest.value}{latestLabel ? ` — ${latestLabel}` : ''}
          </span>
        )}
      </div>

      {loading ? (
        <div className="flex items-center justify-center h-[240px] sm:h-[300px]">
          <div className="flex gap-1.5">
            <div className="w-2 h-2 bg-[#093eaa] rounded-full animate-bounce" />
            <div className="w-2 h-2 bg-[#093eaa]/60 rounded-full animate-bounce [animation-delay:100ms]" />
            <div className="w-2 h-2 bg-[#093eaa]/30 rounded-full animate-bounce [animation-delay:200ms]" />
          </div>
        </div>
      ) : error || !hasData ? (
        <p className="text-center text-sm text-gray-400 py-12">{t('Veri bulunamadı')}</p>
      ) : (
        <>
          <div className="w-full h-[240px] sm:h-[300px]">
            <ResponsiveContainer width="100%" height="100%">
              <ComposedChart data={merged} margin={{ top: 8, right: 8, left: 0, bottom: 0 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="#f1f5f9" />
                <XAxis
                  dataKey="ts"
                  type="number"
                  scale="time"
                  domain={['dataMin', 'dataMax']}
                  tick={{ fontSize: 11, fill: '#6b7280' }}
                  tickFormatter={(v) => dateLabel(v, locale)}
                  minTickGap={28}
                />
                <YAxis
                  yAxisId="price"
                  tick={{ fontSize: 11, fill: PRICE_COLOR }}
                  tickFormatter={(v) => Number(v).toLocaleString(locale, { notation: 'compact', maximumFractionDigits: 1 })}
                  width={52}
                  domain={['auto', 'auto']}
                />
                <YAxis
                  yAxisId="fng"
                  orientation="right"
                  domain={[0, 100]}
                  ticks={[0, 25, 50, 75, 100]}
                  tick={{ fontSize: 11, fill: FNG_COLOR }}
                  width={34}
                />
                <Tooltip content={<FngTooltip t={t} language={language} locale={locale} />} />
                <Line
                  yAxisId="price"
                  type="monotone"
                  dataKey="price"
                  name={t('Fiyat')}
                  stroke={PRICE_COLOR}
                  strokeWidth={2}
                  dot={false}
                  connectNulls
                  isAnimationActive={false}
                />
                <Line
                  yAxisId="fng"
                  type="monotone"
                  dataKey="value"
                  name={t('Korku & Açgözlülük')}
                  stroke={FNG_COLOR}
                  strokeWidth={2}
                  dot={false}
                  connectNulls
                  isAnimationActive={false}
                />
              </ComposedChart>
            </ResponsiveContainer>
          </div>
          {/* Legend + kaynak notu */}
          <div className="flex items-center justify-between gap-3 mt-2 flex-wrap">
            <div className="flex flex-wrap gap-3">
              <span className="flex items-center gap-1.5 text-xs font-semibold text-gray-600">
                <span className="w-3 h-0.5 rounded" style={{ backgroundColor: PRICE_COLOR }} />
                {t('Fiyat')}
              </span>
              <span className="flex items-center gap-1.5 text-xs font-semibold text-gray-600">
                <span className="w-3 h-0.5 rounded" style={{ backgroundColor: FNG_COLOR }} />
                {t('Korku & Açgözlülük')} (0–100)
              </span>
            </div>
            <p className="text-[11px] text-gray-400">{t('Piyasa geneli · son {n} gün', { n: days })}</p>
          </div>
        </>
      )}
    </div>
  );
}
