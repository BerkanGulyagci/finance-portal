import { useEffect, useRef, useState, useCallback } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Bell, AlertTriangle } from 'lucide-react';
import { getUnreadCount, getNotifications, markNotificationRead } from '../../api/notificationApi';
import { useTranslation } from '../../context/LanguageContext';

const POLL_MS = 45000;

function fmtTime(s) {
  if (!s) return '';
  try { return new Date(s).toLocaleString('tr-TR', { dateStyle: 'short', timeStyle: 'short' }); }
  catch { return ''; }
}

/** Header'da bildirim zili — okunmamış sayacı (poll) + son bildirimler açılır menüsü. */
export default function NotificationBell() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [count, setCount] = useState(0);
  const [open, setOpen] = useState(false);
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(false);
  const ref = useRef(null);

  const fetchCount = useCallback(() => {
    getUnreadCount().then(c => setCount(Number(c) || 0)).catch(() => {});
  }, []);

  useEffect(() => {
    fetchCount();
    const id = setInterval(fetchCount, POLL_MS);
    return () => clearInterval(id);
  }, [fetchCount]);

  useEffect(() => {
    function onDoc(e) { if (ref.current && !ref.current.contains(e.target)) setOpen(false); }
    document.addEventListener('mousedown', onDoc);
    return () => document.removeEventListener('mousedown', onDoc);
  }, []);

  function toggle() {
    const next = !open;
    setOpen(next);
    if (next) {
      setLoading(true);
      getNotifications()
        .then(list => setItems(list.slice(0, 7)))
        .catch(() => setItems([]))
        .finally(() => setLoading(false));
    }
  }

  function openItem(n) {
    if (!n.read) {
      markNotificationRead(n.id).then(() => { fetchCount(); }).catch(() => {});
    }
    setOpen(false);
    navigate('/notifications');
  }

  return (
    <div className="relative" ref={ref}>
      <button
        type="button"
        onClick={toggle}
        title={t('Bildirimler')}
        className={`relative p-2 rounded-full hover:bg-gray-100 transition-colors ${open ? 'bg-gray-100' : ''}`}
      >
        <Bell className="w-5 h-5 text-gray-600" />
        {count > 0 && (
          <span className="absolute -top-0.5 -right-0.5 min-w-[16px] h-4 px-1 rounded-full bg-rose-500 text-white text-[10px] font-bold flex items-center justify-center">
            {count > 9 ? '9+' : count}
          </span>
        )}
      </button>

      {open && (
        <div className="absolute right-0 top-full mt-1 w-[calc(100vw-1rem)] max-w-[20rem] sm:w-80 bg-white rounded-xl shadow-xl border border-gray-200 py-2 z-50">
          <div className="px-4 py-2 flex items-center justify-between border-b border-gray-100">
            <span className="text-sm font-bold text-gray-900">{t('Bildirimler')}</span>
            {count > 0 && <span className="text-xs font-semibold text-[#093eaa]">{count} {t('yeni')}</span>}
          </div>
          <div className="max-h-80 overflow-y-auto">
            {loading ? (
              <p className="text-xs text-gray-400 py-6 text-center">{t('Yükleniyor...')}</p>
            ) : items.length === 0 ? (
              <p className="text-xs text-gray-400 py-6 text-center">{t('Henüz bildiriminiz yok.')}</p>
            ) : (
              items.map(n => {
                const isMarginCall = n.type === 'MARGIN_CALL';
                const dotCls = n.read
                  ? 'bg-transparent'
                  : (isMarginCall ? 'bg-rose-500' : 'bg-[#093eaa]');
                const bgCls = n.read
                  ? ''
                  : (isMarginCall ? 'bg-rose-50/40' : 'bg-[#093eaa]/[0.03]');
                return (
                  <button key={n.id} onClick={() => openItem(n)}
                    className={`w-full text-left px-4 py-2.5 hover:bg-gray-50 transition-colors flex gap-2 ${bgCls}`}>
                    <span className={`mt-1.5 w-1.5 h-1.5 rounded-full flex-shrink-0 ${dotCls}`} />
                    <span className="min-w-0">
                      <span className="flex items-center gap-1.5">
                        {isMarginCall && <AlertTriangle className="w-3.5 h-3.5 text-rose-600 flex-shrink-0" />}
                        <span className="block text-sm font-semibold text-gray-900 truncate">{n.title}</span>
                      </span>
                      <span className="block text-xs text-gray-400 mt-0.5">{fmtTime(n.createdAt)}</span>
                    </span>
                  </button>
                );
              })
            )}
          </div>
          <div className="border-t border-gray-100 mt-1 pt-1">
            <Link to="/notifications" onClick={() => setOpen(false)}
              className="block px-4 py-2 text-sm font-semibold text-[#093eaa] hover:bg-gray-50 text-center">
              {t('Tüm Bildirimler')}
            </Link>
          </div>
        </div>
      )}
    </div>
  );
}
