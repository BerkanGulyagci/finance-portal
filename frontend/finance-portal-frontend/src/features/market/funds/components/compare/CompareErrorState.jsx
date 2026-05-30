import { AlertCircle } from 'lucide-react';

export default function CompareErrorState({ errors = {} }) {
  const entries = Object.entries(errors).filter(([, msg]) => msg);
  if (entries.length === 0) return null;
  return (
    <div className="bg-rose-50 border border-rose-200 rounded-2xl p-4 space-y-1">
      {entries.map(([code, msg]) => (
        <div key={code} className="flex items-center gap-2 text-sm text-rose-700">
          <AlertCircle className="w-4 h-4 shrink-0" />
          <span><span className="font-bold">{code}:</span> {msg}</span>
        </div>
      ))}
    </div>
  );
}
