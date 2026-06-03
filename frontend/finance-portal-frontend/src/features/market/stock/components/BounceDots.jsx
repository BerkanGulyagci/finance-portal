/**
 * Küçük "yükleniyor" göstergesi (3 zıplayan nokta). StockComparePage'den ortak dosyaya taşındı —
 * birden çok kart kullanıyor. Markup/Tailwind class'ları birebir aynı.
 */
export default function BounceDots({ size = 'md' }) {
  const sz = size === 'sm' ? 'w-1.5 h-1.5' : 'w-2 h-2';
  return (
    <div className="flex gap-1.5 items-center justify-center">
      <div className={`${sz} bg-[#093eaa] rounded-full animate-bounce`} />
      <div className={`${sz} bg-[#093eaa]/60 rounded-full animate-bounce [animation-delay:100ms]`} />
      <div className={`${sz} bg-[#093eaa]/30 rounded-full animate-bounce [animation-delay:200ms]`} />
    </div>
  );
}
