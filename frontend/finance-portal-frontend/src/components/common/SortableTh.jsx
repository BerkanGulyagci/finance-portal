/**
 * Sortable table header cell.
 * Props: label, sortKey, currentKey, currentDir, onSort, className, align ('left'|'right')
 */
export default function SortableTh({ label, sortKey, currentKey, currentDir, onSort, className = '', align = 'left' }) {
  const active = currentKey === sortKey;
  const arrow = active ? (currentDir === 'asc' ? ' ↑' : ' ↓') : ' ↕';

  return (
    <th
      onClick={() => onSort(sortKey)}
      className={`px-4 py-3 text-xs font-bold uppercase tracking-wider whitespace-nowrap border-b border-gray-200 cursor-pointer select-none transition-colors
        ${active ? 'text-[#093eaa] bg-blue-50' : 'text-gray-500 hover:text-[#093eaa] hover:bg-gray-100'}
        ${align === 'right' ? 'text-right' : 'text-left'}
        ${className}`}
    >
      {label}
      <span className={`ml-0.5 text-[10px] ${active ? 'opacity-100' : 'opacity-30'}`}>{arrow}</span>
    </th>
  );
}
