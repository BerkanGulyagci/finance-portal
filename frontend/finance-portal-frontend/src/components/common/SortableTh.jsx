/**
 * Sortable table header cell.
 * Props: label, sortKey, currentKey, currentDir, onSort, className, align ('left'|'right'), onColor
 * onColor: set true when the header sits on a solid colored background — active/hover
 *          states then use translucent white overlays instead of light gray/blue patches.
 */
export default function SortableTh({ label, sortKey, currentKey, currentDir, onSort, className = '', align = 'left', onColor = false }) {
  const active = currentKey === sortKey;
  const arrow = active ? (currentDir === 'asc' ? ' ↑' : ' ↓') : ' ↕';

  // onColor (solid colored header): no white overlays at all — active = stronger dark
  // overlay, hover = lighter dark overlay. Same direction (dark) but different intensity,
  // and only the active column shows a solid ↑/↓ arrow, so they're never confused.
  const stateClasses = onColor
    ? (active ? 'bg-black/20' : 'hover:bg-black/10')
    : (active ? 'text-[#093eaa] bg-blue-50' : 'text-gray-500 hover:text-[#093eaa] hover:bg-gray-100');
  const borderClass = onColor ? 'border-b border-black/10' : 'border-b border-gray-200';

  return (
    <th
      onClick={() => onSort(sortKey)}
      className={`px-4 py-3 text-xs font-bold uppercase tracking-wider whitespace-nowrap ${borderClass} cursor-pointer select-none transition-colors
        ${stateClasses}
        ${align === 'right' ? 'text-right' : 'text-left'}
        ${className}`}
    >
      {label}
      <span className={`ml-0.5 text-[10px] ${active ? 'opacity-100' : 'opacity-40'}`}>{arrow}</span>
    </th>
  );
}
