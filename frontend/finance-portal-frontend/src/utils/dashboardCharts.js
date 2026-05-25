// Portföy "Grafikler" sekmesinden Dashboard'a eklenen analiz kartları (kullanıcı tercihi).
// Her öğe: { portfolioId, portfolioName, chartKey }

import { prefGet, prefSet } from '../api/prefs';

export const DASH_PF_CHARTS_KEY = 'fp-dashboard-pf-charts';
export const DASH_PF_EVENT = 'fp-dashboard-pf-changed';

export function readPfCharts() {
  const v = prefGet(DASH_PF_CHARTS_KEY, []);
  return Array.isArray(v) ? v : [];
}

export function savePfCharts(list) {
  prefSet(DASH_PF_CHARTS_KEY, list);
  window.dispatchEvent(new Event(DASH_PF_EVENT));
}

export function addPfChart(item) {
  const list = readPfCharts();
  if (list.some(x => x.portfolioId === item.portfolioId && x.chartKey === item.chartKey)) return false;
  savePfCharts([...list, item]);
  return true;
}

export function removePfChart(portfolioId, chartKey) {
  savePfCharts(readPfCharts().filter(x => !(x.portfolioId === portfolioId && x.chartKey === chartKey)));
}
