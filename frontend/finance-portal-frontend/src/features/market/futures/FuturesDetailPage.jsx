import { useEffect, useState } from 'react';
import { useParams, useLocation, Link } from 'react-router-dom';
import { ArrowLeft } from 'lucide-react';
import ViopContractHeader from './components/ViopContractHeader';
import ViopContractStats from './components/ViopContractStats';
import ViopPriceRange from './components/ViopPriceRange';
import ViopOpenPositions from './components/ViopOpenPositions';
import ViopContractInfo from './components/ViopContractInfo';
import ViopPriceChart from './components/ViopPriceChart';
import { getViopContracts } from '../../../api/marketApi';
import { fixViopContractName, viopContractNamesMatch } from './utils/viopContractNameFix';
import { useTranslation } from '../../../context/LanguageContext';
import { SkeletonDetail } from '../../../components/common/Skeleton';

export default function FuturesDetailPage() {
  const { t } = useTranslation();
  const { symbol } = useParams();
  const location = useLocation();
  const [contract, setContract] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    if (location.state?.contract) {
      // Normal akış: listeden tıklandı, state mevcut
      setContract(location.state.contract);
      setLoading(false);
      setError(null);
    } else if (symbol) {
      // Direkt URL ile gelindi: symbol param'ından contract name'i decode et
      // URL encode: encodeURIComponent(r.name) → decode ederek Akbank listesinde ara
      const decodedName = fixViopContractName(decodeURIComponent(symbol));
      setLoading(true);
      getViopContracts()
        .then(contracts => {
          const found = contracts.find(c =>
            viopContractNamesMatch(c.name, decodedName)
          );
          if (found) {
            setContract(found);
            setError(null);
          } else {
            setError(t('Sözleşme bulunamadı. Lütfen listeden tekrar seçin.'));
          }
        })
        .catch(() => setError(t('Sözleşme verisi alınamadı. Lütfen daha sonra tekrar deneyin.')))
        .finally(() => setLoading(false));
    } else {
      setError(t('Lütfen sözleşme listesinden bir sözleşme seçin'));
      setLoading(false);
    }
  }, [location.state, symbol]);

  if (loading) {
    return (
      <div className="space-y-6">
        <Link to="/market/futures" className="inline-flex items-center gap-1.5 text-sm text-[#093eaa] font-semibold hover:underline">
          <ArrowLeft className="w-4 h-4" /> {t('Vadeli İşlemler')}
        </Link>
        <SkeletonDetail />
      </div>
    );
  }

  if (error) {
    return (
      <div className="space-y-6">
        <Link to="/market/futures" className="inline-flex items-center gap-1.5 text-sm text-[#093eaa] font-semibold hover:underline">
          <ArrowLeft className="w-4 h-4" /> {t('Vadeli İşlemler')}
        </Link>
        <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-6 sm:p-12">
          <div className="text-center">
            <p className="text-rose-600 font-semibold mb-2">{error}</p>
            <p className="text-sm text-gray-500">
              {t('Lütfen daha sonra tekrar deneyin veya')}{' '}
              <Link to="/market/futures" className="text-[#093eaa] hover:underline">
                {t('listeye dönün')}
              </Link>
              .
            </p>
          </div>
        </div>
      </div>
    );
  }

  if (!contract) {
    return (
      <div className="space-y-6">
        <Link to="/market/futures" className="inline-flex items-center gap-1.5 text-sm text-[#093eaa] font-semibold hover:underline">
          <ArrowLeft className="w-4 h-4" /> {t('Vadeli İşlemler')}
        </Link>
        <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-6 sm:p-12">
          <div className="text-center">
            <p className="text-rose-600 font-semibold mb-2">{t('Sözleşme verisi bulunamadı.')}</p>
            <p className="text-sm text-gray-500">
              <Link to="/market/futures" className="text-[#093eaa] hover:underline">{t('Listeye dönün')}</Link>
            </p>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-5">
      {/* Back link */}
      <Link to="/market/futures" className="inline-flex items-center gap-1.5 text-sm text-[#093eaa] font-semibold hover:underline">
        <ArrowLeft className="w-4 h-4" /> {t('Vadeli İşlemler')}
      </Link>

      {/* Kompakt header (tek satır) — Portföye Ekle + Alarm header'ın içinde */}
      <ViopContractHeader contract={contract} />

      {/* VIOP kontrat büyüklüğü bilgilendirmesi */}
      <div className="flex items-start gap-2 bg-amber-50 border border-amber-200 rounded-lg px-3 py-2">
        <svg className="w-4 h-4 text-amber-600 mt-[1px] shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
            d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
        </svg>
        <p className="text-[12px] text-amber-800 leading-snug">
          {t('VİOP sözleşmelerinde kontrat büyüklüğü ürün tipine göre değişir. Bu proje kapsamında portföy hesapları basitleştirilmiş takip amacıyla kontrat adedi ve fiyat üzerinden yapılmaktadır.')}
        </p>
      </div>

      {/* Tek 3-kolonlu grid:
          sol (2/3) → grafik + açık pozisyon + gün içi aralık
          sağ (1/3) → fiyat bilgileri + sözleşme bilgileri
          Böylece her şey tek ekranda, kaydırmadan görünür. */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-3 sm:gap-5 items-start">
        <div className="lg:col-span-2 space-y-3 sm:space-y-5">
          <ViopPriceChart contractName={contract.name} />
          <ViopOpenPositions contract={contract} />
          <ViopPriceRange contract={contract} />
        </div>
        <div className="space-y-5">
          <ViopContractStats contract={contract} />
          <ViopContractInfo contract={contract} />
        </div>
      </div>
    </div>
  );
}
