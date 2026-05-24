// Portföy "Grafikler" sekmesinden Dashboard'a eklenen analiz kartları (kullanıcı tercihi).
// Her öğe: { portfolioId, portfolioName, chartKey }

export const DASH_PF_CHARTS_KEY = 'fp-dashboard-pf-charts';
export const DASH_PF_EVENT = 'fp-dashboard-pf-changed';

export function readPfCharts() {
  try {
    const v = JSON.parse(localStorage.getItem(DASH_PF_CHARTS_KEY) || 'null');
    return Array.isArray(v) ? v : [];
  } catch {
    return [];
  }
}

export function savePfCharts(list) {
  try {
    localStorage.setItem(DASH_PF_CHARTS_KEY, JSON.stringify(list));
    window.dispatchEvent(new Event(DASH_PF_EVENT));
  } catch { /* yoksay */ }
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
