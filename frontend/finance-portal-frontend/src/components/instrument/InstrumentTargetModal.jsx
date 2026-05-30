import { useEffect, useState, useCallback } from 'react';
import { Link } from 'react-router-dom';
import { X, Wallet, Eye, Plus, Check, ChevronRight } from 'lucide-react';
import {
  getMyPortfolios, getWatchlistItems, addWatchlistItem, deleteWatchlistItem, createPortfolio,
} from '../../api/portfolioApi';
import { useTranslation } from '../../i18n/LanguageContext';
import { useToast } from '../../context/ToastContext';
import { useWatchlist } from '../../context/WatchlistContext';
import { AddTransactionModal } from '../../features/portfolio';

const sameInst = (it, assetType, symbol) =>
  it && it.assetType === assetType && String(it.symbol).toUpperCase() === String(symbol).toUpperCase();

/**
 * Birleşik hedef seçici: bir enstrümanı hem **varlık portföyüne** (işlem ekleyerek)
 * hem de **izleme listesine** (ekle/çıkar) eklemek için. Hem "Portföye Ekle" butonu
 * hem de liste yıldızı bunu açar.
 *
 * @param instrument { assetType, symbol, name, price }
 */
export default function InstrumentTargetModal({ instrument, onClose, onChanged }) {
  const { assetType, symbol, name } = instrument;
  const { t } = useTranslation();
  const toast = useToast();
  const { reload: reloadWatch } = useWatchlist();

  const [holdings, setHoldings] = useState([]);
  const [watchlists, setWatchlists] = useState([]); // [{id, name, itemId|null}]
  const [loading, setLoading] = useState(true);
  const [busyId, setBusyId] = useState(null);
  const [txPortfolio, setTxPortfolio] = useState(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const all = await getMyPortfolios();
      setHoldings((all ?? []).filter(p => p.portfolioType !== 'WATCHLIST'));
      const wls = (all ?? []).filter(p => p.portfolioType === 'WATCHLIST');
      const withMembership = await Promise.all(wls.map(async wl => {
        const items = await getWatchlistItems(wl.id).catch(() => []);
        const found = (items ?? []).find(it => sameInst(it, assetType, symbol));
        return { id: wl.id, name: wl.name, itemId: found ? found.id : null };
      }));
      setWatchlists(withMembership);
    } finally {
      setLoading(false);
    }
  }, [assetType, symbol]);

  useEffect(() => { load(); }, [load]);

  async function toggleWatchlist(wl) {
    setBusyId(wl.id);
    try {
      if (wl.itemId) {
        await deleteWatchlistItem(wl.id, wl.itemId);
        toast.success(t('İzleme listesinden çıkarıldı.'));
      } else {
        await addWatchlistItem(wl.id, { symbol, assetType, notes: name ?? '' });
        toast.success(t('İzleme listesine eklendi.'));
      }
      await load();
      reloadWatch?.();
      onChanged?.();
    } catch {
      toast.error(t('İşlem başarısız.'));
    } finally {
      setBusyId(null);
    }
  }

  async function createWatchlistAndAdd() {
    setBusyId('new');
    try {
      const created = await createPortfolio({
        name: 'İzleme Listem', description: '', currency: 'TRY', portfolioType: 'WATCHLIST',
      });
      await addWatchlistItem(created.id, { symbol, assetType, notes: name ?? '' });
      toast.success(t('İzleme listesine eklendi.'));
      await load();
      reloadWatch?.();
      onChanged?.();
    } catch {
      toast.error(t('İşlem başarısız.'));
    } finally {
      setBusyId(null);
    }
  }

  // Varlık portföyü seçildi → işlem ekleme modalı
  if (txPortfolio) {
    return (
      <AddTransactionModal
        portfolioId={txPortfolio.id}
        portfolioName={txPortfolio.name}
        holdings={txPortfolio.holdings ?? []}
        initialInstrument={instrument}
        onClose={() => setTxPortfolio(null)}
        onAdded={() => { setTxPortfolio(null); onChanged?.(); onClose(); }}
      />
    );
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-[#1a1b22]/30 backdrop-blur-sm">
      <div className="bg-white rounded-2xl shadow-2xl border border-[#e2e1eb] w-full max-w-md overflow-hidden">
        <div className="flex items-center justify-between px-6 py-4 border-b border-gray-100">
          <h2 className="font-bold text-gray-900 text-lg">{t('Listeye / Portföye Ekle')}</h2>
          <button onClick={onClose} className="text-gray-400 hover:text-gray-600 rounded-full p-1 hover:bg-gray-50">
            <X className="w-5 h-5" />
          </button>
        </div>

        <div className="p-4 max-h-[70vh] overflow-y-auto space-y-5">
          {loading ? (
            <p className="text-sm text-gray-400 py-6 text-center">{t('Yükleniyor...')}</p>
          ) : (
            <>
              {/* Varlık portföyleri → işlem ekle */}
              <div>
                <p className="text-xs font-bold text-gray-400 uppercase tracking-wider mb-2">{t('Varlık Portföyleri')}</p>
                {holdings.length === 0 ? (
                  <p className="text-xs text-gray-400 px-1 py-2">{t('Henüz varlık portföyünüz yok.')}</p>
                ) : (
                  <div className="space-y-2">
                    {holdings.map(p => (
                      <button key={p.id} onClick={() => setTxPortfolio(p)}
                        className="w-full flex items-center justify-between gap-3 px-4 py-3 rounded-xl border border-gray-200 hover:border-[#093eaa] hover:bg-[#093eaa]/5 transition-all text-left">
                        <span className="flex items-center gap-3 min-w-0">
                          <span className="w-8 h-8 rounded-lg bg-[#093eaa]/10 text-[#093eaa] flex items-center justify-center flex-shrink-0">
                            <Wallet className="w-4 h-4" />
                          </span>
                          <span className="font-semibold text-gray-900 truncate">{p.name}</span>
                        </span>
                        <ChevronRight className="w-4 h-4 text-gray-400 flex-shrink-0" />
                      </button>
                    ))}
                  </div>
                )}
              </div>

              {/* İzleme listeleri → ekle/çıkar */}
              <div>
                <p className="text-xs font-bold text-gray-400 uppercase tracking-wider mb-2">{t('İzleme Listeleri')}</p>
                <div className="space-y-2">
                  {watchlists.map(wl => (
                    <button key={wl.id} onClick={() => toggleWatchlist(wl)} disabled={busyId === wl.id}
                      className={`w-full flex items-center justify-between gap-3 px-4 py-3 rounded-xl border transition-all text-left disabled:opacity-50 ${
                        wl.itemId ? 'border-amber-200 bg-amber-50/40' : 'border-gray-200 hover:border-[#093eaa] hover:bg-[#093eaa]/5'
                      }`}>
                      <span className="flex items-center gap-3 min-w-0">
                        <span className="w-8 h-8 rounded-lg bg-gray-100 text-gray-500 flex items-center justify-center flex-shrink-0">
                          <Eye className="w-4 h-4" />
                        </span>
                        <span className="font-semibold text-gray-900 truncate">{wl.name}</span>
                      </span>
                      {wl.itemId ? (
                        <span className="inline-flex items-center gap-1 text-xs font-bold text-amber-600 flex-shrink-0">
                          <Check className="w-4 h-4" /> {t('Ekli')}
                        </span>
                      ) : (
                        <Plus className="w-4 h-4 text-gray-400 flex-shrink-0" />
                      )}
                    </button>
                  ))}
                  <button onClick={createWatchlistAndAdd} disabled={busyId === 'new'}
                    className="w-full flex items-center gap-2 px-4 py-2.5 rounded-xl border border-dashed border-gray-300 text-sm font-semibold text-gray-500 hover:text-[#093eaa] hover:border-[#093eaa] transition-all disabled:opacity-50">
                    <Plus className="w-4 h-4" /> {t('Yeni izleme listesi oluştur ve ekle')}
                  </button>
                </div>
              </div>

              <div className="text-center pt-1">
                <Link to="/portfolio" onClick={onClose} className="text-xs font-semibold text-[#093eaa] hover:underline">
                  {t('Portföyleri Yönet')}
                </Link>
              </div>
            </>
          )}
        </div>
      </div>
    </div>
  );
}
