import { useState } from 'react';
import { ChevronDown, Trash2, X } from 'lucide-react';
import { useTranslation } from '../../../../context/LanguageContext';
import { DRAWING_TOOLS } from '../utils/stockChartConfig';

/* ─── Drawing Toolbar Bileşeni ─── */
export default function DrawingToolbar({ activeTool, onSelectTool, onDeleteSelected, onClearAll, indicatorSlot }) {
  const { t } = useTranslation();
  const [openGroup, setOpenGroup] = useState(null);

  return (
    <div className="flex flex-wrap items-center gap-1.5 p-2 bg-gray-50 border border-gray-200 rounded-xl mb-2">
      {DRAWING_TOOLS.map(({ group, tools }) => (
        <div key={group} className="relative">
          <button
            onClick={() => setOpenGroup(openGroup === group ? null : group)}
            className={`inline-flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg text-xs font-semibold transition-all border ${
              tools.some(tool => tool.id === activeTool) || openGroup === group
                ? 'border-[#093eaa] text-[#093eaa] bg-[#093eaa]/5'
                : 'bg-white text-gray-600 border-gray-200 hover:border-[#093eaa] hover:text-[#093eaa]'
            }`}
          >
            {t(group)}
            <ChevronDown className={`w-3 h-3 transition-transform ${openGroup === group ? 'rotate-180' : ''}`} />
          </button>
          {openGroup === group && (
            <div className="absolute top-full left-0 mt-1 z-50 bg-white border border-gray-200 rounded-xl shadow-lg p-1 min-w-[180px]">
              {tools.map(tool => (
                <button
                  key={tool.id}
                  onClick={() => {
                    onSelectTool(tool.id === activeTool ? null : tool.id);
                    setOpenGroup(null);
                  }}
                  className={`w-full flex items-center gap-2.5 px-3 py-2 rounded-lg text-xs transition-all ${
                    tool.id === activeTool
                      ? 'bg-[#093eaa] text-white'
                      : 'text-gray-700 hover:bg-gray-50'
                  }`}
                >
                  <span className="w-8 text-center font-mono text-[11px] opacity-70">{tool.icon}</span>
                  <span>{t(tool.label)}</span>
                </button>
              ))}
            </div>
          )}
        </div>
      ))}

      {/* İndikatör menüsü (yalnız mum grafikte; çizim gruplarının yanında) */}
      {indicatorSlot && (
        <>
          <div className="w-px h-6 bg-gray-200 mx-1" />
          {indicatorSlot}
        </>
      )}

      {/* Ayırıcı */}
      <div className="w-px h-6 bg-gray-200 mx-1" />

      {/* Seçili çizimi sil */}
      <button
        onClick={onDeleteSelected}
        title={t('Seçili çizimi sil')}
        className="inline-flex items-center px-2 py-1.5 rounded-lg text-gray-500 hover:bg-red-50 hover:text-red-500 hover:border-red-200 transition-all border border-gray-200"
      >
        <Trash2 className="w-3.5 h-3.5" />
      </button>

      {/* Tümünü temizle */}
      <button
        onClick={onClearAll}
        title={t('Tüm çizimleri temizle')}
        className="inline-flex items-center px-2 py-1.5 rounded-lg text-gray-500 hover:bg-red-50 hover:text-red-500 hover:border-red-200 transition-all border border-gray-200"
      >
        <X className="w-3.5 h-3.5" />
      </button>

      {/* Aktif araç göstergesi */}
      {activeTool && (
        <span className="ml-auto text-xs text-[#093eaa] font-medium bg-blue-50 px-2 py-1 rounded-lg">
          {(() => { const lbl = DRAWING_TOOLS.flatMap(g => g.tools).find(it => it.id === activeTool)?.label; return lbl ? t(lbl) : activeTool; })()}
          <button onClick={() => onSelectTool(null)} className="ml-1.5 opacity-60 hover:opacity-100">✕</button>
        </span>
      )}
    </div>
  );
}

