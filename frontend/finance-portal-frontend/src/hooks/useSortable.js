import { useState, useMemo } from 'react';

/**
 * Generic client-side sort hook.
 * @param {Array} data - array to sort
 * @param {string|null} defaultKey - default sort key
 * @param {'asc'|'desc'} defaultDir - default direction
 */
export function useSortable(data, defaultKey = null, defaultDir = 'asc') {
  const [sortKey, setSortKey] = useState(defaultKey);
  const [sortDir, setSortDir] = useState(defaultDir);

  function handleSort(key) {
    if (sortKey === key) {
      setSortDir(d => (d === 'asc' ? 'desc' : 'asc'));
    } else {
      setSortKey(key);
      setSortDir('asc');
    }
  }

  const sorted = useMemo(() => {
    if (!sortKey || !data?.length) return data;
    return [...data].sort((a, b) => {
      let av = a[sortKey];
      let bv = b[sortKey];
      // Try numeric comparison
      const an = parseFloat(String(av ?? '').replace(',', '.').replace('%', ''));
      const bn = parseFloat(String(bv ?? '').replace(',', '.').replace('%', ''));
      if (!isNaN(an) && !isNaN(bn)) {
        return sortDir === 'asc' ? an - bn : bn - an;
      }
      // String comparison
      av = String(av ?? '').toLowerCase();
      bv = String(bv ?? '').toLowerCase();
      if (av < bv) return sortDir === 'asc' ? -1 : 1;
      if (av > bv) return sortDir === 'asc' ? 1 : -1;
      return 0;
    });
  }, [data, sortKey, sortDir]);

  return { sorted, sortKey, sortDir, handleSort };
}
