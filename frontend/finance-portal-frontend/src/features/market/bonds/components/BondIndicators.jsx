import { useMemo } from 'react';
import { fmtPct } from '../utils/bondChartUtils';
import { useTranslation } from '../../../../context/LanguageContext';

// ── Yardımcılar ───────────────────────────────────────────────────────────────

function fmtNum(v, decimals = 2) {
  if (v == null || isNaN(v)) return null;
  return parseFloat(v).toLocaleString('tr-TR', {
    minimumFractionDigits: decimals,
    maximumFractionDigits: decimals,
  });
}

function extractValues(points) {
  return points.map(p => parseFloat(p.indicatorValue)).filter(v => !isNaN(v));
}

function movingAvg(values, n) {
  if (values.length < n) return null;
  const slice = values.slice(-n);
  return slice.reduce((a, b) => a + b, 0) / n;
}

function pctChange(current, base) {
  if (!base || base === 0) return null;
  return ((current - base) / base) * 100;
}

function ytdBaseValue(points) {
  const ytdStart = `${new Date().getFullYear()}-01-01`;
  const found = points.find(p => p.date >= ytdStart);
  if (!found) return null;
  const v = parseFloat(found.indicatorValue);
  return isNaN(v) ? null : v;
}

function monthAgoValue(points) {
  if (points.length === 0) return null;
  const target = new Date();
  target.setDate(target.getDate() - 30);
  const targetStr = target.toISOString().slice(0, 10);
  let best = null;
  for (const p of points) {
    if (p.date <= targetStr) best = p;
    else break;
  }
  if (!best) return null;
  const v = parseFloat(best.indicatorValue);
  return isNaN(v) ? null : v;
}

// ── Satır bileşeni ────────────────────────────────────────────────────────────

function Row({ label, value, badge, badgeColor, loading }) {
  return (
    <div className="flex items-center justify-between py-2.5 border-b border-gray-50 last:border-0">
      <span className="text-xs text-gray-500 shrink-0 mr-2">{label}</span>
      <div className="flex items-center gap-1.5 min-w-0">
        {loading ? (
          <span className="text-xs text-gray-300 animate-pulse">...</span>
        ) : (
          <>
            {value != null && (
              <span className="text-xs font-semibold text-gray-900 text-right">{value}</span>
            )}
            {badge && (
              <span className={`px-2 py-0.5 rounded-full text-xs font-bold whitespace-nowrap ${badgeColor}`}>
                {badge}
              </span>
            )}
          </>
        )}
      </div>
    </div>
  );
}

// ── Ana bileşen ───────────────────────────────────────────────────────────────

export default function BondIndicators({ bond, historyPoints = [], historyLoading = false }) {
  const { t } = useTranslation();
  const days           = bond.remainingDays ?? null;
  const dailyChange    = bond.dailyChange    != null ? parseFloat(bond.dailyChange)    : null;
  const dailyChangePct = bond.dailyChangePercent != null ? parseFloat(bond.dailyChangePercent) : null;
  const couponRate     = bond.couponRate != null ? parseFloat(bond.couponRate) : null;

  const indicators = useMemo(() => {
    const values = extractValues(historyPoints);
    const last   = values.length > 0 ? values[values.length - 1] : null;
    const ma7    = movingAvg(values, 7);
    const ma30   = movingAvg(values, 30);

    let trendLabel = null, trendColor = '';
    if (last != null && ma7 != null) {
      if (last > ma7)      { trendLabel = 'Pozitif'; trendColor = 'bg-emerald-100 text-emerald-700'; }
      else if (last < ma7) { trendLabel = 'Negatif'; trendColor = 'bg-rose-100 text-rose-700'; }
      else                 { trendLabel = 'Yatay';   trendColor = 'bg-gray-100 text-gray-600'; }
    }

    const monthBase = monthAgoValue(historyPoints);
    const monthPct  = last != null && monthBase != null ? pctChange(last, monthBase) : null;
    const ytdBase   = ytdBaseValue(historyPoints);
    const ytdPct    = last != null && ytdBase   != null ? pctChange(last, ytdBase)   : null;

    return { ma7, ma30, trendLabel, trendColor, monthPct, ytdPct };
  }, [historyPoints]);

  const { ma7, ma30, trendLabel, trendColor, monthPct, ytdPct } = indicators;

  const maturityLabel =
    days == null ? null :
    days <= 90   ? 'Kısa Vadeli' :
    days <= 365  ? 'Orta Vadeli' : 'Uzun Vadeli';
  const maturityColor =
    days == null ? '' :
    days <= 90   ? 'bg-amber-100 text-amber-700' :
    days <= 365  ? 'bg-blue-100 text-blue-700'   : 'bg-purple-100 text-purple-700';

  const movementLabel =
    dailyChange == null ? null :
    dailyChange > 0     ? 'Pozitif' :
    dailyChange < 0     ? 'Negatif' : 'Yatay';
  const movementColor =
    dailyChange == null ? '' :
    dailyChange > 0     ? 'bg-emerald-100 text-emerald-700' :
    dailyChange < 0     ? 'bg-rose-100 text-rose-700'       : 'bg-gray-100 text-gray-600';

  const pctColor = (n) =>
    n == null ? 'text-gray-900' : n > 0 ? 'text-emerald-600' : n < 0 ? 'text-rose-600' : 'text-gray-600';

  return (
    <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-5">
      <h2 className="text-sm font-bold text-gray-900 mb-3">{t('Göstergeler')}</h2>

      {/* ── Temel ── */}
      <Row
        label={t('Vade Durumu')}
        value={days != null ? `${days} ${t('gün')}` : null}
        badge={maturityLabel ? t(maturityLabel) : null}
        badgeColor={maturityColor}
      />
      <Row
        label={t('Günlük Hareket')}
        value={dailyChange != null ? `${dailyChange > 0 ? '+' : ''}${fmtNum(dailyChange, 4)}` : null}
        badge={movementLabel ? t(movementLabel) : null}
        badgeColor={movementColor}
      />
      <Row
        label={t('Günlük Değişim %')}
        value={dailyChangePct != null
          ? <span className={pctColor(dailyChangePct)}>{fmtPct(dailyChangePct, 2)}</span>
          : null}
      />      <Row
        label={t('Kupon Durumu')}
        value={couponRate != null ? `%${fmtNum(couponRate, 2)}` : null}
        badge={couponRate != null ? t('Kuponlu') : t('Bilgi yok')}
        badgeColor={couponRate != null ? 'bg-indigo-100 text-indigo-700' : 'bg-gray-100 text-gray-400'}
      />

      {/* ── Tarihsel ── */}
      <p className="text-xs text-gray-400 font-medium mt-3 mb-1 pt-2 border-t border-dashed border-gray-100">
        {t('Tarihsel İndikatörler')}
      </p>

      <Row
        label="MA7"
        value={ma7 != null ? fmtNum(ma7, 4) : null}
        badge={ma7 == null && !historyLoading ? t('Yetersiz Veri') : null}
        badgeColor="bg-gray-100 text-gray-400"
        loading={historyLoading}
      />
      <Row
        label="MA30"
        value={ma30 != null ? fmtNum(ma30, 4) : null}
        badge={ma30 == null && !historyLoading ? t('Yetersiz Veri') : null}
        badgeColor="bg-gray-100 text-gray-400"
        loading={historyLoading}
      />
      <Row
        label={t('Trend')}
        badge={!historyLoading ? (trendLabel ? t(trendLabel) : t('Yetersiz Veri')) : null}
        badgeColor={trendColor || 'bg-gray-100 text-gray-400'}
        loading={historyLoading}
      />
      <Row
        label={t('1 Aylık Değişim')}
        value={monthPct != null
          ? <span className={pctColor(monthPct)}>{fmtPct(monthPct, 2)}</span>
          : null}
        badge={monthPct == null && !historyLoading ? t('Yetersiz Veri') : null}
        badgeColor="bg-gray-100 text-gray-400"
        loading={historyLoading}
      />
      <Row
        label={t('Yılbaşından Beri')}
        value={ytdPct != null
          ? <span className={pctColor(ytdPct)}>{fmtPct(ytdPct, 2)}</span>
          : null}
        badge={ytdPct == null && !historyLoading ? t('Yetersiz Veri') : null}
        badgeColor="bg-gray-100 text-gray-400"
        loading={historyLoading}
      />
      <Row
        label={t('Veri Kaynağı')}
        badge="TCMB EVDS"
        badgeColor="bg-blue-50 text-[#093eaa]"
      />
    </div>
  );
}
