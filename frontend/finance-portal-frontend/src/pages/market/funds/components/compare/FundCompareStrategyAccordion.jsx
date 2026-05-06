import { useState } from 'react';
import { ChevronDown } from 'lucide-react';
import { COMPARE_COLORS } from './compareUtils';

export default function FundCompareStrategyAccordion({ detailMap, selectedCodes }) {
  const [open, setOpen] = useState({});
  const hasAny = selectedCodes.some(code => detailMap[code]?.strategy);
  if (!hasAny) return null;

  return (
    <div className="bg-white rounded-2xl border border-gray-200 shadow-sm overflow-hidden">
      <div className="px-6 py-4 border-b border-gray-100">
        <h2 className="font-bold text-gray-900">Yatırım Stratejisi</h2>
        <p className="text-xs text-gray-400 mt-0.5">Fonların yatırım stratejileri</p>
      </div>
      <div className="divide-y divide-gray-100">
        {selectedCodes.map((code, idx) => {
          const d = detailMap[code];
          if (!d?.strategy) return null;
          const color = COMPARE_COLORS[idx % COMPARE_COLORS.length];
          const isOpen = !!open[code];
          return (
            <div key={code}>
              <button
                onClick={() => setOpen(p => ({ ...p, [code]: !p[code] }))}
                className="w-full flex items-center justify-between px-6 py-4 hover:bg-gray-50 transition-colors text-left">
                <div className="flex items-center gap-2.5">
                  <span className="w-3 h-3 rounded-full shrink-0" style={{ background: color }} />
                  <span className="font-bold text-sm" style={{ color }}>{code}</span>
                  {d.name && (
                    <span className="text-sm text-gray-500 hidden sm:inline">
                      — {d.name.length > 40 ? d.name.slice(0, 40) + '…' : d.name}
                    </span>
                  )}
                </div>
                <ChevronDown className={`w-4 h-4 text-gray-400 transition-transform shrink-0 ${isOpen ? 'rotate-180' : ''}`} />
              </button>
              {isOpen && (
                <div className="px-6 pb-5">
                  <p className="text-sm text-gray-700 leading-relaxed">{d.strategy}</p>
                  {d.benchmarkName && (
                    <p className="text-xs text-gray-500 mt-2">
                      <span className="font-semibold">Kıstas:</span> {d.benchmarkName}
                    </p>
                  )}
                </div>
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
}
