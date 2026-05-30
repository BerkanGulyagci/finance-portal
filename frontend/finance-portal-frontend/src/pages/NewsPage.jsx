import { useState, useEffect } from 'react';
import { Search, X } from 'lucide-react';
import { HeroSection } from './news/components/HeroSection';
import { NewsFeed } from './news/components/NewsFeed';
import { NewsFilterBar } from './news/components/NewsFilterBar';
import { PersonalNewsStrip } from './news/components/PersonalNewsStrip';
import { Sidebar } from './news/components/Sidebar';
import { useTranslation } from '../i18n/LanguageContext';

const FILTERS_KEY = 'news_filters_v1';
const DEFAULT_FILTERS = { category: '', source: '', q: '', region: 'TR' };

function loadFilters() {
  try {
    const s = sessionStorage.getItem(FILTERS_KEY);
    if (s) return { ...DEFAULT_FILTERS, ...JSON.parse(s) };
  } catch { /* yok say */ }
  return { ...DEFAULT_FILTERS };
}

export default function NewsPage() {
  const { t } = useTranslation();
  const [filters, setFilters] = useState(loadFilters);
  const [search, setSearch] = useState(() => loadFilters().q || '');
  const [facets, setFacets] = useState({ categories: [], sources: [] });

  // Arama kutusu → q filtresi (debounce'lu)
  useEffect(() => {
    const id = setTimeout(() => setFilters(f => (f.q === search.trim() ? f : { ...f, q: search.trim() })), 400);
    return () => clearTimeout(id);
  }, [search]);

  // Filtreleri sekme oturumunda sakla — detaya gidip geri dönünce sıfırlanmasın.
  useEffect(() => {
    try { sessionStorage.setItem(FILTERS_KEY, JSON.stringify(filters)); } catch { /* yok say */ }
  }, [filters]);

  // Bu sayfa diğerlerinden daha dar bir kapsayıcıda (sadece /news ve /news/:id).
  return (
    <div className="max-w-[1180px] mx-auto">
      {/* Hero — sol carousel'ın altındaki boş alana filtre yerleşir */}
      <HeroSection
        filterSlot={
          <NewsFilterBar
            filters={filters}
            setFilters={setFilters}
            categories={facets.categories}
            sources={facets.sources}
          />
        }
      />

      {/* Arama — hero altında, belirgin */}
      <div className="relative mb-6">
        <Search className="absolute left-4 top-1/2 -translate-y-1/2 w-5 h-5 text-gray-400" />
        <input
          value={search}
          onChange={e => setSearch(e.target.value)}
          placeholder={t('Haberlerde ara...')}
          className="w-full rounded-md border border-gray-200 bg-white pl-12 pr-10 py-3.5 text-[15px] shadow-sm focus:outline-none focus:ring-2 focus:ring-[#093eaa]/30 focus:border-[#093eaa]/40"
        />
        {search && (
          <button onClick={() => setSearch('')} className="absolute right-3.5 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600">
            <X className="w-5 h-5" />
          </button>
        )}
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-4 gap-8">
        <div className="lg:col-span-3">
          <PersonalNewsStrip />
          <NewsFeed filters={filters} onFacets={setFacets} />
        </div>
        <Sidebar />
      </div>
    </div>
  );
}
