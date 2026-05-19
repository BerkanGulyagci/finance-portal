import { Search } from 'lucide-react';

export default function AdminUsersSearchBar({ value, onChange, onSubmit, resultCount }) {
  return (
    <section className="p-4 border-b border-gray-100 flex flex-col sm:flex-row gap-3 sm:items-center sm:justify-between">
      <form onSubmit={onSubmit} className="relative flex-1 max-w-md">
        <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400 w-4 h-4" />
        <input
          type="text"
          value={value}
          onChange={e => onChange(e.target.value)}
          placeholder="Kullanıcı adı veya e-posta ara..."
          className="w-full pl-9 pr-4 py-2 bg-gray-50 border border-gray-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-[#093eaa]"
        />
      </form>
      <p className="text-xs text-gray-400 font-medium">{resultCount} kullanıcı listelendi</p>
    </section>
  );
}
