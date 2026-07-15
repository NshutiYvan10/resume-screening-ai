import { ReactNode } from 'react';

export default function StatCard({
  label,
  value,
  icon,
  accent = 'text-brand-600 bg-brand-50',
}: {
  label: string;
  value: ReactNode;
  icon?: ReactNode;
  accent?: string;
}) {
  return (
    <div className="card flex items-center gap-4 p-5">
      {icon && (
        <div className={`flex h-11 w-11 items-center justify-center rounded-lg ${accent}`}>{icon}</div>
      )}
      <div>
        <p className="text-2xl font-bold text-slate-900">{value}</p>
        <p className="text-sm text-slate-500">{label}</p>
      </div>
    </div>
  );
}
