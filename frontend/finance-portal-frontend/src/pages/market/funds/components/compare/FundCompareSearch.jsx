import { useState, useMemo, useRef, useEffect } from 'react';
import { Search, X } from 'lucide-react';
import { COMPARE_COLORS } from './compareUtils';

const MAX_FUNDS = 4;

export default function FundCompareSearch({ allFunds, selected, onAdd, onRemove, onCompare, loading, range, onRangeChange }) {
  const [query, setQuery]       = useState('');
  const [open, setOpen]         = useState(false);
  const inputRef = useRef(null);
  const dropRef  = useRef(null);

  const RANGES = [
    { key: '1M', label: '1 Ay' },
    { key: '3M', label: '3 Ay' },
    { key: '6M', label: '6 Ay' },
    { key: '1Y', label: '1 Yıl' },
  ];

  const suggestions = useMemo(() => {
    if (!query.trim()) return [];
    const q = query.toLowerCase();
    return allFunds
      .filter(f =>
        (f.code?.toLowerCase().includes(q) || f.name?.toLowerCase().includes(q)) &&
        !selected.includes(f.code)
      )
      .slice(0, 8);
  }, [query, allFunds, selected]);

  // Dışarı tıklayınca kapat
  useEffect(() => {
    function handler(e) {
      if (dropRef.current && !dropRef.current.contains(e.target)) setOpen(false);
    }
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, []);

  function handleSelect(code) {
    if (selected.length >= MAX_FUNDS) return;
    onAdd(code);
    setQuery('');
    setOpen(false);
  }

  function handleKeyDown(e) {
    if (e.key === 'Enter' && suggestions.length > 0) handleSelect(suggestions[0].code);
    if (e.key === 'Escape') { setOpen(false); setQuery(''); }
  }

  return (
    <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-5 space-y-4">
      {/* Seçili fonlar */}
      <div>
        <p className="text-xs font-bold text-gray-500 uppercase tracking-wider mb-2.5">
          Fon Seç ({selected.length}/{MAX_FUNDS})
        </p>
        <div className="flex flex-wrap gap-2 min-h-[36px] items-center">
          {selected.map((code, idx) => {
            const fund = allFunds.find(f => f.code === code);
            return (
              <span key={code}
                className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full text-sm font-semibold text-white"
                style={{ backgroundColor: COMPARE_COLORS[idx % COMPARE_COLORS.length] }}>
                <span className="font-mono">{code}</span>
                {fund?.name && (
                  <span className="text-white/80 text-xs max-w-[120px] truncate hidden sm:inline">
                    {fund.name.length > 20 ? fund.name.slice(0, 20) + '…' : fund.name}
                  </span>
                )}
                <button onClick={() => onRemove(code)} className="ml-0.5 hover:opacity-70 transition-opacity">
                  <X className="w-3.5 h-3.5" />
                </button>
              </span>
            );
          })}

          {/* Arama input */}
          {selected.length < MAX_FUNDS && (
            <div className="relative" ref={dropRef}>
              <div className="flex items-center gap-1.5 px-3 py-1.5 rounded-full border-2 border-dashed border-gray-300 hover:border-[#093eaa] transition-colors">
                <Search className="w-3.5 h-3.5 text-gray-400" />
                <input
                  ref={inputRef}
                  value={query}
                  onChange={e => { setQuery(e.target.value); setOpen(true); }}
                  onFocus={() => setOpen(true)}
                  onKeyDown={handleKeyDown}
                  placeholder="Kod veya isim ara..."
                  className="text-sm text-gray-700 bg-transparent outline-none w-40 placeholder-gray-400"
                />
                {query && (
                  <button onClick={() => { setQuery(''); setOpen(false); }}>
                    <X className="w-3 h-3 text-gray-400 hover:text-gray-600" />
                  </button>
                )}
              </div>

              {/* Dropdown */}
              {open && suggestions.length > 0 && (
                <div className="absolute top-full left-0 mt-1 z-50 bg-white border border-gray-200 rounded-xl shadow-xl w-72 max-h-64 overflow-y-auto">
                  {suggestions.map(f => (
                    <button key={f.code} onClick={() => handleSelect(f.code)}
                      className="w-full flex items-start gap-3 px-4 py-2.5 hover:bg-blue-50 transition-colors text-left">
                      <span className="font-bold text-[#093eaa] text-sm font-mono w-12 shrink-0">{f.code}</span>
                      <span className="text-sm text-gray-700 leading-tight line-clamp-2">{f.name}</span>
                    </button>
                  ))}
                </div>
              )}
            </div>
          )}
        </div>
      </div>

      {/* Range + Karşılaştır */}
      <div className="flex items-center gap-3 flex-wrap pt-1 border-t border-gray-100">
        <div className="flex gap-1">
          {RANGES.map(r => (
            <button key={r.key} onClick={() => onRangeChange(r.key)}
              className={`px-3 py-1.5 rounded-lg text-xs font-bold transition-all ${
                range === r.key ? 'bg-[#093eaa] text-white' : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
              }`}>
              {r.label}
            </button>
          ))}
        </div>
        <button
          onClick={onCompare}
          disabled={loading || selected.length < 2}
          className="ml-auto px-5 py-2 bg-[#093eaa] text-white rounded-xl text-sm font-bold hover:bg-[#0730a0] transition-all disabled:opacity-50 flex items-center gap-2">
          {loading ? (
            <>
              <div className="w-3.5 h-3.5 border-2 border-white/30 border-t-white rounded-full animate-spin" />
              Yükleniyor...
            </>
          ) : (
            'Karşılaştır'
          )}
        </button>
      </div>
    </div>
  );
}
