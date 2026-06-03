import { useState } from 'react';
import { Settings2, ChevronDown, ChevronUp } from 'lucide-react';
import { useTranslation } from '../../../context/LanguageContext';
import { ALL_COLS, GROUP_ORDER, MAX_COLS, DEFAULT_KEYS } from '../utils/holdingsTableUtils';

// ── Sütun düzenleyici (inline panel) ─────────────────────────────────────────

export default function ColumnEditor({ open, onToggle, selected, onChange }) {
  const { t } = useTranslation();
  const [warn, setWarn] = useState(false);

  function toggle(key) {
    if (selected.includes(key)) {
      if (selected.length <= 1) return;
      onChange(selected.filter(k => k !== key));
      setWarn(false);
    } else {
      if (selected.length >= MAX_COLS) {
        setWarn(true);
        return;
      }
      onChange([...selected, key]);
      setWarn(false);
    }
  }

  const byGroup = {};
  for (const col of ALL_COLS) {
    (byGroup[col.group] ??= []).push(col);
  }

  return (
    <>
      {/* Toolbar satırı */}
      <div className="flex items-center justify-between px-4 py-2 border-b border-gray-100">
        <span className="text-xs text-gray-400">
          {t('{count} sütun görüntüleniyor', { count: selected.length })}
        </span>
        <button
          type="button"
          onClick={() => { onToggle(); setWarn(false); }}
          className={`flex items-center gap-1.5 px-3 py-1.5 text-xs font-semibold border rounded-lg transition-colors
            ${open
              ? 'bg-[#093eaa] text-white border-[#093eaa]'
              : 'text-gray-500 border-gray-200 hover:border-gray-300 hover:bg-gray-50'}`}
        >
          <Settings2 className="w-3.5 h-3.5" />
          {t('Düzenle')}
          {open ? <ChevronUp className="w-3 h-3" /> : <ChevronDown className="w-3 h-3" />}
        </button>
      </div>

      {/* Inline panel — taşmaz, overflow-hidden'dan etkilenmez */}
      {open && (
        <div className="border-b border-gray-200 bg-gray-50 px-4 py-4">
          <div className="flex items-center justify-between mb-3">
            <span className="text-sm font-bold text-gray-800">{t('Sütunları Düzenle')}</span>
            <div className="flex items-center gap-3">
              <span className="text-xs text-gray-400">
                {t('Seçili:')}{' '}
                <span className={selected.length >= MAX_COLS ? 'text-amber-600 font-bold' : 'font-semibold'}>
                  {selected.length}
                </span>{' '}
                / {MAX_COLS}
              </span>
              <button
                type="button"
                onClick={() => { onChange(DEFAULT_KEYS); setWarn(false); }}
                className="text-xs text-[#093eaa] hover:underline font-medium"
              >
                {t('Sıfırla')}
              </button>
            </div>
          </div>

          {warn && (
            <p className="text-xs text-amber-700 bg-amber-50 border border-amber-200 rounded-lg px-3 py-2 mb-3">
              {t('En fazla {max} sütun seçilebilir.', { max: MAX_COLS })}
            </p>
          )}

          {/* Gruplar — hepsi yan yana; dar ekranda az öğeli gruplar alt satıra kayar */}
          <div className="grid grid-cols-2 sm:grid-cols-4 lg:grid-cols-7 gap-x-4 gap-y-5 items-start">
            {GROUP_ORDER.map(group => {
              const cols = byGroup[group];
              if (!cols?.length) return null;
              return (
                <div key={group}>
                  <p className="text-xs font-bold text-gray-400 uppercase tracking-wider mb-2">{t(group)}</p>
                  <div className="space-y-1">
                    {cols.map(col => {
                      const checked  = selected.includes(col.key);
                      const disabled = !checked && selected.length >= MAX_COLS;
                      return (
                        <label
                          key={col.key}
                          title={col.hint ? t(col.hint) : undefined}
                          className={`flex items-start gap-2 rounded-md px-2 py-1 cursor-pointer transition-colors select-none
                            ${checked  ? 'bg-blue-50 text-blue-800' : 'hover:bg-white text-gray-700'}
                            ${disabled ? 'opacity-40 cursor-not-allowed' : ''}`}
                        >
                          <input
                            type="checkbox"
                            checked={checked}
                            disabled={disabled}
                            onChange={() => toggle(col.key)}
                            className="w-3.5 h-3.5 rounded accent-[#093eaa] shrink-0 mt-0.5"
                          />
                          <span className="leading-tight">
                            <span className="block text-xs font-medium">{t(col.label)}</span>
                            {col.hint && (
                              <span className="block text-[10px] font-normal text-gray-400 leading-tight">
                                {t(col.hint)}
                              </span>
                            )}
                          </span>
                        </label>
                      );
                    })}
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      )}
    </>
  );
}
