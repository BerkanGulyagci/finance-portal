import { toPng } from 'html-to-image';
import { jsPDF } from 'jspdf';

/**
 * html-to-image kullanarak DOM düğümünü A4 PDF olarak kaydeder.
 * html-to-image SVG tabanlı render yaptığı için Tailwind v4 oklch() renkleri sorunsuz çalışır.
 *
 * @param {HTMLElement} element
 * @param {object} [options]
 * @param {string} [options.fileName]    — uzantısız dosya adı
 * @param {number} [options.scale]       — render ölçeği (varsayılan 2)
 * @param {number} [options.marginMm]    — sayfa kenar boşluğu mm (varsayılan 10)
 * @param {'p'|'l'} [options.orientation] — varsayılan 'p'
 */
export async function exportElementToPdf(element, options = {}) {
  if (!element || !(element instanceof HTMLElement)) {
    throw new Error('exportElementToPdf: geçerli bir HTMLElement gerekli');
  }

  const fileName    = `${sanitizeFileBase(options.fileName || 'rapor')}.pdf`;
  const scale       = typeof options.scale === 'number' ? options.scale : 2;
  const marginMm    = typeof options.marginMm === 'number' ? options.marginMm : 10;
  const orientation = options.orientation === 'l' ? 'l' : 'p';

  element.scrollIntoView({ block: 'nearest', behavior: 'auto' });

  if (document.fonts) {
    await document.fonts.ready.catch(() => {});
  }

  await waitForNextPaint();

  const captureWidthPx = Math.max(
    element.getBoundingClientRect().width,
    element.scrollWidth,
    element.offsetWidth,
    1280,
  );

  const restoreLayout  = expandAncestorsForCapture(element, captureWidthPx);
  const backupStyle    = applyCaptureSizing(element, captureWidthPx);
  const restoreScrolls = hideInnerScrollbars(element);

  await reflowCharts();

  let dataUrl;
  try {
    dataUrl = await toPng(element, {
      pixelRatio: scale,
      backgroundColor: '#ffffff',
      width:  Math.round(captureWidthPx),
      height: Math.round(element.scrollHeight),
      style: {
        maxWidth: 'none',
        width: `${Math.round(captureWidthPx)}px`,
        overflow: 'visible',
      },
      skipFonts: false,
      cacheBust: true,
    });
  } finally {
    restoreScrolls();
    restoreStyle(element, backupStyle);
    restoreLayout();
  }

  if (!dataUrl || dataUrl === 'data:,') {
    throw new Error('Görüntü oluşturulamadı.');
  }

  const img = new Image();
  await new Promise((resolve, reject) => {
    img.onload = resolve;
    img.onerror = reject;
    img.src = dataUrl;
  });

  const pdf = new jsPDF({ orientation, unit: 'mm', format: 'a4' });
  const pageW = pdf.internal.pageSize.getWidth();
  const pageH = pdf.internal.pageSize.getHeight();
  const useW  = pageW - 2 * marginMm;
  const useH  = pageH - 2 * marginMm;

  const imgRatio   = img.naturalHeight / img.naturalWidth;
  const imgHeightMm = useW * imgRatio;

  let yMm  = marginMm;
  let remaining = imgHeightMm;
  let srcY = 0;

  while (remaining > 0.5) {
    const sliceMm = Math.min(remaining, useH);
    const sliceFrac = sliceMm / imgHeightMm;
    const sliceH = img.naturalHeight * sliceFrac;

    const sliceCanvas = document.createElement('canvas');
    sliceCanvas.width  = img.naturalWidth;
    sliceCanvas.height = Math.round(sliceH);
    const ctx = sliceCanvas.getContext('2d');
    ctx.drawImage(img, 0, -srcY, img.naturalWidth, img.naturalHeight);

    const sliceData = sliceCanvas.toDataURL('image/png');
    pdf.addImage(sliceData, 'PNG', marginMm, yMm, useW, sliceMm);

    srcY      += sliceH;
    remaining -= sliceMm;

    if (remaining > 0.5) {
      pdf.addPage();
      yMm = marginMm;
    }
  }

  pdf.save(fileName);
}

/**
 * PDF yakalanırken overflow-x-auto tablolarındaki yatay kaydırma çubukları görünmesin.
 * Her alt öğe için overflow'u geçici olarak hidden → visible'a çeker; bitince geri alır.
 */
function hideInnerScrollbars(root) {
  const backups = [];
  const all = [root, ...root.querySelectorAll('*')];
  for (const el of all) {
    if (!(el instanceof HTMLElement)) continue;
    const cs = getComputedStyle(el);
    if (cs.overflowX === 'auto' || cs.overflowX === 'scroll' ||
        cs.overflowY === 'auto' || cs.overflowY === 'scroll') {
      backups.push({ el, overflowX: el.style.overflowX, overflowY: el.style.overflowY });
      el.style.setProperty('overflow-x', 'visible', 'important');
      el.style.setProperty('overflow-y', 'visible', 'important');
    }
  }
  return () => {
    backups.reverse().forEach(({ el, overflowX, overflowY }) => {
      el.style.overflowX = overflowX;
      el.style.overflowY = overflowY;
    });
  };
}

function applyCaptureSizing(el, widthPx) {
  const backup = {
    width:    el.style.width,
    minWidth: el.style.minWidth,
    maxWidth: el.style.maxWidth,
    overflow: el.style.overflow,
    boxSizing: el.style.boxSizing,
  };
  el.style.boxSizing = 'border-box';
  el.style.maxWidth  = 'none';
  el.style.minWidth  = `${widthPx}px`;
  el.style.width     = `${widthPx}px`;
  el.style.overflow  = 'visible';
  void el.offsetWidth;
  return backup;
}

function restoreStyle(el, backup) {
  el.style.width    = backup.width;
  el.style.minWidth = backup.minWidth;
  el.style.maxWidth = backup.maxWidth;
  el.style.overflow = backup.overflow;
  el.style.boxSizing = backup.boxSizing;
}

function expandAncestorsForCapture(targetEl, widthPx) {
  const touched = [];
  let node = targetEl.parentElement;
  let depth = 0;

  while (node && node !== document.documentElement && depth < 20) {
    const prev = {
      el:       node,
      maxWidth: node.style.maxWidth,
      width:    node.style.width,
      minWidth: node.style.minWidth,
      overflow: node.style.overflow,
      overflowX: node.style.overflowX,
      overflowY: node.style.overflowY,
    };
    touched.push(prev);

    node.style.setProperty('max-width', 'none', 'important');
    node.style.setProperty('width', `${widthPx}px`, 'important');
    node.style.setProperty('min-width', `${widthPx}px`, 'important');
    node.style.setProperty('overflow', 'visible', 'important');
    node.style.setProperty('overflow-x', 'visible', 'important');
    node.style.setProperty('overflow-y', 'visible', 'important');
    void node.offsetWidth;

    if (node.tagName === 'MAIN') break;
    node = node.parentElement;
    depth++;
  }

  return () => {
    touched.reverse().forEach(({ el, maxWidth, width, minWidth, overflow, overflowX, overflowY }) => {
      el.style.maxWidth  = maxWidth;
      el.style.width     = width;
      el.style.minWidth  = minWidth;
      el.style.overflow  = overflow;
      el.style.overflowX = overflowX;
      el.style.overflowY = overflowY;
    });
  };
}

async function reflowCharts() {
  window.dispatchEvent(new Event('resize'));
  await waitForNextPaint();
  await new Promise(r => setTimeout(r, 350));
}

function sanitizeFileBase(name) {
  const trimmed = String(name).trim() || 'rapor';
  return trimmed.replace(/[<>:"/\\|?*\u0000-\u001f]+/g, '-').replace(/\s+/g, '-').slice(0, 120);
}

function waitForNextPaint() {
  return new Promise(resolve => {
    requestAnimationFrame(() => requestAnimationFrame(() => setTimeout(resolve, 60)));
  });
}
