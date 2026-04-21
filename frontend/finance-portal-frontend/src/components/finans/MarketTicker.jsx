import { useEffect, useState, useRef } from 'react';
import { getFxTcmb, getCryptos, getEconomicIndicators } from '../../api/marketApi';

function Sparkline({ data, color }) {
  if (!data || data.length < 2) return null;
  const w = 64, h = 28;
  const min = Math.min(...data);
  const max = Math.max(...data);
  const range = max - min || 1;
  const pts = data.map((v, i) => {
    const x = (i / (data.length - 1)) * w;
    const y = h - ((v - min) / range) * (h - 4) - 2;
    return `${x},${y}`;
  }).join(' ');
  const fillPts = `0,${h} ${pts} ${w},${h}`;
  return (
    <svg width={w} height={h} className="shrink-0">
      <defs>
        <linearGradient id={`g${color.replace('#','')}`} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor={color} stopOpacity="0.3" />
          <stop offset="100%" stopColor={color} stopOpacity="0" />
        </linearGradient>
      </defs>
      <polygon points={fillPts} fill={`url(#g${color.replace('#','')})`} />
      <polyline points={pts} fill="none" stroke={color} strokeWidth="1.5" strokeLinejoin="round" />
    </svg>
  );
}

function makeSparkline(value, change, points = 12) {
  if (!value || isNaN(value)) return [];
  const arr = [];
  let v = value - (change ?? 0);
  for (let i = 0; i < points; i++) {
    v += (Math.random() - 0.48) * Math.abs(value * 0.003);
    arr.push(v);
  }
  arr.push(value);
  return arr;
}

export function MarketTicker() {
  const [items, setItems] = useState([]);
  const scrollRef = useRef(null);
  const [isPaused, setIsPaused] = useState(false);
  const posRef = useRef(0);
  const animRef = useRef(null);

  useEffect(() => {
    async function load() {
      try {
        const [fx, cryptos, indicators, futures] = await Promise.all([
          getFxTcmb().catch(() => null),
          getCryptos(0, 50).catch(() => []),
          getEconomicIndicators().catch(() => ({})),
          fetch('http://localhost:8080/api/market/futures?page=0&size=100')
            .then(r => r.json()).then(d => d.data).catch(() => null),
        ]);

        const result = [];
        const rates = fx?.rates ?? [];
        const futuresList = futures?.content ?? [];

        // GC=F — Altın/ONS vadeli
        const gcf = futuresList.find(f => f.symbol === 'GC=F');

        // USD/TRY
        const usdRate = rates.find(x => x.symbol === 'USD');
        if (usdRate) {
          const val = parseFloat(usdRate.sell);
          result.push({ label: 'USD/TRY', value: val.toLocaleString('tr-TR', { minimumFractionDigits: 3 }), change: null, dir: null, spark: makeSparkline(val, 0) });
        }

        // EUR/TRY
        const eurRate = rates.find(x => x.symbol === 'EUR');
        if (eurRate) {
          const val = parseFloat(eurRate.sell);
          result.push({ label: 'EUR/TRY', value: val.toLocaleString('tr-TR', { minimumFractionDigits: 3 }), change: null, dir: null, spark: makeSparkline(val, 0) });
        }

        // GBP/TRY
        const gbpRate = rates.find(x => x.symbol === 'GBP');
        if (gbpRate) {
          const val = parseFloat(gbpRate.sell);
          result.push({ label: 'GBP/TRY', value: val.toLocaleString('tr-TR', { minimumFractionDigits: 3 }), change: null, dir: null, spark: makeSparkline(val, 0) });
        }

        // FAİZ
        const faiz = parseFloat(indicators?.policyRate ?? 37);
        result.push({ label: 'FAİZ', value: `${faiz.toLocaleString('tr-TR', { minimumFractionDigits: 2 })}%`, change: null, dir: null, spark: makeSparkline(faiz, 0) });

        // Altın/ONS — GC=F vadeli kontrat
        if (gcf?.price) {
          const val = parseFloat(gcf.price);
          const chg = parseFloat(gcf.changePercent ?? 0);
          result.push({ label: 'ALTIN/ONS', value: val.toLocaleString('tr-TR', { minimumFractionDigits: 2 }), change: chg !== 0 ? chg : null, dir: chg > 0 ? 'up' : chg < 0 ? 'down' : null, spark: makeSparkline(val, chg * val / 100) });
        } else {
          // Fallback: CoinGecko PAXG/XAUT
          const goldToken = cryptos.find(x => x.symbol?.toLowerCase() === 'paxg' || x.symbol?.toLowerCase() === 'xaut');
          if (goldToken) {
            const val = parseFloat(goldToken.currentPrice ?? 0);
            const chg = parseFloat(goldToken.priceChangePercentage24h ?? 0);
            result.push({ label: 'ALTIN/TRY', value: val.toLocaleString('tr-TR', { minimumFractionDigits: 0 }), change: chg !== 0 ? chg : null, dir: chg > 0 ? 'up' : chg < 0 ? 'down' : null, spark: makeSparkline(val, chg * val / 100) });
          }
        }

        // Kripto — BTC, ETH, BNB, SOL
        ['btc', 'eth', 'bnb', 'sol'].forEach(sym => {
          const c = cryptos.find(x => x.symbol?.toLowerCase() === sym);
          if (!c) return;
          const val = parseFloat(c.currentPrice ?? 0);
          const chg = parseFloat(c.priceChangePercentage24h ?? 0);
          result.push({ label: c.symbol?.toUpperCase(), value: val.toLocaleString('tr-TR', { minimumFractionDigits: 0 }), change: chg !== 0 ? chg : null, dir: chg > 0 ? 'up' : chg < 0 ? 'down' : null, spark: makeSparkline(val, chg * val / 100) });
        });

        setItems(result);
      } catch {
        setItems([]);
      }
    }
    load();
  }, []);

  useEffect(() => {
    const el = scrollRef.current;
    if (!el || !items.length) return;
    const speed = 0.5;
    const animate = () => {
      if (!isPaused && el) {
        posRef.current += speed;
        const half = el.scrollWidth / 2;
        if (posRef.current >= half) posRef.current = 0;
        el.scrollLeft = posRef.current;
      }
      animRef.current = requestAnimationFrame(animate);
    };
    animRef.current = requestAnimationFrame(animate);
    return () => cancelAnimationFrame(animRef.current);
  }, [isPaused, items]);

  if (!items.length) return null;

  const TickerCard = ({ item }) => {
    const isUp = item.dir === 'up';
    const isDown = item.dir === 'down';
    const valueColor = isUp ? '#10b981' : isDown ? '#ef4444' : '#093eaa';
    const sparkColor = isUp ? '#10b981' : isDown ? '#ef4444' : '#093eaa';
    return (
      <div className="flex items-center gap-3 shrink-0 px-5 border-r border-gray-200 last:border-r-0 min-w-[160px]">
        <div className="flex-1 min-w-0">
          <p className="text-[10px] font-bold text-gray-400 uppercase tracking-wider mb-0.5">{item.label}</p>
          <p className="text-sm font-bold leading-tight" style={{ color: valueColor }}>{item.value}</p>
          {item.change != null && (
            <p className={`text-[10px] font-semibold mt-0.5 ${isUp ? 'text-emerald-600' : 'text-rose-600'}`}>
              {isUp ? '▲' : '▼'} {Math.abs(item.change).toFixed(2)}%
            </p>
          )}
        </div>
        <Sparkline data={item.spark} color={sparkColor} />
      </div>
    );
  };

  return (
    <div className="bg-white border-b border-gray-200 overflow-hidden"
      onMouseEnter={() => setIsPaused(true)}
      onMouseLeave={() => setIsPaused(false)}>
      <div ref={scrollRef} className="flex items-stretch overflow-x-hidden select-none py-2"
        style={{ scrollBehavior: 'auto' }}>
        {[...items, ...items].map((item, i) => (
          <TickerCard key={`${item.label}-${i}`} item={item} />
        ))}
      </div>
    </div>
  );
}
