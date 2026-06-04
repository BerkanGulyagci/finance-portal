import { Link } from 'react-router-dom';
import { ChevronRight } from 'lucide-react';

/** Tüm kartlarda ortak, sade "Tümü ›" / "Detay ›" bağlantı çipi. */
export function CardLink({ to, children, className = '' }) {
  return (
    <Link
      to={to}
      className={`group inline-flex items-center gap-0.5 text-[11px] font-semibold text-gray-400 hover:text-gray-700 shrink-0 rounded-full px-1.5 py-0.5 hover:bg-gray-100 transition-colors ${className}`}
    >
      {children}
      <ChevronRight className="w-3 h-3 transition-transform group-hover:translate-x-0.5" />
    </Link>
  );
}

/**
 * Dashboard kart kabuğu (Material 3) — başlık + opsiyonel "Tümü" linki / aksiyon, gövde.
 * scroll=true → gövde kendi içinde kayar (kart yüksekliği sabit kalır).
 */
export default function DashCard({
  title, icon: Icon, to, toLabel = 'Tümü', action, children,
  className = '', bodyClass = '', accent = '#093eaa', scroll = false,
}) {
  return (
    <section className={`bg-white rounded-2xl border border-gray-100 shadow-sm hover:shadow-md transition-shadow flex flex-col min-w-0 h-full ${className}`}>
      <header className="flex items-center justify-between gap-2 px-3.5 pt-3 pb-2 shrink-0">
        <h2 className="flex items-center gap-2 font-bold text-gray-900 text-[13px] min-w-0">
          {Icon && <Icon className="w-4 h-4 shrink-0" style={{ color: accent }} />}
          <span className="truncate">{title}</span>
        </h2>
        {action || (to && <CardLink to={to}>{toLabel}</CardLink>)}
      </header>
      <div className={`px-3.5 pb-3 flex-1 min-h-0 ${scroll ? 'overflow-y-auto m3-scroll' : ''} ${bodyClass}`}>{children}</div>
    </section>
  );
}
