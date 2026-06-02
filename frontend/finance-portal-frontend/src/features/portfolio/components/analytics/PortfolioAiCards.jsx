import { useEffect, useState } from 'react';
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
  return <div className="flex items-center justify-center py-8 text-xs text-gray-400">…</div>;
}
function Empty({ msg }) {
  return <div className="flex items-center justify-center py-8 text-xs text-gray-400 text-center px-2">{msg}</div>;
}

function ScoreRing({ score, color }) {
  const r = 34, c = 2 * Math.PI * r;
  const off = c * (1 - Math.max(0, Math.min(100, score)) / 100);
  return (
    <div className="relative" style={{ width: 84, height: 84 }}>
      <svg width="84" height="84" className="-rotate-90">
        <circle cx="42" cy="42" r={r} stroke="#eef1f5" strokeWidth="9" fill="none" />
        <circle cx="42" cy="42" r={r} stroke={color} strokeWidth="9" fill="none"
          strokeDasharray={c} strokeDashoffset={off} strokeLinecap="round" />
      </svg>
      <div className="absolute inset-0 flex flex-col items-center justify-center">
        <span className="text-xl font-extrabold" style={{ color }}>{score}</span>
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
        <div className="flex flex-col items-center">
          <ScoreRing score={d.riskScore} color={riskColor(d.riskScore)} />
          <div className="mt-1 text-sm font-bold text-gray-800">{t(d.riskLabel)}</div>
          <FactorBars factors={d.riskFactors} />
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
        <div className="flex flex-col items-center">
          <ScoreRing score={d.healthScore} color={healthColor(d.healthScore)} />
          <div className="mt-1 text-sm font-bold text-gray-800">{t(d.healthLabel)}</div>
          <FactorBars factors={d.healthFactors} />
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
        <div>
          <span className={`inline-block px-2.5 py-1 rounded-full text-sm font-bold mb-3 ${PROFILE_STYLE[cls.profile] || PROFILE_STYLE.BALANCED}`}>
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

export function PortfolioMonteCarloCard({ portfolioId, valuesHidden }) {
  const { t } = useTranslation();
  const s = useAiAnalysis(portfolioId);
  const mc = s.data?.monteCarlo;
  const money = v => (valuesHidden ? '••••' : fmtMoney(v));
  return (
    <PortfolioChartCard title={t('Monte Carlo Projeksiyon')} subtitle={t('1 yıllık olası değer aralığı (kesin tahmin değil)')}>
      {s.loading ? <Loading /> : !mc?.available ? <Empty msg={t('Yeterli geçmiş yok')} /> : (
        <div className="space-y-2.5 text-sm">
          <Row label={t('Medyan')} value={<span>{money(mc.medianEndValue)} <span className={`text-xs ${signClass(mc.expectedReturnPercent)}`}>({fmtPct(mc.expectedReturnPercent)})</span></span>} strong />
          <Row label={t('İyimser (%95)')} value={<span className="text-emerald-600">{money(mc.p95EndValue)}</span>} />
          <Row label={t('Kötümser (%5)')} value={<span className="text-rose-600">{money(mc.p5EndValue)}</span>} />
          <Row label={t('Kayıp olasılığı')} value={<span className="font-semibold text-rose-500">{fmtPct(mc.probLossPercent)}</span>} />
          <div className="h-2 bg-gradient-to-r from-rose-200 via-amber-200 to-emerald-200 rounded-full mt-1" />
        </div>
      )}
    </PortfolioChartCard>
  );
}

function Row({ label, value, strong }) {
  return (
    <div className="flex justify-between items-center">
      <span className="text-gray-500 text-xs">{label}</span>
      <span className={strong ? 'font-bold text-gray-900' : 'font-semibold text-gray-700'}>{value}</span>
    </div>
  );
}
