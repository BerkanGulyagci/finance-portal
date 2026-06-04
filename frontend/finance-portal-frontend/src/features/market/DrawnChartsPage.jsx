import { useState, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { PenLine, Trash2, ArrowUpRight } from 'lucide-react';
import { useTranslation } from '../../context/LanguageContext';
import { listDrawnCharts, removeDrawnChart } from '../../utils/drawnCharts';
import { prefSet } from '../../api/prefs';
import AssetIcon from '../../components/instrument/AssetIcon';

const ICON_TYPE = { crypto: 'CRYPTO', fx: 'FX', fund: 'FUND', commodity: 'COMMODITY', stock: 'STOCK' };

/**
 * Çizimlerim sayfası — üzerine çizim yapılan tüm grafiklerin listesi (hızlı erişim).
 * Veri localStorage'daki chart-overlays:... anahtarlarından türetilir.
 */
export default function DrawnChartsPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [items, setItems] = useState(() => listDrawnCharts());

  const handleRemove = useCallback((e, key) => {
    e.stopPropagation();
    removeDrawnChart(key, prefSet);
    setItems(listDrawnCharts());
  }, []);

  return (
    <div className="max-w-[1100px] mx-auto px-4 py-6">
      <div className="flex items-center gap-2.5 mb-1">
        <PenLine className="w-6 h-6 text-[#7c3aed]" />
        <h1 className="text-2xl font-extrabold text-gray-900">{t('Çizimlerim')}</h1>
      </div>
      <p className="text-sm text-gray-500 mb-6">
        {t('Üzerine çizim (trend, fibonacci, şekil) yaptığınız grafikler — tıklayınca grafiğe gidersiniz.')}
      </p>

      {items.length === 0 ? (
        <div className="rounded-2xl border border-gray-100 bg-white p-10 text-center">
          <PenLine className="w-10 h-10 text-gray-300 mx-auto mb-3" />
          <p className="font-semibold text-gray-700">{t('Henüz grafik çizimi yok')}</p>
          <p className="text-sm text-gray-400 mt-1">
            {t('Bir grafikte çizim aracını kullanıp çizim yapın; burada listelenir.')}
          </p>
        </div>
      ) : (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
          {items.map(it => (
            <div
              key={it.key}
              role="button"
              tabIndex={0}
              onClick={() => navigate(it.route)}
              onKeyDown={(ev) => { if (ev.key === 'Enter') navigate(it.route); }}
              className="group flex items-center gap-3 rounded-2xl border border-gray-100 bg-white p-4 hover:border-[#7c3aed]/30 hover:shadow-sm transition-all cursor-pointer"
            >
              <AssetIcon assetType={ICON_TYPE[it.type]} symbol={it.symbol} name={it.display} size={36} />
              <div className="flex-1 min-w-0">
                <p className="font-bold text-gray-900 truncate">{it.display}</p>
                <p className="text-xs text-gray-400">
                  {t(it.typeLabel)} · {it.count} {t('çizim')}
                </p>
              </div>
              <button
                type="button"
                onClick={(ev) => handleRemove(ev, it.key)}
                title={t('Çizimleri sil')}
                className="shrink-0 p-1.5 rounded-lg text-gray-300 hover:text-rose-500 hover:bg-rose-50 transition-colors"
              >
                <Trash2 className="w-4 h-4" />
              </button>
              <ArrowUpRight className="w-4 h-4 text-gray-300 group-hover:text-[#7c3aed] transition-colors shrink-0" />
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
