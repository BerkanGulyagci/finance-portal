import { useState } from 'react';
import { createPortal } from 'react-dom';
import { BarChart3, X, RotateCcw } from 'lucide-react';
import {
  ALL_TICKER_KEYS,
  readTickerPrefs, saveTickerPrefs,
  readCustomTickerItems, saveCustomTickerItems,
  readTickerSpeed, saveTickerSpeed,
} from '../../../utils/tickerPrefs';
import { useTranslation } from '../../../context/LanguageContext';
import InstrumentSearchModal from '../../../components/instrument/InstrumentSearchModal';
import TickerCustomizerModalBody from './TickerCustomizerModalBody';

/**
 * Hesap Ayarları'nda piyasa şeridi (ticker) özelleştirme.
 *
 * Profilde gösterilen kart minimaldir: ikon + başlık + açıklama + "{N} sembol seçili"
 * satırı ve sağda "Düzenle" butonu (yandaki "Bülten Aboneliği" kartıyla aynı yükseklikte).
 * "Düzenle"ye tıklayınca, kategori-pill ızgarasını içeren bir modal açılır
 * ({@link TickerCustomizerModalBody}). Modal içindeki tüm değişiklikler anında tercihlere
 * yazılır; modalda ayrıca "Kaydet" butonu yoktur, "Kapat" ve "Sıfırla" vardır.
 */
export default function TickerCustomizer() {
  const { t } = useTranslation();
  const [enabled, setEnabled] = useState(() => readTickerPrefs());
  const [custom, setCustom] = useState(() => readCustomTickerItems());
  const [speed, setSpeed] = useState(() => readTickerSpeed());
  const [open, setOpen] = useState(false);
  const [searchOpen, setSearchOpen] = useState(false);

  function onSpeedChange(v) {
    setSpeed(v);
    saveTickerSpeed(v); // prefSetSync + TICKER_PREFS_EVENT — MarketTicker anında günceller.
  }

  function commit(next) {
    setEnabled(next);
    saveTickerPrefs(next);
  }

  function addCustom(inst) {
    setSearchOpen(false);
    setCustom(prev => {
      if (prev.some(c => c.assetType === inst.assetType && c.symbol === inst.symbol)) return prev;
      const next = [...prev, {
        assetType: inst.assetType,
        symbol: inst.symbol,
        name: inst.name || inst.symbol,
        ...(inst.assetType === 'CRYPTO' && inst.coinId ? { coinId: inst.coinId } : {}),
        ...(inst.subType ? { subType: inst.subType } : {}),
      }];
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

  // "Sıfırla" → tüm katalog öğeleri açık + kullanıcı özel öğeleri temizlenir + hız varsayılana.
  function resetAll() {
    commit(new Set(ALL_TICKER_KEYS));
    setCustom([]);
    saveCustomTickerItems([]);
    setSpeed('normal');
    saveTickerSpeed('normal');
  }

  const count = enabled.size + custom.length;

  return (
    <>
      <section className="m3-card p-3 sm:p-6">
        <div className="flex items-start justify-between gap-3 mb-4">
          <div className="flex items-center gap-3 min-w-0">
            <span className="m3-icon-chip"><BarChart3 className="w-5 h-5" /></span>
            <div className="min-w-0">
              <h3 className="font-bold text-gray-900 leading-tight">{t('Piyasa Şeridi')}</h3>
              <p className="text-xs text-gray-500 mt-0.5">
                {t('Piyasa şeridine hangi varlıkları koyacağını seç')}
              </p>
            </div>
          </div>
        </div>
        <div className="flex items-center justify-between gap-2">
          <span className="text-sm font-semibold text-gray-700">
            {t('{count} sembol seçili').replace('{count}', count)}
          </span>
          <button
            type="button"
            onClick={() => setOpen(true)}
            className="m3-btn m3-btn-text shrink-0"
          >
            {t('Düzenle')}
          </button>
        </div>
      </section>

      {open && createPortal((
        <div
          className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/50"
          onClick={() => setOpen(false)}
          onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); setOpen(false); } }}
          role="button"
          tabIndex={0}
        >
          <div
            className="bg-white rounded-2xl shadow-2xl w-full max-w-2xl max-h-[85vh] flex flex-col m3-scale-in"
            onClick={(e) => e.stopPropagation()}
            onKeyDown={(e) => e.stopPropagation()}
            role="dialog"
            aria-modal="true"
          >
            {/* Header */}
            <div className="flex items-start justify-between gap-2 p-5 border-b border-gray-100">
              <div className="flex items-center gap-2.5 min-w-0">
                <div className="w-10 h-10 rounded-2xl bg-[#093eaa]/10 text-[#093eaa] flex items-center justify-center shrink-0">
                  <BarChart3 className="w-5 h-5" />
                </div>
                <div className="min-w-0">
                  <h2 className="text-lg font-bold text-gray-900">{t('Piyasa Şeridini Özelleştir')}</h2>
                  <p className="text-xs text-gray-500">
                    {t('Sayfanın üstündeki piyasa şeridinde hangi varlıkların görüneceğini seçin.')}
                  </p>
                </div>
              </div>
              <button
                onClick={() => setOpen(false)}
                className="p-1 rounded-md text-gray-400 hover:text-gray-700 hover:bg-gray-100 shrink-0"
                aria-label={t('Kapat')}
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            {/* Scrollable body */}
            <div className="overflow-y-auto p-5 flex-1 min-h-0">
              <TickerCustomizerModalBody
                enabled={enabled}
                toggle={toggle}
                toggleGroup={toggleGroup}
                custom={custom}
                removeCustom={removeCustom}
                onOpenSearch={() => setSearchOpen(true)}
                speed={speed}
                onSpeedChange={onSpeedChange}
              />
            </div>

            {/* Sticky footer */}
            <div className="flex items-center justify-between gap-2 p-4 border-t border-gray-100 bg-white rounded-b-2xl">
              <button
                type="button"
                onClick={resetAll}
                className="inline-flex items-center gap-1.5 text-sm font-semibold text-gray-600 hover:bg-gray-100 rounded-lg px-3 py-2 transition-colors"
              >
                <RotateCcw className="w-4 h-4" />
                {t('Sıfırla')}
              </button>
              <button
                type="button"
                onClick={() => setOpen(false)}
                className="inline-flex items-center gap-1.5 px-4 py-2 rounded-xl bg-[#093eaa] text-white text-sm font-bold hover:bg-[#0a2966] transition-colors"
              >
                {t('Kapat')}
              </button>
            </div>
          </div>
        </div>
      ), document.body)}

      {searchOpen && (
        <InstrumentSearchModal
          portfolioName={t('şerit')}
          onSelect={addCustom}
          onClose={() => setSearchOpen(false)}
        />
      )}
    </>
  );
}
