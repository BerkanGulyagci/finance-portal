import { useEffect, useState } from 'react';
import { useParams, useLocation, Link } from 'react-router-dom';
import { ArrowLeft, ExternalLink, TrendingUp, TrendingDown, BarChart2 } from 'lucide-react';
import { getRasyonetFundDetail } from '../../../api/marketApi';
import { useTranslation } from '../../../context/LanguageContext';
import UniversalCompareButton from '../../../components/common/UniversalCompareButton';
import InstrumentActionButtons from '../../../components/instrument/InstrumentActionButtons';
import TefasPriceChart from './components/TefasPriceChart';
import MonthlyReturnChart from './components/MonthlyReturnChart';
import AssetAllocationChart from './components/AssetAllocationChart';

// ── Yardımcılar ───────────────────────────────────────────────────────────────

function toFloat(v) {
  if (v == null) return null;
  const n = parseFloat(v);
  return isNaN(n) ? null : n;
}

function fmt(v, dec = 6) {
  const n = toFloat(v);
  if (n == null) return '-';
  // dec verilmişse kullan, yoksa fiyat büyüklüğüne göre otomatik
  const abs = Math.abs(n);
  const auto = abs >= 100 ? 2 : abs >= 10 ? 3 : abs >= 1 ? 4 : abs >= 0.1 ? 5 : abs >= 0.01 ? 6 : 8;
  const d = dec === 6 ? auto : dec; // 6 default ise otomatik kullan
  return n.toLocaleString('tr-TR', { minimumFractionDigits: d, maximumFractionDigits: d });
}

function fmtPct(v, dec = 2) {
  const n = toFloat(v);
  if (n == null) return { text: '-', color: 'text-gray-400', pos: null };
  const text = `${n >= 0 ? '+' : ''}${n.toFixed(dec)}%`;
  return { text, color: n >= 0 ? 'text-emerald-600' : 'text-rose-600', pos: n >= 0 };
}

function fmtBig(v) {
  const n = toFloat(v);
  if (n == null) return '-';
  if (n >= 1e9) return `${(n / 1e9).toFixed(2)}B`;
  if (n >= 1e6) return `${(n / 1e6).toFixed(2)}M`;
  if (n >= 1e3) return `${(n / 1e3).toFixed(2)}K`;
  return n.toFixed(2);
}

/** API bazen uzun float string döner; yüzde olarak kısa göster */
function fmtPercentField(raw) {
  if (raw == null || raw === '') return '-';
  const s = String(raw).trim().replace(/\s/g, '').replace(',', '.').replace(/%/g, '');
  const n = parseFloat(s);
  if (Number.isNaN(n)) return String(raw);
  return `%${n.toFixed(2)}`;
}

/**
 * Varlık dağılımı yüzdesi — 2 ondalıkta "0.00"a yuvarlanacak kadar küçük ama SIFIR OLMAYAN
 * değerleri de gerçeğiyle göster (ör. 0.0034). Gerçekten 0 ise "0". Normalde 2 ondalık.
 */
function fmtAllocPct(v) {
  const n = toFloat(v);
  if (n == null || n === 0) return '0';
  if (Math.abs(n) >= 0.005) return n.toFixed(2);
  return String(parseFloat(n.toPrecision(2))); // 0.0034, 0.00012 … (sondaki sıfırlar atılır)
}

// ── Risk göstergesi ───────────────────────────────────────────────────────────

function RiskMeter({ level }) {
  const { t } = useTranslation();
  if (level == null) return null;
  const colors = ['', '#22c55e', '#84cc16', '#eab308', '#f97316', '#ef4444', '#dc2626', '#7c3aed'];
  return (
    <div className="flex items-center gap-2">
      <div className="flex gap-1">
        {[1, 2, 3, 4, 5, 6, 7].map(i => (
          <div key={i} className="w-6 h-3 rounded-sm"
            style={{ backgroundColor: i <= level ? colors[level] : '#e5e7eb' }} />
        ))}
      </div>
      <span className="text-sm font-bold" style={{ color: colors[level] }}>
        {t('Risk')} {level}/7
      </span>
    </div>
  );
}

// ── Getiri kartı ──────────────────────────────────────────────────────────────

function ReturnCard({ label, value }) {
  const { text, color } = fmtPct(value);
  return (
    <div className="bg-gray-50 rounded-xl p-3 text-center">
      <p className="text-xs text-gray-400 mb-1">{label}</p>
      <p className={`text-sm font-bold ${color}`}>{text}</p>
    </div>
  );
}

// ── Fiyat grafiği (KLineCharts) — zaman filtreli ─────────────────────────────

/* ─── Drawing Toolbar (hisse sayfasıyla aynı yapı) ─── */
export default function TefasFundDetailPage() {
  const { t } = useTranslation();
  const { code }   = useParams();
  const location   = useLocation();
  const [fund, setFund]       = useState(location.state?.listItem ?? null);
  const [detail, setDetail]   = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError]     = useState('');
  const [activeTab, setActiveTab] = useState('performance');

  // sourceCode: listItem'dan al (BES=TPF, OKS=TAF, TEFAS=TMF)
  // fundCategory → sourceCode mapping
  const sourceCode = (() => {
    const cat = location.state?.listItem?.fundCategory;
    if (cat === 'PENSION_FUND') return 'TPF';
    if (cat === 'AUTO_ENROLLMENT_FUND') return 'TAF';
    // Osmanlı: uniqueCode'dan çıkar (örn: OSL:TMF → TMF)
    const uc = location.state?.listItem?.uniqueCode;
    if (uc && uc.includes(':')) return uc.split(':')[1];
    return 'TMF';
  })();

  useEffect(() => {
    setLoading(true);
    setError('');
    getRasyonetFundDetail(code, sourceCode)
      .then(data => {
        if (!data) setError(t('Fon bilgisi bulunamadı.'));
        else setDetail(data);
      })
      .catch(() => setError(t('Veriler yüklenemedi.')))
      .finally(() => setLoading(false));
  }, [code, sourceCode]);

  const d = detail;
  const name = d?.name ?? fund?.name ?? code;
  const price = d?.price ?? fund?.price;
  const ret1m = d?.returnOneMonth ?? fund?.returnOneMonth;
  const { text: ret1mText, color: ret1mColor, pos: ret1mPos } = fmtPct(ret1m);

  const TABS = [
    { key: 'performance', label: 'Performans' },
    { key: 'chart',       label: 'Grafik' },
    { key: 'info',        label: 'Fon Bilgileri' },
  ];

  return (
    <div className="max-w-5xl mx-auto space-y-5">
      {/* Geri */}
      <Link to="/market/tefas" className="inline-flex items-center gap-1.5 text-sm text-[#093eaa] font-semibold hover:underline">
        <ArrowLeft className="w-4 h-4" /> {t('TEFAS Fonları')}
      </Link>

      {/* ── Header ── */}
      <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-6">
        <div className="flex items-start justify-between flex-wrap gap-4">
          <div>
            <div className="flex items-center gap-3 mb-1">
              <span className="px-2.5 py-1 bg-[#093eaa] text-white text-xs font-bold rounded-lg font-mono">{code}</span>
              {d?.fundType && (
                <span className="px-2 py-0.5 bg-blue-50 text-blue-700 text-xs font-semibold rounded-full">{d.fundType}</span>
              )}
            </div>
            <h1 className="text-xl font-bold text-gray-900 leading-tight">{name}</h1>
            {d?.managerName && (
              <p className="text-sm text-gray-500 mt-0.5">{d.managerName}</p>
            )}
          </div>
          <div className="text-right">
            <p className="text-3xl font-bold text-gray-900 font-mono">{fmt(price, 6)}</p>
            <p className="text-xs text-gray-400 mt-0.5">TL</p>
            {ret1m != null && (
              <p className={`text-sm font-semibold flex items-center justify-end gap-1 mt-1 ${ret1mColor}`}>
                {ret1mPos ? <TrendingUp className="w-4 h-4" /> : <TrendingDown className="w-4 h-4" />}
                {t('1 Ay:')} {ret1mText}
              </p>
            )}
          </div>
        </div>

        {/* Portföye Ekle + Alarm (yalnız giriş yapan kullanıcı) */}
        <div className="mt-4">
          <InstrumentActionButtons assetType="FUND" symbol={code} name={name} price={price} />
        </div>

        {/* Risk + KAP */}
        <div className="flex items-center justify-between mt-4 flex-wrap gap-3">
          <RiskMeter level={d?.riskLevel} />
          {d?.kapLink && (
            <a href={d.kapLink} target="_blank" rel="noopener noreferrer"
              className="inline-flex items-center gap-1.5 text-xs text-[#093eaa] hover:underline font-semibold">
              <ExternalLink className="w-3.5 h-3.5" /> {t('KAP Fon Bilgileri')}
            </a>
          )}
        </div>
      </div>

      {/* ── Loading / Error ── */}
      {loading && (
        <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-12 flex items-center justify-center">
          <div className="flex gap-2">
            <div className="w-2 h-2 bg-[#093eaa] rounded-full animate-bounce" />
            <div className="w-2 h-2 bg-[#093eaa]/60 rounded-full animate-bounce [animation-delay:100ms]" />
            <div className="w-2 h-2 bg-[#093eaa]/30 rounded-full animate-bounce [animation-delay:200ms]" />
          </div>
        </div>
      )}
      {error && (
        <div className="bg-rose-50 border border-rose-200 rounded-2xl p-4 text-rose-600 text-sm">{error}</div>
      )}

      {/* ── Tab içeriği ── */}
      {!loading && !error && d && (
        <>
          {/* Tab butonları */}
          <div className="flex gap-1 bg-gray-100 rounded-xl p-1 w-fit">
            {TABS.map(tab => (
              <button key={tab.key} onClick={() => setActiveTab(tab.key)}
                className={`px-4 py-2 rounded-lg text-sm font-semibold transition-all ${
                  activeTab === tab.key ? 'bg-white text-[#093eaa] shadow-sm' : 'text-gray-500 hover:text-gray-700'
                }`}>
                {t(tab.label)}
              </button>
            ))}
          </div>

          {/* ── Performans tab ── */}
          {activeTab === 'performance' && (
            <div className="space-y-5">
              <div className="grid grid-cols-2 sm:grid-cols-5 lg:grid-cols-9 gap-2">
                <ReturnCard label={t('Günlük')} value={d.returnOneDay} />
                <ReturnCard label={t('Haftalık')} value={d.returnOneWeek} />
                <ReturnCard label={t('1 Ay')} value={d.returnOneMonth} />
                <ReturnCard label={t('3 Ay')} value={d.returnThreeMonths} />
                <ReturnCard label={t('6 Ay')} value={d.returnSixMonths} />
                <ReturnCard label={t('YBG')} value={d.returnYearToDate} />
                <ReturnCard label={t('1 Yıl')} value={d.returnOneYear} />
                <ReturnCard label={t('3 Yıl')} value={d.returnThreeYears} />
                <ReturnCard label={t('5 Yıl')} value={d.returnFiveYears} />
              </div>

              {/* Aylık getiri grafiği */}
              {d.monthlyReturns && d.monthlyReturns.length > 0 && (
                <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-6">
                  <h2 className="font-bold text-gray-900 mb-4">{t('Aylık Getiri (Son 24 Ay)')}</h2>
                  <MonthlyReturnChart monthlyReturns={d.monthlyReturns} />
                </div>
              )}

              {/* Risk bilgileri */}
              {(d.riskBest || d.riskWorst || d.riskPositiveRateOfReturn) && (
                <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-6">
                  <h2 className="font-bold text-gray-900 mb-4">{t('Risk ve Performans')}</h2>
                  <div className="grid grid-cols-3 gap-4">
                    <div className="bg-emerald-50 rounded-xl p-4 text-center">
                      <p className="text-xs text-gray-500 mb-1">{t('En İyi Ay Getirisi')}</p>
                      <p className="text-lg font-bold text-emerald-600">
                        {d.riskBest ? `+${parseFloat(d.riskBest).toFixed(2)}%` : '-'}
                      </p>
                    </div>
                    <div className="bg-rose-50 rounded-xl p-4 text-center">
                      <p className="text-xs text-gray-500 mb-1">{t('En Kötü Ay Getirisi')}</p>
                      <p className="text-lg font-bold text-rose-600">
                        {d.riskWorst ? `${parseFloat(d.riskWorst).toFixed(2)}%` : '-'}
                      </p>
                    </div>
                    <div className="bg-blue-50 rounded-xl p-4 text-center">
                      <p className="text-xs text-gray-500 mb-1">{t('Pozitif Getiri Oranı')}</p>
                      <p className="text-lg font-bold text-[#093eaa]">{d.riskPositiveRateOfReturn ?? '-'}</p>
                    </div>
                  </div>
                </div>
              )}
            </div>
          )}

          {/* ── Grafik tab ── */}
          {activeTab === 'chart' && (
            <div className="space-y-5">
              <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-6">
                <div className="flex items-center justify-between gap-2 mb-2 flex-wrap">
                  <h2 className="font-bold text-gray-900">{t('Fiyat Grafiği')}</h2>
                  <div className="flex items-center gap-2">
                    <Link
                      to={`/market/tefas/compare?codes=${code}`}
                      title={t('Fonları kendi karşılaştırma sayfasında kıyasla')}
                      className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-bold border border-[#093eaa]/25 text-[#093eaa] bg-[#093eaa]/5 hover:bg-[#093eaa]/10 transition-all"
                    >
                      <BarChart2 className="w-3.5 h-3.5" /> {t('Karşılaştır')}
                    </Link>
                    <UniversalCompareButton assetType="FUND" symbol={code} name={name || code} />
                  </div>
                </div>
                <TefasPriceChart
                  code={code}
                  fonTipi={sourceCode === 'TMF' ? 'YAT' : 'EMK'}
                  priceHistory={d.priceHistory}
                  monthlyReturns={d.monthlyReturns}
                />
                <p className="text-xs text-gray-400 mt-3">{t('Kaynak: Rasyonet (≤1 Yıl) · TEFAS (5 Yıl derin geçmiş)')}</p>
              </div>

              {d.assetAllocation && d.assetAllocation.length > 0 && (
                <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-6">
                  <h2 className="font-bold text-gray-900 mb-4">{t('Varlık Dağılımı')}</h2>
                  <AssetAllocationChart assetAllocation={d.assetAllocation} />
                </div>
              )}
            </div>
          )}

          {/* ── Fon Bilgileri tab ── */}
          {activeTab === 'info' && (
            <div className="space-y-5">
              {/* Temel bilgiler */}
              <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-6">
                <h2 className="font-bold text-gray-900 mb-4">{t('Fon Temel Bilgileri')}</h2>
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-0">
                  {[
                    ['Fon Kodu', d.code ?? '-'],
                    ['Fon Adı', d.name ?? '-'],
                    ['Fon Türü', d.fundType ?? '-'],
                    ['Birim Pay Değeri', fmt(d.price, 6)],
                    ['Fon Büyüklüğü', d.marketCap != null ? `${fmtBig(d.marketCap)} TL` : '-'],
                    ['Fon Büyüklüğü USD', d.marketCapUsd != null ? `${fmtBig(d.marketCapUsd)} USD` : '-'],
                    ['Alım Valörü', d.buySettlement ? `T+${d.buySettlement}` : '-'],
                    ['Satım Valörü', d.sellSettlement ? `T+${d.sellSettlement}` : '-'],
                    ['Min. Alım Miktarı', d.minimumQuantitySales ?? '-'],
                    ['Yıllık Yönetim Ücreti', fmtPercentField(d.managementFeeAnnual)],
                    ['Komisyon', d.commission != null && d.commission !== '' ? fmtPercentField(d.commission) : '-'],
                    ['Fon Yöneticisi', d.managerName ?? '-'],
                    ['Kurucu', d.founderName ?? '-'],
                  ].map(([label, value]) => (
                    <div key={label} className="flex justify-between items-start py-3 border-b border-gray-100 px-1">
                      <span className="text-sm text-gray-500 w-32 sm:w-44 shrink-0">{t(label)}</span>
                      <span className="text-sm text-gray-900 font-medium text-right">{value}</span>
                    </div>
                  ))}
                </div>
              </div>

              {/* Strateji */}
              {d.strategy && (
                <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-6">
                  <h2 className="font-bold text-gray-900 mb-3">{t('Yatırım Stratejisi')}</h2>
                  <p className="text-sm text-gray-700 leading-relaxed">{d.strategy}</p>
                </div>
              )}
            </div>
          )}
        </>
      )}

      {/* Disclaimer */}
      <div className="bg-amber-50 border border-amber-200 rounded-2xl p-4">
        <p className="text-xs text-amber-800 leading-relaxed">
          <span className="font-bold">{t('⚠ Uyarı:')}</span> {t('Bu sayfada yer alan veriler yatırım tavsiyesi değildir. Geçmiş performans gelecekteki getirilerin garantisi değildir. Veriler Rasyonet / YatırımDirekt kaynaklıdır.')}
        </p>
      </div>
    </div>
  );
}
