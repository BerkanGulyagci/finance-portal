import { useState, useEffect } from 'react';
import { LifeBuoy, Plus, Pencil, Trash2, Check } from 'lucide-react';
import { getMyTickets, createTicket, updateTicket, deleteTicket } from '../../../api/supportApi';
import { useTranslation } from '../../../context/LanguageContext';
import { useConfirm } from '../../../context/ConfirmContext';

const STATUS_BADGE = {
  OPEN: 'bg-amber-100 text-amber-800',
  IN_PROGRESS: 'bg-sky-100 text-sky-800',
  RESOLVED: 'bg-emerald-100 text-emerald-800',
};

/** Profil → "Bir problem mi yaşıyorsunuz?" — talep oluştur + kendi taleplerini gör/düzenle/sil (yalnız Açık). */
export default function SupportTicketsCard() {
  const { t } = useTranslation();
  const confirm = useConfirm();
  const [tickets, setTickets] = useState([]);
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);
  const [subject, setSubject] = useState('');
  const [message, setMessage] = useState('');
  const [editId, setEditId] = useState(null);
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState('');

  function load() {
    setLoading(true);
    getMyTickets().then(setTickets).catch(() => setTickets([])).finally(() => setLoading(false));
  }
  useEffect(load, []);

  function resetForm() {
    setShowForm(false); setEditId(null); setSubject(''); setMessage(''); setErr('');
  }

  async function submit(e) {
    e.preventDefault();
    if (!subject.trim() || !message.trim()) { setErr(t('Konu ve açıklama zorunludur.')); return; }
    setBusy(true); setErr('');
    try {
      if (editId) await updateTicket(editId, { subject, message });
      else await createTicket({ subject, message });
      resetForm();
      load();
    } catch (e2) {
      setErr(e2?.response?.data?.message || t('İşlem başarısız.'));
    } finally {
      setBusy(false);
    }
  }

  function startEdit(tk) {
    setEditId(tk.id); setSubject(tk.subject); setMessage(tk.message); setShowForm(true); setErr('');
  }

  async function remove(id) {
    if (!(await confirm(t('Bu talebi silmek istediğinize emin misiniz?')))) return;
    try { await deleteTicket(id); load(); } catch (e2) { alert(e2?.response?.data?.message || t('Silinemedi.')); }
  }

  const activeCount = tickets.filter(tk => tk.status !== 'RESOLVED').length;
  const limitReached = activeCount >= 3;

  return (
    <div className="m3-card p-6">
      <div className="flex items-start justify-between gap-3 mb-4">
        <div className="flex items-center gap-3 min-w-0">
          <span className="m3-icon-chip"><LifeBuoy className="w-5 h-5" /></span>
          <div className="min-w-0">
            <h3 className="font-bold text-gray-900 leading-tight">{t('Bir problem mi yaşıyorsunuz?')}</h3>
            <p className="text-xs text-gray-500 mt-0.5">
              {t('Yaşadığınız sorunu bize iletin; ekibimiz en kısa sürede dönüş yapar.')}
            </p>
          </div>
        </div>
        {!showForm && (
          <button
            type="button"
            onClick={() => { resetForm(); setShowForm(true); }}
            disabled={limitReached}
            title={limitReached ? t('Aynı anda en fazla 3 aktif talebiniz olabilir.') : undefined}
            className="inline-flex shrink-0 items-center gap-1.5 text-sm font-bold text-white bg-[#093eaa] px-3 py-1.5 rounded-lg hover:bg-[#0b347f] transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
          >
            <Plus className="w-4 h-4" /> {t('Yeni Talep')}
          </button>
        )}
      </div>
      {limitReached && !showForm && (
        <p className="text-xs text-amber-600 mb-4 -mt-1">
          {t('Aynı anda en fazla 3 aktif talebiniz olabilir. Mevcut talepleriniz çözülünce yenisini açabilirsiniz.')}
        </p>
      )}

      {showForm && (
        <form onSubmit={submit} className="mb-5 rounded-lg border border-gray-200 bg-[#f8f9fc] p-4 space-y-3">
          <input
            value={subject}
            onChange={e => setSubject(e.target.value)}
            maxLength={200}
            placeholder={t('Konu')}
            className="w-full rounded-lg border border-gray-200 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-[#093eaa]/30"
          />
          <textarea
            value={message}
            onChange={e => setMessage(e.target.value)}
            maxLength={5000}
            rows={4}
            placeholder={t('Sorununuzu açıklayın...')}
            className="w-full rounded-lg border border-gray-200 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-[#093eaa]/30 resize-y"
          />
          {err && <p className="text-xs text-rose-600">{err}</p>}
          <div className="flex gap-2 justify-end">
            <button type="button" onClick={resetForm} className="text-sm font-semibold text-gray-500 px-3 py-1.5 hover:text-gray-700">
              {t('Vazgeç')}
            </button>
            <button type="submit" disabled={busy} className="inline-flex items-center gap-1.5 text-sm font-bold text-white bg-[#093eaa] px-4 py-1.5 rounded-lg hover:bg-[#0b347f] transition-colors disabled:opacity-60">
              <Check className="w-4 h-4" /> {editId ? t('Güncelle') : t('Gönder')}
            </button>
          </div>
        </form>
      )}

      {loading ? (
        <p className="text-sm text-gray-400">{t('Yükleniyor...')}</p>
      ) : tickets.length === 0 ? (
        <p className="text-sm text-gray-400">{t('Henüz bir talebiniz yok.')}</p>
      ) : (
        <ul className="space-y-3">
          {tickets.map(tk => (
            <li key={tk.id} className="rounded-lg border border-gray-200/70 p-3.5">
              <div className="flex items-start justify-between gap-2">
                <div className="min-w-0">
                  <p className="text-sm font-bold text-gray-900 break-words">{tk.subject}</p>
                  <p className="mt-0.5 text-xs text-gray-500 whitespace-pre-wrap break-words line-clamp-3">{tk.message}</p>
                </div>
                <span className={`shrink-0 text-[11px] font-semibold px-2 py-0.5 rounded-md ${STATUS_BADGE[tk.status] || 'bg-gray-100 text-gray-600'}`}>
                  {tk.statusLabel}
                </span>
              </div>
              {tk.adminNote && (
                <p className="mt-2 text-xs text-gray-600 bg-sky-50 border border-sky-100 rounded-lg px-2.5 py-1.5">
                  <span className="font-semibold">{t('Yanıt')}:</span> {tk.adminNote}
                </p>
              )}
              <div className="mt-2 flex items-center justify-between">
                <span className="text-[10px] text-gray-400">
                  {tk.createdAt ? new Date(tk.createdAt).toLocaleString('tr-TR', { dateStyle: 'medium', timeStyle: 'short' }) : ''}
                </span>
                {tk.status === 'OPEN' && (
                  <div className="flex items-center gap-1">
                    <button onClick={() => startEdit(tk)} title={t('Düzenle')} className="p-1.5 rounded-md text-gray-400 hover:text-[#093eaa] hover:bg-[#093eaa]/5">
                      <Pencil className="w-3.5 h-3.5" />
                    </button>
                    <button onClick={() => remove(tk.id)} title={t('Sil')} className="p-1.5 rounded-md text-gray-400 hover:text-rose-500 hover:bg-rose-50">
                      <Trash2 className="w-3.5 h-3.5" />
                    </button>
                  </div>
                )}
              </div>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
