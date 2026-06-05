// Vitest global setup — jest-dom matcher'larını (toBeInTheDocument vb.) yükler.
// vite.config.js > test.setupFiles bu dosyayı her test dosyasından önce çalıştırır.
import '@testing-library/jest-dom';

// jsdom ResizeObserver tanımlamaz; ECharts/grafik bileşenleri chart effect'inde
// `new ResizeObserver(...)` kullanır → mock'lanmazsa "ResizeObserver is not defined"
// unhandled rejection'ı CI'yı (testler geçse bile) exit 1 yapar. Global no-op stub.
if (typeof globalThis.ResizeObserver === 'undefined') {
  globalThis.ResizeObserver = class {
    observe() {}
    unobserve() {}
    disconnect() {}
  };
}
