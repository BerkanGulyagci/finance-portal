import { useTranslation } from '../../../context/LanguageContext';
import { formatBanUntil, getBanStatusLabel, isActiveUser, isPermanentBan, isTemporaryBan } from '../utils/banDisplay';

export default function UserStatusBadge({ user }) {
  const { t } = useTranslation();

  if (isActiveUser(user)) {
    return (
      <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-bold bg-emerald-50 text-emerald-700 border border-emerald-200">
        {t(getBanStatusLabel(user))}
      </span>
    );
  }

  if (isTemporaryBan(user)) {
    const untilLabel = formatBanUntil(user.banUntil);
    return (
      <span
        className="inline-flex flex-col items-start gap-0.5 px-2.5 py-1 rounded-lg text-xs font-bold bg-amber-50 text-amber-800 border border-amber-200 max-w-[11rem]"
        title={untilLabel ? `${t('Bitiş')}: ${untilLabel}` : undefined}
      >
        <span>{t(getBanStatusLabel(user))}</span>
        {untilLabel && <span className="font-medium text-[10px] leading-tight">→ {untilLabel}</span>}
      </span>
    );
  }

  if (isPermanentBan(user)) {
    return (
      <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-bold bg-rose-50 text-rose-700 border border-rose-200">
        {t(getBanStatusLabel(user))}
      </span>
    );
  }

  return (
    <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-bold bg-gray-50 text-gray-600 border border-gray-200">
      {t(getBanStatusLabel(user))}
    </span>
  );
}
