import { useEffect, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { BarChart2 } from 'lucide-react';
import { getFxTcmb, getFxOpen, getBankCurrencyRates, getBankCurrencyRatesByCurrency } from '../../../api/marketApi.js';
import { useSortable } from '../../../hooks/useSortable.js';
import SortableTh from '../../../components/common/SortableTh.jsx';
import { FX_META, FlagImg } from '../../../utils/fxMeta.jsx';

function num(v, dec = 4) {
  return v == null ? '-' : parseFloat(v).toLocaleString('tr-TR', { minimumFractionDigits: dec, maximumFractionDigits: dec });
}

// Değere göre otomatik ondalık basamak — küçük değerler (JPY gibi) için daha fazla basamak
function numAuto(v) {
  if (v == null) return '-';
  const n = parseFloat(v);
  if (isNaN(n)) return '-';
  if (n < 1) return n.toLocaleString('tr-TR', { minimumFractionDigits: 4, maximumFractionDigits: 4 });
  return n.toLocaleString('tr-TR', { minimumFractionDigits: 4, maximumFractionDigits: 4 });
}

function numChange(v) {
  if (v == null) return '-';
  const n = parseFloat(v);
  const color = n > 0 ? 'text-emerald-600' : n < 0 ? 'text-rose-500' : 'text-gray-500';
  const sign = n > 0 ? '+' : '';
  return <span className={color}>{sign}{n.toLocaleString('tr-TR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}%</span>;
}

function formatUpdatedAt(isoStr) {
  if (!isoStr) return '-';
  try {
    return new Date(isoStr).toLocaleTimeString('tr-TR', { hour: '2-digit', minute: '2-digit' });
  } catch {
    return '-';
  }
}

const OPEN_BASES = ['USD', 'EUR', 'GBP', 'TRY'];
const BANK_CURRENCIES = ['Tümü', 'USD', 'EUR', 'GBP', 'CHF', 'JPY', 'SAR', 'NOK', 'DKK', 'AUD', 'CAD', 'SEK'];

export default function FxPage() {
  const [activeTab, setActiveTab] = useState('tcmb');
  const [searchParams] = useSearchParams();

  // URL'den tab parametresi oku (?tab=banks gibi)
  useEffect(() => {
    const tab = searchParams.get('tab');
    if (tab === 'banks' || tab === 'open' || tab === 'tcmb') {
      setActiveTab(tab);
    }
  }, [searchParams]);

  // TCMB
  const [tcmbData, setTcmbData] = useState(null);

  // Open FX
  const [openData, setOpenData] = useState(null);
  const [openBase, setOpenBase] = useState('USD');

  // Banka kurları
  const [bankRates, setBankRates] = useState([]);
  const [bankCurrency, setBankCurrency] = useState('Tümü');

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const navigate = useNavigate();

  // TCMB — sayfa açılışında yükle
  useEffect(() => {
    setLoading(true);
    getFxTcmb()
      .then(setTcmbData)
      .catch(e => setError(!e.response ? 'Sunucuya ulaşılamıyor.' : `Hata (${e.response.status})`))
      .finally(() => setLoading(false));
  }, []);

  // Open FX — sekme açıldığında yükle
  useEffect(() => {
    if (activeTab !== 'open') return;
    setLoading(true);
    setError('');
    getFxOpen(openBase)
      .then(setOpenData)
      .catch(e => setError(!e.response ? 'Sunucuya ulaşılamıyor.' : `Hata (${e.response.status})`))
      .finally(() => setLoading(false));
  }, [activeTab, openBase]);

  // Banka kurları — sekme açıldığında veya döviz filtresi değiştiğinde yükle
  useEffect(() => {
    if (activeTab !== 'banks') return;
    setLoading(true);
    setError('');
    const fetchFn = bankCurrency === 'Tümü'
      ? getBankCurrencyRates()
      : getBankCurrencyRatesByCurrency(bankCurrency);
    fetchFn
      .then(setBankRates)
      .catch(e => setError(!e.response ? 'Sunucuya ulaşılamıyor.' : `Hata (${e.response.status})`))
      .finally(() => setLoading(false));
  }, [activeTab, bankCurrency]);

  const tcmbRates = tcmbData?.rates ?? [];
  const openRates = openData?.rates ?? [];

  const { sorted: sortedTcmb, sortKey: tcmbSortKey, sortDir: tcmbSortDir, handleSort: handleTcmbSort } = useSortable(tcmbRates, 'symbol', 'asc');
  const { sorted: sortedOpen, sortKey: openSortKey, sortDir: openSortDir, handleSort: handleOpenSort } = useSortable(openRates, 'symbol', 'asc');
  const { sorted: sortedBanks, sortKey: bankSortKey, sortDir: bankSortDir, handleSort: handleBankSort } = useSortable(bankRates, 'bankName', 'asc');

  const tcmbTh = (key, label, align = 'left') => ({ label, sortKey: key, currentKey: tcmbSortKey, currentDir: tcmbSortDir, onSort: handleTcmbSort, align });
  const openTh  = (key, label, align = 'left') => ({ label, sortKey: key, currentKey: openSortKey,  currentDir: openSortDir,  onSort: handleOpenSort,  align });
  const bankTh  = (key, label, align = 'left') => ({ label, sortKey: key, currentKey: bankSortKey,  currentDir: bankSortDir,  onSort: handleBankSort,  align });

  const tabs = [
    { key: 'tcmb',  label: '🏦 TCMB Resmi Kurlar' },
    { key: 'open',  label: '🌍 Open Exchange Rates' },
    { key: 'banks', label: '🏛️ Banka Kurları' },
  ];

  return (
    <div>
      <h1 className="text-2xl font-bold text-gray-900 mb-2 border-l-4 border-[#093eaa] pl-4">Döviz Kurları</h1>
      <p className="text-sm text-gray-500 mb-6 pl-5">
        {activeTab === 'tcmb'  && 'TCMB resmi döviz kurları (günlük güncellenir) — Türk Lirası bazlı'}
        {activeTab === 'open'  && 'Open Exchange Rates — gerçek zamanlıya yakın kurlar'}
        {activeTab === 'banks' && 'Türk bankalarının anlık döviz alış/satış kurları — Hesapkurdu'}
      </p>

      {/* Tabs */}
      <div className="flex gap-2 mb-4 flex-wrap">
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
                className={`px-3 py-1.5 rounded-lg text-sm font-bold transition-all border flex items-center gap-1.5 ${
                  openBase === b ? 'bg-[#093eaa] text-white border-[#093eaa]' : 'bg-white text-gray-600 border-gray-200 hover:bg-gray-50'
                }`}>
                <FlagImg cc={FX_META[b]?.cc} size={16} /> {b}
              </button>
            ))}
          </div>
        </div>
      )}

      {/* Banka kurları döviz filtresi */}
      {activeTab === 'banks' && (
        <div className="flex items-center gap-3 mb-4 flex-wrap">
          <span className="text-sm text-gray-500 font-semibold">Döviz:</span>
          <div className="flex gap-2 flex-wrap">
            {BANK_CURRENCIES.map(c => (
              <button key={c} onClick={() => setBankCurrency(c)}
                className={`px-3 py-1.5 rounded-lg text-sm font-bold transition-all border ${
                  bankCurrency === c ? 'bg-[#093eaa] text-white border-[#093eaa]' : 'bg-white text-gray-600 border-gray-200 hover:bg-gray-50'
                }`}>
                {c}
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
                <span>Kaynak: TCMB · Baz: TRY · {tcmbRates.length} döviz</span>
                <span>{tcmbData.asOf}</span>
              </>
            )}
            {activeTab === 'open' && openData && (
              <>
                <span>Kaynak: Open Exchange Rates · Baz: {openData.base}</span>
                <span>{openData.asOf}</span>
              </>
            )}
            {activeTab === 'banks' && (
              <span>Kaynak: Hesapkurdu · {sortedBanks.length} kayıt{bankCurrency !== 'Tümü' ? ` · ${bankCurrency}` : ''}</span>
            )}
          </div>
        )}

        {loading && (
          <div className="p-8 flex justify-center gap-1.5">
            <div className="w-2 h-2 bg-[#093eaa] rounded-full animate-bounce" />
            <div className="w-2 h-2 bg-[#093eaa]/60 rounded-full animate-bounce [animation-delay:100ms]" />
            <div className="w-2 h-2 bg-[#093eaa]/30 rounded-full animate-bounce [animation-delay:200ms]" />
          </div>
        )}
        {error && <div className="p-6 text-rose-500 text-sm">{error}</div>}

        {/* TCMB Table */}
        {!loading && !error && activeTab === 'tcmb' && tcmbData?.rates && (
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead className="bg-gray-50">
                <tr>
                  <SortableTh {...tcmbTh('symbol', 'Döviz Kodu')} />
                  <th className="text-left px-4 py-3 text-xs font-bold text-gray-500 uppercase tracking-wider border-b border-gray-200">
                    Döviz Cinsi
                  </th>
                  <SortableTh {...tcmbTh('unit', 'Birim', 'right')} />
                  <SortableTh {...tcmbTh('buy', 'Alış', 'right')} />
                  <SortableTh {...tcmbTh('sell', 'Satış', 'right')} />
                  <th className="px-4 py-3 border-b border-gray-200 w-8" />
                  <th className="px-4 py-3 border-b border-gray-200 w-10" />
                </tr>
              </thead>
              <tbody>
                {sortedTcmb.map((r, i) => {
                  const meta = FX_META[r.symbol];
                  return (
                    <tr
                      key={r.symbol}
                      onClick={() => navigate('/market/fx/' + r.symbol)}
                      className={`border-t border-gray-100 hover:bg-blue-50 transition-colors cursor-pointer ${i % 2 === 0 ? '' : 'bg-gray-50/30'}`}
                    >
                      <td className="px-4 py-3">
                        <div className="flex items-center gap-2">
                          <FlagImg cc={meta?.cc} />
                          <div>
                            <Link
                              to={`/market/fx/${r.symbol}`}
                              onClick={e => e.stopPropagation()}
                              className="font-bold text-[#093eaa] text-sm hover:underline"
                            >
                              {r.symbol}/TRY
                            </Link>
                          </div>
                        </div>
                      </td>
                      <td className="px-4 py-3 text-sm text-gray-600">{meta?.ad ?? r.symbol}</td>
                      <td className="px-4 py-3 text-sm text-gray-400 text-right">{r.unit ?? 1}</td>
                      <td className="px-4 py-3 text-sm font-semibold text-gray-900 text-right">{num(r.buy)}</td>
                      <td className="px-4 py-3 text-sm font-semibold text-gray-900 text-right">{num(r.sell)}</td>
                      <td className="px-4 py-3 text-gray-300 text-right text-sm">→</td>
                      <td className="px-2 py-3">
                        <Link
                          to={`/market/compare?symbols=${r.symbol}`}
                          onClick={e => e.stopPropagation()}
                          title="Karşılaştır"
                          className="p-1.5 rounded-lg bg-gray-100 hover:bg-[#093eaa] hover:text-white text-gray-400 transition-all inline-flex"
                        >
                          <BarChart2 className="w-3.5 h-3.5" />
                        </Link>
                      </td>
                    </tr>
                  );
                })}
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
                  <SortableTh {...openTh('symbol', 'Döviz')} />
                  <th className="text-left px-4 py-3 text-xs font-bold text-gray-500 uppercase tracking-wider border-b border-gray-200">
                    Döviz Cinsi
                  </th>
                  <SortableTh {...openTh('sell', 'Kur', 'right')} />
                </tr>
              </thead>
              <tbody>
                {sortedOpen.map((r, i) => {
                  const meta = FX_META[r.symbol];
                  return (
                    <tr key={r.symbol} className={`border-t border-gray-100 hover:bg-gray-50 transition-colors ${i % 2 === 0 ? '' : 'bg-gray-50/30'}`}>
                      <td className="px-4 py-3">
                        <div className="flex items-center gap-2">
                          <FlagImg cc={meta?.cc} />
                          <span className="font-bold text-[#093eaa] text-sm">{r.symbol}</span>
                        </div>
                      </td>
                      <td className="px-4 py-3 text-sm text-gray-600">{meta?.ad ?? r.symbol}</td>
                      <td className="px-4 py-3 text-sm font-semibold text-gray-900 text-right">{num(r.sell ?? r.buy, 6)}</td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}

        {/* Banka Kurları Table */}
        {!loading && !error && activeTab === 'banks' && (
          <>
            {sortedBanks.length === 0 ? (
              <div className="p-8 text-center text-gray-400 text-sm">Veri bulunamadı.</div>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full">
                  <thead className="bg-gray-50">
                    <tr>
                      <SortableTh {...bankTh('bankName', 'Banka')} />
                      <SortableTh {...bankTh('currencyCode', 'Döviz')} />
                      <SortableTh {...bankTh('buyRate', 'Alış', 'right')} />
                      <SortableTh {...bankTh('sellRate', 'Satış', 'right')} />
                      <SortableTh {...bankTh('spread', 'Makas', 'right')} />
                      <SortableTh {...bankTh('dailyChangePercent', 'Günlük %', 'right')} />
                      <th className="text-right px-4 py-3 text-xs font-bold text-gray-500 uppercase tracking-wider border-b border-gray-200">
                        Son Güncelleme
                      </th>
                    </tr>
                  </thead>
                  <tbody>
                    {sortedBanks.map((r, i) => {
                      const meta = FX_META[r.currencyCode];
                      return (
                        <tr
                          key={`${r.bankName}-${r.currencyCode}`}
                          className={`border-t border-gray-100 hover:bg-blue-50 transition-colors ${i % 2 === 0 ? '' : 'bg-gray-50/30'}`}
                        >
                          {/* Banka */}
                          <td className="px-4 py-3">
                            <span className="font-semibold text-gray-800 text-sm">{r.bankName ?? '-'}</span>
                          </td>
                          {/* Döviz */}
                          <td className="px-4 py-3">
                            <div className="flex items-center gap-2">
                              <FlagImg cc={meta?.cc} />
                              <div>
                                <span className="font-bold text-[#093eaa] text-sm">{r.currencyCode}</span>
                                <span className="text-xs text-gray-400 ml-1">{r.currencyName}</span>
                              </div>
                            </div>
                          </td>
                          {/* Alış */}
                          <td className="px-4 py-3 text-sm font-semibold text-gray-900 text-right">
                            {numAuto(r.buyRate)}
                          </td>
                          {/* Satış */}
                          <td className="px-4 py-3 text-sm font-semibold text-gray-900 text-right">
                            {numAuto(r.sellRate)}
                          </td>
                          {/* Makas */}
                          <td className="px-4 py-3 text-sm text-gray-500 text-right">
                            {numAuto(r.spread)}
                          </td>
                          {/* Günlük % */}
                          <td className="px-4 py-3 text-sm text-right">
                            {numChange(r.dailyChangePercent)}
                          </td>
                          {/* Son Güncelleme */}
                          <td className="px-4 py-3 text-xs text-gray-400 text-right">
                            {formatUpdatedAt(r.sourceUpdatedAt)}
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
                {/* JPY uyarısı */}
                {(bankCurrency === 'JPY' || bankCurrency === 'Tümü') && sortedBanks.some(r => r.currencyCode === 'JPY') && (
                  <div className="px-4 py-2 bg-amber-50 border-t border-amber-100 text-xs text-amber-700">
                    ⚠️ JPY kurları bankaya göre farklı birimde gösterilebilir: bazı bankalar 1 JPY, bazıları 100 JPY bazında fiyatlar.
                  </div>
                )}
              </div>
            )}
          </>
        )}
      </div>
    </div>
  );
}
