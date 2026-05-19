export const BAN_STATUS = {
  ACTIVE: 'ACTIVE',
  TEMPORARY_BANNED: 'TEMPORARY_BANNED',
  PERMANENT_BANNED: 'PERMANENT_BANNED',
};

export function formatBanUntil(banUntil) {
  if (!banUntil) return null;
  const date = new Date(banUntil);
  if (Number.isNaN(date.getTime())) return null;
  return date.toLocaleString('tr-TR', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

export function isActiveUser(user) {
  return user?.banStatus === BAN_STATUS.ACTIVE || (user?.enabled && !user?.banStatus);
}

export function isTemporaryBan(user) {
  return user?.banStatus === BAN_STATUS.TEMPORARY_BANNED;
}

export function isPermanentBan(user) {
  return user?.banStatus === BAN_STATUS.PERMANENT_BANNED;
}
