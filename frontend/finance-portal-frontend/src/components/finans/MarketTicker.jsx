import { useEffect, useState, useRef } from 'react';
import { TrendingUp, TrendingDown } from 'lucide-react';
import { getFxTcmb, getCryptos } from '../../api/marketApi';

export function MarketTicker() {
  const [tickerData, setTickerData] = useState([]);
  const scrollRef = useRef(null);
  const [isPaused, setIsPaused] = useState(false);

  useEffect(() => {
    async function load() {
      try {
        const [fx, cryptos] = await Promise.all([
          getFxTcmb(),
          getCryptos(0, 5),
        ]);

        const fxItems = (fx?.rates ?? []).slice(0, 5).map(r => ({
          symbol: `${r.symbol}/TRY`,
          value: r.sell?.toLocaleString('tr-TR', { minimumFractionDigits: 2 }) ?? '-',
          change: 0,
          isPositive: null,
        }));

        const cryptoItems = cryptos.slice(0, 5).map(c => ({
          symbol: c.symbol?.toUpperCase(),
          value: c.currentPrice?.toLocaleString('tr-TR', { minimumFractionDigits: 2 }) + ' ₺',
          change: parseFloat(c.priceChangePercentage24h ?? 0),
          isPositive: c.priceChangePercentage24h > 0 ? true : c.priceChangePercentage24h < 0 ? false : null,
        }));

        setTickerData([...fxItems, ...cryptoItems]);
      } catch {
        // fallback static data
        setTickerData([
          { symbol: 'USD/TRY', value: '-', change: 0, isPositive: null },
          { symbol: 'EUR/TRY', value: '-', change: 0, isPositive: null },
          { symbol: 'BTC', value: '-', change: 0, isPositive: null },
        ]);
      }
    }
    load();
  }, []);

  useEffect(() => {
    const el = scrollRef.current;
    if (!el) return;
    let animId;
    let pos = 0;
    const speed = 0.5;
    const animate = () => {
      if (!isPaused && el) {
        pos += speed;
        const max = el.scrollWidth - el.clientWidth;
        if (pos >= max) pos = 0;
        el.scrollLeft = pos;
      }
      animId = requestAnimationFrame(animate);
    };
    animId = requestAnimationFrame(animate);
    return () => cancelAnimationFrame(animId);
  }, [isPaused, tickerData]);

  if (!tickerData.length) return null;

  const TickerItems = () => (
    <>
      {tickerData.map((item, i) => (
        <div key={`${item.symbol}-${i}`} className={`flex items-center gap-2 shrink-0 ${i > 0 ? 'border-l border-gray-200 pl-6' : ''}`}>
          <span className="text-gray-500 uppercase text-xs font-bold">{item.symbol}</span>
          <span className={`text-xs font-bold ${item.isPositive === true ? 'text-emerald-600' : item.isPositive === false ? 'text-rose-600' : 'text-gray-800'}`}>
            {item.value}
          </span>
          {item.change !== 0 && (
            <span className={`px-1 rounded flex items-center gap-0.5 text-xs ${item.isPositive === true ? 'bg-emerald-100 text-emerald-700' : 'bg-rose-100 text-rose-700'}`}>
              {item.isPositive === true ? <TrendingUp className="w-2.5 h-2.5" /> : <TrendingDown className="w-2.5 h-2.5" />}
              {Math.abs(item.change).toFixed(2)}%
            </span>
          )}
        </div>
      ))}
    </>
  );

  return (
    <div className="bg-white border-b border-gray-200 overflow-hidden py-2.5"
      onMouseEnter={() => setIsPaused(true)}
      onMouseLeave={() => setIsPaused(false)}>
      <div ref={scrollRef} className="flex gap-6 items-center px-4 overflow-x-hidden" style={{ scrollBehavior: 'auto' }}>
        <TickerItems />
        <TickerItems />
      </div>
    </div>
  );
}
