import { useState } from 'react';
import {
  LineChart,
  Plus,
  X,
  DollarSign,
  Landmark,
  TrendingUp,
  Coins,
  Bitcoin,
  BarChart3,
} from 'lucide-react';
import {
  TICKER_CATALOG, readTickerPrefs, saveTickerPrefs,
  readCustomTickerItems, saveCustomTickerItems,
} from '../../../utils/tickerPrefs';
import { useTranslation } from '../../../context/LanguageContext';
import InstrumentSearchModal from '../../../components/instrument/InstrumentSearchModal';

const ASSET_LABEL = {
  STOCK: 'Hisse', FUND: 'Fon', FX: 'Döviz', FUTURE: 'Vadeli',
  CRYPTO: 'Kripto', GOLD: 'Altın', COMMODITY: 'Emtia', BOND: 'Tahvil',
};

/**
 * Her kategori için ikon + soluk vurgu renkleri.
 * `chipOn` (seçili pill) ve `chipOff` (seçilmemiş pill) Tailwind sınıfları;
 * `iconWrap` kart başlığındaki ikon-chip rengi.
 */
const CATEGORY_META = {
  'TCMB Döviz': {
    icon: DollarSign,
    iconWrap: 'bg-blue-50 text-[#093eaa]',
    chipOn: 'bg-[#093eaa] text-white border border-[#093eaa] shadow-sm',
    chipOff: 'bg-white text-[#093eaa] border border-blue-200 hover:bg-blue-50',
  },
  'Banka Kurları': {
    icon: Landmark,
    iconWrap: 'bg-indigo-50 text-indigo-700',
    chipOn: 'bg-indigo-600 text-white border border-indigo-600 shadow-sm',
    chipOff: 'bg-white text-indigo-700 border border-indigo-200 hover:bg-indigo-50',
  },
  'Endeksler (BIST)': {
    icon: TrendingUp,
    iconWrap: 'bg-emerald-50 text-emerald-700',
    chipOn: 'bg-emerald-600 text-white border border-emerald-600 shadow-sm',
    chipOff: 'bg-white text-emerald-700 border border-emerald-200 hover:bg-emerald-50',
  },
  'Altın': {
    icon: Coins,
    iconWrap: 'bg-amber-50 text-amber-700',
    chipOn: 'bg-amber-500 text-white border border-amber-500 shadow-sm',
    chipOff: 'bg-white text-amber-700 border border-amber-200 hover:bg-amber-50',
  },
  'Kripto': {
    icon: Bitcoin,
    iconWrap: 'bg-orange-50 text-orange-600',
    chipOn: 'bg-orange-500 text-white border border-orange-500 shadow-sm',
    chipOff: 'bg-white text-orange-700 border border-orange-200 hover:bg-orange-50',
  },
  'Ekonomi': {
    icon: BarChart3,
    iconWrap: 'bg-purple-50 text-purple-700',
    chipOn: 'bg-purple-600 text-white border border-purple-600 shadow-sm',
    chipOff: 'bg-white text-purple-700 border border-purple-200 hover:bg-purple-50',
  },
};

const DEFAULT_META = {
  icon: LineChart,
  iconWrap: 'bg-gray-100 text-gray-600',
  chipOn: 'bg-gray-700 text-white border border-gray-700 shadow-sm',
  chipOff: 'bg-white text-gray-700 border border-gray-200 hover:bg-gray-50',
};

/**
 * Hesap Ayarları'nda piyasa şeridi (ticker) özelleştirme: kullanıcı hangi varlıkların
 * üstteki şeritte görüneceğini seçer. Tercih localStorage'a yazılır, MarketTicker anında okur.
 *
 * UI: kategorilere göre dikey bölümler; her bölüm renkli ikon-başlık + "Hepsi/Hiçbiri" linki +
 * tıklanabilir pill (rounded-full) ızgarası içerir. Pill seçiliyken kategori renginde dolgulu,
 * seçili değilken ince renkli kenarlık ile.
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
    <div className="m3-card p-6">
      <div className="flex items-center gap-3 mb-4">
        <span className="m3-icon-chip"><LineChart className="w-5 h-5" /></span>
        <div className="min-w-0">
          <h3 className="font-bold text-gray-900 leading-tight">{t('Piyasa Şeridini Özelleştir')}</h3>
          <p className="text-xs text-gray-500 mt-0.5">
            {t('Sayfanın üstündeki piyasa şeridinde hangi varlıkların görüneceğini seçin.')}
          </p>
        </div>
      </div>

      <div className="space-y-4">
        {TICKER_CATALOG.map(group => {
          const meta = CATEGORY_META[group.group] ?? DEFAULT_META;
          const Icon = meta.icon;
          const allOn = group.items.every(it => enabled.has(it.key));
          return (
            <div key={group.group}>
              <div className="flex items-center justify-between mb-2">
                <div className="flex items-center gap-2 min-w-0">
                  <span className={`inline-flex items-center justify-center w-6 h-6 rounded-md ${meta.iconWrap}`}>
                    <Icon className="w-3.5 h-3.5" />
                  </span>
                  <span className="text-xs font-bold text-gray-700 uppercase tracking-wider truncate">
                    {t(group.group)}
                  </span>
                </div>
                <button
                  type="button"
                  onClick={() => toggleGroup(group.items, allOn)}
                  className="text-[11px] font-semibold text-[#093eaa] hover:underline shrink-0"
                >
                  {allOn ? t('Hiçbiri') : t('Hepsi')}
                </button>
              </div>
              <div className="flex flex-wrap gap-2">
                {group.items.map(it => {
                  const on = enabled.has(it.key);
                  return (
                    <button
                      key={it.key}
                      type="button"
                      onClick={() => toggle(it.key)}
                      aria-pressed={on}
                      className={`rounded-full px-3 py-1.5 text-xs font-semibold transition-all ${on ? meta.chipOn : meta.chipOff}`}
                    >
                      {it.label}
                    </button>
                  );
                })}
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
            className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-[#e7eefb] text-[#0b347f] text-xs font-bold hover:bg-[#d8e3f9] transition-colors"
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
