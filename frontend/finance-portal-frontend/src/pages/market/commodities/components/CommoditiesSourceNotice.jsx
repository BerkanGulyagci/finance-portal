import { AlertCircle } from 'lucide-react';

export default function CommoditiesSourceNotice() {
  return (
    <div className="flex items-start gap-2.5 bg-amber-50 border border-amber-200 rounded-xl px-4 py-3 text-xs text-amber-800">
      <AlertCircle className="w-4 h-4 shrink-0 mt-0.5 text-amber-500" />
      <span>
        <strong>Kaynak: Yahoo Finance</strong> — vadeli kontrat referans verisi.
        Bu fiyatlar spot piyasa fiyatı değildir.
      </span>
    </div>
  );
}
