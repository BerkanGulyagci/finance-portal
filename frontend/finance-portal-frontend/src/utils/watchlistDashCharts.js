// İzleme listesi "Grafikler" sekmesinden Dashboard'a eklenen grafikler (kullanıcı tercihi, cihazlar arası senkron).
// Her öğe: { watchlistId, watchlistName, chartKind }
import { prefGet, prefSet } from '../api/prefs';

export const DASH_WL_CHARTS_KEY = 'fp-dashboard-wl-charts';
export const DASH_WL_EVENT = 'fp-dashboard-wl-changed';

export function readWlCharts() {
  const v = prefGet(DASH_WL_CHARTS_KEY, []);
  return Array.isArray(v) ? v : [];
}

export function saveWlCharts(list) {
  prefSet(DASH_WL_CHARTS_KEY, list);
  window.dispatchEvent(new Event(DASH_WL_EVENT));
}

export function addWlChart(item) {
  const list = readWlCharts();
  if (list.some(x => x.watchlistId === item.watchlistId && x.chartKind === item.chartKind)) return false;
  saveWlCharts([...list, item]);
  return true;
}

export function removeWlChart(watchlistId, chartKind) {
  saveWlCharts(readWlCharts().filter(x => !(x.watchlistId === watchlistId && x.chartKind === chartKind)));
}
