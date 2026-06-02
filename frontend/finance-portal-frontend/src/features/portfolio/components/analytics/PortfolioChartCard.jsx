/** Ortak analitik kart kabuğu — GridBoard hücresinde h-full alır; içerik kalan alanı doldurur
 *  (flex-1 min-h-0). Sabit-yükseklikli içerikli kartlar (h-[240px] gibi) bundan etkilenmez;
 *  responsive içerik (ResponsiveContainer height="100%") ise kart küçülünce küçülür. */
export default function PortfolioChartCard({ title, subtitle, children, className = '' }) {
  return (
    <div className={`rounded-xl border border-gray-200 bg-white p-4 shadow-sm min-w-0 h-full flex flex-col ${className}`}>
      <h3 className="text-base font-bold text-gray-900 shrink-0">{title}</h3>
      {subtitle && <p className="mt-1 text-xs text-gray-500 leading-snug shrink-0">{subtitle}</p>}
      <div className="mt-3 min-w-0 flex-1 min-h-0">{children}</div>
    </div>
  );
}
