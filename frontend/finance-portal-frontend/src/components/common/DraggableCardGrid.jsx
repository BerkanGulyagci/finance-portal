import { useState, useEffect } from 'react';
import { GripVertical, RotateCcw, X } from 'lucide-react';
import { useCardOrder } from '../../hooks/useCardOrder';
import { useTranslation } from '../../i18n/LanguageContext';

/**
 * Sürükle-bırak ile yeniden sıralanabilen kart ızgarası. Sıralama + gizleme kullanıcı bazında
 * localStorage'da saklanır. Tutamaçtan (grip) sürüklenir; kart içeriği etkileşimi bozulmaz.
 *
 * @param storageKey   localStorage anahtarı
 * @param defaultKeys  varsayılan kart sırası (stabil referans)
 * @param render       { key: ReactNode } — her anahtar için kart içeriği
 * @param gridClassName ızgara sınıfı
 * @param hint         sol üstte gösterilecek ipucu metni
 * @param removable    true → her kartta gizle (X) butonu; "Sıfırla" hepsini geri getirir
 */
export default function DraggableCardGrid({ storageKey, defaultKeys, render, gridClassName, hint, removable = false }) {
  const { t } = useTranslation();
  const { order, hidden, move, hide, reset } = useCardOrder(storageKey, defaultKeys);
  const [dragKey, setDragKey] = useState(null);

  // Sürükleme sırasında ekran kenarına yaklaşınca otomatik kaydır
  // (native HTML5 drag tekerlek kaydırmayı kilitler).
  useEffect(() => {
    if (!dragKey) return undefined;
    const EDGE = 110;
    const MAX_SPEED = 22;
    const onDragOver = (e) => {
      const y = e.clientY;
      const h = window.innerHeight;
      if (y < EDGE) window.scrollBy(0, -Math.ceil(((EDGE - y) / EDGE) * MAX_SPEED));
      else if (y > h - EDGE) window.scrollBy(0, Math.ceil(((y - (h - EDGE)) / EDGE) * MAX_SPEED));
    };
    window.addEventListener('dragover', onDragOver);
    return () => window.removeEventListener('dragover', onDragOver);
  }, [dragKey]);

  const visible = order.filter(key => render[key] && !hidden.has(key));
  const hiddenCount = order.filter(key => render[key] && hidden.has(key)).length;

  return (
    <div className="min-w-0">
      <div className="flex items-center gap-2 mb-3">
        {hint && <p className="text-xs text-gray-400 hidden sm:block">{hint}</p>}
        <button
          onClick={reset}
          className="ml-auto inline-flex items-center gap-1.5 text-xs font-semibold text-gray-500 hover:text-[#093eaa] transition-colors"
          title={t('Tüm kartları geri getir ve varsayılan sıralamaya dön')}
        >
          <RotateCcw className="w-3.5 h-3.5" />
          {t('Sıralamayı Sıfırla')}
          {hiddenCount > 0 && <span className="text-gray-400 font-normal"> ({hiddenCount} {t('gizli')})</span>}
        </button>
      </div>

      <div className={gridClassName}>
        {visible.map(key => (
          <div
            key={key}
            onDragOver={e => { if (dragKey && dragKey !== key) e.preventDefault(); }}
            onDrop={e => { e.preventDefault(); if (dragKey) move(dragKey, key); setDragKey(null); }}
            className={`relative min-w-0 rounded-2xl transition-all ${
              dragKey === key ? 'opacity-40'
                : dragKey ? 'ring-2 ring-dashed ring-[#093eaa]/20 ring-offset-2' : ''
            }`}
          >
            {removable && (
              <button
                type="button"
                onClick={() => hide(key)}
                title={t('Kaldır')}
                className="absolute top-3 right-10 z-20 p-1 rounded-md text-gray-300 hover:text-rose-500 hover:bg-rose-50 transition-colors"
              >
                <X className="w-4 h-4" />
              </button>
            )}
            <button
              type="button"
              draggable
              onDragStart={e => { setDragKey(key); e.dataTransfer.effectAllowed = 'move'; }}
              onDragEnd={() => setDragKey(null)}
              title={t('Sürükleyerek taşı')}
              className="absolute top-3 right-3 z-20 p-1 rounded-md text-gray-300 hover:text-[#093eaa] hover:bg-[#093eaa]/5 cursor-grab active:cursor-grabbing"
            >
              <GripVertical className="w-4 h-4" />
            </button>
            {render[key]}
          </div>
        ))}
      </div>
    </div>
  );
}
