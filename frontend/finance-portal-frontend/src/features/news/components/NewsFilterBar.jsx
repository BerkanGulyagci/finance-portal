import { SlidersHorizontal } from 'lucide-react';
import { useTranslation } from '../../../context/LanguageContext';
import { Dropdown } from '../../../components/shared/Dropdown';
import { groupSources } from '../../../utils/newsUtils';

const REGIONS = [
  { value: 'TR', label: 'Türkiye' },
  { value: 'GLOBAL', label: 'Global' },
  { value: 'ALL', label: 'Tümü' },
];

/** Kompakt haber filtresi (bölge segmenti + tür/kaynak dropdown). Hero carousel'ın altındaki alanı doldurur. */
export function NewsFilterBar({ filters, setFilters, categories = [], sources = [] }) {
  const { t } = useTranslation();
  const categoryOptions = [
    { label: t('Tüm türler'), value: '' },
    ...categories.map(c => ({ label: c.label, value: c.key })),
  ];
  const sourceOptions = [
    { label: t('Tüm kaynaklar'), value: '' },
    ...groupSources(sources).map(g => ({ label: g, value: g })),
  ];

  function setRegion(region) {
    setFilters(f => ({ ...f, region, category: '', source: '' }));
  }

  return (
    <div className="h-full rounded-md border border-gray-200 bg-white p-4 shadow-sm flex flex-col justify-center gap-4">
      <h3 className="text-sm font-bold text-gray-700 flex items-center gap-2">
        <SlidersHorizontal className="w-4 h-4 text-[#093eaa]" /> {t('Haberleri Filtrele')}
      </h3>

      <div className="flex flex-wrap items-center gap-2.5">
        <div className="inline-flex rounded-md border border-gray-200 overflow-hidden">
          {REGIONS.map(r => (
            <button
              key={r.value}
              onClick={() => setRegion(r.value)}
              className={`px-3.5 py-1.5 text-sm font-semibold transition-colors ${
                filters.region === r.value ? 'bg-[#093eaa] text-white' : 'bg-white text-gray-600 hover:bg-gray-50'
              }`}
            >
              {t(r.label)}
            </button>
          ))}
        </div>

        <Dropdown
          className="w-48"
          menuWidth="w-56"
          value={filters.category}
          options={categoryOptions}
          onChange={v => setFilters(f => ({ ...f, category: v }))}
          placeholder={t('Tüm türler')}
        />

        <Dropdown
          className="w-48"
          menuWidth="w-60"
          value={filters.source}
          options={sourceOptions}
          onChange={v => setFilters(f => ({ ...f, source: v }))}
          placeholder={t('Tüm kaynaklar')}
        />
      </div>
    </div>
  );
}
