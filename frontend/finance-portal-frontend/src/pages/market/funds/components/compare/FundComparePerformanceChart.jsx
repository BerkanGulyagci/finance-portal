import { useMemo } from 'react';
import {
  LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip,
  ResponsiveContainer, Legend, ReferenceLine,
} from 'recharts';
import { COMPARE_COLORS, buildChartData, calcPeriodReturn, toNum } from './compareUtils';
import { useTranslation } from '../../../../../i18n/LanguageContext';

function ChartTooltip({ active, payload, label }) {
  if (!active || !payload?.length) return null;
  return (
    <div className="bg-white border border-gray-200 rounded-xl shadow-lg px-4 py-3 text-sm min-w-[180px]">
      <p className="text-gray-500 text-xs font-semibold mb-2 border-b border-gray-100 pb-1.5">{label}</p>
      <div className="space-y-1">
        {payload.map((p, i) => (
          <div key={i} className="flex justify-between gap-4 items-center">
            <span className="flex items-center gap-1.5 text-xs text-gray-600">
              <span className="w-2 h-2 rounded-full shrink-0" style={{ background: p.color }} />
              {p.name}
            </span>
            <span className="font-bold text-xs" style={{ color: p.color }}>
              {p.value >= 0 ? '+' : ''}{p.value?.toFixed(2)}%
            </span>
          </div>
        ))}
      </div>
    </div>
  );
}

export default function FundComparePerformanceChart({ detailMap, selectedCodes, range }) {
  const { t } = useTranslation();
  const chartData = useMemo(
    () => buildChartData(detailMap, selectedCodes, range),
    [detailMap, selectedCodes, range]
  );

  // Dönem getirileri
  const returns = useMemo(() => {
    return selectedCodes.map(code => ({
      code,
      ret: calcPeriodReturn(detailMap[code]?.priceHistory ?? [], range),
    }));
  }, [detailMap, selectedCodes, range]);

  function formatDate(d) {
    if (!d) return '';
    const parts = d.split('-');
    if (range === '1M') return `${parts[2]}/${parts[1]}`;
    return `${parts[1]}/${parts[0]?.slice(2)}`;
  }

  if (chartData.length === 0) {
    return (
      <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-6">
        <h2 className="font-bold text-gray-900 mb-2">{t('Normalize Performans Grafiği')}</h2>
        <div className="h-64 flex items-center justify-center text-gray-400 text-sm">
          {t('Grafik için yeterli fiyat geçmişi bulunamadı.')}
        </div>
      </div>
    );
  }

  return (
    <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-6">
      <div className="mb-4">
        <h2 className="font-bold text-gray-900">{t('Normalize Performans Grafiği')}</h2>
        <p className="text-xs text-gray-400 mt-0.5">{t('Dönem başına göre yüzde getiri — fiyat seviyesi normalize edilmiştir')}</p>

        {/* Dönem getirileri */}
        <div className="flex flex-wrap gap-x-5 gap-y-1 mt-2.5">
          {returns.map(({ code, ret }, idx) => {
            if (ret == null) return null;
            const pos = ret >= 0;
            return (
              <span key={code} className={`text-xs font-semibold flex items-center gap-1.5 ${pos ? 'text-emerald-600' : 'text-rose-600'}`}>
                <span className="w-2.5 h-2.5 rounded-full shrink-0" style={{ background: COMPARE_COLORS[idx % COMPARE_COLORS.length] }} />
                {code}: {pos ? '+' : ''}{ret.toFixed(2)}%
              </span>
            );
          })}
        </div>
      </div>

      <div className="h-80">
        <ResponsiveContainer width="100%" height="100%">
          <LineChart data={chartData} margin={{ top: 5, right: 10, left: 10, bottom: 5 }}>
            <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0" vertical={false} />
            <XAxis
              dataKey="date"
              tick={{ fontSize: 10, fill: '#9ca3af' }}
              tickFormatter={formatDate}
              interval="preserveStartEnd"
              axisLine={false}
              tickLine={false}
            />
            <YAxis
              tick={{ fontSize: 10, fill: '#9ca3af' }}
              tickFormatter={v => `${v >= 0 ? '+' : ''}${v.toFixed(1)}%`}
              width={65}
              axisLine={false}
              tickLine={false}
            />
            <ReferenceLine y={0} stroke="#e5e7eb" strokeDasharray="4 2" />
            <Tooltip content={<ChartTooltip />} />
            <Legend
              formatter={value => {
                const idx = selectedCodes.indexOf(value);
                const name = detailMap[value]?.name;
                return (
                  <span style={{ color: COMPARE_COLORS[idx % COMPARE_COLORS.length], fontSize: 12 }}>
                    {value}{name ? ` — ${name.slice(0, 25)}${name.length > 25 ? '…' : ''}` : ''}
                  </span>
                );
              }}
            />
            {selectedCodes.map((code, idx) => (
              <Line
                key={code}
                type="monotone"
                dataKey={code}
                name={code}
                stroke={COMPARE_COLORS[idx % COMPARE_COLORS.length]}
                strokeWidth={2}
                dot={false}
                activeDot={{ r: 4, strokeWidth: 0 }}
                connectNulls
              />
            ))}
          </LineChart>
        </ResponsiveContainer>
      </div>
    </div>
  );
}
