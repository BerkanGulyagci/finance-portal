import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Plus, X } from 'lucide-react';
import DashCard from './DashCard';
import StockLogo from './StockLogo';
import { getWatchlistDetailPath } from '../../portfolio';
import InstrumentSearchModal from '../../../components/instrument/InstrumentSearchModal';
import { getMarketPriceHistory } from '../../../api/marketApi';
import { fmtMoney, fmtPct, pctClass, num } from '../utils/dashUtils';
import { useTranslation } from '../../../context/LanguageContext';
import { prefGet, prefSet } from '../../../api/prefs';

function readList(key) {
  const v = prefGet(key, []);
  return Array.isArray(v) ? v : [];
}
function saveList(key, v) { prefSet(key, v); }

function Avatar({ row }) {
  if (row.logoKind === 'stock') return <StockLogo symbol={row.symbol} name={row.name} />;
  if (row.image) return <img src={row.image} alt="" className="w-7 h-7 rounded-full shrink-0" />;
  const letter = String(row.symbol || '?').charAt(0).toUpperCase();
  return (
    <span className="w-7 h-7 rounded-full bg-gray-100 text-gray-500 text-[11px] font-bold flex items-center justify-center shrink-0">
      {letter}
    </span>
  );
}

/**
 * Genel piyasa kartı (kripto / hisse / fon …) — varsayılan üst N öğe + kullanıcının
 * eklediği enstrümanlar. Eklenen öğeler kart bazında localStorage'da kalır, silinebilir.
 */
export default function MarketListCard({
  title, icon, accent = '#093eaa', to, type, logoKind = 'letter', defaultRows = [], storageKey, loading = false,
}) {
  const { t } = useTranslation();
  const [customDefs, setCustomDefs] = useState(() => readList(storageKey));
  const [customRows, setCustomRows] = useState([]);
  const [modalOpen, setModalOpen] = useState(false);

  useEffect(() => {
    let cancelled = false;
    if (!customDefs.length) { setCustomRows([]); return undefined; }
    Promise.all(customDefs.map(async d => {
      try {
        const data = await getMarketPriceHistory(d.assetType, d.symbol, '1M');
        const closes = (data?.closePrices ?? []).map(Number).filter(v => Number.isFinite(v) && v > 0);
        if (!closes.length) return { ...d, custom: true, price: null, changePct: null };
        const last = closes[closes.length - 1], first = closes[0];
        return { ...d, custom: true, price: last, changePct: first ? ((last - first) / first) * 100 : null };
      } catch { return { ...d, custom: true, price: null, changePct: null }; }
    })).then(rows => { if (!cancelled) setCustomRows(rows); });
    return () => { cancelled = true; };
  }, [customDefs]);

  function add(inst) {
    setModalOpen(false);
    setCustomDefs(prev => {
      if (prev.some(d => d.assetType === inst.assetType
        && String(d.symbol).toUpperCase() === String(inst.symbol).toUpperCase())) return prev;
      const next = [...prev, { assetType: inst.assetType, symbol: inst.symbol, name: inst.name || inst.symbol }];
      saveList(storageKey, next); return next;
    });
  }
  function remove(d) {
    setCustomDefs(prev => {
      const next = prev.filter(x => !(x.assetType === d.assetType && x.symbol === d.symbol));
      saveList(storageKey, next); return next;
    });
  }

  const defaults = defaultRows.map(r => ({ ...r, logoKind, assetType: r.assetType || type }));
  const customs = customRows.map(r => ({ ...r, logoKind: r.assetType === 'CRYPTO' ? 'letter' : logoKind }));

  const action = (
    <button
      onClick={() => setModalOpen(true)}
      className="inline-flex items-center gap-1 text-[11px] font-semibold rounded-full px-2 py-0.5 hover:bg-gray-100 transition-colors"
      style={{ color: accent }}
    >
      <Plus className="w-3.5 h-3.5" /> {t('Ekle')}
    </button>
  );

  const renderRow = (row, onRemove) => {
    const pct = row.changePct != null ? num(row.changePct) : null;
    const path = getWatchlistDetailPath(row.assetType || type, row.symbol);
    const inner = (
      <>
        <span className="flex items-center gap-2 min-w-0">
          <Avatar row={row} />
          <span className="min-w-0">
            <span className="block text-sm font-semibold text-gray-900 truncate">{row.symbol}</span>
            {row.name && row.name !== row.symbol && (
              <span className="block text-[10px] text-gray-400 truncate">{row.name}</span>
            )}
          </span>
        </span>
        <span className="text-right shrink-0">
          {row.price != null && <span className="block text-sm font-bold text-gray-800 tabular-nums">₺{fmtMoney(row.price)}</span>}
          {pct != null && <span className={`block text-[11px] font-semibold tabular-nums ${pctClass(pct)}`}>{fmtPct(pct)}</span>}
        </span>
      </>
    );
    return (
      <li key={`${row.custom ? 'c' : 'd'}:${row.assetType}:${row.symbol}`} className="group flex items-center gap-1 py-1.5">
        {path
          ? <Link to={path} className="flex items-center justify-between gap-2 flex-1 min-w-0 -mx-1 px-1 rounded-lg hover:bg-gray-50 transition-colors">{inner}</Link>
          : <div className="flex items-center justify-between gap-2 flex-1 min-w-0">{inner}</div>}
        {onRemove && (
          <button onClick={onRemove} title={t('Kaldır')} className="opacity-0 group-hover:opacity-100 transition-opacity p-0.5 rounded text-gray-300 hover:text-rose-500 shrink-0">
            <X className="w-3.5 h-3.5" />
          </button>
        )}
      </li>
    );
  };

  return (
    <DashCard title={title} icon={icon} accent={accent} to={to} action={action} scroll>
      {defaults.length === 0 && customs.length === 0 ? (
        <p className="text-sm text-gray-400 py-6 text-center">
          {loading ? t('Yükleniyor...') : t('Görüntülenecek veri yok.')}
        </p>
      ) : (
        <ul className="divide-y divide-gray-50">
          {defaults.map(r => renderRow(r, null))}
          {customs.map(r => renderRow(r, () => remove(r)))}
        </ul>
      )}
      {modalOpen && <InstrumentSearchModal portfolioName={title} initialType={type} onSelect={add} onClose={() => setModalOpen(false)} />}
    </DashCard>
  );
}
