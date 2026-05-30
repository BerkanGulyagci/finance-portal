import { useState, useEffect, useRef } from 'react';
import { ChevronDown } from 'lucide-react';

/**
 * Site tasarımına uygun özel açılır menü (tarayıcı select'i yerine) — döviz çevirici dropdown'ı ile aynı stil.
 * options: [{ label, value }]. value = seçili değer; onChange(value) çağrılır.
 */
export function Dropdown({ value, options, onChange, placeholder = 'Seç', className = '', menuWidth = 'w-56' }) {
  const [open, setOpen] = useState(false);
  const ref = useRef(null);
  useEffect(() => {
    function onDoc(e) {
      if (ref.current && !ref.current.contains(e.target)) setOpen(false);
    }
    document.addEventListener('mousedown', onDoc);
    return () => document.removeEventListener('mousedown', onDoc);
  }, []);
  const current = options.find(o => o.value === value);
  return (
    <div className={`relative ${className}`} ref={ref}>
      <button
        type="button"
        onClick={() => setOpen(o => !o)}
        className="flex items-center justify-between gap-1 w-full bg-white border border-gray-200 rounded-md px-2.5 py-1.5 text-sm font-medium text-gray-800 hover:bg-gray-50"
      >
        <span className="truncate">{current ? current.label : placeholder}</span>
        <ChevronDown className={`w-3.5 h-3.5 text-gray-400 shrink-0 transition-transform ${open ? 'rotate-180' : ''}`} />
      </button>
      {open && (
        <div className={`absolute left-0 mt-1 ${menuWidth} max-h-64 overflow-auto bg-white border border-gray-200 rounded-md shadow-lg z-30`}>
          {options.map(o => (
            <button
              key={o.value}
              type="button"
              onClick={() => { onChange(o.value); setOpen(false); }}
              className={`block w-full text-left px-3 py-1.5 text-sm hover:bg-gray-50 ${o.value === value ? 'text-[#093eaa] font-bold' : 'text-gray-700'}`}
            >
              {o.label}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
