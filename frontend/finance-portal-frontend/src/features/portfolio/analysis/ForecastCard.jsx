import { Activity, TrendingUp, TrendingDown, Minus } from 'lucide-react';

const fmtPct = v => (v == null ? '—' : `%${Number(v).toLocaleString('tr-TR', { maximumFractionDigits: 1 })}`);
const fmtMoney = v => (v == null ? '—' : Number(v).toLocaleString('tr-TR', { maximumFractionDigits: 0 }) + ' ₺');
const signClass = v => (v == null ? 'text-gray-400' : Number(v) > 0 ? 'text-emerald-600' : Number(v) < 0 ? 'text-rose-600' : 'text-gray-500');

function TrendIcon({ trend }) {
  if (trend === 'UP') return <TrendingUp className="w-3.5 h-3.5 text-emerald-600" />;
  if (trend === 'DOWN') return <TrendingDown className="w-3.5 h-3.5 text-rose-600" />;
  return <Minus className="w-3.5 h-3.5 text-gray-400" />;
}

export default function ForecastCard({ forecast }) {
  if (!forecast) return null;

  return (
    <div className="bg-white rounded-lg border border-gray-200 shadow-sm p-4">
      <div className="flex items-center gap-2 mb-1 text-gray-800 font-bold text-sm">
        <Activity className="w-4 h-4 text-[#093eaa]" />Gelecek Projeksiyon — Olası değer (Monte Carlo)
      </div>
      <p className="text-[11px] text-gray-400 mb-3">
        Geçmiş getiri/oynaklığın süreceği varsayımıyla olası aralık; kesin tahmin değil. Yön yorumu AI raporunda.
      </p>

      {!forecast.available ? (
        <p className="text-sm text-gray-500">{forecast.note}</p>
      ) : (
        <>
          {/* Portföy ufukları */}
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 mb-4">
            {(forecast.portfolioHorizons || []).map(h => (
              <div key={h.months} className="bg-gray-50 rounded-lg p-3 text-center">
                <div className="text-xs font-semibold text-gray-500">{h.label}</div>
                <div className={`text-xl font-extrabold ${signClass(h.expectedReturnPercent)}`}>
                  {Number(h.expectedReturnPercent) > 0 ? '+' : ''}{fmtPct(h.expectedReturnPercent)}
                </div>
                <div className="text-[11px] text-gray-500 mt-0.5">medyan {fmtMoney(h.median)}</div>
                <div className="text-[10px] text-gray-400">aralık {fmtMoney(h.p5)} – {fmtMoney(h.p95)}</div>
                <div className="text-[10px] text-rose-500 mt-0.5">kayıp olasılığı %{Number(h.probLossPercent).toLocaleString('tr-TR', { maximumFractionDigits: 0 })}</div>
              </div>
            ))}
          </div>

          {/* Varlık-bazlı medyan getiri tahmini */}
          {(forecast.assetForecasts || []).length > 0 && (
            <div className="overflow-x-auto">
              <table className="w-full text-xs">
                <thead>
                  <tr className="text-gray-400 border-b border-gray-100">
                    <th className="py-1.5 text-left font-semibold">Varlık</th>
                    <th className="py-1.5 text-center font-semibold">Trend</th>
                    <th className="py-1.5 text-right font-semibold">1 Ay</th>
                    <th className="py-1.5 text-right font-semibold">3 Ay</th>
                    <th className="py-1.5 text-right font-semibold">1 Yıl</th>
                  </tr>
                </thead>
                <tbody>
                  {forecast.assetForecasts.map(a => (
                    <tr key={a.symbol} className="border-b border-gray-50">
                      <td className="py-1.5 font-medium text-gray-700 truncate max-w-[140px]" title={a.name}>{a.name}</td>
                      <td className="py-1.5"><div className="flex justify-center"><TrendIcon trend={a.trend} /></div></td>
                      <td className={`py-1.5 text-right font-semibold ${signClass(a.return1mPercent)}`}>{fmtPct(a.return1mPercent)}</td>
                      <td className={`py-1.5 text-right font-semibold ${signClass(a.return3mPercent)}`}>{fmtPct(a.return3mPercent)}</td>
                      <td className={`py-1.5 text-right font-semibold ${signClass(a.return12mPercent)}`}>{fmtPct(a.return12mPercent)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
              <p className="text-[10px] text-gray-400 mt-1.5">
                Varlık değerleri medyan (en olası) getiri tahminidir; aşırı geçmiş getiriler ortalamaya dönüş
                için yumuşatılmıştır. Trend = fiyatın MA20/MA50'ye göre yönü.
              </p>
            </div>
          )}
        </>
      )}
    </div>
  );
}
