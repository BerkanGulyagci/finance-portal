import { Search, X, LifeBuoy } from 'lucide-react';
import { useTranslation } from '../../../context/LanguageContext';
import { BAN_STATUS_FILTER } from '../utils/banDisplay';

export default function AdminUsersSearchBar({
  value,
  onChange,
  onClear,
  statusFilter,
  onStatusFilterChange,
  withTickets,
  onToggleWithTickets,
  resultCount,
  page,
  hasMore,
}) {
  const { t } = useTranslation();
  const pageNumber = page + 1;

  const FILTER_OPTIONS = [
    { value: BAN_STATUS_FILTER.ALL, label: t('Tümü') },
    { value: BAN_STATUS_FILTER.ACTIVE, label: t('Aktif') },
    { value: BAN_STATUS_FILTER.BANNED, label: t('Banlı') },
  ];

  return (
    <section className="p-4 border-b border-gray-100 flex flex-col gap-3">
      <div className="flex flex-col lg:flex-row gap-3 lg:items-center">
        <div className="relative flex-1 max-w-xl">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400 w-4 h-4" />
          <input
            type="search"
            value={value}
            onChange={e => onChange(e.target.value)}
            placeholder={t('Kullanıcı adı veya e-posta ara...')}
            className="w-full pl-9 pr-10 py-2.5 bg-gray-50 border border-gray-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-[#093eaa]"
            aria-label={t('Kullanıcı ara')}
          />
          {value && (
            <button
              type="button"
              onClick={onClear}
              className="absolute right-2 top-1/2 -translate-y-1/2 p-1 rounded-lg text-gray-400 hover:bg-gray-200 hover:text-gray-600"
              aria-label={t('Aramayı temizle')}
            >
              <X className="w-4 h-4" />
            </button>
          )}
        </div>

        <label className="flex items-center gap-2 text-sm font-semibold text-gray-600 shrink-0">
          <span className="text-xs uppercase tracking-wide text-gray-400">{t('Durum')}</span>
          <select
            value={statusFilter}
            onChange={e => onStatusFilterChange(e.target.value)}
            disabled={withTickets}
            className="rounded-xl border border-gray-200 bg-white px-3 py-2 text-sm font-bold text-gray-800 focus:outline-none focus:ring-2 focus:ring-[#093eaa] disabled:opacity-50"
          >
            {FILTER_OPTIONS.map(option => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        </label>

        <button
          type="button"
          onClick={onToggleWithTickets}
          className={`inline-flex items-center gap-1.5 rounded-xl px-3 py-2 text-sm font-bold border transition-colors shrink-0 ${
            withTickets ? 'bg-[#093eaa] text-white border-[#093eaa]' : 'bg-white text-gray-700 border-gray-200 hover:bg-gray-50'
          }`}
          title={t('Yalnızca destek talebi açan kullanıcılar')}
        >
          <LifeBuoy className="w-4 h-4" /> {t('Talep açanlar')}
        </button>
      </div>

      <p className="text-xs text-gray-400 font-medium">
        {resultCount} {t('kullanıcı')} · {t('Sayfa')} {pageNumber}
        {hasMore ? ` · ${t('Sonraki sayfa mevcut')}` : ''}
      </p>
    </section>
  );
}
