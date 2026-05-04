import { useEffect, useState } from 'react';
import { useParams, useLocation } from 'react-router-dom';
import { getTefasFundDetail } from '../../../api/marketApi';

import FundDetailHeader        from './components/FundDetailHeader';
import FundReturnPeriods       from './components/FundReturnPeriods';
import FundPriceChart          from './components/FundPriceChart';
import FundRiskMeter           from './components/FundRiskMeter';
import FundInfoTable           from './components/FundInfoTable';
import FundDistribution        from './components/FundDistribution';
import FundPerformanceComparison from './components/FundPerformanceComparison';

function LoadingDots() {
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

export default function TefasFundDetailPage() {
  const { code } = useParams();
  const location   = useLocation();
  const [fund, setFund]       = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError]     = useState('');

  useEffect(() => {
    setLoading(true);
    setError('');
    getTefasFundDetail(code)
      .then(data => {
        if (!data) setError('Fon bilgisi bulunamadı.');
        else {
          // Liste sayfasından gelen dönem getirilerini ekle (backend rebuild olmadan)
          const listItem = location.state?.listItem;
          if (listItem) {
            if (!data.return1M && listItem.return1M != null) data.return1M = listItem.return1M;
            if (!data.return3M && listItem.return3M != null) data.return3M = listItem.return3M;
            if (!data.return6M && listItem.return6M != null) data.return6M = listItem.return6M;
            if (!data.return1Y && listItem.return1Y != null) data.return1Y = listItem.return1Y;
            if (!data.return3Y && listItem.return3Y != null) data.return3Y = listItem.return3Y;
            if (!data.return5Y && listItem.return5Y != null) data.return5Y = listItem.return5Y;
          }
          setFund(data);
        }
      })
      .catch(() => setError('Veriler yüklenemedi.'))
      .finally(() => setLoading(false));
  }, [code]); // eslint-disable-line react-hooks/exhaustive-deps

  if (loading) return <LoadingDots />;

  if (error) {
    return (
      <div className="max-w-3xl mx-auto py-12 text-center">
        <p className="text-rose-500 text-sm">{error}</p>
      </div>
    );
  }

  return (
    <div className="max-w-5xl mx-auto space-y-6">

      {/* 1. Header — logo, isim, fiyat, değişim */}
      <FundDetailHeader fund={fund} code={code} />

      {/* 2. Dönem getirileri */}
      <FundReturnPeriods fund={fund} />

      {/* 3. Fiyat grafiği */}
      <FundPriceChart code={code} />

      {/* 4. Alt satır: Risk + Fon Bilgisi */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <FundRiskMeter riskValue={fund?.riskValue} code={fund?.code ?? code} />
        <FundInfoTable fund={fund} />
      </div>

      {/* 5. Alt satır: Fon İçeriği + Performans Karşılaştırması */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <FundDistribution fund={fund} />
        <FundPerformanceComparison fund={fund} />
      </div>

      {/* 6. Disclaimer */}
      <div className="bg-amber-50 border border-amber-200 rounded-2xl p-4">
        <p className="text-xs text-amber-800 leading-relaxed">
          <span className="font-bold">⚠ Uyarı:</span> Bu sayfada yer alan veriler yatırım tavsiyesi değildir.
          Geçmiş performans gelecekteki getirilerin garantisi değildir.
          Veriler HangiKredi ve TEFAS kaynaklıdır.
        </p>
      </div>
    </div>
  );
}
