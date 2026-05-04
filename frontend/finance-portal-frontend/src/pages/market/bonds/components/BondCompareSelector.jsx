import { useState, useEffect, useRef } from 'react';
import { Plus, X, ChevronDown } from 'lucide-react';
import { getEvdsBonds } from '../../../../api/marketApi';

/**
 * EVDS kıymet karşılaştırma seçici.
 * Kullanıcı listeden bir kıymet seçer; aynı kıymet ve ana kıymet seçilemez.
 *
 * Props:
 *   mainCode       — ana kıymet kodu (seçilemez)
 *   compareCode    — seçili karşılaştırma kodu (null = seçilmedi)
 *   onSelect(code) — seçim callback'i
 *   onClear()      — temizleme callback'i
 */
export default function BondCompareSelector({ mainCode, compareCode, onSelect, onClear }) {
  const [open, setOpen]       = useState(false);
  const [search, setSearch]   = useState('');
  const [bonds, setBonds]     = useState([]);
  const [loading, setLoading] = useState(false);
  const ref = useRef(null);

  // Dışarı tıklayınca kapat
  useEffect(() => {
    function handleClick(e) {
      if (ref.current && !ref.current.contains(e.target)) setOpen(false);
    }
    document.addEventListener('mousedown', handleClick);
    return () => document.removeEventListener('mousedown', handleClick);
  }, []);

  // Dropdown açılınca ilk listeyi çek (size=100, tüm aktif kıymetler için yeterli)
  useEffect(() => {
    if (!open || bonds.length > 0) return;
    setLoading(true);
    getEvdsBonds({ size: 100, sortBy: 'maturityDate', sortDir: 'asc' })
      .then(data => setBonds(data.items ?? []))
      .catch(() => setBonds([]))
      .finally(() => setLoading(false));
  }, [open, bonds.length]);

  const filtered = bonds.filter(b => {
    if (b.instrumentCode === mainCode) return false; // ana kıymet hariç
    if (!search.trim()) return true;
    const q = search.toLowerCase();
    return (
      b.instrumentCode?.toLowerCase().includes(q) ||
      b.type?.toLowerCase().includes(q) ||
      b.maturityDate?.includes(q)
    );
  });

  return (
    <div className="relative" ref={ref}>
      {/* Trigger butonu */}
      {compareCode ? (
        <div className="flex items-center gap-1">
          <span className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-bold bg-orange-500 text-white">
            <span className="inline-block w-2 h-2 rounded-full bg-white/60" />
            {compareCode}
          </span>
          <button
            onClick={onClear}
            className="p-1.5 rounded-lg bg-gray-100 hover:bg-rose-100 hover:text-rose-600 transition-all"
            title="Karşılaştırmayı kaldır"
          >
            <X className="w-3.5 h-3.5" />
          </button>
        </div>
      ) : (
        <button
          onClick={() => setOpen(o => !o)}
          className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-bold border border-gray-200 bg-gray-50 text-gray-600 hover:bg-gray-100 transition-all"
        >
          <Plus className="w-3 h-3" />
          Karşılaştır
          <ChevronDown className={`w-3 h-3 transition-transform ${open ? 'rotate-180' : ''}`} />
        </button>
      )}

      {/* Dropdown */}
      {open && !compareCode && (
        <div className="absolute top-full left-0 mt-1 w-72 bg-white border border-gray-200 rounded-xl shadow-xl z-50">
          {/* Arama */}
          <div className="p-3 border-b border-gray-100">
            <input
              autoFocus
              type="text"
              placeholder="Kıymet kodu veya tür ara..."
              value={search}
              onChange={e => setSearch(e.target.value)}
              className="w-full px-3 py-1.5 bg-gray-50 border border-gray-200 rounded-lg text-xs focus:outline-none focus:ring-2 focus:ring-[#093eaa]/30"
            />
          </div>

          {/* Liste */}
          <div className="max-h-56 overflow-y-auto">
            {loading && (
              <div className="flex items-center justify-center py-6 gap-1.5">
                <div className="w-1.5 h-1.5 bg-[#093eaa] rounded-full animate-bounce" />
                <div className="w-1.5 h-1.5 bg-[#093eaa]/60 rounded-full animate-bounce [animation-delay:100ms]" />
                <div className="w-1.5 h-1.5 bg-[#093eaa]/30 rounded-full animate-bounce [animation-delay:200ms]" />
              </div>
            )}
            {!loading && filtered.length === 0 && (
              <p className="text-xs text-gray-400 text-center py-4">Kıymet bulunamadı.</p>
            )}
            {!loading && filtered.map(b => (
              <button
                key={b.instrumentCode}
                onClick={() => { onSelect(b.instrumentCode); setOpen(false); setSearch(''); }}
                className="w-full flex items-center justify-between gap-2 px-3 py-2.5 text-left hover:bg-blue-50 transition-colors border-b border-gray-50 last:border-0"
              >
                <div>
                  <p className="text-sm font-bold text-[#093eaa] font-mono">{b.instrumentCode}</p>
                  <p className="text-xs text-gray-400">{b.type} · Vade: {b.maturityDate ?? '-'}</p>
                </div>
                <div className="text-right shrink-0">
                  <p className="text-xs font-semibold text-gray-700">{b.indicatorValue != null ? parseFloat(b.indicatorValue).toLocaleString('tr-TR', { minimumFractionDigits: 2, maximumFractionDigits: 2 }) : '-'}</p>
                  <p className="text-xs text-gray-400">{b.remainingDays != null ? `${b.remainingDays}g` : ''}</p>
                </div>
              </button>
            ))}
          </div>

          <div className="p-2 border-t border-gray-100">
            <button onClick={() => setOpen(false)}
              className="w-full text-xs text-gray-400 py-1 hover:text-gray-600">
              Kapat
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
