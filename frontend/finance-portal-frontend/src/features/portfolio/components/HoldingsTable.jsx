import { Fragment, useState, useEffect, useMemo } from 'react';
import { ChevronDown, ChevronUp } from 'lucide-react';
import { useTranslation } from '../../../context/LanguageContext';
import CouponIncomeModal from './CouponIncomeModal';
import { renderCell } from './HoldingsTableCells';
import ColumnEditor from './ColumnEditor';
import { BondExpandPanel, renderClosedBondCell } from './HoldingsBondCells';
import {
  MAX_COLS, FUTURE_DEFAULT_EXTRAS, COLS_STORAGE_KEY, DEFAULT_KEYS,
  GROUP_ORDER, loadSavedColumns, buildVisibleCols, columnHasValueForHolding,
} from '../utils/holdingsTableUtils';
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
