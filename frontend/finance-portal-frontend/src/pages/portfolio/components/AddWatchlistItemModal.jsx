import { useState } from 'react';
import { X } from 'lucide-react';
import { addWatchlistItem } from '../../../api/portfolioApi';
import InstrumentSearchModal from './InstrumentSearchModal';

/**
 * İzleme listesine sembol ekleme modal'ı.
 * InstrumentSearchModal'ı kullanır, ardından not girişi gösterir.
 *
 * Props:
 *   portfolioId: string
 *   portfolioName: string
 *   onClose(): void
 *   onAdded(newItem): void
 */
export default function AddWatchlistItemModal({ portfolioId, portfolioName, onClose, onAdded }) {
  const [step, setStep] = useState('search'); // 'search' | 'confirm'
  const [instrument, setInstrument] = useState(null);
  const [notes, setNotes] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  function handleInstrumentSelect(inst) {
    setInstrument(inst);
    setStep('confirm');
  }

  async function handleAdd() {
    setLoading(true);
    setError('');
    try {
      const item = await addWatchlistItem(portfolioId, {
        symbol: instrument.symbol,
        assetType: instrument.assetType,
        notes: notes.trim() || undefined,
      });
      onAdded(item);
      onClose();
    } catch (err) {
      const msg = err.response?.data?.message;
      setError(typeof msg === 'string' ? msg : 'Sembol eklenemedi.');
    } finally {
      setLoading(false);
    }
  }

  if (step === 'search') {
    return (
      <InstrumentSearchModal
        portfolioName={portfolioName}
        onSelect={handleInstrumentSelect}
        onClose={onClose}
      />
    );
  }

  return (
    <div className="fixed inset-0 bg-black/70 z-50 flex items-center justify-center p-4">
      <div className="bg-[#1a1f2e] rounded-2xl shadow-2xl w-full max-w-sm text-white">
        <div className="flex items-center justify-between px-6 py-4 border-b border-[#3a4155]">
          <div>
            <h2 className="font-bold">İzleme Listesine Ekle</h2>
            <p className="text-sm text-gray-400">
              <span className="font-bold text-white">{instrument?.symbol}</span>
              <span className="ml-1 text-gray-500">· {instrument?.assetType}</span>
            </p>
          </div>
          <div className="flex items-center gap-2">
            <button onClick={() => setStep('search')} className="text-xs text-[#4a6cf7] hover:underline">
              Değiştir
            </button>
            <button onClick={onClose} className="text-gray-400 hover:text-white">
              <X className="w-5 h-5" />
            </button>
          </div>
        </div>

        <div className="p-6 space-y-4">
          <div>
            <label className="block text-xs font-semibold text-gray-400 mb-1.5">
              Not <span className="font-normal">(isteğe bağlı)</span>
            </label>
            <input
              type="text"
              value={notes}
              onChange={e => setNotes(e.target.value)}
              placeholder="Kısa bir not..."
              className="w-full bg-[#252b3b] border border-[#3a4155] rounded-xl px-4 py-2.5 text-sm text-white placeholder-gray-600 focus:outline-none focus:border-[#4a6cf7]"
            />
          </div>

          {error && (
            <p className="text-rose-400 text-sm bg-rose-500/10 px-3 py-2 rounded-lg">{error}</p>
          )}

          <div className="flex gap-3">
            <button
              onClick={onClose}
              className="flex-1 px-4 py-2.5 border border-[#3a4155] rounded-xl text-sm font-semibold text-gray-400 hover:bg-[#252b3b] transition-colors"
            >
              İptal
            </button>
            <button
              onClick={handleAdd}
              disabled={loading}
              className="flex-1 bg-sky-500 hover:bg-sky-600 text-white px-4 py-2.5 rounded-xl text-sm font-semibold disabled:opacity-60 transition-colors"
            >
              {loading ? 'Ekleniyor...' : 'Ekle'}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
