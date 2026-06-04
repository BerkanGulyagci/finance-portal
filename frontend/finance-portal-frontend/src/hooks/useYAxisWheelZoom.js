import { useEffect } from 'react';

/**
 * Fiyat ekseni (Y) üzerinde FARE TEKERLEĞİ ile dikey zoom.
 *
 * klinecharts 9.x, fiyat ekseni üzerinde BASILI-SÜRÜKLEME ile dikey ölçeklemeyi
 * zaten destekler (mousedown → mousemove → fiyat aralığı * scale). Ancak TEKERLEK
 * (wheel) varsayılan olarak yalnız X eksenine (zaman) bağlıdır. Bu hook, fiyat
 * ekseninin DOM elementine bir wheel dinleyicisi ekler ve tekerlek hareketini,
 * klinecharts'ın KENDİ çalışan sürükleme-ölçekleme akışını taklit eden sentetik
 * mouse olaylarına (mousedown→mousemove→mouseup) çevirir.
 *
 * Böylece kütüphanenin internal'ine dokunmadan, desteklenen davranış üzerinden
 * "fiyat ekseninde tekerlek = dikey zoom" elde edilir.
 *
 * @param chartRef  klinecharts init(...) örneğini tutan ref (chartRef.current)
 * @param ready     grafik verisi yüklenip Y ekseni DOM'u oluştuğunda true olan tetik
 *                  (ör. !loading). Bu değiştiğinde hook yeniden bağlanır.
 * @param paneId    fiyat pane id'si (varsayılan 'candle_pane')
 */
export function useYAxisWheelZoom(chartRef, ready = true, paneId = 'candle_pane') {
  useEffect(() => {
    if (!ready) return undefined;
    const chart = chartRef?.current;
    if (!chart || typeof chart.getDom !== 'function') return undefined;

    let axisEl;
    try {
      axisEl = chart.getDom(paneId, 'yAxis');
    } catch {
      axisEl = null;
    }
    if (!axisEl) return undefined;

    const onWheel = (e) => {
      e.preventDefault();
      e.stopPropagation();

      const rect = axisEl.getBoundingClientRect();
      const cx = rect.left + rect.width / 2;
      const cy = rect.top + rect.height / 2;

      // Tekerlek yukarı (deltaY<0) → yakınlaş (aralık daralır); aşağı → uzaklaş.
      // klinecharts ölçeklemesi sürükleme MESAFESİNE bağlı; küçük sabit adım yumuşak hisset­tirir.
      const step = 12;
      const dy = e.deltaY < 0 ? -step : step;

      const opts = { bubbles: true, cancelable: true, clientX: cx, view: window };
      // Basıldı (başlangıç mesafesi = clientY) → hareket (clientY + dy) → bırak.
      axisEl.dispatchEvent(new MouseEvent('mousedown', { ...opts, clientY: cy }));
      axisEl.dispatchEvent(new MouseEvent('mousemove', { ...opts, clientY: cy + dy }));
      axisEl.dispatchEvent(new MouseEvent('mouseup',   { ...opts, clientY: cy + dy }));
    };

    // passive:false → preventDefault sayfa kaymasını engeller (zoom sırasında).
    axisEl.addEventListener('wheel', onWheel, { passive: false });
    return () => axisEl.removeEventListener('wheel', onWheel);
  }, [chartRef, ready, paneId]);
}
