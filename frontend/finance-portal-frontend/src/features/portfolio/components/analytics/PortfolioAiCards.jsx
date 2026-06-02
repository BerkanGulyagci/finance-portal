import { useEffect, useState } from 'react';
import {
  ResponsiveContainer, ComposedChart, Area, Line, XAxis, YAxis, CartesianGrid, Tooltip,
} from 'recharts';
import PortfolioChartCard from './PortfolioChartCard';
import { getPortfolioAiAnalysisShared } from '../../../../api/portfolioApi';
import { useTranslation } from '../../../../context/LanguageContext';

/**
 * AI Portföy Analizi kartlarının GridBoard widget sürümleri (Risk/Sağlık skoru, Monte Carlo, Kimlik).
 * Portföy "Grafikler" sekmesinde ve Dashboard'da diğer grafikler gibi taşınır/boyutlandırılır/eklenir.
 * Veri: /ai-analysis (deterministik metrikler) — paylaşımlı cache ile aynı portföyün 4 kartı tek istek atar.
 */

const fmtPct = v => (v == null ? '—' : `%${Number(v).toLocaleString('tr-TR', { maximumFractionDigits: 1 })}`);
const fmtMoney = v => (v == null ? '—' : Number(v).toLocaleString('tr-TR', { maximumFractionDigits: 0 }) + ' ₺');
const signClass = v => (v == null ? 'text-gray-500' : Number(v) > 0 ? 'text-emerald-600' : Number(v) < 0 ? 'text-rose-600' : 'text-gray-500');
const riskColor = s => (s < 33 ? '#10b981' : s < 66 ? '#f59e0b' : '#ef4444');
const healthColor = s => (s < 33 ? '#ef4444' : s < 66 ? '#f59e0b' : '#10b981');
const PROFILE_STYLE = {
  AGGRESSIVE: 'bg-rose-50 text-rose-700',
  BALANCED: 'bg-blue-50 text-blue-700',
  CONSERVATIVE: 'bg-emerald-50 text-emerald-700',
};

function useAiAnalysis(portfolioId) {
  const [state, setState] = useState({ loading: true, data: null, error: false });
  useEffect(() => {
    if (!portfolioId) { setState({ loading: false, data: null, error: true }); return undefined; }
    let alive = true;
    setState({ loading: true, data: null, error: false });
    getPortfolioAiAnalysisShared(portfolioId)
      .then(d => { if (alive) setState({ loading: false, data: d, error: false }); })
      .catch(() => { if (alive) setState({ loading: false, data: null, error: true }); });
    return () => { alive = false; };
  }, [portfolioId]);
  return state;
}

function Loading() {
  return <div className="h-full flex items-center justify-center text-xs text-gray-400">…</div>;
}
function Empty({ msg }) {
  return <div className="h-full flex items-center justify-center text-xs text-gray-400 text-center px-2">{msg}</div>;
}

function ScoreRing({ score, color }) {
  const r = 34, c = 2 * Math.PI * r;
  const off = c * (1 - Math.max(0, Math.min(100, score)) / 100);
  // Kart küçülünce küçülsün: genişliğe göre ölçeklenir (viewBox), max 88px.
  return (
    <div className="relative w-full max-w-[88px] aspect-square mx-auto shrink-0">
      <svg viewBox="0 0 84 84" className="w-full h-full -rotate-90">
        <circle cx="42" cy="42" r={r} stroke="#eef1f5" strokeWidth="9" fill="none" />
        <circle cx="42" cy="42" r={r} stroke={color} strokeWidth="9" fill="none"
          strokeDasharray={c} strokeDashoffset={off} strokeLinecap="round" />
      </svg>
      <div className="absolute inset-0 flex flex-col items-center justify-center">
        <span className="text-lg font-extrabold" style={{ color }}>{score}</span>
        <span className="text-[9px] text-gray-400 font-semibold">/ 100</span>
      </div>
    </div>
  );
}

function FactorBars({ factors }) {
  if (!factors?.length) return null;
  const max = Math.max(1, ...factors.map(f => Math.abs(f.contribution)));
  return (
    <div className="mt-2 space-y-1 w-full">
      {factors.map(f => (
        <div key={f.label} className="text-[11px]">
          <div className="flex justify-between items-baseline gap-3 text-gray-500">
            <span className="truncate">{f.label}</span>
            <span className="font-semibold text-gray-700 shrink-0 tabular-nums">{f.contribution}</span>
          </div>
          <div className="h-1.5 bg-gray-100 rounded-full overflow-hidden">
            <div className="h-full bg-[#093eaa]/70 rounded-full" style={{ width: `${(Math.abs(f.contribution) / max) * 100}%` }} />
          </div>
        </div>
      ))}
    </div>
  );
}

function WeightBar({ label, pct, color }) {
  const v = Math.max(0, Math.min(100, Number(pct) || 0));
  return (
    <div className="text-[11px]">
      <div className="flex justify-between text-gray-500 mb-0.5"><span>{label}</span><span className="font-semibold text-gray-700">{fmtPct(pct)}</span></div>
      <div className="h-2 bg-gray-100 rounded-full overflow-hidden">
        <div className="h-full rounded-full" style={{ width: `${v}%`, backgroundColor: color }} />
      </div>
    </div>
  );
}

export function PortfolioRiskScoreCard({ portfolioId }) {
  const { t } = useTranslation();
  const s = useAiAnalysis(portfolioId);
  const d = s.data;
  return (
    <PortfolioChartCard title={t('Risk Skoru')} subtitle={t('0–100 · yüksek = daha riskli')}>
      {s.loading ? <Loading /> : !d ? <Empty msg={t('Analiz alınamadı.')} /> : (
        <div className="h-full flex flex-col items-center">
          <ScoreRing score={d.riskScore} color={riskColor(d.riskScore)} />
          <div className="mt-1 text-sm font-bold text-gray-800 shrink-0">{t(d.riskLabel)}</div>
          <div className="flex-1 min-h-0 w-full overflow-hidden mt-1">
            <FactorBars factors={d.riskFactors} />
          </div>
        </div>
      )}
    </PortfolioChartCard>
  );
}

export function PortfolioHealthScoreCard({ portfolioId }) {
  const { t } = useTranslation();
  const s = useAiAnalysis(portfolioId);
  const d = s.data;
  return (
    <PortfolioChartCard title={t('Sağlık Skoru')} subtitle={t('0–100 · yüksek = daha sağlıklı')}>
      {s.loading ? <Loading /> : !d ? <Empty msg={t('Analiz alınamadı.')} /> : (
        <div className="h-full flex flex-col items-center">
          <ScoreRing score={d.healthScore} color={healthColor(d.healthScore)} />
          <div className="mt-1 text-sm font-bold text-gray-800 shrink-0">{t(d.healthLabel)}</div>
          <div className="flex-1 min-h-0 w-full overflow-hidden mt-1">
            <FactorBars factors={d.healthFactors} />
          </div>
        </div>
      )}
    </PortfolioChartCard>
  );
}

export function PortfolioIdentityCard({ portfolioId }) {
  const { t } = useTranslation();
  const s = useAiAnalysis(portfolioId);
  const cls = s.data?.classification;
  return (
    <PortfolioChartCard title={t('Portföy Kimliği')} subtitle={t('Varlık-tipi ağırlığına göre profil')}>
      {s.loading ? <Loading /> : !cls ? <Empty msg={t('Sınıflandırma için veri yok.')} /> : (
        <div className="h-full flex flex-col justify-center gap-3 overflow-hidden">
          <span className={`self-start px-2.5 py-1 rounded-full text-sm font-bold ${PROFILE_STYLE[cls.profile] || PROFILE_STYLE.BALANCED}`}>
            {t(cls.label)}
          </span>
          <div className="space-y-2">
            <WeightBar label={t('Büyüme-odaklı')} pct={cls.growthWeightPercent} color="#ef4444" />
            <WeightBar label={t('Korumacı')} pct={cls.defensiveWeightPercent} color="#10b981" />
          </div>
        </div>
      )}
    </PortfolioChartCard>
  );
}

function McTooltip({ active, payload, valuesHidden }) {
  if (!active || !payload?.length) return null;
  const d = payload[0]?.payload;
  if (!d) return null;
  const money = v => (valuesHidden ? '••••' : fmtMoney(v));
  return (
    <div className="bg-white border border-gray-200 rounded shadow px-2 py-1 text-[11px]">
      <div className="font-semibold text-gray-700">{d.month}. ay</div>
      <div className="text-[#093eaa] font-semibold">{money(d.p50)}</div>
      <div className="text-gray-500">%5–%95: {money(d.p5)} – {money(d.p95)}</div>
    </div>
  );
}

export function PortfolioMonteCarloCard({ portfolioId, valuesHidden }) {
  const { t } = useTranslation();
  const s = useAiAnalysis(portfolioId);
  const mc = s.data?.monteCarlo;
  const mcData = (mc?.bands || []).map(b => ({
    month: b.month,
    base: Number(b.p5),
    range: Number(b.p95) - Number(b.p5),
    p50: Number(b.p50),
    p5: Number(b.p5),
    p95: Number(b.p95),
  }));
  const money = v => (valuesHidden ? '••••' : fmtMoney(v));
  return (
    <PortfolioChartCard title={t('Monte Carlo Projeksiyon')} subtitle={t('1 yıllık olası değer aralığı (kesin tahmin değil)')}>
      {s.loading ? <Loading /> : !mc?.available ? <Empty msg={t('Yeterli geçmiş yok')} /> : (
        <div className="h-full flex flex-col">
          <div className="flex justify-between items-baseline text-xs mb-1.5 gap-2 flex-wrap shrink-0">
            <span className="text-gray-500">
              {t('Medyan')}: <span className="font-bold text-gray-800">{money(mc.medianEndValue)}</span>{' '}
              <span className={signClass(mc.expectedReturnPercent)}>({fmtPct(mc.expectedReturnPercent)})</span>
            </span>
            <span className="text-rose-500">{t('Kayıp olasılığı')} {fmtPct(mc.probLossPercent)}</span>
          </div>
          <div className="flex-1 min-h-0">
            <ResponsiveContainer width="100%" height="100%">
              <ComposedChart data={mcData} margin={{ top: 4, right: 6, left: 0, bottom: 0 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="#f1f5f9" />
                <XAxis dataKey="month" tick={{ fontSize: 9 }} tickFormatter={v => `${v}a`} />
                <YAxis tick={{ fontSize: 9 }} tickFormatter={v => (v / 1000).toFixed(0) + 'b'} width={32} />
                <Tooltip content={<McTooltip valuesHidden={valuesHidden} />} />
                <Area dataKey="base" stackId="band" stroke="none" fill="transparent" isAnimationActive={false} />
                <Area dataKey="range" stackId="band" stroke="none" fill="#093eaa" fillOpacity={0.12} isAnimationActive={false} />
                <Line dataKey="p50" stroke="#093eaa" strokeWidth={2} dot={false} isAnimationActive={false} />
              </ComposedChart>
            </ResponsiveContainer>
          </div>
        </div>
      )}
    </PortfolioChartCard>
  );
}
