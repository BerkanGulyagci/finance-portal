import { TrendingUp, TrendingDown } from 'lucide-react';
import { useTheme } from '../../context/ThemeContext';
import { useTranslation } from '../../context/LanguageContext';

/**
 * Grafik hover tooltip kartı — imleci takip eder (çizgi modu). TEMA UYUMLU:
 *  • Açık tema → BEYAZ kart / SİYAH yazı
 *  • Koyu tema → KOYU kart / BEYAZ yazı
 * Hisse/kripto/emtia grafiklerinde ortak kullanılır (her biri kendi setHover'ını besler).
 *
 * @param hover  { dateLabel, priceLabel, changePct, up, volumeLabel } | null
 * @param pos    { x, y, flipX, flipY } — imleç konumu + kenar ters-çevirme
 */
export default function ChartHoverCard({ hover, pos }) {
  const { isDark } = useTheme();
  const { t } = useTranslation();
  if (!hover) return null;

  // Tema renkleri (Tailwind dark: yok → JS koşullu).
  const card = isDark
    ? 'bg-slate-900/90 ring-white/10'
    : 'bg-white/95 ring-black/10';
  const dateText  = isDark ? 'text-slate-300' : 'text-gray-500';
  const priceText = isDark ? 'text-white'     : 'text-gray-900';
  const volBorder = isDark ? 'border-white/10' : 'border-black/10';
  const volLabel  = isDark ? 'text-slate-400'  : 'text-gray-500';
  const volValue  = isDark ? 'text-slate-200'  : 'text-gray-800';
  const periodTxt = isDark ? 'text-slate-500'  : 'text-gray-400';

  return (
    <div
      className={`pointer-events-none absolute z-30 min-w-[140px] rounded-xl px-3.5 py-2.5 shadow-2xl ring-1 backdrop-blur-md ${card}`}
      style={{
        left: pos.x,
        top: pos.y,
        transform: `translate(${pos.flipX ? 'calc(-100% - 14px)' : '14px'}, ${pos.flipY ? 'calc(-100% - 14px)' : '14px'})`,
      }}
    >
      <div className="mb-1 flex items-center gap-1.5">
        <span className="inline-block h-2 w-2 rounded-full" style={{ background: hover.up ? '#10b981' : '#ef4444' }} />
        <span className={`text-[11px] font-medium ${dateText}`}>{hover.dateLabel}</span>
      </div>
      <div className={`text-base font-black leading-tight tabular-nums ${priceText}`}>{hover.priceLabel}</div>
      {hover.changePct != null && (
        <div className={`mt-0.5 flex items-center gap-1 text-xs font-bold ${hover.up ? 'text-emerald-500' : 'text-rose-500'}`}>
          {hover.up ? <TrendingUp className="h-3 w-3" /> : <TrendingDown className="h-3 w-3" />}
          {hover.up ? '+' : ''}{hover.changePct.toFixed(2)}%
          <span className={`font-medium ${periodTxt}`}>· {t('dönem')}</span>
        </div>
      )}
      {hover.volumeLabel && (
        <div className={`mt-1 border-t pt-1 text-[11px] ${volBorder} ${volLabel}`}>
          {t('Hacim')}: <span className={`font-semibold ${volValue}`}>{hover.volumeLabel}</span>
        </div>
      )}
    </div>
  );
}
