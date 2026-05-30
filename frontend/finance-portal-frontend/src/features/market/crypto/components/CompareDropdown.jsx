import { useEffect, useRef, useState } from 'react';
import { ChevronDown, Plus, X } from 'lucide-react';
import { COMPARE_COLORS, POPULAR_COINS } from '../utils/cryptoChartConfig';
import { useTranslation } from '../../../../context/LanguageContext';

/** Karşılaştırma dropdown — coin arama + popüler liste, seçili çoklu chip. */
export default function CompareDropdown({ compareCoins, onAdd, onRemove, allCoins }) {
  const { t } = useTranslation();
  const [open, setOpen] = useState(false);
  const [search, setSearch] = useState('');
  const ref = useRef(null);

  useEffect(() => {
    function handleClick(e) { if (ref.current && !ref.current.contains(e.target)) setOpen(false); }
    document.addEventListener('mousedown', handleClick);
    return () => document.removeEventListener('mousedown', handleClick);
  }, []);

  const suggestions = search.trim()
    ? (allCoins || POPULAR_COINS).filter(c =>
        c.name?.toLowerCase().includes(search.toLowerCase()) ||
        c.symbol?.toLowerCase().includes(search.toLowerCase())
      ).slice(0, 8)
    : POPULAR_COINS;

  return (
    <div className="relative" ref={ref}>
      <button
        onClick={() => setOpen(o => !o)}
        className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-bold transition-all border ${
          compareCoins.length > 0
            ? 'bg-[#093eaa] text-white border-[#093eaa]'
            : 'bg-gray-100 text-gray-600 border-gray-200 hover:bg-gray-200'
        }`}
      >
        <Plus className="w-3 h-3" />
        {t('Karşılaştır')}
        {compareCoins.length > 0 && <span className="bg-white/30 rounded-full px-1">{compareCoins.length}</span>}
        <ChevronDown className={`w-3 h-3 transition-transform ${open ? 'rotate-180' : ''}`} />
      </button>

      {open && (
        <div className="absolute top-full left-0 mt-1 w-64 bg-white border border-gray-200 rounded-xl shadow-xl z-50">
          <div className="p-3 border-b border-gray-100">
            <input
              autoFocus
              type="text"
              placeholder={t('Coin ara...')}
              value={search}
              onChange={e => setSearch(e.target.value)}
              className="w-full px-3 py-1.5 bg-gray-50 border border-gray-200 rounded-lg text-xs focus:outline-none focus:ring-2 focus:ring-[#093eaa]"
            />
          </div>

          {compareCoins.length > 0 && (
            <div className="px-3 pt-2 pb-1">
              <p className="text-xs text-gray-400 mb-1.5">{t('Seçili')}</p>
              <div className="flex flex-wrap gap-1">
                {compareCoins.map((c, i) => (
                  <span key={c.id} className="flex items-center gap-1 text-xs font-bold px-2 py-0.5 rounded-full text-white"
                    style={{ backgroundColor: COMPARE_COLORS[i + 1] ?? '#6b7280' }}>
                    {c.symbol?.toUpperCase()}
                    <button onClick={() => onRemove(c.id)}><X className="w-2.5 h-2.5" /></button>
                  </span>
                ))}
              </div>
            </div>
          )}

          <div className="max-h-48 overflow-y-auto">
            {!search && <p className="text-xs text-gray-400 px-3 pt-2 pb-1">{t('Popüler')}</p>}
            {suggestions.map(c => {
              const selected = compareCoins.some(x => x.id === c.id);
              return (
                <button key={c.id} onClick={() => selected ? onRemove(c.id) : onAdd(c)}
                  className={`w-full flex items-center gap-2 px-3 py-2 text-left hover:bg-gray-50 transition-colors ${selected ? 'bg-blue-50' : ''}`}>
                  {c.image && <img src={c.image} alt="" className="w-5 h-5 rounded-full" />}
                  <span className="text-sm font-semibold text-gray-800">{c.name}</span>
                  <span className="text-xs text-gray-400 uppercase ml-auto">{c.symbol}</span>
                  {selected && <span className="text-[#093eaa] text-xs">✓</span>}
                </button>
              );
            })}
          </div>

          <div className="p-2 border-t border-gray-100">
            <button onClick={() => setOpen(false)}
              className="w-full text-xs text-gray-500 py-1 hover:text-gray-700">{t('Kapat')}</button>
          </div>
        </div>
      )}
    </div>
  );
}
