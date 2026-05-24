import { useCallback, useEffect, useState } from 'react';

/**
 * Kart/widget sıralaması + gizleme durumunu kullanıcı bazında (localStorage) saklar.
 * Yeni eklenen kartlar otomatik sona eklenir; kaldırılan kartlar düşürülür.
 * "Sıfırla" hem sıralamayı hem gizlenenleri varsayılana döndürür (tüm kartlar geri gelir).
 *
 * @param {string} storageKey localStorage anahtarı
 * @param {string[]} defaultKeys varsayılan sıralama (stabil referans olmalı)
 */
export function useCardOrder(storageKey, defaultKeys) {
  const hiddenKey = `${storageKey}:hidden`;
  const [order, setOrder] = useState(() => merge(readArr(storageKey), defaultKeys));
  const [hidden, setHidden] = useState(() => new Set(readArr(hiddenKey) ?? []));

  // defaultKeys değişirse (yeni kart eklendi/çıkarıldı) mevcut sırayla birleştir
  useEffect(() => {
    setOrder(prev => merge(prev, defaultKeys));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [defaultKeys.join('|')]);

  useEffect(() => {
    try { localStorage.setItem(storageKey, JSON.stringify(order)); } catch { /* yoksay */ }
  }, [storageKey, order]);

  useEffect(() => {
    try { localStorage.setItem(hiddenKey, JSON.stringify([...hidden])); } catch { /* yoksay */ }
  }, [hiddenKey, hidden]);

  const move = useCallback((fromKey, toKey) => {
    setOrder(prev => {
      if (fromKey === toKey) return prev;
      const arr = [...prev];
      const from = arr.indexOf(fromKey);
      const to = arr.indexOf(toKey);
      if (from < 0 || to < 0) return prev;
      arr.splice(from, 1);
      arr.splice(to, 0, fromKey);
      return arr;
    });
  }, []);

  const hide = useCallback((key) => {
    setHidden(prev => { const n = new Set(prev); n.add(key); return n; });
  }, []);

  const show = useCallback((key) => {
    setHidden(prev => { const n = new Set(prev); n.delete(key); return n; });
  }, []);

  const reset = useCallback(() => {
    setOrder([...defaultKeys]);
    setHidden(new Set());
  }, [defaultKeys]);

  return { order, hidden, move, hide, show, reset };
}

function readArr(storageKey) {
  try {
    const saved = JSON.parse(localStorage.getItem(storageKey) || 'null');
    return Array.isArray(saved) ? saved : null;
  } catch {
    return null;
  }
}

/** Kayıtlı sırayı geçerli anahtarlara göre süz + eksik varsayılanları sona ekle. */
function merge(saved, defaultKeys) {
  if (!Array.isArray(saved)) return [...defaultKeys];
  const known = saved.filter(k => defaultKeys.includes(k));
  const missing = defaultKeys.filter(k => !known.includes(k));
  return [...known, ...missing];
}
