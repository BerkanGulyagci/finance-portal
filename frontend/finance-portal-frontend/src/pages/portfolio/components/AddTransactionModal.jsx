import { useEffect, useState } from 'react';
import { X } from 'lucide-react';
import { addTransaction } from '../../../api/portfolioApi';
import InstrumentSearchModal from './InstrumentSearchModal';

/**
 * Koyu temalı işlem ekleme modal'ı.
 * Akış: Enstrüman seç → BUY/SELL form → Kaydet
 *
 * Props:
 *   portfolioId: string
 *   portfolioName: string
 *   onClose(): void
 *   onAdded(updatedPortfolio): void
 */
export default function AddTransactionModal({ portfolioId, portfolioName, onClose, onAdded, initialInstrument = null }) {
  const [step, setStep] = useState(initialInstrument ? 'form' : 'search'); // 'search' | 'form'
  const [instrument, setInstrument] = useState(initialInstrument); // { symbol, assetType, name, price }

  const now = new Date();
  const localNow = new Date(now.getTime() - now.getTimezoneOffset() * 60000)
    .toISOString().slice(0, 16);

  const [form, setForm] = useState({
    transactionType: 'BUY',
    quantity: '',
    price: initialInstrument?.price != null ? String(initialInstrument.price) : '',
    commission: '',
    transactionDate: localNow,
  });
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  function set(key, val) { setForm(f => ({ ...f, [key]: val })); }

  useEffect(() => {
    if (!initialInstrument) return;
    setInstrument(initialInstrument);
    setStep('form');
    if (initialInstrument.price != null) {
      set('price', String(initialInstrument.price));
    }
  }, [initialInstrument]);

  function handleInstrumentSelect(inst) {
    setInstrument(inst);
    if (inst.price != null) set('price', String(inst.price));
    setStep('form');
  }

  async function handleSubmit(e) {
    e.preventDefault();
    if (!form.quantity || parseFloat(form.quantity) <= 0) { setError('Miktar 0\'dan büyük olmalıdır.'); return; }
    if (!form.price || parseFloat(form.price) <= 0) { setError('Fiyat 0\'dan büyük olmalıdır.'); return; }
    setLoading(true);
    setError('');
    try {
      const updated = await addTransaction(portfolioId, {
        symbol: instrument.symbol,
        assetType: instrument.assetType,
        transactionType: form.transactionType,
        quantity: parseFloat(form.quantity),
        price: parseFloat(form.price),
        commission: form.commission ? parseFloat(form.commission) : 0,
        transactionDate: new Date(form.transactionDate).toISOString().replace('Z', ''),
      });
      onAdded(updated);
      onClose();
    } catch (err) {
      const msg = err.response?.data?.message || err.response?.data?.data;
      setError(typeof msg === 'string' ? msg : 'İşlem eklenemedi.');
    } finally {
      setLoading(false);
    }
  }

  // Adım 1: Enstrüman arama
  if (step === 'search') {
    return (
      <InstrumentSearchModal
        portfolioName={portfolioName}
        onSelect={handleInstrumentSelect}
        onClose={onClose}
      />
    );
  }

  // Adım 2: İşlem formu
  const total = (parseFloat(form.quantity || 0) * parseFloat(form.price || 0));
  const isBuy = form.transactionType === 'BUY';

  return (
    <div className="fixed inset-0 bg-black/70 z-50 flex items-center justify-center p-4">
      <div className="bg-[#1a1f2e] rounded-2xl shadow-2xl w-full max-w-md text-white">
        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-[#3a4155]">
          <div>
            <h2 className="font-bold text-lg">İşlem Ekle</h2>
            <p className="text-sm text-gray-400">
              <span className="font-bold text-white">{instrument?.symbol}</span>
              {instrument?.name && instrument.name !== instrument.symbol && (
                <span className="ml-1 text-gray-500">· {instrument.name}</span>
              )}
            </p>
          </div>
          <div className="flex items-center gap-2">
            <button
              onClick={() => setStep('search')}
              className="text-xs text-[#4a6cf7] hover:underline"
            >
              Değiştir
            </button>
            <button onClick={onClose} className="text-gray-400 hover:text-white transition-colors">
              <X className="w-5 h-5" />
            </button>
          </div>
        </div>

        <form onSubmit={handleSubmit} className="p-6 space-y-4">
          {/* BUY / SELL */}
          <div className="grid grid-cols-2 gap-2">
            {['BUY', 'SELL'].map(t => (
              <button
                key={t}
                type="button"
                onClick={() => set('transactionType', t)}
                className={`py-2.5 rounded-xl text-sm font-bold transition-all ${
                  form.transactionType === t
                    ? t === 'BUY' ? 'bg-emerald-500 text-white' : 'bg-rose-500 text-white'
                    : 'bg-[#252b3b] text-gray-400 hover:bg-[#2f3650]'
                }`}
              >
                {t === 'BUY' ? '📈 Alış' : '📉 Satış'}
              </button>
            ))}
          </div>

          {/* Miktar + Fiyat */}
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-xs font-semibold text-gray-400 mb-1.5">Miktar *</label>
              <input
                type="number" step="any" min="0"
                value={form.quantity}
                onChange={e => set('quantity', e.target.value)}
                placeholder="0.00"
                className="w-full bg-[#252b3b] border border-[#3a4155] rounded-xl px-4 py-2.5 text-sm text-white placeholder-gray-600 focus:outline-none focus:border-[#4a6cf7]"
              />
            </div>
            <div>
              <label className="block text-xs font-semibold text-gray-400 mb-1.5">
                Fiyat *
                {instrument?.price != null && (
                  <span className="ml-1 text-emerald-400 font-normal">otomatik</span>
                )}
              </label>
              <input
                type="number" step="any" min="0"
                value={form.price}
                onChange={e => set('price', e.target.value)}
                placeholder="0.00"
                className={`w-full bg-[#252b3b] border rounded-xl px-4 py-2.5 text-sm text-white placeholder-gray-600 focus:outline-none focus:border-[#4a6cf7] ${
                  instrument?.price != null ? 'border-emerald-500/50' : 'border-[#3a4155]'
                }`}
              />
            </div>
          </div>

          {/* Komisyon */}
          <div>
            <label className="block text-xs font-semibold text-gray-400 mb-1.5">Komisyon</label>
            <input
              type="number" step="any" min="0"
              value={form.commission}
              onChange={e => set('commission', e.target.value)}
              placeholder="0.00 (isteğe bağlı)"
              className="w-full bg-[#252b3b] border border-[#3a4155] rounded-xl px-4 py-2.5 text-sm text-white placeholder-gray-600 focus:outline-none focus:border-[#4a6cf7]"
            />
          </div>

          {/* Tarih */}
          <div>
            <label className="block text-xs font-semibold text-gray-400 mb-1.5">İşlem Tarihi *</label>
            <input
              type="datetime-local"
              value={form.transactionDate}
              onChange={e => set('transactionDate', e.target.value)}
              className="w-full bg-[#252b3b] border border-[#3a4155] rounded-xl px-4 py-2.5 text-sm text-white focus:outline-none focus:border-[#4a6cf7]"
            />
          </div>

          {/* Özet */}
          {form.quantity && form.price && (
            <div className="bg-[#252b3b] rounded-xl p-3 text-sm">
              <div className="flex justify-between text-gray-400">
                <span>Toplam Tutar</span>
                <span className="font-bold text-white">
                  {total.toLocaleString('tr-TR', { minimumFractionDigits: 2 })}
                </span>
              </div>
            </div>
          )}

          {error && (
            <p className="text-rose-400 text-sm bg-rose-500/10 px-3 py-2 rounded-lg">{error}</p>
          )}

          <div className="flex gap-3 pt-1">
            <button
              type="button"
              onClick={onClose}
              className="flex-1 px-4 py-2.5 border border-[#3a4155] rounded-xl text-sm font-semibold text-gray-400 hover:bg-[#252b3b] transition-colors"
            >
              İptal
            </button>
            <button
              type="submit"
              disabled={loading}
              className={`flex-1 text-white px-4 py-2.5 rounded-xl text-sm font-semibold disabled:opacity-60 transition-colors ${
                isBuy ? 'bg-emerald-500 hover:bg-emerald-600' : 'bg-rose-500 hover:bg-rose-600'
              }`}
            >
              {loading ? 'Ekleniyor...' : isBuy ? 'Alış Ekle' : 'Satış Ekle'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
