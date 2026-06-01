import { useEffect, useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import {
  ArrowLeft, Sparkles, ShieldAlert, HeartPulse, PieChart as PieIcon,
  TrendingUp, TrendingDown, Scale, AlertTriangle, Info, History,
} from 'lucide-react';
import {
  PieChart, Pie, Cell, ResponsiveContainer, Tooltip, Legend,
  BarChart, Bar, XAxis, YAxis, CartesianGrid, LineChart, Line,
} from 'recharts';
import { getPortfolioAiAnalysis } from '../../api/portfolioApi';

const PIE_COLORS = ['#093eaa', '#2563eb', '#0ea5e9', '#10b981', '#f59e0b',
  '#ef4444', '#8b5cf6', '#ec4899', '#14b8a6', '#64748b'];

const fmtMoney = (v) => (v == null ? '—' : Number(v).toLocaleString('tr-TR', { maximumFractionDigits: 0 }) + ' ₺');
const fmtPct = (v) => (v == null ? '—' : `%${Number(v).toLocaleString('tr-TR', { maximumFractionDigits: 2 })}`);
const fmtNum = (v) => (v == null ? '—' : Number(v).toLocaleString('tr-TR', { maximumFractionDigits: 2 }));
const signClass = (v) => (v == null ? 'text-gray-500' : Number(v) > 0 ? 'text-emerald-600' : Number(v) < 0 ? 'text-rose-600' : 'text-gray-500');

// Risk: düşük=yeşil; Sağlık: düşük=kırmızı.
const riskColor = (s) => (s < 33 ? '#10b981' : s < 66 ? '#f59e0b' : '#ef4444');
const healthColor = (s) => (s < 33 ? '#ef4444' : s < 66 ? '#f59e0b' : '#10b981');

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
    <div className="mt-3 space-y-1.5">
      {factors.map((f) => (
        <div key={f.label} className="text-xs">
          <div className="flex justify-between text-gray-500">
            <span title={f.detail}>{f.label}</span>
            <span className="font-semibold text-gray-700">{f.contribution}</span>
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

export default function AiAnalysisPage() {
  const { id } = useParams();
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    let alive = true;
    setLoading(true); setError('');
    getPortfolioAiAnalysis(id)
      .then((d) => { if (alive) setData(d); })
      .catch((e) => { if (alive) setError(e?.response?.data?.message || 'Analiz alınamadı.'); })
      .finally(() => { if (alive) setLoading(false); });
    return () => { alive = false; };
  }, [id]);

  if (loading) {
    return (
      <div className="min-h-[60vh] flex flex-col items-center justify-center text-gray-500">
        <Sparkles className="w-8 h-8 text-[#093eaa] animate-pulse mb-3" />
        <p className="text-sm">Portföyün AI ile analiz ediliyor…</p>
        <p className="text-xs text-gray-400 mt-1">Metrikler hesaplanıyor, AI yorumu hazırlanıyor.</p>
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
  const typeAlloc = (data.assetTypeAllocation || []).map((s) => ({ name: s.label, value: Number(s.weightPercent) }));
  const valueSeries = (data.valueSeries || []).map((p) => ({
    date: p.date, Gerçek: p.value != null ? Number(p.value) : null, Maliyet: p.cost != null ? Number(p.cost) : null,
  }));
  const benchData = [
    { name: 'Portföyün', value: Number(data.totalProfitLossPercent ?? 0), me: true },
    ...(data.benchmarks || []).map((b) => ({ name: b.label, value: Number(b.returnPercent ?? 0), me: false })),
  ];
  const aiText = (data.aiReport || '').replace(/\*\*/g, '').replace(/^#+\s*/gm, '');

  return (
    <div className="max-w-6xl mx-auto px-3 sm:px-4 py-4 space-y-4">
      {/* Header */}
      <div className="bg-white rounded-lg border border-gray-200 shadow-sm p-4 flex items-center gap-3">
        <Link to={`/portfolio/${id}`} className="text-gray-400 hover:text-[#093eaa] mt-0.5 shrink-0">
          <ArrowLeft className="w-5 h-5" />
        </Link>
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2">
            <Sparkles className="w-5 h-5 text-[#093eaa]" />
            <h1 className="text-lg sm:text-xl font-bold text-gray-900 truncate">AI Portföy Analizi</h1>
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

      {/* Skorlar + özet */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        <Card title="Risk Skoru" icon={<ShieldAlert className="w-4 h-4 text-amber-500" />}>
          <div className="flex flex-col items-center">
            <ScoreGauge score={data.riskScore} color={riskColor(data.riskScore)} label={data.riskLabel} sub="risk seviyesi" />
            <FactorBars factors={data.riskFactors} />
          </div>
        </Card>
        <Card title="Sağlık Skoru" icon={<HeartPulse className="w-4 h-4 text-emerald-500" />}>
          <div className="flex flex-col items-center">
            <ScoreGauge score={data.healthScore} color={healthColor(data.healthScore)} label={data.healthLabel} sub="portföy sağlığı" />
            <FactorBars factors={data.healthFactors} />
          </div>
        </Card>
        <Card title="Getiri Özeti" icon={<TrendingUp className="w-4 h-4 text-[#093eaa]" />}>
          <dl className="space-y-2.5 text-sm">
            <Row label="Nominal getiri" value={fmtPct(data.totalProfitLossPercent)} cls={signClass(data.totalProfitLossPercent)} />
            <Row label="Reel getiri (TÜFE'siz)" value={fmtPct(data.realProfitLossPercent)} cls={signClass(data.realProfitLossPercent)} strong />
            <Row label="Dönem enflasyonu" value={fmtPct(data.inflationSincePercent)} cls="text-gray-600" />
            <Row label="Toplam K/Z" value={fmtMoney(data.totalProfitLossTry)} cls={signClass(data.totalProfitLossTry)} />
            <Row label="Maliyet" value={fmtMoney(data.totalCostTry)} cls="text-gray-600" />
          </dl>
          <p className="text-[11px] text-gray-400 mt-3 leading-snug">
            Reel getiri enflasyondan arındırılmıştır — asıl "kazandın mı" sorusunun cevabı budur.
          </p>
        </Card>
      </div>

      {/* Risk metrikleri + yoğunlaşma */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        <Card title="Risk-Ayarlı Metrikler" icon={<Scale className="w-4 h-4 text-[#093eaa]" />} className="md:col-span-2">
          {m.available ? (
            <div className="grid grid-cols-2 sm:grid-cols-3 gap-3">
              <Metric label="Yıllık Volatilite" value={fmtPct(m.annualVolatilityPercent)} hint="Dalgalanma" />
              <Metric label="Sharpe" value={fmtNum(m.sharpe)} hint="Birim risk başına getiri" />
              <Metric label="Sortino" value={fmtNum(m.sortino)} hint="Aşağı-yön risk-ayarlı" />
              <Metric label="Max Düşüş" value={fmtPct(m.maxDrawdownPercent)} hint="Tepe→dip kayıp" />
              <Metric label="Beta (BIST100)" value={fmtNum(m.beta)} hint="Piyasaya duyarlılık" />
              <Metric label="Veri" value={`${m.sampleMonths} ay`} hint="Hesap penceresi" />
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

      {/* Dağılım pasta + değer serisi */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        <Card title="Varlık Dağılımı" icon={<PieIcon className="w-4 h-4 text-[#093eaa]" />}>
          <ResponsiveContainer width="100%" height={260}>
            <PieChart>
              <Pie data={typeAlloc} dataKey="value" nameKey="name" cx="50%" cy="50%" outerRadius={90}
                label={(e) => `${e.name} ${e.value.toFixed(0)}%`} labelLine={false}>
                {typeAlloc.map((_, i) => <Cell key={i} fill={PIE_COLORS[i % PIE_COLORS.length]} />)}
              </Pie>
              <Tooltip formatter={(v) => `${Number(v).toFixed(1)}%`} />
            </PieChart>
          </ResponsiveContainer>
        </Card>
        <Card title="Değer Gelişimi (Gerçek vs Maliyet)" icon={<TrendingUp className="w-4 h-4 text-[#093eaa]" />}>
          <ResponsiveContainer width="100%" height={260}>
            <LineChart data={valueSeries} margin={{ top: 5, right: 10, left: 0, bottom: 0 }}>
              <CartesianGrid strokeDasharray="3 3" stroke="#f1f5f9" />
              <XAxis dataKey="date" tick={{ fontSize: 11 }} minTickGap={30} />
              <YAxis tick={{ fontSize: 11 }} tickFormatter={(v) => (v / 1000).toFixed(0) + 'b'} width={40} />
              <Tooltip formatter={(v) => fmtMoney(v)} />
              <Legend />
              <Line type="monotone" dataKey="Gerçek" stroke="#093eaa" strokeWidth={2} dot={false} />
              <Line type="monotone" dataKey="Maliyet" stroke="#94a3b8" strokeWidth={1.5} strokeDasharray="4 4" dot={false} />
            </LineChart>
          </ResponsiveContainer>
        </Card>
      </div>

      {/* Benchmark karşılaştırması */}
      <Card title="Benchmark Karşılaştırması — Aynı parayı nereye koysaydın?" icon={<Scale className="w-4 h-4 text-[#093eaa]" />}>
        <ResponsiveContainer width="100%" height={Math.max(200, benchData.length * 42)}>
          <BarChart data={benchData} layout="vertical" margin={{ top: 5, right: 30, left: 10, bottom: 0 }}>
            <CartesianGrid strokeDasharray="3 3" stroke="#f1f5f9" horizontal={false} />
            <XAxis type="number" tick={{ fontSize: 11 }} tickFormatter={(v) => `%${v}`} />
            <YAxis type="category" dataKey="name" tick={{ fontSize: 12 }} width={110} />
            <Tooltip formatter={(v) => fmtPct(v)} />
            <Bar dataKey="value" radius={[0, 4, 4, 0]}>
              {benchData.map((d, i) => (
                <Cell key={i} fill={d.me ? '#093eaa' : Number(d.value) >= 0 ? '#10b981' : '#ef4444'} />
              ))}
            </Bar>
          </BarChart>
        </ResponsiveContainer>
        <p className="text-[11px] text-gray-400 mt-1 leading-snug">
          Yeşil/kırmızı = ilgili aracın aynı dönemdeki getirisi; lacivert = senin portföyün. Portföyün bir aracı geçtiyse o araçtan iyisin.
        </p>
      </Card>

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

      {/* En çok kazandıran / kaybettiren */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        <Card title="En Çok Kazandıranlar" icon={<TrendingUp className="w-4 h-4 text-emerald-500" />}>
          <MoversList rows={data.topGainers} />
        </Card>
        <Card title="En Çok Kaybettirenler" icon={<TrendingDown className="w-4 h-4 text-rose-500" />}>
          <MoversList rows={data.topLosers} />
        </Card>
      </div>

      {/* Varlık getiri tablosu */}
      <Card title="Varlık Bazlı Getiri" icon={<PieIcon className="w-4 h-4 text-[#093eaa]" />}>
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="text-left text-gray-400 border-b border-gray-100">
                <th className="py-2 font-semibold">Varlık</th>
                <th className="py-2 font-semibold text-right">Ağırlık</th>
                <th className="py-2 font-semibold text-right">Getiri</th>
                <th className="py-2 font-semibold text-right">K/Z (TL)</th>
              </tr>
            </thead>
            <tbody>
              {(data.assetReturns || []).map((a) => (
                <tr key={a.symbol} className="border-b border-gray-50">
                  <td className="py-2 font-medium text-gray-800">{a.name}</td>
                  <td className="py-2 text-right text-gray-600">{fmtPct(a.weightPercent)}</td>
                  <td className={`py-2 text-right font-semibold ${signClass(a.profitLossPercent)}`}>{fmtPct(a.profitLossPercent)}</td>
                  <td className={`py-2 text-right ${signClass(a.profitLossTry)}`}>{fmtMoney(a.profitLossTry)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Card>

      {/* AI yorum raporu */}
      <Card title="AI Yorum Raporu" icon={<Sparkles className="w-4 h-4 text-[#093eaa]" />}>
        {data.aiReportAvailable ? (
          <div className="text-sm text-gray-700 leading-relaxed whitespace-pre-line">{aiText}</div>
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

function MoversList({ rows }) {
  if (!rows?.length) return <p className="text-sm text-gray-400">Veri yok.</p>;
  return (
    <ul className="space-y-2">
      {rows.map((a) => (
        <li key={a.symbol} className="flex items-center justify-between text-sm">
          <span className="font-medium text-gray-800 truncate">{a.name}</span>
          <span className={`font-bold ${signClass(a.profitLossPercent)}`}>{fmtPct(a.profitLossPercent)}</span>
        </li>
      ))}
    </ul>
  );
}
