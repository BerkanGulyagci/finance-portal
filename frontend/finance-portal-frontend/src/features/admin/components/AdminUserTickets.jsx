import { useState, useEffect } from 'react';
import { LifeBuoy } from 'lucide-react';
import { getUserTickets, updateTicketStatus } from '../../../api/supportApi';
import { useTranslation } from '../../../context/LanguageContext';

const STATUSES = [
  { value: 'OPEN', label: 'Açık' },
  { value: 'IN_PROGRESS', label: 'İlgileniliyor' },
  { value: 'RESOLVED', label: 'Çözüldü' },
];
const BADGE = {
  OPEN: 'bg-amber-100 text-amber-800',
  IN_PROGRESS: 'bg-sky-100 text-sky-800',
  RESOLVED: 'bg-emerald-100 text-emerald-800',
};

/** Admin kullanıcı detay modalında: o kullanıcının destek talepleri + durum/not güncelleme (kullanıcıya bildirim gider). */
export default function AdminUserTickets({ userId }) {
  const { t } = useTranslation();
  const [tickets, setTickets] = useState([]);
  const [loading, setLoading] = useState(true);
  const [savingId, setSavingId] = useState(null);
  const [notes, setNotes] = useState({});

  function load() {
    setLoading(true);
    getUserTickets(userId).then(setTickets).catch(() => setTickets([])).finally(() => setLoading(false));
  }
  useEffect(() => { if (userId) load(); /* eslint-disable-next-line */ }, [userId]);

  async function setStatus(tk, status) {
    setSavingId(tk.id);
    try {
      const note = notes[tk.id] !== undefined ? notes[tk.id] : (tk.adminNote || '');
      const updated = await updateTicketStatus(tk.id, status, note);
      setTickets(prev => prev.map(x => (x.id === updated.id ? updated : x)));
    } catch (e) {
      alert(e?.response?.data?.message || t('Güncellenemedi.'));
    } finally {
      setSavingId(null);
    }
  }

  return (
    <div className="mt-4 pt-4 border-t border-gray-100">
      <h3 className="text-sm font-bold text-gray-900 flex items-center gap-2 mb-3">
        <LifeBuoy className="w-4 h-4 text-[#093eaa]" /> {t('Destek Talepleri')}
        {tickets.length > 0 && <span className="text-xs font-normal text-gray-400">({tickets.length})</span>}
      </h3>
      {loading ? (
        <p className="text-xs text-gray-400">{t('Yükleniyor...')}</p>
      ) : tickets.length === 0 ? (
        <p className="text-xs text-gray-400">{t('Bu kullanıcının destek talebi yok.')}</p>
      ) : (
        <ul className="space-y-3">
          {tickets.map(tk => (
            <li key={tk.id} className="rounded-xl border border-gray-100 p-3">
              <div className="flex items-start justify-between gap-2">
                <p className="text-sm font-bold text-gray-900 break-words min-w-0">{tk.subject}</p>
                <span className={`shrink-0 text-[10px] font-semibold px-2 py-0.5 rounded-full ${BADGE[tk.status] || 'bg-gray-100 text-gray-600'}`}>
                  {tk.statusLabel}
                </span>
              </div>
              <p className="mt-1 text-xs text-gray-600 whitespace-pre-wrap break-words">{tk.message}</p>
              <span className="block mt-1 text-[10px] text-gray-400">
                {tk.createdAt ? new Date(tk.createdAt).toLocaleString('tr-TR', { dateStyle: 'medium', timeStyle: 'short' }) : ''}
              </span>
              <textarea
                defaultValue={tk.adminNote || ''}
                onChange={e => setNotes(n => ({ ...n, [tk.id]: e.target.value }))}
                rows={2}
                placeholder={t('Kullanıcıya iletilecek not (opsiyonel)')}
                className="mt-2 w-full rounded-lg border border-gray-200 px-2.5 py-1.5 text-xs focus:outline-none focus:ring-2 focus:ring-[#093eaa]/30 resize-y"
              />
              <div className="mt-2 flex flex-wrap gap-1.5">
                {STATUSES.map(s => (
                  <button
                    key={s.value}
                    type="button"
                    disabled={savingId === tk.id}
                    onClick={() => setStatus(tk, s.value)}
                    className={`text-[11px] font-semibold px-2.5 py-1 rounded-lg transition-colors disabled:opacity-50 ${
                      tk.status === s.value ? 'bg-[#093eaa] text-white' : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
                    }`}
                  >
                    {t(s.label)}
                  </button>
                ))}
              </div>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
