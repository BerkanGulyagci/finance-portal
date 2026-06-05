import { useRef, useState, useCallback, useMemo, useEffect } from 'react';
import { prefGet, prefSet } from '../api/prefs';

/**
 * klinecharts grafiklerinde çizim (overlay) KALICILIĞI — hisse/endeks/kripto/döviz/fon ortak.
 *
 * Kaydetme tetikleyicisi = SNAPSHOT (klinecharts'ın onDrawEnd'i gerçek mouse etkileşiminde güvenilir
 * tetiklenmiyor): her mouseup/pointerup'ta grafikteki TÜM canlı overlay'ler getOverlayById ile okunup
 * tek JSON listesi olarak {@code prefSet(persistKey, ...)} ile saklanır (localStorage + giriş varsa
 * sunucu senkronu → cihazlar arası). Yüklü veri penceresi dışında kalan çizimler dormantRef'te korunur.
 *
 * Kullanım:
 *   const draw = useChartDrawings({ chartRef, chartIdRef, persistKey: `chart-overlays:${id}` });
 *   // klineInit'ten sonra chartRef.current set edildiğinde + applyNewData'dan SONRA:
 *   draw.restoreOverlays(klineData);
 *   // DrawingToolbar'a: activeTool/onSelectTool/onDeleteSelected/onClearAll = draw.*
 *
 * @param {object}   p
 * @param {React.MutableRefObject} p.chartRef    klinecharts instance ref (chartRef.current)
 * @param {React.MutableRefObject} p.chartIdRef  grafik DOM element id'sini tutan ref
 * @param {string}   p.persistKey                kayıt anahtarı (ör. `chart-overlays:fx:USD`); boşsa no-op
 */
export function useChartDrawings({ chartRef, chartIdRef, persistKey }) {
  const liveIdsRef = useRef(new Set()); // o aralıkta GRAFİĞE ÇİZİLİ canlı overlay id'leri
  const dormantRef = useRef([]);        // kayıtlı ama bu aralıkta görünmeyen (pencere dışı) çizimler
  const [activeTool, setActiveTool] = useState(null);

  const persistSnapshot = useCallback((force = false) => {
    const chart = chartRef.current;
    if (!chart || !persistKey) return;
    const rendered = [];
    const dead = [];
    liveIdsRef.current.forEach(id => {
      const ov = chart.getOverlayById(id);
      if (ov && Array.isArray(ov.points) && ov.points.length > 0) {
        rendered.push({ name: ov.name, points: ov.points.map(pt => ({ timestamp: pt.timestamp, value: pt.value })) });
      } else if (!ov) {
        dead.push(id); // silinmiş/iptal
      }
    });
    dead.forEach(id => liveIdsRef.current.delete(id));
    const full = [...dormantRef.current, ...rendered];
    if (!force && full.length === 0) return; // sadece pan/boş tıklamada gereksiz yazma yok
    prefSet(persistKey, full);
  }, [chartRef, persistKey]);

  const overlayEvents = useMemo(() => ({
    onDrawEnd:        () => { persistSnapshot(); return false; },
    onPressedMoveEnd: () => { persistSnapshot(); return false; },
    onRemoved:        (e) => { if (e?.overlay?.id) liveIdsRef.current.delete(e.overlay.id); persistSnapshot(true); return false; },
  }), [persistSnapshot]);

  const handleSelectTool = useCallback((toolId) => {
    setActiveTool(toolId);
    if (!chartRef.current) return;
    if (toolId) {
      const oid = chartRef.current.createOverlay({ name: toolId, ...overlayEvents });
      const realId = Array.isArray(oid) ? oid[0] : oid;
      if (realId) liveIdsRef.current.add(realId);
    }
  }, [chartRef, overlayEvents]);

  const clearAll = useCallback(() => {
    try { chartRef.current?.removeOverlay(); } catch { /* yoksay */ }
    liveIdsRef.current.clear();
    dormantRef.current = [];
    if (persistKey) prefSet(persistKey, []);
  }, [chartRef, persistKey]);

  const handleDeleteSelected = useCallback(() => { clearAll(); }, [clearAll]);
  const handleClearAll = useCallback(() => { clearAll(); setActiveTool(null); }, [clearAll]);

  /** applyNewData'dan SONRA çağır: kayıtlı çizimleri yükle (pencere içi olanları çiz, dışındakileri sakla). */
  const restoreOverlays = useCallback((klineData) => {
    if (!chartRef.current || !persistKey) return;
    liveIdsRef.current.clear();
    dormantRef.current = [];
    const saved = prefGet(persistKey, []) || [];
    const arr = Array.isArray(klineData) ? klineData : [];
    const dataMin = arr.length ? arr[0].timestamp : -Infinity;
    const dataMax = arr.length ? arr[arr.length - 1].timestamp : Infinity;
    (Array.isArray(saved) ? saved : []).forEach(ov => {
      const pts = ov.points || [];
      const hasTs = pts.some(p => p && p.timestamp != null);
      const inWindow = !hasTs || pts.some(p => p.timestamp == null || (p.timestamp >= dataMin && p.timestamp <= dataMax));
      if (!inWindow) { dormantRef.current.push(ov); return; }
      try {
        const oid = chartRef.current.createOverlay({ name: ov.name, points: ov.points, ...overlayEvents });
        const realId = Array.isArray(oid) ? oid[0] : oid;
        if (realId) liveIdsRef.current.add(realId);
        else dormantRef.current.push(ov);
      } catch { dormantRef.current.push(ov); }
    });
  }, [chartRef, persistKey, overlayEvents]);

  // ASIL kaydetme tetikleyicisi: grafik üzerinde her mouseup/pointerup → kısa gecikmeyle snapshot.
  // CAPTURE fazı: klinecharts olayı yutsa bile yakalar. pointerup gerçek mouse'ta da gelir (onDrawEnd'e
  // güvenmiyoruz). 80ms: klinecharts noktayı sonlandırsın, sonra getOverlayById dolu noktalarla okusun.
  useEffect(() => {
    let timer;
    let cleanup = null;
    const onUp = () => { clearTimeout(timer); timer = setTimeout(() => persistSnapshot(), 80); };

    // chartIdRef.current bazı grafiklerde (GoldChart) chart hazır olunca SONRADAN dolar; bu effect
    // mount anında boş bulup dinleyici kurmuyordu → çizim kaydedilmiyordu. Element gelene kadar
    // kısa aralıkla yokla (poll); bulununca dinleyiciyi kur. Emtia/hisse (id baştan dolu) ilk denemede kurar.
    let attempts = 0;
    let poll;
    const tryAttach = () => {
      const id = chartIdRef?.current;
      const el = id ? document.getElementById(id) : null;
      if (el) {
        el.addEventListener('mouseup', onUp, true);
        el.addEventListener('touchend', onUp, true);
        el.addEventListener('pointerup', onUp, true);
        cleanup = () => {
          el.removeEventListener('mouseup', onUp, true);
          el.removeEventListener('touchend', onUp, true);
          el.removeEventListener('pointerup', onUp, true);
        };
        return;
      }
      if (attempts++ < 40) poll = setTimeout(tryAttach, 100);   // ~4 sn boyunca yokla
    };
    tryAttach();

    return () => {
      clearTimeout(poll);
      clearTimeout(timer);
      if (cleanup) cleanup();
    };
  }, [persistSnapshot, chartIdRef]);

  return { activeTool, setActiveTool, handleSelectTool, handleDeleteSelected, handleClearAll, restoreOverlays, overlayEvents };
}
