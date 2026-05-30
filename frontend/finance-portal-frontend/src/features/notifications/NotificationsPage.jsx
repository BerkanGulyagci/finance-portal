import { useEffect, useState, useCallback } from 'react';
import { Link } from 'react-router-dom';
import { ArrowLeft, Bell, Mail, MailWarning, Trash2, CheckCheck } from 'lucide-react';
import {
  getNotifications, markNotificationRead, markAllNotificationsRead, deleteNotification,
} from '../../api/notificationApi';
import { useTranslation } from '../../context/LanguageContext';
import { useToast } from '../../context/ToastContext';

function emailBadge(status, t) {
  const map = {
    SENT: { label: 'E-posta gönderildi', cls: 'bg-emerald-50 text-emerald-700 border-emerald-200', icon: Mail },
    FAILED: { label: 'E-posta gönderilemedi', cls: 'bg-rose-50 text-rose-700 border-rose-200', icon: MailWarning },
    SKIPPED: { label: 'E-posta gönderilmedi', cls: 'bg-gray-100 text-gray-500 border-gray-200', icon: MailWarning },
    PENDING: { label: 'Beklemede', cls: 'bg-amber-50 text-amber-700 border-amber-200', icon: Mail },
  };
  const s = map[status] ?? map.PENDING;
  const Icon = s.icon;
  return (
    <span className={`inline-flex items-center gap-1 text-[10px] font-bold px-2 py-0.5 rounded-full border ${s.cls}`}>
      <Icon className="w-3 h-3" /> {t(s.label)}
    </span>
  );
}

function fmtDate(s) {
  if (!s) return '';
  try { return new Date(s).toLocaleString('tr-TR'); } catch { return s; }
}

export default function NotificationsPage() {
  const { t } = useTranslation();
  const toast = useToast();
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);

  const refresh = useCallback(() => {
    setLoading(true);
    getNotifications()
      .then(setItems)
      .catch(() => setItems([]))
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => { refresh(); }, [refresh]);

  async function onRead(n) {
    if (n.read) return;
    try { await markNotificationRead(n.id); refresh(); } catch { /* yut */ }
  }
  async function onReadAll() {
    try { await markAllNotificationsRead(); toast.success(t('Tümü okundu işaretlendi.')); refresh(); }
    catch { toast.error(t('İşlem başarısız.')); }
  }
  async function onDelete(n) {
    try { await deleteNotification(n.id); refresh(); } catch { toast.error(t('Silinemedi.')); }
  }

  const unread = items.filter(n => !n.read).length;

  return (
    <div className="max-w-3xl mx-auto space-y-5">
      <div>
        <Link to="/portfolio" className="inline-flex items-center gap-1.5 text-sm text-[#093eaa] font-semibold hover:underline mb-3">
          <ArrowLeft className="w-4 h-4" /> {t('Portföyüm')}
        </Link>
        <div className="flex items-center justify-between gap-2 flex-wrap">
          <h1 className="text-2xl font-bold text-gray-900 border-l-4 border-[#093eaa] pl-4 flex items-center gap-2">
            {t('Bildirimler')}
            {unread > 0 && <span className="text-sm font-semibold text-[#093eaa]">({unread})</span>}
          </h1>
          {unread > 0 && (
            <button onClick={onReadAll}
              className="inline-flex items-center gap-1.5 px-3 py-2 sm:py-1.5 rounded-lg text-xs font-bold border border-gray-200 text-gray-600 hover:bg-gray-50 transition-colors">
              <CheckCheck className="w-3.5 h-3.5" /> {t('Tümünü Okundu İşaretle')}
            </button>
          )}
        </div>
      </div>

      <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-3 sm:p-4">
        {loading ? (
          <p className="text-sm text-gray-400 py-8 text-center">{t('Yükleniyor...')}</p>
        ) : items.length === 0 ? (
          <div className="text-center py-12 text-sm text-gray-400">
            <Bell className="w-8 h-8 mx-auto mb-2 text-gray-300" />
            {t('Henüz bildiriminiz yok.')}
          </div>
        ) : (
          <div className="space-y-2">
            {items.map(n => (
              <div
                key={n.id}
                onMouseEnter={() => onRead(n)}
                className={`p-4 rounded-xl border transition-all ${
                  n.read ? 'border-gray-100 bg-white' : 'border-[#093eaa]/20 bg-[#093eaa]/[0.03]'
                }`}
              >
                <div className="flex items-start justify-between gap-3">
                  <div className="min-w-0">
                    <div className="flex items-center gap-2 flex-wrap">
                      {!n.read && <span className="w-2 h-2 rounded-full bg-[#093eaa] flex-shrink-0" />}
                      <span className="font-bold text-gray-900">{n.title}</span>
                    </div>
                    <p className="text-sm text-gray-600 mt-1 leading-relaxed">{n.body}</p>
                    <div className="flex items-center gap-2 mt-2 flex-wrap">
                      {emailBadge(n.emailStatus, t)}
                      {n.recipientEmail && <span className="text-[11px] text-gray-400">{n.recipientEmail}</span>}
                      <span className="text-[11px] text-gray-400">· {fmtDate(n.createdAt)}</span>
                    </div>
                    {n.emailStatus === 'FAILED' && n.emailError && (
                      <p className="text-[11px] text-rose-500 mt-1">{n.emailError}</p>
                    )}
                  </div>
                  <button onClick={() => onDelete(n)} title={t('Sil')}
                    className="p-2 sm:p-1.5 rounded-lg text-gray-400 hover:text-rose-600 hover:bg-rose-50 transition-colors flex-shrink-0">
                    <Trash2 className="w-4 h-4" />
                  </button>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
