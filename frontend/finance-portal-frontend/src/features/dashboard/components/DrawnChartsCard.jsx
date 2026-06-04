import { useState, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { PenLine, Trash2 } from 'lucide-react';
import DashCard from './DashCard';
import { useTranslation } from '../../../context/LanguageContext';
import { listDrawnCharts, removeDrawnChart } from '../../../utils/drawnCharts';
import { prefSet } from '../../../api/prefs';
import AssetIcon from '../../../components/instrument/AssetIcon';

// drawnCharts tipi → AssetIcon assetType
const ICON_TYPE = { crypto: 'CRYPTO', fx: 'FX', fund: 'FUND', commodity: 'COMMODITY', stock: 'STOCK' };

/**
 * Çizimlerim — kullanıcının üzerine çizim (trend/fibonacci/şekil) yaptığı grafiklerin
 * hızlı erişim listesi. Veri localStorage'daki chart-overlays:... anahtarlarından gelir.
 * Dashboard'da özet (ilk birkaç); tamamı için /market/cizimlerim sayfası.
 */
export default function DrawnChartsCard() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [items, setItems] = useState(() => listDrawnCharts());

  const handleRemove = useCallback((e, key) => {
    e.stopPropagation();
    removeDrawnChart(key, prefSet);
    setItems(listDrawnCharts());
  }, []);

  return (
    <DashCard
      title={t('Çizimlerim')}
      icon={PenLine}
      accent="#7c3aed"
      to="/market/cizimlerim"
      toLabel={t('Tümü')}
      scroll
    >
      {items.length === 0 ? (
        <div className="py-6 text-center text-xs text-gray-400">
          {t('Henüz grafik çizimi yok')}
        </div>
      ) : (
        <div className="divide-y divide-gray-50">
          {items.slice(0, 6).map(it => (
            <div
              key={it.key}
              role="button"
              tabIndex={0}
              onClick={() => navigate(it.route)}
              onKeyDown={(ev) => { if (ev.key === 'Enter') navigate(it.route); }}
              className="w-full flex items-center gap-2 py-1.5 px-1 rounded-lg hover:bg-gray-50 transition-colors cursor-pointer group"
            >
              <AssetIcon assetType={ICON_TYPE[it.type]} symbol={it.symbol} name={it.display} size={20} />
              <span className="text-xs font-semibold text-gray-800 flex-1 min-w-0 truncate">
                {it.display}
                <span className="text-[10px] text-gray-400 font-normal ml-1.5">{t(it.typeLabel)}</span>
              </span>
              <span className="text-[10px] text-gray-400 tabular-nums shrink-0">
                {it.count} {t('çizim')}
              </span>
              <button
                type="button"
                onClick={(ev) => handleRemove(ev, it.key)}
                title={t('Çizimleri sil')}
                className="shrink-0 p-1 rounded text-gray-300 hover:text-rose-500 hover:bg-rose-50 opacity-0 group-hover:opacity-100 transition-all"
              >
                <Trash2 className="w-3.5 h-3.5" />
              </button>
            </div>
          ))}
        </div>
      )}
    </DashCard>
  );
}
