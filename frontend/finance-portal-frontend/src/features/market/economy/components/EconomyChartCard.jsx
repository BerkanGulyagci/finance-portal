import { useState } from 'react';
import { getEconomySeries } from '../../../../api/marketApi.js';
import { useTranslation } from '../../../../context/LanguageContext';
import EconomyChart from './EconomyChart';

/**
 * Ekonomi grafiği + "Tümünü Görüntüle" butonu. Varsayılan (özet) seri gösterilir;
 * butona basınca o göstergenin TÜM geçmişi (kaynaktaki en eski tarihten bugüne) çekilip
 * yerinde gösterilir. Tekrar basınca özet görünüme döner. Tam seri bir kez çekilir, cache'lenir.
 */
export default function EconomyChartCard({ seriesKey, defaultSeries, type = 'line', color, height = 300 }) {
  const { t } = useTranslation();
  const [showFull, setShowFull] = useState(false);
  const [fullSeries, setFullSeries] = useState(null);
  const [loading, setLoading] = useState(false);

  const active = showFull && fullSeries ? fullSeries : defaultSeries;
  const count = active?.points?.length ?? 0;

  async function toggle() {
    if (showFull) { setShowFull(false); return; }
    if (fullSeries) { setShowFull(true); return; }
    setLoading(true);
    try {
      const full = await getEconomySeries(seriesKey, true);
      setFullSeries(full);
      setShowFull(true);
    } catch {
      /* sessiz — buton tekrar denenebilir */
    } finally {
      setLoading(false);
    }
  }

  return (
    <div>
      <div className="flex items-center justify-end gap-2 mb-2">
        {showFull && count > 0 && (
          <span className="text-[11px] text-gray-400">{count} {t('nokta')}</span>
        )}
        <button
          type="button"
          onClick={toggle}
          disabled={loading}
          className="inline-flex items-center gap-1 text-xs font-semibold px-2.5 py-1 rounded-lg border transition-colors
            text-[#093eaa] border-[#093eaa]/30 hover:bg-[#093eaa]/5 disabled:opacity-50"
        >
          {loading ? `${t('Yükleniyor')}…` : showFull ? t('Son 1 Yıl') : t('Tümünü Görüntüle')}
        </button>
      </div>
      <EconomyChart series={active} type={type} color={color} height={height} />
    </div>
  );
}
