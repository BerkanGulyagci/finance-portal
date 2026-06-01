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
import { TICKER_CATALOG } from '../../../utils/tickerPrefs';
import { useTranslation } from '../../../context/LanguageContext';

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
 * Piyasa şeridi özelleştirici modal gövdesi.
 * Kategori-pill ızgarası + özel eklenen varlıklar bölümünü içerir.
 * Tüm değişiklikler {@link saveTickerPrefs} / {@link saveCustomTickerItems} ile
 * anında tercihlere yazılır (prefSet → backend sync); çağıran modalın kaydet adımı yoktur.
 */
export default function TickerCustomizerModalBody({
  enabled,
  toggle,
  toggleGroup,
  custom,
  removeCustom,
  onOpenSearch,
}) {
  const { t } = useTranslation();

  return (
    <div>
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
            onClick={onOpenSearch}
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
    </div>
  );
}
