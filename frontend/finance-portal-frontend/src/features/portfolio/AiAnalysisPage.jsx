import { useEffect, useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import {
  ArrowLeft, Sparkles, ShieldAlert, HeartPulse, Scale, AlertTriangle, Info, History,
  Target, Activity, TrendingUp, TrendingDown, Minus,
} from 'lucide-react';
import {
  ResponsiveContainer, Tooltip, ComposedChart, Area, Line, XAxis, YAxis, CartesianGrid,
} from 'recharts';
import { getPortfolioAiAnalysis } from '../../api/portfolioApi';
import RebalanceCard from './analysis/RebalanceCard';

const fmtMoney = (v) => (v == null ? '—' : Number(v).toLocaleString('tr-TR', { maximumFractionDigits: 0 }) + ' ₺');
const fmtPct = (v) => (v == null ? '—' : `%${Number(v).toLocaleString('tr-TR', { maximumFractionDigits: 2 })}`);
const fmtNum = (v) => (v == null ? '—' : Number(v).toLocaleString('tr-TR', { maximumFractionDigits: 2 }));
const signClass = (v) => (v == null ? 'text-gray-500' : Number(v) > 0 ? 'text-emerald-600' : Number(v) < 0 ? 'text-rose-600' : 'text-gray-500');

// Risk: düşük=yeşil; Sağlık: düşük=kırmızı.
const riskColor = (s) => (s < 33 ? '#10b981' : s < 66 ? '#f59e0b' : '#ef4444');
const healthColor = (s) => (s < 33 ? '#ef4444' : s < 66 ? '#f59e0b' : '#10b981');

// Portföy profili rozet renkleri.
const PROFILE_STYLE = {
  AGGRESSIVE: { bg: 'bg-rose-50', text: 'text-rose-700', ring: 'ring-rose-200' },
  BALANCED: { bg: 'bg-blue-50', text: 'text-blue-700', ring: 'ring-blue-200' },
  CONSERVATIVE: { bg: 'bg-emerald-50', text: 'text-emerald-700', ring: 'ring-emerald-200' },
};

/** Basit SVG skor halkası (0-100). */
function ScoreGauge({ score, color, label, sub }) {
  const r = 52, c = 2 * Math.PI * r;
  const off = c * (1 - Math.max(0, Math.min(100, score)) / 100);
  return (
    <div className="flex flex-col items-center">
      <div className="relative" style={{ width: 128, height: 128 }}>
        <svg width="128" height="128" className="-rotate-90">
          <circle cx="64" cy="64" r={r} stroke="#eef1f5" strokeWidth="12" fill="none" />
          <circle cx="64" cy="64" r={r} stroke={color} strokeWidth="12" fill="none"
            strokeDasharray={c} strokeDashoffset={off} strokeLinecap="round" />
        </svg>
        <div className="absolute inset-0 flex flex-col items-center justify-center">
          <span className="text-3xl font-extrabold" style={{ color }}>{score}</span>
          <span className="text-[11px] text-gray-400 font-semibold">/ 100</span>
        </div>
      </div>
      <div className="mt-1 text-sm font-bold text-gray-800">{label}</div>
      {sub && <div className="text-xs font-semibold" style={{ color }}>{sub}</div>}
    </div>
  );
}

function FactorBars({ factors }) {
  if (!factors?.length) return null;
  const max = Math.max(1, ...factors.map((f) => Math.abs(f.contribution)));
  return (
    <div className="mt-3 space-y-1.5 w-full">
      {factors.map((f) => (
        <div key={f.label} className="text-xs">
          <div className="flex justify-between items-baseline gap-3 text-gray-500">
            <span title={f.detail} className="truncate">{f.label}</span>
            <span className="font-semibold text-gray-700 shrink-0 tabular-nums">{f.contribution}</span>
          </div>
          <div className="h-1.5 bg-gray-100 rounded-full overflow-hidden">
            <div className="h-full bg-[#093eaa]/70 rounded-full"
              style={{ width: `${(Math.abs(f.contribution) / max) * 100}%` }} />
          </div>
        </div>
      ))}
    </div>
  );
}

function Card({ title, icon, children, className = '' }) {
  return (
    <div className={`bg-white rounded-lg border border-gray-200 shadow-sm p-4 ${className}`}>
      {title && (
        <div className="flex items-center gap-2 mb-3 text-gray-800 font-bold text-sm">
          {icon}{title}
        </div>
      )}
      {children}
    </div>
  );
}

/** Trend rozeti (UP/DOWN/NEUTRAL). */
function TrendBadge({ trend, label }) {
  const map = {
    UP: { c: 'bg-emerald-50 text-emerald-700', Icon: TrendingUp },
    DOWN: { c: 'bg-rose-50 text-rose-700', Icon: TrendingDown },
    NEUTRAL: { c: 'bg-gray-100 text-gray-600', Icon: Minus },
  };
  const { c, Icon } = map[trend] || map.NEUTRAL;
  return (
    <span className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[11px] font-semibold ${c}`}>
      <Icon className="w-3 h-3" />{label}
    </span>
  );
}

/** 52-hafta bandındaki konum çubuğu (0=dip, 100=tepe). */
function RangeBar({ pos }) {
  if (pos == null) return <div className="text-[11px] text-gray-400">52h verisi yok</div>;
  return (
    <div>
      <div className="flex justify-between text-[10px] text-gray-400 mb-0.5">
        <span>52h dip</span><span>{fmtPct(pos)}</span><span>tepe</span>
      </div>
      <div className="relative h-1.5 bg-gradient-to-r from-emerald-200 via-amber-200 to-rose-200 rounded-full">
        <div className="absolute top-1/2 -translate-y-1/2 -translate-x-1/2 w-2.5 h-2.5 rounded-full bg-[#093eaa] border-2 border-white shadow"
          style={{ left: `${Math.max(0, Math.min(100, Number(pos)))}%` }} />
      </div>
    </div>
  );
}

function AssetSignalCard({ s }) {
  return (
    <div className="border border-gray-100 rounded-lg p-3 bg-gray-50/50">
      <div className="flex items-start justify-between gap-2 mb-1.5">
        <div className="min-w-0">
          <div className="font-semibold text-gray-800 text-sm truncate">{s.name}</div>
          <div className="text-[11px] text-gray-400">{s.symbol} · ağırlık {fmtPct(s.weightPercent)}</div>
        </div>
        <TrendBadge trend={s.trend} label={s.trendLabel} />
      </div>
      <div className="mb-2"><RangeBar pos={s.range52wPercent} /></div>
      <div className="grid grid-cols-3 gap-2 text-center mb-1.5">
        <Mini label="Getiri" value={fmtPct(s.profitLossPercent)} cls={signClass(s.profitLossPercent)} />
        <Mini label="1 Ay" value={fmtPct(s.momentum1mPercent)} cls={signClass(s.momentum1mPercent)} />
        <Mini label="3 Ay" value={fmtPct(s.momentum3mPercent)} cls={signClass(s.momentum3mPercent)} />
      </div>
      {s.maState && <div className="text-[11px] text-gray-500">{s.maState}</div>}
    </div>
  );
}

function Mini({ label, value, cls }) {
  return (
    <div>
      <div className={`text-sm font-bold ${cls}`}>{value}</div>
      <div className="text-[10px] text-gray-400">{label}</div>
    </div>
  );
}

function MonteCarloTooltip({ active, payload }) {
  if (!active || !payload?.length) return null;
  const d = payload[0]?.payload;
  if (!d) return null;
  return (
    <div className="bg-white border border-gray-200 rounded shadow px-2.5 py-1.5 text-xs">
      <div className="font-semibold text-gray-700 mb-0.5">{d.month}. ay</div>
      <div className="text-[#093eaa] font-semibold">Medyan: {fmtMoney(d.p50)}</div>
      <div className="text-gray-500">%5–%95: {fmtMoney(d.p5)} – {fmtMoney(d.p95)}</div>
    </div>
  );
}

export default function AiAnalysisPage() {
  const { id } = useParams();
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [progress, setProgress] = useState(8);

  useEffect(() => {
    let alive = true;
    setLoading(true); setError(''); setProgress(8);
    getPortfolioAiAnalysis(id)
      .then((d) => { if (alive) setData(d); })
      .catch((e) => { if (alive) setError(e?.response?.data?.message || 'Analiz alınamadı.'); })
      .finally(() => { if (alive) { setProgress(100); setLoading(false); } });
    return () => { alive = false; };
  }, [id]);

  // Analiz sürerken ilerleme çubuğunu ~%92'ye kadar yumuşakça doldur (LLM süresi değişken).
  useEffect(() => {
    if (!loading) return undefined;
    const t = setInterval(() => {
      setProgress((p) => (p < 92 ? p + Math.max(0.6, (92 - p) * 0.07) : p));
    }, 350);
    return () => clearInterval(t);
  }, [loading]);

  if (loading) {
    return (
      <div className="min-h-[60vh] flex flex-col items-center justify-center text-gray-500 px-4">
        <Sparkles className="w-8 h-8 text-[#093eaa] animate-pulse mb-3" />
        <p className="text-sm font-semibold text-gray-700">Portföyün AI ile analiz ediliyor…</p>
        <p className="text-xs text-gray-400 mt-1 mb-4">Metrikler hesaplanıyor, AI yorumu hazırlanıyor.</p>
        <div className="w-64 max-w-full h-2 bg-gray-100 rounded-full overflow-hidden">
          <div className="h-full bg-gradient-to-r from-[#093eaa] to-[#2563eb] rounded-full transition-all duration-500 ease-out"
            style={{ width: `${Math.round(progress)}%` }} />
        </div>
        <p className="text-[11px] text-gray-400 mt-2 tabular-nums">%{Math.round(progress)}</p>
      </div>
    );
  }
  if (error) {
    return (
      <div className="max-w-2xl mx-auto mt-10 bg-white rounded-lg border border-gray-200 p-6 text-center">
        <AlertTriangle className="w-7 h-7 text-rose-500 mx-auto mb-2" />
        <p className="text-sm text-gray-600">{error}</p>
        <Link to={`/portfolio/${id}`} className="inline-block mt-4 text-sm font-semibold text-[#093eaa]">← Portföye dön</Link>
      </div>
    );
  }
  if (!data) return null;

  const m = data.riskMetrics || {};
  const conc = data.concentration || {};
  const cls = data.classification;
  const mc = data.monteCarlo;
  const signals = data.assetSignals || [];
  const profileStyle = (cls && PROFILE_STYLE[cls.profile]) || PROFILE_STYLE.BALANCED;

  const mcData = (mc?.bands || []).map((b) => ({
    month: b.month,
    base: Number(b.p5),
    range: Number(b.p95) - Number(b.p5),
    p50: Number(b.p50),
    p5: Number(b.p5),
    p95: Number(b.p95),
  }));

  return (
    <div className="max-w-6xl mx-auto px-3 sm:px-4 py-4 space-y-4">
      {/* Header */}
      <div className="bg-white rounded-lg border border-gray-200 shadow-sm p-4 flex items-center gap-3">
        <Link to={`/portfolio/${id}`} className="text-gray-400 hover:text-[#093eaa] mt-0.5 shrink-0">
          <ArrowLeft className="w-5 h-5" />
        </Link>
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2 flex-wrap">
            <Sparkles className="w-5 h-5 text-[#093eaa]" />
            <h1 className="text-lg sm:text-xl font-bold text-gray-900 truncate">AI Portföy Analizi</h1>
            {cls && (
              <span className={`px-2 py-0.5 rounded-full text-xs font-bold ring-1 ${profileStyle.bg} ${profileStyle.text} ${profileStyle.ring}`}>
                {cls.label}
              </span>
            )}
          </div>
          <p className="text-sm text-gray-400 truncate">{data.name} · {data.holdingsCount} pozisyon</p>
        </div>
        <div className="text-right shrink-0">
          <div className="text-lg font-bold text-gray-900">{fmtMoney(data.totalValueTry)}</div>
          <div className={`text-sm font-semibold ${signClass(data.totalProfitLossPercent)}`}>
            {fmtPct(data.totalProfitLossPercent)} <span className="text-gray-400 font-normal">nominal</span>
          </div>
        </div>
      </div>

      {/* Skorlar + portföy kimliği */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        <Card title="Risk Skoru" icon={<ShieldAlert className="w-4 h-4 text-amber-500" />}>
          <div className="flex flex-col items-center">
            <ScoreGauge score={data.riskScore} color={riskColor(data.riskScore)} label={data.riskLabel} sub="risk seviyesi" />
            <p className="text-[11px] text-gray-500 mt-2 text-center leading-snug">
              <span className="font-semibold">0–100 · yüksek = daha riskli.</span> Volatilite, yoğunlaşma,
              varlık tipi ve maksimum düşüşten hesaplanır. Aşağıdaki çubuklar her etkenin skora katkısıdır.
            </p>
            <FactorBars factors={data.riskFactors} />
          </div>
        </Card>
        <Card title="Sağlık Skoru" icon={<HeartPulse className="w-4 h-4 text-emerald-500" />}>
          <div className="flex flex-col items-center">
            <ScoreGauge score={data.healthScore} color={healthColor(data.healthScore)} label={data.healthLabel} sub="portföy sağlığı" />
            <p className="text-[11px] text-gray-500 mt-2 text-center leading-snug">
              <span className="font-semibold">0–100 · yüksek = daha sağlıklı.</span> Çeşitlendirme, reel getiri,
              risk-ayarlı getiri (Sharpe) ve düşüş kontrolünden hesaplanır. Çubuklar her etkenin katkısını gösterir.
            </p>
            <FactorBars factors={data.healthFactors} />
          </div>
        </Card>
        <Card title="Portföy Kimliği" icon={<Target className="w-4 h-4 text-[#093eaa]" />}>
          {cls ? (
            <div>
              <div className={`inline-block px-2.5 py-1 rounded-full text-sm font-bold mb-2 ${profileStyle.bg} ${profileStyle.text}`}>
                {cls.label}
              </div>
              <p className="text-sm text-gray-600 leading-snug mb-3">{cls.detail}</p>
              <div className="space-y-2">
                <WeightBar label="Büyüme-odaklı" pct={cls.growthWeightPercent} color="#ef4444" />
                <WeightBar label="Korumacı" pct={cls.defensiveWeightPercent} color="#10b981" />
              </div>
              <p className="text-[11px] text-gray-400 mt-2">
                Hisse/kripto/vadeli/emtia = büyüme; tahvil/altın/döviz = korumacı; fon = karma.
              </p>
            </div>
          ) : <p className="text-sm text-gray-500">Sınıflandırma için veri yok.</p>}
        </Card>
      </div>

      {/* Risk metrikleri + yoğunlaşma */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        <Card title="Risk-Ayarlı Metrikler" icon={<Scale className="w-4 h-4 text-[#093eaa]" />} className="md:col-span-2">
          {m.available ? (
            <div className="grid grid-cols-2 sm:grid-cols-3 gap-3">
              <Metric label="Yıllık Getiri" value={fmtPct(m.annualReturnPercent)} hint="Yıllıklandırılmış" />
              <Metric label="Yıllık Volatilite" value={fmtPct(m.annualVolatilityPercent)} hint="Dalgalanma" />
              <Metric label="Sharpe" value={fmtNum(m.sharpe)} hint="Birim risk başına getiri" />
              <Metric label="Sortino" value={fmtNum(m.sortino)} hint="Aşağı-yön risk-ayarlı" />
              <Metric label="Max Düşüş" value={fmtPct(m.maxDrawdownPercent)} hint="Tepe→dip kayıp" />
              <Metric label="Beta (BIST100)" value={fmtNum(m.beta)} hint="Piyasaya duyarlılık" />
            </div>
          ) : (
            <p className="text-sm text-gray-500">Risk metrikleri için geçmiş çok kısa ({m.note || 'yetersiz veri'}).</p>
          )}
        </Card>
        <Card title="Yoğunlaşma Riski" icon={<AlertTriangle className="w-4 h-4 text-amber-500" />}>
          <div className="text-center mb-2">
            <div className="text-2xl font-extrabold text-gray-900">{fmtPct(conc.topHoldingPercent)}</div>
            <div className="text-xs text-gray-500">en büyük pozisyon{conc.topHoldingLabel ? `: ${conc.topHoldingLabel}` : ''}</div>
          </div>
          <dl className="space-y-1.5 text-sm">
            <Row label="İlk 3 pozisyon" value={fmtPct(conc.top3Percent)} cls="text-gray-700" />
            <Row label="Herfindahl" value={fmtNum(conc.herfindahl)} cls="text-gray-700" />
          </dl>
          <div className="mt-2 text-xs font-semibold px-2 py-1 rounded text-center bg-amber-50 text-amber-700">{conc.label}</div>
        </Card>
      </div>

      {/* Monte Carlo projeksiyon (yelpaze) */}
      {mc?.available && (
        <Card title="Monte Carlo Projeksiyon — Olası değer aralığı" icon={<Activity className="w-4 h-4 text-[#093eaa]" />}>
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 mb-3">
            <Metric label={`${mc.horizonMonths} ay medyan`} value={fmtMoney(mc.medianEndValue)} hint={`getiri ${fmtPct(mc.expectedReturnPercent)}`} />
            <Metric label="İyimser (%95)" value={fmtMoney(mc.p95EndValue)} hint="üst senaryo" />
            <Metric label="Kötümser (%5)" value={fmtMoney(mc.p5EndValue)} hint="alt senaryo" />
            <Metric label="Kayıp olasılığı" value={fmtPct(mc.probLossPercent)} hint="nominal" />
          </div>
          <ResponsiveContainer width="100%" height={260}>
            <ComposedChart data={mcData} margin={{ top: 5, right: 10, left: 0, bottom: 0 }}>
              <CartesianGrid strokeDasharray="3 3" stroke="#f1f5f9" />
              <XAxis dataKey="month" tick={{ fontSize: 11 }} tickFormatter={(v) => `${v}a`} />
              <YAxis tick={{ fontSize: 11 }} tickFormatter={(v) => (v / 1000).toFixed(0) + 'b'} width={44} />
              <Tooltip content={<MonteCarloTooltip />} />
              <Area dataKey="base" stackId="band" stroke="none" fill="transparent" isAnimationActive={false} />
              <Area dataKey="range" stackId="band" stroke="none" fill="#093eaa" fillOpacity={0.12}
                name="%5–%95 aralığı" isAnimationActive={false} />
              <Line dataKey="p50" stroke="#093eaa" strokeWidth={2} dot={false} name="Medyan" isAnimationActive={false} />
            </ComposedChart>
          </ResponsiveContainer>
          <p className="text-[11px] text-gray-400 mt-1 leading-snug">{mc.note}</p>
        </Card>
      )}

      {/* Tarihsel stres testleri */}
      {data.stressTests?.some((s) => s.available) && (
        <Card title="Tarihsel Stres Testleri — Mevcut portföyün kriz dayanıklılığı" icon={<History className="w-4 h-4 text-[#093eaa]" />}>
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
            {data.stressTests.filter((s) => s.available).map((s) => (
              <div key={s.key} className="bg-gray-50 rounded-lg p-4 text-center">
                <div className="text-xs font-semibold text-gray-600">{s.label}</div>
                <div className="text-[10px] text-gray-400 mb-1">{s.period}</div>
                <div className={`text-2xl font-extrabold ${signClass(s.impactPercent)}`}>{fmtPct(s.impactPercent)}</div>
                {s.note && <div className="text-[10px] text-gray-400 mt-1">{s.note}</div>}
              </div>
            ))}
          </div>
          <p className="text-[11px] text-gray-400 mt-2 leading-snug">
            Mevcut varlık dağılımın o dönemde nasıl etkilenirdi (varlık-tipi proxy'leriyle tahmin).
            Pozitif = kazanç (ör. kur krizinde döviz/altın ağırlığı yükselir).
          </p>
        </Card>
      )}

      {/* Varlık-bazlı teknik sinyaller */}
      {signals.length > 0 && (
        <Card title="Varlık Bazlı Sinyaller — Her pozisyonun teknik durumu" icon={<Activity className="w-4 h-4 text-[#093eaa]" />}>
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
            {signals.map((s) => <AssetSignalCard key={s.symbol} s={s} />)}
          </div>
          <p className="text-[11px] text-gray-400 mt-2 leading-snug">
            Trend = fiyatın MA20/MA50'ye göre konumu; çubuk = 52-hafta bandındaki yer; momentum = 1/3 aylık değişim.
          </p>
        </Card>
      )}

      {/* Yeniden dengeleme (risk profili anketi) */}
      {data.rebalance && <RebalanceCard rebalance={data.rebalance} portfolioId={id} />}

      {/* AI yorum raporu */}
      <Card title="AI Yorum Raporu" icon={<Sparkles className="w-4 h-4 text-[#093eaa]" />}>
        {data.aiReportAvailable ? (
          <AiReport text={data.aiReport} />
        ) : (
          <p className="text-sm text-gray-500">
            AI yorumu şu an kullanılamıyor (model meşgul/kota). Yukarıdaki tüm metrikler ve grafikler geçerlidir.
          </p>
        )}
      </Card>

      {/* Notlar / disclaimer */}
      {data.notes?.length > 0 && (
        <div className="flex items-start gap-2 text-xs text-gray-400 px-1">
          <Info className="w-4 h-4 shrink-0 mt-0.5" />
          <div className="space-y-0.5">{data.notes.map((n, i) => <p key={i}>{n}</p>)}</div>
        </div>
      )}
    </div>
  );
}

function Row({ label, value, cls = 'text-gray-700', strong }) {
  return (
    <div className="flex justify-between items-center">
      <dt className="text-gray-500">{label}</dt>
      <dd className={`${cls} ${strong ? 'font-bold text-base' : 'font-semibold'}`}>{value}</dd>
    </div>
  );
}

function Metric({ label, value, hint }) {
  return (
    <div className="bg-gray-50 rounded-lg p-3">
      <div className="text-lg font-bold text-gray-900">{value}</div>
      <div className="text-xs font-semibold text-gray-600">{label}</div>
      <div className="text-[10px] text-gray-400">{hint}</div>
    </div>
  );
}

// AI raporunu başlık (**kalın** / # / bilinen bölüm adı) + madde + paragraf olarak biçimler.
const KNOWN_HEADINGS = [
  'teşhis', 'portföy kimliği', 'güçlü yönler', 'riskler & dikkat edilecekler', 'riskler ve dikkat edilecekler',
  'riskler & bağlantılar', 'riskler ve bağlantılar', 'riskler', 'dikkat edilmesi gerekenler',
  'varlık vurguları', 'benchmark & reel getiri', 'benchmark ve reel getiri', 'kısa-vade görünüm',
  'kısa vade görünüm', 'ne yapılabilir', 'ne yapılabilir (yön)', 'genel çıkarım',
];
const stripInline = (s) => s.replace(/\*\*/g, '').trim();

function AiReport({ text }) {
  if (!text) return null;
  const blocks = [];
  let bullets = [];
  const flush = () => { if (bullets.length) { blocks.push({ type: 'ul', items: bullets }); bullets = []; } };

  text.split(/\r?\n/).forEach((raw) => {
    const line = raw.trim();
    if (!line) { flush(); return; }
    const hm = line.match(/^(?:\d+\.\s*)?\*\*(.+?)\*\*\s*:?\s*$/) || line.match(/^#{1,4}\s*(.+?)\s*$/);
    const plain = stripInline(line).replace(/:$/, '').toLowerCase();
    if (hm) { flush(); blocks.push({ type: 'h', text: stripInline(hm[1]).replace(/:$/, '') }); return; }
    if (KNOWN_HEADINGS.includes(plain)) { flush(); blocks.push({ type: 'h', text: stripInline(line).replace(/:$/, '') }); return; }
    const bm = line.match(/^[*\-•]\s+(.+)$/);
    if (bm) { bullets.push(stripInline(bm[1])); return; }
    flush(); blocks.push({ type: 'p', text: stripInline(line) });
  });
  flush();

  return (
    <div className="space-y-2 text-sm text-gray-700 leading-relaxed">
      {blocks.map((b, i) => {
        if (b.type === 'h') {
          return (
            <h4 key={i} className="flex items-center gap-2 font-bold text-gray-900 mt-4 first:mt-0">
              <span className="w-1 h-4 bg-[#093eaa] rounded-full" />{b.text}
            </h4>
          );
        }
        if (b.type === 'ul') {
          return (
            <ul key={i} className="space-y-1 pl-1">
              {b.items.map((it, j) => (
                <li key={j} className="flex gap-2">
                  <span className="text-[#093eaa] mt-1.5 shrink-0 w-1 h-1 rounded-full bg-[#093eaa]" />
                  <span>{it}</span>
                </li>
              ))}
            </ul>
          );
        }
        return <p key={i}>{b.text}</p>;
      })}
    </div>
  );
}

function WeightBar({ label, pct, color }) {
  const v = Math.max(0, Math.min(100, Number(pct) || 0));
  return (
    <div className="text-xs">
      <div className="flex justify-between text-gray-500 mb-0.5">
        <span>{label}</span><span className="font-semibold text-gray-700">{fmtPct(pct)}</span>
      </div>
      <div className="h-2 bg-gray-100 rounded-full overflow-hidden">
        <div className="h-full rounded-full" style={{ width: `${v}%`, backgroundColor: color }} />
      </div>
    </div>
  );
}
