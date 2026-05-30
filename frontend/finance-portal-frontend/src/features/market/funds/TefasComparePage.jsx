import { useState, useCallback, useEffect } from 'react';
import { useSearchParams, useNavigate, Link } from 'react-router-dom';
import { ArrowLeft } from 'lucide-react';
import { getAllTefasFunds, getRasyonetFundDetail } from '../../../api/marketApi';

// ── Compare components ────────────────────────────────────────────────────────
import FundCompareSearch          from './components/compare/FundCompareSearch';
import FundCompareSummaryCards    from './components/compare/FundCompareSummaryCards';
import FundComparePerformanceChart from './components/compare/FundComparePerformanceChart';
import FundCompareReturnsTable    from './components/compare/FundCompareReturnsTable';
import FundCompareInfoTable       from './components/compare/FundCompareInfoTable';
import FundCompareAllocationSection from './components/compare/FundCompareAllocationSection';
import FundCompareStrategyAccordion from './components/compare/FundCompareStrategyAccordion';
import FundCompareSimulation      from './components/compare/FundCompareSimulation';
import CompareLoadingState        from './components/compare/CompareLoadingState';
import CompareEmptyState          from './components/compare/CompareEmptyState';
import CompareErrorState          from './components/compare/CompareErrorState';
import { useTranslation } from '../../../context/LanguageContext';

const MAX_FUNDS = 4;
const DEFAULT_RANGE = '1Y';

export default function TefasComparePage() {
  const { t } = useTranslation();
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();

  // URL'den başlangıç kodlarını al
  function parseUrlCodes() {
    const raw = searchParams.get('codes');
    if (!raw) return [];
    return raw.split(',').map(s => s.trim().toUpperCase()).filter(Boolean).slice(0, MAX_FUNDS);
  }

  const [selected, setSelected]     = useState(parseUrlCodes);
  const [range, setRange]           = useState(DEFAULT_RANGE);
  const [allFunds, setAllFunds]     = useState([]);
  const [detailMap, setDetailMap]   = useState({});   // code → RasyonetFundDetailDto
  const [errors, setErrors]         = useState({});   // code → error message
  const [loading, setLoading]       = useState(false);
  const [loaded, setLoaded]         = useState(false);

  // Fon listesini yükle (arama için)
  useEffect(() => {
    getAllTefasFunds().then(funds => setAllFunds(Array.isArray(funds) ? funds : [])).catch(() => {});
  }, []);

  // URL senkronizasyonu
  function syncUrl(codes) {
    if (codes.length > 0) {
      navigate(`/market/tefas/compare?codes=${codes.join(',')}`, { replace: true });
    } else {
      navigate('/market/tefas/compare', { replace: true });
    }
  }

  function addFund(code) {
    if (selected.includes(code) || selected.length >= MAX_FUNDS) return;
    const updated = [...selected, code];
    setSelected(updated);
    syncUrl(updated);
    setLoaded(false);
  }

  function removeFund(code) {
    const updated = selected.filter(c => c !== code);
    setSelected(updated);
    syncUrl(updated);
    setDetailMap(prev => { const n = { ...prev }; delete n[code]; return n; });
    setErrors(prev => { const n = { ...prev }; delete n[code]; return n; });
    if (updated.length < 2) setLoaded(false);
  }

  // Karşılaştır — her fon için Rasyonet detay çek
  const handleCompare = useCallback(async () => {
    if (selected.length < 2) return;
    setLoading(true);
    setLoaded(false);
    setErrors({});

    const newDetails = {};
    const newErrors  = {};

    await Promise.all(selected.map(async code => {
      try {
        const detail = await getRasyonetFundDetail(code, 'TMF');
        if (!detail) {
          newErrors[code] = t('Fon detayı bulunamadı.');
        } else {
          newDetails[code] = detail;
        }
      } catch (e) {
        newErrors[code] = t('Veri alınamadı.');
      }
    }));

    setDetailMap(prev => ({ ...prev, ...newDetails }));
    setErrors(newErrors);
    setLoading(false);
    setLoaded(true);
  }, [selected]);

  // Yüklü kodlar — detailMap'te olan seçili kodlar
  const loadedCodes = selected.filter(code => !!detailMap[code]);

  return (
    <div className="space-y-5">
      {/* Başlık */}
      <div>
        <Link to="/market/tefas" className="inline-flex items-center gap-1.5 text-sm text-[#093eaa] font-semibold hover:underline mb-2">
          <ArrowLeft className="w-4 h-4" /> {t('TEFAS Fonları')}
        </Link>
        <h1 className="text-2xl font-bold text-gray-900 border-l-4 border-[#093eaa] pl-4">
          {t('Fon Karşılaştırma')}
        </h1>
        <p className="text-sm text-gray-500 mt-1 pl-5">
          {t('2–4 fon seçin, normalize performans grafiği ve detaylı karşılaştırma tablosu görün')}
        </p>
      </div>

      {/* Arama + Fon seçimi */}
      <FundCompareSearch
        allFunds={allFunds}
        selected={selected}
        onAdd={addFund}
        onRemove={removeFund}
        onCompare={handleCompare}
        loading={loading}
        range={range}
        onRangeChange={r => setRange(r)}
      />

      {/* Hata mesajları */}
      <CompareErrorState errors={errors} />

      {/* Loading */}
      {loading && <CompareLoadingState count={selected.length} />}

      {/* Boş durum */}
      {!loading && !loaded && <CompareEmptyState />}

      {/* Sonuçlar */}
      {!loading && loaded && loadedCodes.length >= 1 && (
        <div className="space-y-5">
          {/* Özet kartlar */}
          <FundCompareSummaryCards
            detailMap={detailMap}
            selectedCodes={loadedCodes}
            range={range}
          />

          {/* Normalize performans grafiği */}
          <FundComparePerformanceChart
            detailMap={detailMap}
            selectedCodes={loadedCodes}
            range={range}
          />

          {/* Dönem getirileri tablosu */}
          <FundCompareReturnsTable
            detailMap={detailMap}
            selectedCodes={loadedCodes}
          />

          {/* TL Yatırım Simülasyonu */}
          <FundCompareSimulation
            detailMap={detailMap}
            selectedCodes={loadedCodes}
            range={range}
          />

          {/* Fon bilgileri tablosu */}
          <FundCompareInfoTable
            detailMap={detailMap}
            selectedCodes={loadedCodes}
          />

          {/* Varlık dağılımı */}
          <FundCompareAllocationSection
            detailMap={detailMap}
            selectedCodes={loadedCodes}
          />

          {/* Strateji accordion */}
          <FundCompareStrategyAccordion
            detailMap={detailMap}
            selectedCodes={loadedCodes}
          />

          {/* Disclaimer */}
          <div className="bg-amber-50 border border-amber-200 rounded-2xl p-4">
            <p className="text-xs text-amber-800 leading-relaxed">
              <span className="font-bold">{t('⚠ Uyarı:')}</span> {t('Bu sayfada yer alan veriler yatırım tavsiyesi değildir. Geçmiş performans gelecekteki getirilerin garantisi değildir. Veriler Rasyonet / YatırımDirekt kaynaklıdır.')}
            </p>
          </div>
        </div>
      )}
    </div>
  );
}
