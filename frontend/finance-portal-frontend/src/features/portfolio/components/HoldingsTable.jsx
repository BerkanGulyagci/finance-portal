import { Fragment, useState, useEffect, useMemo } from 'react';
import { Link } from 'react-router-dom';
import { Settings2, ChevronDown, ChevronUp, TrendingUp, TrendingDown, Plus, Coins, Info, AlertTriangle } from 'lucide-react';
import TrendBadge from '../../../components/common/TrendBadge';
import { MASK_MONEY, MASK_PERCENT, MASK_QTY } from '../utils/portfolioFormatUtils';
import { useTranslation } from '../../../context/LanguageContext';
import CouponIncomeModal from './CouponIncomeModal';
import { renderCell, Dash } from './HoldingsTableCells';
import {
  MAX_COLS, FUTURE_DEFAULT_EXTRAS, COLS_STORAGE_KEY, ASSET_LABELS, ALL_COLS, DEFAULT_KEYS,
  GROUP_ORDER, loadSavedColumns, buildVisibleCols, columnHasValueForHolding,
} from '../utils/holdingsTableUtils';

// ── Sütun düzenleyici (inline panel) ─────────────────────────────────────────

function ColumnEditor({ open, onToggle, selected, onChange }) {
  const { t } = useTranslation();
  const [warn, setWarn] = useState(false);

  function toggle(key) {
    if (selected.includes(key)) {
      if (selected.length <= 1) return;
      onChange(selected.filter(k => k !== key));
      setWarn(false);
    } else {
      if (selected.length >= MAX_COLS) {
        setWarn(true);
        return;
      }
      onChange([...selected, key]);
      setWarn(false);
    }
  }

  const byGroup = {};
  for (const col of ALL_COLS) {
    (byGroup[col.group] ??= []).push(col);
  }

  return (
    <>
      {/* Toolbar satırı */}
      <div className="flex items-center justify-between px-4 py-2 border-b border-gray-100">
        <span className="text-xs text-gray-400">
          {t('{count} sütun görüntüleniyor', { count: selected.length })}
        </span>
        <button
          type="button"
          onClick={() => { onToggle(); setWarn(false); }}
          className={`flex items-center gap-1.5 px-3 py-1.5 text-xs font-semibold border rounded-lg transition-colors
            ${open
              ? 'bg-[#093eaa] text-white border-[#093eaa]'
              : 'text-gray-500 border-gray-200 hover:border-gray-300 hover:bg-gray-50'}`}
        >
          <Settings2 className="w-3.5 h-3.5" />
          {t('Düzenle')}
          {open ? <ChevronUp className="w-3 h-3" /> : <ChevronDown className="w-3 h-3" />}
        </button>
      </div>

      {/* Inline panel — taşmaz, overflow-hidden'dan etkilenmez */}
      {open && (
        <div className="border-b border-gray-200 bg-gray-50 px-4 py-4">
          <div className="flex items-center justify-between mb-3">
            <span className="text-sm font-bold text-gray-800">{t('Sütunları Düzenle')}</span>
            <div className="flex items-center gap-3">
              <span className="text-xs text-gray-400">
                {t('Seçili:')}{' '}
                <span className={selected.length >= MAX_COLS ? 'text-amber-600 font-bold' : 'font-semibold'}>
                  {selected.length}
                </span>{' '}
                / {MAX_COLS}
              </span>
              <button
                type="button"
                onClick={() => { onChange(DEFAULT_KEYS); setWarn(false); }}
                className="text-xs text-[#093eaa] hover:underline font-medium"
              >
                {t('Sıfırla')}
              </button>
            </div>
          </div>

          {warn && (
            <p className="text-xs text-amber-700 bg-amber-50 border border-amber-200 rounded-lg px-3 py-2 mb-3">
              {t('En fazla {max} sütun seçilebilir.', { max: MAX_COLS })}
            </p>
          )}

          {/* Gruplar — hepsi yan yana; dar ekranda az öğeli gruplar alt satıra kayar */}
          <div className="grid grid-cols-2 sm:grid-cols-4 lg:grid-cols-7 gap-x-4 gap-y-5 items-start">
            {GROUP_ORDER.map(group => {
              const cols = byGroup[group];
              if (!cols?.length) return null;
              return (
                <div key={group}>
                  <p className="text-xs font-bold text-gray-400 uppercase tracking-wider mb-2">{t(group)}</p>
                  <div className="space-y-1">
                    {cols.map(col => {
                      const checked  = selected.includes(col.key);
                      const disabled = !checked && selected.length >= MAX_COLS;
                      return (
                        <label
                          key={col.key}
                          title={col.hint ? t(col.hint) : undefined}
                          className={`flex items-start gap-2 rounded-md px-2 py-1 cursor-pointer transition-colors select-none
                            ${checked  ? 'bg-blue-50 text-blue-800' : 'hover:bg-white text-gray-700'}
                            ${disabled ? 'opacity-40 cursor-not-allowed' : ''}`}
                        >
                          <input
                            type="checkbox"
                            checked={checked}
                            disabled={disabled}
                            onChange={() => toggle(col.key)}
                            className="w-3.5 h-3.5 rounded accent-[#093eaa] shrink-0 mt-0.5"
                          />
                          <span className="leading-tight">
                            <span className="block text-xs font-medium">{t(col.label)}</span>
                            {col.hint && (
                              <span className="block text-[10px] font-normal text-gray-400 leading-tight">
                                {t(col.hint)}
                              </span>
                            )}
                          </span>
                        </label>
                      );
                    })}
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      )}
    </>
  );
}

// ── Ana bileşen ───────────────────────────────────────────────────────────────

/**
 * Props:
 *   holdings: PortfolioHoldingResponse[]
 */
export default function HoldingsTable({
  holdings = [],
  commoditySpots = {},
  valuesHidden = false,
  // Yeni props (opsiyonel — geriye uyumlu):
  transactions = [],          // BOND expand'inde kupon listesi için
  portfolioId = null,         // Kupon Ekle modalı için
  onPortfolioChanged = null,  // Kupon eklendikten sonra çağrılır (parent'tan portfolio'yu reload eder)
  onSelectedKeysChange = null, // Excel/PDF export "ekranda seçili kolonlar" — parent'a scalar selectedKeys yansıtır
}) {
  const { t } = useTranslation();
  // İlk açılışta: kaydedilmiş seçim yoksa varsayılan kümeyi al; ek olarak portföyde VİOP
  // varsa "teminat durumu" sütununu otomatik ekle (kullanıcı sonra editör'den kaldırabilir).
  const [selectedKeys, setSelectedKeys] = useState(() => {
    const saved = loadSavedColumns();
    if (saved) return saved;
    const base = [...DEFAULT_KEYS];
    const hasFuture = holdings.some(h => String(h?.assetType ?? '').toUpperCase() === 'FUTURE');
    if (hasFuture) {
      for (const k of FUTURE_DEFAULT_EXTRAS) {
        if (!base.includes(k) && base.length < MAX_COLS) base.push(k);
      }
    }
    return base;
  });
  const [editorOpen, setEditorOpen]     = useState(false);
  const [expandedKeys, setExpandedKeys] = useState(() => new Set());
  const [couponModalSymbol, setCouponModalSymbol] = useState(null);

  // Selected keys değişince parent'a yansıt (export butonlarının visibleCols'u bilmesi için).
  useEffect(() => {
    onSelectedKeysChange?.(selectedKeys);
  }, [selectedKeys, onSelectedKeysChange]);

  // Seçim değişince tarayıcıya kaydet (sonraki girişte korunur).
  useEffect(() => {
    try {
      localStorage.setItem(COLS_STORAGE_KEY, JSON.stringify(selectedKeys));
    } catch {
      /* localStorage yoksa yoksay */
    }
  }, [selectedKeys]);

  const visibleCols = buildVisibleCols(selectedKeys);

  // BOND için sembol başına COUPON_INCOME tx listesi — expand'de gösterilir
  const couponsBySymbol = useMemo(() => {
    const map = new Map();
    (transactions || []).forEach(tx => {
      if (tx?.transactionType === 'COUPON_INCOME' && tx?.symbol) {
        const list = map.get(tx.symbol) || [];
        list.push(tx);
        map.set(tx.symbol, list);
      }
    });
    // Tarihe göre yeni → eski
    for (const list of map.values()) {
      list.sort((a, b) => (b.transactionDate || '').localeCompare(a.transactionDate || ''));
    }
    return map;
  }, [transactions]);

  // Mevcut varlıkların hiçbirinde verisi olmayan seçili kolonlar — başlıkta işaretlenir
  const emptyColKeys = new Set(
    visibleCols
      .filter(c => !holdings.some(h => columnHasValueForHolding(c.key, h)))
      .map(c => c.key),
  );

  // Holding listesinde herhangi bir BOND varsa expand kolonu render edilir
  const hasAnyBond = holdings.some(h => String(h.assetType ?? '').toUpperCase() === 'BOND');

  if (!holdings.length) {
    return (
      <div className="p-12 text-center text-gray-400 text-sm">
        {t('Henüz varlık yok. "Pozisyon Ekle" butonuna basarak başlayın.')}
      </div>
    );
  }

  function toggleExpand(key) {
    setExpandedKeys(prev => {
      const next = new Set(prev);
      if (next.has(key)) next.delete(key);
      else next.add(key);
      return next;
    });
  }

  const couponHoldingForModal = couponModalSymbol
    ? holdings.find(h => h.symbol === couponModalSymbol && String(h.assetType).toUpperCase() === 'BOND')
    : null;

  return (
    <div>
      <ColumnEditor
        open={editorOpen}
        onToggle={() => setEditorOpen(v => !v)}
        selected={selectedKeys}
        onChange={setSelectedKeys}
      />

      {/* Tablo */}
      <div className="overflow-x-auto">
        <table className="w-full">
          <thead className="bg-gray-50">
            <tr>
              {hasAnyBond && (
                <th className="w-8 px-2 py-3 border-b border-gray-200" aria-label="expand" />
              )}
              {visibleCols.map(col => {
                const isEmpty = emptyColKeys.has(col.key);
                return (
                  <th
                    key={col.key}
                    title={isEmpty
                      ? t('Mevcut varlıklarda bu sütun için veri yok')
                      : (col.hint ? t(col.hint) : undefined)}
                    className={`text-left px-4 py-3 text-xs font-bold uppercase tracking-wider border-b border-gray-200 whitespace-nowrap ${isEmpty ? 'text-gray-300' : 'text-gray-500'}`}
                  >
                    {t(col.label)}
                    {isEmpty && (
                      <span className="ml-1 normal-case font-normal text-[10px] text-gray-300">
                        ({t('veri yok')})
                      </span>
                    )}
                  </th>
                );
              })}
            </tr>
          </thead>
          <tbody>
            {holdings.map(h => {
              // FUTURE için aynı sembolde LONG ve SHORT AYRI satır olur — direction'ı
              // key'e ekle ki React duplicate-key collision yapıp birini gizlemesin.
              const rowKey = h.assetType === 'FUTURE' && h.viopDirection
                ? `${h.assetType}-${h.symbol}-${h.viopDirection}`
                : `${h.assetType}-${h.symbol}`;
              const isBond = String(h.assetType ?? '').toUpperCase() === 'BOND';
              const isClosed = !!h.closed;
              const coupons = isBond ? (couponsBySymbol.get(h.symbol) || []) : [];
              // Kupon Ekle butonu: tüm açık BOND pozisyonlarında (DİBS + Eurobond) görünür.
              // Eurobond kategorisi backend'de set edilmediği için kategori filtresi kaldırıldı;
              // kuponsuz bonolarda kullanıcı zaten butona basmaz. Quantity > 0 koşulu pozisyon
              // kapanmadıysa zaten sağlanır (isClosed ile aynı kapı).
              const hasOpenQty = Number(h.totalQuantity ?? h.quantity ?? 0) > 0;
              const isCouponPaying = isBond && hasOpenQty;
              const canExpand = isBond && (coupons.length > 0 || (portfolioId && !isClosed && isCouponPaying));
              const expanded = expandedKeys.has(rowKey);
              const rowClass = isClosed
                ? 'border-t border-gray-100 bg-gray-50/60 text-gray-500'
                : 'border-t border-gray-100 hover:bg-gray-50 transition-colors';
              return (
                <Fragment key={rowKey}>
                  <tr className={rowClass}>
                    {hasAnyBond && (
                      <td className="w-8 px-2 py-3 align-middle">
                        {canExpand ? (
                          <button
                            type="button"
                            onClick={() => toggleExpand(rowKey)}
                            className="p-1 rounded hover:bg-gray-200 text-gray-500 hover:text-gray-700"
                            title={expanded ? t('Daralt') : t('Genişlet — kupon ödemelerini gör')}
                          >
                            {expanded
                              ? <ChevronUp className="w-3.5 h-3.5" />
                              : <ChevronDown className="w-3.5 h-3.5" />}
                          </button>
                        ) : null}
                      </td>
                    )}
                    {visibleCols.map(col => (
                      <td key={col.key} className="px-4 py-3">
                        {isClosed
                          ? renderClosedBondCell(col.key, h, t)
                          : renderCell(col.key, h, commoditySpots, valuesHidden, t)}
                      </td>
                    ))}
                  </tr>
                  {isBond && expanded && (
                    // "Kupon Ekle" butonu: tüm açık BOND pozisyonlarında (DİBS + Eurobond).
                    // Outer scope'taki isCouponPaying'i kullan — DRY ve eurobond için de açılır.
                    <tr className="bg-gray-50/40">
                      <td colSpan={(hasAnyBond ? 1 : 0) + visibleCols.length} className="px-4 py-3">
                        <BondExpandPanel
                          coupons={coupons}
                          canAddCoupon={!!portfolioId && !isClosed && isCouponPaying}
                          onAddCoupon={() => setCouponModalSymbol(h.symbol)}
                          t={t}
                        />
                      </td>
                    </tr>
                  )}
                </Fragment>
              );
            })}
          </tbody>
        </table>
      </div>

      {couponHoldingForModal && (
        <CouponIncomeModal
          portfolioId={portfolioId}
          holding={couponHoldingForModal}
          onClose={() => setCouponModalSymbol(null)}
          onAdded={(updated) => {
            setCouponModalSymbol(null);
            if (onPortfolioChanged) onPortfolioChanged(updated);
          }}
        />
      )}
    </div>
  );
}

// ── BOND expand paneli (kupon ödemeleri + Kupon Ekle butonu) ──────────────────

function BondExpandPanel({ coupons, canAddCoupon, onAddCoupon, t }) {
  const total = coupons.reduce((acc, c) => acc + parseFloat(c.quantity || 0), 0);
  return (
    <div className="space-y-2">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2 text-xs font-semibold text-[#434653]">
          <Coins className="w-4 h-4 text-[#093eaa]" />
          {t('Kupon Ödemeleri')}
          {coupons.length > 0 && (
            <span className="text-[#747684] font-normal">
              · {coupons.length} {t('kayıt')}
              {' · '}
              {t('Toplam')}: {total.toLocaleString('tr-TR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })} TL
            </span>
          )}
        </div>
        {canAddCoupon && (
          <button
            type="button"
            onClick={onAddCoupon}
            className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-md bg-[#093eaa] hover:bg-[#072e80] text-white text-[11px] font-bold transition-colors"
          >
            <Plus className="w-3 h-3" /> {t('Kupon Ekle')}
          </button>
        )}
      </div>
      {coupons.length === 0 ? (
        <p className="text-[11px] text-[#747684] italic">
          {t('Henüz kupon ödemesi kaydedilmedi.')}
          {canAddCoupon && ' ' + t('Bankanızdan kupon geldiğinde yukarıdaki butonla ekleyebilirsiniz.')}
        </p>
      ) : (
        <div className="bg-white border border-gray-200 rounded-md overflow-hidden">
          <table className="w-full text-xs">
            <thead className="bg-gray-50 text-[10px] uppercase text-gray-500">
              <tr>
                <th className="text-left px-3 py-1.5">{t('Tarih')}</th>
                <th className="text-right px-3 py-1.5">{t('Tutar (TL)')}</th>
              </tr>
            </thead>
            <tbody>
              {coupons.map((c, idx) => (
                <tr key={c.id ?? idx} className="border-t border-gray-100">
                  <td className="px-3 py-1.5 text-gray-700">
                    {(c.transactionDate || '').split('T')[0] || '-'}
                  </td>
                  <td className="px-3 py-1.5 text-right font-mono text-gray-900">
                    {parseFloat(c.quantity || 0).toLocaleString('tr-TR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

// ── Kapalı (vade itfası) BOND için sütun render ──────────────────────────────

function renderClosedBondCell(colKey, h, t) {
  const Dash = () => <span className="text-gray-300">—</span>;
  switch (colKey) {
    case 'name':
      return (
        <div className="flex items-center gap-2">
          <span className="text-sm font-medium text-gray-500 line-through">
            {h.name ?? h.symbol}
          </span>
          <span className="text-[10px] bg-amber-100 text-amber-700 px-1.5 py-0.5 rounded font-semibold whitespace-nowrap">
            {t('İtfa edildi')}
          </span>
        </div>
      );
    case 'symbol':
      return <span className="text-sm font-mono text-gray-500">{h.symbol}</span>;
    case 'assetType':
      return (
        <span className="text-xs bg-gray-100 text-gray-600 px-2 py-0.5 rounded-md font-semibold">
          {t('DİBS')}
        </span>
      );
    case 'totalCost':
      // Orijinal yatırılan toplam (initialCost) — kullanıcı "ne yatırdım"ı görür
      if (h.initialCost == null) return <Dash />;
      return (
        <span className="text-sm text-gray-600">
          {parseFloat(h.initialCost).toLocaleString('tr-TR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })} TL
        </span>
      );
    case 'realizedPnl': {
      if (h.realizedGainLoss == null) return <Dash />;
      const n = parseFloat(h.realizedGainLoss);
      const cls = n > 0 ? 'text-emerald-600' : n < 0 ? 'text-rose-600' : 'text-gray-600';
      return (
        <span className={`text-sm font-semibold ${cls}`}>
          {n > 0 ? '+' : ''}{n.toLocaleString('tr-TR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })} TL
        </span>
      );
    }
    case 'realizedPct': {
      if (h.realizedGainLossPercent == null) return <Dash />;
      const n = parseFloat(h.realizedGainLossPercent);
      const cls = n > 0 ? 'text-emerald-600' : n < 0 ? 'text-rose-600' : 'text-gray-600';
      return (
        <span className={`text-sm font-semibold ${cls}`}>
          {n > 0 ? '+' : ''}{n.toFixed(2).replace('.', ',')}%
        </span>
      );
    }
    default:
      return <Dash />;
  }
}
