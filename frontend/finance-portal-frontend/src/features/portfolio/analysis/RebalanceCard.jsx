import { useState } from 'react';
import { Scale, ClipboardList, X, ArrowUp, ArrowDown, Minus } from 'lucide-react';
import { getPortfolioRebalance } from '../../../api/portfolioApi';
import { useTranslation } from '../../../context/LanguageContext';

const fmtPct = v => (v == null ? '—' : `%${Number(v).toLocaleString('tr-TR', { maximumFractionDigits: 1 })}`);

const PROFILE_STYLE = {
  AGGRESSIVE: 'bg-rose-50 text-rose-700',
  BALANCED: 'bg-blue-50 text-blue-700',
  CONSERVATIVE: 'bg-emerald-50 text-emerald-700',
};

// ── Risk profili anketi (5 soru, 0-2 puan; toplam → profil) ──────────────────
const QUESTIONS = [
  { q: 'Yatırım vadem genelde…', opts: ['1 yıldan kısa', '1–5 yıl', '5 yıldan uzun'] },
  { q: 'Portföyüm bir ayda %20 düşerse…', opts: ['Satar, zararı durdururum', 'Bekler, izlerim', 'Fırsat görür, eklerim'] },
  { q: 'Önceliğim…', opts: ['Sermayeyi korumak', 'Dengeli büyüme', 'Maksimum getiri'] },
  { q: 'Yatırım deneyimim…', opts: ['Yeni başlıyorum', 'Orta düzey', 'Deneyimli'] },
  { q: 'Fiyat dalgalanmasına tahammülüm…', opts: ['Düşük', 'Orta', 'Yüksek'] },
];

function profileFromScore(score) {
  if (score <= 3) return 'CONSERVATIVE';
  if (score <= 7) return 'BALANCED';
  return 'AGGRESSIVE';
}

function RiskProfileQuiz({ onClose, onResult }) {
  const { t } = useTranslation();
  const [answers, setAnswers] = useState(Array(QUESTIONS.length).fill(null));
  const allAnswered = answers.every(a => a != null);
  const submit = () => {
    const score = answers.reduce((s, a) => s + (a ?? 0), 0);
    onResult(profileFromScore(score));
  };
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4" onClick={onClose}
      role="button" tabIndex={0}
      onKeyDown={e => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); onClose(e); } }}>
      <div className="bg-white rounded-xl shadow-xl max-w-lg w-full max-h-[85vh] overflow-y-auto p-5"
        onClick={e => e.stopPropagation()}
        role="button" tabIndex={0}
        onKeyDown={e => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); e.stopPropagation(); } }}>
        <div className="flex items-center justify-between mb-3">
          <h3 className="font-bold text-gray-900 flex items-center gap-2">
            <ClipboardList className="w-5 h-5 text-[#093eaa]" />{t('Risk Profili Testi')}
          </h3>
          <button onClick={onClose} className="text-gray-400 hover:text-gray-600"><X className="w-5 h-5" /></button>
        </div>
        <div className="space-y-4">
          {QUESTIONS.map((item, qi) => (
            <div key={qi}>
              <p className="text-sm font-semibold text-gray-700 mb-1.5">{qi + 1}. {t(item.q)}</p>
              <div className="grid grid-cols-1 sm:grid-cols-3 gap-1.5">
                {item.opts.map((opt, oi) => (
                  <button key={oi}
                    onClick={() => setAnswers(a => { const n = [...a]; n[qi] = oi; return n; })}
                    className={`text-xs px-2 py-1.5 rounded-lg border text-left transition
                      ${answers[qi] === oi
                        ? 'border-[#093eaa] bg-[#093eaa]/5 text-[#093eaa] font-semibold'
                        : 'border-gray-200 text-gray-600 hover:border-gray-300'}`}>
                    {t(opt)}
                  </button>
                ))}
              </div>
            </div>
          ))}
        </div>
        <button onClick={submit} disabled={!allAnswered}
          className={`mt-5 w-full py-2 rounded-lg text-sm font-semibold transition
            ${allAnswered ? 'bg-[#093eaa] text-white hover:bg-[#0a3494]' : 'bg-gray-100 text-gray-400 cursor-not-allowed'}`}>
          {t('Profilimi belirle')}
        </button>
      </div>
    </div>
  );
}

function ActionChip({ action, delta }) {
  const map = {
    ARTIR: { c: 'bg-emerald-50 text-emerald-700', Icon: ArrowUp },
    AZALT: { c: 'bg-rose-50 text-rose-700', Icon: ArrowDown },
    KORU: { c: 'bg-gray-100 text-gray-500', Icon: Minus },
  };
  const { c, Icon } = map[action] || map.KORU;
  return (
    <span className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[11px] font-semibold ${c}`}>
      <Icon className="w-3 h-3" />{delta > 0 ? '+' : ''}{fmtPct(delta)}
    </span>
  );
}

export default function RebalanceCard({ rebalance, portfolioId }) {
  const { t } = useTranslation();
  const [data, setData] = useState(rebalance);
  const [quizOpen, setQuizOpen] = useState(false);
  const [loading, setLoading] = useState(false);

  if (!data) return null;

  const handleResult = async profile => {
    setQuizOpen(false);
    setLoading(true);
    try {
      const rb = await getPortfolioRebalance(portfolioId, profile);
      if (rb) setData(rb);
    } catch {
      /* sessiz: mevcut veriyi koru */
    } finally {
      setLoading(false);
    }
  };

  const items = data.items || [];
  const maxPct = Math.max(1, ...items.map(i => Math.max(Number(i.currentPercent) || 0, Number(i.targetPercent) || 0)));

  return (
    <div className="bg-white rounded-lg border border-gray-200 shadow-sm p-4">
      <div className="flex items-center justify-between gap-2 mb-3 flex-wrap">
        <div className="flex items-center gap-2 text-gray-800 font-bold text-sm">
          <Scale className="w-4 h-4 text-[#093eaa]" />{t('Yeniden Dengeleme')}
          <span className={`px-2 py-0.5 rounded-full text-[11px] font-bold ${PROFILE_STYLE[data.basedOnProfile] || PROFILE_STYLE.BALANCED}`}>
            {t(data.profileLabel)} {t('hedef')}
          </span>
        </div>
        <button onClick={() => setQuizOpen(true)}
          className="inline-flex items-center gap-1.5 text-xs font-semibold text-[#093eaa] border border-[#093eaa]/30 rounded-lg px-2.5 py-1 hover:bg-[#093eaa]/5">
          <ClipboardList className="w-3.5 h-3.5" />{loading ? t('Hesaplanıyor…') : t('Risk profilimi ölç')}
        </button>
      </div>

      <p className="text-[11px] text-gray-400 mb-3">
        {t('Toplam sapma')} <span className="font-semibold text-gray-600">{fmtPct(data.driftPercent)}</span> ·
        {' '}{t('mevcut dağılımın seçili profile uzaklığı (0 = birebir).')}
      </p>

      <div className="space-y-2">
        {items.map(it => (
          <div key={it.assetType} className="flex items-center gap-2 text-xs">
            <span className="w-16 shrink-0 text-gray-600 truncate" title={it.label}>{it.label}</span>
            <div className="flex-1 min-w-0 space-y-0.5">
              <Bar pct={it.currentPercent} max={maxPct} color="#94a3b8" label={t('mevcut')} />
              <Bar pct={it.targetPercent} max={maxPct} color="#093eaa" label={t('hedef')} />
            </div>
            <span className="shrink-0"><ActionChip action={it.action} delta={Number(it.deltaPercent)} /></span>
          </div>
        ))}
      </div>

      <p className="text-[11px] text-gray-400 mt-3 leading-snug">{data.note}</p>

      {quizOpen && <RiskProfileQuiz onClose={() => setQuizOpen(false)} onResult={handleResult} />}
    </div>
  );
}

function Bar({ pct, max, color, label }) {
  const v = Math.max(0, Number(pct) || 0);
  return (
    <div className="flex items-center gap-1.5">
      <span className="w-9 shrink-0 text-[9px] text-gray-400">{label}</span>
      <div className="flex-1 min-w-0 h-2 bg-gray-100 rounded-full overflow-hidden">
        <div className="h-full rounded-full" style={{ width: `${(v / max) * 100}%`, backgroundColor: color }} />
      </div>
      <span className="w-9 shrink-0 text-right text-[9px] text-gray-500 tabular-nums">{fmtPct(pct)}</span>
    </div>
  );
}
