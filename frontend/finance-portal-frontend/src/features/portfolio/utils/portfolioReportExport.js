/**
 * Portföy ve İzleme Listesi Profesyonel Dışa Aktarma
 * --------------------------------------------------
 * Hem .xlsx (gerçek Excel) hem .pdf (native, jsPDF) çıktı.
 *
 * - Excel: SheetJS (xlsx) — gerçek hücre tipleri, otomatik kolon genişliği,
 *   başlık formatı, formül-uyumlu sayısal alanlar.
 * - PDF: jsPDF native — Portiva branding header, özet kart, renk kodlu
 *   tablolar (yeşil kâr / kırmızı zarar), sayfa numaralı footer.
 *
 * Marka:
 *   PRIMARY = #093eaa (Portiva mavisi)
 *   SUCCESS = #059669 (yeşil — kâr)
 *   DANGER  = #dc2626 (kırmızı — zarar)
 */

import * as XLSX from 'xlsx';
import { jsPDF } from 'jspdf';

// ── Sabitler ────────────────────────────────────────────────────────────────
const BRAND_NAVY = '093eaa';
const COLOR_SUCCESS = [5, 150, 105];
const COLOR_DANGER = [220, 38, 38];
const COLOR_TEXT_PRIMARY = [17, 24, 39];
const COLOR_TEXT_MUTED = [107, 114, 128];
const COLOR_BORDER = [229, 231, 235];
const COLOR_BG_HEADER = [9, 62, 170]; // Portiva navy as RGB

const ASSET_LABELS = {
  STOCK: 'Hisse', CRYPTO: 'Kripto', FX: 'Döviz', FUND: 'Fon',
  FUTURE: 'Vadeli', GOLD: 'Altın', COMMODITY: 'Emtia', BOND: 'DİBS',
};

// ── Yardımcı fonksiyonlar ───────────────────────────────────────────────────
const num = (v) => {
  if (v == null || v === '') return null;
  const n = typeof v === 'number' ? v : parseFloat(v);
  return Number.isFinite(n) ? n : null;
};

const fmtTr = (v, dec = 2) => {
  const n = num(v);
  if (n == null) return '';
  return n.toLocaleString('tr-TR', { minimumFractionDigits: dec, maximumFractionDigits: dec });
};

const fmtDate = (s) => {
  if (!s) return '';
  return String(s).replace('T', ' ').slice(0, 19);
};

const assetLabel = (t) => ASSET_LABELS[t] ?? String(t ?? '');

export const safeFilenameBase = (name) => {
  const raw = (name || 'portfoy').trim() || 'portfoy';
  return raw.replace(/[/\\?%*:|"<>]/g, '-').slice(0, 80);
};

const stampNow = () => new Date().toISOString().slice(0, 10);
const timestampLabel = () => {
  const d = new Date();
  return d.toLocaleString('tr-TR', { dateStyle: 'long', timeStyle: 'short' });
};

// ═══════════════════════════════════════════════════════════════════════════
// EXCEL — .xlsx (SheetJS)
// ═══════════════════════════════════════════════════════════════════════════

function styleHeaderRow(ws, headers, rowIdx = 0) {
  headers.forEach((_, col) => {
    const cell = XLSX.utils.encode_cell({ r: rowIdx, c: col });
    if (!ws[cell]) return;
    ws[cell].s = {
      font: { bold: true, color: { rgb: 'FFFFFF' }, sz: 11 },
      fill: { fgColor: { rgb: BRAND_NAVY } },
      alignment: { horizontal: 'center', vertical: 'center' },
      border: {
        top: { style: 'thin', color: { rgb: 'E5E7EB' } },
        bottom: { style: 'thin', color: { rgb: 'E5E7EB' } },
        left: { style: 'thin', color: { rgb: 'E5E7EB' } },
        right: { style: 'thin', color: { rgb: 'E5E7EB' } },
      },
    };
  });
}

function autoColWidths(rows) {
  if (!rows.length) return [];
  const widths = rows[0].map(() => 10);
  rows.forEach((row) => {
    row.forEach((cell, idx) => {
      const len = String(cell ?? '').length;
      if (len > widths[idx]) widths[idx] = Math.min(len + 2, 40);
    });
  });
  return widths.map((w) => ({ wch: w }));
}

function buildPortfolioWorkbook(portfolio) {
  const wb = XLSX.utils.book_new();

  // ── Sheet 1: Özet ─────────────────────────────────────────────────────
  const summaryRows = [
    ['PORTİVA — PORTFÖY RAPORU', ''],
    ['', ''],
    ['Portföy Adı', portfolio.name ?? ''],
    ['Tip', portfolio.portfolioType === 'WATCHLIST' ? 'İzleme Listesi' : 'Varlık Portföyü'],
    ['Para Birimi', portfolio.currency ?? 'TRY'],
    ['Rapor Tarihi', timestampLabel()],
    ['', ''],
    ['MALİ ÖZET', ''],
    ['Toplam Maliyet',     num(portfolio.totalCost)],
    ['Piyasa Değeri',      num(portfolio.totalMarketValue)],
    ['Açık Kâr/Zarar',     num(portfolio.totalProfitLoss)],
    ['Gerçekleşen Kâr/Zarar', num(portfolio.totalRealizedProfitLoss)],
    ['Reel Kâr/Zarar (TÜFE arınmış)', num(portfolio.totalRealProfitLoss)],
  ];
  const summaryWs = XLSX.utils.aoa_to_sheet(summaryRows);
  summaryWs['!cols'] = [{ wch: 32 }, { wch: 24 }];
  // Başlık satırı: kalın + büyük
  if (summaryWs['A1']) {
    summaryWs['A1'].s = {
      font: { bold: true, sz: 16, color: { rgb: BRAND_NAVY } },
      alignment: { horizontal: 'left' },
    };
  }
  ['A8'].forEach((addr) => {
    if (summaryWs[addr]) {
      summaryWs[addr].s = {
        font: { bold: true, sz: 12, color: { rgb: 'FFFFFF' } },
        fill: { fgColor: { rgb: BRAND_NAVY } },
      };
    }
  });
  XLSX.utils.book_append_sheet(wb, summaryWs, 'Özet');

  // ── Sheet 2: Varlıklar ────────────────────────────────────────────────
  const holdings = portfolio.holdings ?? [];
  if (holdings.length > 0) {
    const headers = [
      'Sembol', 'Ad', 'Tür', 'Miktar', 'Ortalama Alış', 'Güncel Fiyat',
      'Toplam Maliyet', 'Piyasa Değeri', 'Kâr/Zarar', 'Kâr/Zarar %',
      'Para Birimi', 'İlk Alış',
    ];
    const dataRows = holdings.map((h) => {
      const cost = num(h.totalCost);
      const pl = num(h.profitLoss);
      const pct = cost && pl != null ? (pl / cost) * 100 : null;
      return [
        h.symbol ?? '',
        h.name ?? h.symbol ?? '',
        assetLabel(h.assetType),
        num(h.totalQuantity),
        num(h.averageCost),
        num(h.currentPrice),
        cost,
        num(h.marketValue),
        pl,
        pct,
        h.currency ?? 'TRY',
        h.firstBuyDate ? String(h.firstBuyDate).slice(0, 10) : '',
      ];
    });
    const holdingsWs = XLSX.utils.aoa_to_sheet([headers, ...dataRows]);
    holdingsWs['!cols'] = autoColWidths([headers, ...dataRows.map((r) => r.map((v) => v ?? ''))]);
    styleHeaderRow(holdingsWs, headers);
    XLSX.utils.book_append_sheet(wb, holdingsWs, 'Varlıklar');
  }

  // ── Sheet 3: İşlemler ─────────────────────────────────────────────────
  const txs = portfolio.transactions ?? [];
  if (txs.length > 0) {
    const headers = [
      'Tarih', 'İşlem', 'Sembol', 'Tür', 'Miktar', 'Fiyat', 'Komisyon', 'Tutar',
    ];
    const dataRows = txs.map((tx) => {
      const isBuy = tx.transactionType === 'BUY';
      const isCoupon = tx.transactionType === 'COUPON_INCOME';
      const qty = num(tx.quantity);
      const price = num(tx.price);
      const amount = qty != null && price != null ? qty * price : null;
      return [
        fmtDate(tx.transactionDate),
        isCoupon ? 'KUPON' : (isBuy ? 'ALIŞ' : 'SATIŞ'),
        tx.symbol ?? '',
        assetLabel(tx.assetType),
        qty,
        price,
        num(tx.commission),
        amount,
      ];
    });
    const txWs = XLSX.utils.aoa_to_sheet([headers, ...dataRows]);
    txWs['!cols'] = autoColWidths([headers, ...dataRows.map((r) => r.map((v) => v ?? ''))]);
    styleHeaderRow(txWs, headers);
    XLSX.utils.book_append_sheet(wb, txWs, 'İşlemler');
  }

  // ── Sheet 4 (sadece izleme listesi için): İzleme Sembolleri ──────────
  const watchItems = portfolio.watchlistItems ?? [];
  if (watchItems.length > 0) {
    const headers = [
      'Sembol', 'Tür', 'Güncel Fiyat', 'Günlük Değişim %', '52 Hafta En Yüksek',
      '52 Hafta En Düşük', 'Para Birimi', 'Eklenme Tarihi',
    ];
    const dataRows = watchItems.map((w) => [
      w.symbol ?? '',
      assetLabel(w.assetType),
      num(w.currentPrice),
      num(w.dailyChangePercent),
      num(w.fiftyTwoWeekHigh),
      num(w.fiftyTwoWeekLow),
      w.currency ?? 'TRY',
      w.addedDate ? String(w.addedDate).slice(0, 10) : '',
    ]);
    const watchWs = XLSX.utils.aoa_to_sheet([headers, ...dataRows]);
    watchWs['!cols'] = autoColWidths([headers, ...dataRows.map((r) => r.map((v) => v ?? ''))]);
    styleHeaderRow(watchWs, headers);
    XLSX.utils.book_append_sheet(wb, watchWs, 'İzleme Listesi');
  }

  return wb;
}

export function downloadPortfolioExcel(portfolio) {
  const wb = buildPortfolioWorkbook(portfolio);
  const fname = `${safeFilenameBase(portfolio?.name)}_${stampNow()}.xlsx`;
  XLSX.writeFile(wb, fname, { compression: true });
}

// ═══════════════════════════════════════════════════════════════════════════
// PDF — Native jsPDF
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Çoklu sayfalı stilize portföy/izleme listesi PDF raporu üretir.
 */
function generatePortfolioPdf(portfolio) {
  const doc = new jsPDF({ unit: 'mm', format: 'a4', orientation: 'p' });
  const pageWidth = doc.internal.pageSize.getWidth();
  const pageHeight = doc.internal.pageSize.getHeight();
  const margin = 14;
  const contentWidth = pageWidth - margin * 2;

  const isWatchlist = portfolio.portfolioType === 'WATCHLIST';
  let pageNum = 1;
  const totalPagesPlaceholder = '{P}';

  // ── Sayfa Başlığı + Footer Yardımcıları ────────────────────────────
  function drawHeader() {
    // Üst bant — navy
    doc.setFillColor(...COLOR_BG_HEADER);
    doc.rect(0, 0, pageWidth, 22, 'F');

    // Logo placeholder (sadece metin — Portiva)
    doc.setTextColor(255, 255, 255);
    doc.setFont('helvetica', 'bold');
    doc.setFontSize(18);
    doc.text('Portiva', margin, 14);

    // Sağ üst — rapor tipi
    doc.setFontSize(9);
    doc.setFont('helvetica', 'normal');
    const reportType = isWatchlist ? 'İzleme Listesi Raporu' : 'Portföy Raporu';
    doc.text(reportType, pageWidth - margin, 10, { align: 'right' });
    doc.text(timestampLabel(), pageWidth - margin, 15, { align: 'right' });
  }

  function drawFooter() {
    const y = pageHeight - 10;
    doc.setDrawColor(...COLOR_BORDER);
    doc.setLineWidth(0.2);
    doc.line(margin, y - 4, pageWidth - margin, y - 4);

    doc.setFont('helvetica', 'normal');
    doc.setFontSize(8);
    doc.setTextColor(...COLOR_TEXT_MUTED);
    doc.text(`${portfolio.name ?? ''} · Portiva tarafından oluşturuldu`, margin, y);
    doc.text(`Sayfa ${pageNum} / ${totalPagesPlaceholder}`, pageWidth - margin, y, { align: 'right' });
  }

  function ensurePage(yCursor, neededHeight) {
    if (yCursor + neededHeight > pageHeight - 18) {
      drawFooter();
      doc.addPage();
      pageNum++;
      drawHeader();
      return 30;
    }
    return yCursor;
  }

  // ── İlk sayfa ──────────────────────────────────────────────────────────
  drawHeader();
  let y = 30;

  // Başlık + portföy adı
  doc.setTextColor(...COLOR_TEXT_PRIMARY);
  doc.setFont('helvetica', 'bold');
  doc.setFontSize(18);
  doc.text(portfolio.name ?? 'Portföy', margin, y);
  y += 6;

  doc.setFont('helvetica', 'normal');
  doc.setFontSize(10);
  doc.setTextColor(...COLOR_TEXT_MUTED);
  const subtitle = `${isWatchlist ? 'İzleme Listesi' : 'Varlık Portföyü'} · ${portfolio.currency ?? 'TRY'}`;
  doc.text(subtitle, margin, y);
  y += 8;

  // ── Özet kutuları (sadece holdings için) ──────────────────────────
  if (!isWatchlist) {
    const summary = [
      { label: 'Toplam Maliyet',   value: fmtTr(portfolio.totalCost),         color: COLOR_TEXT_PRIMARY },
      { label: 'Piyasa Değeri',    value: fmtTr(portfolio.totalMarketValue),  color: COLOR_TEXT_PRIMARY },
      { label: 'Açık Kâr/Zarar',   value: fmtTr(portfolio.totalProfitLoss),
        color: num(portfolio.totalProfitLoss) >= 0 ? COLOR_SUCCESS : COLOR_DANGER },
      { label: 'Gerçekleşen K/Z', value: fmtTr(portfolio.totalRealizedProfitLoss),
        color: num(portfolio.totalRealizedProfitLoss) >= 0 ? COLOR_SUCCESS : COLOR_DANGER },
    ];
    const boxWidth = contentWidth / 4 - 2;
    const boxHeight = 18;
    summary.forEach((box, i) => {
      const x = margin + i * (boxWidth + 2.5);
      // Çerçeve
      doc.setDrawColor(...COLOR_BORDER);
      doc.setLineWidth(0.3);
      doc.roundedRect(x, y, boxWidth, boxHeight, 1.5, 1.5);
      // Etiket
      doc.setFont('helvetica', 'normal');
      doc.setFontSize(7);
      doc.setTextColor(...COLOR_TEXT_MUTED);
      doc.text(box.label.toUpperCase(), x + 2.5, y + 5);
      // Değer
      doc.setFont('helvetica', 'bold');
      doc.setFontSize(11);
      doc.setTextColor(...box.color);
      doc.text(box.value || '0,00', x + 2.5, y + 13);
    });
    y += boxHeight + 8;
  }

  // ── Tablo çizici (native jsPDF) ────────────────────────────────────
  function drawTable(title, headers, rows, options = {}) {
    const { colWidths } = options;
    const widths = colWidths || headers.map(() => contentWidth / headers.length);

    // Başlık
    y = ensurePage(y, 14);
    doc.setFont('helvetica', 'bold');
    doc.setFontSize(11);
    doc.setTextColor(...COLOR_TEXT_PRIMARY);
    doc.text(title, margin, y);
    y += 4;

    // Header arka planı
    doc.setFillColor(...COLOR_BG_HEADER);
    doc.rect(margin, y, contentWidth, 7, 'F');

    // Header metni
    doc.setFont('helvetica', 'bold');
    doc.setFontSize(8);
    doc.setTextColor(255, 255, 255);
    let cursorX = margin + 2;
    headers.forEach((h, i) => {
      doc.text(h, cursorX, y + 4.8);
      cursorX += widths[i];
    });
    y += 7;

    // Satırlar
    doc.setFont('helvetica', 'normal');
    doc.setFontSize(8);
    rows.forEach((row, rowIdx) => {
      y = ensurePage(y, 7);
      // Zebra
      if (rowIdx % 2 === 0) {
        doc.setFillColor(249, 250, 251);
        doc.rect(margin, y, contentWidth, 6, 'F');
      }
      cursorX = margin + 2;
      row.forEach((cell, cellIdx) => {
        const value = cell?.value ?? cell ?? '';
        const color = cell?.color || COLOR_TEXT_PRIMARY;
        const align = cell?.align || 'left';
        doc.setTextColor(...color);
        const text = String(value);
        const cellW = widths[cellIdx] - 3;
        const truncated = doc.getStringUnitWidth(text) * 8 / doc.internal.scaleFactor > cellW
          ? doc.splitTextToSize(text, cellW)[0]
          : text;
        if (align === 'right') {
          doc.text(truncated, cursorX + widths[cellIdx] - 4, y + 4, { align: 'right' });
        } else {
          doc.text(truncated, cursorX, y + 4);
        }
        cursorX += widths[cellIdx];
      });
      y += 6;
    });
    // Tablo alt çizgisi
    doc.setDrawColor(...COLOR_BORDER);
    doc.setLineWidth(0.3);
    doc.line(margin, y, margin + contentWidth, y);
    y += 8;
  }

  // ── İzleme listesi tablosu ─────────────────────────────────────────
  const watchItems = portfolio.watchlistItems ?? [];
  if (isWatchlist && watchItems.length > 0) {
    const headers = ['Sembol', 'Tür', 'Güncel Fiyat', 'Günlük %', '52H En Yük.', '52H En Düş.', 'Para Br.'];
    const colW = [28, 22, 28, 22, 28, 28, 25];
    const rows = watchItems.map((w) => [
      w.symbol ?? '',
      assetLabel(w.assetType),
      { value: fmtTr(w.currentPrice, 4), align: 'right' },
      {
        value: fmtTr(w.dailyChangePercent, 2),
        color: num(w.dailyChangePercent) >= 0 ? COLOR_SUCCESS : COLOR_DANGER,
        align: 'right',
      },
      { value: fmtTr(w.fiftyTwoWeekHigh, 2), align: 'right' },
      { value: fmtTr(w.fiftyTwoWeekLow, 2), align: 'right' },
      w.currency ?? 'TRY',
    ]);
    drawTable('İzleme Listesi Sembolleri', headers, rows, { colWidths: colW });
  }

  // ── Varlıklar tablosu (holdings) ───────────────────────────────────
  const holdings = portfolio.holdings ?? [];
  if (!isWatchlist && holdings.length > 0) {
    const headers = ['Sembol', 'Tür', 'Miktar', 'Ort. Alış', 'Güncel', 'Maliyet', 'Değer', 'K/Z', '%'];
    const colW = [22, 18, 24, 22, 22, 24, 24, 22, 18];
    const rows = holdings.map((h) => {
      const cost = num(h.totalCost);
      const pl = num(h.profitLoss);
      const pct = cost && pl != null ? (pl / cost) * 100 : null;
      return [
        h.symbol ?? '',
        assetLabel(h.assetType),
        { value: fmtTr(h.totalQuantity, 4), align: 'right' },
        { value: fmtTr(h.averageCost, 4), align: 'right' },
        { value: fmtTr(h.currentPrice, 4), align: 'right' },
        { value: fmtTr(cost, 2), align: 'right' },
        { value: fmtTr(h.marketValue, 2), align: 'right' },
        {
          value: fmtTr(pl, 2),
          color: pl != null && pl >= 0 ? COLOR_SUCCESS : COLOR_DANGER,
          align: 'right',
        },
        {
          value: pct != null ? `${fmtTr(pct, 2)}%` : '',
          color: pct != null && pct >= 0 ? COLOR_SUCCESS : COLOR_DANGER,
          align: 'right',
        },
      ];
    });
    drawTable('Varlıklar', headers, rows, { colWidths: colW });
  }

  // ── İşlemler tablosu ──────────────────────────────────────────────
  const txs = portfolio.transactions ?? [];
  if (txs.length > 0) {
    const headers = ['Tarih', 'İşlem', 'Sembol', 'Tür', 'Miktar', 'Fiyat', 'Komisyon'];
    const colW = [34, 18, 26, 22, 24, 26, 24];
    const rows = txs.map((tx) => {
      const isBuy = tx.transactionType === 'BUY';
      const isCoupon = tx.transactionType === 'COUPON_INCOME';
      const txLabel = isCoupon ? 'KUPON' : (isBuy ? 'ALIŞ' : 'SATIŞ');
      const txColor = isCoupon ? [37, 99, 235] : (isBuy ? COLOR_SUCCESS : COLOR_DANGER);
      return [
        fmtDate(tx.transactionDate),
        { value: txLabel, color: txColor },
        tx.symbol ?? '',
        assetLabel(tx.assetType),
        { value: fmtTr(tx.quantity, 4), align: 'right' },
        { value: fmtTr(tx.price, 4), align: 'right' },
        { value: fmtTr(tx.commission, 2), align: 'right' },
      ];
    });
    drawTable('İşlemler', headers, rows, { colWidths: colW });
  }

  // İlk sayfa footer'ı
  drawFooter();

  // Toplam sayfa sayısını {P} placeholder'larında güncelle
  const totalPages = doc.internal.getNumberOfPages();
  for (let p = 1; p <= totalPages; p++) {
    doc.setPage(p);
    // Footer'daki "Sayfa X / {P}" placeholder'ını güncellemek için yeniden çiz
    doc.setFillColor(255, 255, 255);
    doc.rect(pageWidth - margin - 30, pageHeight - 14, 30, 6, 'F');
    doc.setFont('helvetica', 'normal');
    doc.setFontSize(8);
    doc.setTextColor(...COLOR_TEXT_MUTED);
    doc.text(`Sayfa ${p} / ${totalPages}`, pageWidth - margin, pageHeight - 10, { align: 'right' });
  }

  return doc;
}

export function downloadPortfolioPdf(portfolio) {
  const doc = generatePortfolioPdf(portfolio);
  const fname = `${safeFilenameBase(portfolio?.name)}_${stampNow()}.pdf`;
  doc.save(fname);
}
