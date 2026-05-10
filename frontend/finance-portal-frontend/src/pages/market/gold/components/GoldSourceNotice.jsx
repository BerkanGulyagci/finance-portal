import { THEORETICAL_DISCLAIMER } from './goldConstants';

export default function GoldSourceNotice({ source, official, fallback, disclaimer }) {
  return (
    <div className="rounded-xl border border-gray-100 bg-gray-50 px-4 py-3 space-y-1">
      {/* Kaynak satırı */}
      <div className="flex items-center gap-2 flex-wrap">
        {official && (
          <span className="inline-flex items-center gap-1 text-xs font-bold text-emerald-700 bg-emerald-50 border border-emerald-200 px-2 py-0.5 rounded-full">
            ✓ Resmi
          </span>
        )}
        {fallback && (
          <span className="inline-flex items-center gap-1 text-xs font-bold text-amber-700 bg-amber-50 border border-amber-200 px-2 py-0.5 rounded-full">
            ⚠ Fallback
          </span>
        )}
        <span className="text-xs text-gray-500 font-semibold">
          Kaynak: {source ?? 'Borsa İstanbul'} — Resmi Referans Fiyat
        </span>
      </div>

      {/* Fallback uyarısı */}
      {fallback && (
        <p className="text-xs text-amber-600">
          BIST verisi alınamadığı için Yahoo Finance fallback verisi gösteriliyor.
        </p>
      )}

      {/* Teorik fiyat uyarısı */}
      <p className="text-xs text-gray-400">
        {disclaimer ?? THEORETICAL_DISCLAIMER}
      </p>
    </div>
  );
}
