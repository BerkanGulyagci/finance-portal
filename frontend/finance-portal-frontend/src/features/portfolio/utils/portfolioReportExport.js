/**
 * Varlık Portföyü Profesyonel Dışa Aktarma
 * ----------------------------------------
 * - Excel (.xlsx): SheetJS — sadece "Varlıklar" + "İşlemler" 2 sayfa
 *   (kullanıcı ekrandaki sekmelere uygun çıktı istedi).
 * - PDF: html-to-image + jsPDF — off-screen HTML template'i PNG'ye çevirip
 *   PDF'e basar. Browser font'undan render edildiği için Türkçe karakter
 *   sorunu yok (ı, ğ, ş, ç, ö, ü, İ tam destekli).
 *
 * Marka: Portiva navy #093eaa, yeşil kâr #059669, kırmızı zarar #dc2626.
 */

import * as XLSX from 'xlsx';
import { exportElementToPdf } from './domToPdf';

// ── Sabitler ────────────────────────────────────────────────────────────────
const BRAND_NAVY_RGB = '093eaa';

const ASSET_LABELS = {
  STOCK: 'Hisse', CRYPTO: 'Kripto', FX: 'Döviz', FUND: 'Fon',
  FUTURE: 'Vadeli', GOLD: 'Altın', COMMODITY: 'Emtia', BOND: 'DİBS',
};

// ── Yardımcılar ─────────────────────────────────────────────────────────────
const num = (v) => {
  if (v == null || v === '') return null;
  const n = typeof v === 'number' ? v : parseFloat(v);
  return Number.isFinite(n) ? n : null;
};

const fmtTr = (v, dec = 2) => {
  const n = num(v);
  if (n == null) return '-';
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

const escapeHtml = (s) => String(s ?? '')
  .replace(/&/g, '&amp;')
  .replace(/</g, '&lt;')
  .replace(/>/g, '&gt;')
  .replace(/"/g, '&quot;');

// ═══════════════════════════════════════════════════════════════════════════
// EXCEL — .xlsx
// ═══════════════════════════════════════════════════════════════════════════

function styleHeaderRow(ws, headers, rowIdx = 0) {
  headers.forEach((_, col) => {
    const cell = XLSX.utils.encode_cell({ r: rowIdx, c: col });
    if (!ws[cell]) return;
    ws[cell].s = {
      font: { bold: true, color: { rgb: 'FFFFFF' }, sz: 11 },
      fill: { fgColor: { rgb: BRAND_NAVY_RGB } },
      alignment: { horizontal: 'center', vertical: 'center' },
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

/**
 * Varlık portföyünden Excel oluşturur. Sadece "Varlıklar" + "İşlemler" 2 sayfa.
 *
 * @param {object} portfolio — PortfolioResponse
 * @param {object} [opts]
 * @param {Array}  [opts.holdings]      — ekranda görünen varlıklar (filtre uygulanmış)
 * @param {Array}  [opts.transactions]  — ekranda görünen işlemler (filtre uygulanmış)
 */
export function downloadPortfolioExcel(portfolio, opts = {}) {
  const wb = XLSX.utils.book_new();

  const holdings = opts.holdings ?? portfolio.holdings ?? [];
  const txs      = opts.transactions ?? portfolio.transactions ?? [];

  // ── Sheet 1: Varlıklar ──────────────────────────────────────────────────
  if (holdings.length > 0) {
    const headers = [
      'Sembol', 'Ad', 'Tür', 'Miktar', 'Ortalama Alış', 'Güncel Fiyat',
      'Toplam Maliyet', 'Piyasa Değeri', 'Kâr/Zarar', 'Kâr/Zarar %',
      'Para Birimi', 'İlk Alış',
    ];
    const dataRows = holdings.map((h) => {
      const cost = num(h.totalCost);
      const pl   = num(h.profitLoss);
      const pct  = cost && pl != null ? (pl / cost) * 100 : null;
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
    const ws = XLSX.utils.aoa_to_sheet([headers, ...dataRows]);
    ws['!cols'] = autoColWidths([headers, ...dataRows.map((r) => r.map((v) => v ?? ''))]);
    styleHeaderRow(ws, headers);
    XLSX.utils.book_append_sheet(wb, ws, 'Varlıklar');
  }

  // ── Sheet 2: İşlemler ───────────────────────────────────────────────────
  if (txs.length > 0) {
    const headers = ['Tarih', 'İşlem', 'Sembol', 'Tür', 'Miktar', 'Fiyat', 'Komisyon', 'Tutar'];
    const dataRows = txs.map((tx) => {
      const isBuy    = tx.transactionType === 'BUY';
      const isCoupon = tx.transactionType === 'COUPON_INCOME';
      const qty   = num(tx.quantity);
      const price = num(tx.price);
      const amt   = qty != null && price != null ? qty * price : null;
      return [
        fmtDate(tx.transactionDate),
        isCoupon ? 'KUPON' : (isBuy ? 'ALIŞ' : 'SATIŞ'),
        tx.symbol ?? '',
        assetLabel(tx.assetType),
        qty,
        price,
        num(tx.commission),
        amt,
      ];
    });
    const ws = XLSX.utils.aoa_to_sheet([headers, ...dataRows]);
    ws['!cols'] = autoColWidths([headers, ...dataRows.map((r) => r.map((v) => v ?? ''))]);
    styleHeaderRow(ws, headers);
    XLSX.utils.book_append_sheet(wb, ws, 'İşlemler');
  }

  const fname = `${safeFilenameBase(portfolio?.name)}_${stampNow()}.xlsx`;
  XLSX.writeFile(wb, fname, { compression: true });
}

// ═══════════════════════════════════════════════════════════════════════════
// PDF — html-to-image + jsPDF (off-screen DOM template)
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Türkçe karakter destekli profesyonel PDF rapor üretir.
 * Off-screen bir HTML template render eder, browser font'uyla PNG'ye çevirir,
 * sonra jsPDF ile A4'e basar (mevcut domToPdf.js utility'si kullanılır).
 */
export async function downloadPortfolioPdf(portfolio, opts = {}) {
  const holdings = opts.holdings ?? portfolio.holdings ?? [];
  const txs      = opts.transactions ?? portfolio.transactions ?? [];

  const root = document.createElement('div');
  // Off-screen container — render edilir ama görünmez
  root.style.cssText = `
    position: fixed;
    top: 0;
    left: -10000px;
    width: 794px;
    background: #ffffff;
    color: #111827;
    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
    font-size: 11px;
    line-height: 1.4;
    padding: 0;
    box-sizing: border-box;
  `;
  root.innerHTML = buildPdfHtml(portfolio, holdings, txs);
  document.body.appendChild(root);

  try {
    // html-to-image render'ının fontları yükleyip layout'u stabilize etmesi için kısa bekleme
    await new Promise((r) => setTimeout(r, 50));
    await exportElementToPdf(root, {
      fileName: `${safeFilenameBase(portfolio?.name)}_${stampNow()}`,
      scale: 2,
      marginMm: 8,
      orientation: 'p',
    });
  } finally {
    document.body.removeChild(root);
  }
}

function buildPdfHtml(portfolio, holdings, txs) {
  const isWatchlist = portfolio.portfolioType === 'WATCHLIST';
  const subtitle = `${isWatchlist ? 'İzleme Listesi' : 'Varlık Portföyü'} · ${portfolio.currency ?? 'TRY'}`;

  // Header bandı
  const header = `
    <div style="background:#093eaa;color:#fff;padding:18px 24px;display:flex;align-items:center;justify-content:space-between;">
      <div style="font-size:22px;font-weight:800;letter-spacing:-0.02em;">Portiva</div>
      <div style="text-align:right;font-size:10px;line-height:1.5;">
        <div style="font-weight:600;">${isWatchlist ? 'İzleme Listesi Raporu' : 'Portföy Raporu'}</div>
        <div style="opacity:0.85;">${escapeHtml(timestampLabel())}</div>
      </div>
    </div>
  `;

  // Başlık + alt başlık
  const title = `
    <div style="padding:20px 24px 12px;">
      <div style="font-size:22px;font-weight:800;color:#111827;margin-bottom:4px;">
        ${escapeHtml(portfolio.name ?? 'Portföy')}
      </div>
      <div style="font-size:11px;color:#6b7280;">${escapeHtml(subtitle)}</div>
    </div>
  `;

  // Özet kartlar (sadece holdings için)
  let summary = '';
  if (!isWatchlist) {
    const pl  = num(portfolio.totalProfitLoss);
    const rpl = num(portfolio.totalRealizedProfitLoss);
    const plColor  = pl  != null && pl  >= 0 ? '#059669' : '#dc2626';
    const rplColor = rpl != null && rpl >= 0 ? '#059669' : '#dc2626';
    summary = `
      <div style="padding:0 24px 16px;display:grid;grid-template-columns:repeat(4,1fr);gap:8px;">
        ${summaryCard('Toplam Maliyet',    fmtTr(portfolio.totalCost),         '#111827')}
        ${summaryCard('Piyasa Değeri',     fmtTr(portfolio.totalMarketValue),  '#111827')}
        ${summaryCard('Açık Kâr / Zarar',  fmtTr(portfolio.totalProfitLoss),   plColor)}
        ${summaryCard('Gerçekleşen K/Z',   fmtTr(portfolio.totalRealizedProfitLoss), rplColor)}
      </div>
    `;
  }

  // İzleme listesi tablosu
  let watchlistTable = '';
  const watchItems = portfolio.watchlistItems ?? [];
  if (isWatchlist && watchItems.length > 0) {
    const rows = watchItems.map((w, i) => {
      const dailyPct  = num(w.dailyChangePercent);
      const pctColor  = dailyPct != null && dailyPct >= 0 ? '#059669' : '#dc2626';
      return tableRow([
        escapeHtml(w.symbol ?? ''),
        escapeHtml(assetLabel(w.assetType)),
        { text: fmtTr(w.currentPrice, 4),       align: 'right' },
        { text: fmtTr(dailyPct, 2),             align: 'right', color: pctColor, bold: true },
        { text: fmtTr(w.fiftyTwoWeekHigh, 2),   align: 'right' },
        { text: fmtTr(w.fiftyTwoWeekLow, 2),    align: 'right' },
        escapeHtml(w.currency ?? 'TRY'),
      ], i);
    }).join('');
    watchlistTable = sectionTable('İzleme Listesi Sembolleri',
      ['Sembol', 'Tür', 'Güncel Fiyat', 'Günlük %', '52H En Yüksek', '52H En Düşük', 'Para Br.'],
      rows);
  }

  // Holdings tablosu
  let holdingsTable = '';
  if (!isWatchlist && holdings.length > 0) {
    const rows = holdings.map((h, i) => {
      const cost = num(h.totalCost);
      const pl   = num(h.profitLoss);
      const pct  = cost && pl != null ? (pl / cost) * 100 : null;
      const plColor = pl != null && pl >= 0 ? '#059669' : '#dc2626';
      return tableRow([
        escapeHtml(h.symbol ?? ''),
        escapeHtml(assetLabel(h.assetType)),
        { text: fmtTr(h.totalQuantity, 4), align: 'right' },
        { text: fmtTr(h.averageCost, 4),   align: 'right' },
        { text: fmtTr(h.currentPrice, 4),  align: 'right' },
        { text: fmtTr(cost, 2),            align: 'right' },
        { text: fmtTr(h.marketValue, 2),   align: 'right' },
        { text: fmtTr(pl, 2),              align: 'right', color: plColor, bold: true },
        { text: pct != null ? `${fmtTr(pct, 2)}%` : '-', align: 'right', color: plColor, bold: true },
      ], i);
    }).join('');
    holdingsTable = sectionTable('Varlıklar',
      ['Sembol', 'Tür', 'Miktar', 'Ort. Alış', 'Güncel', 'Maliyet', 'Değer', 'K/Z', '%'],
      rows);
  }

  // İşlemler tablosu
  let txTable = '';
  if (txs.length > 0) {
    const rows = txs.map((tx, i) => {
      const isBuy    = tx.transactionType === 'BUY';
      const isCoupon = tx.transactionType === 'COUPON_INCOME';
      const txLabel  = isCoupon ? 'KUPON' : (isBuy ? 'ALIŞ' : 'SATIŞ');
      const txColor  = isCoupon ? '#2563eb' : (isBuy ? '#059669' : '#dc2626');
      return tableRow([
        escapeHtml(fmtDate(tx.transactionDate)),
        { text: txLabel, color: txColor, bold: true },
        escapeHtml(tx.symbol ?? ''),
        escapeHtml(assetLabel(tx.assetType)),
        { text: fmtTr(tx.quantity, 4),   align: 'right' },
        { text: fmtTr(tx.price, 4),      align: 'right' },
        { text: fmtTr(tx.commission, 2), align: 'right' },
      ], i);
    }).join('');
    txTable = sectionTable('İşlemler',
      ['Tarih', 'İşlem', 'Sembol', 'Tür', 'Miktar', 'Fiyat', 'Komisyon'],
      rows);
  }

  // Footer
  const footer = `
    <div style="padding:14px 24px;margin-top:8px;border-top:1px solid #e5e7eb;display:flex;justify-content:space-between;font-size:9px;color:#9ca3af;">
      <span>${escapeHtml(portfolio.name ?? '')} · Portiva tarafından oluşturuldu</span>
      <span>${new Date().toLocaleDateString('tr-TR')}</span>
    </div>
  `;

  return header + title + summary + watchlistTable + holdingsTable + txTable + footer;
}

function summaryCard(label, value, color) {
  return `
    <div style="border:1px solid #e5e7eb;border-radius:6px;padding:8px 10px;">
      <div style="font-size:8px;font-weight:700;letter-spacing:0.05em;color:#9ca3af;text-transform:uppercase;margin-bottom:4px;">
        ${escapeHtml(label)}
      </div>
      <div style="font-size:14px;font-weight:700;color:${color};">${escapeHtml(value || '-')}</div>
    </div>
  `;
}

function sectionTable(title, headers, rowsHtml) {
  const thCells = headers.map(h =>
    `<th style="text-align:left;padding:7px 8px;font-size:9px;font-weight:700;color:#fff;background:#093eaa;border-right:1px solid rgba(255,255,255,0.15);">${escapeHtml(h)}</th>`
  ).join('');
  return `
    <div style="padding:0 24px 16px;">
      <div style="font-size:12px;font-weight:700;color:#111827;margin-bottom:6px;">${escapeHtml(title)}</div>
      <table style="width:100%;border-collapse:collapse;border:1px solid #e5e7eb;border-radius:4px;overflow:hidden;">
        <thead>
          <tr>${thCells}</tr>
        </thead>
        <tbody>
          ${rowsHtml}
        </tbody>
      </table>
    </div>
  `;
}

function tableRow(cells, rowIdx) {
  const bg = rowIdx % 2 === 0 ? '#ffffff' : '#f9fafb';
  const tdHtml = cells.map((cell) => {
    if (typeof cell === 'string') {
      return `<td style="padding:6px 8px;font-size:9px;border-top:1px solid #f3f4f6;color:#374151;">${cell}</td>`;
    }
    const align = cell.align || 'left';
    const color = cell.color || '#374151';
    const weight = cell.bold ? '600' : '400';
    return `<td style="padding:6px 8px;font-size:9px;border-top:1px solid #f3f4f6;text-align:${align};color:${color};font-weight:${weight};">${escapeHtml(cell.text ?? '')}</td>`;
  }).join('');
  return `<tr style="background:${bg};">${tdHtml}</tr>`;
}
