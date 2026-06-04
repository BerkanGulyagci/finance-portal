/**
 * Material 3 segmented sekme çipleri — dashboard kartlarında (Hareketliler, Hacim
 * Liderleri) kategori seçimi için ortak buton grubu.
 *
 * @param {{key:string,label:string}[]} tabs
 * @param {number}   active  seçili index
 * @param {(i:number)=>void} onChange
 * @param {(s:string)=>string} t  çeviri fonksiyonu (label'lar çevrilir)
 */
export default function CardTabs({ tabs, active, onChange, t = (s) => s, accent = '#093eaa' }) {
  if (!tabs?.length) return null;
  return (
    <div className="flex flex-wrap gap-1.5 mb-3">
      {tabs.map((tab, i) => {
        const isActive = i === active;
        return (
          <button
            key={tab.key}
            type="button"
            onClick={() => onChange(i)}
            aria-pressed={isActive}
            className={[
              'px-3 py-1 rounded-full text-xs font-semibold transition-all duration-150',
              'border focus:outline-none focus-visible:ring-2 focus-visible:ring-offset-1',
              isActive
                ? 'text-white border-transparent shadow-sm'
                : 'text-gray-600 bg-transparent border-gray-200 hover:bg-gray-50 hover:border-gray-300 active:bg-gray-100',
            ].join(' ')}
            style={isActive ? { backgroundColor: accent, '--tw-ring-color': `${accent}55` } : { '--tw-ring-color': `${accent}55` }}
          >
            {t(tab.label)}
          </button>
        );
      })}
    </div>
  );
}
