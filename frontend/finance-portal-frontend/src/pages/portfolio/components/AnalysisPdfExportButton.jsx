import { useState } from 'react';
import { FileText } from 'lucide-react';
import { exportElementToPdf } from '../utils/domToPdf';

/**
 * Analiz raporu PDF indirmesi — ham veri export’u değil; ekran görüntüsü tabanlı özet rapor.
 *
 * @param {() => HTMLElement | null} getRootElement — yakalanacak rapor kökü (buton bu elemanda olmamalı)
 * @param {string} fileNameBase — .pdf uzantısı olmadan
 */
export default function AnalysisPdfExportButton({
  getRootElement,
  fileNameBase = 'analiz-raporu',
  disabled = false,
  label = "PDF'ye Aktar",
}) {
  const [busy, setBusy] = useState(false);

  async function handleClick() {
    const el = typeof getRootElement === 'function' ? getRootElement() : null;
    if (!el) return;
    setBusy(true);
    try {
      await exportElementToPdf(el, { fileName: fileNameBase });
    } catch (e) {
      console.error(e);
      const detail = e instanceof Error ? e.message : String(e);
      alert(
        import.meta.env.DEV && detail
          ? `PDF oluşturulamadı.\n\n${detail}`
          : 'PDF oluşturulamadı.',
      );
    } finally {
      setBusy(false);
    }
  }

  return (
    <button
      type="button"
      onClick={handleClick}
      disabled={disabled || busy}
      className="flex items-center gap-2 px-4 py-2 rounded-xl text-sm font-semibold border border-gray-200 bg-white text-gray-700 hover:bg-gray-50 hover:border-gray-300 transition-all disabled:opacity-50"
    >
      <FileText className="w-4 h-4 shrink-0" aria-hidden />
      {busy ? 'PDF hazırlanıyor…' : label}
    </button>
  );
}
