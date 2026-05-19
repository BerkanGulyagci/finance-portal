import { Search, X } from 'lucide-react';
import { BAN_STATUS_FILTER } from '../utils/banDisplay';

const FILTER_OPTIONS = [
  { value: BAN_STATUS_FILTER.ALL, label: 'Tümü' },
  { value: BAN_STATUS_FILTER.ACTIVE, label: 'Aktif' },
  { value: BAN_STATUS_FILTER.BANNED, label: 'Banlı' },
];

export default function AdminUsersSearchBar({
  value,
  onChange,
  onClear,
  statusFilter,
  onStatusFilterChange,
  resultCount,
  page,
  hasMore,
}) {
  const pageNumber = page + 1;

  return (
    <section className="p-4 border-b border-gray-100 flex flex-col gap-3">
      <div className="flex flex-col lg:flex-row gap-3 lg:items-center">
        <div className="relative flex-1 max-w-xl">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400 w-4 h-4" />
          <input
            type="search"
            value={value}
            onChange={e => onChange(e.target.value)}
            placeholder="Kullanıcı adı veya e-posta ara..."
            className="w-full pl-9 pr-10 py-2.5 bg-gray-50 border border-gray-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-[#093eaa]"
            aria-label="Kullanıcı ara"
          />
          {value && (
            <button
              type="button"
              onClick={onClear}
              className="absolute right-2 top-1/2 -translate-y-1/2 p-1 rounded-lg text-gray-400 hover:bg-gray-200 hover:text-gray-600"
              aria-label="Aramayı temizle"
            >
              <X className="w-4 h-4" />
            </button>
          )}
        </div>

        <label className="flex items-center gap-2 text-sm font-semibold text-gray-600 shrink-0">
          <span className="text-xs uppercase tracking-wide text-gray-400">Durum</span>
          <select
            value={statusFilter}
            onChange={e => onStatusFilterChange(e.target.value)}
            className="rounded-xl border border-gray-200 bg-white px-3 py-2 text-sm font-bold text-gray-800 focus:outline-none focus:ring-2 focus:ring-[#093eaa]"
          >
            {FILTER_OPTIONS.map(option => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        </label>
      </div>

      <p className="text-xs text-gray-400 font-medium">
        {resultCount} kullanıcı · Sayfa {pageNumber}
        {hasMore ? ' · Sonraki sayfa mevcut' : ''}
      </p>
    </section>
  );
}
