import { useEffect, useState } from 'react';
import { useParams, useLocation, Link, useNavigate } from 'react-router-dom';
import { ArrowLeft } from 'lucide-react';
import ViopContractHeader from './components/ViopContractHeader';
import ViopContractStats from './components/ViopContractStats';
import ViopPriceRange from './components/ViopPriceRange';
import ViopOpenPositions from './components/ViopOpenPositions';
import ViopContractInfo from './components/ViopContractInfo';

export default function FuturesDetailPage() {
  const { symbol } = useParams();
  const location = useLocation();
  const navigate = useNavigate();
  const [contract, setContract] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    // React Router state'inden contract verisini al
    if (location.state?.contract) {
      setContract(location.state.contract);
      setLoading(false);
      setError(null);
    } else {
      // State yoksa (direkt URL ile gelindiyse), listeye yönlendir
      setError('Lütfen sözleşme listesinden bir sözleşme seçin');
      setLoading(false);
    }
  }, [location.state, symbol, navigate]);

  if (loading) {
    return (
      <div className="space-y-6">
        <Link to="/market/futures" className="inline-flex items-center gap-1.5 text-sm text-[#093eaa] font-semibold hover:underline">
          <ArrowLeft className="w-4 h-4" /> Vadeli İşlemler
        </Link>
        <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-12 flex items-center justify-center">
          <div className="flex gap-2">
            <div className="w-3 h-3 bg-[#093eaa] rounded-full animate-bounce" />
            <div className="w-3 h-3 bg-[#093eaa]/60 rounded-full animate-bounce [animation-delay:100ms]" />
            <div className="w-3 h-3 bg-[#093eaa]/30 rounded-full animate-bounce [animation-delay:200ms]" />
          </div>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="space-y-6">
        <Link to="/market/futures" className="inline-flex items-center gap-1.5 text-sm text-[#093eaa] font-semibold hover:underline">
          <ArrowLeft className="w-4 h-4" /> Vadeli İşlemler
        </Link>
        <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-12">
          <div className="text-center">
            <p className="text-rose-600 font-semibold mb-2">{error}</p>
            <p className="text-sm text-gray-500">
              Lütfen daha sonra tekrar deneyin veya{' '}
              <Link to="/market/futures" className="text-[#093eaa] hover:underline">
                listeye dönün
              </Link>
              .
            </p>
          </div>
        </div>
      </div>
    );
  }

  if (!contract) {
    return null;
  }

  return (
    <div className="space-y-6">
      {/* Back link */}
      <Link to="/market/futures" className="inline-flex items-center gap-1.5 text-sm text-[#093eaa] font-semibold hover:underline">
        <ArrowLeft className="w-4 h-4" /> Vadeli İşlemler
      </Link>

      {/* Header */}
      <ViopContractHeader contract={contract} />

      {/* Main content grid */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Sol kolon - Fiyat Bilgileri (2/3 genişlik) */}
        <div className="lg:col-span-2 space-y-6">
          <ViopContractStats contract={contract} />
          <ViopPriceRange contract={contract} />
        </div>

        {/* Sağ kolon - Bilgiler (1/3 genişlik) */}
        <div className="space-y-6">
          <ViopOpenPositions contract={contract} />
          <ViopContractInfo contract={contract} />
        </div>
      </div>
    </div>
  );
}
