import { useEffect, useRef, useState } from 'react';
import { getEconomyCharts } from '../../../api/marketApi.js';
import { useTranslation } from '../../../context/LanguageContext';
import EconomyChart from './EconomyChart';
import { ECONOMY_TOPICS } from './economyContent';

export default function EconomyPage() {
  const { t } = useTranslation();
  const [chartsMap, setChartsMap] = useState({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [activeKey, setActiveKey] = useState(null);
  const sectionRefs = useRef({});

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError('');
    getEconomyCharts()
      .then(list => {
        if (cancelled) return;
        const map = {};
        for (const s of list) map[s.key] = s;
        setChartsMap(map);
      })
      .catch(e =>
        !cancelled && setError(!e.response ? t('Sunucuya ulaşılamıyor.') : `${t('Hata')} (${e.response.status})`),
      )
      .finally(() => !cancelled && setLoading(false));
    return () => { cancelled = true; };
  }, []);

  // Aktif bölüm vurgusu (kaydırırken menüde işaretlenir)
  useEffect(() => {
    if (loading || error) return undefined;
    const observer = new IntersectionObserver(
      entries => {
        entries.forEach(e => {
          if (e.isIntersecting) setActiveKey(e.target.dataset.key);
        });
      },
      { rootMargin: '-100px 0px -70% 0px', threshold: 0 },
    );
    Object.values(sectionRefs.current).forEach(el => el && observer.observe(el));
    return () => observer.disconnect();
  }, [loading, error]);

  function scrollTo(key) {
    const el = sectionRefs.current[key];
    if (el) el.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

  return (
    <div>
      <h1 className="text-2xl font-bold text-gray-900 mb-2 border-l-4 border-[#093eaa] pl-4">
        {t('Türkiye Ekonomisi')}
      </h1>
      <p className="text-sm text-gray-500 mb-6 pl-5">
        {t('TCMB EVDS verileriyle güncel makroekonomik göstergeler — enflasyon, faiz, büyüme, dış denge ve daha fazlası.')}
      </p>

      {loading && (
        <div className="p-8 flex justify-center gap-1.5">
          <div className="w-2 h-2 bg-[#093eaa] rounded-full animate-bounce" />
          <div className="w-2 h-2 bg-[#093eaa]/60 rounded-full animate-bounce [animation-delay:100ms]" />
          <div className="w-2 h-2 bg-[#093eaa]/30 rounded-full animate-bounce [animation-delay:200ms]" />
        </div>
      )}
      {error && <div className="p-6 text-rose-500 text-sm">{error}</div>}

      {!loading && !error && (
        <div className="lg:flex lg:gap-8 lg:items-start">
          {/* Sol dikey bölüm menüsü (sticky) — masaüstünde */}
          <nav className="hidden lg:block lg:w-52 shrink-0 sticky top-20 self-start max-h-[calc(100vh-6rem)] overflow-y-auto">
            <p className="text-[11px] font-bold text-gray-400 uppercase tracking-wider mb-3 pl-4">
              {t('Bölümler')}
            </p>
            <ul className="border-l border-gray-200">
              {ECONOMY_TOPICS.map(tp => {
                const isActive = activeKey === tp.key;
                return (
                  <li key={tp.key}>
                    <button
                      type="button"
                      onClick={() => scrollTo(tp.key)}
                      className={`block w-full text-left text-sm pl-4 pr-2 py-1.5 -ml-px border-l-2 transition-colors
                        ${isActive
                          ? 'border-[#093eaa] text-[#093eaa] font-semibold'
                          : 'border-transparent text-gray-500 hover:text-gray-800 hover:border-gray-300'}`}
                    >
                      {t(tp.navLabel)}
                    </button>
                  </li>
                );
              })}
            </ul>
          </nav>

          {/* İçerik */}
          <div className="flex-1 min-w-0">
            {ECONOMY_TOPICS.map(tp => {
              const series = chartsMap[tp.key];
              const hasChart = (series?.points?.length ?? 0) > 0;
              return (
                <section
                  key={tp.key}
                  data-key={tp.key}
                  ref={el => { sectionRefs.current[tp.key] = el; }}
                  className="mb-12 scroll-mt-24"
                >
                  <h2 className="text-xl font-bold text-gray-900 mb-3 border-l-4 border-[#093eaa] pl-4">
                    {t(tp.title)}
                  </h2>
                  <div className="space-y-3 mb-5">
                    {tp.paragraphs.map((p, i) => (
                      <p key={i} className="text-sm sm:text-[15px] text-gray-600 leading-relaxed">{t(p)}</p>
                    ))}
                  </div>
                  <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-4 sm:p-6">
                    <h3 className="text-center text-base font-bold text-gray-800 mb-4">{t(tp.chartTitle)}</h3>
                    {hasChart ? (
                      <EconomyChart series={series} type={tp.chartType} color={tp.color} height={300} />
                    ) : (
                      <div className="h-40 flex items-center justify-center text-sm text-gray-400">
                        {t('Grafik verisi yok')}
                      </div>
                    )}
                    {series?.source && (
                      <p className="text-xs text-gray-400 mt-3">{t('Kaynak')}: {series.source}</p>
                    )}
                  </div>
                </section>
              );
            })}
          </div>
        </div>
      )}
    </div>
  );
}
