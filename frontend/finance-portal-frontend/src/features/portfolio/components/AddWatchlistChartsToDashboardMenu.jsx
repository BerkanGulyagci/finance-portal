import { useState, useRef, useEffect } from 'react';
import { LayoutDashboard, Check, ChevronDown } from 'lucide-react';
import { WL_CHARTS } from './watchlistChartRegistry';
import { addWlChart, readWlCharts, removeWlChart, DASH_WL_EVENT } from '../../../utils/watchlistDashCharts';
import { useToast } from '../../../context/ToastContext';
import { useTranslation } from '../../../i18n/LanguageContext';

/**
 * İzleme listesi "Grafikler" sekmesi araç çubuğu menüsü: hangi grafiğin Dashboard'a ekleneceğini
 * seçtirir (varlık portföyündeki mantığın aynısı). Eklenen kart dashboard'da kaynak izleme listesi
 * adıyla görünür; tekrar seçince kaldırır.
 */
export default function AddWatchlistChartsToDashboardMenu({ watchlistId, watchlistName }) {
  const { t } = useTranslation();
  const toast = useToast();
  const [open, setOpen] = useState(false);
  const [added, setAdded] = useState(() => readWlCharts());
  const ref = useRef(null);

  useEffect(() => {
    const sync = () => setAdded(readWlCharts());
    window.addEventListener(DASH_WL_EVENT, sync);
    window.addEventListener('storage', sync);
    return () => {
      window.removeEventListener(DASH_WL_EVENT, sync);
      window.removeEventListener('storage', sync);
    };
  }, []);

  useEffect(() => {
    function onDoc(e) { if (ref.current && !ref.current.contains(e.target)) setOpen(false); }
    if (open) document.addEventListener('mousedown', onDoc);
    return () => document.removeEventListener('mousedown', onDoc);
  }, [open]);

  if (!watchlistId) return null;

  const isAdded = (kind) => added.some(x => x.watchlistId === watchlistId && x.chartKind === kind);

  function toggle(kind, label) {
    if (isAdded(kind)) {
      removeWlChart(watchlistId, kind);
      toast.success(t('Dashboard\'dan kaldırıldı.'));
    } else {
      addWlChart({ watchlistId, watchlistName, chartKind: kind });
      toast.success(`${t(label)} → ${t('Dashboard\'a eklendi.')}`);
    }
  }

  return (
    <div className="relative" ref={ref}>
      <button
        type="button"
        onClick={() => setOpen(v => !v)}
        className="inline-flex items-center gap-1.5 text-xs font-semibold px-3 py-1.5 rounded-lg text-[#093eaa] bg-[#093eaa]/5 hover:bg-[#093eaa]/10 transition-colors"
        title={t('Bir grafiği Dashboard\'a ekle')}
      >
        <LayoutDashboard className="w-3.5 h-3.5" /> {t('Dashboard\'a Ekle')}
        <ChevronDown className={`w-3 h-3 transition-transform ${open ? 'rotate-180' : ''}`} />
      </button>

      {open && (
        <div className="absolute right-0 mt-1 w-72 max-h-80 overflow-auto rounded-xl border border-gray-200 bg-white shadow-xl z-40 p-1">
          <p className="px-3 py-2 text-[11px] text-gray-400">{t('Panoya eklemek istediğiniz grafiği seçin')}</p>
          {WL_CHARTS.map(c => {
            const on = isAdded(c.key);
            return (
              <button
                key={c.key}
                type="button"
                onClick={() => toggle(c.key, c.label)}
                className={`w-full flex items-center justify-between gap-2 px-3 py-2 rounded-lg text-left text-sm transition-colors ${on ? 'bg-[#093eaa]/5' : 'hover:bg-gray-50'}`}
              >
                <span className="text-gray-800">{t(c.label)}</span>
                {on && <Check className="w-4 h-4 text-emerald-600 shrink-0" />}
              </button>
            );
          })}
        </div>
      )}
    </div>
  );
}
