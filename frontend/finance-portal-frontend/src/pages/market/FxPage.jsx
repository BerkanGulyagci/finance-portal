import { useEffect, useState } from 'react';
import { getFxTcmb, getFxOpen } from '../../api/marketApi';

function num(v, dec = 4) {
  return v == null ? '-' : parseFloat(v).toLocaleString('tr-TR', { minimumFractionDigits: dec, maximumFractionDigits: dec });
}

const OPEN_BASES = ['USD', 'EUR', 'GBP', 'TRY'];

export default function FxPage() {
  const [activeTab, setActiveTab] = useState('tcmb');
  const [tcmbData, setTcmbData] = useState(null);
  const [openData, setOpenData] = useState(null);
  const [openBase, setOpenBase] = useState('USD');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  // Load TCMB on mount
  useEffect(() => {
    setLoading(true);
    getFxTcmb()
      .then(setTcmbData)
      .catch(e => setError(!e.response ? 'Sunucuya ulaşılamıyor.' : `Hata (${e.response.status})`))
      .finally(() => setLoading(false));
  }, []);

  // Load Open FX when tab switches or base changes
  useEffect(() => {
    if (activeTab !== 'open') return;
    setLoading(true);
    setError('');
    getFxOpen(openBase)
      .then(setOpenData)
      .catch(e => setError(!e.response ? 'Sunucuya ulaşılamıyor.' : `Hata (${e.response.status})`))
      .finally(() => setLoading(false));
  }, [activeTab, openBase]);

  const tabs = [
    { key: 'tcmb', label: '🏦 TCMB Resmi Kurlar' },
    { key: 'open', label: '🌍 Open Exchange Rates' },
  ];

  return (
    <div>
      <h1 className="text-2xl font-bold text-gray-900 mb-2 border-l-4 border-[#093eaa] pl-4">Döviz Kurları</h1>
      <p className="text-sm text-gray-500 mb-6 pl-5">
        {activeTab === 'tcmb'
          ? 'TCMB resmi döviz kurları (günlük güncellenir)'
          : 'Open Exchange Rates — gerçek zamanlıya yakın kurlar'}
      </p>

      {/* Source tabs */}
      <div className="flex gap-2 mb-4">
        {tabs.map(t => (
          <button key={t.key} onClick={() => setActiveTab(t.key)}
            className={`px-4 py-2 rounded-xl text-sm font-semibold transition-all ${
              activeTab === t.key ? 'bg-[#093eaa] text-white' : 'bg-white text-gray-600 border border-gray-200 hover:bg-gray-50'
            }`}>
            {t.label}
          </button>
        ))}
      </div>

      {/* Open FX base selector */}
      {activeTab === 'open' && (
        <div className="flex items-center gap-3 mb-4">
          <span className="text-sm text-gray-500 font-semibold">Baz Para Birimi:</span>
          <div className="flex gap-2">
            {OPEN_BASES.map(b => (
              <button key={b} onClick={() => setOpenBase(b)}
                className={`px-3 py-1.5 rounded-lg text-sm font-bold transition-all border ${
                  openBase === b ? 'bg-[#093eaa] text-white border-[#093eaa]' : 'bg-white text-gray-600 border-gray-200 hover:bg-gray-50'
                }`}>
                {b}
              </button>
            ))}
          </div>
        </div>
      )}

      <div className="bg-white rounded-2xl border border-gray-200 shadow-sm overflow-hidden">
        {/* Meta info */}
        {!loading && !error && (
          <div className="px-6 py-3 border-b border-gray-100 bg-gray-50 text-xs text-gray-500 flex items-center justify-between">
            {activeTab === 'tcmb' && tcmbData && (
              <>
                <span>Kaynak: {tcmbData.provider} · Baz: {tcmbData.base}</span>
                <span>{tcmbData.asOf}</span>
              </>
            )}
            {activeTab === 'open' && openData && (
              <>
                <span>Kaynak: {openData.provider} · Baz: {openData.base}</span>
                <span>{openData.asOf}</span>
              </>
            )}
          </div>
        )}

        {loading && <div className="p-8 text-center text-gray-400 text-sm">Yükleniyor...</div>}
        {error && <div className="p-6 text-rose-500 text-sm">{error}</div>}

        {/* TCMB Table */}
        {!loading && !error && activeTab === 'tcmb' && tcmbData?.rates && (
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead className="bg-gray-50">
                <tr>
                  {['Döviz', 'Alış', 'Satış', 'Birim'].map(h =>
                    <th key={h} className="text-left px-6 py-3 text-xs font-bold text-gray-500 uppercase tracking-wider border-b border-gray-200">{h}</th>
                  )}
                </tr>
              </thead>
              <tbody>
                {tcmbData.rates.map((r, i) => (
                  <tr key={r.symbol} className={`border-t border-gray-100 hover:bg-gray-50 transition-colors ${i % 2 === 0 ? '' : 'bg-gray-50/30'}`}>
                    <td className="px-6 py-3 font-bold text-[#093eaa] text-sm">{r.symbol}</td>
                    <td className="px-6 py-3 text-sm font-semibold text-gray-900">{num(r.buy)}</td>
                    <td className="px-6 py-3 text-sm font-semibold text-gray-900">{num(r.sell)}</td>
                    <td className="px-6 py-3 text-sm text-gray-400">{r.unit}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        {/* Open FX Table */}
        {!loading && !error && activeTab === 'open' && openData?.rates && (
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead className="bg-gray-50">
                <tr>
                  {['Döviz', 'Kur', 'Birim'].map(h =>
                    <th key={h} className="text-left px-6 py-3 text-xs font-bold text-gray-500 uppercase tracking-wider border-b border-gray-200">{h}</th>
                  )}
                </tr>
              </thead>
              <tbody>
                {openData.rates.map((r, i) => (
                  <tr key={r.symbol} className={`border-t border-gray-100 hover:bg-gray-50 transition-colors ${i % 2 === 0 ? '' : 'bg-gray-50/30'}`}>
                    <td className="px-6 py-3 font-bold text-[#093eaa] text-sm">{r.symbol}</td>
                    <td className="px-6 py-3 text-sm font-semibold text-gray-900">{num(r.sell ?? r.buy, 6)}</td>
                    <td className="px-6 py-3 text-sm text-gray-400">{r.unit}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}
