import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Landmark, Users, Download, CheckCircle2, AlertCircle } from 'lucide-react';
import { getEurobondIsins, refreshEurobondIsins } from '../../api/adminApi';
import AdminPageHeader from './components/AdminPageHeader';
import { useTranslation } from '../../context/LanguageContext';

export default function EurobondAdminPage() {
  const { t } = useTranslation();
  const [isins, setIsins] = useState([]);
  const [loading, setLoading] = useState(true);
  const [url, setUrl] = useState('');
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState(null);   // { ok, text }

  function load() {
    setLoading(true);
    getEurobondIsins().then(d => setIsins(d?.isins ?? [])).catch(() => setIsins([])).finally(() => setLoading(false));
  }
  useEffect(load, []);

  async function refresh(e) {
    e.preventDefault();
    if (!url.trim()) return;
    setBusy(true); setMsg(null);
    try {
      const r = await refreshEurobondIsins(url.trim());
      setMsg({ ok: true, text: `${r?.count ?? 0} ISIN yüklendi ve liste güncellendi.` });
      setUrl('');
      load();
    } catch (err) {
      setMsg({ ok: false, text: err?.response?.data?.message || t('Güncelleme başarısız.') });
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="max-w-5xl mx-auto py-6 px-4">
      <AdminPageHeader
        title={t('Eurobond Listesi')}
        description={t('Hazine dış borç tahvilleri — HMB aylık xlsx listesinden ISIN güncelleme')}
        loading={loading}
        onRefresh={load}
      />

      {/* Alt sekme: Kullanıcılar | Eurobond */}
      <div className="flex gap-2 mb-5">
        <Link to="/admin/users" className="inline-flex items-center gap-1.5 px-3 py-2 rounded-xl text-sm font-bold bg-white border border-gray-200 text-gray-600 hover:bg-gray-50">
          <Users className="w-4 h-4" /> {t('Kullanıcılar')}
        </Link>
        <span className="inline-flex items-center gap-1.5 px-3 py-2 rounded-xl text-sm font-bold bg-[#093eaa] text-white">
          <Landmark className="w-4 h-4" /> {t('Eurobond')}
        </span>
      </div>

      <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-5 mb-5">
        <h2 className="font-bold text-gray-900 mb-1">{t('Aylık listeyi güncelle')}</h2>
        <p className="text-xs text-gray-500 mb-3">
          {t('HMB sayfasındaki "Merkezi Yönetim Dış Borç Stoku Tahvil Listesi" Excel ikonuna sağ tık → bağlantıyı kopyala → buraya yapıştır.')}{' '}
          <a href="https://www.hmb.gov.tr/kamu-finansmani-istatistikleri" target="_blank" rel="noopener noreferrer" className="text-[#093eaa] font-semibold hover:underline">hmb.gov.tr</a>
        </p>
        <form onSubmit={refresh} className="flex gap-2 flex-wrap">
          <input
            type="url"
            value={url}
            onChange={e => setUrl(e.target.value)}
            placeholder="https://ms.hmb.gov.tr/uploads/2026/05/Merkezi_Yonetim_Dis_Borc_Stoku_Tahvil_Listesi-....xlsx"
            className="w-full sm:flex-1 sm:w-auto sm:min-w-[280px] px-3 py-2 bg-gray-50 border border-gray-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-[#093eaa]/30"
          />
          <button type="submit" disabled={busy || !url.trim()}
            className="inline-flex items-center gap-1.5 px-4 py-2 rounded-xl text-sm font-bold text-white bg-[#093eaa] hover:bg-[#0b347f] disabled:opacity-50">
            <Download className="w-4 h-4" /> {busy ? t('İndiriliyor...') : t('İndir ve Güncelle')}
          </button>
        </form>
        {msg && (
          <div className={`mt-3 flex items-center gap-2 text-sm ${msg.ok ? 'text-emerald-700' : 'text-rose-600'}`}>
            {msg.ok ? <CheckCircle2 className="w-4 h-4" /> : <AlertCircle className="w-4 h-4" />}
            {msg.text}
          </div>
        )}
      </div>

      <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-5">
        <div className="flex items-center justify-between mb-3">
          <h2 className="font-bold text-gray-900">{t('Güncel ISIN Listesi')}</h2>
          <span className="text-xs text-gray-400">{isins.length} ISIN</span>
        </div>
        {loading
          ? <p className="text-sm text-gray-400">{t('Yükleniyor...')}</p>
          : (
            <div className="flex flex-wrap gap-1.5">
              {isins.map(i => (
                <span key={i} className="font-mono text-xs px-2 py-1 rounded-lg bg-gray-50 border border-gray-200 text-gray-600">{i}</span>
              ))}
            </div>
          )}
      </div>
    </div>
  );
}
