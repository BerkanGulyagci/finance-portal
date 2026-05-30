import { useEffect, useState, useCallback } from 'react';
import { useParams, useLocation, Link } from 'react-router-dom';
import { getEvdsBondDetail, getEvdsBondHistory } from '../../../api/marketApi';
import BondDetailHeader     from './components/BondDetailHeader';
import BondIndicators       from './components/BondIndicators';
import BondInfoTable        from './components/BondInfoTable';
import BondAnalysisCard     from './components/BondAnalysisCard';
import BondEvdsHistoryChart from './components/BondEvdsHistoryChart';
import InstrumentActionButtons from '../../../components/instrument/InstrumentActionButtons';
import { fmtPct } from './components/bondChartUtils';
import { useTranslation } from '../../../context/LanguageContext';

export default function BondDetailPage() {
  const { t } = useTranslation();
  const { symbol } = useParams();
  const location   = useLocation();

  const [bond, setBond]       = useState(location.state?.bond ?? null);
  const [loading, setLoading] = useState(true);
  const [error, setError]     = useState('');

  const [historyPoints, setHistoryPoints]   = useState([]);
  const [historyLoading, setHistoryLoading] = useState(false);

  useEffect(() => {
    const code = decodeURIComponent(symbol).toUpperCase();
    setLoading(true);
    setError('');
    getEvdsBondDetail(code)
      .then(data => { if (!data) setError(t('Kıymet bulunamadı.')); else setBond(data); })
      .catch(e => setError(e.response?.status === 404 ? `${t('Kıymet bulunamadı:')} ${code}` : t('TCMB EVDS verileri şu anda alınamadı.')))
      .finally(() => setLoading(false));
  }, [symbol]);

  const loadHistory = useCallback(() => {
    const code = decodeURIComponent(symbol).toUpperCase();
    setHistoryLoading(true);
    getEvdsBondHistory(code, 'ONE_YEAR')
      .then(data => setHistoryPoints(Array.isArray(data) ? data : []))
      .catch(() => setHistoryPoints([]))
      .finally(() => setHistoryLoading(false));
  }, [symbol]);

  useEffect(() => { loadHistory(); }, [loadHistory]);

  if (loading) {
    return (
      <div className="flex items-center justify-center py-24">
        <div className="flex gap-2">
          <div className="w-2 h-2 bg-[#093eaa] rounded-full animate-bounce" />
          <div className="w-2 h-2 bg-[#093eaa]/60 rounded-full animate-bounce [animation-delay:100ms]" />
          <div className="w-2 h-2 bg-[#093eaa]/30 rounded-full animate-bounce [animation-delay:200ms]" />
        </div>
      </div>
    );
  }

  if (error || !bond) {
    return (
      <div className="max-w-3xl mx-auto py-12 text-center space-y-4">
        <p className="text-rose-500 text-sm">{error || t('Kıymet bulunamadı.')}</p>
        <Link to="/market/bonds" className="text-[#093eaa] text-sm hover:underline">{t('← Listeye Dön')}</Link>
      </div>
    );
  }

  return (
    <div className="max-w-5xl mx-auto space-y-5">

      {/* 1. Header — kıymet kodu, tür, tarihler, kaynak */}
      <BondDetailHeader bond={bond} />

      {/* Portföye Ekle + Alarm (yalnız giriş yapan kullanıcı) */}
      <div className="flex justify-end">
        <InstrumentActionButtons
          assetType="BOND"
          symbol={bond.instrumentCode}
          name={bond.instrumentCode}
          price={bond.indicatorValue}
        />
      </div>

      {/* 2. Özet metrik satırı */}
      <MetricBar bond={bond} />

      {/* 3. Göstergeler (sol) + Kıymet Bilgileri (sağ) */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-5 items-start">
        <BondIndicators
          bond={bond}
          historyPoints={historyPoints}
          historyLoading={historyLoading}
        />
        <BondInfoTable bond={bond} />
      </div>

      {/* 4. Mini Analiz — tam genişlik */}
      <BondAnalysisCard bond={bond} />

      {/* 4. Tarihsel Grafik */}
      <BondEvdsHistoryChart
        instrumentCode={bond.instrumentCode}
        basePoints={historyPoints}
        baseLoading={historyLoading}
        mainDetail={bond}
      />

      {/* 5. Disclaimer */}
      <div className="bg-amber-50 border border-amber-200 rounded-2xl p-4">
        <p className="text-xs text-amber-800 leading-relaxed">
          <span className="font-bold">{t('⚠ Uyarı:')}</span> {t('Bu sayfada yer alan veriler TCMB EVDS kaynaklı gösterge değerleridir. Alış/satış fiyatı değildir. Yatırım tavsiyesi niteliği taşımaz.')}
        </p>
      </div>
    </div>
  );
}

// ── Özet metrik satırı ────────────────────────────────────────────────────────
function MetricBar({ bond }) {
  const { t } = useTranslation();
  function fmtNum(v, d = 2) {
    if (v == null) return '-';
    const n = parseFloat(v);
    return isNaN(n) ? '-' : n.toLocaleString('tr-TR', { minimumFractionDigits: d, maximumFractionDigits: d });
  }

  const dailyChange    = bond.dailyChange    != null ? parseFloat(bond.dailyChange)    : null;
  const dailyChangePct = bond.dailyChangePercent != null ? parseFloat(bond.dailyChangePercent) : null;
  const changePos = dailyChange != null && dailyChange >= 0;

  const metrics = [
    {
      label: 'EVDS Gösterge Değeri',
      value: fmtNum(bond.indicatorValue, 2),
      accent: true,
    },
    {
      label: 'Önceki Değer',
      value: fmtNum(bond.previousValue, 2),
    },
    {
      label: 'Günlük Değişim',
      value: dailyChange != null
        ? `${changePos ? '+' : '-'}${fmtNum(Math.abs(dailyChange), 4)}`
        : '-',
      valueColor: dailyChange == null ? '' : changePos ? 'text-emerald-600' : 'text-rose-600',
      sub: dailyChangePct != null ? fmtPct(dailyChangePct, 2) : null,
    },
    {
      label: 'Kupon Faiz Oranı',
      value: bond.couponRate != null ? `%${fmtNum(bond.couponRate, 2)}` : '-',
    },
  ];

  return (
    <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
      {metrics.map((m, i) => (
        <div
          key={i}
          className={`bg-white rounded-2xl border shadow-sm p-5 ${m.accent ? 'border-[#093eaa]/25' : 'border-gray-200'}`}
        >
          <p className="text-xs text-gray-400 font-medium uppercase tracking-wide mb-2 leading-tight">
            {t(m.label)}
          </p>
          <p className={`text-2xl font-bold font-mono leading-none ${m.valueColor ?? (m.accent ? 'text-[#093eaa]' : 'text-gray-900')}`}>
            {m.value}
          </p>
          {m.sub && (
            <p className={`text-xs mt-1 font-semibold ${m.valueColor ?? 'text-gray-400'}`}>{m.sub}</p>
          )}
        </div>
      ))}
    </div>
  );
}
