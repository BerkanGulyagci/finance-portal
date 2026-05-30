import { useState, useMemo, useCallback } from 'react';
import { Responsive, WidthProvider } from 'react-grid-layout';
import 'react-grid-layout/css/styles.css';
import 'react-resizable/css/styles.css';
import { GripHorizontal, RotateCcw, X, Pencil, Check } from 'lucide-react';
import { useTranslation } from '../../context/LanguageContext';
import { prefGet, prefSet } from '../../api/prefs';

const RGL = WidthProvider(Responsive);

// Breakpoint başına sütun sayısı. lg/md aynı (12) → desktop davranışı korunur.
// Mobile için 2/4/8 sütun: kartlar otomatik tek sütun dizilir.
const COLS = { lg: 12, md: 12, sm: 8, xs: 4, xxs: 2 };
const BREAKPOINTS = { lg: 1200, md: 996, sm: 768, xs: 480, xxs: 0 };

const ROW_H = 30;
const DEFAULT_W = 4;   // 3 kart / satır (lg)
const DEFAULT_H = 13;

// Düzen + gizli kartlar cihazlar arası senkronlanır (prefs: localStorage + giriş yapılmışsa sunucu).
function lsGet(k) { return prefGet(k, null); }
function lsSet(k, v) { prefSet(k, v); }

// Kartları soldan sağa yerleştir, satıra sığmazsa alta sar (lg başlangıç düzeni).
function buildDefault(metaItems) {
  let x = 0, y = 0, rowMaxH = 0;
  return metaItems.map(({ key, w, h }) => {
    const W = Math.min(w || DEFAULT_W, COLS.lg);
    const H = h || DEFAULT_H;
    if (x + W > COLS.lg) { x = 0; y += rowMaxH; rowMaxH = 0; }
    const item = { i: key, x, y, w: W, h: H, minW: 3, minH: 4 };
    x += W;
    rowMaxH = Math.max(rowMaxH, H);
    return item;
  });
}

// Mobile/tablet layout'larını lg'den türet: kullanıcının lg'deki sıralamasını koru,
// her kartı tek sütunda alt alta diz. Yalnız `lg` localStorage'da saklanır.
function stackForCols(lgLayout, items, cols) {
  const sorted = lgLayout?.length
    ? [...lgLayout].sort((a, b) => (a.y - b.y) || (a.x - b.x))
    : items.map(it => ({ i: it.key, h: it.h || DEFAULT_H }));
  let y = 0;
  return sorted.map(s => {
    const meta = items.find(it => it.key === s.i);
    if (!meta) return null;
    const H = s.h || meta.h || DEFAULT_H;
    const item = { i: s.i, x: 0, y, w: cols, h: H, minW: 1, minH: 4 };
    y += H;
    return item;
  }).filter(Boolean);
}

/**
 * Serbest yerleşimli + boyutlandırılabilir kart panosu (react-grid-layout Responsive).
 * Varsayılan olarak DÜZEN SABİTTİR (kazara bozulmasın diye). "Özelleştir" ile
 * düzenleme moduna geçilir: kartlar üstteki şeritten taşınır, köşelerden
 * boyutlandırılır, ✕ ile gizlenir. Yerleşim kullanıcı bazında localStorage'da kalır.
 *
 * Responsive davranış:
 *  - lg (≥1200px) ve md (≥996px): kullanıcının kaydettiği 12-sütun düzeni — desktop'ta hiç değişmez
 *  - sm (≥768px tablet landscape): 8-sütun tek-kart-per-sıra otomatik dizilim
 *  - xs (≥480px tablet portrait): 4-sütun tek-kart-per-sıra
 *  - xxs (<480px mobile): 2-sütun tek-kart-per-sıra (her kart tüm satırı kaplar)
 *
 * Backward compat: eski tek-array localStorage formatı `lg` olarak yorumlanır.
 *
 * @param storageKey localStorage anahtarı
 * @param items      [{ key, node, w?, h?, noHide? }] — w/h: başlangıç boyutu (grid birimi)
 * @param removable  true → düzenleme modunda kartlar gizlenebilir (noHide hariç)
 * @param toolbar    Araç çubuğuna (Sıfırla/Özelleştir yanına) eklenecek ekstra içerik
 */
export default function GridBoard({ storageKey, items, removable = false, toolbar = null }) {
  const { t } = useTranslation();
  const layoutKey = `${storageKey}:rgl`;
  const hiddenKey = `${storageKey}:rgl-hidden`;
  const keys = items.map(it => it.key);
  const keysSig = keys.join('|');

  const [editMode, setEditMode] = useState(false);
  const [hidden, setHidden] = useState(() => new Set(lsGet(hiddenKey) || []));

  // Yalnız `lg` layout saklanır; mobile breakpoint'leri runtime'da türetilir.
  // Eski format (Array) ile geri uyumluluk: doğrudan lg olarak okunur.
  const [storedLg, setStoredLg] = useState(() => {
    const saved = lsGet(layoutKey);
    if (Array.isArray(saved)) return saved;
    if (saved && typeof saved === 'object' && Array.isArray(saved.lg)) return saved.lg;
    return buildDefault(items);
  });

  const metaByKey = useMemo(
    () => Object.fromEntries(items.map(it => [it.key, { w: it.w, h: it.h, noHide: it.noHide }])),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [keysSig],
  );

  const visibleKeys = keys.filter(k => !hidden.has(k));
  const hiddenCount = keys.filter(k => hidden.has(k)).length;

  // Görünür kartlar için lg layout; kayıtta olmayan yeni kart en alta eklenir.
  const lgLayout = useMemo(() => {
    const map = new Map(storedLg.map(l => [l.i, l]));
    let maxY = storedLg.reduce((m, l) => Math.max(m, (l.y || 0) + (l.h || DEFAULT_H)), 0);
    return visibleKeys.map(key => {
      if (map.has(key)) return map.get(key);
      const meta = metaByKey[key] || {};
      const item = { i: key, x: 0, y: maxY, w: meta.w || DEFAULT_W, h: meta.h || DEFAULT_H, minW: 3, minH: 4 };
      maxY += meta.h || DEFAULT_H;
      return item;
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [storedLg, visibleKeys.join('|'), metaByKey]);

  // Tüm breakpoint'ler için layouts map'i. lg/md aynı (desktop davranışı), sm/xs/xxs otomatik dizilir.
  const layouts = useMemo(() => {
    const visibleItems = items.filter(it => visibleKeys.includes(it.key));
    return {
      lg: lgLayout,
      md: lgLayout,
      sm: stackForCols(lgLayout, visibleItems, COLS.sm),
      xs: stackForCols(lgLayout, visibleItems, COLS.xs),
      xxs: stackForCols(lgLayout, visibleItems, COLS.xxs),
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [lgLayout, visibleKeys.join('|'), items]);

  // Responsive onLayoutChange: (currentLayout, allLayouts).
  // Yalnız lg'i sakla; diğer breakpoint'ler her renderda lg'den türetilir.
  const onLayoutChange = useCallback((_current, allLayouts) => {
    if (!editMode) return;
    const lg = allLayouts?.lg;
    if (!lg) return;
    setStoredLg(prev => {
      const map = new Map(prev.map(l => [l.i, l]));
      lg.forEach(l => map.set(l.i, { i: l.i, x: l.x, y: l.y, w: l.w, h: l.h, minW: l.minW, minH: l.minH }));
      const merged = [...map.values()];
      lsSet(layoutKey, merged);
      return merged;
    });
  }, [layoutKey, editMode]);

  const hide = useCallback((key) => {
    setHidden(prev => { const n = new Set(prev); n.add(key); lsSet(hiddenKey, [...n]); return n; });
  }, [hiddenKey]);

  const reset = useCallback(() => {
    setHidden(new Set()); lsSet(hiddenKey, []);
    const d = buildDefault(items);
    setStoredLg(d); lsSet(layoutKey, d);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [keysSig]);

  const nodeByKey = useMemo(() => Object.fromEntries(items.map(it => [it.key, it.node])), [items]);

  return (
    <div className="min-w-0">
      <div className="flex items-center gap-2 mb-3 flex-wrap">
        {editMode && (
          <p className="text-xs text-[#093eaa] font-medium hidden sm:block">
            {t('Düzenleme açık: kartları üstteki şeritten taşıyın, köşelerden boyutlandırın.')}
          </p>
        )}
        <div className="ml-auto flex items-center gap-2">
          {toolbar}
          <button
            onClick={() => setEditMode(v => !v)}
            className={`inline-flex items-center gap-1.5 text-xs font-semibold px-3 py-1.5 rounded-lg transition-colors ${
              editMode
                ? 'bg-[#093eaa] text-white hover:bg-[#0a2966]'
                : 'text-gray-600 bg-gray-100 hover:bg-gray-200'
            }`}
            title={editMode ? t('Düzenlemeyi bitir ve düzeni sabitle') : t('Kartların yerini ve boyutunu düzenle')}
          >
            {editMode
              ? <><Check className="w-3.5 h-3.5" /> {t('Bitir')}</>
              : <><Pencil className="w-3.5 h-3.5" /> {t('Özelleştir')}</>}
          </button>
          <button
            onClick={reset}
            className="inline-flex items-center gap-1.5 text-xs font-semibold text-gray-500 hover:text-[#093eaa] transition-colors px-2 py-1.5"
            title={t('Varsayılan düzene dön ve tüm kartları geri getir')}
          >
            <RotateCcw className="w-3.5 h-3.5" /> {t('Düzeni Sıfırla')}
            {hiddenCount > 0 && <span className="text-gray-400 font-normal"> ({hiddenCount} {t('gizli')})</span>}
          </button>
        </div>
      </div>

      <RGL
        layouts={layouts}
        cols={COLS}
        breakpoints={BREAKPOINTS}
        rowHeight={ROW_H}
        margin={[16, 16]}
        compactType={null}
        preventCollision={false}
        allowOverlap
        isResizable={editMode}
        isDraggable={editMode}
        draggableHandle=".gb-drag"
        draggableCancel=".gb-no-drag"
        resizeHandles={['se', 'sw', 'ne', 'nw']}
        onLayoutChange={onLayoutChange}
        useCSSTransforms
      >
        {visibleKeys.map(key => {
          const meta = metaByKey[key] || {};
          return (
            <div
              key={key}
              className={`group h-full flex flex-col ${editMode ? 'ring-2 ring-[#093eaa]/30 rounded-2xl' : ''}`}
            >
              {editMode && (
                <div
                  className="gb-drag relative h-6 shrink-0 flex items-center justify-center bg-[#093eaa]/10 hover:bg-[#093eaa]/20 rounded-t-2xl cursor-grab active:cursor-grabbing"
                  title={t('Sürükleyerek taşı')}
                >
                  <GripHorizontal className="w-4 h-4 text-[#093eaa]/70" />
                  {removable && !meta.noHide && (
                    <button
                      type="button"
                      onClick={() => hide(key)}
                      title={t('Gizle')}
                      className="gb-no-drag absolute right-1 top-1/2 -translate-y-1/2 p-0.5 rounded text-[#093eaa]/70 hover:text-rose-500 hover:bg-white/70 transition-colors"
                    >
                      <X className="w-4 h-4" />
                    </button>
                  )}
                </div>
              )}
              {/* İçerik kalan alanı doldurur; kart kendi içinde (DashCard) kayar → boyutlandırınca bozulmaz */}
              <div className="flex-1 min-h-0 overflow-hidden [&>*]:h-full">
                {nodeByKey[key]}
              </div>
            </div>
          );
        })}
      </RGL>
    </div>
  );
}
