import { scoreColor, scoreBg } from '../lib/format';
import type { ScreeningStatus } from '../types';

export function ScoreRing({ score, size = 56 }: { score?: number; size?: number }) {
  const pct = score ?? 0;
  const radius = (size - 6) / 2;
  const circ = 2 * Math.PI * radius;
  const dash = (pct / 100) * circ;
  const stroke = pct >= 70 ? '#16a34a' : pct >= 45 ? '#f59e0b' : '#ef4444';

  return (
    <div className="relative shrink-0" style={{ width: size, height: size }}>
      <svg width={size} height={size} className="-rotate-90">
        <circle cx={size / 2} cy={size / 2} r={radius} fill="none" stroke="#e2e8f0" strokeWidth={5} />
        {score != null && (
          <circle
            cx={size / 2} cy={size / 2} r={radius} fill="none" stroke={stroke} strokeWidth={5}
            strokeDasharray={`${dash} ${circ}`} strokeLinecap="round"
          />
        )}
      </svg>
      <div className="absolute inset-0 flex items-center justify-center">
        <span className={`text-sm font-bold ${scoreColor(score)}`}>
          {score != null ? Math.round(score) : '—'}
        </span>
      </div>
    </div>
  );
}

export function ScoreBar({ label, value }: { label: string; value?: number }) {
  return (
    <div>
      <div className="mb-1 flex justify-between text-xs">
        <span className="text-slate-500">{label}</span>
        <span className={`font-semibold ${scoreColor(value)}`}>
          {value != null ? `${Math.round(value)}` : '—'}
        </span>
      </div>
      <div className="h-1.5 rounded-full bg-slate-100">
        <div className={`h-1.5 rounded-full ${scoreBg(value)}`} style={{ width: `${value ?? 0}%` }} />
      </div>
    </div>
  );
}

const STATUS_LABEL: Record<ScreeningStatus, { label: string; className: string }> = {
  PENDING: { label: 'Queued', className: 'bg-slate-100 text-slate-500' },
  PROCESSING: { label: 'Screening…', className: 'bg-blue-100 text-blue-600' },
  COMPLETED: { label: 'Screened', className: 'bg-green-100 text-green-700' },
  FAILED: { label: 'Screening failed', className: 'bg-red-100 text-red-600' },
};

export function ScreeningStatusBadge({ status }: { status?: ScreeningStatus }) {
  if (!status) return null;
  const s = STATUS_LABEL[status];
  return <span className={`badge ${s.className}`}>{s.label}</span>;
}
