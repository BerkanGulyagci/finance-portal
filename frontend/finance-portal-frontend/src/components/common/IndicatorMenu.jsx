import { useState } from 'react';
import { SlidersHorizontal, ChevronDown } from 'lucide-react';
import { useTranslation } from '../../i18n/LanguageContext';

/**
 * "İndikatör ▾" dropdown — hisse detay grafiğindeki tasarımın paylaşılan hali.
 * MA (Hareketli Ortalama) + osilatör/indikatör + opsiyonel ekstra toggle'ları (ör. Trend)
 * tek popover'da toplar. Yeterli veri yoksa ilgili MA butonu pasifleşir.
 *
 * Tüm detay grafiklerinde aynı görünüm/mantık için kullanılır. Türde olmayan bölüm (ör. osilatör
 * yoksa) otomatik gizlenir.
 *
 * @param maDefs     {{period:number,color:string,label:string}[]}
 * @param activeMAs  {number[]}
 * @param onToggleMA {(period:number)=>void}
 * @param dataLen    {number}  yüklü nokta sayısı (MA için yeterli veri kontrolü; 0 → kontrol yok)
 * @param subDefs    {{name:string,color:string,label:string}[]}
 * @param activeSubs {string[]}
 * @param onToggleSub{(name:string)=>void}
 * @param extras     {{key,label,color,active,onToggle,disabled?,title?}[]}  ör. Trend(EMA)
 * @param disabled   {boolean}  tüm menüyü pasifle (ör. karşılaştırma modunda)
 * @param align      {'left'|'right'}  popover hizası
 */
export default function IndicatorMenu({
  maDefs = [], activeMAs = [], onToggleMA, dataLen = 0,
  subDefs = [], activeSubs = [], onToggleSub,
  extras = [],
  disabled = false,
  align = 'left',
}) {
  const { t } = useTranslation();
  const [open, setOpen] = useState(false);
  const activeCount = activeMAs.length + activeSubs.length + extras.filter(e => e.active).length;

  return (
    <div className="relative">
      <button
        onClick={() => { if (!disabled) setOpen(v => !v); }}
        disabled={disabled}
        className={`inline-flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg text-xs font-semibold border transition-all ${
          disabled
            ? 'bg-gray-50 text-gray-300 border-gray-100 cursor-not-allowed'
            : open || activeCount > 0
              ? 'border-[#093eaa] text-[#093eaa] bg-[#093eaa]/5'
              : 'bg-white text-gray-600 border-gray-200 hover:border-[#093eaa] hover:text-[#093eaa]'
        }`}
      >
        <SlidersHorizontal className="w-3.5 h-3.5" />
        {t('İndikatör')}
        {activeCount > 0 && (
          <span className="inline-flex items-center justify-center min-w-[16px] h-4 px-1 rounded-full bg-[#093eaa] text-white text-[10px] font-bold">
            {activeCount}
          </span>
        )}
        <ChevronDown className={`w-3 h-3 transition-transform ${open ? 'rotate-180' : ''}`} />
      </button>

      {open && (
        <>
          <div className="fixed inset-0 z-40" onClick={() => setOpen(false)} />
          <div className={`absolute ${align === 'right' ? 'right-0' : 'left-0'} top-full mt-1 z-50 bg-white border border-gray-200 rounded-xl shadow-lg p-3 w-64`}>
            {maDefs.length > 0 && (
              <>
                <p className="text-[10px] font-bold text-gray-400 uppercase tracking-wider mb-2">{t('Hareketli Ortalama')}</p>
                <div className="flex flex-wrap gap-1.5 mb-3">
                  {maDefs.map(({ period, color, label }) => {
                    const active = activeMAs.includes(period);
                    const dis = dataLen > 0 && dataLen < period;
                    return (
                      <button
                        key={period}
                        onClick={() => { if (!dis) onToggleMA(period); }}
                        disabled={dis}
                        title={dis ? t('Yeterli veri yok ({n} nokta gerekir)', { n: period }) : undefined}
                        className={`px-2.5 py-1 rounded-md text-xs font-bold transition-all border ${
                          dis
                            ? 'border-gray-100 bg-gray-50 text-gray-300 cursor-not-allowed'
                            : active ? 'text-white border-transparent' : 'bg-gray-50 text-gray-500 border-gray-200 hover:bg-gray-100'
                        }`}
                        style={active && !dis ? { backgroundColor: color, borderColor: color } : {}}
                      >
                        {label}
                      </button>
                    );
                  })}
                </div>
              </>
            )}

            {(subDefs.length > 0 || extras.length > 0) && (
              <>
                <p className="text-[10px] font-bold text-gray-400 uppercase tracking-wider mb-2">{t('Osilatör / İndikatör')}</p>
                <div className="flex flex-wrap gap-1.5">
                  {subDefs.map(({ name, color, label }) => {
                    const active = activeSubs.includes(name);
                    return (
                      <button
                        key={name}
                        onClick={() => onToggleSub(name)}
                        className={`px-2.5 py-1 rounded-md text-xs font-semibold transition-all border ${
                          active ? 'text-white border-transparent' : 'bg-gray-50 text-gray-500 border-gray-200 hover:bg-gray-100'
                        }`}
                        style={active ? { backgroundColor: color, borderColor: color } : {}}
                      >
                        {t(label)}
                      </button>
                    );
                  })}
                  {extras.map(({ key, label, color, active, onToggle, disabled: ed, title }) => (
                    <button
                      key={key}
                      onClick={() => { if (!ed) onToggle(); }}
                      disabled={ed}
                      title={title}
                      className={`px-2.5 py-1 rounded-md text-xs font-semibold transition-all border ${
                        ed
                          ? 'border-gray-100 bg-gray-50 text-gray-300 cursor-not-allowed'
                          : active ? 'text-white border-transparent' : 'bg-gray-50 text-gray-500 border-gray-200 hover:bg-gray-100'
                      }`}
                      style={active && !ed ? { backgroundColor: color, borderColor: color } : {}}
                    >
                      {t(label)}
                    </button>
                  ))}
                </div>
              </>
            )}
          </div>
        </>
      )}
    </div>
  );
}
