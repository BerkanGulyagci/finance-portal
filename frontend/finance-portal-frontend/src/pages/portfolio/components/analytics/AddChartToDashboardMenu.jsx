import { useState, useRef, useEffect } from 'react';
import { LayoutDashboard, Check, ChevronDown } from 'lucide-react';
import { ANALYTICS_CHARTS } from './analyticsRegistry';
import { addPfChart, readPfCharts, removePfChart, DASH_PF_EVENT } from '../../../../utils/dashboardCharts';
import { useToast } from '../../../../context/ToastContext';
import { useTranslation } from '../../../../i18n/LanguageContext';

/**
 * Portföy "Grafikler" sekmesi araç çubuğu butonu: tıklayınca hangi grafiğin
 * Dashboard'a ekleneceğini seçtiren menü. Eklenen kart Dashboard'da kaynak
 * portföy adıyla görünür. Tekrar seçince Dashboard'dan kaldırır.
 */
export default function AddChartToDashboardMenu({ portfolioId, portfolioName }) {
  const { t } = useTranslation();
  const toast = useToast();
  const [open, setOpen] = useState(false);
  const [added, setAdded] = useState(() => readPfCharts());
  const ref = useRef(null);

  useEffect(() => {
    const sync = () => setAdded(readPfCharts());
    window.addEventListener(DASH_PF_EVENT, sync);
    window.addEventListener('storage', sync);
    return () => {
      window.removeEventListener(DASH_PF_EVENT, sync);
      window.removeEventListener('storage', sync);
    };
  }, []);

  useEffect(() => {
    function onDoc(e) { if (ref.current && !ref.current.contains(e.target)) setOpen(false); }
    if (open) document.addEventListener('mousedown', onDoc);
    return () => document.removeEventListener('mousedown', onDoc);
  }, [open]);

  if (!portfolioId) return null;

  const isAdded = (key) => added.some(x => x.portfolioId === portfolioId && x.chartKey === key);

  function toggle(key, label) {
    if (isAdded(key)) {
      removePfChart(portfolioId, key);
      toast.success(t('Dashboard\'dan kaldırıldı.'));
    } else {
      addPfChart({ portfolioId, portfolioName, chartKey: key });
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
          <p className="px-3 py-2 text-[11px] text-gray-400">
            {t('Panoya eklemek istediğiniz grafiği seçin')}
          </p>
          {ANALYTICS_CHARTS.map(c => {
            const on = isAdded(c.key);
            return (
              <button
                key={c.key}
                type="button"
                onClick={() => toggle(c.key, c.label)}
                className={`w-full flex items-center justify-between gap-2 px-3 py-2 rounded-lg text-left text-sm transition-colors ${
                  on ? 'bg-[#093eaa]/5' : 'hover:bg-gray-50'
                }`}
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
