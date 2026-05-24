import { useState } from 'react';
import { LineChart, Plus, X } from 'lucide-react';
import {
  TICKER_CATALOG, readTickerPrefs, saveTickerPrefs,
  readCustomTickerItems, saveCustomTickerItems,
} from '../../utils/tickerPrefs';
import { useTranslation } from '../../i18n/LanguageContext';
import InstrumentSearchModal from '../../pages/portfolio/components/InstrumentSearchModal';

const ASSET_LABEL = {
  STOCK: 'Hisse', FUND: 'Fon', FX: 'Döviz', FUTURE: 'Vadeli',
  CRYPTO: 'Kripto', GOLD: 'Altın', COMMODITY: 'Emtia', BOND: 'Tahvil',
};

/**
 * Hesap Ayarları'nda piyasa şeridi (ticker) özelleştirme: kullanıcı hangi varlıkların
 * üstteki şeritte görüneceğini seçer. Tercih localStorage'a yazılır, MarketTicker anında okur.
 */
export default function TickerCustomizer() {
  const { t } = useTranslation();
  const [enabled, setEnabled] = useState(() => readTickerPrefs());
  const [custom, setCustom] = useState(() => readCustomTickerItems());
  const [searchOpen, setSearchOpen] = useState(false);

  function commit(next) {
    setEnabled(next);
    saveTickerPrefs(next);
  }

  function addCustom(inst) {
    setSearchOpen(false);
    setCustom(prev => {
      if (prev.some(c => c.assetType === inst.assetType && c.symbol === inst.symbol)) return prev;
      const next = [...prev, { assetType: inst.assetType, symbol: inst.symbol, name: inst.name || inst.symbol }];
      saveCustomTickerItems(next);
      return next;
    });
  }

  function removeCustom(assetType, symbol) {
    setCustom(prev => {
      const next = prev.filter(c => !(c.assetType === assetType && c.symbol === symbol));
      saveCustomTickerItems(next);
      return next;
    });
  }

  function toggle(key) {
    const next = new Set(enabled);
    if (next.has(key)) next.delete(key); else next.add(key);
    commit(next);
  }

  function toggleGroup(items, allOn) {
    const next = new Set(enabled);
    items.forEach(it => { if (allOn) next.delete(it.key); else next.add(it.key); });
    commit(next);
  }

  return (
    <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-6 mb-6">
      <h2 className="font-bold text-gray-900 mb-1 flex items-center gap-2">
        <LineChart className="w-5 h-5 text-[#093eaa]" /> {t('Piyasa Şeridini Özelleştir')}
      </h2>
      <p className="text-xs text-gray-400 mb-4">
        {t('Sayfanın üstündeki piyasa şeridinde hangi varlıkların görüneceğini seçin.')}
      </p>

      <div className="grid sm:grid-cols-2 gap-4">
        {TICKER_CATALOG.map(group => {
          const allOn = group.items.every(it => enabled.has(it.key));
          return (
            <div key={group.group} className="border border-gray-100 rounded-xl p-3">
              <div className="flex items-center justify-between mb-2">
                <span className="text-xs font-bold text-gray-500 uppercase tracking-wider">{t(group.group)}</span>
                <button
                  type="button"
                  onClick={() => toggleGroup(group.items, allOn)}
                  className="text-[11px] font-semibold text-[#093eaa] hover:underline"
                >
                  {allOn ? t('Hiçbiri') : t('Tümü')}
                </button>
              </div>
              <div className="space-y-1.5">
                {group.items.map(it => (
                  <label key={it.key} className="flex items-center gap-2 cursor-pointer text-sm text-gray-700 hover:text-gray-900">
                    <input
                      type="checkbox"
                      checked={enabled.has(it.key)}
                      onChange={() => toggle(it.key)}
                      className="rounded border-gray-300 text-[#093eaa] focus:ring-2 focus:ring-[#093eaa]/30"
                    />
                    {it.label}
                  </label>
                ))}
              </div>
            </div>
          );
        })}
      </div>

      {/* Özel eklenen varlıklar */}
      <div className="mt-5 border-t border-gray-100 pt-4">
        <div className="flex items-center justify-between mb-2 flex-wrap gap-2">
          <span className="text-xs font-bold text-gray-500 uppercase tracking-wider">{t('Eklediğin Varlıklar')}</span>
          <button
            type="button"
            onClick={() => setSearchOpen(true)}
            className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-[#093eaa] text-white text-xs font-bold hover:bg-[#0a2966] transition-colors"
          >
            <Plus className="w-3.5 h-3.5" /> {t('Varlık Ekle')}
          </button>
        </div>
        {custom.length === 0 ? (
          <p className="text-xs text-gray-400">{t('Şeride istediğin hisse, kripto, döviz, altın, fon… ekleyebilirsin.')}</p>
        ) : (
          <div className="flex flex-wrap gap-2">
            {custom.map(c => (
              <span key={`${c.assetType}:${c.symbol}`} className="inline-flex items-center gap-1.5 pl-3 pr-1.5 py-1 rounded-full bg-[#093eaa]/5 border border-[#093eaa]/15 text-xs font-semibold text-[#093eaa]">
                {c.name}
                <span className="text-[9px] text-gray-400">{t(ASSET_LABEL[c.assetType] ?? c.assetType)}</span>
                <button type="button" onClick={() => removeCustom(c.assetType, c.symbol)} title={t('Kaldır')}
                  className="p-0.5 rounded-full hover:bg-rose-100 hover:text-rose-600 text-gray-400">
                  <X className="w-3 h-3" />
                </button>
              </span>
            ))}
          </div>
        )}
      </div>

      {searchOpen && (
        <InstrumentSearchModal
          portfolioName={t('şerit')}
          onSelect={addCustom}
          onClose={() => setSearchOpen(false)}
        />
      )}
    </div>
  );
}
