import { useState, useMemo, useCallback } from 'react';
import GridLayout, { WidthProvider } from 'react-grid-layout';
import 'react-grid-layout/css/styles.css';
import 'react-resizable/css/styles.css';
import { GripHorizontal, RotateCcw, X, Pencil, Check } from 'lucide-react';
import { useTranslation } from '../../i18n/LanguageContext';
import { prefGet, prefSet } from '../../api/prefs';

const RGL = WidthProvider(GridLayout);

const COLS = 12;
const ROW_H = 30;
const DEFAULT_W = 4;   // 3 kart / satır
const DEFAULT_H = 13;

// Düzen + gizli kartlar cihazlar arası senkronlanır (prefs: localStorage + giriş yapılmışsa sunucu).
function lsGet(k) { return prefGet(k, null); }
function lsSet(k, v) { prefSet(k, v); }

// Kartları soldan sağa yerleştir, satıra sığmazsa alta sar (serbest yerleşim için başlangıç düzeni).
function buildDefault(metaItems) {
  let x = 0, y = 0, rowMaxH = 0;
  return metaItems.map(({ key, w, h }) => {
    const W = Math.min(w || DEFAULT_W, COLS);
    const H = h || DEFAULT_H;
    if (x + W > COLS) { x = 0; y += rowMaxH; rowMaxH = 0; }
    const item = { i: key, x, y, w: W, h: H, minW: 3, minH: 4 };
    x += W;
    rowMaxH = Math.max(rowMaxH, H);
    return item;
  });
}

/**
 * Serbest yerleşimli + boyutlandırılabilir kart panosu (react-grid-layout).
 * Varsayılan olarak DÜZEN SABİTTİR (kazara bozulmasın diye). "Özelleştir" ile
 * düzenleme moduna geçilir: kartlar üstteki şeritten taşınır, köşelerden
 * boyutlandırılır, ✕ ile gizlenir. Yerleşim kullanıcı bazında localStorage'da kalır.
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
  const [layout, setLayout] = useState(() => {
    const saved = lsGet(layoutKey);
    return Array.isArray(saved) ? saved : buildDefault(items);
  });

  const metaByKey = useMemo(
    () => Object.fromEntries(items.map(it => [it.key, { w: it.w, h: it.h, noHide: it.noHide }])),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [keysSig],
  );

  const visibleKeys = keys.filter(k => !hidden.has(k));
  const hiddenCount = keys.filter(k => hidden.has(k)).length;

  // Görünür kartlar için yerleşim; kayıtta olmayan yeni kart en alta eklenir.
  const effectiveLayout = useMemo(() => {
    const map = new Map(layout.map(l => [l.i, l]));
    let maxY = layout.reduce((m, l) => Math.max(m, (l.y || 0) + (l.h || DEFAULT_H)), 0);
    return visibleKeys.map(key => {
      if (map.has(key)) return map.get(key);
      const meta = metaByKey[key] || {};
      const item = { i: key, x: 0, y: maxY, w: meta.w || DEFAULT_W, h: meta.h || DEFAULT_H, minW: 3, minH: 4 };
      maxY += meta.h || DEFAULT_H;
      return item;
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [layout, visibleKeys.join('|'), metaByKey]);

  const onLayoutChange = useCallback((current) => {
    if (!editMode) return; // sadece düzenleme modunda kaydet → sabitlik
    setLayout(prev => {
      const map = new Map(prev.map(l => [l.i, l]));
      current.forEach(l => map.set(l.i, { i: l.i, x: l.x, y: l.y, w: l.w, h: l.h, minW: l.minW, minH: l.minH }));
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
    setLayout(d); lsSet(layoutKey, d);
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
        layout={effectiveLayout}
        cols={COLS}
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
