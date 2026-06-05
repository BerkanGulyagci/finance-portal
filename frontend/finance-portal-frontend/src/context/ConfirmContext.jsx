import { createContext, useCallback, useContext, useMemo, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { AlertTriangle, X } from 'lucide-react';
import { useTranslation } from '../context/LanguageContext';

/**
 * Site-özel onay diyaloğu — tarayıcının çirkin window.confirm()'ü yerine M3 modal.
 *
 * Kullanım:
 *   const confirm = useConfirm();
 *   if (!(await confirm('Bu kaydı silmek istiyor musunuz?'))) return;
 *
 * veya seçeneklerle:
 *   const ok = await confirm({
 *     title: 'Portföyü Sil',
 *     message: 'Tüm veriler silinecek.',
 *     confirmLabel: 'Sil', danger: true,
 *   });
 *
 * Promise<boolean> döner: onay → true, iptal/kapat → false.
 */
const ConfirmContext = createContext(null);

export function ConfirmProvider({ children }) {
  const { t } = useTranslation();
  const [state, setState] = useState(null);   // { title, message, confirmLabel, cancelLabel, danger } | null
  const resolverRef = useRef(null);

  const confirm = useCallback((opts) => {
    const cfg = typeof opts === 'string' ? { message: opts } : (opts || {});
    return new Promise((resolve) => {
      resolverRef.current = resolve;
      setState({
        title: cfg.title || null,
        message: cfg.message || '',
        confirmLabel: cfg.confirmLabel || null,
        cancelLabel: cfg.cancelLabel || null,
        danger: cfg.danger ?? true,   // silme/yıkıcı varsayılan
      });
    });
  }, []);

  const close = useCallback((result) => {
    setState(null);
    const resolve = resolverRef.current;
    resolverRef.current = null;
    if (resolve) resolve(result);
  }, []);

  const value = useMemo(() => confirm, [confirm]);

  return (
    <ConfirmContext.Provider value={value}>
      {children}
      {state && createPortal(
        <div
          className="fixed inset-0 z-[120] flex items-center justify-center p-4 bg-black/40 backdrop-blur-[1px] m3-fade-in"
          onClick={() => close(false)}
          onKeyDown={(e) => { if (e.key === 'Escape') close(false); }}
          role="presentation"
        >
          <div
            className="w-full max-w-sm rounded-2xl bg-white shadow-2xl border border-gray-100 p-5 m3-scale-in"
            onClick={(e) => e.stopPropagation()}
            onKeyDown={(e) => e.stopPropagation()}
            role="alertdialog"
            aria-modal="true"
            aria-label={state.title || t('Onay')}
          >
            <div className="flex items-start gap-3">
              <span
                className={`shrink-0 flex items-center justify-center w-10 h-10 rounded-full ${
                  state.danger ? 'bg-rose-50 text-rose-500' : 'bg-[#093eaa]/10 text-[#093eaa]'
                }`}
                aria-hidden
              >
                <AlertTriangle className="w-5 h-5" />
              </span>
              <div className="min-w-0 flex-1">
                {state.title && (
                  <h3 className="text-base font-bold text-gray-900 leading-snug">{state.title}</h3>
                )}
                <p className={`text-sm text-gray-600 leading-snug ${state.title ? 'mt-1' : 'mt-0.5'}`}>
                  {state.message}
                </p>
              </div>
              <button
                type="button"
                onClick={() => close(false)}
                className="shrink-0 -mt-1 -mr-1 p-1 rounded-lg text-gray-300 hover:text-gray-500 hover:bg-gray-50 transition-colors"
                aria-label={t('Kapat')}
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            <div className="mt-5 flex items-center justify-end gap-2">
              <button
                type="button"
                onClick={() => close(false)}
                className="px-4 py-2 rounded-full text-sm font-semibold text-gray-600 hover:bg-gray-100 transition-colors"
              >
                {state.cancelLabel || t('İptal')}
              </button>
              <button
                type="button"
                autoFocus
                onClick={() => close(true)}
                className={`px-4 py-2 rounded-full text-sm font-bold text-white shadow-sm transition-colors ${
                  state.danger
                    ? 'bg-rose-600 hover:bg-rose-700'
                    : 'bg-[#093eaa] hover:bg-[#0a2966]'
                }`}
              >
                {state.confirmLabel || t('Evet')}
              </button>
            </div>
          </div>
        </div>,
        document.body,
      )}
    </ConfirmContext.Provider>
  );
}

export function useConfirm() {
  const ctx = useContext(ConfirmContext);
  // Provider yoksa (ör. izole test render'ı) güvenli fallback: tarayıcının
  // yerleşik confirm'ü. Uygulama App.jsx'te ConfirmProvider ile sarılı → gerçekte M3 modal.
  if (!ctx) {
    return (opts) => {
      const msg = typeof opts === 'string' ? opts : (opts?.message || '');
      return Promise.resolve(typeof window !== 'undefined' ? window.confirm(msg) : true);
    };
  }
  return ctx;
}
