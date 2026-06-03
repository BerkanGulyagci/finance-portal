/* eslint-disable react-refresh/only-export-components -- render-helper'lar + panel component'i bir arada; fast-refresh dev uyarısı, davranışı etkilemez */
// HoldingsTable'dan çıkarılan BOND-satırı parçaları: genişletme paneli (kupon ödemeleri + Kupon Ekle)
// ve kapanmış-bond hücre render'ı. JSX/Tailwind ve mantık orijinaliyle aynı. `t` parametre olarak gelir.

import { Plus, Coins } from 'lucide-react';
import { Dash } from './HoldingsTableCells';

// ── BOND expand paneli (kupon ödemeleri + Kupon Ekle butonu) ──────────────────

export function BondExpandPanel({ coupons, canAddCoupon, onAddCoupon, t }) {
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

export function renderClosedBondCell(colKey, h, t) {
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
