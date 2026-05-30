/** Ortak analitik kart kabuğu */
export default function PortfolioChartCard({ title, subtitle, children, className = '' }) {
  return (
    <div className={`rounded-xl border border-gray-200 bg-white p-4 shadow-sm min-w-0 ${className}`}>
      <h3 className="text-base font-bold text-gray-900">{title}</h3>
      {subtitle && <p className="mt-1 text-xs text-gray-500 leading-snug">{subtitle}</p>}
      <div className="mt-4 min-w-0">{children}</div>
    </div>
  );
}
